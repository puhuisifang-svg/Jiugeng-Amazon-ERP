import { cachedImage, type ImagePriority } from '@/utils/catalogImageCache'
import { queueImageUpload, uploadImageRequest, type UploadProgress } from '@/utils/imageUpload'
import { ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'

export type CatalogImagePurpose = 'PRODUCT_MAIN' | 'SKU_MAIN' | 'SUPPLIER_QR' | 'MATERIAL_IMAGE' | 'SAMPLE_STORAGE' | 'CUSTOMER_WECHAT' | 'SAMPLE_IMAGE' | 'PURCHASE_ITEM' | 'PURCHASE_WAYBILL'
export interface CatalogImageReceipt { id: string; purpose: CatalogImagePurpose; sourceFileName: string; mediaType: string; byteSize: number; width: number; height: number; sha256: string; thumbnailBase64?: string }
export interface SkuDocument { id: string; kind: 'MANUAL' | 'LABEL'; fileName: string; byteSize: number; uploadedAt: string }
function documentPath(productId: string, variantId: string) { return `/v1/products/${encodeURIComponent(productId)}/skus/${encodeURIComponent(variantId)}/documents` }

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

export function imageFileName(label: string, mediaType: string) {
  const extension = ({ 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'image/gif': 'gif', 'image/avif': 'avif' } as Record<string, string>)[mediaType.split(';')[0]!.toLowerCase()]
  if (!extension) throw new Error('无法识别图片格式，请联系管理员检查图片。')
  const base = label.replace(/[\\/:*?"<>|]/g, '_').split('').filter(char => char.charCodeAt(0) >= 32 && char.charCodeAt(0) !== 127).join('').replace(/\.(png|jpe?g|webp|gif|avif)$/i, '').trim().slice(0, 120) || '产品图片'
  return `${base}.${extension}`
}

export const catalogApi = {
  customerLabels: async (id: string) => (await request(`/v1/customers/products/${encodeURIComponent(id)}/labels`, { method: 'GET' })).json() as Promise<SkuDocument[]>,
  uploadCustomerLabel: async (id: string, file: File) => {
    const body = new FormData(); body.set('file', file)
    return (await request(`/v1/customers/products/${encodeURIComponent(id)}/labels`, { method: 'POST', body })).json() as Promise<SkuDocument>
  },
  downloadCustomerLabel: async (id: string, file: SkuDocument) => {
    const response = await request(`/v1/customers/products/${encodeURIComponent(id)}/labels/${encodeURIComponent(file.id)}/download`, { method: 'GET' })
    const url = URL.createObjectURL(await response.blob()); const link = document.createElement('a')
    link.href = url; link.download = file.fileName; document.body.append(link); link.click(); link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  documents: async (productId: string, variantId: string, kind: SkuDocument['kind']) =>
    (await request(`${documentPath(productId, variantId)}?kind=${kind}`, { method: 'GET' })).json() as Promise<SkuDocument[]>,
  uploadDocument: async (productId: string, variantId: string, kind: SkuDocument['kind'], file: File) => {
    const body = new FormData(); body.set('kind', kind); body.set('file', file)
    return (await request(documentPath(productId, variantId), { method: 'POST', body })).json() as Promise<SkuDocument>
  },
  downloadDocument: async (productId: string, variantId: string, document: SkuDocument) => {
    const response = await request(`${documentPath(productId, variantId)}/${encodeURIComponent(document.id)}/download`, { method: 'GET' })
    const url = URL.createObjectURL(await response.blob())
    const link = window.document.createElement('a'); link.href = url; link.download = document.fileName
    window.document.body.append(link); link.click(); link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  uploadImage: async (purpose: CatalogImagePurpose, file: File, progress?: UploadProgress) => {
    const body = new FormData(); body.set('purpose', purpose); body.set('file', file)
    const session = getSessionToken() ?? ''
    const receipt = await queueImageUpload(() => {
      if ((getSessionToken() ?? '') !== session) throw new Error('登录状态已变化，请重新上传。')
      return progress ? uploadImageRequest<CatalogImageReceipt>(body, progress) : request('/v1/catalog-images', { method: 'POST', body }).then(r => r.json() as Promise<CatalogImageReceipt>)
    }, progress)
    if ((getSessionToken() ?? '') !== session) throw new Error('登录状态已变化，请重新上传。')
    if (receipt.thumbnailBase64 && (getSessionToken() ?? '') === session) {
      try {
        const bytes = Uint8Array.from(atob(receipt.thumbnailBase64), c => c.charCodeAt(0))
        await cachedImage(session, `${receipt.id}/thumbnail`, async () => new Blob([bytes], { type: receipt.mediaType }))
      } catch { /* A preview failure must not turn a successfully persisted upload into a failure. */ }
    }
    return receipt
  },
  downloadImage: async (image: { imageAssetId?: string | undefined; imageUrl?: string | undefined; variantName: string }) => {
    const blob = image.imageAssetId
      ? await catalogApi.imageBlob(image.imageAssetId, 'original')
      : await catalogApi.externalImageBlob(image.imageUrl ?? '', 'original')
    const name = imageFileName(image.variantName, blob.type)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a'); link.href = url; link.download = name
    document.body.append(link); link.click(); link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
  externalImageBlob: (url: string, variant: 'thumbnail' | 'original' = 'thumbnail', signal?: AbortSignal, priority: ImagePriority = variant === 'original' ? 'preview' : 'visible', draftPermission?: string) =>
    cachedImage(getSessionToken() ?? '', `external:${url}/${variant}`, async sharedSignal => {
      if (runtimeConfig.authMode === 'mock') {
        if (!/^https?:\/\//i.test(url)) throw new Error('图片地址不可用。')
        const response = await fetch(url, { credentials: 'omit', referrerPolicy: 'no-referrer', signal: sharedSignal })
        if (!response.ok) throw new Error('图片下载失败。')
        return response.blob()
      }
      const resolved = await request('/v1/catalog-images/external/resolve', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url, draftPermission }), signal: sharedSignal })
      const { id } = await resolved.json() as { id: string }
      return await (await request(`/v1/catalog-images/external/${id}/${variant}`, { method: 'GET', cache: 'no-cache', signal: sharedSignal })).blob()
    }, signal, priority),
  imageBlob: (id: string, variant: 'thumbnail' | 'original' = 'thumbnail', signal?: AbortSignal, priority: ImagePriority = variant === 'original' ? 'preview' : 'visible') =>
    cachedImage(getSessionToken() ?? '', `${id}/${variant}`, async sharedSignal =>
      (await request(`/v1/catalog-images/${id}/${variant}`, { method: 'GET', cache: 'no-cache', signal: sharedSignal })).blob(), signal, priority),
}
