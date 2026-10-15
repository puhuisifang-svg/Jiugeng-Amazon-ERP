import { afterEach, describe, expect, it, vi } from 'vitest'

describe('catalog image API', () => {
  afterEach(() => { vi.unstubAllGlobals(); window.localStorage.clear() })
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
})
