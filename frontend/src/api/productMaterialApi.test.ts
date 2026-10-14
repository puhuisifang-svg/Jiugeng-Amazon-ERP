import { afterEach, describe, expect, it, vi } from 'vitest'

describe('product material relation API routing', () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.resetModules(); window.localStorage.clear() })

  it('uses formal endpoints and URL-encodes the product filter', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [] })
    vi.stubGlobal('fetch', fetchMock)
    const { domainApi } = await import('./domain')

    await domainApi.productMaterialRelations({ productId: 'product / 1' })
    await domainApi.applyMaterialToAllVariants({ productId: 'p1', materialId: 'm1', standardQuantity: 1, unit: 'PCS', lossRate: 0 })

    expect(fetchMock).toHaveBeenNthCalledWith(1,
      expect.stringContaining('productId=product%20%2F%201'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/apply-to-all'),
      expect.objectContaining({ method: 'POST' }))
  })
})
