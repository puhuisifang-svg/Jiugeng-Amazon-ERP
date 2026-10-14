import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/config/runtime', () => ({
  runtimeConfig: { apiBaseUrl: 'http://localhost:8080/api', authMode: 'password' },
}))

import { changePassword, login, logout, usersApi } from './auth'
import { SESSION_TOKEN_KEY } from './client'

describe('password authentication API', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('sends credentials to the versioned login endpoint', async () => {
    const response = {
      token: 'opaque-test-token',
      expiresAt: '2026-10-15T08:00:00Z',
      user: {
        id: 'user-1',
        loginName: 'admin',
        displayName: '管理员',
        role: 'ADMIN',
        status: 'ENABLED',
        mustChangePassword: true,
        capabilities: ['system.users.manage'],
      },
    }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => response })
    vi.stubGlobal('fetch', fetchMock)

    await expect(login({ loginName: 'admin', password: 'test-password' })).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ loginName: 'admin', password: 'test-password' }),
      }),
    )
  })

  it('uses the bearer token for password changes and logout', async () => {
    window.localStorage.setItem(SESSION_TOKEN_KEY, 'opaque-test-token')
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({}) })
    vi.stubGlobal('fetch', fetchMock)

    await changePassword('old-password', 'new-test-password')
    await logout()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    for (const call of fetchMock.mock.calls) {
      const headers = call[1]?.headers as Headers
      expect(headers.get('Authorization')).toBe('Bearer opaque-test-token')
    }
  })

  it('surfaces structured account errors from the server', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 423,
      json: async () => ({ code: 'ACCOUNT_LOCKED', detail: '账号已锁定' }),
    }))

    await expect(login({ loginName: 'locked', password: 'wrong-password' }))
      .rejects.toMatchObject({ status: 423, code: 'ACCOUNT_LOCKED', message: '账号已锁定' })
  })

  it('calls the administrator account endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [] })
    vi.stubGlobal('fetch', fetchMock)

    await usersApi.list()

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/users',
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
  })
})
