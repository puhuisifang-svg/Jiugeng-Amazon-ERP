import { apiRequest } from './client'
export interface SampleLocation { id:string;productId:string|null;variantId:string|null;productName:string;skuCode:string;skuName:string;locationCode:string;version:number;updatedAt:string;specification?:string;remark?:string;storageImageId?:string|null }
export interface SampleInput { productId:string|null;variantId:string|null;locationCode:string;version?:number;sampleName?:string;sampleCode?:string;specification?:string;remark?:string;storageImageId?:string|null }
export const samplesApi={
 parents:()=>apiRequest<{id:string;name:string}[]>('/v1/samples/parents'),
 list:(q:string,page:number,size:number,productId='')=>apiRequest<{items:SampleLocation[];totalCount:number}>(`/v1/samples?${new URLSearchParams({q,page:String(page),size:String(size),productId})}`),
 create:(input:SampleInput)=>apiRequest<SampleLocation>('/v1/samples',{method:'POST',body:JSON.stringify(input)}),
 update:(id:string,input:SampleInput)=>apiRequest<SampleLocation>(`/v1/samples/${encodeURIComponent(id)}`,{method:'PUT',body:JSON.stringify(input)}),
 remove:(id:string,version:number)=>apiRequest<void>(`/v1/samples/${encodeURIComponent(id)}?version=${version}`,{method:'DELETE'}),
}
