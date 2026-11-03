import { apiRequest } from './client'
import type { LaunchItem } from './launch'
export interface CandidateItem extends LaunchItem { version:number;hasDevelopment?:boolean; observation?:Record<string,unknown>; updatedAt?:string }
export interface CandidateFilters { q:string;state:string;source:string;owner:string;page:number;size:number }
export const candidatesApi={
 remove:(id:string,version:number)=>apiRequest(`/v1/selection/workbench/${id}?version=${version}`,{method:'DELETE'}),
 list:(f:CandidateFilters)=>apiRequest<{items:CandidateItem[];total:number;counts:Record<string,number>;owners:string[]}>('/v1/selection/workbench?'+new URLSearchParams({...f,page:String(f.page-1),size:String(f.size)})),
 observation:(id:string)=>apiRequest<{observation:Record<string,unknown>;job:Record<string,unknown>[]}>(`/v1/selection/workbench/${id}/observation`),
 collect:(id:string)=>apiRequest(`/v1/selection/workbench/${id}/collect`,{method:'POST'}),
}
