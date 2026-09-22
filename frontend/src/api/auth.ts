import type { AppRole } from '@/router/meta'

import { apiRequest, assertSyntheticEnvelope } from './client'

export interface MockUser {
  id: string
  displayName: string
  role: AppRole
}

export interface LoginResponse {
  token: string
  user: MockUser
  mockData: boolean
  source: 'SYNTHETIC'
}

export interface MeResponse {
  user?: MockUser
  id?: string
  displayName?: string
  role?: AppRole
  mockData: boolean
  source: 'SYNTHETIC'
}

export async function loginAs(role: AppRole): Promise<LoginResponse> {
  const response = await apiRequest<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ role }) })
  assertSyntheticEnvelope(response)
  return response
}

export async function loadCurrentUser(): Promise<MockUser> {
  const response = await apiRequest<MeResponse>('/auth/me')
  assertSyntheticEnvelope(response)
  const user = response.user ?? response
  if (!user.id || !user.displayName || !user.role) throw new Error('Invalid /auth/me response')
  return { id: user.id, displayName: user.displayName, role: user.role }
}
