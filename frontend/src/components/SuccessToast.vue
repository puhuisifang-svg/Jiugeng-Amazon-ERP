<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ message: string; tone?: 'success' | 'error' }>(), { tone: 'success' })
const visible = ref(false)
const toast = ref<HTMLElement>()
const topLayer = ref(false)
async function showTopLayer() {
  await nextTick()
  if (topLayer.value && visible.value && toast.value?.isConnected) toast.value.showPopover()
}
let timer: ReturnType<typeof setTimeout> | undefined
watch(() => props.message, message => {
  clearTimeout(timer)
  visible.value = Boolean(message)
  void showTopLayer()
  if (message) timer = setTimeout(() => { visible.value = false }, 4000)
}, { immediate: true })
onMounted(() => {
  topLayer.value = typeof HTMLElement.prototype.showPopover === 'function'
  void showTopLayer()
})
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div
    v-if="visible"
    ref="toast"
    :popover="topLayer ? 'manual' : undefined"
    class="success-toast"
    :class="{ 'success-toast--error': tone === 'error' }"
    role="status"
    aria-live="polite"
  >
    <span aria-hidden="true">{{ tone === 'error' ? '!' : '✓' }}</span>
    <span>{{ message }}</span>
    <button
      type="button"
      aria-label="关闭提示"
      @click="visible = false"
    >
      ×
    </button>
  </div>
</template>

<style scoped>
.success-toast[popover] { margin: 0; bottom: auto; right: auto; }
.success-toast.success-toast--error { background: #fff4f2; color: #a53232; border-color: #efc4c4; }
.success-toast { position: fixed; z-index: 10000; top: 24px; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 10px; width: max-content; max-width: calc(100vw - 32px); padding: 12px 18px; border: 1px solid #b6dec7; border-radius: 8px; background: #f0fff5; color: #216941; box-shadow: 0 6px 24px #183c2826; font-size: 14px; overflow-wrap: anywhere; }
.success-toast button { flex: none; min-height: 24px; height: 24px; padding: 0 4px; border: 0; background: transparent; color: inherit; font-size: 20px; cursor: pointer; }
</style>
