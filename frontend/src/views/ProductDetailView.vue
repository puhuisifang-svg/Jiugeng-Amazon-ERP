<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '@/api/client'
import { domainApi, type ProductDetailDto, type ProductInput } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'
import ProductMaterialsPanel from '@/components/ProductMaterialsPanel.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute()
const product = ref<ProductDetailDto | null>(null)
const loading = ref(true)
const busy = ref(false)
const editing = ref(false)
const error = ref('')
const success = ref('')
const writable = computed(() => runtimeConfig.authMode === 'password' && session.canAccess(['products.write']) && product.value?.status !== 'DISABLED')
const formValue = computed<ProductInput | undefined>(() => product.value ? {
  productCode: product.value.productCode,
  nameZh: product.value.nameZh,
  ...(product.value.nameEn ? { nameEn: product.value.nameEn } : {}),
  ...(product.value.brand ? { brand: product.value.brand } : {}),
  ...(product.value.internalCategory ? { internalCategory: product.value.internalCategory } : {}),
  ...(product.value.productType ? { productType: product.value.productType } : {}),
  ...(product.value.description ? { description: product.value.description } : {}),
  status: product.value.status === 'DRAFT' ? 'DRAFT' : 'ACTIVE',
  variants: product.value.variants,
} : undefined)

async function load() {
  loading.value = true; error.value = ''
  try { product.value = await domainApi.product(String(route.params.productId)) }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品详情加载失败。' }
  finally { loading.value = false }
}
async function save(input: ProductInput) {
  if (!product.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    product.value = await domainApi.updateProduct(product.value.id, input)
    editing.value = false; success.value = '产品已更新。'
  }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品更新失败。' }
  finally { busy.value = false }
}
async function disable() {
  if (!product.value || !window.confirm('确认停用该产品及其全部 SKU？')) return
  busy.value = true; error.value = ''
  try { product.value = await domainApi.disableProduct(product.value.id); editing.value = false; success.value = '产品已停用。' }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品停用失败。' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <ProductForm
      v-else-if="editing && formValue"
      :key="product?.updatedAt ?? product?.id ?? 'product-form'"
      :initial="formValue"
      :busy="busy"
      submit-label="保存修改"
      @submit="save"
    />
    <template v-else-if="product">
      <article class="panel compact-panel">
        <div class="card-heading">
          <div><strong>{{ product.productCode }}</strong><h2>{{ product.nameZh }}</h2></div>
          <span class="placeholder-badge">{{ product.status }}</span>
        </div>
        <p>{{ product.nameEn || '无英文名称' }} · {{ product.brand || '无品牌' }} · {{ product.internalCategory || '无内部类目' }}</p>
        <p>{{ product.description || '暂无描述' }}</p>
        <div
          v-if="writable"
          class="table-actions"
        >
          <button
            type="button"
            @click="editing = true"
          >
            编辑
          </button>
          <button
            type="button"
            class="danger-button"
            :disabled="busy"
            @click="disable"
          >
            停用
          </button>
        </div>
      </article>
      <article
        v-for="variant in product.variants"
        :key="variant.id ?? variant.skuCode"
        class="panel compact-panel"
      >
        <div class="card-heading">
          <div><strong>{{ variant.skuCode }}</strong><h2>{{ variant.variantName }}</h2></div><span>{{ variant.status }}</span>
        </div>
        <p>{{ variant.material || '未填写材质' }} · {{ variant.specification || '未填写规格' }}</p>
        <div class="attribute-chips">
          <span
            v-for="attribute in variant.attributes"
            :key="attribute.attributeCode"
          >{{ attribute.attributeCode }} = {{ attribute.attributeValue }}</span>
        </div>
      </article>
      <ProductMaterialsPanel
        v-if="runtimeConfig.authMode === 'password'"
        :product="product"
      />
      <article
        v-else
        class="panel compact-panel"
      >
        <h2>对应辅料</h2><p>模拟模式仅展示既有合成产品，不提供 SKU—辅料关系写入。</p>
      </article>
    </template>
  </section>
</template>
