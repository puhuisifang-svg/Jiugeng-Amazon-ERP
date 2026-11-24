<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComponentPublicInstance } from 'vue'
import PdfPagePreview from './PdfPagePreview.vue'
const props = withDefaults(defineProps<{ bytes: Uint8Array; sizes: { width: number; height: number }[]; page: number; busy: boolean; readonly?: boolean }>(), { readonly: false })
const emit = defineEmits<{ 'update:page': [page: number] }>()
const viewport = ref<HTMLElement>(), rail = ref<HTMLElement>(), availableWidth = ref(600), zoom = ref(1), thumbnails = ref(true)
const pageWidth = computed(() => Math.max(160, availableWidth.value - 32) * zoom.value)
const pages = new Map<number, HTMLElement>()
let resize: ResizeObserver | undefined, frame = 0, current = props.page
function setPageElement(index: number, element: Element | ComponentPublicInstance | null) {
  if (element instanceof HTMLElement) pages.set(index, element); else pages.delete(index)
}
function activate(index: number) { if (props.busy) return; current = index; emit('update:page', index) }
function navigate(index: number) {
  if (props.busy || !Number.isFinite(index)) return
  const target = Math.max(0, Math.min(props.sizes.length - 1, Math.trunc(index)))
  activate(target)
  const element = pages.get(target)
  if (viewport.value && element) viewport.value.scrollTop = element.offsetTop
}
function jump(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.value.trim()) navigate(Number(input.value) - 1)
  input.value = String(current + 1)
}
function syncPage() {
  if (frame || props.busy) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const container = viewport.value
    if (!container) return
    const anchor = container.scrollTop + Math.min(100, container.clientHeight / 4)
    let index = 0
    for (const [number, element] of pages) { if (element.offsetTop <= anchor) index = number; else break }
    // At the end, a short final page cannot be scrolled all the way to the top anchor.
    if (container.scrollTop > 0 && container.scrollHeight - container.scrollTop - container.clientHeight <= 2) index = props.sizes.length - 1
    if (index !== current) activate(index)
  })
}
watch(() => props.page, value => {
  if (value !== current) navigate(value)
  const button = rail.value?.querySelector<HTMLElement>(`[data-page="${value}"]`)
  if (button && rail.value) {
    if (button.offsetTop < rail.value.scrollTop) rail.value.scrollTop = button.offsetTop
    else if (button.offsetTop + button.offsetHeight > rail.value.scrollTop + rail.value.clientHeight) rail.value.scrollTop = button.offsetTop + button.offsetHeight - rail.value.clientHeight
  }
})
let zoomRevision = 0
async function setZoom(value: number, event?: WheelEvent) {
  if (props.busy) return
  const next = Math.max(.1, Math.min(2, Math.round(value * 100) / 100))
  if (next === zoom.value) return
  const container = viewport.value, element = (event?.target instanceof Element ? event.target.closest<HTMLElement>('.pdf-document-page') : undefined) || pages.get(props.page)
  const before = element?.getBoundingClientRect(), bounds = container?.getBoundingClientRect()
  const clientX = event?.clientX ?? (bounds ? bounds.left + bounds.width / 2 : 0)
  const clientY = event?.clientY ?? (bounds ? bounds.top + Math.min(100, bounds.height / 2) : 0)
  const x = before?.width ? (clientX - before.left) / before.width : 0
  const y = before?.height ? (clientY - before.top) / before.height : 0
  const revision = ++zoomRevision
  zoom.value = next
  await nextTick()
  if (revision !== zoomRevision || !container || !element || !before?.width) return
  const after = element.getBoundingClientRect()
  container.scrollLeft += after.left + x * after.width - clientX
  container.scrollTop += after.top + y * after.height - clientY
}
function zoomWithWheel(event: WheelEvent) {
  if (!event.ctrlKey && !event.metaKey) return
  event.preventDefault()
  if (event.deltaY) void setZoom(zoom.value + (event.deltaY < 0 ? .1 : -.1), event)
}
watch(() => props.bytes, async () => {
  zoomRevision++; zoom.value = 1; current = props.page
  await nextTick()
  if (viewport.value) viewport.value.scrollTop = pages.get(props.page)?.offsetTop || 0
})
onMounted(() => {
  if (viewport.value) {
    resize = new ResizeObserver(() => { availableWidth.value = viewport.value?.clientWidth || 600 })
    resize.observe(viewport.value); availableWidth.value = viewport.value.clientWidth || 600
    navigate(props.page)
  }
})
onBeforeUnmount(() => { resize?.disconnect(); cancelAnimationFrame(frame) })
</script>
<template>
  <div class="pdf-document">
    <div class="pdf-editor-toolbar">
      <div class="pdf-document-tools">
        <slot name="tools" />
      </div>
      <div
        class="pdf-document-navigation"
        aria-label="文档导航与缩放"
      >
        <button
          type="button"
          :aria-expanded="thumbnails"
          aria-controls="pdf-editor-thumbnails"
          title="显示或隐藏页面缩略图"
          @click="thumbnails = !thumbnails"
        >
          页面
        </button>
        <button
          type="button"
          aria-label="上一页"
          :disabled="busy || page === 0"
          @click="navigate(page - 1)"
        >
          ↑
        </button>
        <label class="pdf-page-number">第<input
          :value="page + 1"
          type="number"
          min="1"
          :max="sizes.length"
          :disabled="busy"
          :aria-label="readonly ? '预览页码' : '编辑页码'"
          @change="jump"
          @keydown.enter="jump"
        > / {{ sizes.length }} 页</label>
        <button
          type="button"
          aria-label="下一页"
          :disabled="busy || page === sizes.length - 1"
          @click="navigate(page + 1)"
        >
          ↓
        </button>
        <button
          type="button"
          aria-label="缩小页面"
          :disabled="busy || zoom <= .1"
          @click="setZoom(zoom - .25)"
        >
          −
        </button>
        <output aria-label="缩放比例">{{ Math.round(zoom * 100) }}%</output>
        <button
          type="button"
          aria-label="放大页面"
          :disabled="busy || zoom >= 2"
          @click="setZoom(zoom + .25)"
        >
          ＋
        </button>
        <button
          type="button"
          :disabled="busy"
          @click="setZoom(1)"
        >
          适合宽度
        </button>
      </div>
    </div>
    <div
      class="pdf-document-body"
      :class="{ 'without-thumbnails': !thumbnails }"
    >
      <nav
        v-show="thumbnails"
        id="pdf-editor-thumbnails"
        ref="rail"
        class="pdf-document-thumbnails"
        aria-label="PDF页面导航"
      >
        <button
          v-for="(size, index) in sizes"
          :key="index"
          :data-page="index"
          type="button"
          :disabled="busy"
          :aria-label="`前往第${index + 1}页`"
          :aria-current="page === index ? 'page' : undefined"
          @click="navigate(index)"
        >
          <PdfPagePreview
            :bytes="bytes"
            :page="index"
            :size="size"
            thumbnail
          />
          <span>{{ index + 1 }}</span>
        </button>
      </nav>
      <div
        ref="viewport"
        class="pdf-document-scroll"
        :aria-label="readonly ? '连续页面预览区' : '连续页面编辑区'"
        tabindex="0"
        @scroll.passive="syncPage"
        @wheel="zoomWithWheel"
      >
        <article
          v-for="(size, index) in sizes"
          :key="index"
          :ref="element => setPageElement(index, element)"
          class="pdf-document-page"
          :class="{ 'is-active': page === index }"
          :style="{ width: `${pageWidth}px` }"
          :aria-label="`${readonly ? '预览' : '编辑'}第${index + 1}页`"
          @pointerdown.capture="activate(index)"
          @focusin="activate(index)"
        >
          <div class="pdf-document-page-label">
            <span>第 {{ index + 1 }} 页</span><span v-if="page === index">{{ readonly ? '当前预览页' : '当前编辑页' }}</span>
          </div>
          <div
            class="pdf-stage"
            :style="{ aspectRatio: `${size.width} / ${size.height}` }"
          >
            <PdfPagePreview
              :bytes="bytes"
              :page="index"
              :size="size"
              lazy
            >
              <slot
                :page="index"
                :size="size"
                :width="pageWidth"
              />
            </PdfPagePreview>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>
<style scoped>
.pdf-document { display:flex; flex-direction:column; min-width:0; height:calc(100dvh - 224px); min-height:320px; }
.pdf-editor-toolbar { flex:none; display:flex; flex-wrap:wrap; align-items:center; gap:6px; padding:6px; background:#fff; border-bottom:1px solid var(--line,#dce3ec); z-index:2; }
.pdf-document-navigation { display:flex; flex-wrap:wrap; gap:4px; align-items:center; }
.pdf-document-tools { margin-right:auto; }
.pdf-document-navigation button { min-height:var(--ui-control-height,32px); min-width:32px; padding:4px 6px; border:1px solid var(--line,#dce3ec); border-radius:4px; background:white; color:var(--ink,#334155); cursor:pointer; }
.pdf-document-navigation button:disabled { opacity:.45; cursor:not-allowed; }
.pdf-page-number { display:flex; align-items:center; gap:3px; white-space:nowrap; font-size:12px; }
.pdf-page-number input { width:48px; min-width:0; height:32px; padding:3px; border:1px solid var(--line,#dce3ec); border-radius:4px; text-align:center; }
.pdf-document-navigation output { min-width:42px; text-align:center; font-size:12px; font-variant-numeric:tabular-nums; }
.pdf-document-body { display:grid; grid-template-columns:112px minmax(0,1fr); min-height:0; flex:1; }
.pdf-document-body.without-thumbnails { grid-template-columns:minmax(0,1fr); }
.pdf-document-thumbnails { position:relative; overflow:auto; padding:8px; background:#f7f9fc; border-right:1px solid var(--line,#dce3ec); overscroll-behavior:contain; }
.pdf-document-thumbnails button { display:grid; width:100%; gap:5px; padding:5px; margin:0 0 8px; background:transparent; border:2px solid transparent; border-radius:4px; color:#64748b; cursor:pointer; }
.pdf-document-thumbnails button[aria-current=page] { border-color:#4e88cf; background:#edf5ff; color:#2864c5; }
.pdf-document-thumbnails span { font-size:12px; }
.pdf-document-scroll { position:relative; min-width:0; min-height:0; overflow:auto; padding:0 16px 20px; background:#e9edf3; overscroll-behavior:contain; scroll-behavior:auto; overflow-anchor:none; }
.pdf-document-page { margin:0 auto 18px; padding-top:8px; }
.pdf-document-page-label { display:flex; justify-content:space-between; gap:6px; height:24px; color:#758298; font-size:11px; }
.pdf-document-page.is-active .pdf-document-page-label { color:#2864c5; }
.pdf-stage { overflow:hidden; position:relative; width:100%; background:white; box-shadow:0 2px 8px #25334a20; }
.pdf-stage :deep(.pdf-preview) { height:100%; }
.pdf-stage :deep(.pdf-layer) { max-width:100%; }
@media(max-width:1100px) { .pdf-document-body { grid-template-columns:88px minmax(0,1fr); } }
@media(max-width:850px) { .pdf-document { height:65dvh; min-height:320px; } }
@media(max-width:480px) { .pdf-document-body { grid-template-columns:64px minmax(0,1fr); }.pdf-document-thumbnails { padding:3px; }.pdf-document-thumbnails button { padding:2px; }.pdf-document-scroll { padding-right:8px; padding-left:8px; }.pdf-document-navigation { gap:3px; } }
</style>
