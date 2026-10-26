import { apiRequest, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
export interface CostMaterial { materialId: string; quantity: number; usageUnit: string; conversion: number; perProducts: number; lossPercent: number; remark: string }
export interface CostLabor { operationId: string; price: number; basis: 'PIECE' | 'BOX' | 'BATCH'; times: number; perProducts: number; remark: string }
export interface CostInput { unit: string; baseQuantity: number; materials: CostMaterial[]; labor: CostLabor[]; noMaterialReason: string; noLaborReason: string }
export interface CostSnapshot { name: string; sku: string; input: CostInput; materials: { input: CostMaterial; name: string; unit: string; price: string | null; amount: string; error: string }[]; labor: { input: CostLabor; name: string; amount: string; error: string }[]; materialTotal: string; laborTotal: string; total: string; sourceHash: string; errors: string[] }
export interface CostVersion { id: string; variantId: string; versionNumber: number; status: string; input: CostInput; snapshot: CostSnapshot; revision: number; reason: string; createdAt: string; publishedAt: string | null; stale: boolean }
export interface CostRow { sku: { id: string; productId: string; name: string; code: string; productName: string; imageId: string | null }; current: CostVersion | null; draft: CostVersion | null; stale: boolean }
export interface CostOperation { id: string; name: string; enabled: boolean; version: number }
export interface MaterialPrice { netPrice: string; unit: string; source: string; referenceId: string | null; reason: string; version: number }
const base = '/v1/product-costs'
const body = (value: unknown) => JSON.stringify(value)
export const costsApi = {
  list: (q: string, state: string, page: number, size: number, productId = '') => apiRequest<{ items: CostRow[]; totalCount: number }>(`${base}?${new URLSearchParams({ q, state, page: String(page), size: String(size), productId })}`),
  initial: (id: string) => apiRequest<CostInput>(`${base}/skus/${id}/initial`),
  preview: (id: string, input: CostInput) => apiRequest<CostSnapshot>(`${base}/skus/${id}/preview`, { method: 'POST', body: body(input) }),
  save: (id: string, input: CostInput, sourceHash: string, revision: number | null, reason: string) => apiRequest<CostVersion>(`${base}/skus/${id}/draft`, { method: 'POST', body: body({ input, sourceHash, revision, reason }) }),
  versions: (id: string) => apiRequest<CostVersion[]>(`${base}/skus/${id}/versions`),
  history: (id: string) => apiRequest<import('./dashboard').DashboardHistoryEvent[]>(`${base}/skus/${id}/history`),
  publish: (v: CostVersion, reason: string) => apiRequest<CostVersion>(`${base}/versions/${v.id}/publish`, { method: 'POST', body: body({ revision: v.revision, reason }) }),
  remove: (v: CostVersion) => apiRequest<void>(`${base}/versions/${v.id}?revision=${v.revision}`, { method: 'DELETE' }),
  operations: () => apiRequest<CostOperation[]>(`${base}/operations`),
  operation: (name: string, enabled: boolean, version = -1, id?: string) => apiRequest<CostOperation>(`${base}/operations${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: body({ name, enabled, version }) }),
  price: (id: string) => apiRequest<MaterialPrice | null>(`${base}/materials/${id}/price`),
  savePrice: (id: string, input: MaterialPrice & { confirmedNet: boolean }) => apiRequest<MaterialPrice>(`${base}/materials/${id}/price`, { method: 'PUT', body: body(input) }),
  purchases: (id: string) => apiRequest<{ id: string; orderNumber: string; unit: string; price: string }[]>(`${base}/materials/${id}/purchases`),
  async export(id: string) {
    const token = getSessionToken()
    const response = await fetch(`${runtimeConfig.apiBaseUrl}${base}/versions/${id}/export`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    if (!response.ok) { if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT)); throw new Error('成本导出失败，请检查权限后重试。') }
    return response.blob()
  },
}
