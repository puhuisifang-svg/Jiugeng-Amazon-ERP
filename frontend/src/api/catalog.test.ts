import { afterEach, describe, expect, it, vi } from 'vitest'

describe('catalog image API', () => {
  afterEach(() => { vi.unstubAllGlobals(); window.localStorage.clear() })
  it('downloads protected original images with a usable name and actual format', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => new Blob(['png'], { type: 'image/png' }) })
    vi.stubGlobal('fetch', fetchMock)
    const createObjectURL = vi.fn(() => 'blob:preview'); const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', Object.assign(class extends URL {}, { createObjectURL, revokeObjectURL }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('合成产品.jpg.png')
      expect(this.href).toBe('blob:preview')
    })
    const { catalogApi, imageFileName } = await import('./catalog')
    await catalogApi.downloadImage({ imageAssetId: 'img-1', variantName: '合成产品.jpg.png' })
    expect(fetchMock.mock.calls[0]![0]).toContain('/img-1/original')
    expect(click).toHaveBeenCalledOnce()
    expect(imageFileName('产品.jpg', 'image/png')).toBe('产品.png')
    expect(imageFileName('a/b', 'image/jpeg')).toBe('a_b.jpg')
    expect(() => imageFileName('产品', 'text/html')).toThrow()
    click.mockRestore()
  })
  it('uploads protected catalog images without forcing a JSON content type', async () => {
    window.localStorage.setItem('jiugeng.session.token', 'token')
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 'img-1' }) })
    vi.stubGlobal('fetch', fetchMock)
    const { catalogApi } = await import('./catalog')
    await catalogApi.uploadImage('SKU_MAIN', new File(['png'], 'sku.png', { type: 'image/png' }))
    const init = fetchMock.mock.calls[0]![1] as RequestInit
    expect(init.body).toBeInstanceOf(FormData)
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer token')
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  })
  it('reuses the server thumbnail returned by a successful upload', async () => {
    window.localStorage.setItem('jiugeng.session.token', 'preview-session')
    const fetchMock=vi.fn().mockResolvedValue({ok:true,json:async()=>({id:'new-image',mediaType:'image/png',thumbnailBase64:btoa('server bytes')})})
    vi.stubGlobal('fetch',fetchMock)
    const {catalogApi}=await import('./catalog')
    await catalogApi.uploadImage('SKU_MAIN',new File(['source'],'synthetic.png',{type:'image/png'}))
    const thumbnail=await catalogApi.imageBlob('new-image')
    expect(thumbnail.size).toBe(12);expect(thumbnail.type).toBe('image/png');expect(fetchMock).toHaveBeenCalledTimes(1)
  })

})
