import { ApiError, apiRequest, assertSyntheticEnvelope, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'

export interface DashboardSummaryItem { label: string; value: number; note: string }
export interface DashboardSummary { mockData: boolean; source: 'SYNTHETIC'; generatedAt: string; items: DashboardSummaryItem[] }
export type DashboardKind = 'today-shipments' | 'pending-shipments' | 'pending-products'
export type WorklistKind = DashboardKind
export interface ShipmentDocument { id: string; kind: 'LABEL' | 'CARTON_LABEL'; fileName: string; byteSize: number; uploadedAt: string }
export type PendingProductStatus = 'PENDING' | 'COMPLETED' | 'ARCHIVED'
export interface TodayShipment {
  id: string; shipDate: string; productId: string; productCode: string; productName: string; imageId?: string
  quantity: number; quantityUnit: 'SET'; boxCount: number; remark?: string; latestShipAt: string
  status: 'ACTIVE' | 'ARCHIVED' | 'SHIPPED'; sourcePendingShipmentId?: string; version: number; createdAt: string; updatedAt: string
}
export interface PendingShipment {
  id: string; plannedShipDate: string; productId: string; productCode: string; productName: string; imageId?: string
  quantity: number; quantityUnit: 'SET'; boxCount: number; remark?: string; latestShipAt: string
  status: 'PENDING' | 'TRANSFERRED_TO_TODAY' | 'ARCHIVED'; version: number; createdAt: string; updatedAt: string
}
export interface PendingProduct {
  id: string; productName: string; imageId?: string; expectedQuantity: number; quantityUnit: 'SET'; remark?: string
  expectedCompletionDate: string; status: PendingProductStatus; version: number; createdAt: string; updatedAt: string
}
export type DashboardItem = TodayShipment | PendingShipment | PendingProduct
export interface ShipmentDispatch {
  id: string; todayShipmentId: string; productId: string; productCode: string; productName: string; skuCodes: string
  quantity: number; boxCount: number; remark?: string; plannedDate: string; businessDate: string; dispatchedAt: string; actorName: string
}
export interface DispatchTotals { records: number; quantity: number; boxCount: number }
export interface ShipmentStatistics {
  summary: DispatchTotals & { products: number }
  daily: Array<DispatchTotals & { date: string }>
  products: Array<DispatchTotals & { productId: string; productCode: string; productName: string }>
  details: Page<ShipmentDispatch>
}
export interface HomeSlice<T> { items: T[]; totalCount: number; truncated: boolean; businessDate: string; generatedAt: string }
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface ImageReceipt { id: string; purpose: 'SHIPMENT' | 'PENDING_PRODUCT'; sourceFileName: string; mediaType: string; byteSize: number; width: number; height: number; sha256: string }
export interface DashboardHistoryEvent { id: string; action: string; actorLoginName: string; actorDisplayName: string; beforeValue?: unknown; afterValue?: unknown; detail?: string; occurredAt: string }
export interface ShipmentInput { date: string; productId: string; imageId?: string | undefined; quantity: number; boxCount: number; remark?: string | undefined; latestShipTime: string }
export interface PendingProductInput { productName: string; imageId?: string | undefined; expectedQuantity: number; remark?: string | undefined; expectedCompletionDate: string }

export async function loadDashboardSummary(): Promise<DashboardSummary> {
  const summary = await apiRequest<DashboardSummary>('/dashboard/summary')
  assertSyntheticEnvelope(summary)
  return summary
}

async function raw(path: string, init: RequestInit): Promise<Response> {
  const token = getSessionToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? `请求失败（${response.status}）`)
  }
  return response
}
function key() { return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}` }
function qs(filters: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(filters).forEach(([name, value]) => { if (value !== undefined && value !== '') search.set(name, String(value)) })
  return search.size ? `?${search}` : ''
}
export const dashboardApi = {
  dispatch: (id: string, version: number, requestKey: string) => apiRequest<ShipmentDispatch>(`/v1/shipping/today-shipments/${encodeURIComponent(id)}/dispatch`, { method: 'POST', headers: { 'Idempotency-Key': requestKey }, body: JSON.stringify({ version }) }),
  statistics: (filters: { from: string; to: string; keyword: string; page: number; size: number }) => apiRequest<ShipmentStatistics>(`/v1/shipping/statistics${qs(filters)}`),
  documents: async (scope: string, id: string, kind: ShipmentDocument['kind']) => (await raw(`/v1/shipment-documents/${scope}/${encodeURIComponent(id)}?kind=${kind}`, { method: 'GET' })).json() as Promise<ShipmentDocument[]>,
  uploadDocument: async (id: string, kind: ShipmentDocument['kind'], file: File) => {
    const body = new FormData(); body.set('kind', kind); body.set('file', file)
    return (await raw(`/v1/shipment-documents/today-shipments/${encodeURIComponent(id)}`, { method: 'POST', body })).json() as Promise<ShipmentDocument>
  },
  downloadDocument: async (scope: string, id: string, document: ShipmentDocument) => {
    const response = await raw(`/v1/shipment-documents/${scope}/${encodeURIComponent(id)}/${encodeURIComponent(document.id)}/download`, { method: 'GET' })
    const url = URL.createObjectURL(await response.blob()); const link = window.document.createElement('a')
    link.href = url; link.download = document.fileName; window.document.body.append(link); link.click(); link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  home: <T extends DashboardItem>(kind: WorklistKind) => apiRequest<HomeSlice<T>>(`/v1/dashboard/${kind}`),
  page: <T extends DashboardItem>(kind: WorklistKind, filters: Record<string, string | number | undefined>) => apiRequest<Page<T>>(`/v1/dashboard-maintenance/${kind}${qs(filters)}`),
  history: (kind: WorklistKind, id: string) => apiRequest<DashboardHistoryEvent[]>(`/v1/dashboard-maintenance/${kind}/${id}/history`),
  createShipment: (kind: Exclude<WorklistKind, 'pending-products'>, input: ShipmentInput) => apiRequest<TodayShipment | PendingShipment>(`/v1/dashboard-maintenance/${kind}`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify(input) }),
  updateShipment: (kind: Exclude<WorklistKind, 'pending-products'>, id: string, input: ShipmentInput, version: number, correctionReason?: string) => apiRequest<TodayShipment | PendingShipment>(`/v1/dashboard-maintenance/${kind}/${id}`, { method: 'PATCH', body: JSON.stringify({ shipment: input, version, correctionReason }) }),
  createPendingProduct: (input: PendingProductInput) => apiRequest<PendingProduct>('/v1/dashboard-maintenance/pending-products', { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify(input) }),
  updatePendingProduct: (id: string, input: PendingProductInput, version: number) => apiRequest<PendingProduct>(`/v1/dashboard-maintenance/pending-products/${id}`, { method: 'PATCH', body: JSON.stringify({ product: input, version }) }),
  archive: (kind: WorklistKind, id: string, version: number, reason: string) => apiRequest<DashboardItem>(`/v1/dashboard-maintenance/${kind}/${id}/archive`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify({ version, reason }) }),
  completePendingProduct: (id: string, version: number) => apiRequest<PendingProduct>(`/v1/dashboard-maintenance/pending-products/${id}/complete`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify({ version }) }),
  transferToToday: (id: string, input: { quantity: number; boxCount: number; remark?: string | undefined; latestShipTime: string; imageId?: string | undefined; version: number }) => apiRequest<TodayShipment>(`/v1/dashboard-maintenance/pending-shipments/${id}/transfer-to-today`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify(input) }),
  transferToPending: (id: string, version: number, reason: string) => apiRequest<PendingShipment>(`/v1/dashboard-maintenance/today-shipments/${id}/transfer-to-pending`, { method: 'POST', headers: { 'Idempotency-Key': key() }, body: JSON.stringify({ version, reason }) }),
  uploadImage: async (purpose: ImageReceipt['purpose'], file: File) => { const body = new FormData(); body.set('purpose', purpose); body.set('file', file); return (await raw('/v1/dashboard-images', { method: 'POST', body })).json() as Promise<ImageReceipt> },
  imageBlob: async (id: string, variant: 'thumbnail' | 'original' = 'thumbnail') => (await raw(`/v1/dashboard-images/${id}/${variant}`, { method: 'GET' })).blob(),
}
