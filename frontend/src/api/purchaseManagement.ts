import {apiRequest,ApiError,getSessionToken,SESSION_EXPIRED_EVENT} from './client'
import {runtimeConfig} from '@/config/runtime'
import type {PurchaseOrder,PurchaseOrderInput,PurchaseReferenceItem,PurchaseReceipt} from './purchases'
import type {ProductHistoryDto} from './domain'
export interface PurchaseFilters {type:string;status:string;q:string;supplier:string;createdFrom:string;createdTo:string;deliveryFrom:string;deliveryTo:string;delivery:string;logistics:string}
export const blankPurchaseFilters=():PurchaseFilters=>({type:'PRODUCT',status:'',q:'',supplier:'',createdFrom:'',createdTo:'',deliveryFrom:'',deliveryTo:'',delivery:'',logistics:''})
export interface PurchaseSave {requestId:string;id:string|null;order:PurchaseOrderInput;copyFromId?:string}
export interface ShipmentLine {orderLineId:string;quantity:number;receivedQuantity:number}
export interface PurchaseShipment {id:string;carrier:string;trackingNumber:string;imageId?:string;shippedOn:string;packageCount:number;remark:string;version:number;lines:ShipmentLine[]}
export interface ShipmentInput {requestId:string;version:number;carrier:string;trackingNumber:string;imageId:string|null;shippedOn:string;packageCount:number;remark:string;lines:{orderLineId:string;quantity:number}[]}
export interface Page<T>{items:T[];total:number;page:number;size:number}
const base='/v1/purchases/manage',post=(body:unknown)=>({method:'POST',body:JSON.stringify(body)})
export const purchaseManagementApi={
 capabilities:()=>apiRequest<{closeoutVersion:number}>(`${base}/capabilities`),
 page:(f:PurchaseFilters,page=0,size=20)=>apiRequest<Page<PurchaseOrder>>(`${base}/orders?${new URLSearchParams({...f,page:String(page),size:String(size)})}`),
 counts:()=>apiRequest<Record<string,number>>(`${base}/counts`),
 save:(input:PurchaseSave)=>apiRequest<PurchaseOrder>(`${base}/save`,post(input)),
 references:(type:string,supplier:string,q='',page=0,size=20)=>apiRequest<Page<PurchaseReferenceItem>>(`${base}/references?${new URLSearchParams({type,supplier,q,page:String(page),size:String(size)})}`),
 price:(item:string,supplier:string,currency:string)=>apiRequest<{unitPrice?:number;taxIncluded?:boolean;taxRate?:number;unit?:string;orderNo?:string;createdAt?:string}>(`${base}/price?${new URLSearchParams({item,supplier,currency})}`),
 checks:(id:string)=>apiRequest<string[]>(`${base}/${id}/checks`),
 history:(id:string,page=0,size=20)=>apiRequest<Page<ProductHistoryDto>>(`${base}/${id}/history?page=${page}&size=${size}`),
 receipts:(id:string,page=0,size=20)=>apiRequest<Page<{receipt:PurchaseReceipt;shipmentId:string|null}>>(`${base}/${id}/receipts?page=${page}&size=${size}`),
 shipments:(id:string)=>apiRequest<PurchaseShipment[]>(`${base}/${id}/shipments`),
 shipment:(id:string,input:ShipmentInput)=>apiRequest<PurchaseShipment[]>(`${base}/${id}/shipments`,post(input)),
 selection:(filters:PurchaseFilters,ids:string[])=>apiRequest<PurchaseOrder[]>(`${base}/selection`,post({filters,ids})),
 export:async(filters:PurchaseFilters,ids:string[])=>{const token=getSessionToken(),r=await fetch(`${runtimeConfig.apiBaseUrl}${base}/export`,{...post({filters,ids}),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})}});if(!r.ok){if(r.status===401)window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));const p=await r.json().catch(()=>null);throw new ApiError(r.status,p?.code??'EXPORT_FAILED',p?.message??'导出失败')}return r.blob()},
}
