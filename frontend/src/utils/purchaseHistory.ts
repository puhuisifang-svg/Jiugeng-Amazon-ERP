import type { ProductHistoryDto } from '@/api/domain'
import type { PurchaseLine } from '@/api/purchases'

const labels:Record<string,string>={orderType:'采购类型',supplierName:'供应商',status:'状态',currency:'币种',expectedDeliveryDate:'预计到货',deliveryAddress:'收货信息',remark:'备注',cancellationReason:'关闭原因',carrier:'物流公司',trackingNumber:'运单号',waybillImageId:'运单图片',packageCount:'包裹数',shippedOn:'发货日期',lineCount:'收货项数',completed:'是否收齐'}
const enums:Record<string,string>={PRODUCT:'产品采购',MATERIAL:'辅料采购',MANUAL:'日常物品采购',DRAFT:'草稿',SUBMITTED:'待收货',PARTIALLY_RECEIVED:'部分收货',COMPLETED:'已完成',CANCELLED:'已取消 / 余量关闭',CNY:'人民币',USD:'美元'}
const lineLabels:Record<string,string>={remark:'商品备注',quantity:'数量',received:'已收数量',unitPrice:'不含税单价',taxIncluded:'含税',taxRate:'税率',packageCount:'包裹数'}
type Change={key:string;label:string;before:string;after:string;initial:boolean}
const empty=(value:unknown)=>value==null||value===''
function value(v:unknown):string { if(empty(v))return '未填写';if(typeof v==='boolean')return v?'是':'否';return typeof v==='string'||typeof v==='number'?enums[String(v)]??String(v):'—' }
function same(a:unknown,b:unknown){return (empty(a)&&empty(b))||a===b||(!empty(a)&&!empty(b)&&typeof a!=='boolean'&&typeof b!=='boolean'&&Number.isFinite(Number(a))&&Number(a)===Number(b))}
function lines(v:unknown):Record<string,unknown>[] {return Array.isArray(v)?v.filter((x):x is Record<string,unknown>=>!!x&&typeof x==='object'&&!Array.isArray(x)):[]}
export function purchaseHistoryChanges(event:ProductHistoryDto,currentLines:PurchaseLine[]=[]):Change[]{
 const before=event.beforeValue??{},after=event.afterValue??{},initial=!event.beforeValue||Object.keys(event.beforeValue).length===0,changes:Change[]=[]
 for(const [key,label] of Object.entries(labels)){
  if(same(before[key],after[key])||(initial&&empty(after[key])))continue
  changes.push({key,label,initial,before:initial?'':key==='waybillImageId'?(before[key]?'已上传':'未上传'):value(before[key]),after:key==='waybillImageId'?(after[key]?(before[key]?'已更换图片':'已上传'):'已移除'):value(after[key])})
 }
 const oldLines=lines(before.lines),newLines=lines(after.lines)
 // Order snapshots carry names; shipment snapshots carry line IDs. Never render raw IDs or JSON.
 const name=(line:Record<string,unknown>,index:number)=>String(line.itemName||currentLines.find(l=>l.id===line.orderLineId)?.itemName||`历史商品 ${index+1}`)
 const summary=(line:Record<string,unknown>,index:number)=>[name(line,index),...Object.entries(lineLabels).filter(([k])=>!empty(line[k])&&(k!=='taxRate'||line.taxIncluded===true)).map(([k,label])=>`${label}：${value(line[k])}${k==='taxRate'?'%':''}`)].join('\n')
 // Snapshot arrays have no stable item IDs; compare named rows, retaining duplicates by occurrence.
 const used=new Set<number>()
 newLines.forEach((line,i)=>{
  const match=oldLines.findIndex((old,j)=>!used.has(j)&&name(old,j)===name(line,i))
  if(match<0){changes.push({key:`line:${i}`,label:initial?'采购商品':'新增商品',initial:true,before:'',after:summary(line,i)});return}
  used.add(match);const old=oldLines[match]!
  for(const [key,label] of Object.entries(lineLabels))if(!same(old[key],line[key])&&(key!=='taxRate'||old.taxIncluded===true||line.taxIncluded===true))changes.push({key:`line:${i}:${key}`,label:`${name(line,i)} · ${label}`,initial:false,before:value(old[key])+(key==='taxRate'?'%':''),after:value(line[key])+(key==='taxRate'?'%':'')})
 })
 oldLines.forEach((line,i)=>{if(!used.has(i))changes.push({key:`removed:${i}`,label:'移除商品',initial:false,before:summary(line,i),after:'已移除'})})
 return changes
}
