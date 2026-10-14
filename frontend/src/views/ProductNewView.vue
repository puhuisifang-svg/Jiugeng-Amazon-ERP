<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { domainApi, type ProductInput } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'
import { ApiError } from '@/api/client'

const router = useRouter()
const busy = ref(false)
const error = ref('')

async function create(input: ProductInput) {
  busy.value = true; error.value = ''
  try {
    const response = await domainApi.createProduct(input)
    await router.push(`/products/${response.item.id}`)
  }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品创建失败，请检查输入。' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="page-stack">
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <ProductForm
      :busy="busy"
      submit-label="创建产品"
      @submit="create"
    />
  </section>
</template>
