<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { RenderTask } from 'pdfjs-dist/legacy/build/pdf.mjs'
import { acquirePdfPreview, withPdfRenderSlot } from '@/utils/pdfPreviewPool'
const props = withDefaults(defineProps<{ bytes: Uint8Array; page: number; thumbnail?: boolean; lazy?: boolean; size?: { width: number; height: number } | undefined }>(), { thumbnail: false, lazy: false, size: undefined })
const emit = defineEmits<{ dimensions: [size: { width: number; height: number }] }>()
const host = ref<HTMLElement>(), canvas = ref<HTMLCanvasElement>(), error = ref(''), loading = ref(false), retry = ref(0)
const visible = ref(!(props.thumbnail || props.lazy) || typeof IntersectionObserver === 'undefined')
let observer: IntersectionObserver | undefined
onMounted(() => {
  if ((props.thumbnail || props.lazy) && typeof IntersectionObserver !== 'undefined' && host.value) {
    observer = new IntersectionObserver(entries => { visible.value = entries.some(entry => entry.isIntersecting) }, { rootMargin: '100px' })
    observer.observe(host.value)
  }
})
// Keep one lease across page changes; other thumbnail components share this same task.
let lease: ReturnType<typeof acquirePdfPreview> | undefined, leasedBytes: Uint8Array | undefined
watch(() => [props.bytes, props.page, props.thumbnail, visible.value, retry.value] as const, async (_, _old, onCleanup) => {
  const controller = new AbortController()
  let renderTask: RenderTask | undefined
  onCleanup(() => { controller.abort(); renderTask?.cancel() })
  error.value = ''; loading.value = visible.value
  if (!visible.value) {
    lease?.release(); lease = undefined; leasedBytes = undefined
    if (canvas.value) { canvas.value.width = 0; canvas.value.height = 0 }
    return
  }
  let localLease = lease
  try {
    if (leasedBytes !== props.bytes || !lease) {
      lease?.release(); lease = undefined; leasedBytes = undefined
      lease = acquirePdfPreview(props.bytes); leasedBytes = props.bytes
    }
    localLease = lease
    const doc = await localLease.document
    controller.signal.throwIfAborted()
    await withPdfRenderSlot(controller.signal, async () => {
      const page = await doc.getPage(props.page + 1)
      controller.signal.throwIfAborted()
      const viewport = page.getViewport({ scale: 1 })
      emit('dimensions', { width: viewport.width, height: viewport.height })
      const scaled = page.getViewport({ scale: Math.min(2, (props.thumbnail ? 360 : 1400) / Math.max(viewport.width, viewport.height)) })
      const buffer = document.createElement('canvas'); buffer.width = Math.ceil(scaled.width); buffer.height = Math.ceil(scaled.height)
      try {
        renderTask = page.render({ canvas: buffer, viewport: scaled })
        await renderTask.promise
        if (controller.signal.aborted || !canvas.value) return
        canvas.value.width = buffer.width; canvas.value.height = buffer.height
        canvas.value.getContext('2d')?.drawImage(buffer, 0, 0)
      } finally { buffer.width = 0; buffer.height = 0; page.cleanup() }
    })
  } catch {
    if (!controller.signal.aborted) {
      error.value = '当前页预览失败，文件和编辑内容已保留'
      if (lease && lease === localLease) { lease.release(); lease = undefined; leasedBytes = undefined }
    }
  } finally { if (!controller.signal.aborted) loading.value = false }
}, { immediate: true, flush: 'post' })
onBeforeUnmount(() => { observer?.disconnect(); lease?.release() })
</script>
<template>
  <div
    ref="host"
    class="pdf-preview"
    :class="{ 'is-pending': loading || !!error }"
    :aria-busy="loading"
    :style="size ? { aspectRatio: `${size.width} / ${size.height}` } : undefined"
  >
    <canvas
      ref="canvas"
      aria-label="PDF页面预览"
    />
    <span
      v-if="loading"
      class="preview-notice"
    >正在生成预览…</span>
    <span
      v-if="error"
      class="preview-notice"
      role="alert"
    >
      {{ error }}
      <button
        type="button"
        @click.stop="retry++"
      >重试预览</button>
    </span>
    <slot />
  </div>
</template>
<style scoped>
.pdf-preview { position:relative; width:100%; background:white; box-shadow:0 2px 14px #1e293b16; }
.pdf-preview.is-pending { min-height:80px; }
canvas { display:block; width:100%; height:auto; }
.pdf-preview[style] canvas { height:100%; }
.preview-notice { position:absolute; inset:0; display:grid; place-content:center; gap:8px; padding:8px; background:#ffffffe8; font-size:13px; color:#64748b; text-align:center; }
.preview-notice button { justify-self:center; padding:4px 10px; color:var(--accent); background:white; border:1px solid var(--line); border-radius:4px; cursor:pointer; }
</style>
