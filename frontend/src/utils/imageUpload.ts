import { ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
export type UploadProgress = (status: string) => void
let running = 0
const waiting: (() => void)[] = []
export async function queueImageUpload<T>(work: () => Promise<T>, progress?: UploadProgress): Promise<T> {
  if (running >= 2) { progress?.('等待上传…'); await new Promise<void>(resolve => waiting.push(resolve)) }
  else running++
  try { return await work() } finally { const next = waiting.shift(); if (next) next(); else running-- }
}
export function uploadImageRequest<T>(body: FormData, progress: UploadProgress): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${runtimeConfig.apiBaseUrl}/v1/catalog-images`)
    const token = getSessionToken(); if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.timeout = 120_000
    xhr.upload.onprogress = e => { if (e.lengthComputable) progress(`正在上传 ${Math.min(100, Math.round(e.loaded / e.total * 100))}%`) }
    xhr.upload.onload = () => progress('正在处理并保存…')
    xhr.onerror = () => reject(new Error('网络异常，请重试上传。'))
    xhr.ontimeout = () => reject(new Error('上传超时，请重试。'))
    xhr.onabort = () => reject(new Error('上传已取消。'))
    xhr.onload = () => {
      let result: Record<string, unknown>
      try { result = JSON.parse(xhr.responseText) } catch { reject(new Error('上传响应异常，请重试。')); return }
      if (xhr.status >= 200 && xhr.status < 300) resolve(result as T)
      else {
        if (xhr.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
        reject(new ApiError(xhr.status, String(result.code ?? 'UPLOAD_FAILED'), String(result.detail ?? result.message ?? '图片上传失败，请重试。')))
      }
    }
    progress('正在上传 0%'); xhr.send(body)
  })
}
