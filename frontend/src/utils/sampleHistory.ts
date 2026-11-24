import type { ProductHistoryDto } from '@/api/domain'
export const sampleActions:Record<string,string>={SAMPLE_UPDATE:'库位变更'}
const fields:Record<string,string>={locationCode:'库位'}
export function sampleHistoryChanges(event:ProductHistoryDto){
 const before=event.beforeValue??{},after=event.afterValue??{}
 return Object.keys(fields).filter(k=>JSON.stringify(before[k])!==JSON.stringify(after[k])).map(key=>({key,label:fields[key],before:display(before[key]),after:display(after[key])}))
}
function display(value:unknown){return value==null||value===''?'未填写':String(value)}
export function sampleLinkedStatus(value?:string){return ({ACTIVE:'启用',DISABLED:'停用 / 未启用',DELETED:'已删除',UNAVAILABLE:'关联资料不可用',MANUAL:'手动录入'} as Record<string,string>)[value??'']??'关联状态待确认'}
