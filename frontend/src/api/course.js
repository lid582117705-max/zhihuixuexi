import request from './request'

// 课程列表：GET /api/courses（后端已放行，未登录也能浏览）
// 后端这一层做了 Cache Aside 缓存：先查 Redis，没命中再查 MySQL 并把结果写回缓存
export function listCourses() {
  return request.get('/api/courses')
}

// 课程详情：GET /api/courses/{id}（同样走 Redis 缓存）
export function getCourse(id) {
  return request.get(`/api/courses/${id}`)
}