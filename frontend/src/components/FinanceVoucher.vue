<script setup lang="ts">
import ImagePreviewDialog from './ImagePreviewDialog.vue'
import { onBeforeUnmount, ref, watch } from 'vue'
import { financeApi, type BillAttachment } from '@/api/finance'
const props=defineProps<{billId:string;file:BillAttachment}>()
const src=ref(''),failed=ref(false),preview=ref(false)
function clear(){if(src.value)URL.revokeObjectURL(src.value);src.value=''}
watch(()=>[props.billId,props.file.id],async(_value,_old,onCleanup)=>{clear();failed.value=false;if(!props.file.mediaType.startsWith('image/'))return;let active=true;onCleanup(()=>{active=false});try{const blob=await financeApi.attachmentBlob(props.billId,props.file.id);if(active)src.value=URL.createObjectURL(blob)}catch{if(active)failed.value=true}},{immediate:true})
onBeforeUnmount(()=>{clear()})
</script>
<template>
  <div class="finance-voucher">
    <button
      v-if="src"
      type="button"
      class="image-button"
      :aria-label="`预览凭证 ${file.name}`"
      @click="preview=true"
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
    <ImagePreviewDialog
      v-if="preview"
      :title="`凭证预览 ${file.name}`"
      :description="file.name"
      @close="preview = false"
    >
      <img
        v-if="src"
        :src="src"
        :alt="file.name"
      >
    </ImagePreviewDialog>
  </div>
</template>
<style scoped>
.finance-voucher>.image-button{width:120px;height:90px;display:block}.finance-voucher>.image-button img{width:100%;height:100%;object-fit:contain}</style>
