<script setup lang="ts">
import { ref, watch } from 'vue'
import { drawPdfText, measurePdfText, type PdfText } from '@/utils/pdfTextLayout'
const props = defineProps<{ layer: PdfText; scale: number }>()
const canvas = ref<HTMLCanvasElement>()
watch(() => [canvas.value, props.layer.text, props.layer.fontSize, props.layer.color, props.scale], async (_, _old, onCleanup) => {
  let cancelled = false; onCleanup(() => { cancelled = true })
  await document.fonts?.ready
  if (cancelled || !canvas.value) return
  const ctx = canvas.value.getContext('2d')
  if (!ctx) return
  const layer = { ...props.layer, fontSize: Number.isFinite(props.layer.fontSize) ? Math.max(6, Math.min(120, props.layer.fontSize)) : 16 }
  const size = measurePdfText(ctx, layer), density = Math.min(3, 4000 / Math.max(size.width, size.height))
  canvas.value.width = Math.ceil(size.width * density); canvas.value.height = Math.ceil(size.height * density)
  canvas.value.style.width = `${size.width * props.scale}px`; canvas.value.style.height = `${size.height * props.scale}px`
  ctx.scale(density, density); drawPdfText(ctx, layer)
}, { immediate: true, flush: 'post' })
</script>
<template>
  <canvas
    ref="canvas"
    class="pdf-text-canvas"
    aria-hidden="true"
  />
</template>
<style scoped>.pdf-text-canvas { display:block; pointer-events:none; }</style>
