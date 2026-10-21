<template>
  <div class="store">
    <AppHeader active="store" />

    <main class="main">
      <h1 class="page-title">课程商城</h1>
      <p class="page-subtitle">挑一门课，开始学习吧</p>

      <!-- 筛选工具条：左边分类，右边搜索 -->
      <div class="toolbar">
        <div class="cats">
          <button
            v-for="cat in categories"
            :key="cat"
            class="chip"
            :class="{ active: activeCat === cat }"
            @click="activeCat = cat"
          >
            {{ cat }}
          </button>
        </div>
        <input
          v-model="keyword"
          class="search"
          type="search"
          placeholder="搜索课程名 / 讲师"
        />
      </div>

      <!-- 加载中 / 空数据 -->
      <div v-if="loading" class="tips">课程加载中…</div>
      <div v-else-if="courses.length === 0" class="tips">暂时没有上架课程</div>
      <div v-else-if="filtered.length === 0" class="tips">没有匹配的课程，换个关键词试试</div>

      <template v-else>
        <p class="result-tip">共 {{ filtered.length }} 门课程</p>

        <!-- 课程卡片列表 -->
        <div class="grid">
          <div v-for="course in filtered" :key="course.id" class="card">
            <!-- 封面：有图用图，没图就用分类配色块 + 课程名首字占位 -->
            <div class="cover" :style="coverStyle(course)">
              <img v-if="course.cover" :src="course.cover" :alt="course.title" />
              <span v-else class="cover-char">{{ course.title.charAt(0) }}</span>
            </div>

            <div class="card-body">
              <div class="meta">
                <span class="tag">{{ course.category || '未分类' }}</span>
                <span class="meta-item">{{ course.lessonCount || 0 }} 课时</span>
                <span class="meta-item">{{ formatStudents(course.studentCount) }}人在学</span>
              </div>

              <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
              <p class="teacher">讲师：{{ course.teacherName }}</p>
              <p class="desc">{{ course.description }}</p>

              <div class="card-footer">
                <span class="price">¥{{ formatPrice(course.price) }}</span>
                <!-- 已购：按钮变成"去学习"；没买过：立即购买 -->
                <button v-if="ownedIds.has(course.id)" class="learn-btn" @click="goLearn(course.id)">
                  去学习
                </button>
                <button v-else class="buy-btn" :disabled="buyingId === course.id" @click="handleBuy(course)">
                  {{ buyingId === course.id ? '下单中…' : '立即购买' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { listCourses } from '../api/course'
import { createOrder, getMyCourses } from '../api/order'

const router = useRouter()
const courses = ref([])
const loading = ref(true)
const buyingId = ref(null)
// 已购课程 id 集合：买过课的卡片按钮显示"去学习"而不是"立即购买"
const ownedIds = ref(new Set())

// 分类筛选 + 关键词搜索（数据量不大，纯前端过滤，不打后端接口）
const activeCat = ref('全部')
const keyword = ref('')

// 分类列表从数据里动态取，避免写死；用 Set 去重后按首次出现顺序排列
const categories = computed(() => {
  const set = new Set(courses.value.map((c) => c.category).filter(Boolean))
  return ['全部', ...set]
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return courses.value.filter((c) => {
    const catOk = activeCat.value === '全部' || c.category === activeCat.value
    if (!catOk) return false
    if (!kw) return true
    return (
      (c.title || '').toLowerCase().includes(kw) ||
      (c.teacherName || '').toLowerCase().includes(kw)
    )
  })
})

// 封面没有真实图片时，按分类配色；分类缺失则按 id 轮换，避免千篇一律
const categoryPalette = {
  后端开发: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
  前端开发: 'linear-gradient(135deg, #0ea5e9, #06b6d4)',
  数据库: 'linear-gradient(135deg, #f59e0b, #ef4444)',
  'AI 应用': 'linear-gradient(135deg, #10b981, #0ea5e9)',
  架构与中间件: 'linear-gradient(135deg, #ec4899, #8b5cf6)',
}
const fallbackPalette = [
  'linear-gradient(135deg, #6366f1, #8b5cf6)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
]
function coverStyle(course) {
  return {
    background:
      categoryPalette[course.category] || fallbackPalette[course.id % fallbackPalette.length],
  }
}

// 价格是 BigDecimal 序列化来的字符串，比如 "99.00"，转数字后再补两位小数显示
function formatPrice(price) {
  return Number(price || 0).toFixed(2)
}

// 学习人数：过万折成"x.x万"，个位数字太长的数字不好看
function formatStudents(n) {
  const num = Number(n || 0)
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  return String(num)
}

onMounted(async () => {
  try {
    courses.value = await listCourses()
  } catch (e) {
    // 错误提示已被响应拦截器弹出，这里不用再处理
  } finally {
    loading.value = false
  }
  // 已购集合拉失败也不阻塞商城展示（比如课程接口正常、订单接口暂时异常）
  try {
    const mine = await getMyCourses()
    ownedIds.value = new Set(mine.map((m) => m.courseId))
  } catch (e) {
    // 忽略
  }
})

// 立即购买：先下单，再跳去订单页做模拟支付
async function handleBuy(course) {
  if (buyingId.value) return
  buyingId.value = course.id
  try {
    const order = await createOrder(course.id)
    router.push(`/order/${order.id}`)
  } catch (e) {
    // 拦截器已提示
  } finally {
    buyingId.value = null
  }
}

// 已购课程 → 学习页（课程信息 + 学习对话）
function goLearn(courseId) {
  router.push(`/learn/${courseId}`)
}
</script>

<style scoped>
.store {
  min-height: 100vh;
}
.main {
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
.page-title { font-size: 26px; }
.page-subtitle { color: #999; margin: 6px 0 20px; font-size: 14px; }

/* ---------- 筛选工具条 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.cats { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.chip:hover { border-color: #93c5fd; color: #2563eb; }
.chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.search {
  width: 220px;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 13px;
  outline: none;
}
.search:focus { border-color: #93c5fd; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12); }

.result-tip { color: #999; font-size: 13px; margin-bottom: 14px; }
.tips { padding: 60px 0; text-align: center; color: #999; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}
.card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}
.cover {
  height: 130px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  overflow: hidden;
}
.cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-char { font-size: 52px; font-weight: 700; opacity: 0.9; }

.card-body { padding: 16px; }

/* 卡片顶部元信息：分类标签 + 课时 + 学习人数 */
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #9ca3af;
}
.tag {
  padding: 2px 8px;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}
.meta-item { white-space: nowrap; }

.course-title {
  font-size: 16px;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.teacher { font-size: 12px; color: #999; margin-bottom: 8px; }
.desc {
  font-size: 13px;
  color: #666;
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.price { font-size: 20px; font-weight: 700; color: #ef4444; }
.buy-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.buy-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.learn-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: #10b981;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
</style>
