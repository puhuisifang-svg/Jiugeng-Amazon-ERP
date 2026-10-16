import { beforeEach, describe, expect, it, vi } from 'vitest'
import { purchaseApi } from './purchases'

describe('purchaseApi', () => {
  beforeEach(() => { window.sessionStorage.setItem('jiugeng.session.token', 'token'); vi.restoreAllMocks() })
  it('creates and receives purchase orders through authenticated endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'po-1' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'r-1', replayed: false }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    await purchaseApi.create({ orderType: 'PRODUCT', supplierId: 's-1', currency: 'CNY', lines: [{ itemId: 'sku-1', quantity: 2, unitPrice: 10 }] })
    await purchaseApi.receive('po-1', { requestId: 'request-1', receivedAt: '2026-10-16T08:00:00Z', lines: [{ orderLineId: 'line-1', quantity: 2 }] })
    expect(fetchMock.mock.calls[0]?.[0]).toContain('/v1/purchases/orders')
    expect(fetchMock.mock.calls[1]?.[0]).toContain('/v1/purchases/orders/po-1/receipts')
    expect((fetchMock.mock.calls[0]?.[1]?.headers as Headers).get('Authorization')).toBe('Bearer token')
  })
})
