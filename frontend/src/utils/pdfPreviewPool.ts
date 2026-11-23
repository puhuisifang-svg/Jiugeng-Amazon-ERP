import { getDocument, GlobalWorkerOptions, type PDFDocumentLoadingTask } from 'pdfjs-dist/legacy/build/pdf.mjs'
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'
GlobalWorkerOptions.workerSrc = workerUrl

// A document is shared by all visible previews, and destroyed after its last render.
const documents = new WeakMap<Uint8Array, { task: PDFDocumentLoadingTask; users: number }>()
export function acquirePdfPreview(bytes: Uint8Array) {
  let entry = documents.get(bytes)
  if (!entry) {
    entry = { users: 0, task: getDocument({ data: bytes.slice(), useSystemFonts: true, cMapUrl: '/pdf-assets/cmaps/', cMapPacked: true, standardFontDataUrl: '/pdf-assets/standard_fonts/', wasmUrl: '/pdf-assets/wasm/' }) }
    documents.set(bytes, entry)
  }
  entry.users++
  const current = entry
  let released = false
  return { document: current.task.promise, release() {
    if (released) return
    released = true; current.users--
    // Keep the same document when a view swaps pages during this update.
    queueMicrotask(() => {
      if (!current.users && documents.get(bytes) === current) {
        documents.delete(bytes); void current.task.destroy().catch(() => {})
      }
    })
  } }
}
let active = 0
const waiting: (() => void)[] = []
export async function withPdfRenderSlot<T>(signal: AbortSignal, render: () => Promise<T>): Promise<T> {
  await new Promise<void>((resolve, reject) => {
    const abort = () => { const index = waiting.indexOf(start); if (index >= 0) waiting.splice(index, 1); reject(new DOMException('Aborted', 'AbortError')) }
    const start = () => { signal.removeEventListener('abort', abort); active++; resolve() }
    if (signal.aborted) { abort(); return }
    if (active < 2) start()
    else { waiting.push(start); signal.addEventListener('abort', abort, { once: true }) }
  })
  try { signal.throwIfAborted(); return await render() }
  finally { active--; waiting.shift()?.() }
}
