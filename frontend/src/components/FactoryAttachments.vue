<script setup lang="ts">
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import {ref,onMounted} from 'vue'
import {apiRequest,getSessionToken} from '@/api/client'
import {runtimeConfig} from '@/config/runtime'
import type {FactoryResourceKind} from '@/api/factory'
const props=defineProps<{kind:FactoryResourceKind;id:string;writable:boolean}>()
const files=ref<{id:string;name:string;time:string}[]>([]),busy=ref(false),error=ref('')
const path=`/v1/factory/${props.kind}/${props.id}/attachments`
async function load(){try{files.value=await apiRequest(path)}catch(e){error.value=e instanceof Error?e.message:'凭证加载失败'}}
async function upload(file:File){if(busy.value)return false;error.value='';busy.value=true;try{if(file.size>5*1024*1024)throw new Error('凭证最多5MB');const body=new FormData();body.append('file',file);const response=await fetch(`${runtimeConfig.apiBaseUrl}${path}`,{method:'POST',body,headers:{Authorization:`Bearer ${getSessionToken()}`}});if(!response.ok){const problem=await response.json().catch(()=>null) as {detail?:string}|null;throw new Error(problem?.detail||'凭证上传失败')}await load()}catch(e){error.value=e instanceof Error?e.message:'上传失败'; return false }finally{busy.value=false}}
async function download(id:string,name:string){busy.value=true;error.value='';try{const r=await fetch(`${runtimeConfig.apiBaseUrl}${path}/${encodeURIComponent(id)}`,{headers:{Authorization:`Bearer ${getSessionToken()}`}});if(!r.ok)throw new Error('下载失败，请检查权限');const url=URL.createObjectURL(await r.blob()),a=document.createElement('a');a.href=url;a.download=name;a.click();URL.revokeObjectURL(url)}catch(e){error.value=e instanceof Error?e.message:'下载失败'}finally{busy.value=false}}
onMounted(load)
</script>
<template>
  <section class="factory-attachments">
    <h3>凭证附件</h3><p class="factory-help">
      PNG / JPG / PDF，单份最多5MB，每张单据最多5份。凭证仅供有权限的人员下载。
    </p><p
      v-if="error"
      role="alert"
      class="factory-error"
    >
      {{ error }}
    </p><ul>
      <li
        v-for="f in files"
        :key="f.id"
      >
        <button
          class="factory-link"
          :disabled="busy"
          @click="download(f.id,f.name)"
        >
          {{ f.name }}
        </button><small>{{ new Date(f.time).toLocaleString() }}</small>
      </li>
    </ul><SingleFileUpload
      v-if="writable"
      title="工厂凭证"
      accept=".pdf,.png,.jpg,.jpeg"
      :max-mb="5"
      :disabled="busy || files.length>=5"
      :upload="upload"
    />
  </section>
</template>
