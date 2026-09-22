<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { AppRole } from '@/router/meta'
import { session } from '@/state/session'

const router = useRouter()
const route = useRoute()
const role = ref<AppRole>('ADMIN')
const loading = ref(false)
const errorMessage = ref('')

async function submit() {
  loading.value = true
  errorMessage.value = ''
  try {
    await session.login(role.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.replace(redirect)
  }
  catch {
    errorMessage.value = '模拟登录失败，请确认本地后端已使用 mock profile 启动。'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-shell">
    <section
      class="login-card"
      aria-labelledby="login-title"
    >
      <span class="brand__mark">九</span>
      <p class="eyebrow">
        九更亚马逊 ERP
      </p>
      <h1 id="login-title">
        账号登录
      </h1>
      <div class="notice login-notice">
        <strong>SYNTHETIC</strong><span>仅供本地 MVP 演示，不使用真实账号或密码。</span>
      </div>
      <form
        class="login-form"
        @submit.prevent="submit"
      >
        <label for="role">选择模拟角色</label>
        <select
          id="role"
          v-model="role"
        >
          <option value="ADMIN">
            管理员（ADMIN）
          </option>
          <option value="PRODUCT_MATERIAL">
            产品与辅料（PRODUCT_MATERIAL）
          </option>
          <option value="SELECTION">
            选品（SELECTION）
          </option>
        </select>
        <p
          v-if="errorMessage"
          class="data-state data-state--error"
          role="alert"
        >
          {{ errorMessage }}
        </p>
        <button
          :disabled="loading"
          type="submit"
        >
          {{ loading ? '正在进入…' : '进入模拟系统' }}
        </button>
      </form>
    </section>
  </main>
</template>
