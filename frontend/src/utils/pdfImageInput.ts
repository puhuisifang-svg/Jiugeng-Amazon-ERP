import type { PdfImage } from './pdfTools'
export const MAX_PDF_IMAGE_TOTAL = 100 * 1024 * 1024
export const MAX_PDF_IMAGE_PIXELS = 100_000_000
export type NormalizedPdfImage = PdfImage & { url: string; pixels: number; sourceSize: number }
function encode(canvas: HTMLCanvasElement, type: string) {
  return new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片转换失败，请重新选择该图片')), type, .92))
}
// JPEG embedding ignores EXIF orientation. Only oriented photos need pixel normalization.
function needsOrientation(bytes: Uint8Array): boolean {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  for (let offset = 2; offset + 4 <= bytes.length;) {
    if (bytes[offset] !== 0xff) break
    const marker = bytes[offset + 1]!, length = view.getUint16(offset + 2)
    if (marker === 0xda || marker === 0xd9 || length < 2 || offset + 2 + length > bytes.length) break
    const end = offset + 2 + length, start = offset + 4
    if (marker === 0xe1 && start + 14 <= end && view.getUint32(start) === 0x45786966 && view.getUint16(start + 4) === 0) {
      const tiff = start + 6, little = view.getUint16(tiff) === 0x4949
      if ((little || view.getUint16(tiff) === 0x4d4d) && view.getUint16(tiff + 2, little) === 42) {
        const directory = tiff + view.getUint32(tiff + 4, little)
        if (directory >= tiff + 8 && directory + 2 <= end) {
          const count = view.getUint16(directory, little)
          for (let i = 0; i < count; i++) {
            const entry = directory + 2 + i * 12
            if (entry + 12 > end) break
            if (view.getUint16(entry, little) === 0x112 && view.getUint16(entry + 2, little) === 3 && view.getUint32(entry + 4, little) === 1) {
              const orientation = view.getUint16(entry + 8, little)
              return orientation >= 2 && orientation <= 8
            }
          }
        }
      }
    }
    offset = end
  }
  return false
}
export async function normalizePdfImage(file: File, thumbnail = true): Promise<NormalizedPdfImage> {
  if (!/\.(jpe?g|png)$/i.test(file.name) && !['image/jpeg', 'image/png'].includes(file.type)) throw new Error('仅支持 JPG、PNG 图片')
  if (!file.size) throw new Error('图片为空，请重新选择文件')
  if (file.size > 20 * 1024 * 1024) throw new Error('每张图片不能超过 20 MB')
  let bitmap: ImageBitmap
  try { bitmap = await createImageBitmap(file) } catch { throw new Error('图片无法读取，请检查文件是否损坏') }
  const canvas = document.createElement('canvas')
  try {
    const pixels = bitmap.width * bitmap.height
    if (!pixels || pixels > 40_000_000) throw new Error('图片分辨率过大，请缩小到 4000 万像素以内')
    const original = new Uint8Array(await file.arrayBuffer())
    const jpeg = original[0] === 0xff && original[1] === 0xd8 && original[2] === 0xff
    const png = original[0] === 0x89 && original[1] === 0x50 && original[2] === 0x4e && original[3] === 0x47
    const keepOriginal = (jpeg && !needsOrientation(original)) || png
    const format = jpeg && keepOriginal ? 'jpg' : 'png'
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('浏览器无法处理图片，请关闭其他大文件后重试')
    let blob: Blob
    if (keepOriginal) blob = new Blob([original], { type: jpeg ? 'image/jpeg' : 'image/png' })
    else {
      // Keep full decoded pixels; normalize rotated/mirrored photos losslessly, never JPEG-recompress.
      canvas.width = bitmap.width; canvas.height = bitmap.height
      ctx.drawImage(bitmap, 0, 0)
      blob = await encode(canvas, 'image/png')
    }
    if (blob.size > MAX_PDF_IMAGE_TOTAL) throw new Error('图片转换后超过 100 MB，请减少图片后重试')
    const bytes = keepOriginal ? original : new Uint8Array(await blob.arrayBuffer())
    let preview = blob
    if (thumbnail && Math.max(bitmap.width, bitmap.height) > 512) {
      const scale = 512 / Math.max(bitmap.width, bitmap.height)
      canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale))
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
      preview = await encode(canvas, jpeg ? 'image/jpeg' : 'image/png')
    }
    return { bytes, format, url: URL.createObjectURL(preview), pixels, sourceSize: file.size }
  } finally { bitmap.close(); canvas.width = 0; canvas.height = 0 }
}
