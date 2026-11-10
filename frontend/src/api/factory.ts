import { apiRequest, getSessionToken } from './client'
import { runtimeConfig } from '@/config/runtime'
export type FactoryKind = 'employees'|'rates'|'reports'|'payroll'
export type FactoryResourceKind = FactoryKind|'expenses'
export type Amount = string|number
export interface FactoryLine { rateId:string; project:string; operation:string; unit:string; quantity:Amount; price:Amount|null;priced?:boolean; amount:Amount; remark:string; decimalQuantity?:boolean; groupKey?:string }
export interface FactoryAdjustment { direction:'ADD'|'SUBTRACT';amount:Amount;remark:string }
export interface FactoryPayment { id:string;date:string;amount:Amount;method:string;advance:boolean;actor:string;remark:string;voided:boolean;voidReason?:string }
export interface FactoryRecord {
 id:string; version:number; status:string; date:string; name?:string; number?:string;team?:string;phone?:string;bankName?:string;bankAccount?:string;identityNumber?:string;insured?:boolean;category?:string;payee?:string;method?:string;imageAssetId?:string|null;imageUrl?:string|null;operations?:string;endDate?:string;remark?:string;
 project?:string;operation?:string;unit?:string;price?:Amount;employeeId?:string;employeeName?:string;productId?:string;variantId?:string;skuCode?:string;decimalQuantity?:boolean;
 lines?:FactoryLine[];amount?:Amount;reports?:FactoryRecord[];adjustments?:FactoryAdjustment[];payments?:FactoryPayment[];base?:Amount;plus?:Amount;minus?:Amount;paid?:Amount;remaining?:Amount;
 pending?:number;days?:number;revision?:number;stale?:boolean;historical?:boolean;historicalAmount?:Amount;updatedAt?:string;updatedBy?:string;duplicateConfirmed?:boolean;pricingIncomplete?:boolean
}
export interface FactoryPage { items:FactoryRecord[];totalCount:number;amount:Amount }
export interface FactoryEvent { id:string;action:string;actor:string;time:string;reason:string;before:FactoryRecord;after:FactoryRecord }
export const factoryApi={
 list:(kind:FactoryResourceKind,query:Record<string,string|number>={})=>apiRequest<FactoryPage>(`/v1/factory/${kind}?${new URLSearchParams(Object.entries(query).map(([k,v])=>[k,String(v)]))}`),
 get:(kind:FactoryResourceKind,id:string)=>apiRequest<FactoryRecord>(`/v1/factory/${kind}/${encodeURIComponent(id)}`),
 history:(kind:FactoryResourceKind,id:string,page=0,size=10)=>apiRequest<{items:FactoryEvent[];totalCount:number}>(`/v1/factory/${kind}/${encodeURIComponent(id)}/history?page=${page}&size=${size}`),
 command:(kind:FactoryResourceKind,action:string,data:Record<string,unknown>)=>apiRequest<FactoryRecord & {created?:number}>(`/v1/factory/${kind}/actions/${action}`,{method:'POST',body:JSON.stringify(data)}),
 preview:(employeeId:string,date:string,rateId:string)=>apiRequest<FactoryRecord>('/v1/factory/rate-preview',{method:'POST',body:JSON.stringify({employeeId,date,rateId})}),
 operations:()=>apiRequest<{id:string;name:string;active:boolean}[]>('/v1/factory/operations'),
 async export(month:string,employeeId='',mode='summary') {const response=await fetch(`${runtimeConfig.apiBaseUrl}/v1/factory/payroll-export?${new URLSearchParams({month,employeeId,mode})}`,{headers:{Authorization:`Bearer ${getSessionToken()}`}});if(!response.ok)throw new Error('工资导出失败，请检查权限或重新登录');const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download=`${mode==='lines'?'计件明细':'工资汇总'}-${month}.csv`;a.click();URL.revokeObjectURL(url)},
}
