import { apiRequest, assertSyntheticEnvelope } from './client'

export interface DashboardSummaryItem {
  label: string
  value: number
  note: string
}

export interface DashboardSummary {
  mockData: boolean
  source: 'SYNTHETIC'
  generatedAt: string
  items: DashboardSummaryItem[]
}

export async function loadDashboardSummary(): Promise<DashboardSummary> {
  const summary = await apiRequest<DashboardSummary>('/dashboard/summary')
  assertSyntheticEnvelope(summary)
  return summary
}
