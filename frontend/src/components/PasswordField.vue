<script setup lang="ts">
/* eslint-disable vue/require-default-prop */
import { ref } from 'vue'

withDefaults(defineProps<{
  id: string
  modelValue: string
  autocomplete?: string
  minlength?: number
  maxlength?: number
  pattern?: string
  required?: boolean
}>(), {
  autocomplete: 'current-password',
  maxlength: 128,
  required: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/html-closing-bracket-spacing -->
  <div class="password-field">
    <input
      :id="id"
      :autocomplete="autocomplete"
      :maxlength="maxlength"
      :minlength="minlength"
      :pattern="pattern"
      :required="required"
      :type="visible ? 'text' : 'password'"
      :value="modelValue"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    >
    <button
      :aria-label="visible ? '隐藏密码' : '显示密码'"
      :aria-pressed="visible"
      class="password-toggle"
      type="button"
      @click="visible = !visible"
    >
      <svg v-if="!visible" aria-hidden="true" viewBox="0 0 24 24"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Zm10 3.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z"/></svg>
      <svg v-else aria-hidden="true" viewBox="0 0 24 24"><path d="m3 2 19 19-1.4 1.4-3.1-3.1A11.8 11.8 0 0 1 12 21C5 21 1.5 15 1.5 15a19 19 0 0 1 4.1-4.6L1.6 3.4 3 2Zm6.2 11.3a3.5 3.5 0 0 0 4.5 4.5l-4.5-4.5ZM12 9c7 0 10.5 6 10.5 6a18 18 0 0 1-2.7 3.3l-2.2-2.2A3.5 3.5 0 0 0 12.9 11l-2-2H12Z"/></svg>
    </button>
  </div>
</template>
