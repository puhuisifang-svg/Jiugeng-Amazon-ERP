<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { financeApi, type BillAttachment } from '@/api/finance'
const props=defineProps<{billId:string;file:BillAttachment}>()
const src=ref(''),failed=ref(false),dialog=ref<HTMLDialogElement>()
function clear(){if(src.value)URL.revokeObjectURL(src.value);src.value=''}
watch(()=>[props.billId,props.file.id],async(_value,_old,onCleanup)=>{clear();failed.value=false;if(!props.file.mediaType.startsWith('image/'))return;let active=true;onCleanup(()=>{active=false});try{const blob=await financeApi.attachmentBlob(props.billId,props.file.id);if(active)src.value=URL.createObjectURL(blob)}catch{if(active)failed.value=true}},{immediate:true})
onBeforeUnmount(()=>{clear();dialog.value?.close()})
</script>
<template>
  <div class="finance-voucher">
    <button
      v-if="src"
      type="button"
      class="image-button"
      :aria-label="`预览凭证 ${file.name}`"
      @click="dialog?.showModal()"
    >
      <img
        :src="src"
        :alt="file.name"
      >
    </button>
    <span
      v-else-if="failed"
      class="muted"
    >预览加载失败，可下载查看</span>
    <dialog
      ref="dialog"
      :aria-label="`凭证预览 ${file.name}`"
    >
      <header>
        <span>{{ file.name }}</span><button
          type="button"
          aria-label="关闭凭证预览"
          @click="dialog?.close()"
        >
          ×
        </button>
      </header><img
        v-if="src"
        :src="src"
        :alt="file.name"
      >
    </dialog>
  </div>
</template>
<style scoped>
.finance-voucher>.image-button{width:120px;height:90px;display:block}.finance-voucher>.image-button img{width:100%;height:100%;object-fit:contain}.finance-voucher dialog{padding:12px;border:0;border-radius:10px;max-width:90vw;max-height:90dvh;overflow:auto}.finance-voucher dialog::backdrop{background:#17203399}.finance-voucher header{display:flex;gap:16px;align-items:center;justify-content:space-between;margin-bottom:8px}.finance-voucher header button{min-width:32px;min-height:32px;cursor:pointer}.finance-voucher dialog img{max-width:85vw;max-height:75dvh;object-fit:contain}
</style>
