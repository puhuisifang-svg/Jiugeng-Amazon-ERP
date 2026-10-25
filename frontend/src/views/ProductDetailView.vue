<script setup lang="ts">
import ProductName from '@/components/ProductName.vue'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { domainApi, type ProductHistoryDto, type ProductDetailDto, type ProductInput, type ProductVariantDto, type SkuListItemDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import ProductEditDialog from '@/components/ProductEditDialog.vue'
import { productActionLabel } from '@/utils/productHistory'
import SkuDetailDialog from '@/components/SkuDetailDialog.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute()
const product = ref<ProductDetailDto | null>(null), loading = ref(true), busy = ref(false), editing = ref(false)
const error = ref(''), success = ref(''), confirmDisable = ref(false)
const writable = computed(() => runtimeConfig.authMode === 'password' && session.canAccess(['products.write']))
const selectedSku = ref<SkuListItemDto>()
const focusVariantId = ref<string>()
const section = computed(() => route.params.section === 'changes' ? 'changes' : 'overview')
const history = ref<ProductHistoryDto[]>([]), historyLoading = ref(false), historyError = ref('')
let historySequence = 0
async function loadHistory() {
  const request = ++historySequence, id = product.value?.id
  history.value = []; historyError.value = ''; historyLoading.value = false
  if (!id || section.value !== 'changes') return
  historyLoading.value = true
  try { const result = await domainApi.productHistory(id); if (request === historySequence) history.value = result }
  catch (exception) { if (request === historySequence) historyError.value = exception instanceof Error ? exception.message : '变更记录加载失败。' }
  finally { if (request === historySequence) historyLoading.value = false }
}
function openSku(variant: ProductVariantDto) {
  if (!product.value) return
  selectedSku.value = { productId: product.value.id, productCode: product.value.productCode, productName: product.value.productName, productStatus: product.value.status, variant, businessAvailable: product.value.status === 'ACTIVE' && variant.status === 'ACTIVE' }
}
function editSku(row: SkuListItemDto) { selectedSku.value = undefined; focusVariantId.value = row.variant.id; editing.value = true }
const preview = ref<{ id?: string | undefined; url?: string | undefined; name: string }>()
function statusLabel(value: string) { return ({ ACTIVE: '启用', DRAFT: '待定', DISABLED: '停用' } as Record<string, string>)[value] ?? value }
const formValue = computed<ProductInput | undefined>(() => product.value ? ({
  imageAssetId: product.value.imageAssetId, productCode: product.value.productCode, productName: product.value.productName, productType: product.value.productType,
  description: product.value.description, status: product.value.status, version: product.value.version,
  variants: product.value.variants.map(item => ({ id: item.id, customs: item.customs, skuCode: item.skuCode, variantName: item.variantName, nameHighlighted: item.nameHighlighted, nameStyles: item.nameStyles,
    itemDimensions: item.itemDimensions, packageDimensions: item.packageDimensions, packageWeightGrams: item.packageWeightGrams,
    itemLengthCm: item.itemLengthCm, itemWidthCm: item.itemWidthCm, itemHeightCm: item.itemHeightCm,
    packageLengthCm: item.packageLengthCm, packageWidthCm: item.packageWidthCm, packageHeightCm: item.packageHeightCm,
    status: item.status, imageAssetId: item.imageAssetId, imageUrl: item.imageUrl, version: item.version,
    suppliers: item.suppliers ?? [], materials: item.materials ?? [] })),
}) : undefined)
let loadSequence = 0
async function load() {
  const sequence = ++loadSequence
  loading.value = true; error.value = ''; product.value = null; editing.value = false; confirmDisable.value = false
  preview.value = undefined; selectedSku.value = undefined; focusVariantId.value = undefined
  try {
    const result = await domainApi.product(String(route.params.productId))
    if (sequence !== loadSequence) return
    product.value = result
  } catch (exception) { if (sequence === loadSequence) error.value = exception instanceof Error ? exception.message : '产品详情加载失败，请重试。' }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function save(input: ProductInput) {
  if (!product.value || busy.value || !writable.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { await domainApi.updateProduct(product.value.id, input); await load(); success.value = '产品、SKU 及关联资料已更新。' }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '产品更新失败。' }
  finally { busy.value = false }
}
async function disable() {
  if (!product.value || busy.value || !writable.value) return
  busy.value = true; error.value = ''
  try { product.value = await domainApi.disableProduct(product.value.id); confirmDisable.value = false; success.value = '产品已停用。' }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '产品停用失败。' }
  finally { busy.value = false }
}
watch(() => route.params.productId, load, { immediate: true })
watch([section, () => product.value?.id, () => product.value?.updatedAt, () => product.value?.version], loadHistory)
</script>

<template>
  <section class="page-stack product-detail">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <SuccessToast :message="success" />
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <template v-else-if="product">
      <article class="panel compact-panel">
        <div class="detail-summary">
          <button
            v-if="product.imageAssetId"
            type="button"
            class="detail-image"
            aria-label="查看父体图片"
            @click="preview = { id: product.imageAssetId, name: product.productName }"
          >
            <AuthenticatedCatalogImage
              :image-id="product.imageAssetId"
              :alt="product.productName"
            />
          </button>
          <div><h2>{{ product.productName }}</h2><p>{{ product.productCode }} · {{ product.productType || '未填写类型' }} · {{ product.variants.length }} 个 SKU</p></div>
          <span class="placeholder-badge">{{ statusLabel(product.status) }}</span>
          <div
            v-if="writable"
            class="table-actions"
          >
            <button
              type="button"
              :disabled="busy"
              @click="focusVariantId = undefined; editing = true"
            >
              编辑
            </button>
            <button
              type="button"
              class="danger-button"
              :disabled="busy || product.status === 'DISABLED'"
              @click="confirmDisable = true"
            >
              停用
            </button>
          </div>
        </div>
      </article>
      <nav
        class="detail-tabs"
        aria-label="父体详情页签"
      >
        <RouterLink
          :to="'/products/' + product.id + '/overview'"
          :class="{ 'is-current': section === 'overview' }"
        >
          基础信息
        </RouterLink>
        <RouterLink
          :to="'/products/' + product.id + '/changes'"
          :class="{ 'is-current': section === 'changes' }"
        >
          变更记录
        </RouterLink>
      </nav>
      <article
        v-if="section === 'overview'"
        class="panel compact-panel"
      >
        <h2>基础信息</h2>
        <dl class="detail-fields">
          <div><dt>产品编码</dt><dd>{{ product.productCode }}</dd></div><div><dt>产品名称</dt><dd>{{ product.productName }}</dd></div>
          <div><dt>产品类型</dt><dd>{{ product.productType || '—' }}</dd></div><div><dt>SKU数量</dt><dd>{{ product.variants.length }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ product.createdAt || '—' }}</dd></div><div><dt>更新时间</dt><dd>{{ product.updatedAt || '—' }}</dd></div>
          <div class="detail-wide">
            <dt>产品描述</dt><dd>{{ product.description || '—' }}</dd>
          </div>
        </dl>
        <h3>SKU 概览</h3>
        <TableViewport>
          <table class="data-table">
            <thead><tr><th>SKU</th><th>品名</th><th>状态</th><th>包装重量</th></tr></thead><tbody>
              <tr
                v-for="variant in product.variants"
                :key="variant.id ?? variant.skuCode"
              >
                <td>
                  <button
                    type="button"
                    class="sku-overview-link"
                    @click="openSku(variant)"
                  >
                    {{ variant.skuCode }}
                  </button>
                </td><td>
                  <ProductName
                    :text="variant.variantName"
                    :styles="variant.nameStyles"
                    :highlighted="variant.nameHighlighted"
                  />
                </td><td>{{ statusLabel(variant.status) }}</td><td>{{ variant.packageWeightGrams == null ? '—' : variant.packageWeightGrams + ' g' }}</td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
        <p
          v-if="!product.variants.length"
          class="data-state"
        >
          暂无 SKU。
        </p>
      </article>
      <article
        v-else
        class="panel compact-panel"
      >
        <h2>变更记录</h2>
        <p
          v-if="historyLoading"
          class="data-state"
        >
          正在加载变更记录…
        </p>
        <p
          v-else-if="historyError"
          role="alert"
        >
          {{ historyError }} <button
            type="button"
            class="secondary-action"
            @click="loadHistory"
          >
            重试
          </button>
        </p>
        <TableViewport v-else-if="history.length">
          <table class="data-table">
            <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>变更内容</th></tr></thead><tbody>
              <tr
                v-for="item in history"
                :key="item.id"
              >
                <td>{{ item.occurredAt }}</td><td>{{ item.actorDisplayName || item.actorLoginName }}</td><td :title="item.action">
                  {{ productActionLabel(item.action) }}
                </td><td class="history-detail">
                  {{ item.detail || '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
        <p
          v-else
          class="data-state"
        >
          暂无变更记录。
        </p>
      </article>
    </template>
    <ProductEditDialog
      v-if="editing && formValue"
      :key="focusVariantId ?? 'parent'"
      :initial="formValue"
      :focus-variant-id="focusVariantId"
      :busy="busy"
      :error="error"
      @close="editing = false"
      @submit="save"
    />
    <SkuDetailDialog
      v-if="selectedSku"
      :detail-row="selectedSku"
      :writable="writable"
      hide-parent-link
      @close="selectedSku = undefined"
      @edit="editSku"
    />
    <div
      v-if="confirmDisable"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="停用产品"
    >
      <section class="panel operation-dialog__card">
        <h2>停用产品</h2><p>确认停用“{{ product?.productName }}”？历史 SKU 和关联记录将保留。</p><p
          v-if="error"
          role="alert"
        >
          {{ error }}
        </p><div class="table-actions">
          <button
            type="button"
            class="secondary-action"
            :disabled="busy"
            @click="confirmDisable = false"
          >
            取消
          </button><button
            type="button"
            class="danger-button"
            :disabled="busy"
            @click="disable"
          >
            确认停用
          </button>
        </div>
      </section>
    </div>
    <div
      v-if="preview"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="产品图片预览"
      tabindex="-1"
      @click.self="preview = undefined"
      @keydown.esc="preview = undefined"
    >
      <section class="panel operation-dialog__card detail-preview">
        <button
          type="button"
          aria-label="关闭图片预览"
          @click="preview = undefined"
        >
          关闭
        </button><AuthenticatedCatalogImage
          v-if="preview.id"
          :image-id="preview.id"
          :alt="preview.name"
        /><img
          v-else
          :src="preview.url"
          :alt="preview.name"
          referrerpolicy="no-referrer"
        ><p>{{ preview.name }}</p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.sku-overview-link { border: 0; padding: 4px 0; background: transparent; color: var(--accent); text-decoration: underline; cursor: pointer; }
.product-detail, .product-detail > *, .product-detail .panel { min-width: 0; max-width: 100%; }
.detail-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.detail-summary > div { min-width: 0; overflow-wrap: anywhere; }
.detail-summary .table-actions { margin-left: auto; }
.detail-image { width: 96px; height: 96px; padding: 0; flex: 0 0 auto; background: #f7f8fa; border: 1px solid var(--line); cursor: zoom-in; }
.detail-image :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.detail-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 16px 0; }
.detail-fields dt { color: var(--muted); font-size: 12px; }
.detail-fields dd { margin: 6px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.detail-wide { grid-column: 1 / -1; }
.detail-tabs .is-current { color: var(--accent); background: var(--accent-soft); border-color: var(--accent); }
.history-detail { white-space: pre-wrap; min-width: 200px; overflow-wrap: anywhere; }
.detail-preview :deep(img) { display: block; width: 100%; height: auto; max-height: 70vh; object-fit: contain; }
@media (max-width: 800px) { .detail-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .detail-fields { grid-template-columns: 1fr; } .detail-summary .table-actions { margin-left: 0; } }
</style>
