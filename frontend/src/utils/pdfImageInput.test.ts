// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizePdfImage } from './pdfImageInput'
const draw = vi.fn(), close = vi.fn(), encode = vi.fn()
let canvas: { width: number; height: number; getContext: () => unknown; toBlob: typeof encode }
beforeEach(() => {
  canvas = { width: 0, height: 0, getContext: () => ({ drawImage: draw }), toBlob: encode }
  vi.stubGlobal('document', { createElement: () => canvas })
  vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 1600, height: 1200, close })))
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:synthetic')
  encode.mockImplementation((callback: (blob: Blob) => void, type: string) => callback(new Blob(['encoded'], { type })))
})
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.clearAllMocks() })
describe('PDF image input', () => {
  it('normalizes photos as JPEG and creates a small preview without reducing PDF pixels', async () => {
    const image = await normalizePdfImage(new File([new Uint8Array([255, 216, 255, 1])], 'photo.jpg'))
    expect(image.format).toBe('jpg'); expect(image.pixels).toBe(1_920_000)
    expect(encode.mock.calls.every(call => call[1] === 'image/jpeg')).toBe(true)
    expect(draw).toHaveBeenLastCalledWith(expect.anything(), 0, 0, 512, 384)
    expect(canvas.width).toBe(0); expect(close).toHaveBeenCalledTimes(1)
  })
  it('preserves PNG transparency and frees large buffers when encoding fails', async () => {
    const file = new File([new Uint8Array([137, 80, 78, 71])], 'alpha.png')
    expect((await normalizePdfImage(file, false)).format).toBe('png')
    expect(encode).toHaveBeenCalledWith(expect.any(Function), 'image/png', .92)
    encode.mockImplementationOnce((callback: (blob: null) => void) => callback(null))
    await expect(normalizePdfImage(file)).rejects.toThrow('图片转换失败')
    expect(canvas.width).toBe(0); expect(close).toHaveBeenCalledTimes(2)
  })
})
