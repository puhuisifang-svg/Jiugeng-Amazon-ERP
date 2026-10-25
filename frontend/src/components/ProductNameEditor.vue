<script setup lang="ts">
import { nextTick, ref, useId } from 'vue'
import ProductName from './ProductName.vue'
import { editName, formatName, type NameStyleSpan } from '@/utils/productName'
const props = defineProps<{ modelValue: string; styles?: NameStyleSpan[] | null | undefined; highlighted?: boolean | undefined }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'update:styles': [value: NameStyleSpan[]] }>()
const field = ref<HTMLTextAreaElement>(); const id = useId()
const selection = ref({ start: 0, end: 0 }); const message = ref('')
let beforeInput: { start: number; end: number } | undefined
function remember() {
  if (field.value) selection.value = { start: field.value.selectionStart, end: field.value.selectionEnd }
}
function before() { remember(); beforeInput = { ...selection.value } }
function input(event: Event) {
  const text = (event.target as HTMLTextAreaElement).value
  emit('update:styles', editName(props.modelValue, text, props.styles, props.highlighted, beforeInput))
  emit('update:modelValue', text); beforeInput = undefined; message.value = ''; remember()
}
async function apply(patch: Partial<Pick<NameStyleSpan, 'color' | 'bold'>>) {
  const { start, end } = selection.value
  if (start === end) { message.value = '请先选中要设置格式的文字。'; return }
  emit('update:styles', formatName(props.modelValue, props.styles, !!props.highlighted, start, end, patch))
  message.value = '已应用到选中文字。'
  await nextTick(); field.value?.focus(); field.value?.setSelectionRange(start, end)
}
function selectAll() { field.value?.focus(); field.value?.select(); remember() }
</script>
<template>
  <div class="product-name-editor">
    <label :for="id">SKU 名称</label>
    <div
      class="product-name-editor__toolbar"
      role="group"
      aria-label="名称格式"
    >
      <button
        type="button"
        @mousedown.prevent
        @click="selectAll"
      >
        全选名称
      </button>
      <button
        type="button"
        @mousedown.prevent
        @click="apply({ color: 'BLACK' })"
      >
        黑色
      </button>
      <button
        type="button"
        class="product-name-editor__red"
        @mousedown.prevent
        @click="apply({ color: 'RED' })"
      >
        红色
      </button>
      <button
        type="button"
        @mousedown.prevent
        @click="apply({ bold: true })"
      >
        加粗
      </button>
      <button
        type="button"
        @mousedown.prevent
        @click="apply({ bold: false })"
      >
        常规
      </button>
    </div>
    <textarea
      :id="id"
      ref="field"
      :value="modelValue"
      required
      maxlength="200"
      rows="3"
      placeholder="输入名称，Enter 换行；选中文字后设置格式"
      :aria-describedby="`${id}-hint`"
      @beforeinput="before"
      @input="input"
      @select="remember"
      @keyup="remember"
      @mouseup="remember"
      @blur="remember"
    />
    <small :id="`${id}-hint`">选中文字后设置颜色或字重，Enter 换行（最多 200 字）。</small>
    <small role="status">{{ message }}</small>
    <div
      class="product-name-editor__preview"
      aria-label="名称展示预览"
    >
      <small>展示预览</small>
      <ProductName
        v-if="modelValue"
        :text="modelValue"
        :styles="styles"
        :highlighted="highlighted"
      />
      <span
        v-else
        class="muted"
      >输入名称后显示预览</span>
    </div>
  </div>
</template>
