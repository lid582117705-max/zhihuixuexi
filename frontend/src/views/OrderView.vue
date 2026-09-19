<template>
  <div class="order-page">
    <div class="order-card">
      <h2>订单详情</h2>

      <div v-if="loading" class="tips">订单加载中…</div>

      <template v-else-if="order">
        <!-- 状态横幅：待支付(橙) / 已支付(绿) / 已取消(灰) -->
        <div class="status-banner" :class="statusClass">
          <span class="status-dot"></span>
          <div>
            <div class="status-text">{{ statusText }}</div>
            <div v-if="order.status === 0" class="status-hint">
              {{ secondsLeft > 0 ? `请尽快支付，${secondsLeft} 秒后订单将自动关闭` : '等待系统自动关单…' }}
            </div>
            <div v-else-if="order.status === 2" class="status-hint">
              超时未支付，已由 RabbitMQ 延时队列自动关闭
            </div>
            <div v-else class="status-hint">支付成功，课程已开通 ✓</div>
          </div>
        </div>

        <!-- 订单信息 -->
        <div class="info">
          <div class="row"><span class="label">订单号</span><span class="value">{{ order.orderNo }}</span></div>
          <div class="row"><span class="label">课程</span><span class="value">{{ course ? course.title : '课程 #' + order.courseId }}</span></div>
          <div class="row" v-if="course"><span class="label">讲师</span><span class="value">{{ course.teacherName }}</span></div>
          <div class="row"><span class="label">金额</span><span class="value price">¥{{ formatPrice(order.amount) }}</span></div>
          <div class="row"><span class="label">下单时间</span><span class="value">{{ formatTime(order.createTime) }}</span></div>
          <div class="row" v-if="order.payTime"><span class="label">支付时间</span><span class="value">{{ formatTime(order.payTime) }}</span></div>
          <div class="row" v-if="order.cancelTime"><span class="label">关闭时间</span><span class="value">{{ formatTime(order.cancelTime) }}</span></div>
        </div>

        <!-- 操作区 -->
        <div class="actions">
          <button v-if="order.status === 0" class="pay-btn" :disabled="paying" @click="handlePay">
            {{ paying ? '支付中…' : '模拟支付' }}
          </button>
          <button class="refresh-btn" @click="fetchOrder">刷新状态</button>
          <router-link to="/" class="back-link">← 返回商城</router-link>
        </div>
      </template>

      <div v-else class="tips">订单不存在或无权查看</div>
    </div>
  </div>
</template><script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getOrder, payOrder } from '../api/order'
import { getCourse } from '../api/course'

const route = useRoute()
const orderId = route.params.id
const order = ref(null)
const course = ref(null)
const loading = ref(true)
const paying = ref(false)

// ---- 支付倒计时 ----
// 后端下单后会往 RabbitMQ 延时队列发消息，15 秒后若订单仍待支付就自动关单
const ORDER_TTL_SECONDS = 15
const secondsLeft = ref(ORDER_TTL_SECONDS)
let countdownTimer = null
let pollTimer = null

const statusText = computed(() => ({ 0: '待支付', 1: '已支付', 2: '已取消' }[order.value?.status] || '未知状态'))
const statusClass = computed(() => ({ 0: 'pending', 1: 'paid', 2: 'closed' }[order.value?.status] || ''))

// 关单截止时间 = 下单时间 + 15 秒。后端 LocalDateTime 序列化成 "2026-09-19T10:30:00"，
// 这里兼容空格分隔的格式后统一转时间戳，避免不同浏览器解析差异
function deadline() {
  const raw = order.value?.createTime
  if (!raw) return Date.now() + ORDER_TTL_SECONDS * 1000
  return new Date(String(raw).replace(' ', 'T')).getTime() + ORDER_TTL_SECONDS * 1000
}

// 每秒刷新剩余秒数，到 0 后主动刷一次状态，让"已取消"尽快展示出来
function startCountdown() {
  stopCountdown()
  countdownTimer = setInterval(() => {
    secondsLeft.value = Math.max(0, Math.ceil((deadline() - Date.now()) / 1000))
    if (secondsLeft.value <= 0) {
      stopCountdown()
      fetchOrder()
    }
  }, 1000)
}

// 待支付期间每 2 秒轮询一次订单状态（最多 15 次），能直观看到"自动关单"发生
function startPolling() {
  stopPolling()
  let count = 0
  pollTimer = setInterval(async () => {
    count += 1
    try {
      await fetchOrder()
    } catch (e) {
      stopPolling() // 网络异常就停，避免反复弹错误
      return
    }
    if (order.value?.status !== 0 || count >= 15) stopPolling()
  }, 2000)
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// 拉订单 +（顺手）拉课程标题；订单表里只存了 courseId，标题要再查一次课程接口
async function fetchOrder() {
  const data = await getOrder(orderId)
  order.value = data
  if (data.status === 0) {
    startCountdown()
    startPolling()
  } else {
    stopCountdown()
    stopPolling()
  }
  if (data.courseId) {
    try {
      course.value = await getCourse(data.courseId)
    } catch (e) {
      // 课程信息拉不到也不影响看订单
    }
  }
  loading.value = false
}

async function handlePay() {
  paying.value = true
  try {
    const data = await payOrder(orderId)
    order.value = data
    stopCountdown()
    stopPolling()
  } catch (e) {
    // 拦截器已提示
  } finally {
    paying.value = false
  }
}

function formatPrice(price) {
  return Number(price || 0).toFixed(2)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(async () => {
  try {
    await fetchOrder()
  } catch (e) {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  stopCountdown()
  stopPolling()
})
</script><style scoped>
.order-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6fa;
  padding: 24px;
}
.order-card {
  width: 100%;
  max-width: 560px;
  padding: 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}
.order-card h2 { margin: 0 0 20px; }

.tips { padding: 40px 0; text-align: center; color: #999; }

.status-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid transparent;
}
.status-banner.pending { background: #fff7e6; border-color: #ffd591; }
.status-banner.paid    { background: #f0f9eb; border-color: #b7eb8f; }
.status-banner.closed  { background: #f5f5f5; border-color: #d9d9d9; }

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.pending .status-dot { background: #fa8c16; }
.paid .status-dot    { background: #52c41a; }
.closed .status-dot  { background: #bfbfbf; }

.status-text { font-size: 16px; font-weight: 700; }
.pending .status-text { color: #d46b08; }
.paid .status-text    { color: #389e0d; }
.closed .status-text  { color: #8c8c8c; }
.status-hint { font-size: 12px; color: #999; margin-top: 2px; }

.info { margin: 20px 0; }
.row {
  display: flex;
  padding: 10px 0;
  border-bottom: 1px dashed #eee;
  font-size: 14px;
}
.row .label { width: 90px; color: #999; flex-shrink: 0; }
.row .value { color: #333; word-break: break-all; }
.row .value.price { color: #ef4444; font-weight: 700; }

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}
.pay-btn {
  padding: 10px 28px;
  border: none;
  border-radius: 8px;
  background: #10b981;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.pay-btn:disabled { opacity: 0.6; }
.refresh-btn {
  padding: 10px 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #fff;
  color: #666;
  cursor: pointer;
  font-size: 14px;
}
.back-link { margin-left: auto; color: #3b82f6; text-decoration: none; font-size: 14px; }
</style>