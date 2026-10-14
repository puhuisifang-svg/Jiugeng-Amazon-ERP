import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/config/runtime', () => ({
  runtimeConfig: { apiBaseUrl: 'http://localhost:8080/api', authMode: 'password' },
}))

import { auditApi, auditQueryString } from './audit'
import { SESSION_TOKEN_KEY } from './client'

describe('audit API', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('encodes supported audit filters without empty values', () => {
    expect(auditQueryString({
      actorLoginName: 'operator',
      action: 'PASSWORD_RESET',
      outcome: '',
      page: 2,
      size: 20,
    })).toBe('?actorLoginName=operator&action=PASSWORD_RESET&page=2&size=20')
  })

  it('queries audit events with the active bearer session', async () => {
    window.localStorage.setItem(SESSION_TOKEN_KEY, 'opaque-test-token')
    const response = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => response })
    vi.stubGlobal('fetch', fetchMock)

    await expect(auditApi.events({ category: 'ACCOUNT', page: 0, size: 20 })).resolves.toEqual(response)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/v1/audit/events?category=ACCOUNT&page=0&size=20')
    expect((init.headers as Headers).get('Authorization')).toBe('Bearer opaque-test-token')
  })
})
