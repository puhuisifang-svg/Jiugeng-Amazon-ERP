import type { PdfImage } from './pdfTools'
export const MAX_PDF_IMAGE_TOTAL = 100 * 1024 * 1024
export const MAX_PDF_IMAGE_PIXELS = 100_000_000
export type NormalizedPdfImage = PdfImage & { url: string; pixels: number; sourceSize: number }
function encode(canvas: HTMLCanvasElement, type: string) {
  return new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片转换失败，请重新选择该图片')), type, .92))
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
    const signature = new Uint8Array(await file.slice(0, 3).arrayBuffer())
    const jpeg = signature[0] === 0xff && signature[1] === 0xd8 && signature[2] === 0xff
    canvas.width = bitmap.width; canvas.height = bitmap.height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('浏览器无法处理图片，请关闭其他大文件后重试')
    ctx.drawImage(bitmap, 0, 0)
    // Normalize orientation, retain JPEG for photos and PNG alpha for transparent artwork.
    const blob = await encode(canvas, jpeg ? 'image/jpeg' : 'image/png')
    if (blob.size > MAX_PDF_IMAGE_TOTAL) throw new Error('图片转换后超过 100 MB，请缩小图片后重试')
    const bytes = new Uint8Array(await blob.arrayBuffer())
    let preview = blob
    if (thumbnail && Math.max(bitmap.width, bitmap.height) > 512) {
      const scale = 512 / Math.max(bitmap.width, bitmap.height)
      canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale))
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
      preview = await encode(canvas, jpeg ? 'image/jpeg' : 'image/png')
    }
    return { bytes, format: jpeg ? 'jpg' : 'png', url: URL.createObjectURL(preview), pixels, sourceSize: file.size }
  } finally { bitmap.close(); canvas.width = 0; canvas.height = 0 }
}
