import { apiRequest } from './client'
import type { ProductDetailDto, ProductVariantInput } from './domain'

export interface ProductStockBalance { variantId: string; quantity: number; updatedAt: string }
export interface ProductStockEntry { id: string; requestId: string; variantId: string; type: string; quantity: number; signedQuantity: number; balanceAfter: number; reason: string; actorLoginName: string; occurredAt: string }
export interface ProductPage { items: ProductDetailDto[]; totalCount: number; matchingSkuCount: number; matchingVariantIds: string[]; page: number; size: number; balances: ProductStockBalance[] }
export interface ProductStockCommand { requestId: string; type: string; quantity: number; reason: string }
export const productManagementApi = {
  updateSku: (productId: string, variant: ProductVariantInput) => apiRequest<ProductDetailDto>(`/v1/products/${encodeURIComponent(productId)}/skus/${encodeURIComponent(variant.id!)}`, { method: 'PUT', body: JSON.stringify(variant) }),
  skuHistory: (productId: string, id: string) => apiRequest<import('./domain').ProductHistoryDto[]>(`/v1/products/${encodeURIComponent(productId)}/skus/${encodeURIComponent(id)}/history`),
  page: (filters: Record<string, string | number | boolean>) => apiRequest<ProductPage>(`/v1/products/page?${new URLSearchParams(Object.entries(filters).map(([k,v])=>[k,String(v)]))}`),
  balances: (ids: string[]) => apiRequest<ProductStockBalance[]>(`/v1/products/inventory/balances?variantIds=${ids.map(encodeURIComponent).join(',')}`),
  stockHistory: (id: string, page: number) => apiRequest<{ items: ProductStockEntry[]; total: number; page: number; size: number }>(`/v1/products/inventory/${encodeURIComponent(id)}/transactions?page=${page}&size=20`),
  transact: (id: string, value: ProductStockCommand) => apiRequest<{ item: ProductStockEntry; replayed: boolean }>(`/v1/products/inventory/${encodeURIComponent(id)}/transactions`, { method: 'POST', body: JSON.stringify(value) }),
}
