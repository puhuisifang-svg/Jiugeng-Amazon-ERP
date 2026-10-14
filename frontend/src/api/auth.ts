import { capabilitiesForRole } from '@/auth/capabilities'
import { runtimeConfig } from '@/config/runtime'
import type { AppRole, Capability } from '@/router/meta'

import { apiRequest, assertSyntheticEnvelope } from './client'

export interface AppUser {
  id: string
  loginName: string
  displayName: string
  email?: string | null
  phone?: string | null
  role: AppRole
  status: 'ENABLED' | 'DISABLED'
  mustChangePassword: boolean
  lockedAt?: string | null
  capabilities: Capability[]
}

export interface LoginInput { loginName?: string; password?: string; role?: AppRole }
export interface LoginResponse { token: string; expiresAt?: string; user: AppUser }
export type RecoveryChannel = 'EMAIL' | 'SMS'

interface MockLoginResponse {
  token: string
  user: { id: string; displayName: string; role: AppRole }
  mockData: boolean
  source: 'SYNTHETIC'
}

function adaptMockUser(user: MockLoginResponse['user']): AppUser {
  return {
    ...user,
    loginName: user.id,
    status: 'ENABLED',
    mustChangePassword: false,
    capabilities: capabilitiesForRole(user.role),
  }
}

export async function login(input: LoginInput): Promise<LoginResponse> {
  if (runtimeConfig.authMode === 'mock') {
    const response = await apiRequest<MockLoginResponse>('/auth/login', {
      method: 'POST', body: JSON.stringify({ role: input.role ?? 'ADMIN' }),
    })
    assertSyntheticEnvelope(response)
    return { token: response.token, user: adaptMockUser(response.user) }
  }
  return apiRequest<LoginResponse>('/v1/auth/login', {
    method: 'POST', body: JSON.stringify({ loginName: input.loginName, password: input.password }), authenticated: false,
  })
}

export async function loadCurrentUser(): Promise<AppUser> {
  if (runtimeConfig.authMode === 'mock') {
    const response = await apiRequest<MockLoginResponse>('/auth/me')
    assertSyntheticEnvelope(response)
    return adaptMockUser(response.user)
  }
  return apiRequest<AppUser>('/v1/auth/me')
}

export async function logout(): Promise<void> {
  if (runtimeConfig.authMode === 'password') await apiRequest<void>('/v1/auth/logout', { method: 'POST' })
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiRequest<void>('/v1/auth/change-password', {
    method: 'POST', body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export const passwordRecoveryApi = {
  request: (input: { channel: RecoveryChannel; destination: string }) =>
    apiRequest<{ message: string }>('/v1/auth/password-reset/request', {
      method: 'POST', body: JSON.stringify(input), authenticated: false,
    }),
  confirm: (input: { channel: RecoveryChannel; destination: string; code: string; newPassword: string }) =>
    apiRequest<void>('/v1/auth/password-reset/confirm', {
      method: 'POST', body: JSON.stringify(input), authenticated: false,
    }),
}

export const usersApi = {
  list: () => apiRequest<AppUser[]>('/v1/users'),
  create: (input: { loginName: string; displayName: string; email?: string; phone?: string; initialPassword: string; role: AppRole }) =>
    apiRequest<AppUser>('/v1/users', { method: 'POST', body: JSON.stringify(input) }),
  disable: (id: string) => apiRequest<AppUser>(`/v1/users/${id}/disable`, { method: 'POST' }),
  enable: (id: string) => apiRequest<AppUser>(`/v1/users/${id}/enable`, { method: 'POST' }),
  unlock: (id: string) => apiRequest<AppUser>(`/v1/users/${id}/unlock`, { method: 'POST' }),
  setRole: (id: string, role: AppRole) => apiRequest<AppUser>(`/v1/users/${id}/role`, {
    method: 'POST', body: JSON.stringify({ role }),
  }),
  resetPassword: (id: string, newPassword: string) => apiRequest<AppUser>(`/v1/users/${id}/reset-password`, {
    method: 'POST', body: JSON.stringify({ newPassword }),
  }),
  updateRecoveryContacts: (id: string, email: string, phone: string) =>
    apiRequest<AppUser>(`/v1/users/${id}/recovery-contacts`, {
      method: 'POST', body: JSON.stringify({ email, phone }),
    }),
}
