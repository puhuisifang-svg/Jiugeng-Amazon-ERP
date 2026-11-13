<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { customersApi, type Customer } from '@/api/customers'
const props=defineProps<{selected?:{id:string;name:string}|undefined;label:string;disabled?:boolean}>()
const emit=defineEmits<{select:[Customer]}>()
const root=ref<HTMLElement>(),panel=ref<HTMLElement>(),input=ref<HTMLInputElement>()
const open=ref(false),query=ref(''),items=ref<Customer[]>([]),page=ref(0),total=ref(0),busy=ref(false),error=ref(''),active=ref(-1),position=ref<Record<string,string>>({}),id=useId()
let sequence=0,timer:ReturnType<typeof setTimeout>|undefined
function place(){if(!root.value||!panel.value)return;const r=root.value.getBoundingClientRect(),h=panel.value.offsetHeight;position.value={left:`${Math.max(8,Math.min(r.left,innerWidth-r.width-8))}px`,top:`${Math.max(8,r.bottom+h+4>innerHeight?r.top-h-4:r.bottom+4)}px`,width:`${Math.min(r.width,innerWidth-16)}px`}}
function outside(e:Event){if(!root.value?.contains(e.target as Node))close()}
function close(){clearTimeout(timer);sequence++;busy.value=false;panel.value?.hidePopover?.();open.value=false;document.removeEventListener('pointerdown',outside,true);document.removeEventListener('scroll',place,true);window.removeEventListener('resize',place)}
async function load(){const current=++sequence;busy.value=true;error.value='';active.value=-1;try{const result=await customersApi.list({q:query.value.trim(),status:'ACTIVE',page:page.value,size:20});if(current!==sequence)return;items.value=result.items;total.value=result.totalCount}catch(e){if(current===sequence){items.value=[];error.value=e instanceof Error?e.message:'客户加载失败，请重试'}}finally{if(current===sequence){busy.value=false;await nextTick();place()}}}
async function show(){if(props.disabled||open.value)return;open.value=true;query.value='';page.value=0;items.value=[];await nextTick();panel.value?.showPopover?.();place();document.addEventListener('pointerdown',outside,true);document.addEventListener('scroll',place,true);window.addEventListener('resize',place);void load()}
async function type(e:Event){const value=(e.target as HTMLInputElement).value;await show();query.value=value;page.value=0;active.value=-1;items.value=[];sequence++;clearTimeout(timer);busy.value=true;timer=setTimeout(()=>void load(),250)}
function choose(value:Customer){if(props.disabled)return;emit('select',value);close();input.value?.focus()}
function key(e:KeyboardEvent){if(e.key==='Escape'&&open.value){e.preventDefault();e.stopPropagation();close()}else if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(!open.value){void show();return}active.value=Math.max(0,Math.min(items.value.length-1,active.value+(e.key==='ArrowDown'?1:-1)));void nextTick(()=>document.getElementById(`${id}-${active.value}`)?.scrollIntoView?.({block:'nearest'}))}else if(e.key==='Enter'){e.preventDefault();e.stopPropagation();if(open.value&&active.value>=0&&items.value[active.value])choose(items.value[active.value]!);else if(!open.value)void show()}else if(e.key==='Tab')close()}
watch(()=>props.disabled,value=>{if(value)close()})
onBeforeUnmount(close)
</script>
<template>
  <span
    ref="root"
    class="customer-combobox"
  >
    <input
      ref="input"
      :value="open ? query : selected?.name ?? ''"
      :aria-label="label"
      :disabled="disabled"
      placeholder="选择或输入客户名称、账号"
      maxlength="128"
      autocomplete="off"
      role="combobox"
      :aria-expanded="open"
      :aria-controls="id"
      aria-autocomplete="list"
      :aria-activedescendant="active>=0 ? `${id}-${active}` : undefined"
      @input="type"
      @click="show"
      @keydown="key"
    >
    <button
      type="button"
      class="customer-combobox-arrow"
      :disabled="disabled"
      :aria-label="`展开${label}`"
      @click.prevent="open ? close() : show()"
    >⌄</button>
    <span
      v-show="open"
      :id="id"
      ref="panel"
      popover="manual"
      class="customer-combobox-panel"
      :style="position"
    >
      <span
        v-if="busy"
        class="customer-combobox-hint"
        role="status"
      >正在查找客户…</span>
      <span
        v-else-if="error"
        class="customer-combobox-hint"
        role="alert"
      >{{ error }} <button
        type="button"
        @click.prevent="load"
      >重试</button></span>
      <span
        v-else-if="!items.length"
        class="customer-combobox-hint"
      >没有匹配客户，请更换关键词</span>
      <span
        v-else
        role="listbox"
        :aria-label="label"
      >
        <button
          v-for="(item,index) in items"
          :id="`${id}-${index}`"
          :key="item.id"
          type="button"
          role="option"
          :aria-selected="selected?.id===item.id"
          class="customer-combobox-option"
          :class="{active:active===index}"
          @click.prevent="choose(item)"
        >
          <span>{{ item.name }}<small v-if="item.defaultCustomer">默认</small></span><span v-if="selected?.id===item.id">✓</span>
        </button>
      </span>
      <span
        v-if="!busy && !error && total>20"
        class="customer-combobox-pages"
      ><button
        type="button"
        :disabled="page===0"
        @click.prevent="page--;load()"
      >上一页</button><small>{{ page+1 }} / {{ Math.ceil(total/20) }}</small><button
        type="button"
        :disabled="(page+1)*20>=total"
        @click.prevent="page++;load()"
      >下一页</button></span>
    </span>
  </span>
</template>
<style scoped>
.customer-combobox{position:relative;display:block;width:100%;min-width:0}.customer-combobox input{width:100%;padding-right:34px!important;font-size:13px}.customer-combobox .customer-combobox-arrow{position:absolute;right:1px;top:1px;height:calc(100% - 2px);width:32px;min-height:0;border:0;background:transparent;color:#64748b;padding:0}
.customer-combobox-panel{position:fixed;inset:auto;margin:0;border:1px solid #dce3ee;border-radius:4px;padding:4px;background:white;box-shadow:0 6px 18px #1e293b24;max-height:260px;overflow:auto;z-index:1000;box-sizing:border-box}
.customer-combobox-panel .customer-combobox-option{display:flex;justify-content:space-between;align-items:center;width:100%;background:white!important;color:#334155!important;border:0;box-shadow:none;padding:8px 10px;min-height:34px;font-size:13px;text-align:left;overflow-wrap:anywhere}.customer-combobox-panel .customer-combobox-option:hover,.customer-combobox-panel .customer-combobox-option.active,.customer-combobox-panel .customer-combobox-option[aria-selected=true]{background:#eff6ff!important;color:#2864c5!important}.customer-combobox-option small{font-size:11px;color:#64748b;margin-left:8px}.customer-combobox-hint{display:block;padding:10px;color:#64748b;font-size:12px}.customer-combobox-pages{display:flex;justify-content:space-between;align-items:center;border-top:1px solid #e2e8f0;padding:4px;gap:8px}.customer-combobox-panel .customer-combobox-pages button,.customer-combobox-hint button{background:transparent!important;color:#2864c5!important;border:0;min-height:28px;font-size:12px;padding:4px 8px}.customer-combobox-pages button:disabled{opacity:.4}
</style>
