import { createRouter, createWebHistory } from 'vue-router'
import StoreView from '../views/StoreView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import OrderView from '../views/OrderView.vue'
import MineView from '../views/MineView.vue'
import LearnView from '../views/LearnView.vue'

const routes = [
  { path: '/', component: StoreView },          // 课程商城（首页）
  { path: '/order/:id', component: OrderView }, // 订单详情 + 模拟支付
  { path: '/mine', component: MineView },            // 我的课程（已购）
  { path: '/learn/:courseId', component: LearnView }, // 学习对话页
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：前端版的"登录拦截器"（和后端 AuthInterceptor 一个思路）
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  // 登录/注册页：已经登录就直接回首页
  if (to.path === '/login' || to.path === '/register') {
    return token ? '/' : true
  }
  // 其他页面：没有 token 就踢回登录页
  if (!token) {
    return '/login'
  }
  return true
})

export default router