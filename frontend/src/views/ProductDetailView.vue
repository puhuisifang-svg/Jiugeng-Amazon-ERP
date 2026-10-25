<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { domainApi, type ProductDetailDto, type ProductInput, type ProductHistoryDto, type SupplierDto, type ProductMaterialRelationDto } from '@/api/domain'
import type { CustomsProfile } from '@/api/customs'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import ProductForm from '@/components/ProductForm.vue'
import SkuDocumentsPanel from '@/components/SkuDocumentsPanel.vue'
import SupplierContactLinks from '@/components/SupplierContactLinks.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute()
const product = ref<ProductDetailDto | null>(null), loading = ref(true), busy = ref(false), editing = ref(false)
const error = ref(''), success = ref(''), confirmDisable = ref(false)
const section = computed(() => String(route.params.section ?? 'overview'))
const writable = computed(() => runtimeConfig.authMode === 'password' && session.canAccess(['products.write']))
const supplierAccess = computed(() => product.value?.supplierDetailsAvailable && session.canAccess(['suppliers.read']))
const history = ref<ProductHistoryDto[]>([]), historyError = ref(''), historyLoading = ref(false)
const suppliers = ref<Record<string, SupplierDto>>({}), relations = ref<ProductMaterialRelationDto[]>([]), referenceError = ref('')
const preview = ref<{ id?: string | undefined; url?: string | undefined; name: string }>()
const documentSku = ref('')
const tabs = [['overview', '基础信息'], ['variants', 'SKU资料'], ['materials', '关联辅料'], ['documents', '说明书与标签'], ['changes', '变更记录']] as const
const customsFields: [keyof CustomsProfile, string][] = [['nameZh', '中文申报品名'], ['nameEn', '英文申报品名'], ['composition', '材质 / 成分'], ['usage', '用途'], ['brand', '品牌'], ['model', '型号'], ['originCountry', '原产国'], ['unit', '单位'], ['netWeightKg', '净重（kg）'], ['grossWeightKg', '毛重（kg）'], ['chinaCode', '中国商品编码'], ['usCode', '美国 HTS'], ['euCode', '欧盟编码'], ['ukCode', '英国编码'], ['declarationElements', '申报要素'], ['complianceNotes', '合规说明'], ['remark', '备注']]
function statusLabel(value: string) { return ({ ACTIVE: '启用', DRAFT: '待定', DISABLED: '停用' } as Record<string, string>)[value] ?? value }
function dimensions(values: (number | undefined)[], legacy?: string) { return values.every(value => value != null) ? values.join(' × ') + ' cm' : legacy || '—' }
const formValue = computed<ProductInput | undefined>(() => product.value ? ({
  imageAssetId: product.value.imageAssetId, productCode: product.value.productCode, productName: product.value.productName, productType: product.value.productType,
  description: product.value.description, status: product.value.status, version: product.value.version,
  variants: product.value.variants.map(item => ({ id: item.id, customs: item.customs, skuCode: item.skuCode, variantName: item.variantName,
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
  suppliers.value = {}; relations.value = []; referenceError.value = ''; preview.value = undefined
  try {
    const result = await domainApi.product(String(route.params.productId))
    if (sequence !== loadSequence) return
    product.value = result; documentSku.value = result.variants[0]?.id ?? ''
    const requests: Promise<void>[] = []
    if (supplierAccess.value) for (const id of new Set(result.variants.flatMap(item => item.suppliers.map(link => link.supplierId)))) {
      requests.push(domainApi.supplier(id).then(value => { if (sequence === loadSequence) suppliers.value[id] = value }))
    }
    if (session.canAccess(['materials.read'])) requests.push(domainApi.productMaterialRelations({ productId: result.id }).then(value => { if (sequence === loadSequence) relations.value = value }))
    void Promise.allSettled(requests).then(results => { if (sequence === loadSequence && results.some(item => item.status === 'rejected')) referenceError.value = '部分供应商或辅料名称加载失败，可刷新重试；产品资料仍可查看。' })
  } catch (exception) { if (sequence === loadSequence) error.value = exception instanceof Error ? exception.message : '产品详情加载失败，请重试。' }
  finally { if (sequence === loadSequence) loading.value = false }
}
let historySequence = 0
async function loadHistory() {
  const sequence = ++historySequence, id = product.value?.id
  history.value = []; historyError.value = ''; historyLoading.value = false
  if (section.value !== 'changes' || !id) return
  historyLoading.value = true
  try { const result = await domainApi.productHistory(id); if (sequence === historySequence) history.value = result }
  catch (exception) { if (sequence === historySequence) historyError.value = exception instanceof Error ? exception.message : '变更记录加载失败。' }
  finally { if (sequence === historySequence) historyLoading.value = false }
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
watch([section, () => product.value?.updatedAt, () => product.value?.id], loadHistory)
</script>

<template>
  <section class="page-stack product-detail">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <div class="detail-toolbar">
      <RouterLink
        class="secondary-action"
        to="/products"
      >
        返回产品管理
      </RouterLink>
      <button
        type="button"
        class="secondary-action"
        :disabled="loading || busy || editing"
        @click="load"
      >
        刷新
      </button>
      <button
        v-if="editing"
        type="button"
        class="secondary-action"
        :disabled="busy"
        @click="editing = false"
      >
        取消编辑
      </button>
    </div>
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
    <ProductForm
      v-else-if="editing && formValue"
      :initial="formValue"
      :busy="busy"
      submit-label="保存修改"
      show-cancel
      @submit="save"
      @cancel="editing = false"
    />
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
              @click="editing = true"
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
        aria-label="产品详情"
      >
        <RouterLink
          v-for="[key, label] in tabs"
          :key="key"
          :to="'/products/' + product.id + '/' + key"
          :class="{ 'is-current': section === key }"
          :aria-current="section === key ? 'page' : undefined"
        >
          {{ label }}
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
                  <RouterLink :to="'/products/' + product.id + '/variants'">
                    {{ variant.skuCode }}
                  </RouterLink>
                </td><td>{{ variant.variantName }}</td><td>{{ statusLabel(variant.status) }}</td><td>{{ variant.packageWeightGrams == null ? '—' : variant.packageWeightGrams + ' g' }}</td>
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
      <template v-else-if="section === 'variants'">
        <p
          v-if="referenceError"
          role="alert"
        >
          {{ referenceError }}
        </p><p
          v-if="!product.variants.length"
          class="data-state"
        >
          暂无 SKU。
        </p>
        <article
          v-for="variant in product.variants"
          :key="variant.id ?? variant.skuCode"
          class="panel compact-panel"
        >
          <div class="detail-summary">
            <button
              v-if="variant.imageAssetId || variant.imageUrl"
              type="button"
              class="detail-image"
              :aria-label="'查看' + variant.variantName + '图片'"
              @click="preview = { id: variant.imageAssetId, url: variant.imageUrl, name: variant.variantName }"
            >
              <AuthenticatedCatalogImage
                v-if="variant.imageAssetId"
                :image-id="variant.imageAssetId"
                :alt="variant.variantName"
              />
              <img
                v-else
                :src="variant.imageUrl"
                :alt="variant.variantName"
                loading="lazy"
                referrerpolicy="no-referrer"
              >
            </button>
            <div><h2>{{ variant.variantName }}</h2><strong>{{ variant.skuCode }}</strong></div><span>{{ statusLabel(variant.status) }}</span>
          </div>
          <dl class="detail-fields">
            <div><dt>产品规格</dt><dd>{{ dimensions([variant.itemLengthCm, variant.itemWidthCm, variant.itemHeightCm], variant.itemDimensions) }}</dd></div>
            <div><dt>包装规格</dt><dd>{{ dimensions([variant.packageLengthCm, variant.packageWidthCm, variant.packageHeightCm], variant.packageDimensions) }}</dd></div>
            <div><dt>包装重量</dt><dd>{{ variant.packageWeightGrams == null ? '—' : variant.packageWeightGrams + ' g' }}</dd></div>
          </dl>
          <div v-if="supplierAccess">
            <h3>采购信息</h3><p v-if="!variant.suppliers.length">
              未关联供应商。
            </p>
            <div
              v-for="link in variant.suppliers"
              :key="link.supplierId"
              class="detail-supplier"
            >
              <strong>{{ suppliers[link.supplierId]?.name || link.supplierId }}</strong><span v-if="link.primary">首选供应商</span><SupplierContactLinks
                :supplier="suppliers[link.supplierId]"
                :purchase-url="link.purchaseUrl"
              /><span>{{ link.remark }}</span>
            </div>
          </div><p v-else>
            供应商：无查看权限
          </p>
          <details>
            <summary>报关资料</summary>
            <template v-if="variant.customs">
              <p>{{ variant.customs.state === 'REVIEWED' ? '已复核' : '草稿' }}</p><dl class="detail-fields">
                <div
                  v-for="[key, label] in customsFields"
                  :key="key"
                >
                  <dt>{{ label }}</dt><dd>{{ variant.customs[key] === '' ? '—' : variant.customs[key] ?? '—' }}</dd>
                </div>
              </dl>
            </template><p v-else>
              未维护报关资料。
            </p>
          </details>
          <details v-if="variant.attributes?.length">
            <summary>历史动态属性（只读）</summary><p
              v-for="attribute in variant.attributes"
              :key="attribute.attributeCode"
            >
              {{ attribute.attributeCode }}：{{ attribute.attributeValue }}
            </p>
          </details>
        </article>
      </template>
      <article
        v-else-if="section === 'materials'"
        class="panel compact-panel"
      >
        <h2>关联辅料汇总</h2><p
          v-if="referenceError"
          role="alert"
        >
          {{ referenceError }}
        </p>
        <TableViewport>
          <table class="data-table">
            <thead><tr><th>SKU</th><th>辅料</th><th>标准用量</th><th>损耗率</th><th>状态</th><th>备注</th></tr></thead><tbody>
              <template
                v-for="variant in product.variants"
                :key="variant.id ?? variant.skuCode"
              >
                <tr
                  v-for="material in variant.materials"
                  :key="material.materialId"
                >
                  <td>{{ variant.skuCode }}</td><td>{{ relations.find(item => item.variantId === variant.id && item.materialId === material.materialId)?.materialName || material.materialId }}</td><td>{{ material.standardQuantity }} {{ material.unit }}</td><td>{{ material.lossRate }}</td><td>{{ material.enabled ? '启用' : '停用' }}</td><td>{{ material.remark || '—' }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </TableViewport>
        <p
          v-if="product.variants.every(item => !item.materials.length)"
          class="data-state"
        >
          尚未关联辅料。
        </p>
      </article>
      <article
        v-else-if="section === 'documents'"
        class="panel compact-panel"
      >
        <label>选择 SKU<select v-model="documentSku"><option
          v-for="variant in product.variants"
          :key="variant.id"
          :value="variant.id"
        >{{ variant.skuCode }} · {{ variant.variantName }}</option></select></label>
        <template v-if="documentSku">
          <SkuDocumentsPanel
            :key="documentSku + '-manual'"
            :product-id="product.id"
            :variant-id="documentSku"
            kind="MANUAL"
          /><SkuDocumentsPanel
            :key="documentSku + '-label'"
            :product-id="product.id"
            :variant-id="documentSku"
            kind="LABEL"
          />
        </template><p v-else>
          暂无可查看文件的 SKU。
        </p>
      </article>
      <article
        v-else-if="section === 'changes'"
        class="panel compact-panel"
      >
        <h2>变更记录</h2><p
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
                <td>{{ item.occurredAt }}</td><td>{{ item.actorDisplayName || item.actorLoginName }}</td><td>{{ item.action }}</td><td class="history-detail">
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
.product-detail, .product-detail > *, .product-detail .panel { min-width: 0; max-width: 100%; }
.detail-toolbar, .detail-summary, .detail-supplier { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.detail-summary > div { min-width: 0; overflow-wrap: anywhere; }
.detail-summary .table-actions { margin-left: auto; }
.detail-image { width: 96px; height: 96px; padding: 0; flex: 0 0 auto; background: #f7f8fa; border: 1px solid var(--line); cursor: zoom-in; }
.detail-image :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.detail-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 16px 0; }
.detail-fields dt { color: var(--muted); font-size: 12px; }
.detail-fields dd { margin: 6px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.detail-wide { grid-column: 1 / -1; }
.detail-tabs { flex-wrap: wrap; }
.detail-tabs .is-current { color: var(--accent); background: var(--accent-soft); border-bottom: 2px solid currentColor; }
.detail-supplier { padding: 8px 0; overflow-wrap: anywhere; }
.product-detail details { margin-top: 12px; }
.product-detail summary { cursor: pointer; font-weight: 600; }
.detail-preview :deep(img) { display: block; width: 100%; height: auto; max-height: 70vh; object-fit: contain; }
.history-detail { white-space: pre-wrap; min-width: 200px; overflow-wrap: anywhere; }
@media (max-width: 800px) { .detail-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .detail-fields { grid-template-columns: 1fr; } .detail-summary .table-actions { margin-left: 0; } }
</style>
