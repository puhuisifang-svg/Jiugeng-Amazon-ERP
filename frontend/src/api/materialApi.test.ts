import { afterEach, describe, expect, it, vi } from 'vitest'

describe('persistent material API routing', () => {
  afterEach(() => {
    vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.resetModules(); window.localStorage.clear()
  })

  it('uses formal v1 endpoints in password mode', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    const material = { id: 'm1', materialCode: 'MAT-1', name: '纸箱', materialType: '包装', unit: 'PCS', status: 'ACTIVE' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [material] })
      .mockResolvedValueOnce({ ok: true, json: async () => material })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ ...material, status: 'DISABLED' }) })
    vi.stubGlobal('fetch', fetchMock)
    const { domainApi } = await import('./domain')
    expect((await domainApi.materials()).mockData).toBe(false)
    await domainApi.updateMaterial('m1', { materialCode: 'MAT-1', name: '加厚纸箱', materialType: '包装', unit: 'PCS' })
    await domainApi.disableMaterial('m1')
    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/v1/materials'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/v1/materials/m1'), expect.objectContaining({ method: 'PUT' }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, expect.stringContaining('/v1/materials/m1/disable'), expect.objectContaining({ method: 'POST' }))
  })
})
