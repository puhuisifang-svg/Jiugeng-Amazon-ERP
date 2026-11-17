import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'
import { MM, type LabelCrop } from './pdfTools'

// Inspect pixels only to locate empty margins. Export always embeds original PDF objects.
export function inkBounds(data: Uint8ClampedArray, width: number, height: number, padding: number) {
  let left = width, top = height, right = -1, bottom = -1
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4
    if (data[i + 3]! > 0 && Math.min(data[i]!, data[i + 1]!, data[i + 2]!) < 254) {
      left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y)
    }
  }
  if (right < 0) return undefined
  return { left: Math.max(0, left - padding), top: Math.max(0, top - padding), right: Math.min(width, right + 1 + padding), bottom: Math.min(height, bottom + 1 + padding) }
}
const cache = new WeakMap<Uint8Array, Promise<(LabelCrop | undefined)[]>>()
export function labelContentBounds(bytes: Uint8Array): Promise<(LabelCrop | undefined)[]> {
  const existing = cache.get(bytes)
  if (existing) return existing
  const pending = detect(bytes).catch(error => { cache.delete(bytes); throw error })
  cache.set(bytes, pending)
  return pending
}
async function detect(bytes: Uint8Array) {
  const { getDocument, GlobalWorkerOptions } = await import('pdfjs-dist/legacy/build/pdf.mjs')
  GlobalWorkerOptions.workerSrc = workerUrl
  const task = getDocument({ data: bytes.slice(), useSystemFonts: true, cMapUrl: '/pdf-assets/cmaps/', cMapPacked: true, standardFontDataUrl: '/pdf-assets/standard_fonts/', wasmUrl: '/pdf-assets/wasm/' })
  try {
    const doc = await task.promise, crops: (LabelCrop | undefined)[] = []
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i), original = page.getViewport({ scale: 1, rotation: 0 })
      const scale = Math.min(3, 1800 / Math.max(original.width, original.height))
      const viewport = page.getViewport({ scale, rotation: 0 })
      const canvas = document.createElement('canvas'); canvas.width = Math.ceil(viewport.width); canvas.height = Math.ceil(viewport.height)
      const ctx = canvas.getContext('2d', { willReadFrequently: true })!
      await page.render({ canvas, viewport, background: '#ffffff' }).promise
      // Keep at least 4mm of source quiet zone on each edge; never trim internal gaps.
      const bounds = inkBounds(ctx.getImageData(0, 0, canvas.width, canvas.height).data, canvas.width, canvas.height, Math.ceil(4 * MM * scale))
      if (!bounds) crops.push(undefined)
      else {
        const [left, top] = viewport.convertToPdfPoint(bounds.left, bounds.top)
        const [right, bottom] = viewport.convertToPdfPoint(bounds.right, bounds.bottom)
        crops.push({ x: left!, y: bottom!, width: right! - left!, height: top! - bottom! })
      }
      canvas.width = 0; canvas.height = 0; page.cleanup()
    }
    return crops
  } finally { await task.destroy() }
}
