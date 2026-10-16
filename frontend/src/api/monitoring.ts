import { apiRequest } from './client'

export interface MonitorFields { title?: string | null; brand?: string | null; imageUrl?: string | null; bullets?: string[] | null; price?: number | null; currency?: string | null; availability?: 'AVAILABLE' | 'UNAVAILABLE' | null }
export type FieldName = keyof MonitorFields
export interface MonitorTarget { id: string; marketplace: string; asin: string; enabled: boolean; ownerId: string | null; ownerName: string | null; tags: string[]; remark: string; version: number; fields: MonitorFields; source: string | null; observedAt: string | null; lastStatus: string; lastReason: string | null; lastAttemptAt: string | null; createdAt: string; followed: boolean }
export interface MonitorSnapshot { id: string; targetId: string; requestId: string; observedAt: string; recordedAt: string; source: string; quality: string; decision: string; reason: string; fields: MonitorFields; changes: { field: FieldName; before: unknown; after: unknown }[] }
export interface MonitorPage<T> { items: T[]; total: number; activeCount: number; limit: number }
export interface MonitorResult { asin: string; id: string | null; status: string; message: string }
export interface MonitorDetail { target: MonitorTarget; snapshots: MonitorSnapshot[] }
export interface MonitorPreferences { followed: string[]; columns: string[] }
export interface SnapshotInput { requestId: string; version: number; observedAt: string; source: string; quality: string; fields: MonitorFields; reason: string }
const root = '/v1/product-monitoring'
export const monitoringApi = {
  list: (filters: Record<string, string | number | boolean>) => apiRequest<MonitorPage<MonitorTarget>>(`${root}?${new URLSearchParams(Object.entries(filters).map(([k, v]) => [k, String(v)]))}`),
  create: (input: { requestId: string; asins: string[]; tags: string[]; remark: string }) => apiRequest<MonitorResult[]>(root, { method: 'POST', body: JSON.stringify(input) }),
  detail: (id: string) => apiRequest<MonitorDetail>(`${root}/${encodeURIComponent(id)}`),
  batch: (targets: { id: string; version: number }[], action: string, tags: string[] = []) => apiRequest<MonitorResult[]>(`${root}/batch`, { method: 'POST', body: JSON.stringify({ targets, action, tags }) }),
  history: (id: string, page: number) => apiRequest<MonitorPage<MonitorSnapshot>>(`${root}/${encodeURIComponent(id)}/snapshots?page=${page}&size=20`),
  snapshot: (id: string, input: SnapshotInput) => apiRequest<MonitorSnapshot>(`${root}/${encodeURIComponent(id)}/snapshots`, { method: 'POST', body: JSON.stringify(input) }),
  follow: (id: string, followed: boolean) => apiRequest<MonitorPreferences>(`${root}/${encodeURIComponent(id)}/follow`, { method: 'PUT', body: JSON.stringify({ followed }) }),
  preferences: () => apiRequest<MonitorPreferences>(`${root}/preferences`),
  columns: (columns: string[]) => apiRequest<MonitorPreferences>(`${root}/preferences`, { method: 'PUT', body: JSON.stringify({ columns }) }),
}
