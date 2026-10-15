import { afterEach, describe, expect, it, vi } from 'vitest'

describe('supplier API', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.resetModules() })
  it('uses the protected v1 supplier endpoints', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [], totalCount: 0, page: 0, size: 100 }) })
    vi.stubGlobal('fetch', fetchMock)
    const { domainApi } = await import('./domain')
    await domainApi.suppliers({ q: '包装', status: 'ACTIVE' })
    expect(fetchMock.mock.calls[0]?.[0]).toContain('/v1/suppliers?q=%E5%8C%85%E8%A3%85&status=ACTIVE')
  })
})
