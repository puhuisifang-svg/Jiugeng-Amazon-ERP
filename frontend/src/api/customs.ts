import { apiRequest } from './client'
export interface ElementField { key:string; label:string }
export interface HsSchema {code:string;productName:string;measurementUnit:string;fields:ElementField[];sourceUrl:string;sourceUpdatedAt:string;fetchedAt:string;stale:boolean}
export const hsSchemaApi={get:(code:string)=>apiRequest<HsSchema>(`/v1/hs-declaration-schemas/${encodeURIComponent(code)}`)}
export interface CustomsProfile {
  elementFields?:ElementField[]|null;schemaCode?:string|null;measurementUnit?:string|null;
  elements?: Record<string,string> | null;
  nameZh: string; nameEn: string; composition: string; usage: string; brand: string; model: string
  originCountry: string; unit: string; netWeightKg: number | string | null; grossWeightKg: number | string | null
  chinaCode: string; usCode: string; euCode: string; ukCode: string
  declarationElements: string; complianceNotes: string; remark: string; state: 'DRAFT' | 'REVIEWED'; version: number
}
export const blankCustoms = (): CustomsProfile => ({ elements: {}, nameZh: '', nameEn: '', composition: '', usage: '', brand: '', model: '', originCountry: '', unit: '', netWeightKg: null, grossWeightKg: null, chinaCode: '', usCode: '', euCode: '', ukCode: '', declarationElements: '', complianceNotes: '', remark: '', state: 'DRAFT', version: 0 })
export function customsInput(value: CustomsProfile): CustomsProfile {
  return { ...value, ...(value.elements?{declarationElements:serializeElements(value.elements,profileFields(value))}:{}), originCountry: (value.originCountry ?? '').trim().toUpperCase(), netWeightKg: value.netWeightKg === '' ? null : value.netWeightKg, grossWeightKg: value.grossWeightKg === '' ? null : value.grossWeightKg }
}
export interface CustomsRow { productId: string; productCode: string; productName: string; variantId: string; skuCode: string; skuName: string; productStatus: string; skuStatus: string; customs: CustomsProfile | null }
export const customsApi = {
  list: (q: string, state: string, page: number, size = 20) => apiRequest<{ items: CustomsRow[]; totalCount: number }>(`/v1/product-customs?${new URLSearchParams({ q, state, page: String(page), size: String(size) })}`),
  save: (id: string, value: CustomsProfile) => apiRequest<CustomsProfile>(`/v1/product-customs/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(customsInput(value)) }),
}

export const declarationElementFields=[['brandType','品牌类型'],['exportBenefit','出口享惠情况'],['floorCovering','是否为铺地制品'],['fiberComposition','纤维成分'],['dimensions','规格尺寸'],['weavingMethod','织造方法'],['fixingMethod','固定方式'],['gtin','GTIN'],['cas','CAS'],['other','其他']] as const
export function profileFields(profile:CustomsProfile):ReadonlyArray<readonly [string,string]>{return profile.elementFields?.map(f=>[f.key,f.label] as const)??declarationElementFields}
export function serializeElements(elements:Record<string,string>,fields:ReadonlyArray<readonly [string,string]>=declarationElementFields){return fields.map(([key,label])=>`${label}：${(elements[key]??'').trim()}`).join('\n')}
export function profileElements(profile:CustomsProfile){
 if(profile.elements)return {...profile.elements}
 const result:Record<string,string>={},unmatched:string[]=[]
 for(const line of (profile.declarationElements??'').split(/\r?\n/)){if(!line.trim())continue;const match=line.match(/^([^:：]+)[:：](.*)$/);const field=declarationElementFields.find(([,label])=>label===match?.[1]?.trim());if(field&&match)result[field[0]]=match[2]!.trim();else unmatched.push(line)}
 if(profile.composition&&!result.fiberComposition)unmatched.push(`原材质/成分：${profile.composition}`)
 if(profile.model&&!result.dimensions)unmatched.push(`原规格型号：${profile.model}`)
 if(unmatched.length)result.other=[result.other,...unmatched].filter(Boolean).join('\n')
 return result
}

export function applyHsSchema(profile:CustomsProfile,schema:HsSchema):CustomsProfile {
 const previous=profileElements(profile), fields=schema.fields.map(f=>({...f})), values:Record<string,string>={}
 // Only carry answers belonging to the new schema; never append removed fields to Other.
 for(const field of fields)values[field.key]=previous[field.key]??''
 return {...profile,chinaCode:schema.code,schemaCode:schema.code,elementFields:fields.map(f=>({...f})),elements:values,measurementUnit:schema.measurementUnit,state:'DRAFT'}
}
export function documentElementFields(row:Record<string,string>):ReadonlyArray<readonly [string,string]>{
 if(row.elementsVersion!=='2')return declarationElementFields
 try{return (JSON.parse(row.elementFields??'[]') as ElementField[]).map(f=>[f.key,f.label] as const)}catch{return []}
}
