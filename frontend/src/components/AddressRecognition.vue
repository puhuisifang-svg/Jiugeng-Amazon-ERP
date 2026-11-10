<script setup lang="ts">
import { ref } from 'vue'
import {recognizeAddress,type RecognizedAddress} from '@/utils/recognizeAddress'
defineProps<{disabled?:boolean}>()
const emit=defineEmits<{recognize:[RecognizedAddress]}>()
const text=ref(''),message=ref('')
function parse(value=text.value){const result=recognizeAddress(value);if(!value.trim()){message.value='请先粘贴联系人、电话和地址';return}emit('recognize',result);message.value=result.contactPerson&&result.phone&&result.address?'已识别，请核对下方信息':'已填入可识别内容，请补齐并核对下方信息'}
function paste(e:ClipboardEvent){const value=e.clipboardData?.getData('text/plain');if(value){e.preventDefault();text.value=value;parse(value)}}
</script>
<template>
  <div class="address-recognition">
    <label>智能识别<textarea
      v-model="text"
      :disabled="disabled"
      rows="2"
      maxlength="1000"
      placeholder="粘贴联系人、手机号和详细地址，自动识别后可在下方修改"
      @paste="paste"
    /></label>
    <div>
      <span aria-live="polite">{{ message || '本地识别，不上传至外部服务' }}</span><button
        type="button"
        :disabled="disabled || !text.trim()"
        @click="parse()"
      >
        识别并填入
      </button>
    </div>
  </div>
</template>
<style scoped>
.address-recognition{min-width:0;background:#f0f6ff;border:1px solid #dce8fa;border-radius:5px;padding:10px}.address-recognition label{display:grid;gap:6px}.address-recognition textarea{width:100%;box-sizing:border-box;resize:vertical}.address-recognition>div{display:flex;gap:8px;align-items:center;justify-content:space-between;margin-top:6px}.address-recognition span{color:var(--muted);font-size:12px}.address-recognition button{white-space:nowrap;flex-shrink:0}
</style>
