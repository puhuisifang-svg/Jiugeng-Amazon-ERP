import { expect, it, vi } from 'vitest'
import { drawPdfText, measurePdfText } from './pdfTextLayout'
it('uses PDF points and preserves blank lines and leading spaces in both layout paths', () => {
  const ctx = { measureText: vi.fn((line: string) => ({ width: line.length * 10 })), fillText: vi.fn() } as unknown as CanvasRenderingContext2D
  const layer = { text: ' 中文\n\nLatin', fontSize: 20, color: '#123456' }
  expect(measurePdfText(ctx, layer)).toEqual({ width: 52, height: 80 })
  drawPdfText(ctx, layer, 30, 40)
  expect(ctx.fillText).toHaveBeenNthCalledWith(1, ' 中文', 30, 43)
  expect(ctx.fillText).toHaveBeenNthCalledWith(2, '', 30, 69)
  expect(ctx.fillText).toHaveBeenNthCalledWith(3, 'Latin', 30, 95)
  expect(ctx.textBaseline).toBe('top')
})
