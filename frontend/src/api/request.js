import axios from 'axios'

// 创建一个"统一出口"的 axios 实例
const request = axios.create({
  baseURL: 'http://localhost:8081',   // 后端地址
  timeout: 5000,                      // 5 秒超时
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