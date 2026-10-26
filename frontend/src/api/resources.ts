import { apiRequest, assertSyntheticEnvelope } from './client'

export interface SyntheticList<T> {
  items: T[]
  mockData: boolean
  source: 'SYNTHETIC'
}

export interface ProductDto { id: string; productCode: string; name: string; status: string; skuCount?: number }
export interface MaterialDto { id: string; materialCode: string; name: string; unit: string; status: string }
export interface InventoryBalanceDto { warehouseCode: string; materialCode: string; materialName: string; quantity: number; unit: string }
export interface SelectionCandidateDto {
  version?: number
  weightedScore?: number | null
  id: string
  asin: string
  title: string
  status: string
  score?: number
  sourceUrl?: string
  category?: string
  price?: number
  rating?: number
  reviewCount?: number
  owner?: string
  tags?: string[]
  selectionReason?: string
  source?: 'MANUAL' | 'WATCH'
  sourceStore?: string
  discoveredAt?: string
  sourceRunId?: string
  sourceEventId?: string
}
export interface CandidateNoteDto { id: string; content: string; authorId: string; authorName: string; authorRole: string; createdAt: string }
export interface CandidateEvidenceDto { id: string; name: string; url: string; description?: string; addedById: string; addedByName: string; addedByRole: string; createdAt: string }
export interface CandidateDetailDto { candidate: SelectionCandidateDto & { rejectionReason?: string }; notes: CandidateNoteDto[]; evidence: CandidateEvidenceDto[] }
export interface WatchEventDto { id: string; storeId?: string; storeName: string; asin: string; title?: string; detectedAt: string; sourceRunId?: string; status: string; candidateId?: string; version?: number; ignoreReason?: string }
export interface WatchStoreDto {
  version?: number
  owner?: string
  remark?: string
  createdAt?: string
  id: string
  storeKey?: string
  storeName: string
  sourceUrl: string
  status: string
  baselineRunId?: string
  baselineAsinCount: number
  lastRunId?: string
  lastRunStatus?: 'SUCCEEDED' | 'FAILED'
  lastIntegrityStatus?: 'COMPLETE' | 'INCOMPLETE' | 'FAILED'
  lastDecision?: 'PROMOTED' | 'REJECTED_KEEP_PREVIOUS'
  lastReasonCode?: string
  lastRunAt?: string
}

export type ResourceDto = ProductDto | MaterialDto | InventoryBalanceDto | SelectionCandidateDto | WatchEventDto | WatchStoreDto

export async function loadResource<T extends ResourceDto>(path: string): Promise<SyntheticList<T>> {
  const response = await apiRequest<SyntheticList<T>>(path)
  assertSyntheticEnvelope(response)
  if (!Array.isArray(response.items)) throw new Error('Synthetic resource response must contain items')
  return response
}
