import { apiRequest, assertSyntheticEnvelope } from './client'
import type { CandidateDetailDto, InventoryBalanceDto, MaterialDto, ProductDto, SelectionCandidateDto, SyntheticList, WatchEventDto, WatchStoreDto } from './resources'
import { runtimeConfig } from '@/config/runtime'

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED'
export type VariantStatus = 'ACTIVE' | 'DISABLED'
export interface VariantAttributeDto { attributeCode: string; attributeValue: string; displayOrder: number }
export interface ProductVariantDto {
  id?: string
  skuCode: string
  variantName: string
  barcode?: string
  material?: string
  specification?: string
  itemDimensions?: string
  packageDimensions?: string
  packageWeight?: number
  packageWeightUnit?: 'G' | 'KG' | 'OZ' | 'LB'
  status: VariantStatus
  attributes: VariantAttributeDto[]
}
export interface ProductDetailDto {
  id: string
  productCode: string
  nameZh: string
  nameEn?: string
  brand?: string
  internalCategory?: string
  productType?: string
  description?: string
  status: ProductStatus
  variants: ProductVariantDto[]
  skuCount: number
  createdAt?: string
  updatedAt?: string
}
export interface ProductInput {
  productCode: string
  nameZh: string
  nameEn?: string
  brand?: string
  internalCategory?: string
  productType?: string
  description?: string
  status: Exclude<ProductStatus, 'DISABLED'>
  variants: ProductVariantDto[]
}
export type MaterialStatus = 'ACTIVE' | 'DISABLED'
export interface MaterialDetailDto {
  id: string
  materialCode: string
  name: string
  materialType: string
  specification?: string
  unit: string
  imageUrl?: string
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
    nameZh: product.name,
    status: product.status as ProductStatus,
    skuCount: product.skuCount ?? product.skus.length,
    variants: product.skus.map((sku, index) => ({
      skuCode: sku.skuCode,
      variantName: sku.skuCode,
      status: (sku.status ?? 'ACTIVE') as VariantStatus,
      attributes: [{ attributeCode: 'mock_sequence', attributeValue: String(index + 1), displayOrder: 0 }],
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

type LegacyProductInput = { productCode: string; name: string; skuCodes: string[] }

async function createProduct(input: ProductInput | LegacyProductInput): Promise<{ item: ProductDetailDto; mockData: boolean }> {
  const normalized: ProductInput = 'name' in input ? {
    productCode: input.productCode,
    nameZh: input.name,
    status: 'ACTIVE',
    variants: input.skuCodes.map((skuCode, index) => ({ skuCode, variantName: skuCode, status: 'ACTIVE',
      attributes: [{ attributeCode: 'mock_sequence', attributeValue: String(index + 1), displayOrder: 0 }] })),
  } : input
  if (runtimeConfig.authMode === 'mock') {
    const response = assertSynthetic(await apiRequest<SyntheticWrite<MockProduct>>('/products', {
      method: 'POST', body: JSON.stringify({
        productCode: normalized.productCode,
        name: normalized.nameZh,
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
  product,
  createProduct,
  updateProduct: async (id: string, input: ProductInput) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock products cannot be edited')
    return apiRequest<ProductDetailDto>(`/v1/products/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },
  disableProduct: async (id: string) => {
    if (runtimeConfig.authMode === 'mock') throw new Error('Mock products cannot be disabled')
    return apiRequest<ProductDetailDto>(`/v1/products/${id}/disable`, { method: 'POST' })
  },
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
  candidates: () => syntheticList<SelectionCandidateDto & { rejectionReason?: string }>('/selection/candidates'),
  createCandidate: async (input: { asinOrUrl: string; title: string; score?: number }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>('/selection/candidates', { method: 'POST', body: JSON.stringify(input) })),
  candidateDetail: async (id: string) =>
    assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}`)),
  addCandidateNote: async (id: string, content: string) =>
    assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}/notes`, { method: 'POST', body: JSON.stringify({ content }) })),
  addCandidateEvidence: async (id: string, input: { name: string; url: string; description?: string }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<CandidateDetailDto>>(`/selection/candidates/${id}/evidence`, { method: 'POST', body: JSON.stringify(input) })),
  updateCandidateEvaluation: async (id: string, input: { category?: string | undefined; price?: number | undefined; rating?: number | undefined; reviewCount?: number | undefined; owner?: string | undefined; tags?: string[] | undefined; selectionReason?: string | undefined }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>(`/selection/candidates/${id}/evaluation`, { method: 'PUT', body: JSON.stringify(input) })),
  transitionCandidate: async (id: string, input: { status: CandidateStatus; rejectionReason?: string }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>(`/selection/candidates/${id}/transition`, { method: 'POST', body: JSON.stringify(input) })),
  events: () => syntheticList<WatchEventDto & { candidateId?: string }>('/watch/events'),
  confirmEvent: async (id: string) =>
    assertSynthetic(await apiRequest<SyntheticWrite<WatchEventDto & { candidateId?: string }> & { candidate: SelectionCandidateDto }>(`/watch/events/${id}/confirm`, { method: 'POST', body: '{}' })),
  watchStores: () => syntheticList<WatchStoreDto>('/watch/stores'),
  runWatchStore: async (id: string, mode: WatchRunMode) =>
    assertSynthetic(await apiRequest<{ item: WatchStoreDto; events: WatchEventDto[]; baselineChanged: boolean; mockData: boolean; source: 'SYNTHETIC' }>(`/watch/stores/${id}/runs`, { method: 'POST', body: JSON.stringify({ mode }) })),
}
