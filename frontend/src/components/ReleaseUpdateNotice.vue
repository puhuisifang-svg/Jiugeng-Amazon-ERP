<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { hasNewRelease, moduleEntry } from '@/ui/releaseVersion'
const changed = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
let checking = false
let current: string | null = null
let mounted = false
async function check() {
  if (!current || checking || changed.value || document.visibilityState === 'hidden') return
  checking = true
  try { if (await hasNewRelease(current) && mounted) changed.value = true }
  catch { /* A temporary network failure is not evidence of a new release. */ }
  finally { checking = false }
}
function preloadFailure(event: Event) {
  event.preventDefault()
  changed.value = true
}
function reload() {
  if (window.confirm('请确认当前填写内容已保存。刷新将加载最新版本，未保存内容会丢失。')) window.location.reload()
}
onMounted(() => {
  mounted = true
  if (!import.meta.env.PROD) return
  current = moduleEntry(document.documentElement.outerHTML, window.location.origin)
  void check()
  timer = setInterval(() => { void check() }, 60000)
  window.addEventListener('focus', check)
  window.addEventListener('vite:preloadError', preloadFailure)
  document.addEventListener('visibilitychange', check)
})
onBeforeUnmount(() => {
  mounted = false
  clearInterval(timer)
  window.removeEventListener('focus', check)
  window.removeEventListener('vite:preloadError', preloadFailure)
  document.removeEventListener('visibilitychange', check)
})
</script>
<template>
  <aside
    v-if="changed"
    class="release-update-notice"
    role="status"
  >
    <strong>系统版本已更新或页面资源已失效</strong>
    <span>请先保存当前内容，再刷新加载最新功能。</span>
    <button
      type="button"
      @click="reload"
    >
      保存后刷新
    </button>
  </aside>
</template>
<style scoped>
.release-update-notice { position: fixed; right: 20px; bottom: 20px; z-index: 10001; display: grid; gap: 8px; max-width: min(360px, calc(100vw - 40px)); padding: 16px; border: 1px solid #b6dec7; border-radius: 10px; background: #f0fff5; color: #216941; box-shadow: 0 6px 24px #183c2826; }
button { justify-self: start; cursor: pointer; padding: 6px 12px; border: 1px solid #216941; border-radius: 6px; background: white; color: inherit; }
</style>
