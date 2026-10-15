<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { domainApi, type ProductDetailDto, type ProductInput } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import ProductForm from '@/components/ProductForm.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const items = ref<ProductDetailDto[]>([])
const loading = ref(true); const saving = ref(false)
const error = ref(''); const success = ref(''); const showCreateForm = ref(false)
const query = ref(''); const status = ref('')
const writable = computed(() => session.canAccess(['products.write']))
const filtered = computed(() => items.value.filter(item => (!status.value || item.status === status.value)
  && (!query.value || `${item.productCode} ${item.productName}`.toLowerCase().includes(query.value.toLowerCase()))))

async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await domainApi.products()).items }
  catch { error.value = runtimeConfig.authMode === 'mock' ? '产品模拟数据加载失败。' : '产品数据加载失败。' }
  finally { loading.value = false }
}
function openCreate() { showCreateForm.value = true; error.value = ''; success.value = '' }
function closeCreate() { if (!saving.value) showCreateForm.value = false }
async function create(input: ProductInput) {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.createProduct(input)
    items.value.unshift(response.item)
    showCreateForm.value = false
    success.value = '产品及首个 SKU 已创建。'
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '产品创建失败，请检查输入。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <section class="panel filter-panel product-filters">
      <label>搜索<input
        v-model="query"
        type="search"
        placeholder="产品编号 / 名称"
      ></label><label>状态<select v-model="status"><option value="">全部</option><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
    </section>
    <section
      class="panel material-toolbar"
      aria-label="产品管理操作"
    >
      <div>
        <button
          v-if="writable"
          type="button"
          @click="openCreate"
        >
          新增产品
        </button>
      </div>
      <span class="toolbar-hint">{{ runtimeConfig.authMode === 'mock' ? '当前展示模拟产品。' : '产品与内部 SKU 主档。' }}</span>
    </section>
    <div
      v-if="showCreateForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="product-create-dialog-title"
      @click.self="closeCreate"
      @keydown.esc="closeCreate"
    >
      <section class="panel operation-dialog__card product-create-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭新增产品"
          :disabled="saving"
          @click="closeCreate"
        >
          ×
        </button><h2 id="product-create-dialog-title">
          新增产品
        </h2><p>创建产品主档，并在同一窗口维护首个或多个内部 SKU。</p>
        <p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p>
        <ProductForm
          class="product-create-form"
          :busy="saving"
          submit-label="创建产品"
          show-cancel
          @cancel="closeCreate"
          @submit="create"
        />
      </section>
    </div>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <p
      v-if="error && !showCreateForm"
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
      v-else-if="!filtered.length"
      class="panel empty-state"
    >
      <h2>暂无产品</h2><p>点击“新增产品”创建 Product 与首个 SKU。</p>
    </article>
    <article
      v-else
      class="panel table-panel"
    >
      <div class="table-scroll">
        <table class="data-table product-table">
          <thead><tr><th>图片</th><th>产品编码</th><th>产品名称 / 类型</th><th>SKU</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr
              v-for="product in filtered"
              :key="product.id"
            >
              <td>
                <AuthenticatedCatalogImage
                  v-if="product.variants[0]?.imageAssetId"
                  :image-id="product.variants[0].imageAssetId"
                  :alt="`${product.productName}主图`"
                /><img
                  v-else-if="product.variants[0]?.imageUrl"
                  class="product-thumbnail"
                  :src="product.variants[0].imageUrl"
                  :alt="`${product.productName}主图`"
                  loading="lazy"
                  referrerpolicy="no-referrer"
                ><span
                  v-else
                  class="product-thumbnail product-thumbnail--placeholder"
                  role="img"
                  :aria-label="`${product.productName}暂无图片`"
                >图</span>
              </td><td><strong>{{ product.productCode }}</strong></td><td>{{ product.productName }}<small>{{ product.productType || '未分类' }}</small></td><td><strong>{{ product.skuCount }}</strong> 个<small>{{ product.variants.slice(0, 2).map(item => item.skuCode).join('、') || '暂无 SKU' }}</small></td><td><span :class="['status-badge', `status-badge--${product.status.toLowerCase()}`]">{{ product.status === 'ACTIVE' ? '启用' : product.status === 'DRAFT' ? '待定' : '停用' }}</span></td><td>{{ product.updatedAt || '—' }}</td><td>
                <div class="table-actions">
                  <RouterLink
                    class="primary-link"
                    :to="`/products/${product.id}`"
                  >
                    查看
                  </RouterLink>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div><footer class="material-table-footer">
        共 {{ filtered.length }} 条
      </footer>
    </article>
  </section>
</template>
