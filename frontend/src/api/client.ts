import { runtimeConfig } from '@/config/runtime'

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
  }
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

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = window.localStorage.getItem('jiugeng.mock.session.token')
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    throw new ApiError(response.status, `Request ${path} failed with status ${response.status}`)
  }
  return response.json() as Promise<T>
}
