import { apiRequest } from './client'
import type { AppUser } from './auth'
export interface RoleRef { id: string; name: string; status: string }
export interface Permission { code: string; module: string; label: string; dependencies: string[]; sensitive: boolean; assignable: boolean }
export interface Role { id: string; name: string; description: string; kind: 'BUILTIN' | 'CUSTOM'; status: 'ENABLED' | 'DISABLED'; version: number; permissions: string[]; memberCount: number; updatedAt: string }
export interface RoleEdit { name: string; description: string; status: string; permissions: string[]; version: number; reason: string }
export interface Assignment { roleIds: string[]; version: number; reason: string }
export interface PermissionPreview { added: string[]; removed: string[]; members: AppUser[] }
export interface RoleHistory { id: string; action: string; actorLoginName: string; actorDisplayName: string; beforeValue: unknown; afterValue: unknown; detail: string; occurredAt: string }
const body = (method: string, value: unknown) => ({ method, body: JSON.stringify(value) })
export const rolesApi = {
  list: () => apiRequest<Role[]>('/v1/roles'),
  permissions: () => apiRequest<Permission[]>('/v1/permissions'),
  create: (value: { name: string; description: string; copyFrom?: string | undefined }) => apiRequest<Role>('/v1/roles', body('POST', value)),
  preview: (id: string, value: RoleEdit) => apiRequest<PermissionPreview>(`/v1/roles/${id}/preview`, body('POST', value)),
  save: (id: string, value: RoleEdit) => apiRequest<Role>(`/v1/roles/${id}`, body('PUT', value)),
  remove: (id: string, version: number, reason: string) => apiRequest<void>(`/v1/roles/${id}`, body('DELETE', { version, reason })),
  members: (id: string) => apiRequest<AppUser[]>(`/v1/roles/${id}/members`),
  history: (id: string) => apiRequest<RoleHistory[]>(`/v1/roles/${id}/history`),
  userHistory: (id: string, page=1, size=20) => apiRequest<{items:RoleHistory[];total:number;page:number;size:number}>(`/v1/users/${id}/history?page=${page}&size=${size}`),
  sources: (id: string) => apiRequest<Record<string, RoleRef[]>>(`/v1/users/${id}/permission-sources`),
  previewAssignment: (id: string, value: Assignment) => apiRequest<PermissionPreview>(`/v1/users/${id}/roles/preview`, body('POST', value)),
  assign: (id: string, value: Assignment) => apiRequest<AppUser>(`/v1/users/${id}/roles`, body('PUT', value)),
  createUser: (value: { loginName: string; displayName: string; email: string; phone: string; initialPassword: string; roleIds: string[] }) => apiRequest<AppUser>('/v1/users/with-roles', body('POST', value)),
  profile: (id: string, value: { displayName: string; email: string; phone: string }) => apiRequest<AppUser>(`/v1/users/${id}/profile`, body('PUT', value)),
}
