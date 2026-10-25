<script setup lang="ts">
import { computed } from 'vue'
import type { ProductInput } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'
const props = defineProps<{ initial: ProductInput; focusVariantId?: string | undefined; busy?: boolean; error?: string }>()
const emit = defineEmits<{ close: []; submit: [input: ProductInput] }>()
const title = computed(() => props.focusVariantId === '__NEW__' ? '添加SKU' : props.focusVariantId ? '编辑 SKU' : '编辑产品')
function close() { if (!props.busy) emit('close') }
</script>
<template>
  <div
    class="operation-dialog"
    role="dialog"
    aria-modal="true"
    :aria-label="title"
    @keydown.esc.stop="close"
  >
    <section class="panel operation-dialog__card product-create-dialog">
      <button
        type="button"
        class="operation-dialog__close"
        aria-label="关闭编辑"
        :disabled="busy"
        @click="close"
      >
        ×
      </button>
      <h2>{{ title }}</h2>
      <p
        v-if="error"
        class="data-state data-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p>{{ focusVariantId === '__NEW__' ? '填写新子体的唯一 SKU 编码。' : focusVariantId ? '编辑当前子体，保存时保留其他子体。' : '维护父体资料及其全部子体。' }}</p>
      <ProductForm
        class="product-create-form"
        :initial="initial"
        :focus-variant-id="focusVariantId"
        :busy="busy"
        submit-label="保存"
        show-cancel
        @cancel="close"
        @submit="emit('submit', $event)"
      />
    </section>
  </div>
</template>
