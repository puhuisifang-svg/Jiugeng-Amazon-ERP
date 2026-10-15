import { ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'

export type CatalogImagePurpose = 'SKU_MAIN' | 'SUPPLIER_QR' | 'MATERIAL_IMAGE'
export interface CatalogImageReceipt { id: string; purpose: CatalogImagePurpose; sourceFileName: string; mediaType: string; byteSize: number; width: number; height: number; sha256: string }

async function request(path: string, init: RequestInit) {
  const headers = new Headers(init.headers)
  const token = getSessionToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string; message?: string } | null
    if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? problem?.message ?? `请求失败（${response.status}）`)
  }
  return response
}

export const catalogApi = {
  uploadImage: async (purpose: CatalogImagePurpose, file: File) => {
    const body = new FormData(); body.set('purpose', purpose); body.set('file', file)
    return (await request('/v1/catalog-images', { method: 'POST', body })).json() as Promise<CatalogImageReceipt>
  },
  imageBlob: async (id: string, variant: 'thumbnail' | 'original' = 'thumbnail') =>
    (await request(`/v1/catalog-images/${id}/${variant}`, { method: 'GET' })).blob(),
}
