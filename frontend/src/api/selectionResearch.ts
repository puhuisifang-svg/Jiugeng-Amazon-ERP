import { apiRequest } from './client'
export interface Criterion { score: number | null; reason: string }
export interface Research { version?: number; customer: string; differentiation: string; supply: string; riskStatus: string; riskReason: string; criteria: Criterion[]; estimateId: string; conclusion: string }
export interface Cost { name: string; amount: number | null; currency: string; exchangeRate: number | null; basis: string }
export interface EstimateInput { version?: number; requestId: string; name: string; price: number | null; currency: string; costs: Cost[]; commissionRate: number | null; advertisingRate: number | null; lossRate: number | null; rateDate: string; basis: string }
export interface Estimate { id: string; revision: number; input: EstimateInput; result: { unitCost: number; profit: number; margin: number; breakEven: number | null; lowPriceProfit: number; highCostProfit: number }; createdAt: string }
export interface ResearchView { version: number; research: Partial<Research>; weightedScore: number | null; estimates: Estimate[] }
const root = '/v1/selection/candidates'
export const researchApi = {
  get: (id: string) => apiRequest<ResearchView>(`${root}/${id}/research`),
  save: (id: string, input: Research) => apiRequest<ResearchView>(`${root}/${id}/research`, { method: 'PUT', body: JSON.stringify(input) }),
  estimate: (id: string, input: EstimateInput) => apiRequest<Estimate>(`${root}/${id}/estimates`, { method: 'POST', body: JSON.stringify(input) }),
}
