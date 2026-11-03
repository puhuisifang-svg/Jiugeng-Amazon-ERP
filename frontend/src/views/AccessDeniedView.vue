<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { session } from '@/state/session'
const router = useRouter()
const busy = ref(false)
const message = ref('')
const noPermissions = computed(() => !session.user.value?.capabilities.length)
async function refresh() {
  busy.value = true
  message.value = ''
  try {
    await session.refresh()
    if (noPermissions.value) message.value = '暂未分配功能权限，请联系管理员。'
    else await router.replace('/home')
  }
  catch (error) {
    if (error instanceof ApiError && error.status === 401) { session.clear(); await router.replace('/login') }
    else message.value = '刷新失败，请重新登录后重试。'
  }
  finally { busy.value = false }
}
async function logout() {
  try { await session.logout() }
  finally { await router.replace('/login') }
}
</script>
<template>
  <main class="access-denied-page">
    <section
      class="panel"
      aria-labelledby="access-title"
    >
      <p class="eyebrow">
        账号权限
      </p>
      <h1 id="access-title">
        {{ noPermissions ? '暂无功能权限' : '无权访问此页面' }}
      </h1>
      <p>{{ noPermissions ? '账号已登录，请联系管理员分配角色。分配完成后请刷新权限；登录失效时，请重新登录。' : '当前角色未获此页面的访问权限，请联系管理员或返回可用功能。' }}</p>
      <p
        v-if="message"
        role="status"
      >
        {{ message }}
      </p>
      <div class="access-actions">
        <button
          class="primary-button"
          :disabled="busy"
          @click="refresh"
        >
          {{ busy ? '刷新中…' : '刷新权限' }}
        </button>
        <button @click="logout">
          退出登录
        </button>
      </div>
    </section>
  </main>
</template>
<style scoped>
.access-denied-page { min-height: 100vh; display: grid; place-items: center; padding: 16px; box-sizing: border-box; }
.panel { width: min(100%, 500px); box-sizing: border-box; padding: 24px; }
h1 { font-size: 20px; margin: 8px 0; }
p { line-height: 1.7; }
.access-actions { display: flex; gap: 8px; margin-top: 16px; }
</style>
