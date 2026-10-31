import { apiRequest } from './client'
export interface SampleLocation { id:string;productId:string;variantId:string;productName:string;skuCode:string;skuName:string;locationCode:string;version:number;updatedAt:string }
export interface SampleInput { productId:string;variantId:string;locationCode:string;version?:number }
export const samplesApi={
 list:(q:string,page:number,size:number)=>apiRequest<{items:SampleLocation[];totalCount:number}>(`/v1/samples?${new URLSearchParams({q,page:String(page),size:String(size)})}`),
 create:(input:SampleInput)=>apiRequest<SampleLocation>('/v1/samples',{method:'POST',body:JSON.stringify(input)}),
 update:(id:string,input:SampleInput)=>apiRequest<SampleLocation>(`/v1/samples/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(input)}),
 remove:(id:string,version:number)=>apiRequest<void>(`/v1/samples/${encodeURIComponent(id)}?version=${version}`,{method:'DELETE'}),
}
