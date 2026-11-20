// Protected, memory-only cache. Object URLs remain owned by individual views.
export const IMAGE_SESSION_CHANGED = 'jiugeng:image-session-changed'
const entries = new Map<string, Entry>()
const MAX_BYTES = 32 * 1024 * 1024, MAX_ENTRIES = 80, TTL = 60_000
// Aggregate diagnostics contain no image identifiers or session tokens.
export const catalogImageMetrics = { hits: 0, misses: 0, completed: 0, failed: 0, bytes: 0, totalLoadMs: 0 }
let activeLoads = 0
const waiting: Array<() => void> = []
async function limitedLoad(signal: AbortSignal, loader: (signal: AbortSignal) => Promise<Blob>): Promise<Blob> {
  if (activeLoads < 6) activeLoads++
  else await new Promise<void>((resolve, reject) => {
    const resume = () => { signal.removeEventListener('abort', cancel); resolve() }
    const cancel = () => { const i = waiting.indexOf(resume); if (i >= 0) waiting.splice(i, 1); reject(new DOMException('Aborted', 'AbortError')) }
    if (signal.aborted) { reject(new DOMException('Aborted', 'AbortError')); return }
    waiting.push(resume); signal.addEventListener('abort', cancel, { once: true })
  })
  if (signal.aborted) { const next = waiting.shift(); if(next)next();else activeLoads--; throw new DOMException('Aborted', 'AbortError') }
  const start = performance.now()
  try { const blob = await loader(signal); catalogImageMetrics.completed++; catalogImageMetrics.bytes += blob.size; return blob }
  catch (error) { if (!signal.aborted) catalogImageMetrics.failed++; throw error }
  finally { catalogImageMetrics.totalLoadMs += performance.now() - start; const next = waiting.shift(); if(next)next();else activeLoads-- }
}
let scope = ''
interface Entry { promise: Promise<Blob>; controller: AbortController; users: number; blob?: Blob; expires: number }
export function evictCachedImage(session: string, key: string) {
  if (session === scope) entries.delete(key)
}
export function clearImageCache() {
  for (const entry of entries.values()) entry.controller.abort()
  entries.clear(); scope = ''
}
function trim() {
  let bytes = [...entries.values()].reduce((n, e) => n + (e.blob?.size ?? 0), 0)
  for (const [key, e] of entries) {
    if (e.blob && (e.expires <= Date.now() || bytes > MAX_BYTES || entries.size > MAX_ENTRIES)) {
      bytes -= e.blob.size; entries.delete(key)
    }
  }
}
export function cachedImage(session: string, key: string, loader: (signal: AbortSignal) => Promise<Blob>, signal?: AbortSignal): Promise<Blob> {
  if (signal?.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'))
  if (session !== scope) { clearImageCache(); scope = session }
  trim()
  let entry = entries.get(key)
  if (!entry) {
    catalogImageMetrics.misses++
    const controller = new AbortController()
    entry = { controller, users: 0, expires: 0, promise: Promise.resolve(new Blob()) }
    const current = entry
    entry.promise = limitedLoad(controller.signal, loader).then(blob => {
      if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError')
      current.blob = blob; current.expires = Date.now() + TTL; trim(); return blob
    }).catch(error => { if (entries.get(key) === current) entries.delete(key); throw error })
    entries.set(key, entry)
  } else { catalogImageMetrics.hits++; entries.delete(key); entries.set(key, entry) }
  const current = entry
  current.users++
  return new Promise((resolve, reject) => {
    let done = false
    const finish = () => {
      if (done) return false
      done = true; signal?.removeEventListener('abort', abort); current.users--
      if (!current.users && !current.blob) { current.controller.abort(); if (entries.get(key) === current) entries.delete(key) }
      return true
    }
    const abort = () => { if (finish()) reject(new DOMException('Aborted', 'AbortError')) }
    signal?.addEventListener('abort', abort, { once: true })
    current.promise.then(blob => { if (finish()) resolve(blob) }, error => { if (finish()) reject(error) })
  })
}
window.addEventListener(IMAGE_SESSION_CHANGED, clearImageCache)
window.addEventListener('jiugeng:session-expired', clearImageCache)
window.addEventListener('storage', clearImageCache)
