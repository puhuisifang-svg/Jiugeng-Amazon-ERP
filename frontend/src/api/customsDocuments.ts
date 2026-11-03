import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
export interface CustomsDocument {id:string;documentNo:string;status:'DRAFT'|'READY'|'VOID';fields:Record<string,string>;items:Record<string,string>[];version:number;updatedAt:string}
export interface CustomsSummary {id:string;documentNo:string;status:CustomsDocument['status'];seller:string;buyer:string;destination:string;itemCount:number;version:number;updatedAt:string}
export type CustomsInput=Pick<CustomsDocument,'documentNo'|'fields'|'items'> & {status:'DRAFT'|'READY';version?:number|undefined}
const base='/v1/customs-documents'
export const customsDocumentsApi={
 list:(q:string,status:string,page:number,size:number)=>apiRequest<{items:CustomsSummary[];totalCount:number}>(`${base}?${new URLSearchParams({q,status,page:String(page),size:String(size)})}`),
 get:(id:string)=>apiRequest<CustomsDocument>(`${base}/${id}`),
 save:(input:CustomsInput,id?:string)=>apiRequest<CustomsDocument>(`${base}${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 void:(id:string,version:number)=>apiRequest<CustomsDocument>(`${base}/${id}/void?version=${version}`,{method:'POST'}),
 export:async(id:string,name:string)=>{const token=getSessionToken();const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/${id}/export`,{headers:token?{Authorization:`Bearer ${token}`}:{}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null) as {detail?:string}|null;throw new ApiError(r.status,'CUSTOMS_EXPORT_FAILED',p?.detail??'导出失败，请重试')}const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download=`报关资料_${name.replace(/[\\/:*?"<>|]/g,'_')}.xlsx`;document.body.append(a);a.click();a.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000)},
}
