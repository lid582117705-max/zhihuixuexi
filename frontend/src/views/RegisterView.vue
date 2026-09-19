<template>
  <div class="register-page">
    <div class="register-card">
      <h2>博学优课</h2>
      <p class="subtitle">注册一个新账号</p>
      <form @submit.prevent="handleRegister">
        <input v-model="form.username" placeholder="用户名（至少3位）" class="input" />
        <input v-model="form.password" type="password" placeholder="密码（至少6位）" class="input" />
        <input v-model="form.nickname" placeholder="昵称（选填）" class="input" />
        <button type="submit" class="btn" :disabled="loading">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
      </form>
      <p class="tip">已经有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'

const router = useRouter()
const form = reactive({ username: '', password: '', nickname: '' })
const loading = ref(false)

async function handleRegister() {
  if (!form.username || !form.password) {
    alert('用户名和密码不能为空')
    return
  }
  loading.value = true
  try {
    await register({ ...form })
    alert('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6fa;
}
.register-card {
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
  background: #10b981;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.btn:disabled { opacity: 0.6; }
.tip { margin-top: 16px; font-size: 13px; color: #666; text-align: center; }
.tip a { color: #3b82f6; }
</style>