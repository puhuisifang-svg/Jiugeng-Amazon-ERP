<script setup lang="ts">
import {onBeforeUnmount,ref,watch} from 'vue'
const props=withDefaults(defineProps<{modelValue:File[];existingCount:number;disabled?:boolean;title?:string;accept?:string;maxMb?:number;maxCount?:number;single?:boolean;hint?:string;pasteHint?:string;fileStatus?:string;removeLabel?:string;compact?:boolean}>(),{title:'凭证附件',accept:'.png,.jpg,.jpeg,.pdf',maxMb:5,maxCount:5,hint:'保存账单后上传',pasteHint:'或在此账单中按 Ctrl+V / ⌘V 粘贴图片',fileStatus:'待保存',removeLabel:'移除'})
const formats=()=>props.accept.split(',').map(x=>x.replace('.','').toUpperCase()).join(' / ')
function allowed(f:File){return props.accept.split(',').some(ext=>f.name.toLowerCase().endsWith(ext.trim().toLowerCase()))}
const emit=defineEmits<{'update:modelValue':[File[]]}>()
const input=ref<HTMLInputElement>(),error=ref(''),previews=ref<string[]>([])
watch(()=>props.modelValue,files=>{previews.value.forEach(u=>{if(u)URL.revokeObjectURL(u)});previews.value=files.map(f=>/^image\/(png|jpeg|webp)$/.test(f.type)?URL.createObjectURL(f):'')},{immediate:true})
onBeforeUnmount(()=>previews.value.forEach(u=>{if(u)URL.revokeObjectURL(u)}))
function add(files:File[]){if(props.disabled||!files.length)return;error.value='';if(props.single&&files.length>1){error.value='此处每次只能选择一份文件';return}if(!props.single&&files.length+props.modelValue.length+props.existingCount>props.maxCount){error.value=`每笔账单最多${props.maxCount}个凭证，请移除后再添加`;return}if(files.some(f=>!f.size)){error.value='文件不能为空';return}if(files.some(f=>f.size>props.maxMb*1024*1024||!allowed(f))){error.value=`请选择${props.maxMb}MB以内的 ${formats()} 文件`;return}emit('update:modelValue',props.single?files:[...props.modelValue,...files])}
function choose(e:Event){const target=e.target as HTMLInputElement;add(Array.from(target.files??[]));target.value=''}
function paste(e:ClipboardEvent){if(props.disabled||e.defaultPrevented)return;const images=Array.from(e.clipboardData?.items??[]).filter(i=>i.kind==='file'&&i.type.startsWith('image/'));if(!images.length)return;e.preventDefault();const files:File[]=[];for(const item of images){const f=item.getAsFile();if(!f)continue;const ext=({'image/png':'png','image/jpeg':'jpg','image/webp':'webp'} as Record<string,string>)[f.type];if(!ext||!allowed(new File([],`image.${ext}`))){error.value=`粘贴图片仅支持 ${props.accept.includes('.webp')?'PNG、JPG 或 WEBP':'PNG 或 JPG'}，请转换后重试`;return}files.push(new File([f],`粘贴图片-${Date.now()}-${files.length+1}.${ext}`,{type:f.type}))}add(files)}
function remove(i:number){if(props.disabled)return;emit('update:modelValue',props.modelValue.filter((_,index)=>index!==i));error.value=''}
defineExpose({paste})
</script>
<template>
  <div
    class="voucher-upload"
    :class="{'voucher-upload--compact':compact}"
  >
    <div
      v-if="!compact"
      class="voucher-upload-heading"
    >
      <strong>{{ title }}</strong><span v-if="!single">{{ existingCount+modelValue.length }} / {{ maxCount }}</span>
    </div>
    <div
      class="voucher-upload-zone"
      tabindex="0"
      :title="compact ? `${formats()} · ≤${maxMb}MB · ${hint}` : undefined"
      :aria-label="`${title}上传区域，可粘贴图片`"
      :aria-disabled="disabled"
      @paste.stop="paste"
    >
      <span
        v-if="!compact"
        class="voucher-upload-icon"
        aria-hidden="true"
      >＋</span>
      <div>
        <button
          type="button"
          class="voucher-upload-select"
          :disabled="disabled||(!single&&existingCount+modelValue.length>=maxCount)"
          @click="input?.click()"
        >
          {{ compact?'上传':'选择文件' }}
        </button><span class="voucher-upload-hint">{{ pasteHint }}</span><p v-if="!compact">
          {{ formats() }} · 每个不超过 {{ maxMb }}MB · {{ hint }}
        </p>
      </div>
      <input
        ref="input"
        class="voucher-upload-input"
        :aria-label="`上传${title}`"
        type="file"
        :accept="accept"
        :multiple="!single"
        :disabled="disabled"
        @change="choose"
      >
    </div>
    <p
      v-if="error"
      class="voucher-upload-error"
      role="alert"
    >
      {{ error }}
    </p>
    <ul
      v-if="modelValue.length"
      class="voucher-upload-files"
      :aria-label="single?'已选择文件':'待保存凭证'"
    >
      <li
        v-for="(file,i) in modelValue"
        :key="i"
      >
        <img
          v-if="previews[i]"
          :src="previews[i]"
          :alt="file.name"
        ><span
          v-else
          class="voucher-upload-pdf"
        >{{ file.name.split('.').pop()?.toUpperCase() || '文件' }}</span><div>
          <span
            class="voucher-upload-name"
            :title="file.name"
          >{{ file.name }}</span><small>{{ (file.size/1024).toFixed(1) }} KB · {{ fileStatus }}</small>
        </div><button
          type="button"
          :aria-label="`${removeLabel}${single?'文件':'待保存凭证'} ${file.name}`"
          :disabled="disabled"
          @click="remove(i)"
        >
          {{ removeLabel }}
        </button>
      </li>
    </ul>
  </div>
</template>
<style scoped>
.voucher-upload{container-type:inline-size;min-width:0;width:100%;text-align:left;flex:1 1 260px;color:var(--ink)}.voucher-upload-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;font-size:13px}.voucher-upload-heading span{color:var(--muted);font-size:12px}.voucher-upload-zone{position:relative;display:flex;gap:12px;align-items:center;padding:14px;border:1px dashed #b7c8df;border-radius:6px;background:#f7faff;min-width:0}.voucher-upload-zone:focus-visible{outline:2px solid var(--accent);outline-offset:2px}.voucher-upload-icon{color:var(--accent);font-size:26px;flex:none}.voucher-upload-zone>div{min-width:0}.voucher-upload-select{background:white;color:var(--accent);border:1px solid #b7c8df;min-height:32px;padding:5px 12px;border-radius:4px;font-size:13px}.voucher-upload-hint{font-size:12px;color:var(--muted);margin-left:10px}.voucher-upload-zone p{margin:7px 0 0;font-size:12px;color:var(--muted);line-height:1.6}.voucher-upload-input{display:none!important}.voucher-upload-error{color:#b42318;font-size:12px;margin:8px 0}.voucher-upload-files{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;padding:0!important;list-style:none;margin:10px 0 0}.voucher-upload-files li{display:flex;align-items:center;gap:10px;border:1px solid var(--line);border-radius:5px;padding:8px;min-width:0}.voucher-upload-files img,.voucher-upload-pdf{width:44px;height:44px;object-fit:contain;border-radius:4px;background:#f3f6fa;flex:none}.voucher-upload-pdf{display:grid;place-items:center;color:#af4338;font-size:12px}.voucher-upload-files li>div{flex:1;min-width:0}.voucher-upload-name{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.voucher-upload-files small{display:block;margin-top:4px;color:var(--muted);font-size:12px}.voucher-upload-files button{border:0;background:transparent;color:#b42318;padding:5px;font-size:12px;flex:none}.voucher-upload button:disabled{opacity:.5;cursor:not-allowed}@media(max-width:640px){.voucher-upload-files{grid-template-columns:minmax(0,1fr)}.voucher-upload-hint{display:block;margin:8px 0 0}.voucher-upload-zone{padding:10px}}
@container(max-width:420px){.voucher-upload-files{grid-template-columns:minmax(0,1fr)}.voucher-upload-hint{display:block;margin:8px 0 0}.voucher-upload-zone{padding:10px}.voucher-upload-files li{flex-wrap:wrap}.voucher-upload-files li>div{min-width:70px}.voucher-upload-files button{margin-left:auto}}
.voucher-upload--compact .voucher-upload-zone{padding:5px 7px;gap:5px;border-radius:4px}.voucher-upload--compact .voucher-upload-zone>div{display:flex;align-items:center;gap:6px;flex-wrap:wrap}.voucher-upload--compact .voucher-upload-hint{display:inline;margin:0;font-size:12px}.voucher-upload--compact .voucher-upload-select{padding:3px 8px;min-height:28px}.voucher-upload--compact .voucher-upload-files{margin:5px 0 0;grid-template-columns:1fr;gap:4px}.voucher-upload--compact .voucher-upload-files li{padding:4px;gap:4px}.voucher-upload--compact .voucher-upload-files img,.voucher-upload--compact .voucher-upload-pdf{width:24px;height:24px}.voucher-upload--compact .voucher-upload-files small{margin-top:0}
</style>
