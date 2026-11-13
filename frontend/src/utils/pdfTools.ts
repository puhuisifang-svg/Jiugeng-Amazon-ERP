import { PDFDocument, degrees, PrintScaling } from 'pdf-lib'

export const MM = 72 / 25.4
export const MAX_PDF_BYTES = 50 * 1024 * 1024
export async function loadPdf(bytes: Uint8Array) {
  if (!bytes.length || bytes.length > MAX_PDF_BYTES) throw new Error('请选择不超过50 MB的PDF文件')
  let doc: PDFDocument
  try { doc = await PDFDocument.load(bytes) }
  catch { throw new Error('PDF无法读取，请检查文件是否损坏或有密码保护') }
  if (!doc.getPageCount() || doc.getPageCount() > 500) throw new Error('支持1～500页的PDF')
  return doc
}
export function ensurePrintable(doc: PDFDocument) {
  if (doc.getForm().getFields().length) throw new Error('此文件包含交互表单或签名，请先另存为普通PDF后处理')
}
export function pageGroups(input: string, total: number): number[][] {
  if (!input.trim()) throw new Error('请输入页码，例如1-3,5,7-9')
  return input.replaceAll('，', ',').split(',').map(part => {
    const match = /^\s*(\d+)\s*(?:-\s*(\d+)\s*)?$/.exec(part)
    if (!match) throw new Error('页码格式不正确，请使用1-3,5这样的格式')
    const start = Number(match[1]), end = Number(match[2] || match[1])
    if (start < 1 || end < start || end > total) throw new Error(`页码必须在1～${total}内，且起始页不能大于结束页`)
    return Array.from({ length: end - start + 1 }, (_, i) => start - 1 + i)
  })
}
export async function splitPdf(bytes: Uint8Array, groups: number[][]) {
  const source = await loadPdf(bytes)
  ensurePrintable(source)
  if (!groups.length || groups.some(group => !group.length || group.some(i => !Number.isInteger(i) || i < 0 || i >= source.getPageCount()))) throw new Error('请选择有效的页面范围')
  const results: Uint8Array[] = []
  for (const group of groups) {
    const out = await PDFDocument.create()
    for (const page of await out.copyPages(source, group)) out.addPage(page)
    results.push(await out.save())
  }
  return results
}
export async function squareLabels(bytes: Uint8Array, marginMm = 2) {
  if (!Number.isFinite(marginMm) || marginMm < 0 || marginMm > 10) throw new Error('边距应为0～10毫米')
  const source = await loadPdf(bytes)
  ensurePrintable(source)
  const out = await PDFDocument.create(), side = 100 * MM, usable = side - 2 * marginMm * MM
  for (const page of source.getPages()) {
    if (page.node.Annots()?.size()) throw new Error('标签包含批注，请先另存为普通PDF，以免遗漏批注内容')
    const box = page.getCropBox(), rotation = ((page.getRotation().angle % 360) + 360) % 360
    const swapped = rotation === 90 || rotation === 270
    const width = swapped ? box.height : box.width, height = swapped ? box.width : box.height
    const scale = Math.min(usable / width, usable / height)
    const embedded = await out.embedPage(page, { left: box.x, bottom: box.y, right: box.x + box.width, top: box.y + box.height })
    const x = (side - width * scale) / 2, y = (side - height * scale) / 2
    const dx = rotation === 180 || rotation === 270 ? width * scale : 0
    const dy = rotation === 90 || rotation === 180 ? height * scale : 0
    out.addPage([side, side]).drawPage(embedded, { x: x + dx, y: y + dy, width: box.width * scale, height: box.height * scale, rotate: degrees(-rotation) })
  }
  out.catalog.getOrCreateViewerPreferences().setPrintScaling(PrintScaling.None)
  return out.save()
}
export type PdfImage = { bytes: Uint8Array; format: 'png' | 'jpg' }
export async function imagesToPdf(images: PdfImage[], size: 'a4' | 'square' | 'original', landscape: boolean, marginMm: number) {
  if (!images.length) throw new Error('请先添加图片')
  if (!Number.isFinite(marginMm) || marginMm < 0 || marginMm > 20) throw new Error('边距应为0～20毫米')
  const doc = await PDFDocument.create()
  for (const image of images) {
    const embedded = image.format === 'png' ? await doc.embedPng(image.bytes) : await doc.embedJpg(image.bytes)
    let w = size === 'square' ? 100 * MM : 210 * MM, h = size === 'square' ? 100 * MM : 297 * MM
    if (size === 'original') { w = embedded.width * .75 + marginMm * MM * 2; h = embedded.height * .75 + marginMm * MM * 2 }
    else if (landscape) [w, h] = [h, w]
    const scale = Math.min((w - marginMm * MM * 2) / embedded.width, (h - marginMm * MM * 2) / embedded.height)
    const width = embedded.width * scale, height = embedded.height * scale
    doc.addPage([w, h]).drawImage(embedded, { x: (w - width) / 2, y: (h - height) / 2, width, height })
  }
  return doc.save()
}
// Transparent layers use display coordinates (top-left, including page rotation).
// Keep original PDF objects and annotations intact; never rasterize the source page.
export async function overlayPdf(bytes: Uint8Array, layers: { page: number; png: Uint8Array }[]) {
  const doc = await loadPdf(bytes)
  ensurePrintable(doc)
  for (const layer of layers) {
    const page = doc.getPage(layer.page), box = page.getCropBox()
    const r = ((page.getRotation().angle % 360) + 360) % 360
    const swapped = r === 90 || r === 270
    const image = await doc.embedPng(layer.png)
    page.drawImage(image, { x: box.x + (r === 90 || r === 180 ? box.width : 0), y: box.y + (r === 180 || r === 270 ? box.height : 0), width: swapped ? box.height : box.width, height: swapped ? box.width : box.height, rotate: degrees(r) })
  }
  return doc.save()
}
export function downloadPdf(bytes: Uint8Array, name: string, mime = 'application/pdf') {
  const url = URL.createObjectURL(new Blob([new Uint8Array(bytes)], { type: mime }))
  const a = document.createElement('a'); a.href = url; a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}
