<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getDocument, GlobalWorkerOptions, type PDFDocumentLoadingTask } from 'pdfjs-dist/legacy/build/pdf.mjs'
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'
GlobalWorkerOptions.workerSrc = workerUrl
const props = defineProps<{ bytes: Uint8Array; page: number }>()
const emit = defineEmits<{ dimensions: [size: { width: number; height: number }] }>()
const canvas = ref<HTMLCanvasElement>(), error = ref(''), loading = ref(false)
let generation = 0, task: PDFDocumentLoadingTask | undefined, loadedBytes: Uint8Array | undefined
watch(() => [props.bytes, props.page] as const, async () => {
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    if (loadedBytes !== props.bytes) {
      const previous = task
      loadedBytes = props.bytes
      task = getDocument({ data: props.bytes.slice(), useSystemFonts: true, cMapUrl: '/pdf-assets/cmaps/', cMapPacked: true, standardFontDataUrl: '/pdf-assets/standard_fonts/', wasmUrl: '/pdf-assets/wasm/' })
      void previous?.destroy()
    }
    const local = await task!.promise
    if (current !== generation) return
    const page = await local.getPage(props.page + 1), viewport = page.getViewport({ scale: 1 })
    emit('dimensions', { width: viewport.width, height: viewport.height })
    const scaled = page.getViewport({ scale: Math.min(2, 1400 / Math.max(viewport.width, viewport.height)) })
    // Render offscreen to avoid concurrent renders on the visible canvas.
    const buffer = document.createElement('canvas'); buffer.width = Math.ceil(scaled.width); buffer.height = Math.ceil(scaled.height)
    await page.render({ canvas: buffer, viewport: scaled }).promise
    if (current !== generation || !canvas.value) return
    canvas.value.width = buffer.width; canvas.value.height = buffer.height
    canvas.value.getContext('2d')?.drawImage(buffer, 0, 0)
  } catch { if (current === generation) error.value = '预览失败，请重新选择有效PDF文件' }
  finally { if (current === generation) loading.value = false }
}, { immediate: true, flush: 'post' })
onBeforeUnmount(() => { generation++; void task?.destroy() })
</script>
<template>
  <div
    class="pdf-preview"
    :aria-busy="loading"
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
    >{{ error }}</span>
    <slot />
  </div>
</template>
<style scoped>
.pdf-preview { position:relative; width:100%; background:white; box-shadow:0 2px 14px #1e293b16; }
canvas { display:block; width:100%; height:auto; }
.preview-notice { position:absolute; inset:0; display:grid; place-content:center; background:#ffffffe8; font-size:13px; color:#64748b; }
</style>
