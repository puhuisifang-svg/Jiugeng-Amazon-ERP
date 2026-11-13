<script setup lang="ts">
import { beijingDateTime } from '@/utils/dateTime'


import ProductName from './ProductName.vue'
import { profileFields, profileElements } from '@/api/customs'
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { domainApi, type SkuListItemDto, type ProductHistoryDto, type ProductVariantDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SkuDocumentsPanel from '@/components/SkuDocumentsPanel.vue'
import TableViewport from '@/components/TableViewport.vue'
import { productActionLabel } from '@/utils/productHistory'
const props = defineProps<{ detailRow: SkuListItemDto; writable: boolean; error?: string; hideParentLink?: boolean }>()
const emit = defineEmits<{ close: []; edit: [row: SkuListItemDto] }>()
const detailVariant = computed(() => props.detailRow.variant)
const customs = computed(() => detailVariant.value.customs)
const customsElements = computed(() => customs.value ? profileElements(customs.value) : {})
const detailTab = ref('basic'), detailHistory = ref<ProductHistoryDto[]>([]), historyError = ref('')
function labelStatus(status: string) { return status === 'ACTIVE' ? '启用' : status === 'DRAFT' ? '待定' : '停用' }
function dimensionLabel(variant: ProductVariantDto) {
  const item = [variant.itemLengthCm, variant.itemWidthCm, variant.itemHeightCm]
  const pack = [variant.packageLengthCm, variant.packageWidthCm, variant.packageHeightCm]
  if (item.every(value => value != null)) return `产品 ${item.join('×')} cm`
  if (pack.every(value => value != null)) return `包装 ${pack.join('×')} cm`
  return variant.itemDimensions || variant.packageDimensions || '—'
}
let sequence = 0
watch(() => props.detailRow, async row => {
  const request = ++sequence
  detailTab.value = 'basic'; detailHistory.value = []; historyError.value = ''
  try { const result = await domainApi.productHistory(row.productId); if (request === sequence) detailHistory.value = result }
  catch { if (request === sequence) historyError.value = '操作日志加载失败，请重新打开详情重试。' }
}, { immediate: true })
</script>
<template>
  <div
    v-if="detailRow"
    class="operation-dialog"
    role="dialog"
    aria-modal="true"
    @click.self="emit('close')"
    @keydown.esc="emit('close')"
  >
    <section class="panel operation-dialog__card sku-detail-dialog">
      <button
        class="operation-dialog__close"
        type="button"
        aria-label="关闭"
        @click="emit('close')"
      >
        ×
      </button><header class="sku-detail-header">
        <AuthenticatedCatalogImage
          v-if="detailVariant?.imageAssetId"
          :image-id="detailVariant.imageAssetId"
          :alt="`${detailVariant.variantName}主图`"
        /><img
          v-else-if="detailVariant?.imageUrl"
          class="product-thumbnail"
          :src="detailVariant.imageUrl"
          alt="SKU主图"
        ><span
          v-else
          class="product-thumbnail product-thumbnail--placeholder"
        >图</span><div>
          <h2>
            <ProductName
              :text="detailVariant?.variantName ?? ''"
              :styles="detailVariant?.nameStyles"
              :highlighted="detailVariant?.nameHighlighted"
            />
          </h2><p
            v-if="error"
            class="data-state data-state--error"
            role="alert"
          >
            {{ error }}
          </p><div class="sku-identity">
            <p><span>SKU 编码</span> {{ detailVariant?.skuCode }}</p><p><span>所属父体</span> {{ detailRow.productName }}</p><p><span>父体编码</span> {{ detailRow.productCode }}</p>
          </div>
        </div>
      </header><nav class="product-editor-tabs">
        <button
          v-for="tab in ([['basic','基础信息'],['purchase','采购信息'],['specs','规格信息'],['materials','关联辅料'],['customs','报关资料'],['manual','说明书管理'],['history','操作日志']] as const)"
          :key="tab[0]"
          type="button"
          :class="{ active: detailTab === tab[0] }"
          @click="detailTab = tab[0]"
        >
          {{ tab[1] }}
        </button>
      </nav>
      <div class="sku-detail-body">
        <section
          v-if="detailTab === 'basic'"
          class="sku-basic-panel"
        >
          <div class="sku-detail-image sku-basic-image">
            <AuthenticatedCatalogImage
              v-if="detailVariant?.imageAssetId"
              :image-id="detailVariant.imageAssetId"
              :alt="`${detailVariant.variantName}主图`"
            /><img
              v-else-if="detailVariant?.imageUrl"
              :src="detailVariant.imageUrl"
              alt="SKU主图"
            ><p v-else>
              暂无图片。
            </p>
          </div>
          <dl
            class="sku-detail-grid"
          >
            <dt>SKU</dt><dd>{{ detailVariant?.skuCode }}</dd><dt>品名</dt><dd>
              <ProductName
                :text="detailVariant?.variantName ?? ''"
                :styles="detailVariant?.nameStyles"
                :highlighted="detailVariant?.nameHighlighted"
              />
            </dd><dt>SKU 状态</dt><dd>{{ labelStatus(detailVariant?.status ?? '') }}</dd>
          </dl>
          <p
            v-if="detailRow.productStatus !== 'ACTIVE'"
            class="sku-parent-notice"
          >
            所属父体{{ labelStatus(detailRow.productStatus) }}，当前 SKU 暂不可用于业务；请到父体资料中调整。
          </p>
        </section>
        <section
          v-else-if="detailTab === 'customs'"
          class="sku-customs-panel"
        >
          <template v-if="customs">
            <p class="customs-summary">
              出口美国 · 申报要素
            </p>
            <dl class="sku-detail-grid">
              <dt>HS</dt><dd>{{ customs.chinaCode || '—' }}</dd>
              <dt>品名</dt><dd>{{ customs.nameZh || '—' }}</dd>
              <template
                v-for="[key, label] in profileFields(customs)"
                :key="key"
              >
                <dt>{{ label }}</dt><dd>{{ customsElements[key] || '—' }}</dd>
              </template>
              <dt>计量单位</dt><dd>{{ customs.measurementUnit || customs.unit || '—' }}</dd>
            </dl>
          </template>
          <p
            v-else
            class="data-state"
          >
            暂无报关资料{{ writable ? '，请点击“编辑 SKU”补充。' : '。' }}
          </p>
        </section>
        <div
          v-else-if="detailTab === 'purchase'"
          class="detail-list"
        >
          <p v-if="!detailVariant?.suppliers.length">
            未关联供应商。
          </p><p
            v-for="supplier in detailVariant?.suppliers"
            :key="supplier.supplierId"
          >
            {{ supplier.primary ? '主供应商' : '供应商' }}：{{ supplier.supplierId }} <a
              v-if="supplier.purchaseUrl"
              :href="supplier.purchaseUrl"
              target="_blank"
              rel="noopener"
            >采购链接</a>
          </p>
        </div>
        <dl
          v-else-if="detailTab === 'specs'"
          class="sku-detail-grid"
        >
          <dt>产品规格</dt><dd>{{ detailVariant ? dimensionLabel(detailVariant) : '—' }}</dd><dt>包装规格</dt><dd>{{ [detailVariant?.packageLengthCm, detailVariant?.packageWidthCm, detailVariant?.packageHeightCm].every(value => value != null) ? `${detailVariant?.packageLengthCm}×${detailVariant?.packageWidthCm}×${detailVariant?.packageHeightCm} cm` : detailVariant?.packageDimensions || '—' }}</dd><dt>包装重量</dt><dd>{{ detailVariant?.packageWeightGrams != null ? `${detailVariant.packageWeightGrams} g` : '—' }}</dd>
        </dl>
        <div
          v-else-if="detailTab === 'materials'"
          class="detail-list"
        >
          <p v-if="!detailVariant?.materials.length">
            未关联辅料。
          </p><p
            v-for="material in detailVariant?.materials"
            :key="material.materialId"
          >
            {{ material.materialId }} · {{ material.standardQuantity }} {{ material.unit }} · 损耗率 {{ material.lossRate }}
          </p>
        </div>
        <SkuDocumentsPanel
          v-else-if="detailTab === 'manual' && detailVariant?.id"
          :key="`${detailVariant.id}-${detailTab}`"
          :product-id="detailRow.productId"
          :variant-id="detailVariant.id"
          kind="MANUAL"
        />
        <div
          v-else
          class="detail-list operation-history"
        >
          <p
            v-if="historyError"
            class="data-state data-state--error"
            role="alert"
          >
            {{ historyError }}
          </p><p v-else-if="!detailHistory.length">
            暂无可见操作日志。
          </p>
          <TableViewport v-else>
            <table class="data-table">
              <thead><tr><th>操作时间</th><th>操作人</th><th>操作类型</th><th>操作内容</th></tr></thead><tbody>
                <tr
                  v-for="event in detailHistory"
                  :key="event.id"
                >
                  <td>{{ beijingDateTime(event.occurredAt) }}</td><td>{{ event.actorDisplayName || event.actorLoginName }}</td><td :title="event.action">
                    {{ productActionLabel(event.action) }}
                  </td><td>{{ event.detail || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
      </div>
      <footer class="ui-dialog-actions dialog-footer">
        <RouterLink
          v-if="!hideParentLink"
          :to="`/products/${detailRow.productId}`"
        >
          查看产品完整资料
        </RouterLink><button
          v-if="writable"
          class="ui-button-primary"
          type="button"
          @click="emit('edit', detailRow)"
        >
          编辑 SKU
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.sku-basic-image{min-height:0;padding:12px;border:1px solid var(--line);border-radius:6px;margin-top:12px;background:#f8fafc}.sku-basic-image :deep(.catalog-image),.sku-basic-image img{max-height:180px;max-width:100%;object-fit:contain}.sku-detail-grid{grid-template-columns:120px minmax(0,1fr) 120px minmax(0,1fr);margin:12px 0}.sku-detail-grid dd{white-space:pre-wrap;overflow-wrap:anywhere;min-width:0}.customs-summary{margin:12px 0;color:var(--muted);font-size:13px}@media(max-width:640px){.sku-detail-grid{grid-template-columns:100px minmax(0,1fr)}}
</style>
