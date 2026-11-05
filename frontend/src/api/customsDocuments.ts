import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
export interface CustomsDocument {id:string;documentNo:string;status:'DRAFT'|'READY'|'VOID';fileStatus?:'PENDING_FILES'|'COMPLETED';fields:Record<string,string>;items:Record<string,string>[];version:number;updatedAt:string}
export interface CustomsSummary {id:string;documentNo:string;status:CustomsDocument['status'];seller:string;buyer:string;destination:string;fbaNo:string;warehouse:string;cartonCount:string|null;freight:string;fileStatus:'PENDING_FILES'|'COMPLETED';itemCount:number;version:number;updatedAt:string}
export type CustomsInput=Pick<CustomsDocument,'documentNo'|'fields'|'items'> & {status:'DRAFT'|'READY';version?:number|undefined}
const base='/v1/customs-documents'
export const customsDocumentsApi={
 list:(q:string,status:string,page:number,size:number,fba='')=>apiRequest<{items:CustomsSummary[];totalCount:number}>(`${base}?${new URLSearchParams({q,status,fba,page:String(page),size:String(size)})}`),
 get:(id:string)=>apiRequest<CustomsDocument>(`${base}/${id}`),
 save:(input:CustomsInput,id?:string)=>apiRequest<CustomsDocument>(`${base}${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 remove:(id:string,version:number)=>apiRequest<void>(`${base}/${id}?version=${version}`,{method:'DELETE'}),
 void:(id:string,version:number)=>apiRequest<CustomsDocument>(`${base}/${id}/void?version=${version}`,{method:'POST'}),
 export:async(id:string,name:string)=>{const token=getSessionToken();const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/${id}/export`,{headers:token?{Authorization:`Bearer ${token}`}:{}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null) as {detail?:string}|null;throw new ApiError(r.status,'CUSTOMS_EXPORT_FAILED',p?.detail??'导出失败，请重试')}const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download=exportFilename(r.headers.get('Content-Disposition'),name);document.body.append(a);a.click();a.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000)},
}

export interface CustomsShipper {id:string;name:string;prefix:string;address:string;phone:string;fax:string}
export const customsShippersApi={
 update:(id:string,shipper:Omit<CustomsShipper,'id'>)=>apiRequest<CustomsShipper>(`/v1/customs-shippers/${id}`,{method:'PUT',body:JSON.stringify(shipper)}),
 list:()=>apiRequest<CustomsShipper[]>('/v1/customs-shippers'),
 add:(shipper:Omit<CustomsShipper,'id'>)=>apiRequest<CustomsShipper>('/v1/customs-shippers',{method:'POST',body:JSON.stringify(shipper)}),
}

export interface CustomsWarehouse {code:string;country:string;region:string;city:string;postalCode:string;address:string;source:string;verifiedOn:string;version?:number|undefined}
export interface WarehouseSyncStatus {state:string;startedAt:string;finishedAt:string;nextRunAt:string;message:string;sourceDate:string;added:number;updated:number;preserved:number;rejected:number;automatic:boolean}
export const customsWarehousesApi={
 status:()=>apiRequest<WarehouseSyncStatus>('/v1/customs-warehouses/sync'),
 sync:()=>apiRequest<WarehouseSyncStatus>('/v1/customs-warehouses/sync',{method:'POST'}),
 list:()=>apiRequest<CustomsWarehouse[]>('/v1/customs-warehouses'),
 save:(row:CustomsWarehouse,editing=false)=>apiRequest<CustomsWarehouse>(`/v1/customs-warehouses${editing?`/${encodeURIComponent(row.code)}`:''}`,{method:editing?'PUT':'POST',body:JSON.stringify(row)}),
 import:(rows:CustomsWarehouse[])=>apiRequest<CustomsWarehouse[]>('/v1/customs-warehouses/import',{method:'POST',body:JSON.stringify(rows)}),
}

export function exportFilename(header:string|null,fallback:string){const encoded=header?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];if(encoded){try{return decodeURIComponent(encoded).replace(/[\\/:*?"<>|]/g,'_')}catch{/* fallback for malformed headers */}}return `报关单资料-${fallback.replace(/[\\/:*?"<>|]/g,'_')}.xlsx`}
export interface CustomsAttachment {id:string;category:'SCREENSHOT'|'INVOICE'|'OTHER';name:string;size:number;mediaType:string;createdAt:string}
export async function customsFileRequest(path:string,init:RequestInit={}){const token=getSessionToken();const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/${path}`,{...init,headers:token?{Authorization:`Bearer ${token}`}:{}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null) as {detail?:string}|null;throw new Error(p?.detail||'文件操作失败，请重试')}return r}
export const customsFilesApi={
 list:(id:string)=>apiRequest<CustomsAttachment[]>(`${base}/${id}/attachments`),
 upload:async(id:string,category:string,file:File)=>{const body=new FormData();body.append('category',category);body.append('file',file);await customsFileRequest(`${id}/attachments`,{method:'POST',body})},
 remove:(id:string,fileId:string)=>apiRequest<void>(`${base}/${id}/attachments/${fileId}`,{method:'DELETE'}),
 download:async(id:string,file:CustomsAttachment)=>{const r=await customsFileRequest(`${id}/attachments/${file.id}`);const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download=file.name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)},
}
