import { apiRequest, assertSyntheticEnvelope } from './client'
import type { InventoryBalanceDto, MaterialDto, ProductDto, SelectionCandidateDto, SyntheticList, WatchEventDto } from './resources'

export interface SkuDto { skuCode: string }
export interface ProductDetailDto extends ProductDto { skus: SkuDto[] }
export interface LedgerEntryDto { id: string; requestId: string; materialCode: string; type: InventoryTransactionType; quantity: number; reason: string; occurredAt: string }
export type InventoryTransactionType = 'OPENING' | 'INBOUND' | 'OUTBOUND' | 'ADJUSTMENT'
export type CandidateStatus = 'NEW' | 'REVIEWING' | 'APPROVED' | 'REJECTED'

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

export const domainApi = {
  products: () => syntheticList<ProductDetailDto>('/products'),
  createProduct: async (input: { productCode: string; name: string; skuCodes: string[] }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<ProductDetailDto>>('/products', { method: 'POST', body: JSON.stringify(input) })),
  materials: () => syntheticList<MaterialDto>('/materials'),
  createMaterial: async (input: { materialCode: string; name: string; unit: string }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<MaterialDto>>('/materials', { method: 'POST', body: JSON.stringify(input) })),
  balances: () => syntheticList<InventoryBalanceDto>('/inventory/balances'),
  ledger: () => syntheticList<LedgerEntryDto>('/inventory/ledger'),
  transact: async (input: { requestId: string; materialCode: string; type: InventoryTransactionType; quantity: number; reason: string }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<LedgerEntryDto> & { balance: InventoryBalanceDto }>('/inventory/transactions', { method: 'POST', body: JSON.stringify(input) })),
  candidates: () => syntheticList<SelectionCandidateDto & { rejectionReason?: string }>('/selection/candidates'),
  createCandidate: async (input: { asinOrUrl: string; title: string; score?: number }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>('/selection/candidates', { method: 'POST', body: JSON.stringify(input) })),
  transitionCandidate: async (id: string, input: { status: CandidateStatus; rejectionReason?: string }) =>
    assertSynthetic(await apiRequest<SyntheticWrite<SelectionCandidateDto & { rejectionReason?: string }>>(`/selection/candidates/${id}/transition`, { method: 'POST', body: JSON.stringify(input) })),
  events: () => syntheticList<WatchEventDto & { candidateId?: string }>('/watch/events'),
  confirmEvent: async (id: string) =>
    assertSynthetic(await apiRequest<SyntheticWrite<WatchEventDto & { candidateId?: string }> & { candidate: SelectionCandidateDto }>(`/watch/events/${id}/confirm`, { method: 'POST', body: '{}' })),
}
