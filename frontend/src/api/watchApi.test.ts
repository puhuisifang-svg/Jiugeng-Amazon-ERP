import { afterEach, describe, expect, it, vi } from 'vitest'
describe('persistent watch API routing', () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.resetModules(); window.localStorage.clear() })
  it('uses formal store, manual snapshot and event endpoints in password mode', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password'); vi.stubGlobal('crypto', { randomUUID: () => 'request-1' })
    const store = { id: 's1', storeName: 'Store', sourceUrl: 'https://example.com', status: 'ACTIVE', baselineAsinCount: 0 }
    const event = { id: 'e1', storeName: 'Store', asin: 'B0WATCH0001', detectedAt: 'now', status: 'PENDING_REVIEW' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [store] })
      .mockResolvedValueOnce({ ok: true, json: async () => store })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ item: store, events: [event], baselineChanged: true }) })
      .mockResolvedValueOnce({ ok: true, json: async () => [event] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ item: { ...event, status: 'CONFIRMED' }, candidate: { id: 'c1' }, replayed: false }) })
    vi.stubGlobal('fetch', fetchMock); const { domainApi } = await import('./domain')
    expect((await domainApi.watchStores()).mockData).toBe(false)
    await domainApi.createWatchStore({ storeKey: 'STORE', storeName: 'Store', sourceUrl: 'https://example.com' })
    await domainApi.runWatchStore('s1', 'COMPLETE', ['B0WATCH0001'])
    await domainApi.events(); await domainApi.confirmEvent('e1')
    expect(fetchMock).toHaveBeenNthCalledWith(3, expect.stringContaining('/v1/watch/stores/s1/runs'), expect.objectContaining({ method: 'POST', body: expect.stringContaining('B0WATCH0001') }))
    expect(fetchMock).toHaveBeenNthCalledWith(5, expect.stringContaining('/v1/watch/events/e1/confirm'), expect.objectContaining({ method: 'POST' }))
  })
})
