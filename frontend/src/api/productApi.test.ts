import { afterEach, describe, expect, it, vi } from 'vitest'

describe('persistent product API routing', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    vi.resetModules()
    window.localStorage.clear()
  })

  it('uses the formal v1 endpoint without requiring a synthetic envelope in password mode', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [{
        id: 'product-1', productCode: 'PROD-1', productName: '正式产品', status: 'ACTIVE', version: 0,
        supplierDetailsAvailable: true, skuCount: 1,
        variants: [{ id: 'variant-1', skuCode: 'SKU-1', variantName: '黑色', status: 'ACTIVE', version: 0, attributes: [], suppliers: [], materials: [] }],
      }],
    })
    vi.stubGlobal('fetch', fetchMock)

    const { domainApi } = await import('./domain')
    const response = await domainApi.products()

    expect(response.mockData).toBe(false)
    expect(response.items[0]?.productName).toBe('正式产品')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/v1/products'), expect.any(Object))
  })
})
