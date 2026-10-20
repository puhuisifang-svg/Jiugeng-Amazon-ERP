import { afterEach, expect, it, vi } from 'vitest'
import { blankCustoms, customsApi } from './customs'
afterEach(() => vi.unstubAllGlobals())
it('encodes query and versioned profiles, normalizes empty weights, and surfaces conflicts', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [], totalCount: 0 }) }); vi.stubGlobal('fetch', fetch)
  await customsApi.list('合成 & A', 'MISSING', 2)
  expect(fetch.mock.calls[0]![0]).toContain('state=MISSING&page=2&size=20')
  await customsApi.save('v', { ...blankCustoms(), originCountry: 'cn', netWeightKg: '', version: 3 })
  expect(JSON.parse(fetch.mock.calls[1]![1].body)).toMatchObject({ originCountry: 'CN', netWeightKg: null, version: 3 })
  fetch.mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({ detail: '请刷新资料' }) })
  await expect(customsApi.save('v', blankCustoms())).rejects.toThrow('请刷新资料')
})
