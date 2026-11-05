import { apiRequest, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
export type Carrier='KAIQI'|'HUAMAO'
export const carrierNames={KAIQI:'凯琦',HUAMAO:'华茂万通'}
export interface ForwarderInput {documentNo:string;carrier:Carrier;status:'DRAFT'|'READY';fields:Record<string,string>;items:Record<string,string>[];version?:number|undefined}
export interface ForwarderDocument extends ForwarderInput {id:string;version:number;updatedAt:string;templateVersion:string}
export interface ForwarderSummary {id:string;documentNo:string;carrier:Carrier;status:string;waybill:string;warehouse:string;itemCount:number;cartons:number;amount:string;version:number;updatedAt:string}
export interface ForwarderOptions {channels:string[];addresses:Record<string,string>[];templateVersion:string}
const base='/v1/forwarder-documents'
export const forwardersApi={
 list:(q:string,carrier:string,page:number,size:number)=>apiRequest<{items:ForwarderSummary[];totalCount:number}>(`${base}?${new URLSearchParams({q,carrier,page:String(page),size:String(size)})}`),
 get:(id:string)=>apiRequest<ForwarderDocument>(`${base}/${id}`),
 options:(carrier:Carrier)=>apiRequest<ForwarderOptions>(`${base}/options?carrier=${carrier}`),
 save:(input:ForwarderInput,id?:string)=>apiRequest<ForwarderDocument>(`${base}${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 remove:(id:string,version:number)=>apiRequest<void>(`${base}/${id}?version=${version}`,{method:'DELETE'}),
 export:async(d:{id:string;carrier:Carrier;documentNo:string})=>{const token=getSessionToken();const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/${d.id}/export`,{headers:token?{Authorization:`Bearer ${token}`}:{}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null) as {detail?:string}|null;throw new Error(p?.detail??'导出失败')}const url=URL.createObjectURL(await r.blob()),a=document.createElement('a');a.href=url;a.download=`${carrierNames[d.carrier]}_${d.documentNo.replace(/[\\/:*?"<>|]/g,'_')}.${d.carrier==='KAIQI'?'xls':'xlsx'}`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)},
}
