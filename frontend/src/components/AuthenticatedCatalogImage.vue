<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { catalogApi } from '@/api/catalog'
import { runtimeConfig } from '@/config/runtime'
import { ApiError, getSessionToken } from '@/api/client'
import { evictCachedImage, peekCachedImage, promoteCachedImage, IMAGE_SESSION_CHANGED, type ImagePriority } from '@/utils/catalogImageCache'
const props = withDefaults(defineProps<{ imageId?: string | null | undefined; imageUrl?: string | null | undefined; draftPermission?: string | undefined; variant?: 'thumbnail' | 'original'; alt: string }>(), { variant: 'thumbnail', imageId: undefined, imageUrl: undefined, draftPermission: undefined })
const host = ref<HTMLElement>(), source = ref(''), failed = ref(false), previewFailed = ref(false), revision = ref(0)
const visible = ref(props.variant === 'original' || typeof IntersectionObserver === 'undefined')
const priority = ref<ImagePriority>(props.variant === 'original' ? 'preview' : 'prefetch')
const imageKey = computed(() => props.imageId || (props.imageUrl ? `external:${props.imageUrl}` : ''))
let syncRetries = 0
let observer: IntersectionObserver | undefined, inViewObserver: IntersectionObserver | undefined, controller: AbortController | undefined, blocked = false
watch(imageKey, () => { syncRetries = 0 })
function retry() { syncRetries = 0; if (imageKey.value) evictCachedImage(getSessionToken() ?? '', `${imageKey.value}/${props.variant}`); revision.value++ }
function revoke() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
async function changed() {
  controller?.abort(); revoke(); blocked = true
  await nextTick(); blocked = !getSessionToken(); revision.value++
}
watch(() => [imageKey.value, props.variant, visible.value, revision.value] as const, async ([id, variant, inView], _old, onCleanup) => {
  revoke(); failed.value = false; previewFailed.value = false
  if (!id || (!inView && variant !== 'original') || blocked) return
  // The retained mock API has no controlled-image mirror endpoint.
  if (!props.imageId && runtimeConfig.authMode === 'mock' && /^https?:\/\//i.test(props.imageUrl ?? '')) { source.value = props.imageUrl!; return }
  const current = new AbortController(); controller = current
  onCleanup(() => current.abort())
  if (variant === 'original') {
    const small = peekCachedImage(getSessionToken() ?? '', `${id}/thumbnail`)
    if (small) source.value = URL.createObjectURL(small)
  }
  try {
    const level = variant === 'original' ? 'preview' : priority.value
    const blob = props.imageId ? await catalogApi.imageBlob(props.imageId, variant, current.signal, level)
      : await catalogApi.externalImageBlob(props.imageUrl!, variant, current.signal, level, props.draftPermission)
    if (!current.signal.aborted) { revoke(); source.value = URL.createObjectURL(blob) }
  } catch (error) {
    if (!current.signal.aborted && error instanceof ApiError && error.status === 503 && syncRetries < 6) {
      const timer = window.setTimeout(() => { if (!current.signal.aborted) revision.value++ }, 2000 * ++syncRetries)
      current.signal.addEventListener('abort', () => clearTimeout(timer), { once: true })
    } else if (!current.signal.aborted) { if (source.value) previewFailed.value = true; else failed.value = true }
  }
}, { immediate: true })
onMounted(() => {
  if (typeof IntersectionObserver !== 'undefined' && props.variant !== 'original') {
    observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting)) visible.value = true }, { rootMargin: '100px' })
    inViewObserver = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting)) {
      priority.value = 'visible'; visible.value = true
      promoteCachedImage(getSessionToken() ?? '', `${imageKey.value}/${props.variant}`, 'visible')
    } })
    if (host.value) { observer.observe(host.value); inViewObserver.observe(host.value) }
  }
  window.addEventListener(IMAGE_SESSION_CHANGED, changed)
  window.addEventListener('jiugeng:session-expired', changed)
  window.addEventListener('storage', storageChanged)
})
watch(host, (value, old) => { if (old) { observer?.unobserve(old); inViewObserver?.unobserve(old) } if (value) { observer?.observe(value); inViewObserver?.observe(value) } })
function storageChanged(event: StorageEvent) { if (!event.key || event.key === 'jiugeng.session.token') void changed() }
onBeforeUnmount(() => {
  observer?.disconnect(); inViewObserver?.disconnect(); controller?.abort(); revoke()
  window.removeEventListener(IMAGE_SESSION_CHANGED, changed)
  window.removeEventListener('jiugeng:session-expired', changed)
  window.removeEventListener('storage', storageChanged)
})
</script>

<template>
  <span
    v-if="!imageKey || failed"
    ref="host"
    class="catalog-image catalog-image--placeholder"
    :role="failed ? undefined : 'img'"
    :aria-label="failed ? undefined : `${alt}（无可用图片）`"
  >
    <span
      v-if="failed && imageKey"
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
    :loading="variant === 'original' ? 'eager' : 'lazy'"
    :title="previewFailed ? '高清图加载失败，点击重试' : undefined"
    decoding="async"
    @click="previewFailed && retry()"
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
