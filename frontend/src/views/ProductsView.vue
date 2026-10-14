<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { domainApi, type ProductDetailDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

const items = ref<ProductDetailDto[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const productCode = ref('')
const name = ref('')
const skuText = ref('')
const writable = computed(() => session.canAccess(['products.write']))

async function load() {
  loading.value = true
  error.value = ''
  try { items.value = (await domainApi.products()).items }
  catch { error.value = '产品模拟数据加载失败。' }
  finally { loading.value = false }
}

async function create() {
  const skuCodes = skuText.value.split(/[\s,，]+/).map(value => value.trim()).filter(Boolean)
  if (!skuCodes.length) { error.value = '请至少填写一个 SKU 编码。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.createProduct({ productCode: productCode.value.trim(), name: name.value.trim(), skuCodes })
    items.value.unshift(response.item)
    productCode.value = ''; name.value = ''; skuText.value = ''
    success.value = '模拟产品已创建。'
  }
  catch { error.value = '创建失败：请检查编码唯一性和 mock 后端状态。' }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice />
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="create"
    >
      <h2>新增模拟产品</h2>
      <label>产品编码<input
        v-model="productCode"
        required
      ></label>
      <label>产品名称<input
        v-model="name"
        required
      ></label>
      <label class="form-wide">SKU 编码（逗号或空格分隔）<input
        v-model="skuText"
        required
        placeholder="SKU-001, SKU-002"
      ></label>
      <button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '保存中…' : '新增产品' }}
      </button>
    </form>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <div
      v-else
      class="card-list"
    >
      <article
        v-for="product in items"
        :key="product.id"
        class="panel compact-panel"
      >
        <div class="card-heading">
          <div><strong>{{ product.productCode }}</strong><h2>{{ product.name }}</h2></div><span class="placeholder-badge">{{ product.status }}</span>
        </div>
        <p>{{ product.skuCount }} 个内部 SKU</p>
        <div
          v-for="sku in product.skus"
          :key="sku.skuCode"
          class="sku-block"
        >
          <strong>{{ sku.skuCode }}</strong>
        </div>
      </article>
    </div>
  </section>
</template>
