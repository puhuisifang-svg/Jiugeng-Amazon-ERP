<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId } from 'vue'
const props=defineProps<{modelValue:string;options:string[];label:string;required?:boolean;disabled?:boolean;maxLength?:number}>()
const emit=defineEmits<{'update:modelValue':[string];remove:[string]}>()
const root=ref<HTMLElement>(),panel=ref<HTMLElement>(),input=ref<HTMLInputElement>()
const open=ref(false),filter=ref(''),active=ref(-1),position=ref<Record<string,string>>({}),id=useId()
const visible=computed(()=>props.options.filter(v=>v.toLowerCase().includes(filter.value.toLowerCase())))
function place(){if(!root.value||!panel.value)return;const r=root.value.getBoundingClientRect();const h=panel.value.offsetHeight;position.value={left:`${Math.max(8,Math.min(r.left,innerWidth-r.width-8))}px`,top:`${Math.max(8,r.bottom+h+4>innerHeight?r.top-h-4:r.bottom+4)}px`,width:`${Math.min(r.width,innerWidth-16)}px`}}
function outside(e:Event){if(!root.value?.contains(e.target as Node))close()}
function close(){panel.value?.hidePopover?.();open.value=false;document.removeEventListener('pointerdown',outside,true);document.removeEventListener('scroll',place,true);window.removeEventListener('resize',place)}
async function show(){if(props.disabled||open.value)return;filter.value='';active.value=-1;open.value=true;await nextTick();panel.value?.showPopover?.();place();document.addEventListener('pointerdown',outside,true);document.addEventListener('scroll',place,true);window.addEventListener('resize',place)}
async function type(e:Event){const value=(e.target as HTMLInputElement).value;emit('update:modelValue',value);await show();filter.value=value;active.value=-1;await nextTick();place()}
function choose(value:string){emit('update:modelValue',value);close();input.value?.focus()}
function key(e:KeyboardEvent){if(e.key==='Escape'&&open.value){e.preventDefault();e.stopPropagation();close()}else if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(!open.value){void show();return}active.value=Math.max(0,Math.min(visible.value.length-1,active.value+(e.key==='ArrowDown'?1:-1)))}else if(e.key==='Enter'&&open.value){e.preventDefault();if(active.value>=0&&visible.value[active.value])choose(visible.value[active.value]!);else close()}else if(e.key==='Tab')close()}
function remove(value:string){close();emit('remove',value)}
onBeforeUnmount(close)
</script>
<template>
  <span
    ref="root"
    class="editable-select"
  >
    <input
      ref="input"
      :value="modelValue"
      :aria-label="label"
      :required="required"
      :disabled="disabled"
      :maxlength="maxLength ?? 100"
      :placeholder="`输入或选择${label}`"
      role="combobox"
      :aria-expanded="open"
      :aria-controls="id"
      aria-autocomplete="list"
      :aria-activedescendant="active >= 0 ? `${id}-${active}` : undefined"
      @input="type"
      @click="show"
      @keydown="key"
    >
    <button
      type="button"
      class="editable-select-arrow"
      :disabled="disabled"
      :aria-label="`选择${label}`"
      @click.prevent="open ? close() : show()"
    >⌄</button>
    <span
      v-show="open"
      :id="id"
      ref="panel"
      class="editable-select-panel"
      popover="manual"
      :style="position"
    >
      <span
        role="listbox"
        :aria-label="label"
      >
        <span
          v-for="(option,index) in visible"
          :key="option"
          class="editable-select-row"
          :class="{active:active===index}"
        >
          <button
            :id="`${id}-${index}`"
            type="button"
            role="option"
            :aria-selected="modelValue===option"
            @click.prevent="choose(option)"
          ><span>{{ option }}</span><span v-if="modelValue===option">✓</span></button>
          <button
            type="button"
            class="editable-select-remove"
            :aria-label="`删除${label} ${option}`"
            @click.prevent="remove(option)"
          >×</button>
        </span>
      </span>
      <span
        v-if="!visible.length"
        class="editable-select-hint"
      >{{ filter ? '无匹配项，保存时使用当前输入' : '暂无历史选项，可直接输入' }}</span>
    </span>
  </span>
</template>
<style scoped>
.editable-select{position:relative;display:block;width:100%;min-width:0}
.editable-select input{width:100%;padding-right:34px!important}
.editable-select .editable-select-arrow{position:absolute;right:1px;top:1px;height:calc(100% - 2px);width:32px;min-height:0;border:0;background:transparent;color:#64748b;padding:0}
.editable-select-panel{position:fixed;inset:auto;margin:0;border:1px solid #dce3ee;border-radius:4px;padding:4px;background:white;box-shadow:0 6px 18px #1e293b24;max-height:240px;overflow:auto;z-index:1000;box-sizing:border-box}
.editable-select-row{display:flex;align-items:center;border-radius:3px}
.editable-select-row.active,.editable-select-row:hover{background:#eff6ff}
.editable-select-row button{background:transparent!important;color:#334155!important;border:0;box-shadow:none;padding:7px 9px;min-height:32px;font-size:13px;text-align:left;display:flex;justify-content:space-between;flex:1;min-width:0;overflow-wrap:anywhere}
.editable-select-row .editable-select-remove{color:#c24141!important;flex:0 0 30px;justify-content:center;font-size:18px;padding:4px}
.editable-select-hint{display:block;font-size:12px;color:#64748b;padding:10px}
</style>
