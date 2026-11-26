import { apiRequest } from './client'

export const inventoryOptionsApi = {
  reasons: () => apiRequest<string[]>('/v1/inventory/reason-options'),
  removeReason: (name:string) => apiRequest<void>('/v1/inventory/reason-options/remove', {method:'POST',body:JSON.stringify({name})}),
}
