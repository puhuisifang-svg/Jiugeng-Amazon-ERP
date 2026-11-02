<script setup lang="ts">
import {computed,ref} from 'vue'
import VoucherUpload from './VoucherUpload.vue'
const props=withDefaults(defineProps<{title:string;accept?:string;maxMb?:number;disabled?:boolean;upload:(file:File)=>Promise<unknown>|unknown;deferred?:boolean;hint?:string}>(),{accept:'.png,.jpg,.jpeg,.pdf',maxMb:5,hint:''})
const files=ref<File[]>([]),working=ref(false),failed=ref(false),status=ref('')
const unavailable=computed(()=>props.disabled||working.value)
async function send(){if(unavailable.value||!files.value[0])return;working.value=true;failed.value=false;status.value=props.deferred?'正在准备…':'正在上传…';try{const result=await props.upload(files.value[0]);if(result===false)throw new Error('upload failed');status.value=props.deferred?'待保存': '已上传'}catch{failed.value=true;status.value='上传失败，可重试'}finally{working.value=false}}
async function select(next:File[]){if(unavailable.value)return;files.value=next;failed.value=false;status.value='';if(next.length)await send()}
</script>
<template>
  <div
    class="single-file-upload"
    :aria-busy="working"
  >
    <VoucherUpload
      :model-value="files"
      :existing-count="0"
      single
      :title="title"
      :accept="accept"
      :max-mb="maxMb"
      :disabled="unavailable"
      :hint="hint||(deferred?'保存表单后上传':'选择或粘贴后立即上传')"
      paste-hint="点击此区域，按 Ctrl+V / ⌘V 粘贴图片"
      :file-status="status"
      remove-label="收起预览"
      @update:model-value="select"
    />
    <p
      v-if="failed"
      role="alert"
    >
      上传失败，请重试或重新选择文件。<button
        type="button"
        :disabled="unavailable"
        @click="send"
      >
        重试上传
      </button>
    </p>
    <p
      v-else-if="files.length&&!working"
      class="upload-selection-note"
    >
      {{ deferred?'该图片尚未保存，请完成表单保存。':'上传结果见页面记录；收起预览不会删除已上传文件。' }}
    </p>
  </div>
</template>
<style scoped>
.single-file-upload{width:100%;min-width:0;flex:1 1 260px;grid-column:1/-1}.single-file-upload>p{margin:6px 0;font-size:12px;line-height:1.6;color:var(--muted)}.single-file-upload>[role=alert]{color:#b42318}.single-file-upload>p button{padding:4px 8px;border:1px solid var(--line);background:white;border-radius:4px;color:var(--accent);font-size:12px}
</style>
