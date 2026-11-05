<script setup lang="ts">
import { computed,ref,watch,watchEffect,onBeforeUnmount } from 'vue'
import { profileFields, hsSchemaApi, applyHsSchema, profileElements, type CustomsProfile } from '@/api/customs'
const model=defineModel<CustomsProfile>({required:true})
const props=defineProps<{disabled?:boolean}>()
const fields=computed(()=>model.value.elementFields||Object.values(profileElements(model.value)).some(Boolean)?profileFields(model.value):[])
const hsInput=ref<HTMLInputElement>()
const loading=ref(false),lookupError=ref(''),source=ref(''),stale=ref(false)
let seq=0,timer:ReturnType<typeof setTimeout>|undefined
async function lookup(){
 const code=model.value.chinaCode??'',current=++seq
 if(!/^[0-9]{10}$/.test(code)||props.disabled)return
 loading.value=true;lookupError.value=''
 try{const schema=await hsSchemaApi.get(code);if(current!==seq||model.value.chinaCode!==code)return;model.value=applyHsSchema(model.value,schema);source.value=schema.sourceUrl;stale.value=schema.stale}
 catch(e){if(current===seq)lookupError.value=e instanceof Error?e.message:'查询失败，请重试'}finally{if(current===seq)loading.value=false}
}
watch(()=>model.value.chinaCode,(code)=>{
 ++seq;clearTimeout(timer);loading.value=false;lookupError.value='';source.value='';stale.value=false
 if(props.disabled||model.value.schemaCode===code&&model.value.elementFields?.length)return
 if(/^[0-9]{10}$/.test(code??'')){loading.value=true;timer=setTimeout(()=>void lookup(),350)}
},{immediate:true})
onBeforeUnmount(()=>{++seq;clearTimeout(timer)})
const mismatch=computed(()=>!!model.value.elementFields&&model.value.schemaCode!==model.value.chinaCode)
watchEffect(()=>{hsInput.value?.setCustomValidity(loading.value?'正在查询HS申报要素，请稍候':lookupError.value|| (mismatch.value?'请先完成HS申报要素查询':''))})
const elements=computed(()=>profileElements(model.value))
function update(key:string,value:string){model.value={...model.value,elements:{...elements.value,[key]:value}}}
function enableStructured(){if(!model.value.elements)model.value={...model.value,elements:elements.value}}
</script>
<template>
  <fieldset
    class="customs-fields operation-form form-wide"
    :disabled="disabled"
  >
    <legend>出口美国 · 申报要素</legend>
    <p class="form-wide customs-help">
      选择此 SKU 制作报关单时，自动带入以下资料；本票修改不覆盖 SKU 档案。
    </p>
    <label>HS<input
      ref="hsInput"
      v-model="model.chinaCode"
      inputmode="numeric"
      pattern="[0-9]{10}"
      maxlength="10"
      placeholder="填写已确认的10位HS编码"
      :required="model.state==='REVIEWED'"
      :aria-invalid="mismatch||undefined"
      @input="enableStructured"
    ></label>
    <label>品名<input
      v-model="model.nameZh"
      maxlength="200"
      :required="model.state==='REVIEWED'"
      @input="enableStructured"
    ></label>
    <div
      class="form-wide customs-help"
      role="status"
    >
      <span v-if="loading">正在查询 HS 申报要素…</span>
      <span
        v-else-if="lookupError"
        class="error"
      >{{ lookupError }} <button
        type="button"
        class="text-action"
        @click="lookup"
      >重试</button></span>
      <span v-else-if="source"><a
        :href="source"
        target="_blank"
        rel="noopener noreferrer"
      >HS 字段来源</a>{{ stale?' · 当前使用已缓存资料，来源暂时无法刷新':'' }}</span>
      <span v-else-if="!model.chinaCode">输入 10 位 HS 编码，自动带出对应字段和计量单位。</span>
      <span v-if="mismatch && !loading"> HS 已变化，查询成功后才能保存新字段。</span>
    </div>
    <label
      v-for="[key,label] in fields"
      :key="key"
      :class="{'form-wide':key==='other'}"
    >{{ label }}<textarea
      v-if="key==='other'"
      :value="elements[key]??''"
      maxlength="8000"
      rows="3"
      @input="update(key,($event.target as HTMLTextAreaElement).value)"
    /><input
      v-else
      :value="elements[key]??''"
      maxlength="1000"
      @input="update(key,($event.target as HTMLInputElement).value)"
    ></label>
    <label>计量单位<input
      v-model="model.measurementUnit"
      maxlength="100"
      placeholder="随 HS 带出，可核对修改"
    ></label>
  </fieldset>
</template>
<style scoped>
.customs-fields{min-width:0;border:1px solid var(--line);border-radius:6px;padding:12px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px 16px}.customs-help{color:var(--muted);font-size:12px;margin:0;line-height:1.6}.customs-fields legend{font-size:13px;font-weight:600}.customs-fields label{font-size:13px;min-width:0}.customs-fields textarea{min-height:72px}.customs-fields .form-wide{grid-column:1/-1}@media(max-width:600px){.customs-fields{grid-template-columns:1fr}}
</style>
