import { afterEach, describe, expect, it, vi } from 'vitest'
import { createShipmentImage, wrapExportText } from './shipmentExport'
import type { TodayShipment } from '@/api/dashboard'
vi.mock('@/api/domain', () => ({ domainApi: { product: vi.fn(async () => ({ variants: [] })) } }))
afterEach(() => vi.restoreAllMocks())
describe('shipment image export', () => {
  it('wraps Chinese, long strings and explicit newlines without losing text', () => {
    const context = { measureText: (text: string) => ({ width: [...text].length * 10 } as TextMetrics) }
    expect(wrapExportText(context, '产品名称很长\n小心轻放', 30)).toEqual(['产品名', '称很长', '小心轻', '放'])
  })
  it('renders required fields and reports missing product images', async () => {
    const fillText = vi.fn()
    const context = { fillStyle: '', fillRect: vi.fn(), fillText, measureText: (text: string) => ({ width: text.length * 26 }), beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(), stroke: vi.fn() }
    const textColors: Record<string, string> = {}
    fillText.mockImplementation((text: string) => { textColors[text] = context.fillStyle })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(callback => callback(new Blob(['png'], { type: 'image/png' })))
    const result = await createShipmentImage([{ productId: 'p1', productName: '测试产品', quantity: 3, boxCount: 2, remark: '小心轻放\n第二行' } as TodayShipment], '2026-10-16')
    expect(result.blob.type).toBe('image/png'); expect(result.missingImages).toBe(1)
    const text = fillText.mock.calls.map(call => call[0]).join('|')
    for (const value of ['2026-10-16', '测试产品', '3 套', '2 箱', '小心轻放', '图片不可用']) expect(text).toContain(value)
    expect(fillText).toHaveBeenCalledWith('今日发货清单--', 40, 62)
    expect(fillText).toHaveBeenCalledWith('发货日期：2026-10-16', 40 + context.measureText('今日发货清单--').width, 62)
    expect(textColors['今日发货清单--']).toBe('#172033')
    expect(textColors['发货日期：2026-10-16']).toBe('#dc2626')
    expect(fillText.mock.calls.filter(call => call[0].includes('发货日期'))).toHaveLength(1)
    expect(fillText).toHaveBeenCalledWith('测试产品', 220, 280)
    expect(fillText).toHaveBeenCalledWith('3 套', 650, 280)
    expect(fillText).toHaveBeenCalledWith('小心轻放', 960, 262)
    expect(fillText).toHaveBeenCalledWith('第二行', 960, 298)
  })
})
