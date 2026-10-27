<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import StoreTimingForm from './StoreTimingForm.vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props=defineProps<{open:boolean;targets:{id:string;label:string}[];initialId?:string|undefined;writable:boolean}>()
const emit=defineEmits<{close:[];saved:[]}>()
const dialog=ref<HTMLDialogElement>(), form=ref<InstanceType<typeof StoreTimingForm>>(), selected=ref(''), unsaved=ref(false), nextStore=ref<string>()
watch(()=>props.open,async open=>{if(open){selected.value=props.initialId??'';await nextTick();dialog.value?.showModal()}})
function discard(){unsaved.value=false;if(nextStore.value!==undefined){selected.value=nextStore.value;nextStore.value=undefined}else{dialog.value?.close();emit('close')}}
function close(){if(form.value?.busy)return;if(form.value?.dirty){nextStore.value=undefined;unsaved.value=true}else discard()}
function change(event:Event){const el=event.target as HTMLSelectElement;const id=el.value;el.value=selected.value;if(form.value?.dirty){nextStore.value=id;unsaved.value=true}else selected.value=id}
onBeforeRouteLeave(()=>{if(props.open&&form.value?.busy)return false;if(props.open&&form.value?.dirty){nextStore.value=undefined;unsaved.value=true;return false}return true})
</script>
<template>
  <dialog
    ref="dialog"
    class="mw-dialog store-schedule-dialog monitor-workbench"
    aria-label="采集设置"
    @cancel.prevent="close"
  >
    <header>
      <div>
        <h2>采集设置</h2><p class="store-subtitle">
          每个店铺独立安排采集频率与时间
        </p>
      </div><button
        :disabled="form?.busy"
        @click="close"
      >
        关闭
      </button>
    </header>
    <div class="mw-dialog-body">
      <label>选择店铺<select
        :value="selected"
        :disabled="form?.busy"
        @change="change"
      ><option value="">请选择店铺</option><option
        v-for="target in targets"
        :key="target.id"
        :value="target.id"
      >{{ target.label }}</option></select></label>
      <StoreTimingForm
        v-if="selected"
        ref="form"
        :store-id="selected"
        :writable="writable"
        hide-actions
        @saved="emit('saved')"
      />
      <p
        v-else
        class="store-settings-empty"
      >
        选择店铺后，可查看并修改它的采集计划。
      </p>
    </div>
    <footer>
      <button
        :disabled="form?.busy"
        @click="close"
      >
        关闭
      </button><button
        class="mw-primary"
        :disabled="!writable||!form?.dirty||form?.busy"
        @click="form?.save()"
      >
        {{ form?.busy?'保存中…':'保存采集计划' }}
      </button>
    </footer>
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false;nextStore=undefined"
      @discard="discard"
    />
  </dialog>
</template>
