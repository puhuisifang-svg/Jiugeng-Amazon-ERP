import { runtimeConfig } from '@/config/runtime'

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
  const response = await fetch(`${runtimeConfig.apiBaseUrl}/dashboard/summary`)

  if (!response.ok) {
    throw new Error(`Dashboard summary request failed with status ${response.status}`)
  }

  return response.json() as Promise<DashboardSummary>
}
