<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ProductInput } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'
const props = defineProps<{ initial: ProductInput; focusVariantId?: string | undefined; focusField?: 'image' | 'dimensions' | 'weight' | 'supplier' | undefined; busy?: boolean; error?: string }>()
const emit = defineEmits<{ close: []; submit: [input: ProductInput] }>()
const title = computed(() => props.focusVariantId === '__NEW__' ? '添加SKU' : props.focusVariantId ? '编辑 SKU' : '编辑产品')
const form = ref<InstanceType<typeof ProductForm>>()
function close() { if (!props.busy) { if (form.value?.requestClose) form.value.requestClose(); else emit('close') } }
</script>
<template>
  <div
    class="operation-dialog"
    role="dialog"
    aria-modal="true"
    :aria-label="title"
    @keydown.esc.stop="close"
  >
    <section
      class="panel operation-dialog__card product-create-dialog product-ui"
      :class="{ 'product-create-dialog--single': !!focusVariantId && focusVariantId !== '__NEW__' }"
    >
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
      <p class="product-editor-description">
        {{ focusVariantId === '__NEW__' ? '填写新子体资料，内部编码由系统生成。' : focusVariantId ? '编辑当前子体，保存时保留其他子体。' : '维护父体资料及其全部子体。' }}
      </p>
      <ProductForm
        ref="form"
        class="product-create-form"
        :initial="initial"
        :focus-variant-id="focusVariantId"
        :focus-field="focusField"
        :busy="busy"
        submit-label="保存"
        show-cancel
        @cancel="!busy && emit('close')"
        @submit="emit('submit', $event)"
      />
    </section>
  </div>
</template>
