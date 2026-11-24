<script setup lang="ts">
import '@/styles/productManagement.css'
import ProductUiState from '@/components/ProductUiState.vue'
import ProductRestoreDialog from '@/components/ProductRestoreDialog.vue'
import ProductInventoryDialog from '@/components/ProductInventoryDialog.vue'
import { productManagementApi } from '@/api/productManagement'
import ImagePreviewDialog from '@/components/ImagePreviewDialog.vue'
import { beijingDateTime } from '@/utils/dateTime'


import { vSingleSelect } from '@/directives/singleSelect'
import { catalogApi } from '@/api/catalog'
import ProductName from '@/components/ProductName.vue'
import ListPagination from '@/components/ListPagination.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import CatalogDeleteDialog from '@/components/CatalogDeleteDialog.vue'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, onBeforeUnmount, nextTick, reactive, ref, toRaw, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { ApiError } from '@/api/client'
import { domainApi, type BatchVariantPatchInput, type ProductDetailDto, type ProductInput, type ProductVariantDto, type SkuListItemDto, type VariantStatus } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import ParentProductDetailDialog from '@/components/ParentProductDetailDialog.vue'
import SkuDetailDialog from '@/components/SkuDetailDialog.vue'
import ProductForm from '@/components/ProductForm.vue'
import ProductEditDialog from '@/components/ProductEditDialog.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const parentDetailId = ref<string>()
const imageDownloading = ref(false)
const imageDownloadError = ref('')
async function downloadPreviewImage() {
  if (!imagePreview.value || imageDownloading.value) return
  imageDownloading.value = true; imageDownloadError.value = ''
  try { await catalogApi.downloadImage(imagePreview.value) }
  catch (exception) { imageDownloadError.value = exception instanceof Error ? exception.message : '图片下载失败，请重试。' }
  finally { imageDownloading.value = false }
}
const imagePreview = ref<{ imageAssetId?: string | undefined; imageUrl?: string | undefined; variantName: string } | null>(null)
watch(imagePreview, () => { imageDownloadError.value = '' })
type ColumnKey = 'image' | 'sku' | 'status' | 'dimensions' | 'weight' | 'supplier' | 'material' | 'updated' | 'stock'
const columns: Array<{ key: ColumnKey; label: string }> = [
  { key: 'image', label: '图片' }, { key: 'sku', label: '品名' },
  { key: 'status', label: '状态' }, { key: 'dimensions', label: '规格' },
  { key: 'weight', label: '包装重量' }, { key: 'supplier', label: '供应商' }, { key: 'material', label: '辅料' }, { key: 'updated', label: '更新时间' }, { key: 'stock', label: '库存数（件）' },
]
const defaultColumns: ColumnKey[] = ['image', 'sku', 'status', 'stock', 'dimensions', 'weight']
const storageKey = computed(() => `jiugeng.product-management.columns.v2.${session.user.value?.id ?? 'current'}`)
function orderedColumns(keys: ColumnKey[]): ColumnKey[] {
  return [...(keys.includes('image') ? ['image' as const] : []), 'sku', ...keys.filter(key => key !== 'image' && key !== 'sku')]
}
function restoredColumns(): ColumnKey[] {
  try {
    const values = JSON.parse(localStorage.getItem(storageKey.value) ?? '[]') as ColumnKey[]
    const valid = Array.isArray(values) ? [...new Set(values.filter(key => columns.some(column => column.key === key)))] : []
    return valid.length ? orderedColumns(valid) : defaultColumns
  } catch { return defaultColumns }
}

const formal = runtimeConfig.authMode === 'password'
const listHost = ref<HTMLElement>()
const showDeleted = ref(false)
const inventoryTarget = ref<{ id: string; name: string; parentName: string }>()
const stockBalances = ref<Record<string, number>>({})
const matchingVariantIds = ref<string[]>([]), serverSkuCount = ref(0)
const moreFilters = ref(false)
let scrollTop = 0, initialLoad = true, restoringView = true
const focusAfterSave = ref<string>(), focusParentAfterSave = ref<string>()
const stateKey = `jiugeng.products.view.${session.user.value?.id ?? 'current'}`
function readViewState() { try { return JSON.parse(sessionStorage.getItem(stateKey) ?? 'null') } catch { return null } }
const savedView = readViewState()
function captureScroll() { scrollTop = listHost.value?.querySelector('.table-viewport')?.scrollTop ?? scrollTop }
function saveViewState() { captureScroll(); try { sessionStorage.setItem(stateKey, JSON.stringify({ filters: { ...filters }, page: page.value, size: size.value, expanded: expanded.value, scrollTop })) } catch { /* Optional preference storage. */ } }
function skuStock(id?: string) { return formal ? stockBalances.value[id ?? ''] ?? 0 : undefined }
function parentStock(product: ProductDetailDto) { return formal ? product.variants.reduce((sum,v)=>sum+BigInt(skuStock(v.id) ?? 0),0n).toString() : undefined }
function missingFields(variant: ProductVariantDto) {
  return [!variant.imageAssetId && !variant.imageUrl ? '图片' : '', [variant.itemLengthCm,variant.itemWidthCm,variant.itemHeightCm].some(v=>v==null) ? '规格' : '', variant.packageWeightGrams == null ? '重量' : '', !variant.suppliers.length ? '供应商' : ''].filter(Boolean)
}
const rows = ref<SkuListItemDto[]>([]); const products = ref<ProductDetailDto[]>([])
const total = ref(0); const page = ref(0); const size = ref(20); const loading = ref(true); const saving = ref(false)
const error = ref(''); const success = ref(''); const selected = ref<string[]>([])
const expanded = ref<string[]>([])
const deleteTarget = ref<{ product: ProductDetailDto; variant?: ProductVariantDto }>()
const deleteError = ref(''), deleting = ref(false)
const deleteBlockedReason = computed(() => {
  const target = deleteTarget.value
  if (!target || target.variant) return ''
  const count = Math.max(target.product.skuCount ?? 0, target.product.variants.length)
  return count > 0 ? `该父体下还有 ${count} 个子体，请先删除全部子体后再删除父体（停用子体也需删除）。` : ''
})
function requestDelete(product: ProductDetailDto, variant?: ProductVariantDto) {
  deleteError.value = ''; deleteTarget.value = variant ? { product, variant } : { product }
}
async function confirmDelete() {
  if (deleting.value || !deleteTarget.value || deleteBlockedReason.value) return
  const { product, variant } = deleteTarget.value; deleting.value = true; deleteError.value = ''
  try {
    if (variant?.id) await domainApi.deleteSku(product.id, variant.id, product.version, variant.version ?? 0)
    else await domainApi.deleteProduct(product.id, product.version)
    deleteTarget.value = undefined; selected.value = []; page.value = 0
    success.value = variant ? 'SKU 及对应客户商品已逻辑删除，历史单据保留。' : '父体已删除。'
    await load()
  } catch (e) { deleteError.value = apiMessage(e, '删除失败，请重试。') }
  finally { deleting.value = false }
}
const appliedFilters = ref({ field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', missing: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' })
const createForm = ref<InstanceType<typeof ProductForm>>()
function closeCreate() { if (!saving.value) createForm.value?.requestClose() }
const showCreate = ref(false); const showColumns = ref(false); const showBatch = ref(false); const showDetail = ref(false); const showEdit = ref(false); const showExport = ref(false)
const showAddSku = ref(false); const addSkuProductId = ref('')
const showBatchSearch = ref(false); const batchSearchText = ref('')
const exportScope = ref<'FILTERED' | 'SELECTED'>('FILTERED')
const detailRow = ref<SkuListItemDto | null>(null); const editProduct = ref<ProductDetailDto | null>(null); const focusVariantId = ref<string>()
const visibleColumns = ref<ColumnKey[]>(restoredColumns())
const columnDraft = ref<ColumnKey[]>([...visibleColumns.value])
const columnSearch = ref(''); const filteredColumns = computed(() => columns.filter(column => column.label.includes(columnSearch.value.trim()) && (column.key !== 'supplier' || session.canAccess(['suppliers.read']))))
const filters = reactive({ field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', missing: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' })

const filterNames: Record<string, string> = { q: '关键词', productStatus: '产品状态', status: '子体状态', stock: '库存', missing: '资料缺项', hasImage: '图片', hasSupplier: '供应商', hasMaterial: '辅料', productType: '类型', field: '搜索范围', exact: '匹配方式' }
function filterValue(key: string, value: string | boolean) {
  if (key === 'q' || key === 'productType') return String(value)
  if (key === 'exact') return '精确匹配'
  if (key === 'hasImage') return value === 'true' ? '有图片' : '无图片'
  if (key === 'hasSupplier' || key === 'hasMaterial') return value === 'true' ? '已关联' : '未关联'
  return ({ ACTIVE: '启用', DRAFT: '待定', DISABLED: '停用', POSITIVE: '有库存', ZERO: '零库存', UNINITIALIZED: '未建账', image: '缺图片', dimensions: '缺规格', weight: '缺重量', supplier: '缺供应商', NAME: '子体名称', PRODUCT_NAME: '父体名称', SKU: '历史子体编码', PRODUCT_CODE: '历史父体编码' } as Record<string,string>)[String(value)] ?? String(value)
}
const activeFilterChips = computed(() => Object.entries(appliedFilters.value).filter(([key,value]) => filterNames[key] && value && !(key === 'field' && value === 'KEYWORD')).map(([key,value]) => ({ key, label: `${filterNames[key]}：${filterValue(key,value)}` })))
const advancedFilterCount = computed(() => activeFilterChips.value.filter(item => !['q','productStatus','stock'].includes(item.key)).length)
function removeFilter(key: string) {
  if (key === 'exact') filters.exact = false
  else if (key === 'field') filters.field = 'KEYWORD'
  else if (key in filters) Object.assign(filters, { [key]: '' })
}
const missingFieldKeys = { 图片: 'image', 规格: 'dimensions', 重量: 'weight', 供应商: 'supplier' } as const
function fillMissing(row: SkuListItemDto, label: string) { void openEdit(row, missingFieldKeys[label as keyof typeof missingFieldKeys]) }
function openInventory(row: SkuListItemDto) {
  if (formal && row.variant.id && session.canAccess(['inventory.read'])) inventoryTarget.value = { id: row.variant.id, name: row.variant.variantName, parentName: row.productName }
}
function stockNumber(value: number | string | undefined) { return value == null ? '—' : BigInt(value).toLocaleString('zh-CN') }

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
  return { imageAssetId: product.imageAssetId, productCode: product.productCode, productName: product.productName, productType: product.productType, description: product.description, status: product.status, version: product.version, variants: product.variants.map(variant => ({ id: variant.id, customs: variant.customs, skuCode: variant.skuCode, variantName: variant.variantName, nameHighlighted: variant.nameHighlighted, nameStyles: variant.nameStyles, itemDimensions: variant.itemDimensions, packageDimensions: variant.packageDimensions, itemLengthCm: variant.itemLengthCm, itemWidthCm: variant.itemWidthCm, itemHeightCm: variant.itemHeightCm, packageLengthCm: variant.packageLengthCm, packageWidthCm: variant.packageWidthCm, packageHeightCm: variant.packageHeightCm, packageWeightGrams: variant.packageWeightGrams, status: variant.status, imageAssetId: variant.imageAssetId, imageUrl: variant.imageUrl, version: variant.version, suppliers: structuredClone(toRaw(variant.suppliers)), materials: structuredClone(toRaw(variant.materials)) })) }
}
function apiMessage(exception: unknown, fallback: string) { return exception instanceof ApiError || exception instanceof Error ? exception.message : fallback }
let loadSequence = 0
const displayedColumns = computed(() => orderedColumns(visibleColumns.value).filter(key => hasColumn(key)))
const groups = computed(() => {
  if (formal) {
    const ids = new Set(matchingVariantIds.value)
    return products.value.map(product=>({ product, children: product.variants.filter(v=>ids.has(v.id ?? '')).map(variant=>({ productId: product.id, productCode: product.productCode, productName: product.productName, productStatus: product.status, productType: product.productType, variant, businessAvailable: product.status === 'ACTIVE' && variant.status === 'ACTIVE', updatedAt: product.updatedAt } as SkuListItemDto)) }))
  }
  const f = appliedFilters.value
  const needles = f.q.trim().toLocaleUpperCase().split(/\r?\n/).filter(Boolean)
  const matches = (product: ProductDetailDto, variant?: ProductVariantDto) => {
    if (f.productType && product.productType !== f.productType) return false
    if (f.stock === 'POSITIVE' && (skuStock(variant?.id) ?? 0) <= 0) return false
    if (f.stock === 'ZERO' && (skuStock(variant?.id) ?? 0) !== 0) return false
    if (f.stock === 'UNINITIALIZED' && variant?.id && variant.id in stockBalances.value) return false
    if (f.productStatus && product.status !== f.productStatus) return false
    if (f.status && variant?.status !== f.status) return false
    if (f.hasImage && Boolean(variant?.imageAssetId || variant?.imageUrl) !== (f.hasImage === 'true')) return false
    if (f.hasSupplier && Boolean(variant?.suppliers.length) !== (f.hasSupplier === 'true')) return false
    if (f.missing && (!variant || !missingFields(variant).includes(({ image: '图片', supplier: '供应商', dimensions: '规格', weight: '重量' } as Record<string, string>)[f.missing] ?? ''))) return false
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
      const first = f.sort === 'sku' ? a.product.productName : a.product.updatedAt ?? ''
      const second = f.sort === 'sku' ? b.product.productName : b.product.updatedAt ?? ''
      return first.localeCompare(second) * (f.direction === 'asc' ? 1 : -1)
    })
})
const pageGroups = computed(() => formal ? groups.value : groups.value.slice(page.value * size.value, (page.value + 1) * size.value))
const pageRows = computed(() => pageGroups.value.flatMap(group => group.children))
const matchingSkuCount = computed(() => formal ? serverSkuCount.value : groups.value.reduce((sum, group) => sum + group.children.length, 0))
function toggleParent(id: string) { expanded.value = expanded.value.includes(id) ? expanded.value.filter(value => value !== id) : [...expanded.value, id] }
function toggleGroup(children: SkuListItemDto[]) {
  const ids = children.flatMap(row => row.variant.id ? [row.variant.id] : [])
  selected.value = ids.every(id => selected.value.includes(id)) ? selected.value.filter(id => !ids.includes(id)) : [...new Set([...selected.value, ...ids])]
}
async function editParent(product: ProductDetailDto) {
  editFocusField.value = undefined
  error.value = ''; saving.value = true
  try { editProduct.value = await domainApi.product(product.id); focusVariantId.value = undefined; showEdit.value = true }
  catch (exception) { error.value = apiMessage(exception, '产品加载失败。') }
  finally { saving.value = false }
}
async function load() {
  const sequence = ++loadSequence
  const first = initialLoad; initialLoad = false
  if (!first) captureScroll()
  loading.value = true; error.value = ''
  try {
    const result = formal ? await productManagementApi.page({ ...filters, page: page.value, size: size.value, ...(focusParentAfterSave.value ? { focusProductId: focusParentAfterSave.value } : {}) }) : await domainApi.products()
    if (sequence !== loadSequence) return
    products.value = result.items
    appliedFilters.value = { ...filters }
    if ('matchingVariantIds' in result) {
      matchingVariantIds.value = result.matchingVariantIds; serverSkuCount.value = result.matchingSkuCount
      stockBalances.value = Object.fromEntries(result.balances.map(b=>[b.variantId,b.quantity]))
      total.value = result.totalCount; page.value = result.page
    } else { total.value = groups.value.length; page.value = Math.min(page.value, totalPages.value - 1) }
    rows.value = groups.value.flatMap(group => group.children)
    selected.value = selected.value.filter(id => rows.value.some(row => row.variant.id === id))
    if ((!first || !savedView) && (filters.q || filters.status || filters.hasImage || filters.hasSupplier || filters.hasMaterial || filters.stock || filters.missing)) expanded.value = groups.value.map(group => group.product.id)
  } catch (exception) { if (sequence === loadSequence) error.value = apiMessage(exception, '产品管理 数据加载失败。') }
  finally {
    if (sequence === loadSequence) {
      loading.value = false; await nextTick()
      const viewport = listHost.value?.querySelector('.table-viewport')
      if (viewport) viewport.scrollTop = scrollTop
      if (focusAfterSave.value) {
        const row = listHost.value?.querySelector(`[data-variant-id="${focusAfterSave.value.replace(/[^a-zA-Z0-9_-]/g, '')}"]`)
        row?.scrollIntoView?.({ block: 'nearest' })
        if (!row && !error.value) success.value += ' 保存的子体不符合当前筛选条件，可重置筛选查看。'
      }
      focusAfterSave.value = undefined; focusParentAfterSave.value = undefined
    }
  }
}
function applyFilters() { appliedFilters.value = { ...filters }; total.value = groups.value.length; page.value = Math.min(page.value, totalPages.value - 1); rows.value = groups.value.flatMap(group => group.children); if (filters.q) expanded.value = groups.value.map(group => group.product.id) }
function search() { page.value = 0; selected.value = []; const viewport=listHost.value?.querySelector('.table-viewport');if(viewport)viewport.scrollTop=0;scrollTop = 0; if (formal) void load(); else applyFilters() }
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(() => ({ ...filters }), () => { clearTimeout(searchTimer); searchTimer = setTimeout(search, 250) })
function reset() { Object.assign(filters, { field: 'KEYWORD', q: '', exact: false, status: '', productStatus: '', productType: '', stock: '', missing: '', hasImage: '', hasSupplier: '', hasMaterial: '', sort: 'updatedAt', direction: 'desc' }); page.value = 0; selected.value = []; expanded.value = []; void load() }
function applyBatchSearch() { const unique = [...new Set(batchSearchText.value.split(/\r?\n/).map(value => value.trim()).filter(Boolean))]; if (unique.length > 100) { error.value = '批量搜索最多输入 100 个 SKU 编码。'; return } filters.field = 'SKU'; filters.exact = true; filters.q = unique.join('\n'); showBatchSearch.value = false; search() }
function toggleSort(field: 'sku' | 'updatedAt') { if (filters.sort === field) filters.direction = filters.direction === 'asc' ? 'desc' : 'asc'; else { filters.sort = field; filters.direction = field === 'sku' ? 'asc' : 'desc' } page.value = 0; }
function toggleAll() { selected.value = allSelected.value ? [] : pageRows.value.map(row => row.variant.id).filter((id): id is string => Boolean(id)) }
function toggle(id?: string) { if (!id) return; selected.value = selected.value.includes(id) ? selected.value.filter(value => value !== id) : [...selected.value, id] }
function openColumns() { error.value = ''; columnDraft.value = [...visibleColumns.value]; showColumns.value = true }
function closeColumns() { columnDraft.value = [...visibleColumns.value]; showColumns.value = false }
const fixedColumnCount = computed(() => columnDraft.value.includes('image') ? 2 : 1)
function persistColumns() { visibleColumns.value = orderedColumns(columnDraft.value); try { localStorage.setItem(storageKey.value, JSON.stringify(visibleColumns.value)) } catch { success.value = '列设置已应用，浏览器未允许持久保存。' } showColumns.value = false }
function moveColumn(index: number, direction: number) { const next = index + direction; if (index < fixedColumnCount.value || next < fixedColumnCount.value || next >= columnDraft.value.length) return; const copy = [...columnDraft.value]; [copy[index], copy[next]] = [copy[next]!, copy[index]!]; columnDraft.value = copy }
const editFocusField = ref<'image' | 'dimensions' | 'weight' | 'supplier'>()
const detailInitialTab = ref<'basic' | 'purchase' | 'materials'>('basic')
function openDetail(row: SkuListItemDto, tab: 'basic' | 'purchase' | 'materials' = 'basic') { detailInitialTab.value = tab; detailRow.value = row; showDetail.value = true }
async function openEdit(row: SkuListItemDto, field?: 'image' | 'dimensions' | 'weight' | 'supplier') {
  editFocusField.value = field
  saving.value = true; error.value = ''
  try { editProduct.value = await domainApi.product(row.productId); focusVariantId.value = row.variant.id; showEdit.value = true }
  catch (exception) { error.value = apiMessage(exception, 'SKU 加载失败。') } finally { saving.value = false }
}
async function copySku(row: SkuListItemDto) {
  editFocusField.value = undefined
  saving.value = true; error.value = ''
  try {
    const product = await domainApi.product(row.productId); const source = product.variants.find(item => item.id === row.variant.id) ?? row.variant
    const duplicate = structuredClone(toRaw(source))
    delete duplicate.id; delete duplicate.version; delete duplicate.customs
    Object.assign(duplicate, { skuCode: '', variantName: `${source.variantName} 副本`, status: 'ACTIVE' as const })
    editProduct.value = { ...product, variants: [...product.variants, duplicate] }; focusVariantId.value = '__NEW__'; showEdit.value = true
  } catch (exception) { error.value = apiMessage(exception, '复制 SKU 失败。') } finally { saving.value = false }
}
async function addSkuToProduct() {
  editFocusField.value = undefined
  if (!addSkuProductId.value) { error.value = '请选择所属产品。'; return }
  saving.value = true; error.value = ''
  try {
    const product = await domainApi.product(addSkuProductId.value)
    editProduct.value = { ...product, variants: [...product.variants, { skuCode: '', variantName: '', status: 'ACTIVE', attributes: [], suppliers: [], materials: [] }] }
    focusVariantId.value = '__NEW__'; showAddSku.value = false; showEdit.value = true
  } catch (exception) { error.value = apiMessage(exception, '产品加载失败。') } finally { saving.value = false }
}
async function create(input: ProductInput) {
  if (saving.value) return
  saving.value = true; error.value = ''
  try { const saved = await domainApi.createProduct(input); focusParentAfterSave.value = saved.item.id; focusAfterSave.value = saved.item.variants[0]?.id; expanded.value.push(saved.item.id); showCreate.value = false; success.value = '产品及 SKU 已创建。'; await load() }
  catch (exception) { error.value = apiMessage(exception, '产品创建失败，请检查输入。') } finally { saving.value = false }
}
async function update(input: ProductInput) {
  if (saving.value) return
  if (!editProduct.value) return; saving.value = true; error.value = ''
  try {
    const target = focusVariantId.value && focusVariantId.value !== '__NEW__' ? input.variants.find(v=>v.id===focusVariantId.value) : undefined
    const saved = formal && target ? await productManagementApi.updateSku(editProduct.value.id,target) : await domainApi.updateProduct(editProduct.value.id, input)
    const savedVariant = target ?? (focusVariantId.value === '__NEW__' ? saved.variants.find(v => !editProduct.value?.variants.some(old => old.id === v.id)) : undefined)
    focusAfterSave.value = savedVariant?.id; focusParentAfterSave.value = editProduct.value.id; if(!expanded.value.includes(editProduct.value.id)) expanded.value.push(editProduct.value.id)
    showEdit.value = false; success.value = 'SKU 资料已保存。'; await load() }
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
    await domainApi.exportSkus({ sort: filters.sort, direction: filters.direction, stock: filters.stock, missing: filters.missing, productType: filters.productType || undefined, field: filters.field, q: filters.q.trim(), exact: filters.exact, status: filters.status as VariantStatus || undefined, productStatus: filters.productStatus as 'DRAFT' | 'ACTIVE' | 'DISABLED' || undefined, hasImage: filters.hasImage === '' ? undefined : filters.hasImage === 'true', hasSupplier: filters.hasSupplier === '' ? undefined : filters.hasSupplier === 'true', hasMaterial: filters.hasMaterial === '' ? undefined : filters.hasMaterial === 'true', variantIds: exportScope.value === 'SELECTED' ? selected.value : undefined })
    showExport.value = false; success.value = 'SKU 导出文件已生成。'
  } catch (exception) { error.value = apiMessage(exception, 'SKU 导出失败。') } finally { saving.value = false }
}
function changePage(next: number) { page.value = next; selected.value = []; const viewport = listHost.value?.querySelector('.table-viewport'); if (viewport) viewport.scrollTop = 0; scrollTop = 0; if (formal) void load() }
watch(size,()=>{ if (!restoringView) changePage(0) })
onBeforeUnmount(()=>{ clearTimeout(searchTimer); saveViewState(); window.removeEventListener('beforeunload', saveViewState) })
const route=useRoute()
async function openLinkedSupplier(){const id=route?.query.editProduct;if(typeof id!=='string'||!session.canAccess(['products.write','suppliers.write']))return;try{editProduct.value=await domainApi.product(id);focusVariantId.value=typeof route?.query.variantId==='string'?route?.query.variantId:undefined;showEdit.value=true}catch(e){error.value=apiMessage(e,'产品加载失败。')}}
watch(()=>route?.query.editProduct,openLinkedSupplier)
onMounted(async()=>{
  if(savedView){ for(const key of Object.keys(filters)) if(typeof savedView.filters?.[key] === typeof (filters as Record<string, string | boolean>)[key]) (filters as Record<string, string | boolean>)[key]=savedView.filters[key]; page.value=Math.max(0,Number(savedView.page)||0); size.value=[10,20,50,100].includes(savedView.size)?savedView.size:20; expanded.value=Array.isArray(savedView.expanded)?savedView.expanded:[]; scrollTop=Number(savedView.scrollTop)||0; await nextTick();clearTimeout(searchTimer) }
  restoringView = false; window.addEventListener('beforeunload', saveViewState)
  await load();await openLinkedSupplier()
})
</script>

<template>
  <section
    ref="listHost"
    class="page-stack product-sku-page product-ui"
  >
    <SyntheticNotice v-if="runtimeConfig.authMode === 'mock'" />
    <form
      class="panel filter-panel material-filters product-management-filters"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="filters.q"
        type="search"
        placeholder="品名或规格模糊搜索"
        @input="filters.field = 'KEYWORD'; filters.exact = false"
      ></label>
      <label>产品状态<select
        v-model="filters.productStatus"
        v-single-select
      ><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
      <label v-if="session.canAccess(['inventory.read'])">库存<select
        v-model="filters.stock"
        v-single-select
      ><option value="">全部库存</option><option value="POSITIVE">有库存</option><option value="ZERO">零库存</option><option value="UNINITIALIZED">未建账</option></select></label>
      <button
        type="button"
        class="filter-more-button"
        :aria-expanded="moreFilters"
        @click="moreFilters = !moreFilters"
      >
        更多筛选 <span
          v-if="advancedFilterCount"
          class="filter-count"
        >{{ advancedFilterCount }}</span> {{ moreFilters ? '▴' : '▾' }}
      </button>
      <template v-if="moreFilters">
        <label>子体状态<select
          v-model="filters.status"
          v-single-select
        ><option value="">全部</option><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
        <label>图片<select
          v-model="filters.hasImage"
          v-single-select
        ><option value="">全部</option><option value="true">有图片</option><option value="false">无图片</option></select></label>
        <label v-if="session.canAccess(['suppliers.read'])">供应商<select
          v-model="filters.hasSupplier"
          v-single-select
        ><option value="">全部</option><option value="true">已关联</option><option value="false">未关联</option></select></label>
        <label>辅料<select
          v-model="filters.hasMaterial"
          v-single-select
        ><option value="">全部</option><option value="true">已关联</option><option value="false">未关联</option></select></label>
        <label>资料缺项<select
          v-model="filters.missing"
          v-single-select
        ><option value="">不限</option><option value="image">缺图片</option><option value="dimensions">缺规格</option><option value="weight">缺重量</option><option value="supplier">缺供应商</option></select></label>
      </template>
      <button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button>
      <div
        v-if="activeFilterChips.length"
        class="product-filter-chips"
        aria-label="已生效筛选"
      >
        <span>已筛选</span><button
          v-for="chip in activeFilterChips"
          :key="chip.key"
          type="button"
          :aria-label="`移除筛选：${chip.label}`"
          :title="chip.label"
          @click="removeFilter(chip.key)"
        >
          {{ chip.label }} <span aria-hidden="true">×</span>
        </button>
      </div>
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
          class="toolbar-link product-import-link"
          to="/products/imports"
        >
          导入
        </RouterLink><button
          v-if="session.canAccess(['products.export'])"
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
          v-if="formal && session.canAccess(['products.delete'])"
          type="button"
          class="secondary-button"
          @click="showDeleted = true"
        >
          回收站
        </button>
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
    <ProductRestoreDialog
      v-if="showDeleted"
      @close="showDeleted=false"
      @changed="load"
    />
    <ProductInventoryDialog
      v-if="inventoryTarget"
      :variant-id="inventoryTarget.id"
      :name="inventoryTarget.name"
      :parent-name="inventoryTarget.parentName"
      @close="inventoryTarget = undefined"
      @changed="load"
    />
    <CatalogDeleteDialog
      v-if="deleteTarget"
      :title="deleteTarget.variant ? '删除 SKU' : '删除父体'"
      :name="deleteTarget.variant ? deleteTarget.variant.variantName : deleteTarget.product.productName"
      notice="采用逻辑删除，保留图片、资料和历史单据；已有采购及发货仍可继续处理，不再用于新建业务。误删可从“回收站”中恢复目录资料。"
      :description="deleteTarget.variant ? '将从列表隐藏此 SKU 及对应客户商品，不影响父体或其他 SKU。' : '仅删除已无子体的父体资料，保留历史单据。'"
      :busy="deleting"
      :error="deleteBlockedReason || deleteError"
      :blocked="!!deleteBlockedReason"
      @cancel="!deleting && (deleteTarget = undefined)"
      @confirm="confirmDelete"
    />
    <SuccessToast :message="success" />
    <ProductUiState
      v-if="loading"
      loading
      title="正在加载产品…"
    />
    <ProductUiState
      v-else-if="error"
      error
      title="产品列表加载失败"
      :description="error"
      @retry="load"
    />
    <ProductUiState
      v-else-if="!groups.length"
      :title="activeFilterChips.length ? '没有符合条件的产品' : '暂无产品'"
      :description="activeFilterChips.length ? '调整或清除筛选条件后再试。' : '创建产品后，可继续添加子体、供应商和辅料。'"
    >
      <button
        v-if="activeFilterChips.length"
        type="button"
        class="product-text-button"
        @click="reset"
      >
        清除筛选
      </button>
      <button
        v-else-if="writable"
        type="button"
        class="product-text-button"
        @click="showCreate = true"
      >
        新增产品
      </button>
    </ProductUiState>
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
                    :indeterminate="selected.length > 0 && !allSelected"
                    @change="toggleAll"
                  >
                </th>
                <th
                  v-for="key in displayedColumns"
                  :key="key"
                  :class="`product-column--${key}`"
                  scope="col"
                >
                  <button
                    v-if="key === 'sku' || key === 'updated'"
                    class="sort-button"
                    type="button"
                    @click="toggleSort(key === 'sku' ? 'sku' : 'updatedAt')"
                  >
                    {{ key === 'sku' ? '品名' : '更新时间' }} {{ filters.sort === (key === 'sku' ? 'sku' : 'updatedAt') ? (filters.direction === 'asc' ? '↑' : '↓') : '' }}
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
                  :class="{ 'is-selected': group.children.length > 0 && group.children.every(row => selected.includes(row.variant.id ?? '')), 'is-expanded': expanded.includes(group.product.id) }"
                  @click="toggleParent(group.product.id)"
                >
                  <td @click.stop>
                    <input
                      type="checkbox"
                      :aria-label="`选择父体 ${group.product.productName}`"
                      :checked="group.children.length > 0 && group.children.every(row => selected.includes(row.variant.id ?? ''))"
                      :indeterminate="group.children.some(row => selected.includes(row.variant.id ?? '')) && !group.children.every(row => selected.includes(row.variant.id ?? ''))"
                      :disabled="!group.children.length"
                      @change="toggleGroup(group.children)"
                    >
                  </td>
                  <td
                    v-for="key in displayedColumns"
                    :key="key"
                    :class="[`product-column--${key}`, { 'hierarchy-cell': key === 'sku' }]"
                  >
                    <button
                      v-if="key === 'sku'"
                      type="button"
                      class="parent-toggle"
                      :aria-expanded="expanded.includes(group.product.id)"
                      @click.stop="toggleParent(group.product.id)"
                    >
                      <span
                        class="product-expand-icon"
                        aria-hidden="true"
                      >{{ expanded.includes(group.product.id) ? '▾' : '▸' }}</span>
                      <span class="product-parent-label">
                        <span class="product-display-name">{{ group.product.productName }}</span>
                        <small class="product-display-code">父体 · {{ group.children.length === group.product.skuCount ? `${group.product.skuCount} 个子体` : `匹配 ${group.children.length} / 共 ${group.product.skuCount} 个` }}</small>
                      </span>
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
                        class="product-thumbnail product-image-empty"
                        role="img"
                        :aria-label="`${group.product.productName}暂无图片`"
                      ><span aria-hidden="true">▧</span><small>暂无图片</small></span>
                    </template>
                    <span
                      v-else-if="key === 'status'"
                      :class="['status-badge', `status-badge--${group.product.status.toLowerCase()}`]"
                    >{{ labelStatus(group.product.status) }}</span>
                    <span v-else-if="key === 'updated'">{{ group.product.updatedAt ? beijingDateTime(group.product.updatedAt) : '—' }}</span>
                    <span
                      v-else-if="key === 'stock'"
                      class="product-parent-stock"
                      title="全部未删除子体的库存之和，不受当前筛选影响"
                    >{{ session.canAccess(['inventory.read']) ? stockNumber(parentStock(group.product)) : '—' }}<small>合计</small></span><span v-else>—</span>
                  </td>
                  <td
                    class="sticky-action"
                    @click.stop
                  >
                    <div class="table-actions">
                      <button
                        type="button"
                        class="row-detail-link"
                        @click="parentDetailId = group.product.id"
                      >
                        详情
                      </button>
                      <RowMoreActions
                        v-if="writable || session.canAccess(['products.delete'])"
                        :label="`父体 ${group.product.productName} 更多操作`"
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
                          v-if="session.canAccess(['products.delete'])"
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
                    v-for="(row, childIndex) in group.children"
                    :key="row.variant.id ?? row.variant.skuCode"
                    class="product-child-row"
                    :data-variant-id="row.variant.id"
                    :class="{ 'is-selected': selected.includes(row.variant.id ?? ''), 'is-last-child': childIndex === group.children.length - 1 }"
                  >
                    <td>
                      <input
                        type="checkbox"
                        :aria-label="`选择 ${row.variant.variantName}`"
                        :checked="selected.includes(row.variant.id ?? '')"
                        @change="toggle(row.variant.id)"
                      >
                    </td>
                    <td
                      v-for="key in displayedColumns"
                      :key="key"
                      :class="[`product-column--${key}`, { 'hierarchy-cell': key === 'sku' }]"
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
                          <AuthenticatedCatalogImage
                            v-else-if="row.variant.imageUrl"
                            class="product-thumbnail"
                            :image-url="row.variant.imageUrl"
                            :alt="`${row.variant.variantName}主图`"
                          />
                        </button>
                        <span
                          v-else
                          class="product-thumbnail product-image-empty"
                          role="img"
                          :aria-label="`${row.variant.variantName}暂无图片`"
                        ><span aria-hidden="true">▧</span><small>暂无图片</small></span>
                      </template>
                      <div
                        v-else-if="key === 'sku'"
                        class="product-child-name"
                      >
                        <button
                          class="sku-code-button"
                          type="button"
                          @click="openDetail(row)"
                        >
                          <ProductName
                            :text="row.variant.variantName"
                            :styles="row.variant.nameStyles"
                            :highlighted="row.variant.nameHighlighted"
                          />
                          <small class="product-child-label">子体</small>
                        </button>
                        <details
                          v-if="missingFields(row.variant).length"
                          class="product-missing"
                          @click.stop
                        >
                          <summary :aria-label="`${row.variant.variantName}待补 ${missingFields(row.variant).length} 项`">
                            待补 {{ missingFields(row.variant).length }} 项
                          </summary>
                          <div class="product-missing-items">
                            <template
                              v-for="field in missingFields(row.variant)"
                              :key="field"
                            >
                              <button
                                v-if="writable"
                                type="button"
                                :disabled="saving"
                                @click="fillMissing(row,field)"
                              >
                                补充{{ field }} <span aria-hidden="true">›</span>
                              </button><span v-else>缺{{ field }}</span>
                            </template>
                          </div>
                        </details>
                      </div>
                      <template v-else-if="key === 'stock'">
                        <button
                          v-if="formal && session.canAccess(['inventory.read'])"
                          type="button"
                          class="product-stock-link"
                          :aria-label="`查看${row.variant.variantName}的库存与流水`"
                          @click.stop="openInventory(row)"
                        >
                          {{ stockNumber(skuStock(row.variant.id)) }}<small v-if="!(row.variant.id! in stockBalances)">未建账</small>
                        </button><span v-else>—</span>
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
                        <button
                          type="button"
                          class="relation-count-link"
                          :aria-label="`查看${row.variant.variantName}的供应商`"
                          @click.stop="openDetail(row, 'purchase')"
                        >
                          {{ row.variant.suppliers.length }} 家 <span aria-hidden="true">›</span>
                        </button>
                      </template>
                      <template v-else-if="key === 'material'">
                        <button
                          v-if="session.canAccess(['materials.read'])"
                          type="button"
                          class="relation-count-link"
                          :aria-label="`查看${row.variant.variantName}的关联辅料`"
                          @click.stop="openDetail(row, 'materials')"
                        >
                          {{ row.variant.materials.length }} 种 <span aria-hidden="true">›</span>
                        </button>
                        <span
                          v-else
                          title="暂无辅料资料查看权限"
                        >{{ row.variant.materials.length }} 种</span>
                      </template>
                      <template v-else-if="key === 'updated'">
                        {{ row.updatedAt ? beijingDateTime(row.updatedAt) : '—' }}
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
                          v-if="writable || session.canAccess(['products.delete']) || session.canAccess(['inventory.read'])"
                          :label="`SKU ${row.variant.variantName} 更多操作`"
                        >
                          <button
                            v-if="formal && session.canAccess(['inventory.read'])"
                            type="button"
                            @click="openInventory(row)"
                          >
                            库存操作 / 流水
                          </button>
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
                            v-if="session.canAccess(['products.delete'])"
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
    </article>
    <ListPagination
      v-model:page-size="size"
      :page="page + 1"
      :total="total"
      :disabled="loading"
      @update:page="changePage($event - 1)"
    >
      <template #summary>
        按父体分页 · 已选 {{ selected.length }} 个 SKU，共 {{ total }} 个父体 / {{ matchingSkuCount }} 个匹配子体
      </template>
    </ListPagination>


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
          v-single-select
          required
        ><option value="">请选择产品</option><option
          v-for="product in selectedProducts"
          :key="product.id"
          :value="product.id"
        >{{ product.productName }}</option></select></label>
        <footer class="ui-dialog-actions dialog-footer">
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
        <footer class="ui-dialog-actions dialog-footer">
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
      @keydown.esc.stop="closeCreate"
    >
      <section class="panel operation-dialog__card product-create-dialog product-ui">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭"
          :disabled="saving"
          @click="closeCreate"
        >
          ×
        </button><h2>新增产品</h2><p
          v-if="error"
          class="data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p><p>创建产品主档，并维护一个或多个内部 SKU。</p><ProductForm
          ref="createForm"
          class="product-create-form"
          :busy="saving"
          submit-label="创建产品"
          show-cancel
          @cancel="showCreate = false"
          @submit="create"
        />
      </section>
    </div>
    <ProductEditDialog
      v-if="showEdit && editProduct"
      :key="`${editProduct.id}-${focusVariantId ?? 'parent'}`"
      :initial="toInput(editProduct)"
      :focus-variant-id="focusVariantId"
      :focus-field="editFocusField"
      :busy="saving"
      :error="error"
      @close="showEdit = false"
      @submit="update"
    />
    <ParentProductDetailDialog
      v-if="parentDetailId"
      :product-id="parentDetailId"
      :writable="writable"
      @close="parentDetailId = undefined"
      @edit="parentDetailId = undefined; editParent($event)"
      @sku="parentDetailId = undefined; openDetail($event)"
    />
    <SkuDetailDialog
      v-if="showDetail && detailRow"
      :detail-row="detailRow"
      :initial-tab="detailInitialTab"
      :writable="writable"
      :error="error"
      @close="showDetail = false"
      @edit="showDetail = false; openEdit($event)"
    />
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
        </p>
        <p class="column-order-hint">
          图片显示时固定在首列，品名紧随其后；其余字段可调整顺序。
        </p>
        <div class="column-picker">
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
              @change="columnDraft = orderedColumns(columnDraft)"
            >{{ column.label }}</label>
          </div><ol>
            <li
              v-for="(key, index) in columnDraft"
              :key="key"
            >
              <span>{{ columns.find(column => column.key === key)?.label }}</span><button
                type="button"
                :disabled="index <= fixedColumnCount"
                :aria-label="`上移${columns.find(column => column.key === key)?.label}`"
                @click="moveColumn(index, -1)"
              >
                ↑
              </button><button
                type="button"
                :disabled="index < fixedColumnCount || index + 1 === columnDraft.length"
                :aria-label="`下移${columns.find(column => column.key === key)?.label}`"
                @click="moveColumn(index, 1)"
              >
                ↓
              </button>
            </li>
          </ol>
        </div><footer class="ui-dialog-actions dialog-footer">
          <button
            class="secondary-action"
            type="button"
            @click="columnDraft = [...defaultColumns]"
          >
            恢复默认
          </button><button
            class="ui-button-primary"
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
        <footer class="ui-dialog-actions dialog-footer">
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
          v-single-select
          :disabled="!batch.statusSelected"
        ><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label><fieldset>
          <legend>
            <label><input
              v-model="batch.itemDimensionsSelected"
              type="checkbox"
            >产品规格（cm）</label>
          </legend><select
            v-model="batch.itemDimensionsMode"
            v-single-select
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
            v-single-select
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
          v-single-select
          :disabled="!batch.packageWeightSelected"
          aria-label="包装重量修改方式"
        ><option value="SET">填入值</option><option value="CLEAR">清空</option></select><input
          v-model.number="batch.packageWeightGrams"
          type="number"
          min="0.001"
          step="0.001"
          :disabled="!batch.packageWeightSelected || batch.packageWeightMode === 'CLEAR'"
        ></label><section class="batch-preview">
          <strong>变更预览</strong><p>目标：{{ selectedRows.map(row => row.variant.variantName).join('、') }}</p><ul>
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
        </section><footer class="ui-dialog-actions dialog-footer">
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
    <ImagePreviewDialog
      v-if="imagePreview"
      :title="`${imagePreview.variantName}图片预览`"
      :description="imagePreview.variantName"
      @close="imagePreview = null"
    >
      <AuthenticatedCatalogImage
        v-if="imagePreview.imageAssetId"
        :image-id="imagePreview.imageAssetId"
        variant="original"
        :alt="imagePreview.variantName"
      />
      <AuthenticatedCatalogImage
        v-else
        variant="original"
        :image-url="imagePreview.imageUrl"
        :alt="imagePreview.variantName"
      />
      <template #actions>
        <button
          type="button"
          :disabled="imageDownloading"
          @click="downloadPreviewImage"
        >
          {{ imageDownloading ? '正在下载…' : '保存图片' }}
        </button>
      </template>
      <template
        v-if="imageDownloadError"
        #message
      >
        <p role="alert">
          {{ imageDownloadError }}
        </p>
      </template>
    </ImagePreviewDialog>
  </section>
</template>

<style scoped>

/* Product list only: retain shared scrolling, pagination and image dimensions. */
.product-sku-page .sku-master-table { min-width: 960px; }
.product-sku-page .sku-master-table :is(th, td):first-child { width: 44px; min-width: 44px; padding-inline: 12px; text-align: center; }
.product-sku-page .sku-master-table th { color: var(--muted); background: #f7f9fc; border-bottom: 1px solid var(--line); }
.product-sku-page .sku-master-table td { padding: 10px 12px; border-bottom: 1px solid #e9edf3; }
.product-sku-page .sku-master-table .product-column--image { width: 128px; min-width: 128px; text-align: left; }
#main-content .product-sku-page .sku-master-table td.product-column--image { position: relative; padding-left: 12px; padding-right: 12px; }
#main-content .product-sku-page .sku-master-table .product-child-row > .product-column--image { padding-left: 36px; }
.product-child-row > .product-column--image::before { content: ''; position: absolute; left: 12px; top: 0; bottom: 0; border-left: 1px solid #c2cfdf; }
.product-child-row.is-last-child > .product-column--image::before { bottom: 50%; }
.product-child-row > .product-column--image::after { content: ''; position: absolute; left: 12px; top: 50%; width: 16px; border-top: 1px solid #c2cfdf; }
.product-sku-page .sku-master-table .hierarchy-cell { width: 32%; min-width: 240px; white-space: normal; }
.product-sku-page .sku-master-table .product-parent-row > td { background: #eaf0f8; border-top: 1px solid #cdd9e8; border-bottom-color: #d7e1ed; }
.product-sku-page .sku-master-table .product-parent-row > td:first-child { box-shadow: inset 3px 0 #9aafc9; }
.product-sku-page .sku-master-table .product-child-row > td { background: #fff; }
.product-sku-page .sku-master-table tbody tr:hover > td { background: #f0f6ff; }
.filter-more-button { min-height:var(--ui-control-height);padding:4px 8px;border:0;background:transparent;color:#2864c5;cursor:pointer;font-size:13px; }
.product-missing { color: #996a20; font-size: 11px; }
.product-sku-page .sku-master-table tbody tr.is-selected > td { background: var(--accent-soft); }
.product-sku-page .sku-master-table tbody tr.is-selected > td:first-child { box-shadow: inset 3px 0 var(--accent); }
.product-sku-page .sku-master-table .parent-toggle { display: flex; align-items: center; gap: 10px; width: 100%; color: var(--ink); }
.product-expand-icon { display: grid; flex: 0 0 24px; width: 24px; height: 24px; place-items: center; color: var(--muted); border: 1px solid #dce3ee; border-radius: 5px; background: #fff; }
.is-expanded .product-expand-icon { color: var(--accent); border-color: #c6d9f4; background: var(--accent-soft); }
.product-parent-label { min-width: 0; }
#main-content .product-sku-page .sku-master-table .product-child-row .hierarchy-cell { padding-left: 46px; border-left: 0; }
.product-sku-page .sku-master-table .sku-code-button { position: relative; line-height: 1.6; }
.product-sku-page .sku-master-table .sku-code-button::before { content: ''; position: absolute; left: -20px; top: 6px; width: 10px; height: 12px; border-left: 1px solid #c9d5e5; border-bottom: 1px solid #c9d5e5; border-radius: 0 0 0 3px; }
.product-sku-page .sku-master-table .material-image-button { margin: 0; border-radius: var(--ui-radius); outline: 1px solid var(--line); background: #fff; }
.product-sku-page .sku-master-table .material-image-button:hover { outline-color: #aac8ef; box-shadow: 0 2px 8px #2675d815; }
.product-sku-page .sku-master-table .material-image-button:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
.product-sku-page .sku-master-table .product-image-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; margin: 0; gap: 2px; color: #9aa6b6; background: #f9fafc; border: 1px dashed #d8e0eb; border-radius: var(--ui-radius); }
.product-sku-page .sku-master-table .product-child-label { font-size: 12px; font-weight: 400; color: var(--muted); }
.product-image-empty > span { font-size: 24px; line-height: 1; }
.product-sku-page .sku-master-table .product-image-empty small { margin: 0; color: var(--muted); font-size: 11px; }
.product-sku-page .sku-master-table .status-badge { border-radius: 4px; font-size: 12px; font-weight: 500; padding: 3px 8px; }
.product-sku-page .sku-master-table .product-column--weight { font-variant-numeric: tabular-nums; }
.product-sku-page .sku-master-table .sticky-action { border-left: 1px solid var(--line); box-shadow: -4px 0 8px #17203304; }
.product-sku-page .sku-master-table .table-actions { flex-wrap: nowrap; }
.product-sku-page .sku-master-table .relation-count-link { display: inline-flex; align-items: center; gap: 8px; min-height: 32px; padding: 3px 8px; border: 1px solid #dbe6f4; border-radius: 4px; color: var(--accent); background: #f4f8fd; font-size: 13px; cursor: pointer; }
.product-sku-page .sku-master-table .relation-count-link:hover { background: #e8f1fc; border-color: #b6cee9; }
.column-order-hint { margin: 8px 0 12px; color: var(--muted); font-size: 12px; }
@media (max-width: 640px) {
  .product-sku-page .sku-master-table .sticky-action { position: static; box-shadow: none; }
}

</style>
