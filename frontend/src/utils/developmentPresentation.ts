import { beijingDate } from '@/utils/dateTime'
import type {Document,Workspace} from '@/api/launch'
export const developmentStates=['DRAFT','PROJECT_REVIEW','ACTIVE','FINAL_REVIEW','PAUSED','COMPLETED','VOID']
export const developmentTabs=[{key:'overview',title:'项目概览',sections:['overview']},{key:'scheme',title:'方案与需求',sections:['specs','needs','requirements','market']},{key:'supply',title:'供应与打样',sections:['quotes','samples']},{key:'cost',title:'成本与预算',sections:['cost']},{key:'risk',title:'定型风险',sections:['risk']},{key:'handover',title:'建档与交接',sections:['handover','skus','evidence']},{key:'changes',title:'方案变更',sections:[]},{key:'history',title:'操作记录',sections:[]}]
export const stageGuides=[{tab:'scheme',text:'确认目标规格，为每条开发要求指定责任人和验收标准。'},{tab:'supply',text:'比较报价并选定供应方案，登记样品轮次、到样及验证记录。'},{tab:'supply',text:'选定通过验收的最终样品，逐项记录开发要求的测试结果与证据。'},{tab:'risk',text:'按当前方案版本完成风险调查，由审核人员复核。'},{tab:'handover',text:'核对最终成本，关联启用商品与目标 SKU，完成资料交接后提交验收。'}]
export function developmentActionLabel(action:string,w?:Workspace){if(action==='START')return '接手并开始开发';if(action==='SUBMIT')return w?.state==='ACTIVE'?'提交完成审核':'提交立项审核';if(action==='APPROVE')return '通过立项';if(action==='NEXT')return '完成本阶段';if(action==='BACK')return '退回上一阶段';if(action==='COMPLETE')return '通过验收并完成';return ''}
export function overdue(d:Document,state:string,today=new Date()){const end=String(d.endDate??'');const local=beijingDate(today);return /^\d{4}-\d{2}-\d{2}$/.test(end)&&end<local&&!['COMPLETED','VOID'].includes(state)}
export function changedKeys(before:Document,after:Document){return Array.from(new Set([...Object.keys(before),...Object.keys(after)])).filter(k=>JSON.stringify(before[k]??null)!==JSON.stringify(after[k]??null))}

export function developmentIssueTab(issue:string){
 if(/变更/.test(issue))return 'changes'
 if(/SKU|关联商品|交接/.test(issue))return 'handover'
 if(/风险|调查|授权|商标|版权|专利|类目/.test(issue))return 'risk'
 if(/报价|供应|样品|打样/.test(issue))return 'supply'
 if(/费用|成本|汇率|费率|售价|佣金|广告|损耗|commission|advertising|loss/.test(issue))return 'cost'
 if(/需求|规格|要求|改进|验证方式|销售数据/.test(issue))return 'scheme'
 return 'overview'
}
