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
  actorLoginName?: string | undefined
  category?: string | undefined
  action?: string | undefined
  resourceType?: string | undefined
  outcome?: AuditOutcome | '' | undefined
  from?: string | undefined
  to?: string | undefined
  page?: number | undefined
  size?: number | undefined
}

export function auditQueryString(query: AuditQuery): string {
  const parameters = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  }
  const encoded = parameters.toString()
  return encoded ? `?${encoded}` : ''
}

export const auditApi = {
  events: (query: AuditQuery = {}) => apiRequest<AuditPage>(`/v1/audit/events${auditQueryString(query)}`),
}
