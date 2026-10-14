import { afterEach, describe, expect, it, vi } from 'vitest'

describe('persistent selection API routing', () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.resetModules(); window.localStorage.clear() })

  it('uses formal v1 endpoints without synthetic envelopes in password mode', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'password')
    const candidate = { id: 'c1', asin: 'B0FORMAL001', title: '正式候选', status: 'NEW', score: 50, tags: [], source: 'MANUAL' }
    const detail = { candidate, notes: [], evidence: [] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [candidate] })
      .mockResolvedValueOnce({ ok: true, json: async () => candidate })
      .mockResolvedValueOnce({ ok: true, json: async () => detail })
      .mockResolvedValueOnce({ ok: true, json: async () => detail })
      .mockResolvedValueOnce({ ok: true, json: async () => detail })
    vi.stubGlobal('fetch', fetchMock)
    const { domainApi } = await import('./domain')
    expect((await domainApi.candidates()).mockData).toBe(false)
    await domainApi.createCandidate({ asinOrUrl: candidate.asin, title: candidate.title })
    await domainApi.candidateDetail(candidate.id)
    await domainApi.addCandidateNote(candidate.id, 'note')
    await domainApi.addCandidateEvidence(candidate.id, { name: 'proof', url: 'https://example.com/proof' })
    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/v1/selection/candidates'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/v1/selection/candidates'), expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenNthCalledWith(4, expect.stringContaining('/v1/selection/candidates/c1/notes'), expect.objectContaining({ method: 'POST' }))
  })
})
