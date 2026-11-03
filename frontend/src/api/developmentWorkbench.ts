import {apiRequest} from './client'
import type {LaunchItem} from './launch'
export interface DevelopmentItem extends LaunchItem {version:number;hasProposal:boolean;updatedAt:string}
export const developmentWorkbenchApi={
 remove:(id:string,version:number)=>apiRequest(`/v1/development/workbench/${id}?version=${version}`,{method:'DELETE'}),
 list:(f:{q:string;state:string;owner:string;stage:number;page:number;size:number})=>apiRequest<{items:DevelopmentItem[];total:number;counts:Record<string,number>;owners:string[]}>('/v1/development/workbench?'+new URLSearchParams({...f,stage:String(f.stage),page:String(f.page-1),size:String(f.size)})),
 readiness:(id:string)=>apiRequest<{action:string;issues:string[];version:number}>(`/v1/development/workbench/${id}/readiness`),
}
