import { PDFDocument, PrintScaling } from 'pdf-lib'
import { MM } from './pdfTools'

// Render the fitted preview's exact line positions at 384 DPI, including CJK fonts.
export async function sampleLabelPdf(doc: Document): Promise<Uint8Array> {
  await doc.fonts?.ready
  const label = doc.querySelector<HTMLElement>('.label')
  if (!label) throw new Error('标签预览尚未加载，请稍后重试')
  const box = label.getBoundingClientRect(), scale = 4
  if (!box.width || !box.height) throw new Error('标签预览尺寸无效')
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(box.width * scale); canvas.height = Math.ceil(box.height * scale)
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('当前浏览器无法生成标签PDF')
  ctx.scale(scale, scale); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, box.width, box.height); ctx.fillStyle = '#000'
  for (const element of label.querySelectorAll<HTMLElement>('.name,.spec,.location')) {
    const style = doc.defaultView!.getComputedStyle(element)
    ctx.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
    ctx.textBaseline = 'alphabetic'
    const node = element.firstChild
    if (!node || node.nodeType !== Node.TEXT_NODE) continue
    const text = node.textContent || '', range = doc.createRange()
    let line = '', x = 0, y = 0
    const draw = () => { if (line) ctx.fillText(line, x - box.left, y - box.top + ctx.measureText(line).fontBoundingBoxAscent) }
    for (const segment of new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text)) {
      range.setStart(node, segment.index); range.setEnd(node, segment.index + segment.segment.length)
      const rect = range.getBoundingClientRect()
      if (segment.segment === '\n') { draw(); line = ''; continue }
      if (line && Math.abs(rect.top - y) > 1) { draw(); line = '' }
      if (!line) { x = rect.left; y = rect.top }
      line += segment.segment
    }
    draw()
  }
  const png = await new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('标签图片生成失败')), 'image/png'))
  const pdf = await PDFDocument.create(), image = await pdf.embedPng(await png.arrayBuffer())
  pdf.addPage([70 * MM, 30 * MM]).drawImage(image, { x: 0, y: 0, width: 70 * MM, height: 30 * MM })
  pdf.catalog.getOrCreateViewerPreferences().setPrintScaling(PrintScaling.None)
  return pdf.save()
}
