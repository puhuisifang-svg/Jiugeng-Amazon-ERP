import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
import type { ProductHistoryDto } from './domain'
export interface SampleLocation { id:string;productId:string|null;variantId:string|null;productName:string;skuCode:string;skuName:string;locationCode:string;version:number;updatedAt:string;specification?:string;remark?:string;storageImageId?:string|null;sampleImageId?:string|null;deletedAt?:string|null;snapshotImageUrl?:string|null;imageSnapshotTaken?:boolean;currentProductName?:string|null;currentSkuName?:string|null;linkedStatus?:string;displayImageId?:string|null;displayImageUrl?:string|null;imageSource?:string }
export interface SampleInput { productId:string|null;variantId:string|null;locationCode:string;version?:number;sampleName?:string;sampleCode?:string;specification?:string;remark?:string;storageImageId?:string|null;sampleImageId?:string|null;copyFromId?:string }
export interface SampleFilters { source:string;photo:string;location:string;locationMode:string;deleted:boolean }
export const emptySampleFilters=():SampleFilters=>({source:'',photo:'',location:'',locationMode:'prefix',deleted:false})
function params(q:string,productId:string,filters:Partial<SampleFilters>={}) { return new URLSearchParams({q,productId,...Object.fromEntries(Object.entries(filters).map(([k,v])=>[k,String(v)]))}) }
export const samplesApi={
 capabilities:()=>apiRequest<{closeoutVersion:number}>('/v1/samples/capabilities'),
 parents:()=>apiRequest<{id:string;name:string}[]>('/v1/samples/parents'),
 list:(q:string,page:number,size:number,productId='',filters:Partial<SampleFilters>={})=>{const p=params(q,productId,filters);p.set('page',String(page));p.set('size',String(size));return apiRequest<{items:SampleLocation[];totalCount:number}>(`/v1/samples?${p}`)},
 detail:(id:string)=>apiRequest<SampleLocation>(`/v1/samples/${encodeURIComponent(id)}`),
 history:(id:string,page:number,size:number)=>apiRequest<{items:ProductHistoryDto[];total:number}>(`/v1/samples/${encodeURIComponent(id)}/history?page=${page}&size=${size}`),
 locations:(q='')=>apiRequest<string[]>(`/v1/samples/locations?${new URLSearchParams({q})}`),
 duplicates:(variantId:string,locationCode:string)=>apiRequest<SampleLocation[]>(`/v1/samples/duplicates?${new URLSearchParams({variantId,locationCode})}`),
 selection:(ids:string[])=>apiRequest<SampleLocation[]>('/v1/samples/selection',{method:'POST',body:JSON.stringify({ids})}),
 create:(input:SampleInput,key?:string)=>apiRequest<SampleLocation>('/v1/samples',{method:'POST',headers:key?{'Idempotency-Key':key}:{},body:JSON.stringify(input)}),
 update:(id:string,input:SampleInput,key?:string)=>apiRequest<SampleLocation>(`/v1/samples/${encodeURIComponent(id)}`,{method:'PUT',headers:key?{'Idempotency-Key':key}:{},body:JSON.stringify(input)}),
 remove:(id:string,version:number)=>apiRequest<void>(`/v1/samples/${encodeURIComponent(id)}?version=${version}`,{method:'DELETE'}),
 restore:(id:string,version:number)=>apiRequest<SampleLocation>(`/v1/samples/${encodeURIComponent(id)}/restore`,{method:'POST',body:JSON.stringify({version})}),
 export:async(q:string,productId:string,filters:Partial<SampleFilters>,ids:string[])=>{
  const token=getSessionToken();const response=await fetch(`${runtimeConfig.apiBaseUrl}/v1/samples/export`,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify({q,productId,...filters,ids})})
  if(!response.ok){if(response.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const problem=await response.json().catch(()=>null) as {detail?:string}|null;throw new ApiError(response.status,'SAMPLE_EXPORT_FAILED',problem?.detail??'导出失败')}
  return response.blob()
 },
}
