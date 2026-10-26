import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'

export interface LabelRow { key: 'SKU' | 'QUANTITY' | 'BOXES' | 'PURCHASER'; label: string; height: number; fontSize: number }
export interface LabelConfig { singlePage?: boolean; title: string; titleSize: number; color: boolean; defaultDestination: string; rows: LabelRow[] }
export interface LabelTemplateInput { name: string; config: LabelConfig; enabled: boolean; defaultTemplate: boolean; version?: number }
export interface LabelTemplate extends LabelTemplateInput { id: string; customerId: string; version: number }
export interface LabelEntry {
  shipmentId: string; shipmentVersion: number; templateId: string; templateVersion: number
  productName: string; customerCode: string; purchaser: string; contractNo: string; destination: string; carrier: string; trackingNo: string
}
export interface LabelDraft { orderReference?: string; shipmentId: string; shipmentVersion: number; customerId: string; customerName: string; productName: string; customerCode: string; quantity: number; boxes: number; unit: string; templates: LabelTemplate[]; previous: LabelEntry | null }
export interface LabelExport { id: string; pages: number; createdAt: string }
export interface LabelBatch { items: LabelEntry[]; requestKey: string }
export const defaultLabelConfig = (): LabelConfig => ({
  title: '客户 & 供应商发货外包装唛头表', titleSize: 14, color: false, defaultDestination: '', singlePage: false,
  rows: [{ key: 'SKU', label: 'SKU编码\n（客户产品编码）', height: 20, fontSize: 18 },
    { key: 'QUANTITY', label: '发货数量\n（套数）', height: 14, fontSize: 20 },
    { key: 'BOXES', label: '发货件数\n（箱数）', height: 14, fontSize: 20 },
    { key: 'PURCHASER', label: '采购人', height: 14, fontSize: 18 }],
})
async function pdf(path: string, body?: unknown): Promise<Blob> {
  const headers = new Headers({ Accept: 'application/pdf' })
  const token = getSessionToken(); if (token) headers.set('Authorization', `Bearer ${token}`)
  if (body) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${runtimeConfig.apiBaseUrl}/v1/carton-labels${path}`, { method: body ? 'POST' : 'GET', headers, ...(body ? { body: JSON.stringify(body) } : {}) })
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    const error = await response.json().catch(() => ({}))
    throw new ApiError(response.status, error.code ?? 'LABEL_PDF_FAILED', error.message ?? error.detail ?? 'PDF 加载失败')
  }
  return response.blob()
}
export const cartonLabelsApi = {
  templates: (id: string) => apiRequest<LabelTemplate[]>(`/v1/carton-labels/customers/${encodeURIComponent(id)}/templates`),
  saveTemplate: (customer: string, id: string | undefined, input: LabelTemplateInput) => apiRequest<LabelTemplate>(`/v1/carton-labels/customers/${encodeURIComponent(customer)}/templates${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
  templatePreview: (config: LabelConfig) => pdf('/template-preview', config),
  draft: (id: string) => apiRequest<LabelDraft>(`/v1/carton-labels/shipments/${encodeURIComponent(id)}/draft`),
  history: (id: string) => apiRequest<LabelExport[]>(`/v1/carton-labels/shipments/${encodeURIComponent(id)}/history`),
  preview: (batch: LabelBatch) => pdf('/preview', batch),
  generate: (batch: LabelBatch) => apiRequest<LabelExport>('/v1/carton-labels/exports', { method: 'POST', body: JSON.stringify(batch) }),
  download: (id: string) => pdf(`/exports/${encodeURIComponent(id)}/pdf`),
}
export function downloadLabelBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = name; a.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
