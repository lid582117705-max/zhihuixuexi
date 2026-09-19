import request from './request'

// 拉当前用户的全部学习对话（时间从旧到新）
export function listChat() {
  return request.get('/api/learn/chat')
}

// 追加一条对话：role 只能是 user（用户）或 assistant（助手）
export function sendChat(role, content) {
  return request.post('/api/learn/chat', { role, content })
}