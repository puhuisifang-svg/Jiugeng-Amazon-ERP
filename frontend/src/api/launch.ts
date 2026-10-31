import { apiRequest, getSessionToken } from './client'
import { runtimeConfig } from '@/config/runtime'
export type Value = string | number | null
export type Row = Record<string, Value>
export interface Document { [key: string]: Value | Row[] }
export interface Revision { version: number; action: string; actor: string; reason: string; date: string; snapshot: {document: Document; state: string; stage: number} }
export interface Workspace { kind: string; id: string; state: string; stage: number; version: number; document: Document; baseline: Document; proposal: Document; riskReview: {valid?: boolean; actor?: string; date?: string; reason?: string}; issues: string[]; calculation: Record<string, number | null> | null; history: Revision[] }
export interface LaunchItem { id: string; title: string; asin?: string; number?: string; source: string; owner: string; state: string; legacy_state: string; stage?: number; document: Document }
export const launchApi = (development: boolean) => {
  const base = development ? '/v1/development/workbench' : '/v1/selection/workbench'
  return {
    list: (q: string, state: string, page: number, size: number) => apiRequest<{items: LaunchItem[]; total: number}>(base + '?' + new URLSearchParams({q,state,page:String(page-1),size:String(size)})),
    get: (id: string) => apiRequest<{workspace: Workspace | Record<string, never>; legacy: Record<string, unknown>}>(`${base}/${id}`),
    initialize: (id: string) => apiRequest<Workspace>(`${base}/${id}/initialize`,{method:'POST'}),
    create: (input: Record<string,string>) => apiRequest<{id:string}>(base,{method:'POST',body:JSON.stringify(input)}),
    save: (id: string, version: number, document: Document) => apiRequest<Workspace>(`${base}/${id}`,{method:'PUT',body:JSON.stringify({version,document})}),
    action: (id: string, version: number, action: string, reason: string, selfConfirmed: boolean) => apiRequest<Workspace>(`${base}/${id}/action`,{method:'POST',body:JSON.stringify({version,action,reason,selfConfirmed})}),
  }
}
export const stateNames: Record<string,string> = {NEW:'待评估',EVALUATING:'评估中',REVIEWING:'评估中',RETURNED:'待补充',REVIEW:'待审核',APPROVED:'已通过',REJECTED:'已淘汰',DRAFT:'待接手',PROJECT_REVIEW:'待立项审核',ACTIVE:'开发中',IN_PROGRESS:'开发中',FINAL_REVIEW:'待完成审核',PAUSED:'已暂停',COMPLETED:'已完成',VOID:'已终止'}
export const stages = ['方案确认','供应与打样','样品验证','定型风险复核','定型与建档']
export const actionNames: Record<string,string> = {SAVE:'保存资料',START:'开始',SUBMIT:'提交审核',APPROVE:'通过并生成开发草稿',RETURN:'退回补充',REJECT:'淘汰',REOPEN:'重新评估',WITHDRAW:'撤回审核',REVIEW_RISK:'复核风险调查',NEXT:'推进下一阶段',BACK:'退回上一阶段',PAUSE:'暂停',RESUME:'恢复',TERMINATE:'终止项目',CHANGE_SUBMIT:'提交方案变更',CHANGE_APPROVE:'批准变更',CHANGE_REJECT:'驳回变更',COMPLETE:'通过完成审核'}
export const rows = (d: Document, key: string): Row[] => Array.isArray(d[key]) ? d[key] as Row[] : []
export function defaults(input: Document): Document {
  const d: Document = {market:'US',specVersion:'1',platform:'Amazon',...JSON.parse(JSON.stringify(input))}
  for(const key of ['specs','needs','requirements','risks','quotes','samples','evidence','criteria','costs','skus','handover']) if(!Array.isArray(d[key])) d[key]=[]
  if(!rows(d,'criteria').length) d.criteria=['需求证据 · 25%','差异化 · 25%','利润空间 · 20%','供应可行性 · 20%','风险可控性 · 10%'].map((name,i)=>({id:`criterion-${i}`,name,score:null,reason:''}))
  // Historical criteria had no IDs. Preserve values while supplying stable row identities.
  d.criteria=rows(d,'criteria').map((r,i)=>({id:`criterion-${i}`,...r}))
  if(!rows(d,'costs').length)d.costs=['采购','包装','头程','履约','其他'].map((category,i)=>({id:`cost-${i}`,category,currency:'USD',rate:1,amount:null,basis:''}))
  if(!rows(d,'risks').length)d.risks=['TRADEMARK','COPYRIGHT','DESIGN','PATENT','COMPLIANCE'].map(type=>({id:`risk-${type}`,type,market:'US',conclusion:'PENDING'}))
  return d
}

export interface LaunchAttachment { id:string; name:string; size:number }
export async function attachmentList(kind:string,id:string){return apiRequest<LaunchAttachment[]>(`/v1/launch/${kind}/${id}/attachments`)}

export async function uploadAttachment(kind:string,id:string,file:File){
 const body=new FormData();body.set('file',file)
 const response=await fetch(`${runtimeConfig.apiBaseUrl}/v1/launch/${kind}/${id}/attachments`,{method:'POST',headers:{Authorization:`Bearer ${getSessionToken()}`},body})
 if(!response.ok){const e=await response.json().catch(()=>({}));throw new Error(e.message??'附件上传失败')}
 return response.json() as Promise<{id:string;name:string}>
}
export async function downloadAttachment(kind:string,id:string,file:LaunchAttachment){
 const response=await fetch(`${runtimeConfig.apiBaseUrl}/v1/launch/${kind}/${id}/attachments/${file.id}`,{headers:{Authorization:`Bearer ${getSessionToken()}`}})
 if(!response.ok)throw new Error('附件下载失败')
 const url=URL.createObjectURL(await response.blob()),a=document.createElement('a');a.href=url;a.download=file.name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
