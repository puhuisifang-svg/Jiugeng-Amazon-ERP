<script setup lang="ts">
import { beijingDate } from '@/utils/dateTime'


import { onMounted,onBeforeUnmount,ref } from 'vue'
import type { PurchaseOrder } from '@/api/purchases'
import { exportPages,purchaseExportRows,renderPurchasePage } from '@/ui/purchaseImageExport'
import AccessDialog from './AccessDialog.vue'
const props=defineProps<{orders:PurchaseOrder[]}>();const emit=defineEmits<{close:[]}>()
const pages=ref<string[]>([]),warnings=ref<string[]>([]),busy=ref(true),error=ref(''),completed=ref(0),current=ref(0)
const rows=purchaseExportRows(props.orders);let alive=true
async function generate(){
  try{await document.fonts.ready;for(const batch of exportPages(rows)){if(!alive)return;const result=await renderPurchasePage(batch,()=>completed.value++);if(!alive)return;pages.value.push(URL.createObjectURL(result.blob));warnings.value.push(...result.warnings)}}catch(e){error.value=e instanceof Error?e.message:'导出失败，请关闭重试'}finally{busy.value=false}
}
function download(){const url=pages.value[current.value];if(!url)return;const link=document.createElement('a');link.href=url;link.download=`采购清单-${beijingDate()}-${current.value+1}.png`;document.body.append(link);link.click();link.remove()}
onMounted(generate);onBeforeUnmount(()=>{alive=false;pages.value.forEach(url=>URL.revokeObjectURL(url))})
</script>
<template>
  <AccessDialog
    title="导出采购图片"
    wide
    @close="!busy && emit('close')"
  >
    <p
      v-if="busy"
      role="status"
    >
      正在生成图片：{{ completed }} / {{ rows.length }} 项…
    </p>
    <p
      v-if="error"
      class="error-message"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="warnings.length"
      class="error-message"
      role="alert"
    >
      {{ warnings.length }} 项图片加载失败：{{ warnings.join('、') }}。导出中已标注，可关闭后重试。
    </p>
    <p class="export-note">
      已选 {{ orders.length }} 张采购单，共 {{ rows.length }} 项。按采购日期分组，每张图片最多 20 项。
    </p>
    <div
      v-if="pages.length"
      class="purchase-export-preview"
    >
      <img
        :src="pages[current]"
        alt="采购清单导出预览"
      >
    </div>
    <template #footer>
      <span>第 {{ pages.length ? current+1 : 0 }} / {{ pages.length }} 张</span>
      <button
        type="button"
        :disabled="busy || current===0"
        @click="current--"
      >
        上一张
      </button>
      <button
        type="button"
        :disabled="busy || current>=pages.length-1"
        @click="current++"
      >
        下一张
      </button>
      <button
        type="button"
        :disabled="busy"
        @click="emit('close')"
      >
        关闭
      </button>
      <button
        type="button"
        class="primary-button"
        :disabled="busy || !pages.length"
        @click="download"
      >
        下载当前图片
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.export-note{font-size:12px;color:var(--muted);margin:0 0 10px}.purchase-export-preview{background:#f1f5f9;padding:12px;text-align:center}.purchase-export-preview img{width:100%;height:auto;display:block}
</style>
