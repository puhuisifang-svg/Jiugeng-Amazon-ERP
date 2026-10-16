import { afterEach, describe, expect, it, vi } from 'vitest'

import { dashboardApi } from './dashboard'
import { SESSION_TOKEN_KEY } from './client'

describe('dashboardApi', () => {
  afterEach(() => { window.localStorage.clear(); window.sessionStorage.clear(); vi.unstubAllGlobals() })

  it('encodes maintenance filters and requests the formal V1 endpoint', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) }))
    await dashboardApi.page('pending-products', { keyword: '新品 A', status: 'PENDING', page: 0, size: 20 })
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      expect.stringContaining('/v1/dashboard-maintenance/pending-products?keyword=%E6%96%B0%E5%93%81+A&status=PENDING&page=0&size=20'),
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
  })

  it.each(['localStorage', 'sessionStorage'] as const)('uploads and retrieves images with %s login', async (storage) => {
    window[storage].setItem(SESSION_TOKEN_KEY, 'protected-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'img-1', purpose: 'SHIPMENT' }) })
      .mockResolvedValueOnce({ ok: true, blob: async () => new Blob(['png'], { type: 'image/png' }) })
    vi.stubGlobal('fetch', fetchMock)
    await dashboardApi.uploadImage('SHIPMENT', new File(['png'], 'pattern.png', { type: 'image/png' }))
    await dashboardApi.imageBlob('img-1', 'thumbnail')

    const upload = fetchMock.mock.calls[0]![1] as RequestInit
    expect(upload.body).toBeInstanceOf(FormData)
    expect(new Headers(upload.headers).get('Authorization')).toBe('Bearer protected-token')
    expect(new Headers(upload.headers).has('Content-Type')).toBe(false)
    const image = fetchMock.mock.calls[1]![1] as RequestInit
    expect(new Headers(image.headers).get('Authorization')).toBe('Bearer protected-token')
  })
})
