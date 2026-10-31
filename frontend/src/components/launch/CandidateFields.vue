<script setup lang="ts">
import DatePicker from '@/components/DatePicker.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import type { Field } from './schema'
import type { Value } from '@/api/launch'
defineProps<{fields:Field[];model:Record<string,unknown>;disabled?:boolean}>()
const emit=defineEmits<{change:[string,Value]}>()
const percent=(f:Field)=>['commission','advertising','loss'].includes(f.key)
function input(event:Event,f:Field){const value=(event.target as HTMLInputElement).value;emit('change',f.key,f.type==='number'?(value===''?null:Number(value)/(percent(f)?100:1)):value)}
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <div class="candidate-fields">
    <label v-for="field in fields" :key="field.key" :class="{'candidate-field-wide':field.type==='textarea'}">
      <span>{{ percent(field)?field.label.replace('（0–1）','（%）'):field.label }}</span>
      <textarea v-if="field.type==='textarea'" :value="String(model[field.key]??'')" :disabled="disabled" rows="3" maxlength="12000" @input="input($event,field)" />
      <select v-else-if="field.type==='select'" v-single-select :aria-label="field.label" :value="String(model[field.key]??'')" :disabled="disabled" @change="input($event,field)"><option value="">请选择</option><option v-for="option in field.options" :key="option[0]" :value="option[0]">{{ option[1] }}</option></select>
      <DatePicker v-else-if="field.type==='date'" :aria-label="field.label" :model-value="String(model[field.key]??'')" :disabled="!!disabled" @update:model-value="emit('change',field.key,$event)" />
      <input v-else :type="field.type??'text'" :value="percent(field)&&model[field.key]!=null&&model[field.key]!==''?Number((Number(model[field.key])*100).toFixed(6)):String(model[field.key]??'')" :disabled="disabled" :min="field.type==='number'?0:undefined" :max="percent(field)?100:field.key==='score'?5:undefined" :step="field.key==='score'?1:percent(field)?'0.0001':field.type==='number'?'0.000001':undefined" maxlength="12000" @input="input($event,field)">
      <small v-if="field.hint">{{ field.hint }}</small>
    </label>
  </div>
</template>
