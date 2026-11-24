<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { customsFilesApi, type CustomsAttachment, type CustomsSpreadsheetPreview } from '@/api/customsDocuments'
const PdfContinuousDocument = defineAsyncComponent(() => import('./PdfContinuousDocument.vue'))
const props = defineProps<{ documentId: string; file: CustomsAttachment }>()
const emit = defineEmits<{ close: [] }>()
const loading = ref(true), error = ref(''), downloading = ref(false), downloadError = ref(''), imageUrl = ref('')
const bytes = shallowRef<Uint8Array>(), sizes = ref<{ width: number; height: number }[]>([]), page = ref(0)
const workbook = shallowRef<CustomsSpreadsheetPreview>(), sheetIndex = ref(0)
const sheet = computed(() => workbook.value?.sheets[sheetIndex.value])
let request = 0, controller: AbortController | undefined
function release() { if (imageUrl.value) URL.revokeObjectURL(imageUrl.value); imageUrl.value = ''; bytes.value = undefined; workbook.value = undefined }
async function load() {
  const sequence = ++request
  controller?.abort(); controller = new AbortController(); release()
  loading.value = true; error.value = ''; downloadError.value = ''; page.value = 0; sheetIndex.value = 0
  try {
    if (['application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'].includes(props.file.mediaType)) {
      const result = await customsFilesApi.spreadsheetPreview(props.documentId, props.file.id, controller.signal)
      if (sequence === request) workbook.value = result
    } else {
      const blob = await customsFilesApi.blob(props.documentId, props.file.id, controller.signal)
      if (sequence !== request) return
      if (['image/png', 'image/jpeg'].includes(props.file.mediaType)) imageUrl.value = URL.createObjectURL(blob)
      else if (props.file.mediaType === 'application/pdf') {
        const data = new Uint8Array(await blob.arrayBuffer())
        const { loadPdf } = await import('@/utils/pdfTools')
        const doc = await loadPdf(data)
        if (sequence !== request) return
        sizes.value = doc.getPages().map(p => { const box = p.getCropBox(), rotated = Math.abs(p.getRotation().angle % 180) === 90; return { width: rotated ? box.height : box.width, height: rotated ? box.width : box.height } })
        bytes.value = data
      } else throw new Error('该文件格式暂不支持预览，可下载原文件查看')
    }
  } catch (e) { if (sequence === request) error.value = e instanceof Error ? e.message : '预览加载失败，请重试' }
  finally { if (sequence === request) loading.value = false }
}
async function download() {
  if (downloading.value) return
  downloading.value = true; downloadError.value = ''
  try { await customsFilesApi.download(props.documentId, props.file) }
  catch (e) { downloadError.value = e instanceof Error ? e.message : '下载失败，请重试' }
  finally { downloading.value = false }
}
watch(() => [props.documentId, props.file.id], () => void load(), { immediate: true })
onBeforeUnmount(() => { request++; controller?.abort(); release() })
</script>
<template>
  <Teleport to="#app">
    <AccessDialog
      :title="`文件预览 · ${file.name}`"
      dialog-class="customs-attachment-preview"
      @close="emit('close')"
    >
      <div
        v-if="loading"
        class="preview-state"
        role="status"
      >
        正在加载预览…
      </div>
      <div
        v-else-if="error"
        class="preview-state"
        role="alert"
      >
        <p>{{ error }}</p><button
          type="button"
          @click="load"
        >
          重新加载
        </button>
      </div>
      <img
        v-else-if="imageUrl"
        class="attachment-image"
        :src="imageUrl"
        :alt="file.name"
        @error="error = '图片无法显示，请重试或下载原文件查看'"
      >
      <PdfContinuousDocument
        v-else-if="bytes"
        v-model:page="page"
        :bytes="bytes"
        :sizes="sizes"
        :busy="false"
        readonly
      />
      <div
        v-else-if="workbook"
        class="customs-sheet-preview"
      >
        <div class="customs-sheet-toolbar">
          <label>工作表<select
            v-model.number="sheetIndex"
            v-single-select
            aria-label="预览工作表"
          ><option
            v-for="(item, index) in workbook.sheets"
            :key="index"
            :value="index"
          >{{ item.name }}</option></select></label>
          <span>表格内容预览，原始排版可下载查看</span>
        </div>
        <p
          v-if="workbook.truncated"
          class="preview-truncated"
        >
          文件较大，预览最多展示前 5 张工作表、每表 200 行及 30 列，长文本已截短；完整内容请下载查看。
        </p>
        <div
          v-if="sheet?.rows.length"
          class="customs-sheet-scroll"
          tabindex="0"
          :aria-label="`${sheet.name}表格内容`"
        >
          <table class="customs-sheet-grid">
            <thead>
              <tr>
                <th scope="col">
                  行
                </th><th
                  v-for="column in sheet.columns"
                  :key="column"
                  scope="col"
                >
                  {{ column }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row, r) in sheet.rows"
                :key="r"
              >
                <th scope="row">
                  {{ r + 1 }}
                </th><td
                  v-for="(cell, c) in row"
                  :key="c"
                >
                  {{ cell }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div
          v-else
          class="preview-state"
        >
          此工作表暂无内容
        </div>
      </div>
      <template #footer>
        <span
          v-if="downloadError"
          class="preview-download-error"
          role="alert"
        >{{ downloadError }}</span>
        <button
          type="button"
          :disabled="downloading"
          @click="download"
        >
          {{ downloading ? '下载中…' : '下载原文件' }}
        </button>
        <button
          type="button"
          @click="emit('close')"
        >
          关闭
        </button>
      </template>
    </AccessDialog>
  </Teleport>
</template>
<style scoped>
.preview-state { display:flex; flex:1; flex-direction:column; align-items:center; justify-content:center; gap:10px; min-height:180px; padding:16px; color:var(--muted); font-size:13px; text-align:center; }
.preview-state button { min-height:32px; padding:4px 12px; border:1px solid var(--line); border-radius:4px; background:white; color:var(--accent); cursor:pointer; }
.attachment-image { display:block; flex:1; width:100%; height:100%; min-height:0; object-fit:contain; padding:12px; box-sizing:border-box; background:#f5f7fa; }
.customs-sheet-preview { display:flex; flex-direction:column; flex:1; min-height:0; }
.customs-sheet-toolbar { display:flex; flex-wrap:wrap; align-items:center; gap:8px 14px; padding:8px 12px; border-bottom:1px solid var(--line); font-size:12px; }
.customs-sheet-toolbar label { display:flex; align-items:center; gap:8px; min-width:0; }
.customs-sheet-toolbar :deep(.single-select-trigger) { width:180px; max-width:60vw; }
.customs-sheet-toolbar>span { color:var(--muted); }
.customs-sheet-scroll { flex:1; min-height:0; overflow:auto; }
.customs-sheet-grid { border-collapse:separate; border-spacing:0; min-width:100%; font-size:12px; }
.customs-sheet-grid th,.customs-sheet-grid td { min-width:80px; max-width:360px; padding:6px 8px; border-right:1px solid #e2e8f0; border-bottom:1px solid #e2e8f0; white-space:pre-wrap; overflow-wrap:anywhere; text-align:left; }
.customs-sheet-grid th { color:#64748b; background:#f5f7fa; font-weight:400; }
.customs-sheet-grid thead th { position:sticky; top:0; z-index:1; }
.customs-sheet-grid tr>th:first-child { position:sticky; left:0; min-width:30px; width:30px; text-align:center; }
.customs-sheet-grid thead th:first-child { z-index:2; }
.preview-truncated { margin:0; padding:6px 12px; background:#fff7e6; color:#986b16; font-size:12px; }
.preview-download-error { margin-right:auto; color:#b42318; font-size:12px; }
</style>
<style>
.access-dialog.customs-attachment-preview { width:min(1000px,calc(100vw - 24px)); height:min(780px,calc(100dvh - 24px)); }
.access-dialog.customs-attachment-preview>header h2 { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.access-dialog.customs-attachment-preview>.access-dialog-body { display:flex; flex-direction:column; flex:1; padding:0; min-height:0; overflow:hidden; }
.access-dialog.customs-attachment-preview .pdf-document { width:100%; height:100%; flex:1; min-height:0; }
</style>
