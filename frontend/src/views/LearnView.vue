<template>
  <div class="learn">
    <AppHeader active="" />

    <main class="main">
      <!-- 课程信息卡 -->
      <div v-if="course" class="course-banner">
        <div class="cover" :style="coverStyle(course)">
          <img v-if="course.cover" :src="course.cover" :alt="course.title" />
          <span v-else class="cover-char">{{ course.title.charAt(0) }}</span>
        </div>
        <div class="course-info">
          <h1>{{ course.title }}</h1>
          <p class="teacher">讲师：{{ course.teacherName }}</p>
          <p class="desc">{{ course.description }}</p>
        </div>
      </div>
      <div v-else class="tips">课程加载中…</div>

      <!-- 学习对话区 -->
      <div class="chat-box">
        <div class="chat-head">
          <span>学习助手 · 提问板</span>
          <span class="chat-tip">（阶段 7 接入 Spring AI 前，助手回复为模拟占位）</span>
        </div>

        <div class="messages" ref="msgListRef">
          <div v-if="messages.length === 0" class="chat-empty">还没有提问，在下方输入框发一条试试</div>
          <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
            <div class="bubble">{{ msg.content }}</div>
            <div class="meta">{{ roleText(msg.role) }} · {{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <div class="input-row">
          <input
            v-model="content"
            class="chat-input"
            placeholder="向学习助手提问，比如：这个知识点面试里怎么答？"
            @keyup.enter="send"
          />
          <button class="send-btn" :disabled="sending" @click="send">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </div>
      </div>
    </main>
  </div>
</template><script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { getCourse } from '../api/course'
import { listChat, sendChat } from '../api/learn'

const route = useRoute()
const router = useRouter()
const courseId = route.params.courseId

const course = ref(null)
const messages = ref([])
const content = ref('')
const sending = ref(false)
const msgListRef = ref(null)

// 封面占位渐变色：和商城/我的课程页保持一致
const palette = [
  'linear-gradient(135deg, #6366f1, #8b5cf6)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #ec4899, #8b5cf6)',
]
function coverStyle(c) {
  return { background: palette[c.id % palette.length] }
}

function roleText(role) {
  return role === 'assistant' ? '学习助手' : '我'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

// 新消息进来后滚到底部，保证能看到最新一条
function scrollToBottom() {
  nextTick(() => {
    const el = msgListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(async () => {
  try {
    course.value = await getCourse(courseId)
  } catch (e) {
    // 课程不存在或已下架：回到我的课程页
    router.replace('/mine')
    return
  }
  try {
    messages.value = await listChat()
    scrollToBottom()
  } catch (e) {
    // 拦截器已提示
  }
})

async function send() {
  const text = content.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    // 第 1 步：发用户消息，把后端返回的这条追加进列表
    const userMsg = await sendChat('user', text)
    messages.value.push(userMsg)
    content.value = ''
    scrollToBottom()

    // 第 2 步：补一条模拟助手回复（阶段 7 接 Spring AI 后这里会是真的 AI 回答）
    const reply = '收到你的提问啦：「' + text + '」。等阶段 7 接入 Spring AI 后，这里会由 AI 助教自动回答。'
    const assistantMsg = await sendChat('assistant', reply)
    messages.value.push(assistantMsg)
    scrollToBottom()
  } catch (e) {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}
</script><style scoped>
.learn {
  min-height: 100vh;
}
.main {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

.tips { padding: 60px 0; text-align: center; color: #999; }

/* 课程信息卡 */
.course-banner {
  display: flex;
  gap: 20px;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
}
.cover {
  width: 180px;
  height: 120px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  overflow: hidden;
}
.cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-char { font-size: 44px; font-weight: 700; opacity: 0.9; }
.course-info h1 { font-size: 20px; margin-bottom: 8px; }
.teacher { font-size: 13px; color: #999; margin-bottom: 8px; }
.desc { font-size: 14px; color: #666; line-height: 1.6; }

/* 对话区 */
.chat-box {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 700;
  font-size: 15px;
}
.chat-tip { font-weight: 400; font-size: 12px; color: #999; }

.messages {
  height: 420px;
  overflow-y: auto;
  padding: 18px;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.chat-empty { margin: auto; color: #bbb; font-size: 13px; }

.msg { max-width: 78%; display: flex; flex-direction: column; }
.msg.user { align-self: flex-end; align-items: flex-end; }
.msg.assistant { align-self: flex-start; align-items: flex-start; }
.bubble {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.msg.user .bubble { background: #3b82f6; color: #fff; border-top-right-radius: 2px; }
.msg.assistant .bubble { background: #fff; border: 1px solid #e8e8e8; border-top-left-radius: 2px; }
.meta { font-size: 11px; color: #bbb; margin-top: 4px; }

.input-row {
  display: flex;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid #f0f0f0;
}
.chat-input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
}
.chat-input:focus { border-color: #3b82f6; }
.send-btn {
  padding: 10px 22px;
  border: none;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.send-btn:disabled { opacity: 0.6; }
</style>