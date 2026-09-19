<template>
  <div class="store">
    <AppHeader active="store" />

    <main class="main">
      <h1 class="page-title">课程商城</h1>
      <p class="page-subtitle">挑一门课，开始学习吧</p>

      <!-- 加载中 / 空数据 -->
      <div v-if="loading" class="tips">课程加载中…</div>
      <div v-else-if="courses.length === 0" class="tips">暂时没有上架课程</div>

      <!-- 课程卡片列表 -->
      <div v-else class="grid">
        <div v-for="course in courses" :key="course.id" class="card">
          <!-- 封面：有图用图，没图就用渐变色块 + 课程名首字占位 -->
          <div class="cover" :style="coverStyle(course)">
            <img v-if="course.cover" :src="course.cover" :alt="course.title" />
            <span v-else class="cover-char">{{ course.title.charAt(0) }}</span>
          </div>

          <div class="card-body">
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
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
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

// 封面没有真实图片时，按课程 id 轮着用几种渐变色，避免千篇一律
const palette = [
  'linear-gradient(135deg, #6366f1, #8b5cf6)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
]
function coverStyle(course) {
  return { background: palette[course.id % palette.length] }
}

// 价格是 BigDecimal 序列化来的字符串，比如 "99.00"，转数字后再补两位小数显示
function formatPrice(price) {
  return Number(price || 0).toFixed(2)
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
</script><style scoped>
.store {
  min-height: 100vh;
}
.main {
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
.page-title { font-size: 26px; }
.page-subtitle { color: #999; margin: 6px 0 24px; font-size: 14px; }

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