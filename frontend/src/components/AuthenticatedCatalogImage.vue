<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { catalogApi } from '@/api/catalog'
import { getSessionToken } from '@/api/client'
import { evictCachedImage, IMAGE_SESSION_CHANGED } from '@/utils/catalogImageCache'
const props = withDefaults(defineProps<{ imageId: string | undefined; variant?: 'thumbnail' | 'original'; alt: string }>(), { variant: 'thumbnail' })
const host = ref<HTMLElement>(), source = ref(''), failed = ref(false), revision = ref(0)
const visible = ref(props.variant === 'original' || typeof IntersectionObserver === 'undefined')
let observer: IntersectionObserver | undefined, controller: AbortController | undefined, blocked = false
function retry() { if (props.imageId) evictCachedImage(getSessionToken() ?? '', `${props.imageId}/${props.variant}`); revision.value++ }
function revoke() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
async function changed() {
  controller?.abort(); revoke(); blocked = true
  await nextTick(); blocked = !getSessionToken(); revision.value++
}
watch(() => [props.imageId, props.variant, visible.value, revision.value] as const, async ([id, variant, inView], _old, onCleanup) => {
  revoke(); failed.value = false
  if (!id || (!inView && variant !== 'original') || blocked) return
  const current = new AbortController(); controller = current
  onCleanup(() => current.abort())
  try { const blob = await catalogApi.imageBlob(id, variant, current.signal); if (!current.signal.aborted) source.value = URL.createObjectURL(blob) }
  catch { if (!current.signal.aborted) failed.value = true }
}, { immediate: true })
onMounted(() => {
  if (!visible.value && host.value) {
    observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting)) { visible.value = true; observer?.disconnect() } }, { rootMargin: '200px' })
    observer.observe(host.value)
  }
  window.addEventListener(IMAGE_SESSION_CHANGED, changed)
  window.addEventListener('jiugeng:session-expired', changed)
  window.addEventListener('storage', changed)
})
onBeforeUnmount(() => {
  observer?.disconnect(); controller?.abort(); revoke()
  window.removeEventListener(IMAGE_SESSION_CHANGED, changed)
  window.removeEventListener('jiugeng:session-expired', changed)
  window.removeEventListener('storage', changed)
})
</script>

<template>
  <span
    v-if="!imageId || failed"
    ref="host"
    class="catalog-image catalog-image--placeholder"
    :role="failed ? undefined : 'img'"
    :aria-label="failed ? undefined : `${alt}（无可用图片）`"
  >
    <span
      v-if="failed && imageId"
      role="button"
      tabindex="0"
      :aria-label="`重新加载${alt}`"
      @click.stop="retry"
      @keydown.enter.stop.prevent="retry"
      @keydown.space.stop.prevent="retry"
    >加载失败 · 重试</span>
    <template v-else>图</template>
  </span>
  <img
    v-else-if="source"
    ref="host"
    class="catalog-image"
    :src="source"
    :alt="alt"
    loading="lazy"
    decoding="async"
    @error="failed = true"
  >
  <span
    v-else
    ref="host"
    class="catalog-image catalog-image--loading"
    role="status"
    aria-label="图片加载中"
  >…</span>
</template>
