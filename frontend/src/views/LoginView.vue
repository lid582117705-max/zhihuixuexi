<template>
  <div class="login-page">
    <div class="login-card">
      <h2>博学优课</h2>
      <p class="subtitle">登录你的账号</p>
      <form @submit.prevent="handleLogin">
        <input v-model="form.username" placeholder="用户名" class="input" />
        <input v-model="form.password" type="password" placeholder="密码" class="input" />
        <button type="submit" class="btn" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
      <p class="tip">还没有账号？<router-link to="/register">去注册</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    alert('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // 拦截器成功时只返回 data，所以这里直接拿到 { token, user }
    const data = await login({ ...form })
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    alert(`欢迎回来，${data.user.nickname || data.user.username}`)
    router.push('/')
  } catch (e) {
    // 错误提示已被响应拦截器弹过了，这里不用再处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6fa;
}
.login-card {
  width: 340px;
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}
h2 { margin: 0 0 4px; }
.subtitle { color: #999; margin: 0 0 20px; font-size: 14px; }
.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
}
.btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.btn:disabled { opacity: 0.6; }
.tip { margin-top: 16px; font-size: 13px; color: #666; text-align: center; }
.tip a { color: #3b82f6; }
</style>