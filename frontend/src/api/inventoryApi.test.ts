import { afterEach, describe, expect, it, vi } from 'vitest'

describe('formal inventory API', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('uses versioned balance, ledger and command endpoints in password mode', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    vi.stubEnv('VITE_API_BASE_URL', 'http://inventory.test/api')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [] })
      .mockResolvedValueOnce({ ok: true, json: async () => [] })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ item: {}, balance: {}, replayed: false }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ materialId: 'm1', safetyStock: 5 }) })
    vi.stubGlobal('fetch', fetchMock)
    const { domainApi } = await import('./domain')

    await domainApi.balances()
    await domainApi.ledger('m1')
    await domainApi.transact({ requestId: 'req-1', materialId: 'm1', type: 'OUTBOUND', quantity: 2,
      unit: 'PCS', reason: '领用' })
    await domainApi.updateSafetyStock('m1', 5)

    expect(fetchMock.mock.calls[0]?.[0]).toContain('/v1/inventory/balances')
    expect(fetchMock.mock.calls[1]?.[0]).toContain('/v1/inventory/transactions?materialId=m1')
    expect(fetchMock.mock.calls[2]?.[0]).toContain('/v1/inventory/transactions')
    expect(fetchMock.mock.calls[2]?.[1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ requestId: 'req-1', materialId: 'm1', type: 'OUTBOUND', quantity: 2,
        unit: 'PCS', reason: '领用' }),
    }))
    expect(fetchMock.mock.calls[3]?.[0]).toContain('/v1/inventory/balances/m1/safety-stock')
  })
})
