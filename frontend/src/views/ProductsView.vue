<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { domainApi, type ProductDetailDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const items = ref<ProductDetailDto[]>([])
const loading = ref(true)
const error = ref('')
const writable = computed(() => session.canAccess(['products.write']))

async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await domainApi.products()).items }
  catch { error.value = runtimeConfig.authMode === 'mock' ? '产品模拟数据加载失败。' : '产品数据加载失败。' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <div class="page-actions">
      <p>{{ runtimeConfig.authMode === 'mock' ? '当前展示模拟产品。' : '产品与内部 SKU 主档。' }}</p>
      <RouterLink
        v-if="writable"
        class="primary-link"
        to="/products/new"
      >
        新增产品
      </RouterLink>
    </div>
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
    <article
      v-else-if="!items.length"
      class="panel empty-state"
    >
      <h2>暂无产品</h2><p>点击“新增产品”创建 Product 与首个 SKU。</p>
    </article>
    <div
      v-else
      class="card-list"
    >
      <RouterLink
        v-for="product in items"
        :key="product.id"
        class="panel compact-panel product-card"
        :to="`/products/${product.id}`"
      >
        <div class="card-heading">
          <div><strong>{{ product.productCode }}</strong><h2>{{ product.nameZh }}</h2></div><span class="placeholder-badge">{{ product.status }}</span>
        </div>
        <p>{{ product.skuCount }} 个内部 SKU<span v-if="product.brand"> · {{ product.brand }}</span></p>
        <div
          v-for="sku in product.variants"
          :key="sku.id ?? sku.skuCode"
          class="sku-block"
        >
          <strong>{{ sku.skuCode }}</strong> · {{ sku.variantName }}
        </div>
      </RouterLink>
    </div>
  </section>
</template>
