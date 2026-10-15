<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { securitySettingsApi } from '@/api/auth'
import { runtimeConfig } from '@/config/runtime'
import type { AppRole } from '@/router/meta'
import { session } from '@/state/session'
import PasswordField from '@/components/PasswordField.vue'

const router = useRouter()
const route = useRoute()
const role = ref<AppRole>('ADMIN')
const loginName = ref('')
const password = ref('')
const staySignedIn = ref(false)
const staySignedInDays = ref(7)
const loading = ref(false)
const errorMessage = ref('')

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    await session.login(runtimeConfig.authMode === 'mock'
      ? { role: role.value }
      : { loginName: loginName.value, password: password.value, staySignedIn: staySignedIn.value })
    if (session.user.value?.mustChangePassword) {
      await router.replace('/change-password')
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.replace(redirect)
  }
  catch (error) {
    errorMessage.value = error instanceof ApiError
      ? error.message
      : runtimeConfig.authMode === 'mock'
        ? '模拟登录失败，请确认本地后端已使用 mock profile 启动。'
        : '登录失败，请稍后重试。'
  }
  finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (runtimeConfig.authMode === 'mock') return
  try {
    staySignedInDays.value = (await securitySettingsApi.loginSettings()).staySignedInDays
  }
  catch {
    // 登录仍可继续，后端会使用当前生效的安全策略。
  }
})
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main class="login-shell">
    <section class="login-card" aria-labelledby="login-title">
      <span class="brand__mark">九</span>
      <p class="eyebrow">九更亚马逊 ERP</p>
      <h1 id="login-title">账号登录</h1>
      <div v-if="runtimeConfig.authMode === 'mock'" class="notice login-notice">
        <strong>SYNTHETIC</strong><span>本地模拟模式，不使用真实账号或密码。</span>
      </div>
      <p v-else>使用内部账号登录。连续失败会锁定账号，首次登录必须修改初始密码。</p>
      <form class="login-form" @submit.prevent="submit">
        <template v-if="runtimeConfig.authMode === 'mock'">
          <label for="role">选择模拟角色</label>
          <select id="role" v-model="role">
            <option value="ADMIN">管理员（ADMIN）</option>
            <option value="PRODUCT_MATERIAL">产品与辅料（PRODUCT_MATERIAL）</option>
            <option value="SELECTION">选品（SELECTION）</option>
          </select>
        </template>
        <template v-else>
          <label for="login-name">登录名</label>
          <input id="login-name" v-model.trim="loginName" autocomplete="username" maxlength="128" required>
          <label for="password">密码</label>
          <PasswordField id="password" v-model="password" autocomplete="current-password" required />
          <div class="login-options">
            <label class="checkbox-label" for="stay-signed-in">
              <input id="stay-signed-in" v-model="staySignedIn" type="checkbox">
              {{ staySignedInDays }} 天内免登录
            </label>
            <RouterLink class="form-link" to="/forgot-password">忘记密码？</RouterLink>
          </div>
        </template>
        <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
        <button :disabled="loading" type="submit">
          {{ loading ? '正在登录…' : runtimeConfig.authMode === 'mock' ? '进入模拟系统' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>
