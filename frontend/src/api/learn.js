import request from './request'

// 拉当前用户的全部学习对话（时间从旧到新）
export function listChat() {
  return request.get('/api/learn/chat')
}

// 追加一条对话：role 只能是 user（用户）或 assistant（助手）
export function sendChat(role, content) {
  return request.post('/api/learn/chat', { role, content })
}
// 问 AI 助教一句话，返回 AI 的回答；后端会自动把这一问一答存进 Redis
// timeout 30 秒：qwen-turbo 快，但工具调用/资料检索需要更多时间
export function chatAI(message) {
  return request.post('/api/learn/ai/chat', { message }, { timeout: 30000 })
}
