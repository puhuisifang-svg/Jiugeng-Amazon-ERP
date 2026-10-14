import { afterEach, describe, expect, it, vi } from 'vitest'

import { domainApi } from './domain'

describe('domainApi synthetic write safety', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('fails closed when a write response is not explicitly synthetic', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        mockData: false,
        source: 'UNKNOWN',
        item: { id: 'p1' },
      }),
    }))

    await expect(domainApi.createProduct({ productCode: 'P-1', name: 'Product', skuCodes: ['S-1'] }))
      .rejects.toThrow('not explicitly synthetic')
  })

  it('sends an immutable inventory request identifier with the transaction', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        mockData: true,
        source: 'SYNTHETIC',
        item: { id: 'l1', requestId: 'req-1', materialCode: 'M-1', type: 'INBOUND', quantity: 2, reason: 'test', occurredAt: 'now' },
        balance: { warehouseCode: 'US-WH-01', materialCode: 'M-1', materialName: 'Box', quantity: 2, unit: 'PCS' },
        replayed: false,
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await domainApi.transact({ requestId: 'req-1', materialCode: 'M-1', type: 'INBOUND', quantity: 2, reason: 'test' })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/inventory/transactions'), expect.objectContaining({
      body: JSON.stringify({ requestId: 'req-1', materialCode: 'M-1', type: 'INBOUND', quantity: 2, reason: 'test' }),
    }))
  })

  it('only sends rejectionReason for a rejected candidate transition', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ mockData: true, source: 'SYNTHETIC', item: { id: 'c1', asin: 'B0001', title: 'Item', status: 'REJECTED', score: 10, rejectionReason: 'low score' } }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await domainApi.transitionCandidate('c1', { status: 'REJECTED', rejectionReason: 'low score' })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/selection/candidates/c1/transition'), expect.objectContaining({
      body: JSON.stringify({ status: 'REJECTED', rejectionReason: 'low score' }),
    }))
  })

  it('creates a candidate from an ASIN or Amazon URL input', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ mockData: true, source: 'SYNTHETIC', item: { id: 'c2', asin: 'B0TEST12345', title: 'Item', status: 'NEW', score: 68, sourceUrl: 'https://www.amazon.com/dp/B0TEST12345' } }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await domainApi.createCandidate({ asinOrUrl: 'https://www.amazon.com/dp/B0TEST12345', title: 'Item', score: 68 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/selection/candidates'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ asinOrUrl: 'https://www.amazon.com/dp/B0TEST12345', title: 'Item', score: 68 }),
    }))
  })

  it('updates traceable candidate evaluation fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ mockData: true, source: 'SYNTHETIC', item: { id: 'c1', asin: 'B0TEST12345', title: 'Item', status: 'REVIEWING', owner: 'Owner', tags: ['tag'] } }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await domainApi.updateCandidateEvaluation('c1', { category: 'Home', price: 19.9, rating: 4.5, reviewCount: 120, owner: 'Owner', tags: ['tag'], selectionReason: 'Reason' })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/selection/candidates/c1/evaluation'), expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ category: 'Home', price: 19.9, rating: 4.5, reviewCount: 120, owner: 'Owner', tags: ['tag'], selectionReason: 'Reason' }),
    }))
  })

  it('runs a synthetic watch snapshot with an explicit safety mode', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ item: { id: 'store-1', storeName: 'Store', sourceUrl: 'https://example.test', status: 'ACTIVE', baselineAsinCount: 0 }, events: [], baselineChanged: false, mockData: true, source: 'SYNTHETIC' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await domainApi.runWatchStore('store-1', 'INCOMPLETE')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/watch/stores/store-1/runs'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ mode: 'INCOMPLETE' }),
    }))
  })
})
