<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { sampleLabelPdf } from '@/utils/sampleLabelPdf'
import { downloadPdf } from '@/utils/pdfTools'
import AccessDialog from './AccessDialog.vue'
import { sampleLabelsDocument, type SampleLabelData } from '@/utils/sampleLabel'
const props = defineProps<{ sample?: SampleLabelData; samples?: SampleLabelData[] }>()
const emit = defineEmits<{ close: [] }>()
const frame = ref<HTMLIFrameElement>(), ready = ref(false), error = ref(''), exporting = ref(false)
const rows=computed(()=>props.samples ?? (props.sample?[props.sample]:[])), copies=ref<Record<number,number>>({})
watch(rows,value=>{copies.value=Object.fromEntries(value.map((_row,i)=>[i,1]))},{immediate:true})
const total=computed(()=>rows.value.reduce((sum,_row,i)=>sum+(copies.value[i]??1),0))
const valid=computed(()=>rows.value.length>0&&rows.value.length<=100&&total.value<=500&&rows.value.every((_r,i)=>Number.isInteger(copies.value[i]??1)&&(copies.value[i]??1)>0&&(copies.value[i]??1)<=100))
const expanded=computed(()=>valid.value?rows.value.flatMap((r,i)=>Array.from({length:copies.value[i]??1},()=>r)):[])
const documentHtml = computed(() => expanded.value.length?sampleLabelsDocument(expanded.value):'')
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
    if(!fits)bad.push(`${expanded.value[i]?.skuName}（${expanded.value[i]?.locationCode}）`)
  })
  if(bad.length)error.value=`以下样品内容过长，70×30毫米无法清晰展示，请精简品名或规格：${[...new Set(bad)].join('、')}`
  else ready.value=labels.length===expanded.value.length&&labels.length>0
}
async function exportPdf() {
  if (!ready.value || exporting.value || !frame.value?.contentDocument) return
  exporting.value = true; error.value = ''
  try { downloadPdf(await sampleLabelPdf(frame.value.contentDocument), `样品标签-${total.value}张.pdf`) }
  catch (e) { error.value = e instanceof Error ? e.message : '导出失败，请重试' }
  finally { exporting.value = false }
}
function print() {if(!ready.value||exporting.value)return;try{frame.value?.contentWindow?.focus();frame.value?.contentWindow?.print()}catch{error.value='无法打开打印窗口，请重试。'}}
function change(){ready.value=false;error.value=''}
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
    <div class="sample-label-quantities">
      <label
        v-for="(row,i) in rows"
        :key="i"
      ><span>{{ row.skuName }} · {{ row.locationCode }}</span><input
        v-model.number="copies[i]"
        type="number"
        min="1"
        max="100"
        :placeholder="'1'"
        :aria-label="`${row.skuName} 标签份数`"
        :disabled="exporting"
        @input="change"
      >份</label>
    </div>
    <p
      v-if="!valid"
      role="alert"
      class="access-danger"
    >
      每条 1～100 份，最多 100 条样品、合计 500 张标签。
    </p>
    <div
      v-else
      class="sample-label-preview"
    >
      <iframe
        ref="frame"
        title="样品标签预览"
        :srcdoc="documentHtml"
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
.sample-label-meta{display:flex;gap:12px;color:var(--muted);font-size:13px;margin-bottom:10px}.sample-label-quantities{max-height:150px;overflow:auto;margin-bottom:10px}.sample-label-quantities label{display:flex;align-items:center;gap:8px;padding:4px 0;font-size:13px}.sample-label-quantities label span{flex:1}.sample-label-quantities input{width:64px;min-height:32px;border:1px solid var(--line);padding:4px;text-align:right}.sample-label-preview{padding:14px;background:#f1f4f8;border-radius:6px;overflow:auto}.sample-label-preview iframe{display:block;width:calc(70mm + 18px);height:240px;max-width:100%;border:0;margin:auto;background:white}.sample-label-hint{font-size:12px;color:var(--muted);line-height:1.6;margin:10px 0 0}
</style>
