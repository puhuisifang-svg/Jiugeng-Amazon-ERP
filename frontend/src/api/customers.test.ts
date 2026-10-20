import { afterEach, describe, expect, it, vi } from 'vitest'
import { customersApi } from './customers'

afterEach(() => { vi.unstubAllGlobals() })
describe('customer API', () => {
  it('encodes customer filters and retains paging', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [] }) }); vi.stubGlobal('fetch', fetch)
    await customersApi.products({ q: '客户 & SKU', customerId: 'c1', status: 'ACTIVE', page: 2, size: 20 })
    expect(fetch.mock.calls[0]![0]).toContain('/v1/customers/products?q=%E5%AE%A2%E6%88%B7+%26+SKU&customerId=c1&status=ACTIVE&page=2&size=20')
  })
  it('uses versioned update payloads and propagates conflict errors', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ code: 'CUSTOMER_SKU_DUPLICATE', message: '请编辑现有记录' }) }); vi.stubGlobal('fetch', fetch)
    const input = { customerId: 'c1', productId: 'p1', variantId: 'v1', supplyPrice: '12.50', priceUnit: '套', customerProductCode: 'SYN', requirements: '', status: 'ACTIVE' as const, version: 4 }
    await expect(customersApi.saveProduct('cp1', input)).rejects.toThrow('请编辑现有记录')
    expect(fetch.mock.calls[0]![0]).toContain('/v1/customers/products/cp1')
    expect(fetch.mock.calls[0]![1]).toEqual(expect.objectContaining({ method: 'PUT', body: JSON.stringify(input) }))
  })
})
