import { domainApi } from '@/api/domain'
import { catalogApi } from '@/api/catalog'
import type { TodayShipment } from '@/api/dashboard'

export function wrapExportText(context: Pick<CanvasRenderingContext2D, 'measureText'>, text: string, width: number): string[] {
  const lines: string[] = []
  for (const paragraph of text.split('\n')) {
    let line = ''
    for (const character of paragraph) {
      if (line && context.measureText(line + character).width > width) { lines.push(line); line = '' }
      line += character
    }
    lines.push(line)
  }
  return lines
}
async function productImage(productId: string): Promise<HTMLImageElement | undefined> {
  let url = ''; let owned = false
  try {
    const product = await domainApi.product(productId)
    const variant = product.variants.find(v => v.imageAssetId || v.imageUrl)
    if (!variant) return undefined
    if (variant.imageAssetId) { url = URL.createObjectURL(await catalogApi.imageBlob(variant.imageAssetId, 'original')); owned = true }
    else url = variant.imageUrl ?? ''
    return await new Promise<HTMLImageElement | undefined>(resolve => {
      const image = new Image(); image.crossOrigin = 'anonymous'; image.referrerPolicy = 'no-referrer'
      const timer = window.setTimeout(() => { image.src = ''; resolve(undefined) }, 10000)
      image.onload = () => { clearTimeout(timer); resolve(image) }
      image.onerror = () => { clearTimeout(timer); resolve(undefined) }
      image.src = url
    })
  } catch { return undefined }
  finally { if (owned) URL.revokeObjectURL(url) }
}
export async function createShipmentImage(items: TodayShipment[], date: string): Promise<{ blob: Blob; missingImages: number }> {
  if (!items.length) throw new Error('没有可导出的发货记录。')
  await document.fonts?.ready
  const canvas = document.createElement('canvas'); const context = canvas.getContext('2d')
  if (!context) throw new Error('当前浏览器不支持图片导出。')
  const imageCache = new Map<string, Promise<HTMLImageElement | undefined>>()
  const rows = await Promise.all(items.map(async item => {
    if (!imageCache.has(item.productId)) imageCache.set(item.productId, productImage(item.productId))
    const image = await imageCache.get(item.productId)
    context.font = '26px sans-serif'
    const name = wrapExportText(context, item.productName, 400)
    const remark = wrapExportText(context, item.remark || '—', 580)
    return { item, image, name, remark, height: Math.max(160, Math.max(name.length, remark.length) * 36 + 40) }
  }))
  canvas.width = 1600; canvas.height = 240 + rows.reduce((sum, row) => sum + row.height, 0)
  context.fillStyle = '#ffffff'; context.fillRect(0, 0, canvas.width, canvas.height)
  const title = '今日发货清单--'
  context.fillStyle = '#172033'; context.font = 'bold 40px sans-serif'; context.fillText(title, 40, 62)
  context.fillStyle = '#dc2626'; context.fillText(`发货日期：${date}`, 40 + context.measureText(title).width, 62)
  context.font = '24px sans-serif'; context.fillStyle = '#566176'
  context.fillText(`共 ${items.length} 条    数量：${items.reduce((sum, item) => sum + item.quantity, 0)} 套    箱数：${items.reduce((sum, item) => sum + item.boxCount, 0)} 箱`, 40, 110)
  context.fillStyle = '#edf1f7'; context.fillRect(40, 140, 1520, 60)
  context.fillStyle = '#42516a'; context.font = 'bold 24px sans-serif'
  context.textBaseline = 'middle'
  for (const [label, x] of [['产品图片', 55], ['产品名称', 220], ['发货数量', 650], ['发货箱数', 800], ['备注', 960]] as const) context.fillText(label, x, 170)
  let y = 200
  for (const [index, row] of rows.entries()) {
    if (index % 2 === 1) { context.fillStyle = '#f8fafc'; context.fillRect(40, y, 1520, row.height) }
    context.fillStyle = '#172033'; context.font = '26px sans-serif'
    const center = y + row.height / 2
    row.name.forEach((line, i) => context.fillText(line, 220, center + (i - (row.name.length - 1) / 2) * 36))
    row.remark.forEach((line, i) => context.fillText(line, 960, center + (i - (row.remark.length - 1) / 2) * 36))
    context.fillText(`${row.item.quantity} 套`, 650, center); context.fillText(`${row.item.boxCount} 箱`, 800, center)
    if (row.image) {
      const scale = Math.min(130 / row.image.naturalWidth, 130 / row.image.naturalHeight)
      const width = row.image.naturalWidth * scale; const height = row.image.naturalHeight * scale
      context.drawImage(row.image, 55 + (130 - width) / 2, center - height / 2, width, height)
    } else { context.font = '20px sans-serif'; context.fillStyle = '#8a5260'; context.fillText('图片不可用', 55, center) }
    y += row.height; context.strokeStyle = '#e2e7ee'; context.beginPath(); context.moveTo(40, y); context.lineTo(1560, y); context.stroke()
  }
  const blob = await new Promise<Blob>((resolve, reject) => { canvas.toBlob(value => value ? resolve(value) : reject(new Error('生成图片失败，请重试。')), 'image/png') })
  return { blob, missingImages: rows.filter(row => !row.image).length }
}
