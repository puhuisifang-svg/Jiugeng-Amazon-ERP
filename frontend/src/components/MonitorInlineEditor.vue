<script setup lang="ts">
import {computed,nextTick,ref,watch} from 'vue'
import {useAnchoredPopover} from '@/composables/useAnchoredPopover'
import {onBeforeRouteLeave} from 'vue-router'
import {monitoringApi,type MonitorTarget} from '@/api/monitoring'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props=defineProps<{target:MonitorTarget|undefined;field:'remark'|'sales30Days';anchor?:HTMLElement|undefined}>()
const emit=defineEmits<{close:[];saved:[]}>()
const dialog=ref<HTMLDialogElement>(),value=ref(''),baseline=ref(''),busy=ref(false),error=ref(''),unsaved=ref(false)
const {position,reposition}=useAnchoredPopover(dialog,()=>props.anchor)
const title=computed(()=>props.field==='remark'?'编辑备注':'编辑近30天销量')
watch(()=>props.target,async row=>{if(!row){dialog.value?.close();return}value.value=props.field==='remark'?row.remark:String(row.sales30Days??'');baseline.value=value.value;error.value='';await nextTick();dialog.value?.showModal();reposition();dialog.value?.querySelector<HTMLElement>('textarea,input')?.focus()},{immediate:true})
function close(){if(busy.value)return;if(value.value!==baseline.value)unsaved.value=true;else discard()}
function discard(){unsaved.value=false;dialog.value?.close();emit('close');props.anchor?.focus()}
onBeforeRouteLeave(()=>{if(!props.target)return true;if(busy.value)return false;if(value.value!==baseline.value){unsaved.value=true;return false}return true})
async function save(){if(!props.target||busy.value)return;busy.value=true;error.value='';try{await monitoringApi.edit(props.target.id,{version:props.target.version,field:props.field,value:String(value.value)});dialog.value?.close();emit('saved')}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}}
</script>
<template>
  <dialog
    ref="dialog"
    class="mw-dialog mw-remark-popover"
    :style="position"
    :aria-label="title"
    @click.self="close"
    @cancel.prevent="close"
  >
    <header>
      <h2>{{ field==='remark'?'修改备注':'修改近30天销量' }}</h2><button
        :disabled="busy"
        aria-label="关闭编辑"
        @click="close"
      >
        ✕
      </button>
    </header>
    <form
      id="monitor-inline-form"
      class="mw-dialog-body"
      @submit.prevent="save"
    >
      <p
        v-if="error"
        role="alert"
        class="mw-error"
      >
        {{ error }}
      </p>
      <label
        v-if="field==='remark'"
        class="mw-remark-label"
      >备注<textarea
        v-model="value"
        maxlength="1000"
        placeholder="填写产品备注"
        :disabled="busy"
      /></label>
      <p v-if="field==='remark'">
        {{ String(value).length }} / 1000
      </p>
      <label
        v-else
        class="mw-remark-label"
      >近30天销量<input
        v-model="value"
        type="number"
        min="0"
        max="999999999"
        step="1"
        :disabled="busy"
      ><small>手动录入；留空表示未知，采集不会覆盖。</small></label>
    </form>
    <footer>
      <button
        type="button"
        :disabled="busy"
        @click="close"
      >
        取消
      </button><button
        class="mw-primary"
        type="submit"
        form="monitor-inline-form"
        :disabled="busy"
      >
        {{ busy?'保存中…':'保存' }}
      </button>
    </footer>
  </dialog>
  <UnsavedChangesDialog
    v-if="unsaved"
    @keep="unsaved=false"
    @discard="discard"
  />
</template>
