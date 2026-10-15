import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/config/runtime', () => ({
  runtimeConfig: { apiBaseUrl: 'http://localhost:8080/api', authMode: 'password' },
}))

import { changePassword, login, logout, passwordRecoveryApi, securitySettingsApi, usersApi } from './auth'
import { clearSessionToken, getSessionToken, SESSION_TOKEN_KEY, setSessionToken } from './client'

describe('password authentication API', () => {
  afterEach(() => {
    window.localStorage.clear()
    window.sessionStorage.clear()
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
        body: JSON.stringify({ loginName: 'admin', password: 'test-password', staySignedIn: false }),
      }),
    )
  })

  it('loads and updates the configured stay-signed-in duration', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ staySignedInDays: 7 }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ staySignedInDays: 14 }) })
    vi.stubGlobal('fetch', fetchMock)

    await expect(securitySettingsApi.loginSettings()).resolves.toEqual({ staySignedInDays: 7 })
    await expect(securitySettingsApi.update(14)).resolves.toEqual({ staySignedInDays: 14 })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/v1/auth/login-settings')
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      method: 'POST', body: JSON.stringify({ staySignedInDays: 14 }),
    }))
  })

  it('stores short sessions in session storage and remembered sessions persistently', () => {
    setSessionToken('short-token', false)
    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBe('short-token')
    expect(window.localStorage.getItem(SESSION_TOKEN_KEY)).toBeNull()
    expect(getSessionToken()).toBe('short-token')

    setSessionToken('remembered-token', true)
    expect(window.localStorage.getItem(SESSION_TOKEN_KEY)).toBe('remembered-token')
    expect(window.sessionStorage.getItem(SESSION_TOKEN_KEY)).toBeNull()
    clearSessionToken()
    expect(getSessionToken()).toBeNull()
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

  it('uses unauthenticated password recovery endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, json: async () => ({ message: '验证码已发送' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await passwordRecoveryApi.request({ channel: 'EMAIL', destination: 'user@example.com' })
    await passwordRecoveryApi.confirm({
      channel: 'EMAIL', destination: 'user@example.com', code: '123456', newPassword: 'Reset123',
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/v1/auth/password-reset/request')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/auth/password-reset/confirm')
    for (const call of fetchMock.mock.calls) {
      const headers = call[1]?.headers as Headers
      expect(headers.has('Authorization')).toBe(false)
    }
  })
})
