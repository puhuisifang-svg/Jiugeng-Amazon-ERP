<script setup lang="ts">
import { vSingleSelect } from '@/directives/singleSelect'
import type { Field } from './schema'
import type { Value } from '@/api/launch'
defineProps<{ fields: Field[]; model: Record<string, unknown>; disabled: boolean }>()
const emit=defineEmits<{change:[key:string,value:Value]}>()
function value(event:Event,field:Field){const v=(event.target as HTMLInputElement).value;emit('change',field.key,field.type==='number'?(v===''?null:Number(v)):v)}
</script>
<template>
  <div class="launch-fields">
    <label
      v-for="field in fields"
      :key="field.key"
      :class="{'launch-wide':field.type==='textarea'}"
    >
      <span>{{ field.label }}</span>
      <textarea
        v-if="field.type==='textarea'"
        :value="String(model[field.key] ?? '')"
        :disabled="disabled"
        maxlength="12000"
        rows="3"
        @input="value($event,field)"
      />
      <select
        v-else-if="field.type==='select'"
        v-single-select
        :value="String(model[field.key] ?? '')"
        :disabled="disabled"
        @change="value($event,field)"
      ><option value="">请选择</option><option
        v-for="option in field.options"
        :key="option[0]"
        :value="option[0]"
      >{{ option[1] }}</option></select>
      <input
        v-else
        :type="field.type ?? 'text'"
        :value="String(model[field.key] ?? '')"
        :disabled="disabled"
        :step="field.type==='number'?'0.000001':undefined"
        :min="field.type==='number'?0:undefined"
        maxlength="12000"
        @input="value($event,field)"
      >
    </label>
  </div>
</template>
