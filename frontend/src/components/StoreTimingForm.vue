<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { apiRequest } from '@/api/client'
import SuccessToast from './SuccessToast.vue'
interface Timing { intervalDays:number; localTime:string; timeZone:string; version:number; nextAt:string; enabled:boolean }
const props=defineProps<{storeId:string;writable:boolean;hideActions?:boolean}>()
const emit=defineEmits<{saved:[]}>()
const form=ref<HTMLFormElement>()
const draft=ref<Timing>(), baseline=ref(''), loading=ref(false), busy=ref(false), error=ref(''), notice=ref('')
const dirty=computed(()=>!!draft.value&&JSON.stringify(draft.value)!==baseline.value)
let request=0
watch(()=>props.storeId,async id=>{const current=++request;draft.value=undefined;baseline.value='';error.value='';notice.value='';if(!id)return;loading.value=true;try{const result=await apiRequest<Timing>(`/v1/collection/stores/${id}/timing`);if(current===request){draft.value=result;baseline.value=JSON.stringify(result)}}catch(e){if(current===request)error.value=e instanceof Error?e.message:'加载采集计划失败'}finally{if(current===request)loading.value=false}},{immediate:true})
async function save(){if(!draft.value||!props.writable||busy.value)return;if(!form.value?.reportValidity())return;busy.value=true;error.value='';notice.value='';try{draft.value=await apiRequest<Timing>(`/v1/collection/stores/${props.storeId}/timing`,{method:'PUT',body:JSON.stringify(draft.value)});baseline.value=JSON.stringify(draft.value);notice.value='采集计划已保存';emit('saved')}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}}
defineExpose({dirty,busy,loading,save})
</script>
<template>
  <section
    class="store-settings-card"
    aria-label="自动采集计划"
  >
    <div class="store-card-heading">
      <div><h3>自动采集计划</h3><p>为当前店铺安排更新；手动刷新不会改变定时计划。</p></div><span class="store-badge">按店铺设置</span>
    </div>
    <p
      v-if="loading"
      role="status"
    >
      正在加载采集计划…
    </p>
    <p
      v-if="error"
      class="mw-error"
      role="alert"
    >
      {{ error }}
    </p>
    <SuccessToast :message="notice" />
    <form
      v-if="draft"
      ref="form"
      class="store-timing-form"
      @submit.prevent="save"
    >
      <fieldset :disabled="!writable||busy">
        <div class="store-settings-grid">
          <label>采集频率<span class="store-number-unit">每 <input
            v-model.number="draft.intervalDays"
            type="number"
            min="1"
            max="30"
            step="1"
            required
            aria-label="采集间隔天数"
          > 天</span></label>
          <label>计划开始时间<input
            v-model="draft.localTime"
            type="time"
            required
          ></label>
          <label>时间所属时区<select v-model="draft.timeZone"><option value="Asia/Shanghai">北京时间</option><option value="America/Los_Angeles">美国太平洋时间（自动夏令时）</option></select></label>
        </div>
      </fieldset>
      <div class="store-schedule-summary">
        <span>下次计划</span><strong>{{ new Date(draft.nextAt).toLocaleString('zh-CN',{timeZone:draft.timeZone,hour12:false}) }}</strong><small>{{ draft.timeZone==='Asia/Shanghai'?'北京时间':'美国太平洋时间' }} · 保存后重新计算</small>
      </div>
      <p class="store-hint">
        店铺暂停时不采集；到达计划时间后进入队列，实际开始时间取决于前序任务。
      </p>
      <div
        v-if="!hideActions"
        class="store-card-actions"
      >
        <button
          class="mw-primary"
          :disabled="!writable||busy||!dirty"
        >
          {{ busy?'保存中…':'保存采集计划' }}
        </button>
      </div>
    </form>
  </section>
</template>
