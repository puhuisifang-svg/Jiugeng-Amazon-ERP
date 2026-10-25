import { apiRequest } from './client'
export interface CustomsProfile {
  nameZh: string; nameEn: string; composition: string; usage: string; brand: string; model: string
  originCountry: string; unit: string; netWeightKg: number | string | null; grossWeightKg: number | string | null
  chinaCode: string; usCode: string; euCode: string; ukCode: string
  declarationElements: string; complianceNotes: string; remark: string; state: 'DRAFT' | 'REVIEWED'; version: number
}
export const blankCustoms = (): CustomsProfile => ({ nameZh: '', nameEn: '', composition: '', usage: '', brand: '', model: '', originCountry: '', unit: '', netWeightKg: null, grossWeightKg: null, chinaCode: '', usCode: '', euCode: '', ukCode: '', declarationElements: '', complianceNotes: '', remark: '', state: 'DRAFT', version: 0 })
export function customsInput(value: CustomsProfile): CustomsProfile {
  return { ...value, originCountry: (value.originCountry ?? '').trim().toUpperCase(), netWeightKg: value.netWeightKg === '' ? null : value.netWeightKg, grossWeightKg: value.grossWeightKg === '' ? null : value.grossWeightKg }
}
export interface CustomsRow { productId: string; productCode: string; productName: string; variantId: string; skuCode: string; skuName: string; productStatus: string; skuStatus: string; customs: CustomsProfile | null }
export const customsApi = {
  list: (q: string, state: string, page: number, size = 20) => apiRequest<{ items: CustomsRow[]; totalCount: number }>(`/v1/product-customs?${new URLSearchParams({ q, state, page: String(page), size: String(size) })}`),
  save: (id: string, value: CustomsProfile) => apiRequest<CustomsProfile>(`/v1/product-customs/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(customsInput(value)) }),
}
