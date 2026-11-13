import { apiRequest } from './client'
export const materialTypesApi = {
  units: () => apiRequest<string[]>('/v1/materials/unit-options'),
  removeUnit: (name: string) => apiRequest<void>('/v1/materials/unit-options/remove', {method:'POST',body:JSON.stringify({name})}),
  list: () => apiRequest<string[]>('/v1/materials/type-options'),
  remove: (name: string) => apiRequest<void>('/v1/materials/type-options/remove', {method:'POST',body:JSON.stringify({name})}),
}
