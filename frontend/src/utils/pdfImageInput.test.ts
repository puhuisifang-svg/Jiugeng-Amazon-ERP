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
  it('keeps original JPEG bytes and only compresses the disposable small preview', async () => {
    const image = await normalizePdfImage(new File([new Uint8Array([255, 216, 255, 1])], 'photo.jpg'))
    expect(image.bytes).toEqual(new Uint8Array([255, 216, 255, 1]))
    expect(encode).toHaveBeenCalledTimes(1)
    expect(image.format).toBe('jpg'); expect(image.pixels).toBe(1_920_000)
    expect(encode.mock.calls.every(call => call[1] === 'image/jpeg')).toBe(true)
    expect(draw).toHaveBeenLastCalledWith(expect.anything(), 0, 0, 512, 384)
    expect(canvas.width).toBe(0); expect(close).toHaveBeenCalledTimes(1)
  })
  it('preserves PNG transparency and frees large buffers when encoding fails', async () => {
    const file = new File([new Uint8Array([137, 80, 78, 71])], 'alpha.png')
    const image = await normalizePdfImage(file, false)
    expect(image.format).toBe('png')
    expect(image.bytes).toEqual(new Uint8Array([137, 80, 78, 71]))
    expect(encode).not.toHaveBeenCalled()
    encode.mockImplementationOnce((callback: (blob: null) => void) => callback(null))
    await expect(normalizePdfImage(file)).rejects.toThrow('图片转换失败')
    expect(canvas.width).toBe(0); expect(close).toHaveBeenCalledTimes(2)
  })
  it('normalizes EXIF-rotated photos as full-resolution lossless PNG instead of JPEG', async () => {
    const bytes = new Uint8Array(40), view = new DataView(bytes.buffer)
    bytes.set([255,216,255,225,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0])
    view.setUint16(22, 0x112, true); view.setUint16(24, 3, true)
    view.setUint32(26, 1, true); view.setUint16(30, 6, true)
    const image = await normalizePdfImage(new File([bytes], 'rotated.jpg'), false)
    expect(image.format).toBe('png')
    expect(image.pixels).toBe(1_920_000)
    expect(encode).toHaveBeenCalledTimes(1)
    expect(encode).toHaveBeenCalledWith(expect.any(Function), 'image/png', .92)
    expect(draw).toHaveBeenCalledWith(expect.anything(), 0, 0)
  })

})
