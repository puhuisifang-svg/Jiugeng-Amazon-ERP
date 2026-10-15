import { runtimeConfig } from '@/config/runtime'

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message)
  }
}

export const SESSION_TOKEN_KEY = 'jiugeng.session.token'
export const SESSION_EXPIRED_EVENT = 'jiugeng:session-expired'

export function getSessionToken(): string | null {
  return window.localStorage.getItem(SESSION_TOKEN_KEY) ?? window.sessionStorage.getItem(SESSION_TOKEN_KEY)
}

export function setSessionToken(token: string, persistent: boolean) {
  window.localStorage.removeItem(SESSION_TOKEN_KEY)
  window.sessionStorage.removeItem(SESSION_TOKEN_KEY)
  const storage = persistent ? window.localStorage : window.sessionStorage
  storage.setItem(SESSION_TOKEN_KEY, token)
}

export function clearSessionToken() {
  window.localStorage.removeItem(SESSION_TOKEN_KEY)
  window.sessionStorage.removeItem(SESSION_TOKEN_KEY)
}

export interface ApiRequestInit extends RequestInit {
  authenticated?: boolean
}

export interface SyntheticEnvelope {
  mockData: true
  source: 'SYNTHETIC'
}

export function assertSyntheticEnvelope<T>(value: T): asserts value is T & SyntheticEnvelope {
  if (!value || typeof value !== 'object') throw new Error('Invalid synthetic API response')
  const envelope = value as Partial<SyntheticEnvelope>
  if (envelope.mockData !== true || envelope.source !== 'SYNTHETIC') {
    throw new Error('Refusing to render data that is not explicitly synthetic')
  }
}

export async function apiRequest<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const { authenticated = true, ...requestInit } = init
  const token = getSessionToken()
  const headers = new Headers(requestInit.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (authenticated && token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, { ...requestInit, headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    if (response.status === 401 && authenticated) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.detail ?? `请求失败（${response.status}）`)
  }
  if (response.status === 204) return undefined as T
  return response.json().catch(() => undefined as T)
}
