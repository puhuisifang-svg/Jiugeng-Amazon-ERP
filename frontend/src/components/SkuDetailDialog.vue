<script setup lang="ts">
import '@/styles/productManagement.css'
import ProductHistoryTable from './ProductHistoryTable.vue'
import ProductUiState from './ProductUiState.vue'
import { productManagementApi } from '@/api/productManagement'
import { runtimeConfig } from '@/config/runtime'
import { beijingDateTime } from '@/utils/dateTime'


import ProductName from './ProductName.vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { session } from '@/state/session'
import { domainApi, type SkuListItemDto, type ProductHistoryDto, type ProductVariantDto, type SupplierLinkDto, type ProductMaterialRelationDto, type SupplierDto, type MaterialDetailDto } from '@/api/domain'
import SkuDocumentsPanel from '@/components/SkuDocumentsPanel.vue'
import TableViewport from '@/components/TableViewport.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import SupplierContactLinks from './SupplierContactLinks.vue'
const props = defineProps<{ detailRow: SkuListItemDto; writable: boolean; error?: string; initialTab?: 'basic' | 'purchase' | 'materials' }>()
const emit = defineEmits<{ close: []; edit: [row: SkuListItemDto] }>()
const detailVariant = computed(() => props.detailRow.variant)
const detailTab = ref('basic'), detailHistory = ref<ProductHistoryDto[]>([]), historyError = ref('')
const supplierLinks = ref<SupplierLinkDto[]>([]), materialRelations = ref<ProductMaterialRelationDto[]>([])
const relationLoading = ref(false), relationError = ref(''), relationRevision = ref(0)
const supplierDetails = ref<Record<string, SupplierDto>>({}), materialDetails = ref<Record<string, MaterialDetailDto>>({})
const detailFailures = ref<Record<string, boolean>>({}), detailPending = ref<Record<string, boolean>>({})
let retryDetail: (id: string) => Promise<void> = async () => {}
const expandedRelations = ref<Record<string, boolean>>({})
const lossPercent = (value: number) => `${Number((value * 100).toFixed(4))}%`
function purchaseLink(value?: string) {
  if (!value) return undefined
  try { return ['http:', 'https:'].includes(new URL(value).protocol) ? value : undefined } catch { return undefined }
}
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
const materialAccess = computed(() => session.canAccess(['products.read', 'materials.read']))
watch([() => props.detailRow, detailTab, relationRevision, supplierAccess, materialAccess], async ([row, tab], _old, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  expandedRelations.value = {}
  supplierLinks.value = []; materialRelations.value = []; relationError.value = ''; relationLoading.value = false
  supplierDetails.value = {}; materialDetails.value = {}; detailFailures.value = {}; detailPending.value = {}
  if (tab !== 'purchase' && tab !== 'materials') return
  if (tab === 'purchase' ? !supplierAccess.value : !materialAccess.value) return
  if (!row.variant.id) {
    relationError.value = '子体资料不完整，请关闭后刷新列表重试。'
    return
  }
  const fetchDetail = async (id: string) => {
    if (!active || detailPending.value[id]) return
    detailPending.value[id] = true; detailFailures.value[id] = false
    try {
      if (tab === 'purchase') {
        const detail = await domainApi.supplier(id)
        if (active) supplierDetails.value[id] = detail
      } else {
        const detail = await domainApi.material(id)
        if (active) materialDetails.value[id] = detail
      }
    } catch { if (active) detailFailures.value[id] = true }
    finally { if (active) detailPending.value[id] = false }
  }
  retryDetail = fetchDetail
  relationLoading.value = true
  try {
    if (tab === 'purchase') {
      const links = await domainApi.variantSupplierLinks(row.variant.id)
      if (active) supplierLinks.value = links
    } else {
      const relations = await domainApi.productMaterialRelations({ variantId: row.variant.id })
      if (active) materialRelations.value = relations
    }
    if (!active) return
    relationLoading.value = false
    const queue = [...new Set(tab === 'purchase' ? supplierLinks.value.map(link => link.supplierId) : materialRelations.value.map(link => link.materialId))]
    await Promise.all(Array.from({ length: Math.min(4, queue.length) }, async () => {
      while (active && queue.length) {
        const id = queue.shift()
        if (id) await fetchDetail(id)
      }
    }))
  } catch {
    if (active) relationError.value = tab === 'purchase' ? '供应商资料加载失败，请重试。' : '辅料资料加载失败，请重试。'
  } finally { if (active) relationLoading.value = false }
})
function labelStatus(status: string) { return status === 'ACTIVE' ? '启用' : status === 'DRAFT' ? '待定' : '停用' }
function dimensionLabel(variant: ProductVariantDto) {
  const item = [variant.itemLengthCm, variant.itemWidthCm, variant.itemHeightCm]
  const pack = [variant.packageLengthCm, variant.packageWidthCm, variant.packageHeightCm]
  if (item.every(value => value != null)) return `产品 ${item.join('×')} cm`
  if (pack.every(value => value != null)) return `包装 ${pack.join('×')} cm`
  return variant.itemDimensions || variant.packageDimensions || '—'
}
const historyLoading = ref(false)
let sequence = 0
async function loadHistory() {
  const request = ++sequence, row = props.detailRow
  historyLoading.value = true; historyError.value = ''
  try {
    const result = runtimeConfig.authMode === 'password' && row.variant.id ? await productManagementApi.skuHistory(row.productId,row.variant.id) : await domainApi.productHistory(row.productId)
    if (request === sequence) detailHistory.value = result
  } catch { if (request === sequence) historyError.value = '操作日志加载失败，请重试。' }
  finally { if (request === sequence) historyLoading.value = false }
}
watch([() => props.detailRow, () => props.initialTab], () => {
  detailTab.value = props.initialTab ?? 'basic'; detailHistory.value = []; void loadHistory()
}, { immediate: true })
onBeforeUnmount(() => { sequence++ })
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
    <section class="panel operation-dialog__card sku-detail-dialog product-ui">
      <button
        class="operation-dialog__close"
        type="button"
        aria-label="关闭"
        @click="emit('close')"
      >
        ×
      </button><header class="sku-detail-header">
        <div class="product-detail-main-image">
          <PurchaseItemImage
            :image-asset-id="detailVariant.imageAssetId"
            :image-url="detailVariant.imageUrl"
            :alt="detailVariant.variantName"
          />
        </div>
        <div>
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
            <p><span>所属父体</span> {{ detailRow.productName }} <span :class="['status-badge', `status-badge--${detailVariant.status.toLowerCase()}`]">{{ labelStatus(detailVariant.status) }}</span></p>
          </div>
        </div>
      </header><nav class="product-editor-tabs">
        <button
          v-for="tab in ([['basic','基础信息'],['purchase','采购信息'],['specs','规格信息'],['materials','关联辅料'],['manual','说明书管理'],['history','操作日志']] as const)"
          :key="tab[0]"
          type="button"
          :class="{ active: detailTab === tab[0] }"
          @click="detailTab = tab[0]"
        >
          {{ tab[1] }}
        </button>
      </nav>
      <div
        class="sku-detail-body"
        :class="{ 'sku-detail-body--relations': detailTab === 'purchase' || detailTab === 'materials' }"
      >
        <section
          v-if="detailTab === 'basic'"
          class="sku-basic-panel"
        >
          <dl class="sku-detail-grid product-basic-facts">
            <dt>产品规格</dt><dd>{{ dimensionLabel(detailVariant) }}</dd>
            <dt>包装重量</dt><dd>{{ detailVariant.packageWeightGrams != null ? `${detailVariant.packageWeightGrams} g` : '未填写' }}</dd>
            <dt>供应商</dt><dd>
              <button
                v-if="supplierAccess"
                type="button"
                class="product-text-button"
                @click="detailTab = 'purchase'"
              >
                {{ detailVariant.suppliers.length }} 家 <span aria-hidden="true">›</span>
              </button><span v-else>无查看权限</span>
            </dd>
            <dt>关联辅料</dt><dd>
              <button
                v-if="materialAccess"
                type="button"
                class="product-text-button"
                @click="detailTab = 'materials'"
              >
                {{ detailVariant.materials.length }} 种 <span aria-hidden="true">›</span>
              </button><span v-else>无查看权限</span>
            </dd>
          </dl>
          <p
            v-if="detailRow.productStatus !== 'ACTIVE'"
            class="sku-parent-notice"
          >
            所属父体{{ labelStatus(detailRow.productStatus) }}，当前 SKU 暂不可用于业务；请到父体资料中调整。
          </p>
        </section>
        <div
          v-else-if="detailTab === 'purchase'"
          class="detail-list sku-relation-panel"
        >
          <ProductUiState
            v-if="!supplierAccess"
            title="暂无供应商资料查看权限。"
          />
          <ProductUiState
            v-else-if="relationLoading"
            loading
            title="正在加载供应商资料…"
          />
          <ProductUiState
            v-else-if="relationError"
            error
            title="供应商资料加载失败"
            :description="relationError"
            @retry="relationRevision++"
          />
          <ProductUiState
            v-else-if="!supplierLinks.length"
            title="未关联供应商。"
            description="可在编辑子体时维护关联资料。"
          />
          <TableViewport
            v-else
            class="relation-viewport"
          >
            <table
              class="relation-table"
              aria-label="采购供应商"
            >
              <thead>
                <tr>
                  <th scope="col">
                    供应商
                  </th><th scope="col">
                    联系人 / 微信
                  </th><th scope="col">
                    采购 / 电话
                  </th><th scope="col">
                    状态
                  </th><th scope="col">
                    更多资料
                  </th>
                </tr>
              </thead>
              <tbody>
                <template
                  v-for="supplier in supplierLinks"
                  :key="supplier.id"
                >
                  <tr :class="{ 'relation-row--primary': supplier.primary }">
                    <td class="relation-name-cell">
                      <strong class="relation-name">{{ supplierDetails[supplier.supplierId]?.name || supplier.supplierName || '供应商名称未维护' }}</strong>
                      <div class="relation-meta">
                        <span
                          v-if="supplier.primary"
                          class="relation-primary"
                        >主供应商</span>
                        <span>{{ supplierDetails[supplier.supplierId]?.categories?.map(category => category === 'PRODUCT' ? '产品' : '辅料').join('、') }}</span>
                      </div>
                      <p
                        v-if="detailFailures[supplier.supplierId]"
                        class="relation-feedback"
                        role="alert"
                      >
                        详细资料加载失败 <button
                          type="button"
                          :disabled="detailPending[supplier.supplierId]"
                          @click="retryDetail(supplier.supplierId)"
                        >
                          重试详情
                        </button>
                      </p>
                      <small
                        v-else-if="!supplierDetails[supplier.supplierId]"
                        role="status"
                      >正在加载联系资料…</small>
                    </td>
                    <td>
                      {{ supplierDetails[supplier.supplierId] ? supplierDetails[supplier.supplierId]?.contactPerson || '未填写' : '—' }}
                      <small>微信：{{ supplierDetails[supplier.supplierId] ? supplierDetails[supplier.supplierId]?.wechatId || '未填写' : '—' }}</small>
                    </td>
                    <td class="relation-contact-links">
                      <SupplierContactLinks
                        v-if="supplierDetails[supplier.supplierId]"
                        :supplier="supplierDetails[supplier.supplierId]"
                        :purchase-url="supplier.purchaseUrl"
                      />
                      <a
                        v-else-if="purchaseLink(supplier.purchaseUrl)"
                        :href="purchaseLink(supplier.purchaseUrl)"
                        target="_blank"
                        rel="noopener noreferrer"
                      >采购链接</a>
                      <span v-else>—</span>
                    </td>
                    <td>
                      <span
                        class="relation-badge"
                        :class="{ 'relation-badge--muted': supplier.supplierStatus === 'DISABLED' }"
                      >{{ supplier.supplierStatus === 'DISABLED' ? '已停用' : '启用' }}</span>
                    </td>
                    <td class="relation-action-cell">
                      <button
                        class="relation-expand"
                        type="button"
                        :aria-expanded="!!expandedRelations[supplier.id]"
                        :aria-controls="`supplier-extra-${supplier.id}`"
                        :aria-label="`${expandedRelations[supplier.id] ? '收起' : '展开'}${supplier.supplierName}的更多资料`"
                        @click="expandedRelations[supplier.id] = !expandedRelations[supplier.id]"
                      >
                        {{ expandedRelations[supplier.id] ? '收起 ▴' : '展开 ▾' }}
                      </button>
                    </td>
                  </tr>
                  <tr
                    v-show="expandedRelations[supplier.id]"
                    :id="`supplier-extra-${supplier.id}`"
                    class="relation-expanded-row"
                  >
                    <td colspan="5">
                      <div class="relation-extra">
                        <dl class="relation-notes">
                          <dt>采购备注</dt><dd>{{ supplier.remark || '—' }}</dd>
                          <dt>供应商备注</dt><dd>{{ supplierDetails[supplier.supplierId]?.remark || '—' }}</dd>
                          <dt>关联更新</dt><dd>{{ beijingDateTime(supplier.updatedAt) }}</dd>
                        </dl>
                        <div
                          v-if="supplierDetails[supplier.supplierId]?.qrImageId || supplierDetails[supplier.supplierId]?.qrImageUrl"
                          class="relation-qr"
                        >
                          <PurchaseItemImage
                            :image-asset-id="supplierDetails[supplier.supplierId]?.qrImageId"
                            :image-url="supplierDetails[supplier.supplierId]?.qrImageUrl"
                            :alt="`${supplier.supplierName}联系图片`"
                          />
                          <small>联系图片 · 点击放大</small>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <dl
          v-else-if="detailTab === 'specs'"
          class="sku-detail-grid"
        >
          <dt>产品规格</dt><dd>{{ detailVariant ? dimensionLabel(detailVariant) : '—' }}</dd><dt>包装规格</dt><dd>{{ [detailVariant?.packageLengthCm, detailVariant?.packageWidthCm, detailVariant?.packageHeightCm].every(value => value != null) ? `${detailVariant?.packageLengthCm}×${detailVariant?.packageWidthCm}×${detailVariant?.packageHeightCm} cm` : detailVariant?.packageDimensions || '—' }}</dd><dt>包装重量</dt><dd>{{ detailVariant?.packageWeightGrams != null ? `${detailVariant.packageWeightGrams} g` : '—' }}</dd>
        </dl>
        <div
          v-else-if="detailTab === 'materials'"
          class="detail-list sku-relation-panel"
        >
          <ProductUiState
            v-if="!materialAccess"
            title="暂无辅料资料查看权限。"
          />
          <ProductUiState
            v-else-if="relationLoading"
            loading
            title="正在加载辅料资料…"
          />
          <ProductUiState
            v-else-if="relationError"
            error
            title="辅料资料加载失败"
            :description="relationError"
            @retry="relationRevision++"
          />
          <ProductUiState
            v-else-if="!materialRelations.length"
            title="未关联辅料。"
            description="可在编辑子体时维护关联资料。"
          />
          <TableViewport
            v-else
            class="relation-viewport"
          >
            <table
              class="relation-table relation-table--materials"
              aria-label="关联辅料"
            >
              <thead>
                <tr>
                  <th scope="col">
                    辅料
                  </th><th scope="col">
                    规格
                  </th><th scope="col">
                    标准用量
                  </th><th scope="col">
                    损耗率
                  </th><th scope="col">
                    状态
                  </th><th scope="col">
                    更多资料
                  </th>
                </tr>
              </thead>
              <tbody>
                <template
                  v-for="material in materialRelations"
                  :key="material.id"
                >
                  <tr>
                    <td class="relation-name-cell">
                      <div class="relation-material">
                        <div class="relation-material-image">
                          <PurchaseItemImage
                            :image-asset-id="materialDetails[material.materialId]?.imageAssetId"
                            :image-url="materialDetails[material.materialId]?.imageUrl"
                            :alt="material.materialName"
                          />
                        </div>
                        <div>
                          <strong class="relation-name">{{ materialDetails[material.materialId]?.name || material.materialName || '辅料名称未维护' }}</strong>
                          <small>{{ materialDetails[material.materialId]?.materialType || '辅料' }}</small>
                          <p
                            v-if="detailFailures[material.materialId]"
                            class="relation-feedback"
                            role="alert"
                          >
                            详细资料加载失败 <button
                              type="button"
                              :disabled="detailPending[material.materialId]"
                              @click="retryDetail(material.materialId)"
                            >
                              重试详情
                            </button>
                          </p>
                          <small
                            v-else-if="!materialDetails[material.materialId]"
                            role="status"
                          >正在加载辅料资料…</small>
                        </div>
                      </div>
                    </td>
                    <td>{{ materialDetails[material.materialId] ? materialDetails[material.materialId]?.specification || '未填写' : '—' }}</td>
                    <td class="relation-quantity">
                      {{ material.standardQuantity }} {{ material.unit }}
                    </td>
                    <td class="relation-loss">
                      {{ lossPercent(material.lossRate) }}
                    </td>
                    <td>
                      <span
                        class="relation-badge"
                        :class="{ 'relation-badge--muted': !material.enabled }"
                      >{{ material.enabled ? '关联启用' : '关联停用' }}</span>
                      <small>{{ materialDetails[material.materialId] ? (materialDetails[material.materialId]?.status === 'ACTIVE' ? '辅料启用' : '辅料停用') : '辅料状态：—' }}</small>
                    </td>
                    <td class="relation-action-cell">
                      <button
                        class="relation-expand"
                        type="button"
                        :aria-expanded="!!expandedRelations[material.id]"
                        :aria-controls="`material-extra-${material.id}`"
                        :aria-label="`${expandedRelations[material.id] ? '收起' : '展开'}${material.materialName}的更多资料`"
                        @click="expandedRelations[material.id] = !expandedRelations[material.id]"
                      >
                        {{ expandedRelations[material.id] ? '收起 ▴' : '展开 ▾' }}
                      </button>
                    </td>
                  </tr>
                  <tr
                    v-show="expandedRelations[material.id]"
                    :id="`material-extra-${material.id}`"
                    class="relation-expanded-row"
                  >
                    <td colspan="6">
                      <dl class="relation-notes">
                        <dt>关联备注</dt><dd>{{ material.remark || '—' }}</dd>
                        <dt>辅料备注</dt><dd>{{ materialDetails[material.materialId]?.remark || '—' }}</dd>
                        <dt>资料单位</dt><dd>{{ materialDetails[material.materialId]?.unit || '—' }}</dd>
                        <dt>关联更新</dt><dd>{{ beijingDateTime(material.updatedAt) }}</dd>
                      </dl>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <SkuDocumentsPanel
          v-else-if="detailTab === 'manual' && detailVariant?.id"
          :key="`${detailVariant.id}-${detailTab}`"
          :product-id="detailRow.productId"
          :variant-id="detailVariant.id"
          kind="MANUAL"
        />
        <ProductHistoryTable
          v-else
          :entries="detailHistory"
          :loading="historyLoading"
          :error="historyError"
          @retry="loadHistory"
        />
      </div>
      <footer class="ui-dialog-actions dialog-footer">
        <button
          type="button"
          class="secondary-action"
          @click="emit('close')"
        >
          关闭
        </button>
        <button
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
#app .sku-detail-body--relations { display: flex; flex-direction: column; overflow: hidden; padding: var(--ui-page-padding); }
.sku-detail-dialog .sku-relation-panel { display: flex; flex-direction: column; min-height: 0; padding: 0; }
.relation-viewport { min-height: 0; max-height: 100% !important; border: 1px solid var(--line); border-radius: 4px; }
.relation-table { min-width: 680px; font-size: 13px; }
.relation-table th, .relation-table td { padding: var(--ui-section-gap) 10px; text-align: left; vertical-align: middle; border-bottom: 1px solid var(--line); }
.relation-table th { height: var(--ui-control-height); box-sizing: border-box; color: var(--muted); font-size: 12px; font-weight: 500; white-space: nowrap; }
.relation-table td { overflow-wrap: anywhere; }
.relation-table tbody > tr:last-child td { border-bottom: 0; }
.relation-table tbody > tr:not(.relation-expanded-row):hover { background: #f8fafc; }
.relation-name-cell { width: 30%; }
.relation-name { display: block; color: var(--ink); font-size: 13px; font-weight: 600; }
.relation-meta { display: flex; align-items: center; flex-wrap: wrap; gap: var(--ui-section-gap); margin-top: 3px; color: var(--muted); font-size: 12px; }
.relation-primary { color: #2864a8; }
.relation-row--primary > td:first-child { box-shadow: inset 2px 0 #3976bf; }
.relation-table small { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
.relation-badge { display: inline-block; padding: 2px 5px; border-radius: 3px; color: #26744d; background: #f0f8f3; font-size: 12px; white-space: nowrap; }
.relation-badge--muted { color: #677387; background: #f0f2f5; }
.relation-contact-links :deep(a), .relation-expand { color: #2864a8; }
.relation-contact-links :deep(a) { margin: 0; text-decoration: underline; text-underline-offset: 3px; }
.relation-contact-links :deep(> span) { display: block; }
.relation-contact-links :deep(> span + span) { margin-top: 3px; }
.relation-material { display: flex; align-items: center; gap: var(--ui-page-padding); }
.relation-material > div:last-child { min-width: 0; }
.relation-material-image { flex: none; }
.relation-material-image :deep(.image-button), .relation-qr :deep(.image-button), .relation-material-image :deep(.catalog-image), .relation-qr :deep(.catalog-image) { display: grid; place-items: center; width: 64px; height: 64px; min-height: 0; padding: 0; border-radius: 4px; border: 1px solid var(--line); background: #f7f9fc; object-fit: contain; }
.relation-quantity, .relation-loss { font-variant-numeric: tabular-nums; white-space: nowrap; }
.relation-action-cell { width: 76px; }
.relation-expand { min-height: var(--ui-control-height); padding: 4px 6px; border: 0; border-radius: 4px; background: transparent; cursor: pointer; white-space: nowrap; }
.relation-expand:hover { background: #edf3fb; }
.relation-expanded-row { background: #f8fafc; }
.relation-extra { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 12px; }
.relation-notes { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 5px 10px; flex: 1; margin: 4px 0; min-width: 0; font-size: 12px; line-height: 1.6; }
.relation-notes dt { color: var(--muted); }
.relation-notes dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.relation-qr { flex: none; }
.relation-feedback { margin: 3px 0 0; font-size: 12px; color: #a34f25; }
.relation-feedback button { min-height: var(--ui-control-height); padding: 3px 6px; border: 1px solid var(--line); border-radius: 4px; color: #2864a8; background: white; cursor: pointer; }
.sku-basic-image{min-height:0;padding:12px;border:1px solid var(--line);border-radius:6px;margin-top:12px;background:#f8fafc}.sku-basic-image :deep(.catalog-image),.sku-basic-image img{max-height:180px;max-width:100%;object-fit:contain}.sku-detail-grid{grid-template-columns:120px minmax(0,1fr) 120px minmax(0,1fr);margin:12px 0}.sku-detail-grid dd{white-space:pre-wrap;overflow-wrap:anywhere;min-width:0}.customs-summary{margin:12px 0;color:var(--muted);font-size:13px}@media(max-width:640px){.sku-detail-grid{grid-template-columns:100px minmax(0,1fr)}}
</style>
