import axios from 'axios'

// 创建一个"统一出口"的 axios 实例
const request = axios.create({
  baseURL: '/',                       // 根路径：请求仍是 /api/users/login，由 Vite 代理转发到网关 8080
  // 注意：不能设成 '/api'，否则和 api 模块里的 '/api' 前缀叠成 /api/api/xxx 404
  timeout: 10000,                     // 默认 10 秒超时；AI 接口单独放宽（chatAI 传 30 秒）
})

// 请求拦截器：每次发请求前，自动带上登录 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：把后端返回的 {code, message, data} 统一拆开
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 0) {
      return res.data          // 成功：只把 data 交给调用方
    }
    alert(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    alert('网络错误，请稍后再试')
    return Promise.reject(error)
  }
)

export default request