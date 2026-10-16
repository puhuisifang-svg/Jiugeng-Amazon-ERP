import { ApiError, SESSION_EXPIRED_EVENT, SESSION_TOKEN_KEY, apiRequest, assertSyntheticEnvelope } from './client'
import type { CandidateDetailDto, InventoryBalanceDto, MaterialDto, ProductDto, SelectionCandidateDto, SyntheticList, WatchEventDto, WatchStoreDto } from './resources'
import { runtimeConfig } from '@/config/runtime'

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED'
export type VariantStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED'
export interface VariantAttributeDto { attributeCode: string; attributeValue: string; displayOrder: number }
export interface SupplierAssignmentDto { supplierId: string; purchaseUrl?: string | undefined; remark?: string | undefined; primary: boolean }
export interface MaterialAssignmentDto { materialId: string; standardQuantity: number; unit: string; lossRate: number; remark?: string | undefined; enabled: boolean }
export interface ProductVariantDto {
  id?: string
  skuCode: string
  variantName: string
  barcode?: string
  material?: string
  specification?: string
  itemDimensions?: string
  packageDimensions?: string
  itemLengthCm?: number
  itemWidthCm?: number
  itemHeightCm?: number
  packageLengthCm?: number
  packageWidthCm?: number
  packageHeightCm?: number
  packageWeight?: number
  packageWeightUnit?: 'G' | 'KG' | 'OZ' | 'LB'
  packageWeightGrams?: number
  imageAssetId?: string
  imageUrl?: string
  version?: number
  status: VariantStatus
  attributes: VariantAttributeDto[]
  suppliers: SupplierAssignmentDto[]
  materials: MaterialAssignmentDto[]
}
export interface ProductVariantInput {
  id?: string | undefined; skuCode: string; variantName: string; itemDimensions?: string | undefined; packageDimensions?: string | undefined
  itemLengthCm?: number | undefined; itemWidthCm?: number | undefined; itemHeightCm?: number | undefined
  packageLengthCm?: number | undefined; packageWidthCm?: number | undefined; packageHeightCm?: number | undefined
  packageWeightGrams?: number | undefined; status: VariantStatus; imageAssetId?: string | undefined; imageUrl?: string | undefined; version?: number | undefined
  suppliers: SupplierAssignmentDto[]; materials: MaterialAssignmentDto[]
}
export interface SkuListItemDto { productId: string; productCode: string; productName: string; productStatus: ProductStatus; productType?: string; variant: ProductVariantDto; businessAvailable: boolean; updatedAt?: string }
export interface SkuPageDto { items: SkuListItemDto[]; totalCount: number; page: number; size: number }
export interface ProductHistoryDto { id: string; action: string; actorLoginName: string; actorDisplayName?: string; detail?: string; occurredAt: string }
export interface BatchVariantPatchInput { productId: string; variantId: string; productVersion: number; variantVersion: number; status?: VariantStatus | undefined; statusSelected: boolean; itemLengthCm?: number | undefined; itemWidthCm?: number | undefined; itemHeightCm?: number | undefined; itemDimensionsSelected: boolean; packageLengthCm?: number | undefined; packageWidthCm?: number | undefined; packageHeightCm?: number | undefined; packageDimensionsSelected: boolean; packageWeightGrams?: number | undefined; packageWeightSelected: boolean }
export interface ProductDetailDto {
  id: string
  productCode: string
  productName: string
  nameZh: string
  nameEn?: string
  brand?: string
  internalCategory?: string
  productType?: string | undefined
  description?: string | undefined
  status: ProductStatus
  version: number
  variants: ProductVariantDto[]
  skuCount: number
  supplierDetailsAvailable: boolean
  createdAt?: string
  updatedAt?: string
}
export interface ProductInput {
  productCode: string
  productName: string
  productType?: string | undefined
  description?: string | undefined
  status: ProductStatus
  version?: number | undefined
  variants: ProductVariantInput[]
}
export type SupplierStatus = 'ACTIVE' | 'DISABLED'
export interface SupplierDto { id: string; name: string; contactPerson?: string; phone?: string; wechatId?: string; sourceUrl?: string; qrImageUrl?: string; qrImageId?: string; remark?: string; status: SupplierStatus; version: number; createdAt?: string; updatedAt?: string }
export interface SupplierInput { name: string; contactPerson?: string | undefined; phone?: string | undefined; wechatId?: string | undefined; sourceUrl?: string | undefined; qrImageUrl?: string | undefined; qrImageId?: string | undefined; remark?: string | undefined; status: SupplierStatus; version?: number | undefined }
export interface SupplierLinkDto extends SupplierAssignmentDto { id: string; ownerId: string; supplierName: string; supplierStatus: SupplierStatus; updatedAt?: string }
export interface PageDto<T> { items: T[]; totalCount: number; page: number; size: number }
export type MaterialStatus = 'ACTIVE' | 'DISABLED'
export interface MaterialDetailDto {
  id: string
  materialCode: string
  name: string
  materialType: string
  specification?: string
  unit: string
  imageUrl?: string
  imageAssetId?: string
  remark?: string
  status: MaterialStatus
  createdAt?: string
  updatedAt?: string
}
export interface MaterialInput {
  materialCode: string
  name: string
  materialType: string
  specification?: string
  unit: string
  imageUrl?: string
  imageAssetId?: string
  remark?: string
}
export interface ProductMaterialRelationDto {
  id: string
  productId: string
  productCode: string
  variantId: string
  skuCode: string
  materialId: string
  materialCode: string
  materialName: string
  standardQuantity: number
  unit: string
  lossRate: number
  remark?: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}
export interface ProductMaterialRelationInput {
  productId: string
  variantId: string
  materialId: string
  standardQuantity: number
  unit: string
  lossRate: number
  remark?: string
  enabled?: boolean
}
export type InventoryTransactionType = 'OPENING' | 'INBOUND' | 'OUTBOUND' | 'ADJUST_IN' | 'ADJUST_OUT' | 'REVERSAL' | 'ADJUSTMENT'
export type InventoryCommandType = Exclude<InventoryTransactionType, 'OPENING' | 'ADJUSTMENT'>
export interface InventoryBalanceViewDto extends InventoryBalanceDto {
  materialId?: string
  safetyStock?: number
  lowStock?: boolean
  updatedAt?: string
}
export interface LedgerEntryDto {
  id: string
  requestId: string
  warehouseCode?: string
  materialId?: string
  materialCode: string
  materialName?: string
  type: InventoryTransactionType
  quantity: number
  signedQuantity?: number
  unit?: string
  reason: string
  referenceTransactionId?: string
  balanceAfter?: number
  actorLoginName?: string
  occurredAt: string
}
export interface InventoryCommandInput {
  requestId: string
  materialId?: string
  materialCode?: string
  type: InventoryTransactionType
  quantity?: number
  unit?: string
  reason: string
  referenceTransactionId?: string
}
export type CandidateStatus = 'NEW' | 'REVIEWING' | 'APPROVED' | 'REJECTED'
export type WatchRunMode = 'COMPLETE' | 'INCOMPLETE' | 'FAILED'

interface SyntheticWrite<T> { mockData: boolean; source: 'SYNTHETIC'; item: T; replayed?: boolean }

function assertSynthetic<T extends { mockData: boolean; source: string }>(response: T): T {
  assertSyntheticEnvelope(response)
  return response
}

async function syntheticList<T>(path: string): Promise<SyntheticList<T>> {
  const response = await apiRequest<SyntheticList<T>>(path)
  assertSyntheticEnvelope(response)
  return response
}

interface MockProduct extends ProductDto { name: string; skus: Array<{ skuCode: string; status?: string }> }

function adaptMockProduct(product: MockProduct): ProductDetailDto {
  return {
    id: product.id,
    productCode: product.productCode,
    productName: product.name,
    nameZh: product.name,
    status: product.status as ProductStatus,
    version: 0,
    supplierDetailsAvailable: false,
    skuCount: product.skuCount ?? product.skus.length,
    variants: product.skus.map((sku, index) => ({
      skuCode: sku.skuCode,
      variantName: sku.skuCode,
      status: (sku.status ?? 'ACTIVE') as VariantStatus, version: 0,
      attributes: [{ attributeCode: 'mock_sequence', attributeValue: String(index + 1), displayOrder: 0 }],
      suppliers: [], materials: [],
    })),
  }
}

async function products(): Promise<{ items: ProductDetailDto[]; mockData: boolean }> {
  if (runtimeConfig.authMode === 'mock') {
    const response = await syntheticList<MockProduct>('/products')
    return { items: response.items.map(adaptMockProduct), mockData: true }
  }
  return { items: await apiRequest<ProductDetailDto[]>('/v1/products'), mockData: false }
}

async function product(id: string): Promise<ProductDetailDto> {
  if (runtimeConfig.authMode === 'mock') {
    const response = await syntheticList<MockProduct>('/products')
    const item = response.items.find(value => value.id === id)
    if (!item) throw new Error('Product not found')
    return adaptMockProduct(item)
  }
  return apiRequest<ProductDetailDto>(`/v1/products/${id}`)
}

async function exportSkus(filters: { productType?: string | undefined; field?: string | undefined; q?: string | undefined; exact?: boolean | undefined; status?: VariantStatus | undefined; productStatus?: ProductStatus | undefined; hasImage?: boolean | undefined; hasSupplier?: boolean | undefined; hasMaterial?: boolean | undefined; variantIds?: string[] | undefined }): Promise<void> {
  if (runtimeConfig.authMode === 'mock') throw new Error('模拟模式不提供正式导出')
  const search = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === '' || (Array.isArray(value) && !value.length)) return
    if (Array.isArray(value)) value.forEach(item => search.append(key, item)); else search.set(key, String(value))
  })
  const headers = new Headers(); const token = window.localStorage.getItem(SESSION_TOKEN_KEY) ?? window.sessionStorage.getItem(SESSION_TOKEN_KEY)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}/v1/products/skus/export?${search}`, { headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? `请求失败（${response.status}）`)
  }
  const url = URL.createObjectURL(await response.blob()); const anchor = document.createElement('a')
  anchor.href = url; anchor.download = `产品SKU_${new Date().toISOString().slice(0, 10)}.xlsx`; anchor.click(); URL.revokeObjectURL(url)
}

type LegacyProductInput = { productCode: string; name: string; skuCodes: string[] }

async function createProduct(input: ProductInput | LegacyProductInput): Promise<{ item: ProductDetailDto; mockData: boolean }> {
  const normalized: ProductInput = 'name' in input ? {
    productCode: input.productCode,
    productName: input.name,
    status: 'DRAFT',
    variants: input.skuCodes.map((skuCode, index) => ({ skuCode, variantName: skuCode, status: 'DRAFT',
      attributes: [{ attributeCode: 'mock_sequence', attributeValue: String(index + 1), displayOrder: 0 }], suppliers: [], materials: [] })),
  } : input
  if (runtimeConfig.authMode === 'mock') {
    const response = assertSynthetic(await apiRequest<SyntheticWrite<MockProduct>>('/products', {
      method: 'POST', body: JSON.stringify({
        productCode: normalized.productCode,
        name: normalized.productName,
        skuCodes: normalized.variants.map(variant => variant.skuCode),
      }),
    }))
    return { item: adaptMockProduct(response.item), mockData: true }
  }
  return { item: await apiRequest<ProductDetailDto>('/v1/products', {
    method: 'POST', body: JSON.stringify(normalized),
  }), mockData: false }
}

function adaptMockMaterial(material: MaterialDto): MaterialDetailDto {
  return { ...material, materialType: '模拟辅料', status: material.status as MaterialStatus }
}

async function materials(): Promise<{ items: MaterialDetailDto[]; mockData: boolean }> {
  if (runtimeConfig.authMode === 'mock') {
    const response = await syntheticList<MaterialDto>('/materials')
    return { items: response.items.map(adaptMockMaterial), mockData: true }
  }
  return { items: await apiRequest<MaterialDetailDto[]>('/v1/materials'), mockData: false }
}

async function createMaterial(input: MaterialInput | { materialCode: string; name: string; unit: string }) {
  if (runtimeConfig.authMode === 'mock') {
    const response = assertSynthetic(await apiRequest<SyntheticWrite<MaterialDto>>('/materials', {
      method: 'POST', body: JSON.stringify({ materialCode: input.materialCode, name: input.name, unit: input.unit }),
    }))
    return { item: adaptMockMaterial(response.item), mockData: true }
  }
  return { item: await apiRequest<MaterialDetailDto>('/v1/materials', {
    method: 'POST', body: JSON.stringify(input),
  }), mockData: false }
}

export const domainApi = {
  products,
  skus: async (filters: { field?: string | undefined; q?: string | undefined; exact?: boolean | undefined; status?: VariantStatus | undefined; productStatus?: ProductStatus | undefined; hasImage?: boolean | undefined; hasSupplier?: boolean | undefined; hasMaterial?: boolean | undefined; sort?: string | undefined; direction?: string | undefined; page?: number | undefined; size?: number | undefined } = {}) => {
    if (runtimeConfig.authMode === 'mock') {
      const response = await products(); const values = response.items.flatMap(product => product.variants.map(variant => ({ productId: product.id, productCode: product.productCode, productName: product.productName, productStatus: product.status, productType: product.productType, variant, businessAvailable: product.status === 'ACTIVE' && variant.status === 'ACTIVE', updatedAt: product.updatedAt })))
      return { items: values, totalCount: values.length, page: 0, size: values.length || 20 } as SkuPageDto
    }
    const search = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') search.set(key, String(value)) })
    return apiRequest<SkuPageDto>(`/v1/products/skus?${search}`)
  },
  product,
  productHistory: (id: string) => runtimeConfig.authMode === 'mock' ? Promise.resolve([] as ProductHistoryDto[]) : apiRequest<ProductHistoryDto[]>(`/v1/products/${id}/history`),
  exportSkus,
  createProduct,
  updateProduct: async (id: string, input: ProductInput) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock products cannot be edited')
    return apiRequest<ProductDetailDto>(`/v1/products/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },
  disableProduct: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock products cannot be disabled')
    const current = await product(id)
    return apiRequest<ProductDetailDto>(`/v1/products/${id}/disable`, { method: 'POST', body: JSON.stringify({ version: current.version }) })
  },
  batchUpdateSkus: (items: BatchVariantPatchInput[]) => apiRequest<ProductDetailDto[]>('/v1/products/skus/batch', { method: 'POST', body: JSON.stringify({ items }) }),
  suppliers: async (filters: { q?: string; status?: SupplierStatus; page?: number; size?: number } = {}) => {
    if (runtimeConfig.authMode === 'mock') return { items: [], totalCount: 0, page: 0, size: filters.size ?? 100 } as PageDto<SupplierDto>
    const search = new URLSearchParams()
    if (filters.q) search.set('q', filters.q)
    if (filters.status) search.set('status', filters.status)
    search.set('page', String(filters.page ?? 0)); search.set('size', String(filters.size ?? 100))
    return apiRequest<PageDto<SupplierDto>>(`/v1/suppliers?${search}`)
  },
  supplier: (id: string) => apiRequest<SupplierDto>(`/v1/suppliers/${id}`),
  createSupplier: (input: SupplierInput) => apiRequest<SupplierDto>('/v1/suppliers', { method: 'POST', body: JSON.stringify(input) }),
  updateSupplier: (id: string, input: SupplierInput) => apiRequest<SupplierDto>(`/v1/suppliers/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  disableSupplier: (id: string, version: number) => apiRequest<SupplierDto>(`/v1/suppliers/${id}/disable`, { method: 'POST', body: JSON.stringify({ version }) }),
  materialSupplierLinks: (id: string) => apiRequest<SupplierLinkDto[]>(`/v1/suppliers/material/${id}`),
  replaceMaterialSupplierLinks: (id: string, links: SupplierAssignmentDto[]) => apiRequest<SupplierLinkDto[]>(`/v1/suppliers/material/${id}`, { method: 'PUT', body: JSON.stringify({ links }) }),
  materials,
  createMaterial,
  material: async (id: string) => {
    if (runtimeConfig.authMode !== 'mock') return apiRequest<MaterialDetailDto>(`/v1/materials/${id}`)
    const item = (await materials()).items.find(value => value.id === id)
    if (!item) throw new Error('Material not found')
    return item
  },
  updateMaterial: async (id: string, input: MaterialInput) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock materials cannot be edited')
    return apiRequest<MaterialDetailDto>(`/v1/materials/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },
  disableMaterial: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock materials cannot be disabled')
    return apiRequest<MaterialDetailDto>(`/v1/materials/${id}/disable`, { method: 'POST' })
  },
  enableMaterial: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock materials cannot be enabled')
    return apiRequest<MaterialDetailDto>(`/v1/materials/${id}/enable`, { method: 'POST' })
  },
  productMaterialRelations: async (filter: { productId: string } | { variantId: string }) => {
    if (runtimeConfig.authMode === 'mock') return [] as ProductMaterialRelationDto[]
    const query = 'productId' in filter
      ? `productId=${encodeURIComponent(filter.productId)}`
      : `variantId=${encodeURIComponent(filter.variantId)}`
    return apiRequest<ProductMaterialRelationDto[]>(`/v1/product-material-relations?${query}`)
  },
  createProductMaterialRelation: async (input: ProductMaterialRelationInput) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock product material relations are read-only')
    return apiRequest<ProductMaterialRelationDto>('/v1/product-material-relations', {
      method: 'POST', body: JSON.stringify(input),
    })
  },
  updateProductMaterialRelation: async (id: string, input: ProductMaterialRelationInput) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock product material relations are read-only')
    return apiRequest<ProductMaterialRelationDto>(`/v1/product-material-relations/${id}`, {
      method: 'PUT', body: JSON.stringify(input),
    })
  },
  disableProductMaterialRelation: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock product material relations are read-only')
    return apiRequest<ProductMaterialRelationDto>(`/v1/product-material-relations/${id}/disable`, { method: 'POST' })
  },
  applyMaterialToAllVariants: async (input: Omit<ProductMaterialRelationInput, 'variantId' | 'enabled'>) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock product material relations are read-only')
    return apiRequest<ProductMaterialRelationDto[]>('/v1/product-material-relations/apply-to-all', {
      method: 'POST', body: JSON.stringify(input),
    })
  },
  balances: async (): Promise<{ items: InventoryBalanceViewDto[]; mockData: boolean }> => {
    if (runtimeConfig.authMode === 'mock') return syntheticList<InventoryBalanceDto>('/inventory/balances')
    return { items: await apiRequest<InventoryBalanceViewDto[]>('/v1/inventory/balances'), mockData: false }
  },
  ledger: async (materialId?: string): Promise<{ items: LedgerEntryDto[]; mockData: boolean }> => {
    if (runtimeConfig.authMode === 'mock') return syntheticList<LedgerEntryDto>('/inventory/ledger')
    const query = materialId ? `?materialId=${encodeURIComponent(materialId)}` : ''
    return { items: await apiRequest<LedgerEntryDto[]>(`/v1/inventory/transactions${query}`), mockData: false }
  },
  transact: async (input: InventoryCommandInput) => {
    if (runtimeConfig.authMode === 'mock') {
      return assertSynthetic(await apiRequest<SyntheticWrite<LedgerEntryDto> & { balance: InventoryBalanceDto }>('/inventory/transactions', {
        method: 'POST', body: JSON.stringify(input),
      }))
    }
    return apiRequest<{ item: LedgerEntryDto; balance: InventoryBalanceViewDto; replayed: boolean }>('/v1/inventory/transactions', {
      method: 'POST', body: JSON.stringify(input),
    })
  },
  updateSafetyStock: async (materialId: string, safetyStock: number) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock inventory safety stock is read-only')
    return apiRequest<InventoryBalanceViewDto>(`/v1/inventory/balances/${materialId}/safety-stock`, {
      method: 'PUT', body: JSON.stringify({ safetyStock }),
    })
  },
  candidates: async () => runtimeConfig.authMode === 'mock'
    ? syntheticList<SelectionCandidateDto & { rejectionReason?: string }>('/selection/candidates')
    : { items: await apiRequest<Array<SelectionCandidateDto & { rejectionReason?: string }>>('/v1/selection/candidates'), mockData: false },
  createCandidate: async (input: { asinOrUrl: string; title: string; score?: number }) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>('/selection/candidates', { method: 'POST', body: JSON.stringify(input) }))
    return { item: await apiRequest<SelectionCandidateDto & { rejectionReason?: string }>('/v1/selection/candidates', { method: 'POST', body: JSON.stringify(input) }), mockData: false }
  },
  candidateDetail: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}`))
    return { item: await apiRequest<CandidateDetailDto>(`/v1/selection/candidates/${id}`), mockData: false }
  },
  addCandidateNote: async (id: string, content: string) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}/notes`, { method: 'POST', body: JSON.stringify({ content }) }))
    return { item: await apiRequest<CandidateDetailDto>(`/v1/selection/candidates/${id}/notes`, { method: 'POST', body: JSON.stringify({ content }) }), mockData: false }
  },
  addCandidateEvidence: async (id: string, input: { name: string; url: string; description?: string }) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}/evidence`, { method: 'POST', body: JSON.stringify(input) }))
    return { item: await apiRequest<CandidateDetailDto>(`/v1/selection/candidates/${id}/evidence`, { method: 'POST', body: JSON.stringify(input) }), mockData: false }
  },
  updateCandidateEvaluation: async (id: string, input: { category?: string | undefined; price?: number | undefined; rating?: number | undefined; reviewCount?: number | undefined; owner?: string | undefined; tags?: string[] | undefined; selectionReason?: string | undefined }) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>(`/selection/candidates/${id}/evaluation`, { method: 'PUT', body: JSON.stringify(input) }))
    return { item: await apiRequest<SelectionCandidateDto & { rejectionReason?: string }>(`/v1/selection/candidates/${id}/evaluation`, { method: 'PUT', body: JSON.stringify(input) }), mockData: false }
  },
  transitionCandidate: async (id: string, input: { status: CandidateStatus; rejectionReason?: string }) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>(`/selection/candidates/${id}/transition`, { method: 'POST', body: JSON.stringify(input) }))
    return { item: await apiRequest<SelectionCandidateDto & { rejectionReason?: string }>(`/v1/selection/candidates/${id}/transition`, { method: 'POST', body: JSON.stringify(input) }), mockData: false }
  },
  events: async () => runtimeConfig.authMode === 'mock'
    ? syntheticList<WatchEventDto & { candidateId?: string }>('/watch/events')
    : { items: await apiRequest<Array<WatchEventDto & { candidateId?: string }>>('/v1/watch/events'), mockData: false },
  confirmEvent: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<SyntheticWrite<WatchEventDto & { candidateId?: string }> & { candidate: SelectionCandidateDto }>(`/watch/events/${id}/confirm`, { method: 'POST', body: '{}' }))
    return apiRequest<{ item: WatchEventDto & { candidateId?: string }; candidate: { id: string }; replayed: boolean }>(`/v1/watch/events/${id}/confirm`, { method: 'POST', body: '{}' })
  },
  watchStores: async () => runtimeConfig.authMode === 'mock'
    ? syntheticList<WatchStoreDto>('/watch/stores')
    : { items: await apiRequest<WatchStoreDto[]>('/v1/watch/stores'), mockData: false },
  createWatchStore: async (input: { storeKey: string; storeName: string; sourceUrl: string }) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock watch stores are fixed synthetic data')
    return apiRequest<WatchStoreDto>('/v1/watch/stores', { method: 'POST', body: JSON.stringify(input) })
  },
  runWatchStore: async (id: string, mode: WatchRunMode, asins: string[] = []) => {
    if (runtimeConfig.authMode === 'mock') return assertSynthetic(await apiRequest<{ item: WatchStoreDto; events: WatchEventDto[]; baselineChanged: boolean; mockData: boolean; source: 'SYNTHETIC' }>(`/watch/stores/${id}/runs`, { method: 'POST', body: JSON.stringify({ mode }) }))
    const status = mode === 'FAILED' ? 'FAILED' : 'SUCCEEDED'
    return apiRequest<{ item: WatchStoreDto; events: WatchEventDto[]; baselineChanged: boolean; replayed: boolean }>(`/v1/watch/stores/${id}/runs`, {
      method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID(), status, itemCount: asins.length,
        pageCount: mode === 'FAILED' ? 0 : 1, pagesComplete: mode === 'COMPLETE', asins }),
    })
  },
}
