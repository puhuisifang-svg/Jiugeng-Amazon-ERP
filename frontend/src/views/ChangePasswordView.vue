<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { changePassword } from '@/api/auth'
import { ApiError } from '@/api/client'
import { session } from '@/state/session'

const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirmation = ref('')
const loading = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  if (newPassword.value !== confirmation.value) {
    errorMessage.value = '两次输入的新密码不一致。'
    return
  }
  loading.value = true
  try {
    await changePassword(currentPassword.value, newPassword.value)
    await session.refresh()
    await router.replace('/home')
  }
  catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '修改密码失败，请稍后重试。'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main class="login-shell">
    <section class="login-card" aria-labelledby="change-password-title">
      <span class="brand__mark">九</span>
      <p class="eyebrow">账号安全</p>
      <h1 id="change-password-title">修改密码</h1>
      <p>新密码至少 12 位。完成后即可进入系统。</p>
      <form class="login-form" @submit.prevent="submit">
        <label for="current-password">当前密码</label>
        <input id="current-password" v-model="currentPassword" autocomplete="current-password" required type="password">
        <label for="new-password">新密码</label>
        <input id="new-password" v-model="newPassword" autocomplete="new-password" minlength="12" maxlength="128" required type="password">
        <label for="confirm-password">确认新密码</label>
        <input id="confirm-password" v-model="confirmation" autocomplete="new-password" minlength="12" maxlength="128" required type="password">
        <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
        <button :disabled="loading" type="submit">{{ loading ? '正在保存…' : '保存新密码' }}</button>
      </form>
    </section>
  </main>
</template>
