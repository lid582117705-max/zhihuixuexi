import request from './request'

// 下单：body 只传 courseId，后端返回完整订单
// 下单成功后端会往 RabbitMQ 延时队列发一条消息，15 秒后检查订单是否已支付
export function createOrder(courseId) {
  return request.post('/api/orders', { courseId })
}

// 订单详情：只能查自己的订单（后端做过归属校验）
export function getOrder(id) {
  return request.get(`/api/orders/${id}`)
}

// 模拟支付：真实项目里这里是支付平台回调通知，学习项目里点按钮直接置为已支付
export function payOrder(id) {
  return request.post(`/api/orders/${id}/pay`)
}
// 我的已购课程：后端把"已支付订单 + 课程信息"组装好再返回
export function getMyCourses() {
  return request.get('/api/orders/mine')
}