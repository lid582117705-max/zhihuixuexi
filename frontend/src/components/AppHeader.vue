<template>
  <header class="header">
    <div class="brand" @click="goHome">📚 博学优课</div>

    <nav class="nav">
      <router-link to="/" class="nav-link" :class="{ active: active === 'store' }">课程商城</router-link>
      <router-link to="/mine" class="nav-link" :class="{ active: active === 'mine' }">我的课程</router-link>
    </nav>

    <div class="header-right">
      <span class="welcome">你好，{{ nickname }}</span>
      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { logout } from '../api/auth'

// 当前在哪个页面就高亮哪个入口：'store' 商城 / 'mine' 我的课程 / '' 都不高亮（如学习页）
defineProps({
  active: { type: String, default: '' },
})

const router = useRouter()

const nickname = computed(() => {
  const raw = localStorage.getItem('user')
  if (!raw) return '同学'
  try {
    const user = JSON.parse(raw)
    return user.nickname || user.username
  } catch (e) {
    return '同学'
  }
})

function goHome() {
  router.push('/')
}

async function handleLogout() {
  try {
    await logout() // 通知后端删掉 Redis 里的会话
  } catch (e) {
    // 后端失败也照常本地退出
  }
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 14px 32px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.brand {
  font-size: 20px;
  font-weight: 700;
  color: #3b82f6;
  cursor: pointer;
}
.nav {
  display: flex;
  gap: 18px;
}
.nav-link {
  font-size: 15px;
  color: #666;
  text-decoration: none;
  padding-bottom: 4px;
}
.nav-link.active {
  color: #3b82f6;
  font-weight: 700;
  border-bottom: 2px solid #3b82f6;
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.welcome { font-size: 14px; color: #666; }
.logout-btn {
  padding: 6px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #fff;
  color: #666;
  cursor: pointer;
  font-size: 13px;
}
.logout-btn:hover { color: #ef4444; border-color: #ef4444; }
</style>