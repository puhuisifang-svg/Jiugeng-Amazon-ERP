<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { sampleLabelPdf } from '@/utils/sampleLabelPdf'
import { downloadPdf } from '@/utils/pdfTools'
import AccessDialog from './AccessDialog.vue'
import { sampleLabelsDocument, sampleLabelFilename, type SampleLabelData } from '@/utils/sampleLabel'
const props = defineProps<{ sample?: SampleLabelData; samples?: SampleLabelData[] }>()
const emit = defineEmits<{ close: [] }>()
const frame = ref<HTMLIFrameElement>(), ready = ref(false), error = ref(''), exporting = ref(false)
const rows = computed(() => props.samples ?? (props.sample ? [props.sample] : []))
const total = computed(() => rows.value.length)
const valid = computed(() => total.value > 0 && total.value <= 100)
const documentHtml = computed(() => valid.value ? sampleLabelsDocument(rows.value) : '')
const previewHeight = computed(() => `calc(${30 * total.value}mm + ${12 * Math.max(0, total.value - 1)}px)`)
watch(documentHtml, () => { ready.value = false; error.value = '' })
async function fit() {
  ready.value = false; error.value = ''
  const doc = frame.value?.contentDocument
  if (!doc || !valid.value) return
  await doc.fonts?.ready
  if(doc!==frame.value?.contentDocument)return
  const labels=[...doc.querySelectorAll<HTMLElement>('.label')],bad:string[]=[]
  labels.forEach((label,i)=>{
    const content=label.querySelector<HTMLElement>('.content');if(!content)return
    const style=doc.defaultView!.getComputedStyle(label), available=label.clientHeight-parseFloat(style.paddingTop)-parseFloat(style.paddingBottom)
    let fits=false
    for(let size=12;size>=8;size-=.25){content.style.fontSize=`${size}px`;if(content.scrollHeight<=available&&content.scrollWidth<=content.clientWidth){fits=true;break}}
    if(!fits)bad.push(`${rows.value[i]?.skuName}（${rows.value[i]?.locationCode}）`)
  })
  if(bad.length)error.value=`以下样品内容过长，70×30毫米无法清晰展示，请精简品名或规格：${[...new Set(bad)].join('、')}`
  else ready.value=labels.length===rows.value.length&&labels.length>0
}
async function exportPdf() {
  if (!ready.value || exporting.value || !frame.value?.contentDocument) return
  exporting.value = true; error.value = ''
  try { downloadPdf(await sampleLabelPdf(frame.value.contentDocument), sampleLabelFilename(rows.value)) }
  catch (e) { error.value = e instanceof Error ? e.message : '导出失败，请重试' }
  finally { exporting.value = false }
}
function print() {if(!ready.value||exporting.value)return;try{frame.value?.contentWindow?.focus();frame.value?.contentWindow?.print()}catch{error.value='无法打开打印窗口，请重试。'}}
</script>
<template>
  <AccessDialog
    title="样品标签打印"
    dialog-class="samples-ui sample-label-dialog"
    :busy="exporting"
    @close="emit('close')"
  >
    <div class="sample-label-meta">
      <strong>70 × 30 mm</strong><span>{{ rows.length }} 条样品 · {{ total }} 张标签</span>
    </div>
    <p
      v-if="!valid"
      role="alert"
      class="access-danger"
    >
      每条样品打印 1 张，一次最多选择 100 条样品。
    </p>
    <div
      v-else
      class="sample-label-preview"
    >
      <iframe
        ref="frame"
        title="样品标签预览"
        :srcdoc="documentHtml"
        :style="{ height: previewHeight }"
        @load="fit"
      />
    </div>
    <p class="sample-label-hint">
      打印请选择70×30毫米纸张、实际大小（100%），关闭页眉页脚。每张标签独立一页。
    </p>
    <p
      v-if="error"
      role="alert"
      class="access-danger"
    >
      {{ error }}
    </p>
    <template #footer>
      <button
        type="button"
        :disabled="!ready || !valid || exporting"
        @click="exportPdf"
      >
        {{ exporting?'正在导出…':'导出 PDF' }}
      </button><button
        type="button"
        :disabled="exporting"
        @click="emit('close')"
      >
        关闭
      </button><button
        type="button"
        class="primary-button"
        :disabled="!ready || !valid || exporting"
        @click="print"
      >
        打印
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.sample-label-meta{display:flex;align-items:center;gap:12px;flex-wrap:wrap;color:var(--muted);font-size:13px;margin-bottom:10px}
.sample-label-meta strong{color:var(--ink)}
.sample-label-preview{padding:14px;background:#f1f4f8;border:1px solid var(--line);border-radius:6px;overflow:auto;max-height:360px}
.sample-label-preview iframe{display:block;width:70mm;min-width:70mm;border:0;margin:auto;background:transparent}
.sample-label-hint{font-size:12px;color:var(--muted);line-height:1.6;margin:10px 0 0}
</style>
