import { apiRequest } from './client'
export type DevelopmentStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'VOID'
export interface DevelopmentFields {
  name: string; developer: string | null; buyer: string | null; stars: number
  startDate: string | null; endDate: string | null; launchDate: string | null
  productName: string | null; brand: string | null; category: string | null; plannedSku: string | null; material: string | null
  purchaseCost: number | null; weight: number | null; length: number | null; width: number | null; height: number | null
  supplyNotes: string | null; sampleNotes: string | null; researchNotes: string | null; remark: string | null; linkedProductId: string | null
}
export interface DevelopmentItem {
  id: string; number: string; status: DevelopmentStatus; version: number; fields: DevelopmentFields
  source: { candidateId: string; asin: string; title: string; category: string | null; score: number; referencePrice: number | null; sourceUrl: string; selectionReason: string | null }
  voidReason: string | null; createdAt: string; updatedAt: string
}
export interface DevelopmentPage { items: DevelopmentItem[]; total: number; page: number; size: number }
export interface DevelopmentDetail {
  item: DevelopmentItem
  history: Array<{ id: string; action: string; actorLoginName: string; actorDisplayName: string | null; occurredAt: string }>
  product: { id: string; productCode: string; name: string; active: boolean } | null
}
const base = '/v1/product-development'
export const developmentApi = {
  list: (q: string, status: DevelopmentStatus | '', page: number, size: number) => apiRequest<DevelopmentPage>(base + '?' + new URLSearchParams({ q, ...(status ? { status } : {}), page: String(page), size: String(size) })),
  detail: (id: string) => apiRequest<DevelopmentDetail>(base + '/' + id),
  update: (id: string, version: number, fields: DevelopmentFields) => apiRequest<DevelopmentItem>(base + '/' + id, { method: 'PUT', body: JSON.stringify({ version, fields }) }),
  transition: (id: string, version: number, target: DevelopmentStatus, reason?: string) => apiRequest<DevelopmentItem>(base + '/' + id + '/transition', { method: 'POST', body: JSON.stringify({ version, target, reason }) }),
}
