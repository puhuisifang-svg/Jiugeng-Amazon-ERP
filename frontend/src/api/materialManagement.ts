import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
import type { MaterialDetailDto, MaterialInput, SupplierAssignmentDto, ProductHistoryDto, InventoryBalanceViewDto, LedgerEntryDto } from './domain'
import type { MaterialPrice } from './costs'
export interface MaterialRecord extends MaterialDetailDto { version:number }
export interface MaterialRow { material:MaterialRecord;quantity:number|null;safetyStock:number|null;initialized:boolean;netPrice:string|null;priceUnit:string|null }
export interface MaterialFilters { q:string;type:string;status:string;stock:string;supplier:string;photo:string;price:string }
export const emptyMaterialFilters=():MaterialFilters=>({q:'',type:'',status:'',stock:'',supplier:'',photo:'',price:''})
export interface MaterialSupplier extends SupplierAssignmentDto { supplierName:string;status:string;materialEligible:boolean;contactPerson?:string;phone?:string;wechatId?:string;sourceUrl?:string }
export interface MaterialDetail {material:MaterialRecord;price:MaterialPrice|null;suppliers:MaterialSupplier[];unitLocked:boolean;quantityScale:number}
export interface MaterialSave {requestId:string;id:string|null;version:number|null;material:MaterialInput;suppliers?:SupplierAssignmentDto[];copyFromId?:string;cost?:{netPrice:string|null;version:number}}
export interface MaterialImpact {id:string;name:string;products:number;purchases:number;transactions:number;quantity:number;canDelete:boolean;unitLocked:boolean}
export interface MaterialProduct {productId:string;variantId:string;productName:string;name:string;quantity:number;unit:string;lossRate:number;enabled:boolean;archived:boolean}
const base='/v1/materials/manage'
const body=(value:unknown)=>({method:'POST',body:JSON.stringify(value)})
export const materialManagementApi={
 selection:(ids:string[])=>apiRequest<MaterialRow[]>(`${base}/selection`,body({ids})),
 capabilities:()=>apiRequest<{closeoutVersion:number;simpleCostVersion?:number}>(`${base}/capabilities`),
 page:(filters:MaterialFilters,page:number,size:number)=>apiRequest<{items:MaterialRow[];totalCount:number}>(`${base}/page?${new URLSearchParams({...filters,page:String(page),size:String(size)})}`),
 detail:(id:string)=>apiRequest<MaterialDetail>(`${base}/${encodeURIComponent(id)}`),
 save:(input:MaterialSave)=>apiRequest<MaterialRecord>(`${base}/save`,body(input)),
 impact:(id:string)=>apiRequest<MaterialImpact>(`${base}/${encodeURIComponent(id)}/impact`),
 status:(item:MaterialRecord)=>apiRequest<MaterialRecord>(`${base}/${encodeURIComponent(item.id)}/status`,body({version:item.version})),
 remove:(items:MaterialRecord[])=>apiRequest<void>(`${base}/delete`,body({items:items.map(({id,version})=>({id,version}))})),
 history:(id:string,page:number,size:number)=>apiRequest<{items:ProductHistoryDto[];total:number}>(`${base}/${encodeURIComponent(id)}/history?page=${page}&size=${size}`),
 products:(id:string,page:number,size:number)=>apiRequest<{items:MaterialProduct[];totalCount:number}>(`${base}/${encodeURIComponent(id)}/products?page=${page}&size=${size}`),
 inventory:(id:string,page:number,size:number)=>apiRequest<{balance:InventoryBalanceViewDto|null;ledger:{items:LedgerEntryDto[];totalCount:number}}>(`${base}/${encodeURIComponent(id)}/inventory?page=${page}&size=${size}`),
 export:async(filters:MaterialFilters,ids:string[])=>{const token=getSessionToken();const response=await fetch(`${runtimeConfig.apiBaseUrl}${base}/export`,{...body({filters,ids}),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})}});if(!response.ok){if(response.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await response.json().catch(()=>null) as {detail?:string}|null;throw new ApiError(response.status,'MATERIAL_EXPORT_FAILED',p?.detail??'导出失败')}return response.blob()},
}
