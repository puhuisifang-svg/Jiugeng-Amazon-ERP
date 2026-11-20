import type { ProductHistoryDto } from '@/api/domain'
export const sampleActions:Record<string,string>={SAMPLE_CREATE:'新增登记',SAMPLE_COPY:'复制登记',SAMPLE_UPDATE:'修改登记',SAMPLE_REMOVE:'移除登记',SAMPLE_RESTORE:'恢复登记'}
const fields:Record<string,string>={sampleName:'样品名称',locationCode:'库位',specification:'规格',remark:'备注',storageImageId:'存放照片',sampleImageId:'样品图片',removed:'已移除'}
export function sampleHistoryChanges(event:ProductHistoryDto){
 const before=event.beforeValue??{},after=event.afterValue??{}
 return Object.keys(fields).filter(k=>JSON.stringify(before[k])!==JSON.stringify(after[k])).map(key=>({key,label:fields[key],before:display(key,before[key]),after:display(key,after[key])}))
}
function display(key:string,value:unknown){if(value==null||value==='')return '未填写';if(key.endsWith('ImageId'))return '已上传';if(typeof value==='boolean')return value?'是':'否';return String(value)}
export function sampleLinkedStatus(value?:string){return ({ACTIVE:'启用',DISABLED:'停用 / 未启用',DELETED:'已删除',UNAVAILABLE:'关联资料不可用',MANUAL:'手动录入'} as Record<string,string>)[value??'']??'关联状态待确认'}
