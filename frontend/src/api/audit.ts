import { apiRequest } from './client'

export type AuditOutcome = 'SUCCESS' | 'FAILURE'

export interface AuditEvent {
  id: string
  category: string
  action: string
  actorUserId?: string | null
  actorLoginName: string
  actorDisplayName?: string | null
  resourceType: string
  resourceId?: string | null
  outcome: AuditOutcome
  beforeValue?: Record<string, unknown> | null
  afterValue?: Record<string, unknown> | null
  detail?: string | null
  occurredAt: string
}

export interface AuditPage {
  items: AuditEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditQuery {
  actorLoginName?: string | string[] | undefined
  category?: string | string[] | undefined
  action?: string | string[] | undefined
  resourceType?: string | string[] | undefined
  outcome?: AuditOutcome | '' | undefined
  from?: string | undefined
  to?: string | undefined
  page?: number | undefined
  size?: number | undefined
}

export function auditQueryString(query: AuditQuery): string {
  const parameters = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (Array.isArray(value)) value.forEach(item => parameters.append(key, item))
    else if (value !== undefined && value !== '') parameters.set(key, String(value))
  }
  const encoded = parameters.toString()
  return encoded ? `?${encoded}` : ''
}

export interface AuditOptions { actors: { value: string; label: string | null }[]; categories: string[]; actions: string[]; resources: string[] }

export const auditApi = {
  options: () => apiRequest<AuditOptions>('/v1/audit/events/options'),
  events: (query: AuditQuery = {}) => apiRequest<AuditPage>(`/v1/audit/events${auditQueryString(query)}`),
}
