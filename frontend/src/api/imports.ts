import { runtimeConfig } from '@/config/runtime'

import { ApiError, SESSION_EXPIRED_EVENT, SESSION_TOKEN_KEY, apiRequest } from './client'

export const activeImportTypes = [
  'PRODUCTS',
  'VARIANTS',
  'AUXILIARY_MATERIALS',
  'PRODUCT_AUXILIARY_MATERIALS',
  'AUXILIARY_MATERIAL_OPENING_INVENTORY',
] as const

export type ImportType = typeof activeImportTypes[number]
export type ImportStatus = 'QUEUED' | 'VALIDATING' | 'VALIDATED' | 'COMMITTING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface ImportBatch {
  id: string
  importType: ImportType
  templateVersion: string
  status: ImportStatus
  phase: string
  fileHash: string
  totalRows: number
  processedRows: number
  percent: number
  errorCount: number
  warningCount: number
  commitAllowed: boolean
  previewVersion?: string | null
  previewExpiresAt?: string | null
  revalidationOfBatchId?: string | null
  failureCode?: string | null
  createdAt: string
  updatedAt: string
}

export interface ImportIssue {
  id: string
  severity: string
  blocking: boolean
  scope: string
  sheet?: string | null
  row?: number | null
  field?: string | null
  code: string
  reason: string
  rawValue?: string | null
  suggestion?: string | null
}

export interface ImportPreviewRow {
  id: string
  sheet: string
  row: number
  businessKey: string
  action?: string | null
  data: Record<string, string>
}

export interface ImportPreview {
  batchId: string
  previewVersion: string
  expiresAt: string
  commitAllowed: boolean
  summary: Record<string, number>
  rows: ImportPreviewRow[]
}

export interface ImportReceipt { batch: ImportBatch; replayed: boolean }
export interface ImportResult {
  batch: ImportBatch
  createdRows: number
  updatedRows: number
  unchangedRows: number
  writtenRows: number
  committedAt?: string | null
  message: string
}

function idempotencyKey(operation: string): string {
  return `${operation}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function multipart<T>(path: string, body: FormData, key: string): Promise<T> {
  const headers = new Headers({ Accept: 'application/json', 'Idempotency-Key': key })
  const token = window.localStorage.getItem(SESSION_TOKEN_KEY)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, { method: 'POST', headers, body })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? `请求失败（${response.status}）`)
  }
  return response.json() as Promise<T>
}

async function download(type: ImportType): Promise<void> {
  const headers = new Headers()
  const token = window.localStorage.getItem(SESSION_TOKEN_KEY)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}/v1/import-templates/${type}`, { headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? `请求失败（${response.status}）`)
  }
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${type.toLowerCase()}_v1.0.xlsx`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

export const importsApi = {
  list: () => apiRequest<ImportBatch[]>('/v1/import-batches'),
  get: (id: string) => apiRequest<ImportBatch>(`/v1/import-batches/${encodeURIComponent(id)}`),
  issues: (id: string) => apiRequest<ImportIssue[]>(`/v1/import-batches/${encodeURIComponent(id)}/issues`),
  preview: (id: string) => apiRequest<ImportPreview>(`/v1/import-batches/${encodeURIComponent(id)}/preview`),
  upload(type: ImportType, file: File, key = idempotencyKey('upload')) {
    const body = new FormData()
    body.set('importType', type)
    body.set('templateVersion', '1.0')
    body.set('file', file)
    return multipart<ImportReceipt>('/v1/import-batches', body, key)
  },
  cancel: (id: string, key = idempotencyKey('cancel')) => apiRequest<ImportBatch>(
    `/v1/import-batches/${encodeURIComponent(id)}/cancel`,
    { method: 'POST', headers: { 'Idempotency-Key': key } },
  ),
  revalidate: (id: string, key = idempotencyKey('revalidate')) => apiRequest<ImportReceipt>(
    `/v1/import-batches/${encodeURIComponent(id)}/revalidate`,
    { method: 'POST', headers: { 'Idempotency-Key': key } },
  ),
  commit: (id: string, previewVersion: string, fileHash: string, warningsAccepted: boolean, key = idempotencyKey('commit')) => apiRequest<ImportBatch>(
    `/v1/import-batches/${encodeURIComponent(id)}/commit`,
    { method: 'POST', headers: { 'Idempotency-Key': key, 'Content-Type': 'application/json' }, body: JSON.stringify({ previewVersion, fileHash, warningsAccepted }) },
  ),
  result: (id: string) => apiRequest<ImportResult>(`/v1/import-batches/${encodeURIComponent(id)}/result`),
  downloadTemplate: download,
}
