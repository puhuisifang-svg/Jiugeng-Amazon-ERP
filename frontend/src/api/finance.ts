import { apiRequest, ApiError, getSessionToken, SESSION_EXPIRED_EVENT } from './client'
import { runtimeConfig } from '@/config/runtime'
export interface FinanceShop { id:string;name:string;remark:string;active:boolean;version:number }
export interface FinanceAccount { id:string;shopId:string;name:string;active:boolean;version:number }
export interface BillInput { shopId:string;date:string;direction:'INCOME'|'EXPENSE';category:string;detail:string;account:string;amount:string;remark:string;version?:number }
export interface FinanceBill extends Omit<BillInput,'amount'> { id:string;shopName:string;amount:number;voided:boolean;version:number;attachments:number }
export interface BillTotal { label:string;income:number;expense:number;balance:number;count:number }
export interface BillPage { items:FinanceBill[];totalCount:number;totals:BillTotal[];accounts:BillTotal[];months:BillTotal[] }
export interface BillFilter { shopId:string;from:string;to:string;direction:string;q:string;voided:string }
export interface BillAttachment { id:string;name:string;size:number;mediaType:string }
export interface BillHistory { action:string;actor:string;time:string;before:string;after:string }
const base='/v1/finance'
async function fileRequest(path:string,init:RequestInit={}) {
 const headers=new Headers(init.headers);const token=getSessionToken();if(token)headers.set('Authorization',`Bearer ${token}`)
 const r=await fetch(`${runtimeConfig.apiBaseUrl}${base}${path}`,{...init,headers})
 if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null) as {detail?:string}|null;throw new ApiError(r.status,'FINANCE_FILE_ERROR',p?.detail??'文件操作失败')}
 return r
}
function saveBlob(blob:Blob,name:string){const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();window.setTimeout(()=>URL.revokeObjectURL(url),1000)}
export const financeApi={
 accounts:(shopId:string)=>apiRequest<FinanceAccount[]>(`${base}/shops/${shopId}/accounts`),
 saveAccount:(shopId:string,input:{name:string;active:boolean;version?:number},id?:string)=>apiRequest<FinanceAccount>(`${base}/shops/${shopId}/accounts${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 shops:()=>apiRequest<FinanceShop[]>(`${base}/shops`),
 saveShop:(input:{name:string;remark:string;active:boolean;version?:number;accounts?:string[]},id?:string)=>apiRequest<FinanceShop>(`${base}/shops${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 list:(filter:BillFilter,page:number,size:number)=>apiRequest<BillPage>(`${base}/bills?${new URLSearchParams({...filter,page:String(page),size:String(size)})}`),
 save:(input:BillInput,id?:string)=>apiRequest<FinanceBill>(`${base}/bills${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(input)}),
 void:(bill:FinanceBill)=>apiRequest<FinanceBill>(`${base}/bills/${bill.id}/void?version=${bill.version}`,{method:'POST'}),
 history:async(id:string)=>{const rows=await apiRequest<Record<string,unknown>[]>(`${base}/bills/${id}/history`);return rows.map(r=>{const v=Object.fromEntries(Object.entries(r).map(([k,v])=>[k.toLowerCase(),v]));return {action:String(v.action),actor:String(v.actor_display_name),time:String(v.occurred_at),before:String(v.before_value??''),after:String(v.after_value??'')} as BillHistory})},
 attachments:(id:string)=>apiRequest<BillAttachment[]>(`${base}/bills/${id}/attachments`),
 upload:async(id:string,file:File)=>{const data=new FormData();data.set('file',file);return (await fileRequest(`/bills/${id}/attachments`,{method:'POST',body:data})).json() as Promise<BillAttachment>},
 removeAttachment:(billId:string,id:string)=>apiRequest<void>(`${base}/bills/${billId}/attachments/${id}`,{method:'DELETE'}),
 attachmentBlob:async(billId:string,id:string)=>(await fileRequest(`/bills/${billId}/attachments/${id}`)).blob(),
 download:async(billId:string,a:BillAttachment)=>saveBlob(await financeApi.attachmentBlob(billId,a.id),a.name),
 export:async(filter:BillFilter)=>saveBlob(await(await fileRequest(`/export?${new URLSearchParams({...filter})}`)).blob(),`收支账单_${filter.from}_${filter.to}.csv`),
}
