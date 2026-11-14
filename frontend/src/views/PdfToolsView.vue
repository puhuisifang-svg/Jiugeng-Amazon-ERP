<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue'
import JSZip from 'jszip'
import PdfPagePreview from '@/components/PdfPagePreview.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { downloadPdf, ensurePrintable, imagesToPdf, loadPdf, MAX_PDF_BYTES, MM, overlayPdf, pageGroups, splitPdf, squareLabels, type PdfImage } from '@/utils/pdfTools'

type Tab = 'split' | 'images' | 'edit' | 'fba'
type ImageItem = PdfImage & { id: number; name: string; url: string }
type Layer = { id: number; page: number; kind: 'text' | 'image' | 'highlight' | 'rectangle'; x: number; y: number; width: number; height: number; text: string; color: string; fontSize: number; filled?: boolean; url?: string }
const tabs: { id: Tab; title: string; description: string }[] = [
  { id: 'split', title: '拆分PDF', description: '按范围拆分，或提取指定页面' },
  { id: 'images', title: '图片转PDF', description: '支持JPG、PNG，多张图片按顺序合并' },
  { id: 'edit', title: '编辑PDF文件', description: '添加文字、图片、矩形及高亮标注' },
  { id: 'fba', title: 'FBA外箱标转换', description: '完整等比缩放，输出100 × 100毫米标签' },
]
const tab = ref<Tab>('split'), fileInput = ref<HTMLInputElement>(), overlayInput = ref<HTMLInputElement>()
// Each tool owns its files, output and working settings for the lifetime of this page.
function createTabState() {
  return {
    source: shallowRef<Uint8Array>(), result: shallowRef<Uint8Array>(), name: ref(''), pageCount: ref(0), page: ref(0),
    message: ref(''), error: ref(''), ranges: ref(''), splitMode: ref('ranges'),
    margin: ref(2), imageMargin: ref(2), paper: ref<'a4' | 'square' | 'original'>('a4'), landscape: ref(false),
    images: ref<ImageItem[]>([]), layers: ref<Layer[]>([]), selectedId: ref<number>(), dimensions: ref({ width: 595, height: 842 }),
  }
}
const states = { split: createTabState(), images: createTabState(), edit: createTabState(), fba: createTabState() }
function activeRef<T>(select: (state: ReturnType<typeof createTabState>) => Ref<T>) {
  return computed({ get: () => select(states[tab.value]).value, set: (value: T) => { select(states[tab.value]).value = value } })
}
const source = activeRef(s => s.source), result = activeRef(s => s.result), name = activeRef(s => s.name), pageCount = activeRef(s => s.pageCount), page = activeRef(s => s.page)
const busy = ref(false), message = activeRef(s => s.message), error = activeRef(s => s.error), ranges = activeRef(s => s.ranges), splitMode = activeRef(s => s.splitMode)
const margin = activeRef(s => s.margin), imageMargin = activeRef(s => s.imageMargin), paper = activeRef(s => s.paper), landscape = activeRef(s => s.landscape)
const images = activeRef<ImageItem[]>(s => s.images), layers = activeRef<Layer[]>(s => s.layers), selectedId = activeRef(s => s.selectedId), dimensions = activeRef(s => s.dimensions)
const thumbnailPage = ref(0)
const extraction = computed(() => tab.value === 'split' && splitMode.value === 'merge')
const selectedPages = computed(() => { try { return [...new Set(pageGroups(ranges.value, pageCount.value).flat())].sort((a,b) => a-b) } catch { return [] } })
const thumbnailPages = computed(() => Array.from({ length: Math.min(12, Math.max(0, pageCount.value - thumbnailPage.value * 12)) }, (_, i) => thumbnailPage.value * 12 + i))
function togglePage(index: number) {
  const pages = new Set(selectedPages.value)
  if (pages.has(index)) pages.delete(index); else pages.add(index)
  ranges.value = [...pages].sort((a,b) => a-b).map(i => i+1).join(',')
}
const stage = ref<HTMLElement>(), stageWidth = ref(600)
let sequence = 0, observer: ResizeObserver | undefined
const selected = computed(() => layers.value.find(l => l.id === selectedId.value))
const visibleLayers = computed(() => layers.value.filter(l => l.page === page.value))
const preview = computed(() => result.value || source.value)
const title = computed(() => tabs.find(t => t.id === tab.value)!)
const baseName = computed(() => name.value.replace(/\.pdf$/i, '') || '图片')
const canExport = computed(() => !busy.value && (tab.value === 'images' ? images.value.length > 0 : !!source.value))
const outputLabel = computed(() => extraction.value ? '合并所选页面并下载' : ({ split: '拆分并下载', images: '生成PDF', edit: '保存并下载', fba: '转换并下载' })[tab.value])
function resetResult() { result.value = undefined; message.value = ''; error.value = '' }
for (const state of Object.values(states)) {
  watch([state.margin, state.imageMargin, state.paper, state.landscape, state.ranges, state.splitMode], () => {
    state.result.value = undefined; state.message.value = ''; state.error.value = ''
  }, { flush: 'sync' })
}
watch([tab, page], () => { endDrag?.() })
watch(stage, element => {
  observer?.disconnect()
  if (element) { observer = new ResizeObserver(() => { stageWidth.value = element.clientWidth }); observer.observe(element); stageWidth.value = element.clientWidth }
}, { flush: 'post' })
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try { await action() } catch (e) { error.value = e instanceof Error ? e.message : '处理失败，请重试' }
  finally { busy.value = false }
}
function chooseFile() { fileInput.value?.click() }
async function normalizeImage(file: File): Promise<PdfImage & { url: string }> {
  if (!/\.(jpe?g|png)$/i.test(file.name) && !['image/jpeg', 'image/png'].includes(file.type)) throw new Error('仅支持JPG、PNG图片')
  if (file.size > 20 * 1024 * 1024) throw new Error('每张图片不能超过20 MB')
  const bitmap = await createImageBitmap(file)
  try {
    if (bitmap.width * bitmap.height > 40_000_000) throw new Error('图片分辨率过大，请缩小到4000万像素以内')
    const canvas = document.createElement('canvas'); canvas.width = bitmap.width; canvas.height = bitmap.height
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(b => b ? resolve(b) : reject(new Error('图片读取失败')), 'image/png'))
    return { bytes: new Uint8Array(await blob.arrayBuffer()), format: 'png', url: URL.createObjectURL(blob) }
  } finally { bitmap.close() }
}
async function acceptFiles(files: File[]) {
  await run(async () => {
    if (!files.length) return
    resetResult()
    if (tab.value === 'images') {
      if (images.value.length + files.length > 50 || files.reduce((n, f) => n + f.size, 0) + images.value.reduce((n, i) => n + i.bytes.length, 0) > 100 * 1024 * 1024) throw new Error('最多50张图片，合计不超过100 MB')
      const pending: ImageItem[] = []
      try { for (const file of files) pending.push({ ...await normalizeImage(file), id: ++sequence, name: file.name }) }
      catch (e) { pending.forEach(i => URL.revokeObjectURL(i.url)); throw e }
      images.value.push(...pending)
    } else {
      const file = files[0]!
      if (files.length !== 1 || !/\.pdf$/i.test(file.name) || file.size > MAX_PDF_BYTES) throw new Error('每次请选择一个PDF文件，最大50 MB')
      const bytes = new Uint8Array(await file.arrayBuffer()), doc = await loadPdf(bytes)
      ensurePrintable(doc)
      source.value = bytes; pageCount.value = doc.getPageCount(); page.value = 0; name.value = file.name
      ranges.value = `1-${pageCount.value}`; thumbnailPage.value = 0; clearLayers()
      if (tab.value === 'fba') result.value = await squareLabels(bytes, margin.value)
    }
  })
}
function fileChange(event: Event) { const input = event.target as HTMLInputElement; void acceptFiles(Array.from(input.files || [])); input.value = '' }
function drop(event: DragEvent) { if (!busy.value) void acceptFiles(Array.from(event.dataTransfer?.files || [])) }
function moveImage(index: number, delta: number) { const next = index + delta; if (next < 0 || next >= images.value.length) return; const [item] = images.value.splice(index, 1); images.value.splice(next, 0, item!); resetResult() }
function removeImage(index: number) { URL.revokeObjectURL(images.value[index]!.url); images.value.splice(index, 1); resetResult() }
function clearLayers() { layers.value.forEach(l => { if (l.url) URL.revokeObjectURL(l.url) }); layers.value = []; selectedId.value = undefined }
function addLayer(kind: Layer['kind']) {
  resetResult()
  const layer: Layer = { id: ++sequence, page: page.value, kind, x: .1, y: .1, width: .35, height: .09, text: '请输入文字', color: kind === 'highlight' ? '#facc15' : kind === 'rectangle' ? '#ffffff' : '#111827', filled: true, fontSize: 16 }
  layers.value.push(layer); selectedId.value = layer.id
  return layer
}
async function addOverlay(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''
  if (!file) return
  await run(async () => {
    const image = await normalizeImage(file), bitmap = await createImageBitmap(new Blob([new Uint8Array(image.bytes)]))
    const layer = addLayer('image'); layer.url = image.url; layer.height = Math.min(.8, layer.width * dimensions.value.width / dimensions.value.height * bitmap.height / bitmap.width)
    bitmap.close()
  })
}
function removeLayer() { const l = selected.value; if (l?.url) URL.revokeObjectURL(l.url); layers.value = layers.value.filter(i => i.id !== selectedId.value); selectedId.value = undefined; resetResult() }
function layerStyle(l: Layer) {
  return { left: `${l.x * 100}%`, top: `${l.y * 100}%`, width: l.kind === 'text' ? undefined : `${l.width * 100}%`, height: l.kind === 'text' ? undefined : `${l.height * 100}%`, fontSize: `${l.fontSize * stageWidth.value / dimensions.value.width}px`, color: l.color, background: l.kind === 'highlight' ? `${l.color}66` : l.kind === 'rectangle' && l.filled ? l.color : undefined, borderColor: l.kind === 'rectangle' ? l.color : undefined }
}
let endDrag: (() => void) | undefined
function drag(event: PointerEvent, layer: Layer) {
  if (busy.value || event.button !== 0) return
  event.preventDefault(); selectedId.value = layer.id; resetResult(); endDrag?.()
  const rect = stage.value!.getBoundingClientRect(), target = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const startX = event.clientX, startY = event.clientY, x = layer.x, y = layer.y
  const move = (e: PointerEvent) => { layer.x = Math.max(0, Math.min(1 - target.width / rect.width, x + (e.clientX - startX) / rect.width)); layer.y = Math.max(0, Math.min(1 - target.height / rect.height, y + (e.clientY - startY) / rect.height)) }
  const end = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', end); window.removeEventListener('pointercancel', end); endDrag = undefined }
  endDrag = end; window.addEventListener('pointermove', move); window.addEventListener('pointerup', end); window.addEventListener('pointercancel', end)
}
type Corner = 'nw' | 'ne' | 'sw' | 'se'
const resizeCorners: { corner: Corner; label: string }[] = [{ corner: 'nw', label: '左上' }, { corner: 'ne', label: '右上' }, { corner: 'sw', label: '左下' }, { corner: 'se', label: '右下' }]
function resizeLayer(event: PointerEvent, layer: Layer, corner: Corner) {
  if (busy.value || event.button !== 0 || !stage.value) return
  event.preventDefault(); endDrag?.(); selectedId.value = layer.id; resetResult()
  const rect = stage.value.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  const startX = event.clientX, startY = event.clientY
  const left = layer.x, top = layer.y, right = left + layer.width, bottom = top + layer.height
  const move = (e: PointerEvent) => {
    const dx = (e.clientX - startX) / rect.width, dy = (e.clientY - startY) / rect.height
    const x = corner.includes('w') ? Math.max(0, Math.min(right - .02, left + dx)) : left
    const y = corner.includes('n') ? Math.max(0, Math.min(bottom - .02, top + dy)) : top
    const r = corner.includes('e') ? Math.min(1, Math.max(left + .02, right + dx)) : right
    const b = corner.includes('s') ? Math.min(1, Math.max(top + .02, bottom + dy)) : bottom
    Object.assign(layer, { x, y, width: r - x, height: b - y })
  }
  const end = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', end); window.removeEventListener('pointercancel', end); endDrag = undefined }
  endDrag = end; window.addEventListener('pointermove', move); window.addEventListener('pointerup', end); window.addEventListener('pointercancel', end)
}
async function renderLayers() {
  if (layers.value.some(l => l.kind === 'text' && (!Number.isFinite(l.fontSize) || l.fontSize < 6 || l.fontSize > 120))) throw new Error('字号应为6～120pt')
  const doc = await loadPdf(source.value!), output: { page: number; png: Uint8Array }[] = []
  for (const pageIndex of new Set(layers.value.map(l => l.page))) {
    const pdfPage = doc.getPage(pageIndex), box = pdfPage.getCropBox(), swap = Math.abs(pdfPage.getRotation().angle % 180) === 90
    const w = swap ? box.height : box.width, h = swap ? box.width : box.height
    const scale = Math.min(3, 4000 / Math.max(w, h))
    const canvas = document.createElement('canvas'); canvas.width = Math.ceil(w * scale); canvas.height = Math.ceil(h * scale)
    const ctx = canvas.getContext('2d')!; ctx.scale(canvas.width / w, canvas.height / h)
    for (const l of layers.value.filter(i => i.page === pageIndex)) {
      ctx.save(); ctx.fillStyle = l.color; ctx.strokeStyle = l.color
      if (l.kind === 'text') {
        ctx.font = `${l.fontSize}px "PingFang SC", "Microsoft YaHei", sans-serif`; ctx.textBaseline = 'top'
        l.text.split('\n').forEach((line, i) => ctx.fillText(line, l.x * w, l.y * h + i * l.fontSize * 1.3 + l.fontSize * .15))
      } else if (l.kind === 'image' && l.url) {
        const img = new Image(); img.src = l.url; await img.decode(); ctx.drawImage(img, l.x * w, l.y * h, l.width * w, l.height * h)
      } else if (l.kind === 'highlight') { ctx.globalAlpha = .4; ctx.fillRect(l.x * w, l.y * h, l.width * w, l.height * h) }
      else if (l.filled) { ctx.fillRect(l.x * w, l.y * h, l.width * w, l.height * h) }
      else { ctx.lineWidth = 1; ctx.strokeRect(l.x * w + .5, l.y * h + .5, l.width * w - 1, l.height * h - 1) }
      ctx.restore()
    }
    const blob = await new Promise<Blob>(resolve => canvas.toBlob(b => resolve(b!), 'image/png'))
    output.push({ page: pageIndex, png: new Uint8Array(await blob.arrayBuffer()) })
  }
  return output
}
async function exportFile() {
  await run(async () => {
    if (tab.value === 'split') {
      let groups = splitMode.value === 'each' ? Array.from({ length: pageCount.value }, (_, i) => [i]) : pageGroups(ranges.value, pageCount.value)
      if (splitMode.value === 'merge') groups = [[...new Set(groups.flat())].sort((a,b) => a-b)]
      const outputs = await splitPdf(source.value!, groups)
      if (outputs.length === 1) downloadPdf(outputs[0]!, `${baseName.value}-提取.pdf`)
      else {
        const zip = new JSZip()
        outputs.forEach((bytes, i) => zip.file(`${String(i + 1).padStart(3, '0')}-第${groups[i]![0]! + 1}页起.pdf`, bytes))
        downloadPdf(await zip.generateAsync({ type: 'uint8array' }), `${baseName.value}-拆分.zip`, 'application/zip')
      }
      message.value = `已生成${outputs.length}个PDF，下载已开始`
    } else if (tab.value === 'images') {
      result.value = await imagesToPdf(images.value, paper.value, landscape.value, imageMargin.value); page.value = 0
      downloadPdf(result.value, '图片合并.pdf'); message.value = `已生成${images.value.length}页PDF，下载已开始`
    } else if (tab.value === 'edit') {
      const bytes = await overlayPdf(source.value!, await renderLayers())
      downloadPdf(bytes, `${baseName.value}-编辑.pdf`); message.value = '编辑结果已生成，下载已开始'
    } else {
      result.value = await squareLabels(source.value!, margin.value)
      downloadPdf(result.value, `${baseName.value}-100x100mm.pdf`); message.value = '转换完成。打印请选择100×100毫米纸张、实际大小（100%）'
    }
  })
}
function paste(event: ClipboardEvent) {
  if (tab.value !== 'images' || busy.value || ['INPUT', 'TEXTAREA'].includes((event.target as HTMLElement)?.tagName)) return
  const files = Array.from(event.clipboardData?.files || []).filter(f => f.type.startsWith('image/'))
  if (files.length) { event.preventDefault(); void acceptFiles(files) }
}
onMounted(() => window.addEventListener('paste', paste))
onBeforeUnmount(() => { observer?.disconnect(); endDrag?.(); window.removeEventListener('paste', paste); for (const state of Object.values(states)) { state.images.value.forEach(i => URL.revokeObjectURL(i.url)); state.layers.value.forEach(l => { if (l.url) URL.revokeObjectURL(l.url) }) } })
</script>
<template>
  <section class="pdf-tools">
    <nav
      class="pdf-tabs"
      aria-label="PDF工具"
    >
      <button
        v-for="item in tabs"
        :key="item.id"
        type="button"
        :class="{ active: tab === item.id }"
        :aria-current="tab === item.id ? 'page' : undefined"
        :disabled="busy"
        @click="tab = item.id"
      >
        {{ item.title }}
      </button>
      <span>文件仅在当前浏览器处理</span>
    </nav>
    <div class="pdf-actions">
      <div><strong>{{ title.title }}</strong><span>{{ title.description }}</span></div>
      <button
        type="button"
        class="primary"
        :disabled="!canExport"
        @click="exportFile"
      >
        {{ busy ? '正在处理…' : outputLabel }}
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="pdf-error"
    >
      {{ error }}
    </p>
    <p
      v-if="message"
      role="status"
      class="pdf-success"
    >
      {{ message }}
    </p>
    <input
      :key="tab"
      ref="fileInput"
      class="hidden-input"
      type="file"
      :accept="tab === 'images' ? 'image/jpeg,image/png,.jpg,.jpeg,.png' : 'application/pdf,.pdf'"
      :multiple="tab === 'images'"
      @change="fileChange"
    >
    <input
      ref="overlayInput"
      class="hidden-input"
      type="file"
      accept="image/jpeg,image/png"
      @change="addOverlay"
    >
    <div class="pdf-workspace">
      <aside class="pdf-settings">
        <button
          class="pdf-upload"
          type="button"
          :disabled="busy"
          @click="chooseFile"
          @dragover.prevent
          @drop.prevent="drop"
        >
          <b class="upload-icon">＋</b><strong>{{ tab === 'images' ? '添加图片' : source ? '更换PDF文件' : '选择PDF文件' }}</strong>
          <span>{{ tab === 'images' ? 'JPG / PNG · 支持拖入、粘贴' : '拖入PDF · 最大50 MB / 500页' }}</span>
        </button>
        <div
          v-if="source && tab !== 'images'"
          class="pdf-file-info"
        >
          <strong :title="name">{{ name }}</strong><span>共{{ pageCount }}页</span>
        </div>
        <template v-if="tab === 'split'">
          <label>拆分方式<select
            v-model="splitMode"
            v-single-select
            :disabled="busy"
          ><option value="ranges">按范围拆分为多个PDF</option><option value="merge">提取页面，合为一个PDF</option><option value="each">每页拆分为一个PDF</option></select></label>
          <label v-if="splitMode !== 'each'">页面范围<input
            v-model="ranges"
            :disabled="busy"
            placeholder="例如：1-3,5,7-9"
          ></label>
          <p class="pdf-hint">
            页码从1开始，逗号分隔不同范围。多个文件将打包为ZIP下载。
          </p>
        </template>
        <template v-if="tab === 'images'">
          <label>纸张<select
            v-model="paper"
            v-single-select
            :disabled="busy"
          ><option value="a4">A4</option><option value="square">100 × 100毫米</option><option value="original">跟随图片尺寸（96 DPI）</option></select></label>
          <label v-if="paper === 'a4'">方向<select
            v-model="landscape"
            v-single-select
            :disabled="busy"
          ><option :value="false">纵向</option><option :value="true">横向</option></select></label>
          <label>边距（毫米）<input
            v-model.number="imageMargin"
            type="number"
            min="0"
            max="20"
            :disabled="busy"
          ></label>
          <p class="pdf-hint">
            每张图片一页，可调整顺序。图片保持原比例，不裁切。最多50张，每张20 MB。
          </p>
          <button
            v-if="result"
            class="secondary"
            :disabled="busy"
            @click="result = undefined"
          >
            返回图片排序
          </button>
        </template>
        <template v-if="tab === 'fba'">
          <div class="pdf-spec">
            <strong>100 × 100 <small>mm</small></strong><span>正方形标签 · 保留矢量条码</span>
          </div>
          <label>打印边距（毫米）<input
            v-model.number="margin"
            type="number"
            min="0"
            max="10"
            :disabled="busy"
          ></label>
          <button
            type="button"
            class="secondary"
            :disabled="!source || busy"
            @click="run(async () => { result = await squareLabels(source!, margin) })"
          >
            预览转换效果
          </button>
          <p class="pdf-hint">
            整页等比缩小、居中留白，不裁切、不拉伸。打印选择“实际大小 / 100%”，建议先试打一张确认扫码。
          </p>
        </template>
        <template v-if="tab === 'edit'">
          <div class="pdf-edit-actions">
            <button
              :disabled="!source || busy"
              @click="addLayer('text')"
            >
              ＋文字
            </button><button
              :disabled="!source || busy"
              @click="overlayInput?.click()"
            >
              ＋图片
            </button>
            <button
              :disabled="!source || busy"
              @click="addLayer('highlight')"
            >
              高亮标注
            </button><button
              :disabled="!source || busy"
              @click="addLayer('rectangle')"
            >
              矩形框
            </button>
          </div>
          <p class="pdf-hint">
            拖动内容调整位置，拖动矩形四角调整大小。支持中英文；新增内容以高清图层保存，原有文字不可直接改写。
          </p>
          <fieldset
            v-if="selected"
            :disabled="busy"
            class="layer-settings"
          >
            <legend>选中内容</legend>
            <label v-if="selected.kind === 'text'">文字<textarea
              v-model="selected.text"
              rows="3"
              maxlength="2000"
            /></label>
            <label v-if="selected.kind === 'text'">字号（pt）<input
              v-model.number="selected.fontSize"
              type="number"
              min="6"
              max="120"
            ></label>
            <label v-if="selected.kind !== 'image'">颜色<input
              v-model="selected.color"
              type="color"
            ></label>
            <label v-if="selected.kind === 'rectangle'">样式<select
              v-model="selected.filled"
              v-single-select
            ><option :value="true">实心填充</option><option :value="false">空心边框</option></select></label>
            <template v-if="selected.kind !== 'text'">
              <label>宽度<input
                v-model.number="selected.width"
                type="range"
                min="0.02"
                :max="1-selected.x"
                step="0.01"
              ></label>
              <label>高度<input
                v-model.number="selected.height"
                type="range"
                min="0.02"
                :max="1-selected.y"
                step="0.01"
              ></label>
            </template>
            <button
              type="button"
              class="pdf-danger"
              @click="removeLayer"
            >
              移除选中内容
            </button>
          </fieldset>
          <p
            v-if="layers.length"
            class="pdf-hint"
          >
            已添加{{ layers.length }}项内容，切换页面可继续编辑。
          </p>
        </template>
      </aside>
      <main class="pdf-content">
        <template v-if="tab === 'images' && !result">
          <div
            v-if="images.length"
            class="pdf-image-grid"
          >
            <article
              v-for="(item, index) in images"
              :key="item.id"
              class="pdf-image-card"
            >
              <img
                :src="item.url"
                :alt="item.name"
              ><p :title="item.name">
                {{ index + 1 }}. {{ item.name }}
              </p>
              <div>
                <button
                  :disabled="index === 0 || busy"
                  @click="moveImage(index, -1)"
                >
                  前移
                </button><button
                  :disabled="index === images.length - 1 || busy"
                  @click="moveImage(index, 1)"
                >
                  后移
                </button><button
                  class="pdf-danger"
                  :disabled="busy"
                  @click="removeImage(index)"
                >
                  移除
                </button>
              </div>
            </article>
          </div>
          <button
            v-else
            class="pdf-empty"
            :disabled="busy"
            @click="chooseFile"
            @dragover.prevent
            @drop.prevent="drop"
          >
            <b>JPG / PNG → PDF</b><span>点击选择或拖入图片，也可直接粘贴截图</span>
          </button>
        </template>
        <template v-else-if="source && extraction">
          <div class="pdf-pagebar">
            <strong>已选 {{ selectedPages.length }} / {{ pageCount }} 页</strong>
            <button
              :disabled="busy"
              @click="ranges = `1-${pageCount}`"
            >
              全选
            </button>
            <button
              :disabled="busy"
              @click="ranges = ''"
            >
              清空
            </button>
            <span>点击页面选择，按原文档顺序合并</span>
          </div>
          <div class="pdf-thumbnail-grid">
            <button
              v-for="index in thumbnailPages"
              :key="index"
              type="button"
              class="pdf-thumbnail"
              :class="{ checked: selectedPages.includes(index) }"
              :aria-pressed="selectedPages.includes(index)"
              :aria-label="`选择第${index+1}页`"
              :disabled="busy"
              @click="togglePage(index)"
            >
              <span class="page-check">{{ selectedPages.includes(index) ? '✓' : '' }}</span>
              <PdfPagePreview
                :bytes="source"
                :page="index"
              />
              <span>第 {{ index + 1 }} 页</span>
            </button>
          </div>
          <div class="pdf-pagebar">
            <button
              :disabled="thumbnailPage === 0 || busy"
              @click="thumbnailPage--"
            >
              上一组
            </button>
            <span>{{ thumbnailPage + 1 }} / {{ Math.ceil(pageCount / 12) }}</span>
            <button
              :disabled="(thumbnailPage+1)*12 >= pageCount || busy"
              @click="thumbnailPage++"
            >
              下一组
            </button>
          </div>
        </template>
        <template v-else-if="preview">
          <div class="pdf-pagebar">
            <button
              :disabled="page === 0 || busy"
              @click="page--"
            >
              上一页
            </button>
            <label>第<input
              v-model.number="page"
              type="hidden"
            ><select
              v-model.number="page"
              v-single-select
              :disabled="busy"
              aria-label="预览页码"
            ><option
              v-for="(_, i) in (tab === 'images' ? images.length : pageCount)"
              :key="i"
              :value="i"
            >{{ i + 1 }}</option></select>页 / {{ tab === 'images' ? images.length : pageCount }}页</label>
            <button
              :disabled="page >= (tab === 'images' ? images.length : pageCount)-1 || busy"
              @click="page++"
            >
              下一页
            </button>
            <span>{{ (dimensions.width/MM).toFixed(1) }} × {{ (dimensions.height/MM).toFixed(1) }} mm{{ tab === 'fba' ? result ? ' · 转换后' : ' · 原文件' : '' }}</span>
          </div>
          <div class="pdf-paper-area">
            <div
              ref="stage"
              class="pdf-stage"
              :class="{ square: tab === 'fba' && result }"
            >
              <PdfPagePreview
                :bytes="preview"
                :page="page"
                @dimensions="dimensions = $event"
              >
                <template v-if="tab === 'edit'">
                  <div
                    v-for="layer in visibleLayers"
                    :key="layer.id"
                    class="pdf-layer"
                    :class="[layer.kind, { selected: selectedId === layer.id }]"
                    :style="layerStyle(layer)"
                    role="button"
                    tabindex="0"
                    :aria-label="`选择${layer.kind === 'text' ? layer.text : '标注'}`"
                    @pointerdown="drag($event, layer)"
                    @keydown.enter="selectedId = layer.id"
                    @keydown.delete="selectedId = layer.id; removeLayer()"
                  >
                    <img
                      v-if="layer.kind === 'image'"
                      :src="layer.url"
                      alt="新增图片"
                      draggable="false"
                    ><template v-if="layer.kind === 'text'">
                      {{ layer.text }}
                    </template>
                    <template v-if="layer.kind === 'rectangle' && selectedId === layer.id">
                      <span
                        v-for="handle in resizeCorners"
                        :key="handle.corner"
                        class="pdf-resize-handle"
                        :class="handle.corner"
                        :aria-label="`${handle.label}缩放手柄`"
                        :title="`拖动${handle.label}调整大小`"
                        @pointerdown.stop="resizeLayer($event, layer, handle.corner)"
                      />
                    </template>
                  </div>
                </template>
              </PdfPagePreview>
            </div>
          </div>
        </template>
        <button
          v-else
          class="pdf-empty"
          :disabled="busy"
          @click="chooseFile"
          @dragover.prevent
          @drop.prevent="drop"
        >
          <b>{{ tab === 'fba' ? 'FBA → 100 × 100 mm' : 'PDF' }}</b><span>选择或拖入PDF，查看页面预览</span><small>文件不会上传到服务器，关闭页面后不保留</small>
        </button>
      </main>
    </div>
  </section>
</template>
<style scoped>
.pdf-thumbnail-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(160px,1fr)); gap:16px; padding:20px; max-height:calc(100vh - 310px); overflow:auto; }
.pdf-tools .pdf-thumbnail { position:relative; display:flex; flex-direction:column; align-items:center; gap:12px; min-width:0; padding:20px 16px 12px; border:2px solid transparent; border-radius:8px; background:white; color:#526078; }
.pdf-tools .pdf-thumbnail.checked { border-color:#36a776; background:#f4fcf8; }
.pdf-thumbnail :deep(.pdf-preview) { pointer-events:none; }
.page-check { position:absolute; z-index:1; top:4px; left:4px; width:22px; height:22px; border:1px solid #bcc7d4; border-radius:50%; background:white; line-height:20px; }
.checked .page-check { background:#36a776; border-color:#36a776; color:white; }
.pdf-tools { display:flex; flex-direction:column; gap:8px; color:#26364d; font-size:13px; }
.pdf-tabs { display:flex; align-items:center; gap:22px; padding:0 16px; background:#fff; border:1px solid #e4e9f0; border-radius:8px; flex-wrap:wrap; }
.pdf-tabs button { border:0; border-bottom:2px solid transparent; border-radius:0; background:none; color:#64748b; min-height:42px; padding:0 2px; font-size:14px; }
.pdf-tabs button.active { color:#2874d7; border-bottom-color:#2874d7; font-weight:600; }
.pdf-tabs>span { margin-left:auto; color:#8491a3; font-size:12px; }
.pdf-actions { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:8px 12px; background:white; border:1px solid #e4e9f0; border-radius:8px; }
.pdf-actions>div { display:flex; gap:14px; align-items:center; flex-wrap:wrap; }.pdf-actions span { color:#7c889a; }
.pdf-tools button { cursor:pointer; }.pdf-tools button:disabled { cursor:not-allowed; opacity:.5; }
.pdf-tools .primary { background:#2878df; border:1px solid #2878df; color:white; min-height:36px; border-radius:6px; padding:0 18px; font-weight:600; white-space:nowrap; }
.pdf-workspace { display:grid; grid-template-columns:280px minmax(0,1fr); gap:10px; align-items:start; }
.pdf-settings { display:flex; flex-direction:column; gap:16px; padding:16px; background:#fff; border:1px solid #e4e9f0; border-radius:8px; }
.pdf-settings label { display:flex; flex-direction:column; gap:7px; font-size:13px; color:#526078; }
.pdf-settings input:not([type=range]):not([type=color]), .pdf-settings textarea { width:100%; min-width:0; min-height:38px; box-sizing:border-box; border:1px solid #dce3ec; border-radius:6px; padding:8px 10px; font-size:13px; color:#26364d; background:white; }
.pdf-settings textarea { resize:vertical; }.pdf-settings input[type=color] { width:100%; height:34px; border:1px solid #dce3ec; border-radius:6px; background:#fff; }
.pdf-upload { display:flex; flex-direction:column; gap:8px; align-items:center; justify-content:center; min-height:132px; background:#f7faff; border:1px dashed #afc7e8; border-radius:8px; color:#2874d7; }
.pdf-upload span { font-size:12px; color:#78889d; }.upload-icon { font-size:25px; font-weight:400; }
.pdf-file-info { display:flex; flex-direction:column; gap:6px; padding-bottom:14px; border-bottom:1px solid #edf0f5; }.pdf-file-info strong { overflow-wrap:anywhere; font-weight:500; }.pdf-file-info span { color:#8a96a6; }
.pdf-hint { margin:0; line-height:1.7; font-size:12px; color:#7d8a9e; }
.pdf-spec { display:flex; flex-direction:column; gap:8px; padding:18px 12px; text-align:center; border-radius:8px; background:#eff6ff; }.pdf-spec strong { font-size:24px; color:#2874d7; }.pdf-spec small { font-size:13px; }.pdf-spec span { color:#6a7d96; font-size:12px; }
.pdf-content { min-width:0; border:1px solid #e1e7ef; background:#eef2f7; border-radius:8px; overflow:hidden; }
.pdf-empty { display:flex; width:100%; min-height:520px; align-items:center; justify-content:center; gap:18px; flex-direction:column; border:0; background:transparent; color:#8795a9; }
.pdf-empty b { color:#a3b7d3; font-size:28px; font-weight:500; }.pdf-empty small { color:#97a3b3; }
.pdf-pagebar { display:flex; flex-wrap:wrap; align-items:center; gap:12px; padding:8px 14px; background:white; border-bottom:1px solid #dfe6ef; }
.pdf-pagebar label { display:flex; align-items:center; gap:6px; white-space:nowrap; }.pdf-pagebar :deep(.single-select-trigger) { width:62px; min-height:30px; }.pdf-pagebar>span { margin-left:auto; color:#8190a2; font-size:12px; }
.pdf-pagebar button, .pdf-edit-actions button, .secondary, .pdf-image-card button, .layer-settings button { background:white; border:1px solid #d9e2ee; border-radius:5px; color:#47729f; padding:6px 10px; font-size:12px; min-height:32px; }
.pdf-paper-area { overflow:auto; max-height:calc(100vh - 255px); min-height:360px; padding:24px; }.pdf-stage { max-width:620px; width:100%; margin:auto; position:relative; }.pdf-stage.square { max-width:560px; }
.pdf-edit-actions { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.layer-settings { display:flex; flex-direction:column; gap:12px; margin:0; padding:12px; border:1px solid #e0e7f0; border-radius:6px; min-width:0; }.layer-settings legend { color:#697e99; font-size:12px; padding:0 4px; }
.pdf-layer { position:absolute; cursor:move; touch-action:none; box-sizing:border-box; line-height:1.3; font-family:"PingFang SC","Microsoft YaHei",sans-serif; user-select:none; }.pdf-resize-handle { position:absolute; width:24px; height:24px; touch-action:none; z-index:2; }
.pdf-resize-handle::after { content:''; position:absolute; inset:8px; background:#fff; border:1px solid #2580ed; box-sizing:border-box; }
.pdf-resize-handle.nw { left:-12px; top:-12px; cursor:nwse-resize; }.pdf-resize-handle.ne { right:-12px; top:-12px; cursor:nesw-resize; }.pdf-resize-handle.sw { left:-12px; bottom:-12px; cursor:nesw-resize; }.pdf-resize-handle.se { right:-12px; bottom:-12px; cursor:nwse-resize; }
.pdf-layer.text { white-space:pre; }.pdf-layer.selected { outline:1px dashed #2580ed; outline-offset:3px; }.pdf-layer.rectangle { border:1px solid; }.pdf-layer img { display:block; width:100%; height:100%; pointer-events:none; }
.pdf-image-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:14px; padding:20px; max-height:calc(100vh - 220px); overflow:auto; }.pdf-image-card { padding:10px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; min-width:0; }.pdf-image-card img { width:100%; height:170px; object-fit:contain; background:#f8fafc; }.pdf-image-card p { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:12px; }.pdf-image-card>div { display:flex; gap:5px; }.pdf-tools .pdf-danger { color:#dc5454; }
.pdf-error,.pdf-success { margin:0; padding:9px 14px; border-radius:6px; line-height:1.5; }.pdf-error { color:#bb3939; background:#fff1f1; }.pdf-success { color:#218056; background:#edf9f2; }.hidden-input { display:none; }
@media(max-width:850px) { .pdf-workspace { grid-template-columns:1fr; }.pdf-settings { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }.pdf-tabs { gap:14px; }.pdf-tabs>span { display:none; }.pdf-paper-area { max-height:none; }.pdf-empty { min-height:300px; } }
@media(max-width:480px) { .pdf-settings { grid-template-columns:1fr; }.pdf-actions span { display:none; }.pdf-paper-area { padding:12px; } }
</style>
