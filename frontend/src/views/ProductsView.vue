<script setup lang="ts">
import ListPagination from '@/components/ListPagination.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import CatalogDeleteDialog from '@/components/CatalogDeleteDialog.vue'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, reactive, ref, toRaw, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { domainApi, type BatchVariantPatchInput, type ProductDetailDto, type ProductHistoryDto, type ProductInput, type ProductVariantDto, type SkuListItemDto, type VariantStatus } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SkuDocumentsPanel from '@/components/SkuDocumentsPanel.vue'
import ProductForm from '@/components/ProductForm.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const imagePreview = ref<{ imageAssetId?: string | undefined; imageUrl?: string | undefined; variantName: string } | null>(null)
type ColumnKey = 'image' | 'sku' | 'name' | 'product' | 'status' | 'dimensions' | 'weight' | 'supplier' | 'material' | 'updated' | 'stock'
const columns: Array<{ key: ColumnKey; label: string }> = [
  { key: 'image', label: '图片' }, { key: 'sku', label: 'SKU' }, { key: 'name', label: '品名' },
  { key: 'product', label: '产品类型' }, { key: 'status', label: '状态' }, { key: 'dimensions', label: '规格' },
  { key: 'weight', label: '包装重量' }, { key: 'supplier', label: '供应商' }, { key: 'material', label: '辅料' }, { key: 'updated', label: '更新时间' }, { key: 'stock', label: '库存状态' },
]
const defaultColumns: ColumnKey[] = ['sku', 'image', 'name', 'product', 'status', 'stock', 'dimensions', 'weight']
const storageKey = computed(() => `jiugeng.product-management.columns.v2.${session.user.value?.id ?? 'current'}`)
function restoredColumns(): ColumnKey[] {
  try {
    const values = JSON.parse(localStorage.getItem(storageKey.value) ?? '[]') as ColumnKey[]
    const valid = Array.isArray(values) ? [...new Set(values.filter(key => columns.some(column => column.key === key)))] : []
    return valid.length ? (valid.includes('sku') ? valid : ['sku', ...valid]) : defaultColumns
  } catch { return defaultColumns }
}

const rows = ref<SkuListItemDto[]>([]); const products = ref<ProductDetailDto[]>([])
const total = ref(0); const page = ref(0); const size = ref(20); const loading = ref(true); const saving = ref(false)
const error = ref(''); const success = ref(''); const selected = ref<string[]>([])
const expanded = ref<string[]>([])
const deleteTarget = ref<{ product: ProductDetailDto; variant?: ProductVariantDto }>()
const deleteError = ref(''), deleting = ref(false)
function requestDelete(product: ProductDetailDto, variant?: ProductVariantDto) {
  deleteError.value = ''; deleteTarget.value = variant ? { product, variant } : { product }
}
async function confirmDelete() {
  if (deleting.value || !deleteTarget.value) return
  const { product, variant } = deleteTarget.value; deleting.value = true; deleteError.value = ''
  try {
    if (variant?.id) await domainApi.deleteSku(product.id, variant.id, product.version, variant.version ?? 0)
    else await domainApi.deleteProduct(product.id, product.version)
    deleteTarget.value = undefined; selected.value = []; page.value = 0
    success.value = variant ? 'SKU 及未使用的客户商品已删除。' : '父体、全部 SKU 及未使用的客户商品已删除。'
    await load()
  } catch (e) { deleteError.value = apiMessage(e, '删除失败，请重试。') }
  finally { deleting.value = false }
}
const appliedFilters = ref({ field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' })
const showCreate = ref(false); const showColumns = ref(false); const showBatch = ref(false); const showDetail = ref(false); const showEdit = ref(false); const showExport = ref(false)
const showAddSku = ref(false); const addSkuProductId = ref('')
const showBatchSearch = ref(false); const batchSearchText = ref('')
const exportScope = ref<'FILTERED' | 'SELECTED'>('FILTERED')
const detailRow = ref<SkuListItemDto | null>(null); const editProduct = ref<ProductDetailDto | null>(null); const focusVariantId = ref<string>()
const detailTab = ref<'basic' | 'purchase' | 'specs' | 'materials' | 'image' | 'history' | 'manual' | 'label'>('basic'); const detailHistory = ref<ProductHistoryDto[]>([])
const visibleColumns = ref<ColumnKey[]>(restoredColumns())
const columnDraft = ref<ColumnKey[]>([...visibleColumns.value])
const columnSearch = ref(''); const filteredColumns = computed(() => columns.filter(column => column.label.includes(columnSearch.value.trim()) && (column.key !== 'supplier' || session.canAccess(['suppliers.read']))))
const filters = reactive({ field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' })
const batch = reactive({ statusSelected: false, status: 'ACTIVE' as VariantStatus, itemDimensionsSelected: false, itemDimensionsMode: 'SET' as 'SET' | 'CLEAR', itemLengthCm: undefined as number | undefined, itemWidthCm: undefined as number | undefined, itemHeightCm: undefined as number | undefined, packageDimensionsSelected: false, packageDimensionsMode: 'SET' as 'SET' | 'CLEAR', packageLengthCm: undefined as number | undefined, packageWidthCm: undefined as number | undefined, packageHeightCm: undefined as number | undefined, packageWeightSelected: false, packageWeightMode: 'SET' as 'SET' | 'CLEAR', packageWeightGrams: undefined as number | undefined })
const writable = computed(() => session.canAccess(['products.write']))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const allSelected = computed(() => pageRows.value.length > 0 && pageRows.value.every(row => selected.value.includes(row.variant.id ?? '')))
const selectedRows = computed(() => rows.value.filter(row => selected.value.includes(row.variant.id ?? '')))
const selectedProducts = computed(() => products.value.filter(product => selectedRows.value.some(row => row.productId === product.id)))
function openAddSku() {
  if (!selectedProducts.value.length) return
  addSkuProductId.value = selectedProducts.value.length === 1 ? selectedProducts.value[0]!.id : ''
  error.value = ''; showAddSku.value = true
}
const historyError = ref('')
const detailVariant = computed(() => detailRow.value?.variant)
function hasColumn(key: ColumnKey) { return visibleColumns.value.includes(key) && (key !== 'supplier' || session.canAccess(['suppliers.read'])) }
function labelStatus(status: string) { return status === 'ACTIVE' ? '启用' : status === 'DRAFT' ? '待定' : '停用' }
function dimensionLabel(variant: ProductVariantDto) {
  const item = [variant.itemLengthCm, variant.itemWidthCm, variant.itemHeightCm]
  const pack = [variant.packageLengthCm, variant.packageWidthCm, variant.packageHeightCm]
  if (item.every(value => value != null)) return `产品 ${item.join('×')} cm`
  if (pack.every(value => value != null)) return `包装 ${pack.join('×')} cm`
  return variant.itemDimensions || variant.packageDimensions || '—'
}
function toInput(product: ProductDetailDto): ProductInput {
  return { imageAssetId: product.imageAssetId, productCode: product.productCode, productName: product.productName, productType: product.productType, description: product.description, status: product.status, version: product.version, variants: product.variants.map(variant => ({ id: variant.id, customs: variant.customs, skuCode: variant.skuCode, variantName: variant.variantName, itemDimensions: variant.itemDimensions, packageDimensions: variant.packageDimensions, itemLengthCm: variant.itemLengthCm, itemWidthCm: variant.itemWidthCm, itemHeightCm: variant.itemHeightCm, packageLengthCm: variant.packageLengthCm, packageWidthCm: variant.packageWidthCm, packageHeightCm: variant.packageHeightCm, packageWeightGrams: variant.packageWeightGrams, status: variant.status, imageAssetId: variant.imageAssetId, imageUrl: variant.imageUrl, version: variant.version, suppliers: structuredClone(toRaw(variant.suppliers)), materials: structuredClone(toRaw(variant.materials)) })) }
}
function apiMessage(exception: unknown, fallback: string) { return exception instanceof ApiError || exception instanceof Error ? exception.message : fallback }
let loadSequence = 0
const displayedColumns = computed(() => visibleColumns.value.filter(key => hasColumn(key)))
const productTypes = computed(() => [...new Set(products.value.map(product => product.productType).filter((value): value is string => Boolean(value)))].sort())
const groups = computed(() => {
  const f = appliedFilters.value
  const needles = f.q.trim().toLocaleUpperCase().split(/\r?\n/).filter(Boolean)
  const matches = (product: ProductDetailDto, variant?: ProductVariantDto) => {
    if (f.productType && product.productType !== f.productType) return false
    if (f.stock && f.stock !== 'UNINITIALIZED') return false
    if (f.productStatus && product.status !== f.productStatus) return false
    if (f.status && variant?.status !== f.status) return false
    if (f.hasImage && Boolean(variant?.imageAssetId || variant?.imageUrl) !== (f.hasImage === 'true')) return false
    if (f.hasSupplier && Boolean(variant?.suppliers.length) !== (f.hasSupplier === 'true')) return false
    if (f.hasMaterial && Boolean(variant?.materials.length) !== (f.hasMaterial === 'true')) return false
    const value = ({ KEYWORD: [product.productCode, product.productName, variant?.skuCode, variant?.variantName, variant?.specification, variant?.itemDimensions, variant?.packageDimensions, variant ? dimensionLabel(variant) : '', [variant?.packageLengthCm, variant?.packageWidthCm, variant?.packageHeightCm].filter(value => value != null).join('×')].filter(Boolean).join(' '), SKU: variant?.skuCode, NAME: variant?.variantName, PRODUCT_CODE: product.productCode, PRODUCT_NAME: product.productName } as Record<string, string | undefined>)[f.field]?.toLocaleUpperCase() ?? ''
    return !needles.length || needles.some(needle => f.exact ? value === needle : value.includes(needle))
  }
  return products.value.map(product => ({
    product,
    children: product.variants.filter(variant => matches(product, variant)).map(variant => ({
      productId: product.id, productCode: product.productCode, productName: product.productName,
      productType: product.productType, productStatus: product.status, variant,
      businessAvailable: product.status === 'ACTIVE' && variant.status === 'ACTIVE', updatedAt: product.updatedAt,
    } as SkuListItemDto)),
  })).filter(group => group.children.length || (!group.product.variants.length && matches(group.product)))
    .sort((a, b) => {
      const first = f.sort === 'sku' ? a.product.productCode : a.product.updatedAt ?? ''
      const second = f.sort === 'sku' ? b.product.productCode : b.product.updatedAt ?? ''
      return first.localeCompare(second) * (f.direction === 'asc' ? 1 : -1)
    })
})
const pageGroups = computed(() => groups.value.slice(page.value * size.value, (page.value + 1) * size.value))
const pageRows = computed(() => pageGroups.value.flatMap(group => group.children))
const matchingSkuCount = computed(() => groups.value.reduce((sum, group) => sum + group.children.length, 0))
function toggleParent(id: string) { expanded.value = expanded.value.includes(id) ? expanded.value.filter(value => value !== id) : [...expanded.value, id] }
function toggleGroup(children: SkuListItemDto[]) {
  const ids = children.flatMap(row => row.variant.id ? [row.variant.id] : [])
  selected.value = ids.every(id => selected.value.includes(id)) ? selected.value.filter(id => !ids.includes(id)) : [...new Set([...selected.value, ...ids])]
}
async function editParent(product: ProductDetailDto) {
  error.value = ''; saving.value = true
  try { editProduct.value = await domainApi.product(product.id); focusVariantId.value = undefined; showEdit.value = true }
  catch (exception) { error.value = apiMessage(exception, '产品加载失败。') }
  finally { saving.value = false }
}
async function load() {
  const sequence = ++loadSequence
  loading.value = true; error.value = ''
  try {
    const result = await domainApi.products()
    if (sequence !== loadSequence) return
    products.value = result.items
    appliedFilters.value = { ...filters }
    total.value = groups.value.length
    page.value = Math.min(page.value, totalPages.value - 1)
    rows.value = groups.value.flatMap(group => group.children)
    selected.value = selected.value.filter(id => rows.value.some(row => row.variant.id === id))
    if (filters.q || filters.status || filters.hasImage || filters.hasSupplier || filters.hasMaterial) expanded.value = groups.value.map(group => group.product.id)
  } catch (exception) { if (sequence === loadSequence) error.value = apiMessage(exception, '产品管理 数据加载失败。') }
  finally { if (sequence === loadSequence) loading.value = false }
}
function applyFilters() { appliedFilters.value = { ...filters }; total.value = groups.value.length; page.value = Math.min(page.value, totalPages.value - 1); rows.value = groups.value.flatMap(group => group.children); if (filters.q) expanded.value = groups.value.map(group => group.product.id) }
function search() { page.value = 0; selected.value = []; applyFilters() }
watch(() => [filters.q, filters.productType, filters.productStatus, filters.stock], search)
function reset() { Object.assign(filters, { field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' }); page.value = 0; selected.value = []; expanded.value = []; void load() }
function applyBatchSearch() { const unique = [...new Set(batchSearchText.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean))]; if (unique.length > 100) { error.value = '批量搜索最多输入 100 个 SKU 编码。'; return } filters.field = 'SKU'; filters.exact = true; filters.q = unique.join('\n'); showBatchSearch.value = false; search() }
function toggleSort(field: 'sku' | 'updatedAt') { if (filters.sort === field) filters.direction = filters.direction === 'asc' ? 'desc' : 'asc'; else { filters.sort = field; filters.direction = field === 'sku' ? 'asc' : 'desc' } page.value = 0; void load() }
function toggleAll() { selected.value = allSelected.value ? [] : pageRows.value.map(row => row.variant.id).filter((id): id is string => Boolean(id)) }
function toggle(id?: string) { if (!id) return; selected.value = selected.value.includes(id) ? selected.value.filter(value => value !== id) : [...selected.value, id] }
function openColumns() { error.value = ''; columnDraft.value = [...visibleColumns.value]; showColumns.value = true }
function closeColumns() { columnDraft.value = [...visibleColumns.value]; showColumns.value = false }
function persistColumns() { if (!columnDraft.value.includes('sku')) columnDraft.value.unshift('sku'); visibleColumns.value = [...columnDraft.value]; try { localStorage.setItem(storageKey.value, JSON.stringify(visibleColumns.value)) } catch { success.value = '列设置已应用，浏览器未允许持久保存。' } showColumns.value = false }
function moveColumn(index: number, direction: number) { const next = index + direction; if (next < 0 || next >= columnDraft.value.length) return; const copy = [...columnDraft.value]; [copy[index], copy[next]] = [copy[next]!, copy[index]!]; columnDraft.value = copy }
async function openDetail(row: SkuListItemDto) { detailRow.value = row; detailTab.value = 'basic'; detailHistory.value = []; historyError.value = ''; showDetail.value = true; try { detailHistory.value = await domainApi.productHistory(row.productId) } catch { historyError.value = '操作日志加载失败，请重新打开详情重试。' } }
async function openEdit(row: SkuListItemDto) {
  saving.value = true; error.value = ''
  try { editProduct.value = await domainApi.product(row.productId); focusVariantId.value = row.variant.id; showEdit.value = true }
  catch (exception) { error.value = apiMessage(exception, 'SKU 加载失败。') } finally { saving.value = false }
}
async function copySku(row: SkuListItemDto) {
  saving.value = true; error.value = ''
  try {
    const product = await domainApi.product(row.productId); const source = product.variants.find(item => item.id === row.variant.id) ?? row.variant
    const duplicate = structuredClone(toRaw(source))
    delete duplicate.id; delete duplicate.version; delete duplicate.customs
    Object.assign(duplicate, { skuCode: '', variantName: `${source.variantName} 副本`, status: 'DRAFT' as const, suppliers: [], materials: [] })
    editProduct.value = { ...product, variants: [...product.variants, duplicate] }; focusVariantId.value = '__NEW__'; showEdit.value = true
  } catch (exception) { error.value = apiMessage(exception, '复制 SKU 失败。') } finally { saving.value = false }
}
async function addSkuToProduct() {
  if (!addSkuProductId.value) { error.value = '请选择所属产品。'; return }
  saving.value = true; error.value = ''
  try {
    const product = await domainApi.product(addSkuProductId.value)
    editProduct.value = { ...product, variants: [...product.variants, { skuCode: '', variantName: '', status: 'DRAFT', attributes: [], suppliers: [], materials: [] }] }
    focusVariantId.value = '__NEW__'; showAddSku.value = false; showEdit.value = true
  } catch (exception) { error.value = apiMessage(exception, '产品加载失败。') } finally { saving.value = false }
}
async function create(input: ProductInput) {
  if (saving.value) return
  saving.value = true; error.value = ''
  try { await domainApi.createProduct(input); showCreate.value = false; success.value = '产品及 SKU 已创建。'; await load() }
  catch (exception) { error.value = apiMessage(exception, '产品创建失败，请检查输入。') } finally { saving.value = false }
}
async function update(input: ProductInput) {
  if (saving.value) return
  if (!editProduct.value) return; saving.value = true; error.value = ''
  try { await domainApi.updateProduct(editProduct.value.id, input); showEdit.value = false; success.value = 'SKU 资料已保存。'; await load() }
  catch (exception) { error.value = apiMessage(exception, 'SKU 保存失败。') } finally { saving.value = false }
}
function resetBatch() { Object.assign(batch, { statusSelected: false, status: 'ACTIVE', itemDimensionsSelected: false, itemDimensionsMode: 'SET', itemLengthCm: undefined, itemWidthCm: undefined, itemHeightCm: undefined, packageDimensionsSelected: false, packageDimensionsMode: 'SET', packageLengthCm: undefined, packageWidthCm: undefined, packageHeightCm: undefined, packageWeightSelected: false, packageWeightMode: 'SET', packageWeightGrams: undefined }) }
async function submitBatch() {
  if (saving.value) return
  if (selected.value.length > 100) { error.value = '一次最多批量修改 100 个 SKU，请减少勾选。'; return }
  if (!selectedRows.value.length || !Object.keys(batch).some(key => key.endsWith('Selected') && batch[key as keyof typeof batch] === true)) { error.value = '请选择 SKU，并至少勾选一个要批量修改的字段。'; return }
  if (batch.itemDimensionsSelected && batch.itemDimensionsMode === 'SET' && [batch.itemLengthCm, batch.itemWidthCm, batch.itemHeightCm].some(value => value == null)) { error.value = '产品规格选择填入值时，长、宽、高必须完整。'; return }
  if (batch.packageDimensionsSelected && batch.packageDimensionsMode === 'SET' && [batch.packageLengthCm, batch.packageWidthCm, batch.packageHeightCm].some(value => value == null)) { error.value = '包装规格选择填入值时，长、宽、高必须完整。'; return }
  if (batch.packageWeightSelected && batch.packageWeightMode === 'SET' && batch.packageWeightGrams == null) { error.value = '包装重量选择填入值时必须填写数值。'; return }
  const versions = new Map(products.value.map(product => [product.id, product.version])); const items: BatchVariantPatchInput[] = selectedRows.value.map(row => ({ productId: row.productId, variantId: row.variant.id!, productVersion: versions.get(row.productId) ?? 0, variantVersion: row.variant.version ?? 0, statusSelected: batch.statusSelected, status: batch.status, itemDimensionsSelected: batch.itemDimensionsSelected, itemLengthCm: batch.itemDimensionsMode === 'CLEAR' ? undefined : batch.itemLengthCm, itemWidthCm: batch.itemDimensionsMode === 'CLEAR' ? undefined : batch.itemWidthCm, itemHeightCm: batch.itemDimensionsMode === 'CLEAR' ? undefined : batch.itemHeightCm, packageDimensionsSelected: batch.packageDimensionsSelected, packageLengthCm: batch.packageDimensionsMode === 'CLEAR' ? undefined : batch.packageLengthCm, packageWidthCm: batch.packageDimensionsMode === 'CLEAR' ? undefined : batch.packageWidthCm, packageHeightCm: batch.packageDimensionsMode === 'CLEAR' ? undefined : batch.packageHeightCm, packageWeightSelected: batch.packageWeightSelected, packageWeightGrams: batch.packageWeightMode === 'CLEAR' ? undefined : batch.packageWeightGrams }))
  saving.value = true; error.value = ''
  try { await domainApi.batchUpdateSkus(items); showBatch.value = false; selected.value = []; resetBatch(); success.value = `已批量更新 ${items.length} 个 SKU。`; await load() }
  catch (exception) { error.value = apiMessage(exception, '批量修改失败，未提交任何变更。') } finally { saving.value = false }
}
async function exportRows() {
  if (saving.value) return
  const filters = appliedFilters.value
  saving.value = true; error.value = ''
  try {
    await domainApi.exportSkus({ productType: filters.productType || undefined, field: filters.field, q: filters.q.trim(), exact: filters.exact, status: filters.status as VariantStatus || undefined, productStatus: filters.productStatus as 'DRAFT' | 'ACTIVE' | 'DISABLED' || undefined, hasImage: filters.hasImage === '' ? undefined : filters.hasImage === 'true', hasSupplier: filters.hasSupplier === '' ? undefined : filters.hasSupplier === 'true', hasMaterial: filters.hasMaterial === '' ? undefined : filters.hasMaterial === 'true', variantIds: exportScope.value === 'SELECTED' ? selected.value : undefined })
    showExport.value = false; success.value = 'SKU 导出文件已生成。'
  } catch (exception) { error.value = apiMessage(exception, 'SKU 导出失败。') } finally { saving.value = false }
}
function changePage(next: number) { page.value = next; selected.value = [] }
onMounted(load)
</script>

<template>
  <section class="page-stack product-sku-page">
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <form
      class="panel filter-panel material-filters product-management-filters"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="filters.q"
        type="search"
        placeholder="SKU、品名或规格模糊搜索"
        @input="filters.field = 'KEYWORD'; filters.exact = false"
      ></label>
      <label>产品类型<select v-model="filters.productType"><option value="">全部类型</option><option
        v-for="type in productTypes"
        :key="type"
        :value="type"
      >{{ type }}</option></select></label>
      <label>产品状态<select v-model="filters.productStatus"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
      <label>库存状态<select v-model="filters.stock"><option value="">全部库存</option><option value="UNINITIALIZED">未建账</option></select></label>
      <button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button>
    </form>

    <section
      class="panel material-toolbar sku-toolbar"
      aria-label="产品 SKU 操作"
    >
      <div>
        <button
          v-if="writable"
          type="button"
          @click="showCreate = true"
        >
          新增产品
        </button><button
          v-if="writable"
          class="secondary-button"
          type="button"
          :disabled="!selected.length || loading || saving"
          @click="openAddSku"
        >
          添加SKU
        </button><RouterLink
          class="toolbar-link"
          to="/products/imports"
        >
          导入
        </RouterLink><button
          class="secondary-button"
          type="button"
          :disabled="!rows.length"
          @click="showExport = true"
        >
          导出
        </button>
      </div>
      <div>
        <button
          class="secondary-button"
          type="button"
          @click="openColumns"
        >
          列设置
        </button><button
          class="secondary-button"
          type="button"
          aria-label="刷新"
          @click="load"
        >
          刷新
        </button>
      </div>
    </section>
    <CatalogDeleteDialog
      v-if="deleteTarget"
      :title="deleteTarget.variant ? '删除 SKU' : '删除父体'"
      :name="deleteTarget.variant ? deleteTarget.variant.skuCode + ' · ' + deleteTarget.variant.variantName : deleteTarget.product.productCode + ' · ' + deleteTarget.product.productName"
      notice="采用逻辑删除，保留图片、资料和历史单据；已有采购及发货仍可继续处理，不再用于新建业务。原编码保留，删除后暂不支持页面恢复。"
      :description="deleteTarget.variant ? '将从列表隐藏此 SKU 及对应客户商品，不影响父体或其他 SKU。' : '将从列表隐藏此父体、全部 SKU 及对应客户商品。'"
      :busy="deleting"
      :error="deleteError"
      @cancel="!deleting && (deleteTarget = undefined)"
      @confirm="confirmDelete"
    />
    <SuccessToast :message="success" />
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
      v-else-if="!groups.length"
      class="panel empty-state"
    >
      <h2>暂无匹配的产品</h2><p
        v-if="error"
        class="data-state data-state--error"
        role="alert"
      >
        {{ error }}
      </p><p>调整筛选条件，或点击“新增产品”建立父体及子体 SKU。</p>
    </article>
    <article
      v-else
      class="panel table-panel sku-table-panel"
    >
      <div class="table-scroll">
        <TableViewport>
          <table class="data-table material-table sku-master-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="全选本页"
                    :checked="allSelected"
                    @change="toggleAll"
                  >
                </th>
                <th
                  v-for="key in displayedColumns"
                  :key="key"
                >
                  <button
                    v-if="key === 'sku' || key === 'updated'"
                    class="sort-button"
                    type="button"
                    @click="toggleSort(key === 'sku' ? 'sku' : 'updatedAt')"
                  >
                    {{ key === 'sku' ? '产品编码 / SKU' : '更新时间' }} {{ filters.sort === (key === 'sku' ? 'sku' : 'updatedAt') ? (filters.direction === 'asc' ? '↑' : '↓') : '' }}
                  </button><template v-else>
                    {{ columns.find(column => column.key === key)?.label }}
                  </template>
                </th><th class="sticky-action">
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="group in pageGroups"
                :key="group.product.id"
              >
                <tr
                  class="product-parent-row"
                  @click="toggleParent(group.product.id)"
                >
                  <td @click.stop>
                    <input
                      type="checkbox"
                      :aria-label="`选择父体 ${group.product.productCode}`"
                      :checked="group.children.length > 0 && group.children.every(row => selected.includes(row.variant.id ?? ''))"
                      :disabled="!group.children.length"
                      @change="toggleGroup(group.children)"
                    >
                  </td>
                  <td
                    v-for="key in displayedColumns"
                    :key="key"
                    :class="{ 'hierarchy-cell': key === 'sku' }"
                  >
                    <button
                      v-if="key === 'sku'"
                      type="button"
                      class="parent-toggle"
                      :aria-expanded="expanded.includes(group.product.id)"
                      @click.stop="toggleParent(group.product.id)"
                    >
                      {{ expanded.includes(group.product.id) ? '▾' : '▸' }} {{ group.product.productCode }}<small>父体 · {{ group.children.length }} 个子体</small>
                    </button>
                    <template v-else-if="key === 'image'">
                      <button
                        v-if="group.product.imageAssetId"
                        type="button"
                        class="material-image-button"
                        :aria-label="`查看父体 ${group.product.productName} 图片`"
                        @click.stop="imagePreview = { imageAssetId: group.product.imageAssetId, variantName: group.product.productName }"
                      >
                        <AuthenticatedCatalogImage
                          :image-id="group.product.imageAssetId"
                          :alt="group.product.productName"
                        />
                      </button>
                      <span
                        v-else
                        class="product-thumbnail"
                      >父</span>
                    </template>
                    <strong v-else-if="key === 'name'">{{ group.product.productName }}</strong>
                    <span v-else-if="key === 'product'">{{ group.product.productType || '—' }}</span>
                    <span
                      v-else-if="key === 'status'"
                      :class="['status-badge', `status-badge--${group.product.status.toLowerCase()}`]"
                    >{{ labelStatus(group.product.status) }}</span>
                    <span v-else-if="key === 'updated'">{{ group.product.updatedAt ? new Date(group.product.updatedAt).toLocaleString('zh-CN') : '—' }}</span>
                    <span
                      v-else-if="key === 'stock'"
                      class="stock-uninitialized"
                    >未建账</span><span v-else>—</span>
                  </td>
                  <td
                    class="sticky-action"
                    @click.stop
                  >
                    <div class="table-actions">
                      <RouterLink
                        :to="`/products/${group.product.id}`"
                        class="row-detail-link"
                      >
                        详情
                      </RouterLink>
                      <RowMoreActions
                        v-if="writable"
                        :label="`父体 ${group.product.productCode} 更多操作`"
                      >
                        <button
                          v-if="writable"
                          type="button"
                          :disabled="saving"
                          @click="editParent(group.product)"
                        >
                          编辑父体
                        </button>
                        <button
                          v-if="writable"
                          type="button"
                          class="secondary-button"
                          :disabled="saving || deleting"
                          @click="requestDelete(group.product)"
                        >
                          删除父体
                        </button>
                        <button
                          v-if="writable"
                          type="button"
                          class="secondary-button"
                          :disabled="saving"
                          @click="addSkuProductId = group.product.id; addSkuToProduct()"
                        >
                          添加子体
                        </button>
                      </RowMoreActions>
                    </div>
                  </td>
                </tr>
                <template v-if="expanded.includes(group.product.id)">
                  <tr
                    v-for="row in group.children"
                    :key="row.variant.id ?? row.variant.skuCode"
                    class="product-child-row"
                  >
                    <td>
                      <input
                        type="checkbox"
                        :aria-label="`选择 ${row.variant.skuCode}`"
                        :checked="selected.includes(row.variant.id ?? '')"
                        @change="toggle(row.variant.id)"
                      >
                    </td>
                    <td
                      v-for="key in displayedColumns"
                      :key="key"
                      :class="{ 'hierarchy-cell': key === 'sku' }"
                    >
                      <template v-if="key === 'image'">
                        <button
                          v-if="row.variant.imageAssetId || row.variant.imageUrl"
                          type="button"
                          class="material-image-button"
                          :aria-label="`查看${row.variant.variantName}大图`"
                          @click="imagePreview = row.variant"
                        >
                          <AuthenticatedCatalogImage
                            v-if="row.variant.imageAssetId"
                            :image-id="row.variant.imageAssetId"
                            :alt="`${row.variant.variantName}主图`"
                          />
                          <img
                            v-else-if="row.variant.imageUrl"
                            class="product-thumbnail"
                            :src="row.variant.imageUrl"
                            :alt="`${row.variant.variantName}主图`"
                          >
                        </button>
                        <span
                          v-else
                          class="product-thumbnail"
                        >图</span>
                      </template>
                      <button
                        v-else-if="key === 'sku'"
                        class="sku-code-button"
                        type="button"
                        @click="openDetail(row)"
                      >
                        ↳ {{ row.variant.skuCode }}
                      </button>
                      <span
                        v-else-if="key === 'stock'"
                        class="stock-uninitialized"
                      >未建账</span><template v-else-if="key === 'name'">
                        {{ row.variant.variantName }}
                      </template>
                      <template v-else-if="key === 'product'">
                        {{ row.productType || '—' }}
                      </template>
                      <template v-else-if="key === 'status'">
                        <span :class="['status-badge', `status-badge--${row.variant.status.toLowerCase()}`]">{{ labelStatus(row.variant.status) }}</span><small v-if="!row.businessAvailable">业务不可用</small>
                      </template>
                      <template v-else-if="key === 'dimensions'">
                        {{ dimensionLabel(row.variant) }}
                      </template>
                      <template v-else-if="key === 'weight'">
                        {{ row.variant.packageWeightGrams != null ? `${row.variant.packageWeightGrams} g` : '—' }}
                      </template>
                      <template v-else-if="key === 'supplier'">
                        {{ row.variant.suppliers.length }} 个
                      </template>
                      <template v-else-if="key === 'material'">
                        {{ row.variant.materials.length }} 个
                      </template>
                      <template v-else-if="key === 'updated'">
                        {{ row.updatedAt ? new Date(row.updatedAt).toLocaleString('zh-CN') : '—' }}
                      </template>
                    </td>
                    <td class="sticky-action">
                      <div class="table-actions">
                        <button
                          type="button"
                          class="secondary-button"
                          @click="openDetail(row)"
                        >
                          详情
                        </button>
                        <RowMoreActions
                          v-if="writable"
                          :label="`SKU ${row.variant.skuCode} 更多操作`"
                        >
                          <button
                            v-if="writable"
                            type="button"
                            :disabled="saving"
                            @click="openEdit(row)"
                          >
                            编辑
                          </button>
                          <button
                            v-if="writable"
                            type="button"
                            class="secondary-button"
                            :disabled="saving"
                            @click="copySku(row)"
                          >
                            复制
                          </button>
                          <button
                            v-if="writable"
                            type="button"
                            class="secondary-button"
                            :disabled="saving || deleting"
                            @click="requestDelete(group.product, row.variant)"
                          >
                            删除 SKU
                          </button>
                        </RowMoreActions>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!group.children.length">
                    <td :colspan="displayedColumns.length + 2">
                      暂无子体，可点击“添加子体”创建。
                    </td>
                  </tr>
                </template>
              </template>
            </tbody>
          </table>
        </TableViewport>
      </div>
      <ListPagination
        v-model:page-size="size"
        :page="page + 1"
        :total="total"
        :disabled="loading"
        @update:page="changePage($event - 1)"
      >
        <template #summary>
          已选 {{ selected.length }} 个 SKU，共 {{ total }} 个父体 / {{ matchingSkuCount }} 个子体
        </template>
      </ListPagination>
    </article>

    <div
      v-if="showAddSku"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showAddSku = false"
      @keydown.esc="showAddSku = false"
    >
      <form
        class="panel operation-dialog__card operation-dialog__card--compact batch-dialog"
        @submit.prevent="addSkuToProduct"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showAddSku = false"
        >
          ×
        </button><h2>添加SKU</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p>
        <label>所属产品<select
          v-model="addSkuProductId"
          required
        ><option value="">请选择产品</option><option
          v-for="product in selectedProducts"
          :key="product.id"
          :value="product.id"
        >{{ product.productCode }} · {{ product.productName }}</option></select></label>
        <footer class="dialog-footer">
          <button
            class="secondary-action"
            type="button"
            @click="showAddSku = false"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving"
          >
            下一步
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="showBatchSearch"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showBatchSearch = false"
      @keydown.esc="showBatchSearch = false"
    >
      <form
        class="panel operation-dialog__card operation-dialog__card--compact batch-dialog"
        @submit.prevent="applyBatchSearch"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showBatchSearch = false"
        >
          ×
        </button>
        <h2>批量搜索 SKU</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>每行一个 SKU 编码，自动去除空行和重复项，最多 100 个。</p>
        <textarea
          v-model="batchSearchText"
          rows="10"
          placeholder="SKU-001&#10;SKU-002"
          autofocus
        />
        <footer class="dialog-footer">
          <span>{{ [...new Set(batchSearchText.split(/\r?\n/).map(value => value.trim()).filter(Boolean))].length }} 个有效编码</span><button
            class="secondary-action"
            type="button"
            @click="showBatchSearch = false"
          >
            取消
          </button><button type="submit">
            应用并搜索
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="showCreate"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showCreate = false"
      @keydown.esc="showCreate = false"
    >
      <section class="panel operation-dialog__card product-create-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showCreate = false"
        >
          ×
        </button><h2>新增产品</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>创建产品主档，并维护一个或多个内部 SKU。</p><ProductForm
          class="product-create-form"
          :busy="saving"
          submit-label="创建产品"
          show-cancel
          @cancel="showCreate = false"
          @submit="create"
        />
      </section>
    </div>
    <div
      v-if="showEdit && editProduct"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showEdit = false"
      @keydown.esc="showEdit = false"
    >
      <section class="panel operation-dialog__card product-create-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showEdit = false"
        >
          ×
        </button><h2>编辑 SKU</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>{{ focusVariantId === '__NEW__' ? '填写新子体的唯一 SKU 编码。' : focusVariantId ? '编辑当前子体，保存时保留其他子体。' : '维护父体资料及其全部子体。' }}</p><ProductForm
          :key="`${editProduct.id}-${focusVariantId ?? 'copy'}`"
          class="product-create-form"
          :initial="toInput(editProduct)"
          :focus-variant-id="focusVariantId"
          :busy="saving"
          submit-label="保存"
          show-cancel
          @cancel="showEdit = false"
          @submit="update"
        />
      </section>
    </div>
    <div
      v-if="showDetail && detailRow"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showDetail = false"
      @keydown.esc="showDetail = false"
    >
      <section class="panel operation-dialog__card sku-detail-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showDetail = false"
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
            <h2>{{ detailVariant?.variantName }}</h2><p
              v-if="error"
              class="data-state data-state--error"
              role="alert"
            >
              {{ error }}
            </p><p>{{ detailVariant?.skuCode }} · {{ detailRow.productName }}（{{ detailRow.productCode }}）</p>
          </div>
        </header><nav class="product-editor-tabs">
          <button
            v-for="tab in ([['basic','基础信息'],['purchase','采购信息'],['specs','规格信息'],['materials','关联辅料'],['image','图片管理'],['manual','说明书管理'],['label','商品标签管理'],['history','操作日志']] as const)"
            :key="tab[0]"
            type="button"
            :class="{ active: detailTab === tab[0] }"
            @click="detailTab = tab[0]"
          >
            {{ tab[1] }}
          </button>
        </nav>
        <dl
          v-if="detailTab === 'basic'"
          class="sku-detail-grid"
        >
          <dt>SKU</dt><dd>{{ detailVariant?.skuCode }}</dd><dt>品名</dt><dd>{{ detailVariant?.variantName }}</dd><dt>SKU 状态</dt><dd>{{ labelStatus(detailVariant?.status ?? '') }}</dd><dt>产品状态</dt><dd>{{ labelStatus(detailRow.productStatus) }}</dd>
        </dl>
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
        <div
          v-else-if="detailTab === 'image'"
          class="sku-detail-image"
        >
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
        <SkuDocumentsPanel
          v-else-if="(detailTab === 'manual' || detailTab === 'label') && detailVariant?.id"
          :key="`${detailVariant.id}-${detailTab}`"
          :product-id="detailRow.productId"
          :variant-id="detailVariant.id"
          :kind="detailTab === 'manual' ? 'MANUAL' : 'LABEL'"
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
                  <td>{{ event.occurredAt }}</td><td>{{ event.actorDisplayName || event.actorLoginName }}</td><td>{{ event.action }}</td><td>{{ event.detail || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <footer class="dialog-footer">
          <RouterLink :to="`/products/${detailRow.productId}`">
            查看产品完整资料
          </RouterLink><button
            v-if="writable"
            type="button"
            @click="showDetail = false; openEdit(detailRow)"
          >
            编辑 SKU
          </button>
        </footer>
      </section>
    </div>
    <div
      v-if="showColumns"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="closeColumns"
      @keydown.esc="closeColumns"
    >
      <section class="panel operation-dialog__card operation-dialog__card--compact column-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="closeColumns"
        >
          ×
        </button><h2>列设置</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><div class="column-picker">
          <div>
            <h3>可选列</h3><label class="column-search">字段搜索<input
              v-model="columnSearch"
              type="search"
              placeholder="搜索字段"
            ></label><label
              v-for="column in filteredColumns"
              :key="column.key"
            ><input
              v-model="columnDraft"
              type="checkbox"
              :value="column.key"
              :disabled="column.key === 'sku'"
            >{{ column.label }}</label>
          </div><ol>
            <li
              v-for="(key, index) in columnDraft"
              :key="key"
            >
              <span>{{ columns.find(column => column.key === key)?.label }}</span><button
                type="button"
                :disabled="index === 0"
                @click="moveColumn(index, -1)"
              >
                ↑
              </button><button
                type="button"
                :disabled="index + 1 === columnDraft.length"
                @click="moveColumn(index, 1)"
              >
                ↓
              </button>
            </li>
          </ol>
        </div><footer class="dialog-footer">
          <button
            class="secondary-action"
            type="button"
            @click="columnDraft = [...defaultColumns]"
          >
            恢复默认
          </button><button
            type="button"
            @click="persistColumns"
          >
            保存
          </button>
        </footer>
      </section>
    </div>
    <div
      v-if="showExport"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showExport = false"
      @keydown.esc="showExport = false"
    >
      <form
        class="panel operation-dialog__card operation-dialog__card--compact batch-dialog"
        @submit.prevent="exportRows"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showExport = false"
        >
          ×
        </button><h2>导出产品 SKU</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>导出当前可见业务字段为 XLSX；不包含图片原始地址、采购链接或操作列，最多 10000 条。</p>
        <label class="batch-field"><input
          v-model="exportScope"
          type="radio"
          value="FILTERED"
        >筛选全部（预计 {{ matchingSkuCount }} 条）</label>
        <label class="batch-field"><input
          v-model="exportScope"
          type="radio"
          value="SELECTED"
          :disabled="!selected.length"
        >已选记录（{{ selected.length }} 条）</label>
        <footer class="dialog-footer">
          <button
            class="secondary-action"
            type="button"
            @click="showExport = false"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving || (exportScope === 'FILTERED' && matchingSkuCount > 10000) || (exportScope === 'SELECTED' && !selected.length)"
          >
            {{ saving ? '生成中…' : '确认导出' }}
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="showBatch"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      @click.self="showBatch = false"
      @keydown.esc="showBatch = false"
    >
      <form
        class="panel operation-dialog__card operation-dialog__card--compact batch-dialog"
        @submit.prevent="submitBatch"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          @click="showBatch = false"
        >
          ×
        </button><h2>批量修改 {{ selected.length }} 个 SKU</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>只有已勾选的字段会被修改；选填字段须明确选择“填入值”或“清空”。整批校验通过后一次提交。</p><label class="batch-field"><input
          v-model="batch.statusSelected"
          type="checkbox"
        >SKU 状态<select
          v-model="batch.status"
          :disabled="!batch.statusSelected"
        ><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label><fieldset>
          <legend>
            <label><input
              v-model="batch.itemDimensionsSelected"
              type="checkbox"
            >产品规格（cm）</label>
          </legend><select
            v-model="batch.itemDimensionsMode"
            :disabled="!batch.itemDimensionsSelected"
            aria-label="产品规格修改方式"
          >
            <option value="SET">
              填入值
            </option><option value="CLEAR">
              清空
            </option>
          </select><div>
            <input
              v-model.number="batch.itemLengthCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="长"
              :disabled="!batch.itemDimensionsSelected || batch.itemDimensionsMode === 'CLEAR'"
            ><input
              v-model.number="batch.itemWidthCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="宽"
              :disabled="!batch.itemDimensionsSelected || batch.itemDimensionsMode === 'CLEAR'"
            ><input
              v-model.number="batch.itemHeightCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="高"
              :disabled="!batch.itemDimensionsSelected || batch.itemDimensionsMode === 'CLEAR'"
            >
          </div>
        </fieldset><fieldset>
          <legend>
            <label><input
              v-model="batch.packageDimensionsSelected"
              type="checkbox"
            >包装规格（cm）</label>
          </legend><select
            v-model="batch.packageDimensionsMode"
            :disabled="!batch.packageDimensionsSelected"
            aria-label="包装规格修改方式"
          >
            <option value="SET">
              填入值
            </option><option value="CLEAR">
              清空
            </option>
          </select><div>
            <input
              v-model.number="batch.packageLengthCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="长"
              :disabled="!batch.packageDimensionsSelected || batch.packageDimensionsMode === 'CLEAR'"
            ><input
              v-model.number="batch.packageWidthCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="宽"
              :disabled="!batch.packageDimensionsSelected || batch.packageDimensionsMode === 'CLEAR'"
            ><input
              v-model.number="batch.packageHeightCm"
              type="number"
              min="0.001"
              step="0.001"
              placeholder="高"
              :disabled="!batch.packageDimensionsSelected || batch.packageDimensionsMode === 'CLEAR'"
            >
          </div>
        </fieldset><label class="batch-field"><input
          v-model="batch.packageWeightSelected"
          type="checkbox"
        >包装重量（克）<select
          v-model="batch.packageWeightMode"
          :disabled="!batch.packageWeightSelected"
          aria-label="包装重量修改方式"
        ><option value="SET">填入值</option><option value="CLEAR">清空</option></select><input
          v-model.number="batch.packageWeightGrams"
          type="number"
          min="0.001"
          step="0.001"
          :disabled="!batch.packageWeightSelected || batch.packageWeightMode === 'CLEAR'"
        ></label><section class="batch-preview">
          <strong>变更预览</strong><p>目标：{{ selectedRows.map(row => row.variant.skuCode).join('、') }}</p><ul>
            <li v-if="batch.statusSelected">
              SKU 状态 → {{ labelStatus(batch.status) }}
            </li><li v-if="batch.itemDimensionsSelected">
              产品规格 → {{ batch.itemDimensionsMode === 'CLEAR' ? '清空' : `${batch.itemLengthCm ?? '—'}×${batch.itemWidthCm ?? '—'}×${batch.itemHeightCm ?? '—'} cm` }}
            </li><li v-if="batch.packageDimensionsSelected">
              包装规格 → {{ batch.packageDimensionsMode === 'CLEAR' ? '清空' : `${batch.packageLengthCm ?? '—'}×${batch.packageWidthCm ?? '—'}×${batch.packageHeightCm ?? '—'} cm` }}
            </li><li v-if="batch.packageWeightSelected">
              包装重量 → {{ batch.packageWeightMode === 'CLEAR' ? '清空' : `${batch.packageWeightGrams ?? '—'} g` }}
            </li>
          </ul>
        </section><footer class="dialog-footer">
          <button
            class="secondary-action"
            type="button"
            @click="showBatch = false"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving"
          >
            {{ saving ? '保存中…' : '确认修改' }}
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="imagePreview"
      class="image-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="`${imagePreview.variantName}图片预览`"
      tabindex="-1"
      @click.self="imagePreview = null"
      @keydown.esc="imagePreview = null"
    >
      <div class="material-preview">
        <button
          type="button"
          aria-label="关闭图片预览"
          @click="imagePreview = null"
        >
          ×
        </button>
        <AuthenticatedCatalogImage
          v-if="imagePreview.imageAssetId"
          :image-id="imagePreview.imageAssetId"
          variant="original"
          :alt="imagePreview.variantName"
        />
        <img
          v-else
          class="material-preview__image"
          :src="imagePreview.imageUrl"
          :alt="imagePreview.variantName"
          referrerpolicy="no-referrer"
        >
        <p>{{ imagePreview.variantName }}</p>
      </div>
    </div>
  </section>
</template>
