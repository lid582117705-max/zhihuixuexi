<template>
  <div class="mine">
    <AppHeader active="mine" />

    <main class="main">
      <h1 class="page-title">我的课程</h1>
      <p class="page-subtitle">你购买过的课程都在这里，点击进入学习</p>

      <div v-if="loading" class="tips">加载中…</div>
      <div v-else-if="courses.length === 0" class="tips">
        <p>还没有购买任何课程</p>
        <router-link to="/" class="go-store">去商城逛逛 →</router-link>
      </div>

      <div v-else class="grid">
        <div v-for="item in courses" :key="item.courseId" class="card" @click="goLearn(item.courseId)">
          <!-- 封面：跟商城页同一套占位逻辑 -->
          <div class="cover" :style="coverStyle(item)">
            <img v-if="item.cover" :src="item.cover" :alt="item.courseTitle" />
            <span v-else class="cover-char">{{ item.courseTitle.charAt(0) }}</span>
          </div>

          <div class="card-body">
            <h3 class="course-title" :title="item.courseTitle">{{ item.courseTitle }}</h3>
            <p class="teacher">讲师：{{ item.teacherName }}</p>

            <div class="card-footer">
              <div class="meta">
                <span class="price">¥{{ formatPrice(item.amount) }}</span>
                <span class="date">购买于 {{ formatTime(item.payTime) }}</span>
              </div>
              <button class="learn-btn">进入学习</button>
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
import { getMyCourses } from '../api/order'

const router = useRouter()
const courses = ref([])
const loading = ref(true)

// 封面占位渐变色：跟商城页保持一致（学习页/商城页都能兜底没有封面图的课程）
const palette = [
  'linear-gradient(135deg, #6366f1, #8b5cf6)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
]
function coverStyle(item) {
  return { background: palette[item.courseId % palette.length] }
}

function formatPrice(price) {
  return Number(price || 0).toFixed(2)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  try {
    courses.value = await getMyCourses()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
})

function goLearn(courseId) {
  router.push(`/learn/${courseId}`)
}
</script><style scoped>
.mine {
  min-height: 100vh;
}
.main {
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
.page-title { font-size: 26px; }
.page-subtitle { color: #999; margin: 6px 0 24px; font-size: 14px; }

.tips {
  padding: 80px 0;
  text-align: center;
  color: #999;
}
.go-store {
  display: inline-block;
  margin-top: 12px;
  color: #3b82f6;
  text-decoration: none;
  font-size: 14px;
}

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
  cursor: pointer;
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
.teacher { font-size: 12px; color: #999; margin-bottom: 12px; }

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.meta { display: flex; flex-direction: column; gap: 4px; }
.price { font-size: 18px; font-weight: 700; color: #ef4444; }
.date { font-size: 12px; color: #999; }
.learn-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: #10b981;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
</style>