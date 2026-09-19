import request from './request'

// 登录：后端返回 data = { token, user }
export function login(data) {
  return request.post('/api/users/login', data)
}

// 注册：后端返回 data = 用户信息
export function register(data) {
  return request.post('/api/users/register', data)
}

// 退出登录：通知后端删掉 Redis 里的 token
export function logout() {
  return request.post('/api/users/logout')
}