import { apiRequest,ApiError,getSessionToken,SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
import type { SupplierDto,SupplierInput,SupplierCategory,SupplierStatus,SupplierDependency,ProductHistoryDto } from './domain'
export interface SupplierFilters {q:string;status:''|SupplierStatus;category:''|SupplierCategory}
export interface SupplierImpact {counts:Partial<Record<'PRODUCT'|'MATERIAL'|'PURCHASE',number>>;total:number;restrictedCount:number;drafts:number|null;pendingReceipts?:number|null}
export interface SupplierSave {requestId:string;id:string|null;supplier:SupplierInput;copyFromId?:string;impactConfirmed:boolean}
export type SupplierRelationType='PRODUCT'|'MATERIAL'|'PURCHASE'
const base='/v1/suppliers/manage',post=(v:unknown)=>({method:'POST',body:JSON.stringify(v)})
export const supplierManagementApi={
 capabilities:()=>apiRequest<{closeoutVersion:number}>(`${base}/capabilities`),
 save:(input:SupplierSave)=>apiRequest<SupplierDto>(`${base}/save`,post(input)),
 impact:(id:string)=>apiRequest<SupplierImpact>(`${base}/${encodeURIComponent(id)}/impact`),
 status:(s:SupplierDto,status:SupplierStatus,impactConfirmed:boolean)=>apiRequest<SupplierDto>(`${base}/${encodeURIComponent(s.id)}/status`,post({version:s.version,status,impactConfirmed})),
 relations:(id:string,type:SupplierRelationType,page:number,size:number)=>apiRequest<{items:SupplierDependency[];totalCount:number}>(`${base}/${encodeURIComponent(id)}/relations?${new URLSearchParams({type,page:String(page),size:String(size)})}`),
 history:(id:string,page:number,size:number)=>apiRequest<{items:ProductHistoryDto[];total:number}>(`${base}/${encodeURIComponent(id)}/history?page=${page}&size=${size}`),
 duplicates:(name:string,phone:string,wechat:string,exclude='')=>apiRequest<SupplierDto[]>(`${base}/duplicates?${new URLSearchParams({name,phone,wechat,exclude})}`),
 export:async(filters:SupplierFilters,ids:string[])=>{const token=getSessionToken();const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/export`,{...post({q:filters.q,status:filters.status||null,category:filters.category||null,ids}),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null);throw new ApiError(r.status,p?.code??'SUPPLIER_EXPORT_FAILED',p?.message??'导出失败')}return r.blob()},
}
