<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { passwordRecoveryApi, type RecoveryChannel } from '@/api/auth'
import { ApiError } from '@/api/client'
import PasswordField from '@/components/PasswordField.vue'

const router = useRouter()
const channel = ref<RecoveryChannel>('EMAIL')
const destination = ref('')
const code = ref('')
const newPassword = ref('')
const confirmation = ref('')
const requested = ref(false)
const loading = ref(false)
const message = ref('')
const errorMessage = ref('')

async function requestCode() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await passwordRecoveryApi.request({ channel: channel.value, destination: destination.value })
    requested.value = true
    message.value = response.message
  }
  catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '验证码发送失败，请稍后重试。'
  }
  finally {
    loading.value = false
  }
}

async function resetPassword() {
  errorMessage.value = ''
  if (newPassword.value !== confirmation.value) {
    errorMessage.value = '两次输入的新密码不一致。'
    return
  }
  loading.value = true
  try {
    await passwordRecoveryApi.confirm({
      channel: channel.value,
      destination: destination.value,
      code: code.value,
      newPassword: newPassword.value,
    })
    await router.replace({ path: '/login', query: { reset: 'success' } })
  }
  catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '密码重置失败，请稍后重试。'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <main class="login-shell">
    <section class="login-card" aria-labelledby="forgot-password-title">
      <span class="brand__mark">九</span>
      <p class="eyebrow">账号安全</p>
      <h1 id="forgot-password-title">忘记密码</h1>
      <p>使用账号已绑定的邮箱或手机号接收 6 位验证码。</p>
      <form class="login-form" @submit.prevent="requested ? resetPassword() : requestCode()">
        <label for="recovery-channel">验证方式</label>
        <select id="recovery-channel" v-model="channel" :disabled="requested">
          <option value="EMAIL">邮箱验证码</option>
          <option value="SMS">手机短信验证码</option>
        </select>
        <label for="recovery-destination">{{ channel === 'EMAIL' ? '邮箱' : '手机号' }}</label>
        <input id="recovery-destination" v-model.trim="destination" :disabled="requested" maxlength="254" required>
        <template v-if="requested">
          <label for="verification-code">验证码</label>
          <input id="verification-code" v-model.trim="code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" pattern="\\d{6}" required>
          <label for="recovery-password">新密码</label>
          <PasswordField id="recovery-password" v-model="newPassword" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\\d).{6,128}" required />
          <label for="recovery-confirmation">确认新密码</label>
          <PasswordField id="recovery-confirmation" v-model="confirmation" autocomplete="new-password" :minlength="6" pattern="(?=.*[A-Za-z])(?=.*\\d).{6,128}" required />
        </template>
        <SuccessToast :message="message" />
        <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
        <button :disabled="loading" type="submit">{{ loading ? '正在处理…' : requested ? '重置密码' : '发送验证码' }}</button>
        <button v-if="requested" class="secondary-button" type="button" @click="requested = false; message = ''">重新填写</button>
        <RouterLink class="form-link" to="/login">返回登录</RouterLink>
      </form>
    </section>
  </main>
</template>
