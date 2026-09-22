import { apiRequest, assertSyntheticEnvelope } from './client'

export interface SyntheticList<T> {
  items: T[]
  mockData: boolean
  source: 'SYNTHETIC'
}

export interface ProductDto { id: string; productCode: string; name: string; status: string; skuCount?: number }
export interface MaterialDto { id: string; materialCode: string; name: string; unit: string; status: string }
export interface InventoryBalanceDto { warehouseCode: string; materialCode: string; materialName: string; quantity: number; unit: string }
export interface SelectionCandidateDto { id: string; asin: string; title: string; status: string; score?: number }
export interface WatchEventDto { id: string; storeName: string; asin: string; title?: string; detectedAt: string; status: string }

export type ResourceDto = ProductDto | MaterialDto | InventoryBalanceDto | SelectionCandidateDto | WatchEventDto

export async function loadResource<T extends ResourceDto>(path: string): Promise<SyntheticList<T>> {
  const response = await apiRequest<SyntheticList<T>>(path)
  assertSyntheticEnvelope(response)
  if (!Array.isArray(response.items)) throw new Error('Synthetic resource response must contain items')
  return response
}
