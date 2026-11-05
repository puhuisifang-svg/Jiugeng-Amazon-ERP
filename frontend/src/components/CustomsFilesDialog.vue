<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import AccessDialog from './AccessDialog.vue'
import SingleFileUpload from './SingleFileUpload.vue'
import { customsFilesApi,type CustomsAttachment } from '@/api/customsDocuments'
import { confirmAction } from '@/state/uiConfirmation'
const props=defineProps<{id:string;documentNo:string;writable:boolean}>()
const emit=defineEmits<{close:[];changed:[]}>()
const tabs=[{key:'SCREENSHOT',label:'申报订单截图'},{key:'INVOICE',label:'发票'},{key:'OTHER',label:'其他文件'}] as const
const tab=ref<CustomsAttachment['category']>('SCREENSHOT'),files=ref<CustomsAttachment[]>([]),busy=ref(false),loading=ref(true),error=ref('')
const visible=computed(()=>files.value.filter(f=>f.category===tab.value))
const has=(category:string)=>files.value.some(f=>f.category===category)
const completed=computed(()=>has('SCREENSHOT')&&has('INVOICE'))
async function load(){loading.value=true;try{files.value=await customsFilesApi.list(props.id)}catch(e){error.value=e instanceof Error?e.message:'文件加载失败';throw e}finally{loading.value=false}}
async function upload(file:File){busy.value=true;error.value='';try{await customsFilesApi.upload(props.id,tab.value,file);emit('changed');await load()}catch(e){error.value=e instanceof Error?e.message:'上传失败';throw e}finally{busy.value=false}}
async function remove(file:CustomsAttachment){if(!await confirmAction(`移除文件「${file.name}」？移除最后一份申报截图或发票后，单据将恢复为文档待上传。`))return;busy.value=true;error.value='';try{await customsFilesApi.remove(props.id,file.id);emit('changed');await load()}catch(e){error.value=e instanceof Error?e.message:'移除失败'}finally{busy.value=false}}
async function download(file:CustomsAttachment){busy.value=true;error.value='';try{await customsFilesApi.download(props.id,file)}catch(e){error.value=e instanceof Error?e.message:'下载失败'}finally{busy.value=false}}
function key(event:KeyboardEvent){const i=tabs.findIndex(t=>t.key===tab.value);const next=event.key==='ArrowRight'?(i+1)%3:event.key==='ArrowLeft'?(i+2)%3:event.key==='Home'?0:event.key==='End'?2:-1;if(next<0||busy.value)return;event.preventDefault();tab.value=tabs[next]!.key;document.getElementById(`customs-file-tab-${tab.value}`)?.focus()}
onMounted(()=>void load().catch(()=>{}))
</script>
<template>
  <AccessDialog
    :title="`文件上传 · ${documentNo}`"
    :busy="busy"
    wide
    dialog-class="customs-files-dialog"
    @close="emit('close')"
  >
    <div class="customs-files-summary">
      <strong :class="{complete:completed}">{{ loading?'正在加载文件…':completed?'已完成':'文档待上传' }}</strong>
      <span>申报订单截图 {{ has('SCREENSHOT')?'✓ 已上传':'待上传' }}</span><span>发票 {{ has('INVOICE')?'✓ 已上传':'待上传' }}</span>
    </div>
    <p class="customs-files-help">
      上传跨境通申报订单截图及实际发票后自动完成；其他文件为选填。上传后立即保存。
    </p>
    <div
      class="customs-files-tabs"
      role="tablist"
      aria-label="文件分类"
      @keydown="key"
    >
      <button
        v-for="t in tabs"
        :id="`customs-file-tab-${t.key}`"
        :key="t.key"
        type="button"
        role="tab"
        :aria-selected="tab===t.key"
        :aria-controls="`customs-file-panel-${t.key}`"
        :tabindex="tab===t.key?0:-1"
        :disabled="busy"
        @click="tab=t.key"
      >
        {{ t.label }} <small>{{ files.filter(f=>f.category===t.key).length }}</small>
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="data-state--error"
    >
      {{ error }} <button
        :disabled="busy||loading"
        @click="load().catch(()=>{})"
      >
        重试加载
      </button>
    </p>
    <section
      :id="`customs-file-panel-${tab}`"
      role="tabpanel"
      :aria-labelledby="`customs-file-tab-${tab}`"
    >
      <div
        v-if="tab==='SCREENSHOT'"
        class="customs-files-actions"
      >
        <a
          class="customs-portal-link"
          href="https://ctp.kjtong.com/"
          target="_blank"
          rel="noopener noreferrer"
        >前往跨境通 <span aria-hidden="true">↗</span></a>
      </div>
      <SingleFileUpload
        v-if="writable"
        :key="tab"
        :title="tabs.find(t=>t.key===tab)!.label"
        :accept="tab==='SCREENSHOT'?'.png,.jpg,.jpeg':'.png,.jpg,.jpeg,.pdf,.xls,.xlsx'"
        :max-mb="10"
        :disabled="busy||loading||files.length>=30"
        :upload="upload"
        hint="选择或粘贴后立即上传；单份最多10MB，每票最多30份"
      />
      <ul class="customs-file-list">
        <li
          v-for="file in visible"
          :key="file.id"
        >
          <div>
            <button
              class="file-name"
              :disabled="busy"
              @click="download(file)"
            >
              {{ file.name }}
            </button><small>{{ (file.size/1024).toFixed(1) }} KB · {{ new Date(file.createdAt).toLocaleString('zh-CN',{hour12:false}) }}</small>
          </div>
          <button
            :disabled="busy"
            @click="download(file)"
          >
            下载
          </button><button
            v-if="writable"
            class="file-remove"
            :disabled="busy"
            @click="remove(file)"
          >
            移除
          </button>
        </li>
      </ul>
      <p
        v-if="!visible.length&&!loading"
        class="data-state"
      >
        暂无{{ tabs.find(t=>t.key===tab)!.label }}{{ tab==='OTHER'?'（选填）':'' }}
      </p>
    </section>
  </AccessDialog>
</template>
<style scoped>
.customs-files-actions{display:flex;justify-content:flex-start;margin-bottom:8px}.customs-portal-link{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:32px;padding:0 12px;border:1px solid var(--accent);border-radius:5px;color:var(--accent);background:white;font-size:13px;text-decoration:none;white-space:nowrap}.customs-portal-link:hover{background:#f0f5ff}.customs-portal-link:focus-visible{outline:2px solid var(--accent);outline-offset:2px}

.customs-files-summary{display:flex;align-items:center;gap:16px;flex-wrap:wrap;padding:12px;background:#f5f8fc;border:1px solid var(--line);border-radius:6px;font-size:13px}.customs-files-summary strong{color:#986b16}.customs-files-summary .complete{color:#21834a}.customs-files-help{font-size:12px;color:var(--muted);line-height:1.6}.customs-files-tabs{display:flex;gap:20px;border-bottom:1px solid var(--line);margin-bottom:14px;overflow-x:auto}.customs-files-tabs button{border:0;border-bottom:2px solid transparent;background:none;border-radius:0;white-space:nowrap;font-size:13px}.customs-files-tabs [aria-selected=true]{border-bottom-color:var(--accent);color:var(--accent)}.customs-files-tabs small{margin-left:5px}.customs-file-list{list-style:none;padding:0;margin:12px 0}.customs-file-list li{display:flex;align-items:center;gap:8px;padding:12px 0;border-bottom:1px solid var(--line)}.customs-file-list li>div{flex:1;min-width:0}.customs-file-list small{display:block;font-size:12px;color:var(--muted);margin-top:4px}.customs-file-list button{font-size:13px;flex-shrink:0;background:white}.customs-file-list .file-name{border:0;padding:0;color:var(--accent);text-align:left;word-break:break-all}.customs-file-list .file-remove{color:#b42318}@media(max-width:420px){.customs-files-summary{gap:8px}.customs-file-list li{flex-wrap:wrap}.customs-file-list li>div{flex-basis:100%}.customs-files-tabs{gap:6px}}
</style>
