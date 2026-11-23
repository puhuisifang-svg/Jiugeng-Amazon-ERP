// Session-scoped decoded bytes; HTTP private revalidation provides reuse across reloads.
export const IMAGE_SESSION_CHANGED = 'jiugeng:image-session-changed'
export type ImagePriority = 'preview' | 'visible' | 'prefetch'
const entries = new Map<string, Entry>()
const MAX_BYTES = 32 * 1024 * 1024, MAX_ENTRIES = 200, TTL = 60_000
export const catalogImageMetrics = { hits: 0, misses: 0, completed: 0, failed: 0, bytes: 0, totalLoadMs: 0, totalQueueMs: 0 }
let activeLoads = 0, regularLoads = 0
interface Queued { signal: AbortSignal; priority: ImagePriority; start: () => void }
const waiting: Queued[] = []
const rank = { preview: 0, visible: 1, prefetch: 2 }
function drain() {
  waiting.sort((a,b) => rank[a.priority] - rank[b.priority])
  while (activeLoads < 6) {
    const i = waiting.findIndex(task => task.priority === 'preview' || regularLoads < 5)
    if (i < 0) return
    waiting.splice(i, 1)[0]!.start()
  }
}
function promote(signal: AbortSignal, priority: ImagePriority) {
  const task = waiting.find(item => item.signal === signal)
  if (task && rank[priority] < rank[task.priority]) { task.priority = priority; drain() }
}
async function limitedLoad(signal: AbortSignal, loader: (signal: AbortSignal) => Promise<Blob>, priority: ImagePriority): Promise<Blob> {
  const queuedAt = performance.now()
  let regular = false
  await new Promise<void>((resolve, reject) => {
    const task: Queued = { signal, priority, start: () => {
      signal.removeEventListener('abort', cancel)
      regular = task.priority !== 'preview'; activeLoads++; if (regular) regularLoads++
      resolve()
    } }
    const cancel = () => { const i = waiting.indexOf(task); if (i >= 0) waiting.splice(i,1); reject(new DOMException('Aborted','AbortError')) }
    if (signal.aborted) { cancel(); return }
    waiting.push(task); signal.addEventListener('abort', cancel, { once: true }); drain()
  })
  const start = performance.now(); catalogImageMetrics.totalQueueMs += start - queuedAt
  try {
    if (signal.aborted) throw new DOMException('Aborted','AbortError')
    const blob = await loader(signal); catalogImageMetrics.completed++; catalogImageMetrics.bytes += blob.size; return blob
  } catch (error) { if (!signal.aborted) catalogImageMetrics.failed++; throw error }
  finally { catalogImageMetrics.totalLoadMs += performance.now() - start; activeLoads--; if (regular) regularLoads--; drain() }
}
let scope = ''
interface Entry { promise: Promise<Blob>; controller: AbortController; users: number; blob?: Blob; expires: number }
export function evictCachedImage(session: string, key: string) { if (session === scope) entries.delete(key) }
export function promoteCachedImage(session: string, key: string, priority: ImagePriority) { if (session === scope) { const entry = entries.get(key); if (entry) promote(entry.controller.signal,priority) } }
export function peekCachedImage(session: string, key: string): Blob | undefined {
  const entry = session === scope ? entries.get(key) : undefined
  return entry && entry.expires > Date.now() ? entry.blob : undefined
}
export function clearImageCache() { for (const entry of entries.values()) entry.controller.abort(); entries.clear(); scope = '' }
function trim() {
  let bytes = [...entries.values()].reduce((n,e) => n + (e.blob?.size ?? 0),0)
  for (const [key,e] of entries) if (e.blob && (e.expires <= Date.now() || bytes > MAX_BYTES || entries.size > MAX_ENTRIES)) { bytes -= e.blob.size; entries.delete(key) }
}
export function cachedImage(session: string, key: string, loader: (signal: AbortSignal) => Promise<Blob>, signal?: AbortSignal, priority: ImagePriority = 'visible'): Promise<Blob> {
  if (signal?.aborted) return Promise.reject(new DOMException('Aborted','AbortError'))
  if (session !== scope) { clearImageCache(); scope = session }
  trim()
  let entry = entries.get(key)
  if (!entry) {
    catalogImageMetrics.misses++
    const controller = new AbortController()
    entry = { controller, users: 0, expires: 0, promise: Promise.resolve(new Blob()) }
    const current = entry
    entry.promise = limitedLoad(controller.signal,loader,priority).then(blob => {
      if (controller.signal.aborted) throw new DOMException('Aborted','AbortError')
      current.blob = blob; current.expires = Date.now() + TTL; trim(); return blob
    }).catch(error => { if (entries.get(key) === current) entries.delete(key); throw error })
    entries.set(key,entry)
  } else { catalogImageMetrics.hits++; entries.delete(key); entries.set(key,entry); promote(entry.controller.signal,priority) }
  const current = entry; current.users++
  return new Promise((resolve,reject) => {
    let done = false
    const finish = () => {
      if (done) return false
      done = true; signal?.removeEventListener('abort',abort); current.users--
      if (!current.users && !current.blob) { current.controller.abort(); if (entries.get(key) === current) entries.delete(key) }
      return true
    }
    const abort = () => { if (finish()) reject(new DOMException('Aborted','AbortError')) }
    signal?.addEventListener('abort',abort,{ once: true })
    current.promise.then(blob => { if (finish()) resolve(blob) },error => { if (finish()) reject(error) })
  })
}
window.addEventListener(IMAGE_SESSION_CHANGED,clearImageCache)
window.addEventListener('jiugeng:session-expired',clearImageCache)
window.addEventListener('storage',event => { if (!event.key || event.key === 'jiugeng.session.token') clearImageCache() })
// Anonymous aggregates only: no URLs, IDs, tokens or business fields.
export function imagePerformanceSnapshot() {
  const requests = performance.getEntriesByType('resource').filter((e): e is PerformanceResourceTiming => e instanceof PerformanceResourceTiming && e.name.includes('/catalog-images/'))
  const duration = requests.map(e => e.duration).sort((a,b) => a-b)
  return { ...catalogImageMetrics, activeLoads, queued: waiting.length, network: {
    count: requests.length, transferredBytes: requests.reduce((n,e) => n+e.transferSize,0),
    medianMs: duration[Math.floor(duration.length/2)] ?? 0, p95Ms: duration[Math.max(0,Math.ceil(duration.length*.95)-1)] ?? 0,
    waitingMs: requests.reduce((n,e) => n+Math.max(0,e.responseStart-e.requestStart),0), downloadMs: requests.reduce((n,e) => n+Math.max(0,e.responseEnd-e.responseStart),0),
  } }
}
declare global { interface Window { jiugengImageDiagnostics?: typeof imagePerformanceSnapshot } }
window.jiugengImageDiagnostics = imagePerformanceSnapshot
