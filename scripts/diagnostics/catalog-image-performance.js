/* Paste into the ERP page console. Read-only; output has no URLs, IDs or credentials.
 * Run once after reload, once after switching away/back. Do not enable Disable Cache.
 * To repeat a single already-loaded image GET: await jiugengImageProbe.repeat(3).
 */
window.jiugengImageProbe = {
  summary() {
    const items = performance.getEntriesByType('resource').filter(e => e.name.includes('/catalog-images/') && !e.name.endsWith('/resolve'))
    const times = items.map(e => e.duration).sort((a,b) => a-b)
    const round = value => Math.round(value * 10) / 10
    return {
      requests: items.length,
      medianMs: round(times[Math.floor(times.length / 2)] || 0),
      p95Ms: round(times[Math.max(0,Math.ceil(times.length * .95)-1)] || 0),
      transferredBytes: items.reduce((sum,e) => sum+e.transferSize,0),
      bodyBytes: items.reduce((sum,e) => sum+e.encodedBodySize,0),
      waitMs: round(items.reduce((sum,e) => sum+Math.max(0,e.responseStart-e.requestStart),0)),
      downloadMs: round(items.reduce((sum,e) => sum+Math.max(0,e.responseEnd-e.responseStart),0)),
      memory: window.jiugengImageDiagnostics?.(),
    }
  },
  async repeat(count = 3) {
    const entry = performance.getEntriesByType('resource').find(e => e.name.includes('/catalog-images/') && /\/(thumbnail|original)(\?|$)/.test(e.name))
    if (!entry || new URL(entry.name).origin !== location.origin) throw Error('先在当前站点打开含图片的列表')
    const token = localStorage.getItem('jiugeng.session.token') || sessionStorage.getItem('jiugeng.session.token')
    const samples = []
    for (let i=0;i<Math.min(5,Math.max(1,count));i++) {
      const start=performance.now()
      const response=await fetch(entry.name,{ headers:{ Authorization:`Bearer ${token}` } })
      const headersAt=performance.now(), blob=await response.blob()
      const timing=performance.getEntriesByName(entry.name).filter(e=>e.startTime>=start).at(-1)
      samples.push({ transferBytes:timing?.transferSize ?? null, sample:i+1,status:response.status,ms:Math.round(performance.now()-start),waitMs:Math.round(headersAt-start),bytes:blob.size,cache:response.headers.get('Cache-Control'),serverTiming:response.headers.get('Server-Timing') })
    }
    return samples
  },
}
window.jiugengImageProbe.summary()
