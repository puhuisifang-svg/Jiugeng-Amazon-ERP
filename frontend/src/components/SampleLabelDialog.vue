<script setup lang="ts">
import { computed, ref } from 'vue'
import { sampleLabelPdf } from '@/utils/sampleLabelPdf'
import { downloadPdf } from '@/utils/pdfTools'
import AccessDialog from './AccessDialog.vue'
import { sampleLabelDocument, type SampleLabelData } from '@/utils/sampleLabel'
const props = defineProps<{ sample: SampleLabelData }>()
const emit = defineEmits<{ close: [] }>()
const frame = ref<HTMLIFrameElement>(), ready = ref(false), error = ref('')
const exporting = ref(false)
const documentHtml = computed(() => sampleLabelDocument(props.sample))
async function fit() {
  ready.value = false; error.value = ''
  const doc = frame.value?.contentDocument
  if (!doc) return
  await doc.fonts?.ready
  const content = doc.querySelector<HTMLElement>('.content'), label = doc.querySelector<HTMLElement>('.label')
  if (!content || !label) return
  const style = getComputedStyle(label), available = label.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
  for (let size = 12; size >= 8; size -= .25) {
    content.style.fontSize = `${size}px`
    if (content.scrollHeight <= available && content.scrollWidth <= content.clientWidth) { ready.value = true; return }
  }
  error.value = '内容过长，70×30毫米标签无法完整清晰展示，请先精简品名或规格。'
}
async function exportPdf() {
  if (!ready.value || exporting.value || !frame.value?.contentDocument) return
  exporting.value = true; error.value = ''
  try {
    const bytes = await sampleLabelPdf(frame.value.contentDocument)
    const filename = `样品标签-${props.sample.skuName}-${props.sample.locationCode}`.replace(/[\\/:*?"<>|]/g, '_').slice(0, 120)
    downloadPdf(bytes, `${filename}.pdf`)
  } catch (e) { error.value = e instanceof Error ? e.message : '导出失败，请重试' }
  finally { exporting.value = false }
}
function print() {
  if (!ready.value) return
  try { frame.value?.contentWindow?.focus(); frame.value?.contentWindow?.print() }
  catch { error.value = '无法打开打印窗口，请重试。' }
}
</script>
<template>
  <AccessDialog
    title="样品标签打印"
    :busy="exporting"
    @close="emit('close')"
  >
    <div class="sample-label-meta">
      <strong>70 × 30 mm</strong><span>样品库位标签</span>
    </div>
    <div class="sample-label-preview">
      <iframe
        ref="frame"
        title="样品标签预览"
        :srcdoc="documentHtml"
        @load="fit"
      />
    </div>
    <p class="sample-label-hint">
      打印请选择70×30毫米纸张、实际大小（100%），关闭页眉页脚。
    </p>
    <p
      v-if="error"
      class="sample-label-error"
      role="alert"
    >
      {{ error }}
    </p>
    <template #footer>
      <button
        type="button"
        class="sample-label-export"
        :disabled="!ready || exporting"
        @click="exportPdf"
      >
        {{ exporting ? '正在导出…' : '导出 PDF' }}
      </button>
      <button
        type="button"
        :disabled="exporting"
        @click="emit('close')"
      >
        关闭
      </button><button
        type="button"
        class="primary-button"
        :disabled="!ready || exporting"
        @click="print"
      >
        打印
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.sample-label-export{margin-right:auto;color:#2864c5;border-color:#b7cbed!important;background:#f5f9ff;}
.sample-label-meta{display:flex;align-items:center;gap:12px;font-size:13px;color:#64748b;margin-bottom:12px}.sample-label-meta strong{color:#334155}.sample-label-preview{padding:20px;background:#f1f4f8;border-radius:6px;overflow:auto}.sample-label-preview iframe{display:block;width:70mm;height:30mm;border:0;margin:auto;background:white;box-shadow:0 2px 8px #00000015}.sample-label-hint{font-size:12px;color:#64748b;line-height:1.6;margin:12px 0 0}.sample-label-error{color:#b42318;font-size:13px}
</style>
