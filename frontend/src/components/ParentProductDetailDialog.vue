<script setup lang="ts">
import { beijingDateTime } from '@/utils/dateTime'


import { onMounted, ref, watch } from 'vue'
import { domainApi, type ProductDetailDto, type ProductHistoryDto, type ProductVariantDto, type SkuListItemDto } from '@/api/domain'
import { productActionLabel } from '@/utils/productHistory'
import AccessDialog from './AccessDialog.vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
import ProductName from './ProductName.vue'
const props = defineProps<{ productId: string; writable: boolean }>()
const emit = defineEmits<{ close: []; edit: [product: ProductDetailDto]; sku: [row: SkuListItemDto] }>()
const product = ref<ProductDetailDto>(), loading = ref(false), error = ref(''), tab = ref('basic')
const history = ref<ProductHistoryDto[]>([]), historyLoading = ref(false), historyError = ref('')
async function load() { loading.value = true; error.value = ''; try { product.value = await domainApi.product(props.productId) } catch (e) { error.value = e instanceof Error ? e.message : '详情加载失败' } finally { loading.value = false } }
async function loadHistory() { historyLoading.value = true; historyError.value = ''; try { history.value = await domainApi.productHistory(props.productId) } catch (e) { historyError.value = e instanceof Error ? e.message : '变更记录加载失败' } finally { historyLoading.value = false } }
watch(tab, value => { if (value === 'history') void loadHistory() })
onMounted(load)
function openSku(variant: ProductVariantDto) { if (product.value) emit('sku', { productId: product.value.id, productCode: product.value.productCode, productName: product.value.productName, productStatus: product.value.status, variant, businessAvailable: product.value.status === 'ACTIVE' && variant.status === 'ACTIVE' }) }
function status(value: string) { return ({ ACTIVE: '启用', DISABLED: '停用', DRAFT: '待定' } as Record<string, string>)[value] ?? value }
</script>
<template>
  <AccessDialog
    title="产品详情"
    wide
    @close="emit('close')"
  >
    <p
      v-if="loading"
      role="status"
    >
      正在加载产品详情…
    </p>
    <div
      v-else-if="error"
      role="alert"
    >
      {{ error }} <button
        type="button"
        @click="load"
      >
        重试
      </button>
    </div>
    <template v-else-if="product">
      <nav
        class="access-tabs"
        aria-label="产品详情页签"
      >
        <button
          type="button"
          :class="{ active: tab === 'basic' }"
          @click="tab = 'basic'"
        >
          基础信息
        </button><button
          type="button"
          :class="{ active: tab === 'history' }"
          @click="tab = 'history'"
        >
          变更记录
        </button>
      </nav>
      <div v-if="tab === 'basic'">
        <div class="parent-detail-summary">
          <AuthenticatedCatalogImage
            :image-id="product.imageAssetId ?? undefined"
            alt="父体图片"
          /><div>
            <h3>{{ product.productName }}</h3><p>父体编码：{{ product.productCode }}</p><p>产品状态：{{ status(product.status) }}</p><p>说明：{{ product.description || '—' }}</p><p class="access-muted">
              创建时间：{{ beijingDateTime(product.createdAt) }} · 更新时间：{{ beijingDateTime(product.updatedAt) }}
            </p>
          </div>
        </div>
        <h3 class="parent-detail-heading">
          SKU 概览 <span>{{ product.variants.length }} 个子体</span>
        </h3>
        <div class="parent-detail-table">
          <table class="data-table">
            <thead><tr><th>图片</th><th>SKU 名称 / 编码</th><th>SKU 状态</th><th>包装重量</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="variant in product.variants"
                :key="variant.id"
              >
                <td>
                  <img
                    v-if="!variant.imageAssetId && variant.imageUrl"
                    class="catalog-image"
                    :src="variant.imageUrl"
                    :alt="variant.variantName"
                    referrerpolicy="no-referrer"
                  >
                  <AuthenticatedCatalogImage
                    v-else
                    :image-id="variant.imageAssetId"
                    :alt="variant.variantName"
                  />
                </td><td>
                  <ProductName
                    :text="variant.variantName"
                    :highlighted="variant.nameHighlighted"
                    :styles="variant.nameStyles"
                  /><small>{{ variant.skuCode }}</small>
                </td><td>{{ status(variant.status) }}</td><td>{{ variant.packageWeightGrams == null ? '—' : variant.packageWeightGrams + ' g' }}</td><td>
                  <button
                    type="button"
                    class="row-detail-link"
                    @click="openSku(variant)"
                  >
                    详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else>
        <p
          v-if="historyLoading"
          role="status"
        >
          正在加载变更记录…
        </p><p
          v-else-if="historyError"
          role="alert"
        >
          {{ historyError }} <button
            type="button"
            @click="loadHistory"
          >
            重试
          </button>
        </p>
        <div
          v-else
          class="parent-detail-table"
        >
          <table class="data-table">
            <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>内容</th></tr></thead><tbody>
              <tr
                v-for="item in history"
                :key="item.id"
              >
                <td>{{ beijingDateTime(item.occurredAt) }}</td><td>{{ item.actorDisplayName || item.actorLoginName }}</td><td>{{ productActionLabel(item.action) }}</td><td>{{ item.detail || '—' }}</td>
              </tr><tr v-if="!history.length">
                <td colspan="4">
                  暂无变更记录
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
    <template #footer>
      <button
        type="button"
        @click="emit('close')"
      >
        关闭
      </button><button
        v-if="writable && product"
        type="button"
        class="primary-button"
        @click="emit('edit', product)"
      >
        编辑产品
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.parent-detail-summary{display:flex;gap:18px;padding:14px;background:#f7f9fc;border:1px solid var(--line);border-radius:6px}.parent-detail-summary h3{font-size:16px;margin:0 0 10px}.parent-detail-summary p{font-size:13px;margin:6px 0;overflow-wrap:anywhere}.parent-detail-summary :deep(.catalog-image){width:112px;height:112px;object-fit:contain;flex:none}.parent-detail-heading{font-size:14px;margin:18px 0 10px}.parent-detail-heading span{font-size:12px;color:var(--muted);font-weight:400;margin-left:8px}.parent-detail-table{overflow:auto}.parent-detail-table table{width:100%;min-width:550px}.parent-detail-table small{display:block;color:var(--muted);font-size:12px;margin-top:6px}
</style>
