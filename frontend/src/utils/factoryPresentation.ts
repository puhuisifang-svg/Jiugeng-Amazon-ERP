import { beijingDate } from '@/utils/dateTime'
import type { Amount, FactoryKind } from '@/api/factory'
export const factoryTitles:Record<FactoryKind,string>={employees:'员工管理',rates:'工价管理',reports:'每日报工',payroll:'月度工资'}
export const factoryStates:Record<string,string>={ACTIVE:'在用',LEFT:'已离职',STOPPED:'已设停止日期',DRAFT:'草稿',PENDING:'待核对',REVIEWED:'已核对',VOID:'已作废',LOCKED:'已锁定',CORRECTING:'更正中'}
export const factoryActions:Record<string,string>={EXPENSE_SAVE:'保存开销',EXPENSE_REMOVE:'删除开销',ATTACHMENT_UPLOAD:'上传凭证',EMPLOYEE_REMOVE:'删除员工',EMPLOYEE_SAVE:'维护员工',RATE_CREATE:'新增工价版本',RATE_STOP:'停止工价',REPORT_SAVE:'保存报工',REPORT_REVIEW:'核对报工',REPORT_WITHDRAW:'撤回核对',REPORT_VOID:'作废报工',PAYROLL_GENERATE:'生成工资',PAYROLL_CALCULATE:'重新计算',PAYROLL_ADJUST:'调整工资',PAYROLL_LOCK:'锁定工资',PAYROLL_CORRECT:'发起更正',PAYROLL_PAY:'登记发放',PAYROLL_VOIDPAYMENT:'作废发放'}
export function factoryMoney(value?:Amount){return value===undefined||value===null?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}
export function localDay(){return beijingDate()}
export function paymentState(amount?:Amount,paid?:Amount){const a=Number(amount??0),p=Number(paid??0);return p>a?'待处理超发':p===0?(a===0?'无需发放':'未发放'):p===a?'已发清':'部分发放'}

export function factoryProject(value:{project?:string;operation?:string}){const p=value.project??'',o=value.operation??'';return o&&o!==p?`${p} / ${o}`:p}
