<script setup lang="ts">
import '@/styles/purchaseManagement.css'
import {purchaseManagementApi as management,blankPurchaseFilters,type PurchaseSave} from '@/api/purchaseManagement'
import {readPurchasePending,writePurchasePending} from '@/ui/purchasePending'
import {validMaterialQuantity} from '@/ui/materialQuantity'
import PurchasePicker from '@/components/PurchasePicker.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import PurchaseShipmentsDialog from '@/components/PurchaseShipmentsDialog.vue'
import SupplierDetailDialog from '@/components/SupplierDetailDialog.vue'
import { beijingDate } from '@/utils/dateTime'


import PurchaseExportDialog from '@/components/PurchaseExportDialog.vue'
import { purchaseLineAmount } from '@/ui/purchaseAmounts'
import { costsApi } from '@/api/costs'
import PurchaseOrderDetailView from './PurchaseOrderDetailView.vue'
import { confirmAction } from '@/state/uiConfirmation'
import AccessDialog from '@/components/AccessDialog.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { catalogApi } from '@/api/catalog'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import PurchaseLogisticsDialog from '@/components/PurchaseLogisticsDialog.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import EditableSelect from '@/components/EditableSelect.vue'
import DatePicker from '@/components/DatePicker.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ApiError } from '@/api/client'
import { domainApi, type SupplierDto } from '@/api/domain'
import { purchaseApi, type PurchaseOrder, type PurchaseOrderInput, type PurchaseOrderType, type PurchaseReferenceItem, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const route = useRoute(); const router = useRouter()
const detailId=ref(''),supplierDetailId=ref(''),shipmentId=ref(''),pickSupplier=ref(false),pickLine=ref<number>(),copyFrom=ref(''),initialForm=ref(''),conflict=ref<PurchaseOrder>(),pendingSave=ref<PurchaseSave|undefined>(readPurchasePending('save')),capable=ref(false),counts=ref<Record<string,number>>({}),rowExpanded=ref<Record<string,boolean>>({}),exportBusy=ref(false)
const appliedFilters=ref(blankPurchaseFilters())
const filters=reactive(blankPurchaseFilters()),totalCount=ref(0),advanced=ref(false),editingLoading=ref(false),editLoadFailed=ref(false)
const formDialog=ref<InstanceType<typeof AccessDialog>>()
const dirty=computed(()=>showOrderForm.value&&JSON.stringify(form)!==initialForm.value)
const locked=computed(()=>saving.value||uploading.value||!!pendingSave.value||!!conflict.value||editingLoading.value||editLoadFailed.value)
let listSequence=0,alive=true
const listKey=`jiugeng.purchase.list.${session.user.value?.id??'current'}`
function remember(){try{sessionStorage.setItem(listKey,JSON.stringify({filters:{...appliedFilters.value},page:page.value,size:size.value,scroll:window.scrollY}))}catch{/* optional */}}
async function mayLeave(){return !saving.value&&!uploading.value&&(!dirty.value||!!pendingSave.value||await confirmAction('采购资料尚未保存，确定离开？', { unsaved: true, discardLabel: '放弃并离开' }))}
onBeforeRouteLeave(mayLeave)
function unload(e:BeforeUnloadEvent){if(dirty.value||saving.value||uploading.value){e.preventDefault();e.returnValue=''}}
onBeforeUnmount(()=>{alive=false;listSequence++;referenceSequence++;window.removeEventListener('beforeunload',unload);window.removeEventListener('scroll',remember)})
const detailView=ref<InstanceType<typeof PurchaseOrderDetailView>>()
async function closeDetail(){if(await detailView.value?.mayLeave()!==false){detailId.value='';await load()}}
const orders = ref<PurchaseOrder[]>([])
const deliveryOptions = ref<string[]>([])
const removingDelivery = ref(false)
async function loadDeliveryOptions() {
  try { deliveryOptions.value = await purchaseApi.deliveryOptions() }
  catch (exception) { formError.value = message(exception,'历史收货信息加载失败，可直接输入，或重新打开弹窗重试。') }
}
async function removeDeliveryOption(name:string) {
  if(removingDelivery.value || !await confirmAction(`从历史选项中删除“${name}”？已有采购单及当前填写内容不受影响。`))return
  removingDelivery.value=true;formError.value=''
  try { await purchaseApi.removeDeliveryOption(name);deliveryOptions.value=deliveryOptions.value.filter(value=>value!==name) }
  catch(exception) { formError.value=message(exception,'收货信息删除失败，请重试。') }
  finally { removingDelivery.value=false }
}
const selectedIds=ref<string[]>([]),exportOrders=ref<PurchaseOrder[]>([])
const selectedOrders=computed(()=>orders.value.filter(order=>selectedIds.value.includes(order.id)))
const actionQueue=ref<string[]>([]), listAction=ref<'receive'|'cancel'>('receive')
const canReceive=computed(()=>capable.value&&session.canAccess(['purchases.receive']))
const canCancel=computed(()=>capable.value&&session.canAccess(['purchases.cancel']))
const receiveSelectionValid=computed(()=>selectedOrders.value.length>0 && selectedOrders.value.every(order=>['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.status) && order.lines.some(line=>line.remainingQuantity>0)))
function startReceipts(){if(!canReceive.value || !selectedIds.value.length || actionQueue.value.length)return;listAction.value='receive';actionQueue.value=[...selectedIds.value]}
function startCancel(order:PurchaseOrder){if(!canCancel.value || ['COMPLETED','CANCELLED'].includes(order.status) || actionQueue.value.length)return;listAction.value='cancel';actionQueue.value=[order.id]}
async function actionCompleted(){const id=actionQueue.value.shift();selectedIds.value=selectedIds.value.filter(value=>value!==id);success.value=listAction.value==='receive'?'本次收货已入账。':'采购单已取消，已有收货记录与库存保留。';await load()}
async function closeAction(){actionQueue.value=[];await load()}
const allPageSelected=computed(()=>visibleOrders.value.length>0&&visibleOrders.value.every(o=>selectedIds.value.includes(o.id)))
function togglePage(checked:boolean){const ids=visibleOrders.value.map(o=>o.id);if(checked&&new Set([...selectedIds.value,...ids]).size>100){error.value='一次最多选择 100 张采购单';return}selectedIds.value=checked?[...new Set([...selectedIds.value,...ids])]:selectedIds.value.filter(id=>!ids.includes(id))}
const suppliers = ref<SupplierDto[]>([]); const references = ref<PurchaseReferenceItem[]>([])
const loading = ref(false); const referenceLoading = ref(false); const supplierLoading = ref(false); const saving = ref(false)
const error = ref(''); const formError = ref(''); const success = ref('')
const type = computed<PurchaseOrderType>(() => route.query.type === 'MANUAL' ? 'MANUAL' : route.query.type === 'MATERIAL' ? 'MATERIAL' : 'PRODUCT')
const status = ref<PurchaseStatus | ''>(''); const keyword = ref(''); const delivery = ref(''); const page = ref(1); const size = ref(20)
const editing = ref<PurchaseOrder>(); const showOrderForm = ref(false)
const form = reactive<PurchaseOrderInput>({ orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [] })
const logisticsOrder = ref<PurchaseOrder>()
function logisticsSaved() { void load(); logisticsOrder.value = undefined; success.value = '运单资料已保存。' }
const deleting = ref('')
const removable = computed(() => capable.value&&session.canAccess(['purchases.write', 'purchases.cancel']))
const hasReceipts = (order: PurchaseOrder) => ['PARTIALLY_RECEIVED', 'COMPLETED'].includes(order.status) || order.lines.some(line => line.receivedQuantity > 0)
async function removeOrder(order: PurchaseOrder) {
  if (deleting.value || !removable.value || hasReceipts(order)) return
  deleting.value = order.id
  try {
    if (!await confirmAction(`确认删除采购单「${order.orderNo}」？删除后将移出采购列表及待接收清单，并解除该单据的供应商关联。此操作不可恢复。`)) return
    error.value = ''; success.value = ''
    await purchaseApi.remove(order.id, order.version)
    orders.value = orders.value.filter(item => item.id !== order.id)
    page.value = Math.min(page.value, pageCount.value)
    await load();success.value = '采购单已删除。'
  } catch (e) { error.value = message(e, '删除结果未确认，请刷新列表核对后再操作。') }
  finally { deleting.value = '' }
}
const maintainable=computed(()=>capable.value&&session.canAccess(['purchases.write']))
const writable = computed(() => capable.value&&session.canAccess(['purchases.write','purchases.submit']))
const statusLabels: Record<PurchaseStatus, string> = { DRAFT: '草稿', SUBMITTED: '待收货', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消 / 余量关闭' }
const typeLabels = { PRODUCT: '产品采购', MATERIAL: '辅料采购', MANUAL: '日常物品采购' }
const uploading = ref(false), uploadTarget = ref<number>()
async function uploadItem(file:File,index:number){uploading.value=true;try{const image=await catalogApi.uploadImage('PURCHASE_ITEM',file);if(alive&&form.lines[index])form.lines[index]!.imageAssetId=image.id;uploadTarget.value=undefined}catch(e){formError.value=message(e,'图片上传失败');throw e}finally{uploading.value=false}}
function moveType(step:number){const types:PurchaseOrderType[]=['PRODUCT','MATERIAL','MANUAL'];void changeType(types[(types.indexOf(type.value)+step+3)%3]!)}
const overdue = (order: PurchaseOrder) => ['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(order.status) && !!order.expectedDeliveryDate && order.expectedDeliveryDate < beijingDate()
const filtered = computed(() => orders.value)
const pageCount = computed(() => Math.max(1, Math.ceil(totalCount.value / size.value)))
const visibleOrders = computed(() => orders.value)
function filterValue(key:string,value:string){if(key==='status')return statusLabels[value as PurchaseStatus]??value;if(key==='delivery')return value==='overdue'?'已逾期未收齐':'未填写交期';if(key==='logistics')return '未填写物流';if(key==='supplier')return orders.value.find(o=>o.supplierId===value)?.supplierName??'指定供应商';return value}
function displayLines(order:PurchaseOrder){return rowExpanded.value[order.id]?order.lines:order.lines.slice(0,2)}
async function applyFilters(){filters.type=type.value;filters.status=status.value;filters.q=keyword.value;filters.delivery=delivery.value;page.value=1;selectedIds.value=[];appliedFilters.value={...filters};remember();await load()}
async function exportSelection(image=false,all=false){if(exportBusy.value)return;exportBusy.value=true;error.value='';try{if(image)exportOrders.value=await management.selection({...appliedFilters.value},all?[]:selectedIds.value);else{const blob=await management.export({...appliedFilters.value},all?[]:selectedIds.value),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='采购明细.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}}catch(e){error.value=message(e,'导出失败')}finally{exportBusy.value=false}}
async function pickedSupplier(value:SupplierDto){if(form.supplierId&&form.supplierId!==value.id&&form.lines.some(l=>l.itemId)&&!await confirmAction('更换供应商将清空当前采购明细，是否继续？'))return;suppliers.value=[value];form.supplierId=value.id;form.supplierName=value.name;pickSupplier.value=false;await loadReferences()}
async function pickedItem(value:PurchaseReferenceItem){if(pickLine.value===undefined)return;const line=form.lines[pickLine.value];if(!line)return;if(form.lines.some(l=>l!==line&&l.itemId===value.id)){formError.value='已选择该项目，请合并数量';return}references.value=[...references.value.filter(v=>v.id!==value.id),value];line.itemId=value.id;pickLine.value=undefined;await selectItem(line)}
const total = (order: PurchaseOrder) => order.lines.reduce((sum, line) => sum + purchaseLineAmount(line), 0).toFixed(2)
const showTaxRate=computed(()=>form.lines.some(line=>line.taxIncluded))
function purchaseName(order:PurchaseOrder){return (order.lines[0]?.itemName||'未命名采购')+(order.lines.length>1?' 等 '+order.lines.length+' 项':'')}
const formTotal = computed(() => form.lines.reduce((sum, line) => sum + purchaseLineAmount(line), 0).toFixed(2))
const requestedMaterialQuantities=ref<string>('')
const requestedMaterials = ref<string[]>([])
let referenceSequence = 0
function message(exception: unknown, fallback: string) { return exception instanceof Error ? exception.message : fallback }
function resetFilters() { status.value='';keyword.value='';delivery.value='';Object.assign(filters,blankPurchaseFilters(),{type:type.value});void applyFilters() }
async function changeType(value: PurchaseOrderType) { await router.replace({query:{type:value}});status.value='';keyword.value='';delivery.value='';Object.assign(filters,blankPurchaseFilters(),{type:value});page.value=1;selectedIds.value=[];appliedFilters.value={...filters};await load() }
function closeForm() { if (saving.value || uploading.value) return; referenceSequence++; showOrderForm.value = false; requestedMaterials.value = []; references.value = []; formError.value = '' }
async function load() {
 const seq=++listSequence;loading.value=true;error.value='';filters.type=type.value
 try{const [result,totals]=await Promise.all([management.page({...appliedFilters.value},page.value-1,size.value),management.counts()]);if(seq!==listSequence)return;orders.value=result.items;totalCount.value=result.total;counts.value=totals;if(page.value>pageCount.value){page.value=pageCount.value;await load()}remember()}
 catch(e){if(seq===listSequence)error.value=message(e,'采购数据读取失败')+(orders.value.length?'；以下为上次读取结果':'')}
 finally{if(seq===listSequence)loading.value=false}
}
async function loadSuppliers(){supplierLoading.value=true;try{if(form.supplierId){const value=await domainApi.supplier(form.supplierId);suppliers.value=[value]}else suppliers.value=[]}catch(e){formError.value=message(e,'供应商读取失败')}finally{supplierLoading.value=false}}
async function loadReferences(resetLines = true) {
  const sequence = ++referenceSequence
  if(resetLines)references.value = []; formError.value = ''
  if (resetLines) form.lines = [{ itemId: '', quantity: 1, unitPrice: 0, taxIncluded:false, taxRate:1 }]
  if (form.orderType === 'MANUAL' || !form.supplierId) { referenceLoading.value = false; return }
  referenceLoading.value = true
  try { const result = (await management.references(form.orderType, form.supplierId)).items; if (sequence === referenceSequence) {
    references.value = [...new Map([...references.value,...result].map(v=>[v.id,v])).values()]
    if(resetLines && requestedMaterials.value.length && form.orderType === 'MATERIAL') {
      const selected=requestedMaterials.value.map(id=>result.find(item=>item.id===id))
      if(selected.some(item=>!item)) {
        formError.value='该供应商未关联当前辅料（或部分所选辅料），请换共同供应商或分批采购。'
        return
      }
      let suggested:Record<string,number>={}
      try { const raw=requestedMaterialQuantities.value;if(typeof raw==='string'){const value=JSON.parse(raw);if(value&&typeof value==='object')suggested=value} } catch { /* Invalid optional suggestions are ignored. */ }
      const lines=selected.map(item=>({itemId:item!.id,quantity:Number.isFinite(suggested[item!.id])&&Number(suggested[item!.id])>0?Number(suggested[item!.id]):1,unitPrice:0,taxIncluded:false,taxRate:1}))
      const warnings:string[]=[]
      if(session.canAccess(['costs.read'])) {
        await Promise.all(selected.map(async (item,index)=>{
          try {
            const price=await costsApi.price(item!.id)
            if(!price) warnings.push(item!.name+'尚未维护成本价')
            else if(price.unit!==item!.unit || form.currency!=='CNY') warnings.push(item!.name+'：单位或币制与采购单不一致')
            else if(!validNumber(Number(price.netPrice),false)) warnings.push(item!.name+'：成本价超出四位小数')
            else lines[index]!.unitPrice=Number(price.netPrice)
          }catch {warnings.push(item!.name+'：成本价读取失败')}
        }))
      }else warnings.push('当前账号无成本价查看权限')
      if(sequence!==referenceSequence)return
      form.lines=lines
      if(warnings.length)formError.value=warnings.join('；')+'，请手动填写本次采购单价。'
    }
  } }
  catch (exception) { if (sequence === referenceSequence) formError.value = message(exception, '可采购项目加载失败，请重新选择供应商。') }
  finally { if (sequence === referenceSequence) referenceLoading.value = false }
}
const priceLoads=ref(0)
async function selectItem(line:PurchaseOrderInput['lines'][number]){
  const id=line.itemId, supplier=form.supplierId
  line.unitPrice=0;line.taxIncluded=false;line.taxRate=1
  const item=references.value.find(item=>item.id===id)
  if(!item)return
  priceLoads.value++;formError.value=''
  try{
    if(form.orderType==='MATERIAL'&&session.canAccess(['costs.read'])){
      const price=await costsApi.price(id)
      if(line.itemId!==id||form.supplierId!==supplier||!form.lines.includes(line))return
      if(price&&price.unit===item.unit&&form.currency==='CNY'&&!validNumber(Number(price.netPrice),false)){formError.value='成本价超出四位小数，请核对并手动填写本次采购单价。'}
      else if(price&&price.unit===item.unit&&form.currency==='CNY'){line.unitPrice=Number(price.netPrice);formError.value='已带入当前辅料标准成本（CNY），请核对本次采购价格。'}
      else formError.value='当前辅料未维护同单位人民币成本价，请填写采购单价。'
    }else{
      const previous=await management.price(id,supplier,form.currency)
      if(line.itemId!==id||form.supplierId!==supplier||!form.lines.includes(line))return
      if(previous.unitPrice!==undefined&&previous.unit===item.unit){line.unitPrice=previous.unitPrice;formError.value=`已带入 ${previous.orderNo}（${previous.createdAt?.slice(0,10)}）的同币种历史价格，请核对。`;line.taxIncluded=previous.taxIncluded??false;line.taxRate=previous.taxIncluded?(previous.taxRate??1):1}
      else formError.value='暂无可带入的采购价格，请填写本次采购单价。'
    }
  }catch{if(line.itemId===id)formError.value='单价读取失败，请手动填写采购单价。'}
  finally{priceLoads.value--}
}
function defaultDeliveryDate(){
  const day=beijingDate()
  const date=new Date(day+'T00:00:00Z');date.setUTCDate(date.getUTCDate()+2)
  return date.toISOString().slice(0,10)
}
async function openCreate() {
  if(pendingSave.value){resumeSave();return}editLoadFailed.value=false;copyFrom.value='';conflict.value=undefined;requestedMaterials.value=[]; editing.value = undefined; formError.value = ''; references.value = []; referenceSequence++
  Object.assign(form, { orderType: type.value, supplierId: '', supplierName: '', currency: 'CNY', remark: '', version: undefined, expectedDeliveryDate: defaultDeliveryDate(), deliveryAddress: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0, taxIncluded:false, taxRate:1 }] })
  initialForm.value=JSON.stringify(form);showOrderForm.value = true; await Promise.all([loadDeliveryOptions(),type.value!=='MANUAL'?loadSuppliers():Promise.resolve()])
}
async function pool<T,R>(items:T[],run:(v:T)=>Promise<R>){const out:R[]=[];let cursor=0;await Promise.all(Array.from({length:Math.min(4,items.length)},async()=>{while(cursor<items.length){const i=cursor++;out[i]=await run(items[i]!)}}));return out}
async function createFromMaterials(ids:string[]) {
  if(!writable.value || !session.canAccess(['materials.read','suppliers.read']))return
  await openCreate()
  if(!showOrderForm.value || form.orderType!=='MATERIAL')return
  if(ids.length>200){formError.value='每张采购单最多 200 项，请分批选择。';return}
  requestedMaterials.value=ids
  try {
    const groups=await pool(ids,id=>domainApi.materialSupplierLinks(id))
    if(!alive||!showOrderForm.value || requestedMaterials.value.join(',')!==ids.join(','))return
    const common=(groups[0]??[]).filter(link=>groups.every(group=>group.some(l=>l.supplierId===link.supplierId)))
    const candidates=await pool(common,link=>domainApi.supplier(link.supplierId));const available=common.filter(link=>candidates.some(s=>s.id===link.supplierId&&s.status==='ACTIVE'&&s.categories?.includes('MATERIAL')))
    const selected=available.find(link=>groups.every(group=>group.some(l=>l.supplierId===link.supplierId&&l.primary)))??available[0]
    if(selected){form.supplierId=selected.supplierId;await loadSuppliers();const records=await pool(ids,id=>domainApi.material(id));references.value=records.map(m=>({id:m.id,code:m.materialCode,name:m.name,unit:m.unit,primarySupplier:false,imageAssetId:m.imageAssetId??null,imageUrl:m.imageUrl??null}));await loadReferences(false);let qs:Record<string,number>={};try{qs=JSON.parse(requestedMaterialQuantities.value||'{}')}catch{/* optional quantities */}if(!alive||!showOrderForm.value)return;form.lines=references.value.filter(v=>ids.includes(v.id)).map(v=>({itemId:v.id,quantity:Number(qs[v.id])>0?Number(qs[v.id]):1,unitPrice:0}));await pool(form.lines,selectItem)}
    else formError.value=ids.length===1?'当前辅料未关联启用的供应商，请先回到辅料管理编辑供应商关系，再发起采购。':'所选辅料没有共同的启用供应商，请按供应商分批勾选采购，或先维护供应商关系。'
  }catch(e){formError.value=message(e,'辅料供应商关系加载失败，请关闭后从辅料列表重试。')}
}
watch(()=>[route.query.materialId,route.query.materialIds,capable.value], async ([id,batch])=>{
  if(id||batch)requestedMaterialQuantities.value=typeof route.query.materialQuantities==='string'?route.query.materialQuantities:''
  const value=typeof batch==='string'?batch:typeof id==='string'?id:''
  if(!capable.value||!value||type.value!=='MATERIAL')return
  await createFromMaterials([...new Set(value.split(',').filter(Boolean))])
  if(route.query.materialId===id&&route.query.materialIds===batch){const query={...route.query};delete query.materialId;delete query.materialIds;delete query.materialQuantities;await router.replace({query})}
},{immediate:true})
async function openEdit(order: PurchaseOrder,copy=false) {
  if(pendingSave.value){resumeSave();return}
  editingLoading.value=true;editLoadFailed.value=false;formError.value='';showOrderForm.value=true
  try{order=await purchaseApi.order(order.id)}catch(e){editLoadFailed.value=true;formError.value=message(e,'单据读取失败，无法编辑');return}finally{editingLoading.value=false}
  copyFrom.value=copy?order.id:'';conflict.value=undefined
  editing.value = order; formError.value = ''; showOrderForm.value = true
  Object.assign(form, { orderType: order.orderType, supplierId: order.supplierId, supplierName: order.supplierName, currency: order.currency, remark: order.remark ?? '', version: order.version, expectedDeliveryDate: order.expectedDeliveryDate ?? '', deliveryAddress: order.deliveryAddress ?? '', lines: order.lines.map(line => ({ itemId: line.itemId, quantity: line.orderedQuantity, packageCount:line.packageCount??null, unitPrice: line.unitPrice, taxIncluded:line.taxIncluded??false,taxRate:line.taxIncluded?(line.taxRate??1):1, itemName:line.itemName, unit:line.unit, specification:line.specification??'', imageAssetId:line.imageAssetId })) })
  references.value=order.lines.map(l=>({id:l.itemId,code:l.itemCode,name:l.itemName,unit:l.unit,primarySupplier:false,imageAssetId:l.imageAssetId??null,imageUrl:l.imageUrl??null}))
  if(copy){editing.value=undefined;delete form.version;form.expectedDeliveryDate=defaultDeliveryDate();form.lines.forEach(l=>{l.packageCount=null})}
  initialForm.value=JSON.stringify(form)
  await Promise.all([loadDeliveryOptions(),...(order.orderType!=='MANUAL'?[loadSuppliers(),loadReferences(false)]:[])])
}
function addLine() { if (form.lines.length < 200) form.lines.push({ itemId: '', quantity: 1, unitPrice: 0, taxIncluded:false, taxRate:1 }) }
function validNumber(value: number, positive: boolean) { return Number.isFinite(Number(value)) && (positive ? Number(value) > 0 : Number(value) >= 0) && /^\d+(\.\d{1,4})?$/.test(String(value)) }
async function saveOrder() {
  if (saving.value || uploading.value || editingLoading.value || editLoadFailed.value || conflict.value || referenceLoading.value || priceLoads.value>0) return
  if(pendingSave.value){await sendSave();return}
  formError.value = ''; success.value = ''
  if(form.lines.some(line=>line.taxIncluded&&(!Number.isFinite(Number(line.taxRate))||Number(line.taxRate)<0||Number(line.taxRate)>100||!/^\d+(\.\d{1,2})?$/.test(String(line.taxRate))))){formError.value='税率须为 0 至 100，最多两位小数。';return}
  if (form.orderType !== 'MANUAL' && (!form.supplierId || !form.lines.length || form.lines.some(line => !references.value.some(item => item.id === line.itemId)))) { formError.value = '请选择供应商及有效采购项目。'; return }
  if(form.orderType==='MANUAL' && form.lines.some(line=>!line.itemName?.trim()||!line.unit?.trim())){formError.value='请填写物品名称及单位。';return}
  if (form.orderType !== 'MANUAL' && new Set(form.lines.map(line => line.itemId)).size !== form.lines.length) { formError.value = '采购项目不可重复，请合并相同项目的数量。'; return }
  if (form.lines.some(line => !validNumber(line.quantity, true) || !validNumber(line.unitPrice, false))) { formError.value = '数量必须大于 0，单价不可为负数，最多支持四位小数。'; return }
  if(form.lines.some(line => line.packageCount != null && String(line.packageCount) !== '' && (!Number.isInteger(Number(line.packageCount)) || Number(line.packageCount)<0 || Number(line.packageCount)>2147483647))){formError.value='包裹数须为 0 至 2147483647 的整数。';return}
  if(form.orderType!=='MANUAL'&&form.lines.some(l=>!validMaterialQuantity(l.quantity,form.orderType==='PRODUCT'?'套':references.value.find(v=>v.id===l.itemId)?.unit??'个'))){formError.value='产品和计数辅料须填写整数，计量辅料最多四位小数';return}
  const payload = { ...form, expectedDeliveryDate: form.expectedDeliveryDate || null, lines: form.lines.map(line => ({ ...line,packageCount:line.packageCount==null||String(line.packageCount)===''?null:Number(line.packageCount),taxIncluded:line.taxIncluded??false,taxRate:line.taxIncluded?(line.taxRate??1):0 })) }
    pendingSave.value={requestId:crypto.randomUUID(),id:editing.value?.id??null,order:JSON.parse(JSON.stringify(payload)),...(copyFrom.value?{copyFromId:copyFrom.value}:{})};writePurchasePending('save',pendingSave.value);await sendSave()
}
function resumeSave(){const input=pendingSave.value;if(!input)return;editLoadFailed.value=false;conflict.value=undefined;Object.assign(form,input.order);editing.value=input.id?{id:input.id} as PurchaseOrder:undefined;copyFrom.value=input.copyFromId??'';initialForm.value=JSON.stringify(form);showOrderForm.value=true}
async function sendSave(){if(!pendingSave.value)return;saving.value=true;const input=pendingSave.value
 try{const saved=await management.save(input);pendingSave.value=undefined;writePurchasePending('save',null);saving.value=false;closeForm();await load();success.value=orders.value.some(o=>o.id===saved.id)?'采购单已保存，等待收货。':'采购单已保存；不在当前页或当前筛选范围内。'}
 catch(e){formError.value=message(e,'保存结果待确认，请确认原请求');if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pendingSave.value=undefined;writePurchasePending('save',null);if(e.code==='PURCHASE_VERSION_CONFLICT'&&input.id){try{conflict.value=await purchaseApi.order(input.id)}catch{formError.value+='；最新资料读取失败，请保留草稿重试'}}}}
 finally{saving.value=false}
}
async function adoptLatest(){const value=conflict.value;if(!value)return;conflict.value=undefined;await openEdit(value)}
function keepDraft(){if(!conflict.value)return;form.version=conflict.value.version;editing.value=conflict.value;conflict.value=undefined;formError.value='草稿已保留，请核对状态、数量及价格后重新保存'}
watch(selectedIds,ids=>{if(ids.length>100){selectedIds.value=ids.slice(0,100);error.value='一次最多选择 100 张采购单'}})
watch([page,size],()=>{if(capable.value){remember();void load()}})
onMounted(async()=>{window.addEventListener('beforeunload',unload);window.addEventListener('scroll',remember,{passive:true});try{const saved=JSON.parse(sessionStorage.getItem(listKey)??'null');if(saved?.filters){Object.assign(filters,saved.filters,{type:type.value});appliedFilters.value={...filters};keyword.value=filters.q;status.value=filters.status as PurchaseStatus|'';delivery.value=filters.delivery;page.value=saved.page||1;size.value=saved.size||20}appliedFilters.value={...filters,type:type.value};capable.value=(await management.capabilities()).closeoutVersion>=1;await load();if(saved?.scroll){await nextTick();window.scrollTo(0,saved.scroll)}}catch(e){error.value=message(e,'采购新接口不可用，请更新本地后端后刷新；维护功能暂不可用')}})


watch(pageCount, value => { if (page.value > value) page.value = value })

</script>

<template>
  <section class="page-stack purchase-page purchase-ui">
    <header class="panel purchase-header">
      <div>
        <p class="eyebrow">
          PURCHASE ORDERS
        </p><h1>采购管理</h1><p>建单并下单 → 分批收货 → 完成，采购与到货进度在一处管理。</p>
      </div>
      <div class="purchase-header-actions">
        <button
          v-if="writable"
          class="primary-link"
          type="button"
          :disabled="showOrderForm"
          @click="openCreate"
        >
          新建{{ typeLabels[type] }}单
        </button>
      </div>
    </header>
    <div
      class="purchase-tabs"
      role="tablist"
      aria-label="采购类型"
    >
      <button
        v-for="(label, key) in typeLabels"
        :id="`tab-${key}`"
        :key="key"
        type="button"
        role="tab"
        :aria-selected="type === key"
        aria-controls="purchase-orders-panel"
        @click="changeType(key)"
        @keydown.right.prevent="moveType(1)"
        @keydown.left.prevent="moveType(-1)"
      >
        {{ label }} <span>{{ counts[key]??0 }}</span>
      </button>
    </div>
    <p
      v-if="error"
      class="error-message"
      role="alert"
    >
      {{ error }}
    </p><SuccessToast :message="success" />
    <p
      v-if="pendingSave"
      class="purchase-warning"
    >
      有一笔保存结果待确认。<button @click="resumeSave">
        恢复并确认原请求
      </button>
    </p>
    <section class="panel filter-panel purchase-order-filters">
      <label>搜索<input
        v-model="keyword"
        placeholder="采购单号 / 供应商 / SKU / 辅料"
        aria-label="搜索采购单"
      ></label>
      <label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部状态</option><option
        v-for="(label,key) in statusLabels"
        :key="key"
        :value="key"
      >{{ label }}</option></select></label>
      <label>交期<select
        v-model="delivery"
        v-single-select
      ><option value="">全部交期</option><option value="overdue">已逾期未收齐</option><option value="missing">未填写交期</option></select></label>
      <button
        type="button"
        @click="applyFilters"
      >
        搜索
      </button><button @click="resetFilters">
        重置
      </button><button @click="advanced=!advanced">
        更多筛选
      </button><button
        type="button"
        :disabled="loading"
        @click="load"
      >
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
      <div
        v-if="advanced"
        class="purchase-advanced"
      >
        <label>创建起始<DatePicker
          v-model="filters.createdFrom"
          type="date"
        /></label><label>创建截止<DatePicker
          v-model="filters.createdTo"
          type="date"
        /></label><label>交期起始<DatePicker
          v-model="filters.deliveryFrom"
          type="date"
        /></label><label>交期截止<DatePicker
          v-model="filters.deliveryTo"
          type="date"
        /></label><label>物流<select
          v-model="filters.logistics"
          v-single-select
        ><option value="">全部</option><option value="missing">未填物流</option></select></label>
      </div>
      <div class="purchase-filter-tags">
        <span
          v-for="(value,key) in appliedFilters"
          v-show="value&&key!=='type'"
          :key="key"
        >{{ ({status:'状态',q:'关键词',supplier:'供应商',createdFrom:'创建起始',createdTo:'创建截止',deliveryFrom:'交期起始',deliveryTo:'交期截止',delivery:'交期',logistics:'物流',type:'类型'})[key] }}：{{ filterValue(key,value) }} <button
          :aria-label="`移除${key}筛选`"
          @click="filters[key]='';if(key==='q')keyword='';if(key==='status')status='';if(key==='delivery')delivery='';applyFilters()"
        >×</button></span>
      </div>
    </section>
    <section
      id="purchase-orders-panel"
      class="panel table-panel"
      role="tabpanel"
      :aria-labelledby="`tab-${type}`"
      :aria-busy="loading"
    >
      <div class="purchase-list-actions">
        <button
          v-if="canReceive"
          type="button"
          class="purchase-batch-action"
          :disabled="!selectedIds.length || loading || !!actionQueue.length"
          :title="selectedOrders.length && !receiveSelectionValid ? '请选择待收货或部分收货的采购单' : '按所选采购单逐单登记收货'"
          @click="startReceipts"
        >
          登记收货
        </button>
        <button
          type="button"
          class="purchase-batch-action"
          :disabled="!selectedIds.length || loading || exportBusy"
          @click="exportSelection(true)"
        >
          导出图片{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
        </button>
        <button
          :disabled="exportBusy||!selectedIds.length"
          @click="exportSelection()"
        >
          导出选中 CSV
        </button><button
          :disabled="exportBusy||!totalCount"
          @click="exportSelection(false,true)"
        >
          导出筛选 CSV
        </button><button
          v-if="selectedIds.length"
          @click="selectedIds=[]"
        >
          清空选择（{{ selectedIds.length }}）
        </button>
      </div>
      <p class="purchase-list-note">
        {{ type === 'MANUAL' ? '直接录入未建档的日常物品，支持图片及分批收货，不计入产品或辅料库存。' : type === 'PRODUCT' ? '产品按 SKU 采购，新登记收货同步增加产品库存；历史收货不自动补账。' : '辅料按档案单位采购，登记收货后同步增加固定仓库存。' }} 收货与历史记录在采购单详情查看。
      </p>
      <p
        v-if="loading"
        class="purchase-list-note"
      >
        正在加载采购单…
      </p>
      <div
        v-else
        class="table-scroll"
      >
        <TableViewport>
          <table class="data-table ui-action-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="全选当前页采购单"
                    :checked="allPageSelected"
                    :indeterminate="!allPageSelected && visibleOrders.some(o=>selectedIds.includes(o.id))"
                    @change="togglePage(($event.target as HTMLInputElement).checked)"
                  >
                </th><th>采购名称 / 供应商</th><th>商品</th><th>数量 / 单位</th><th>物流</th><th>采购金额</th><th>收货进度</th><th>预计到货</th><th>状态</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filtered.length">
                <td
                  colspan="10"
                  class="muted"
                >
                  暂无采购记录
                </td>
              </tr>
              <tr
                v-for="order in visibleOrders"
                :key="order.id"
              >
                <td>
                  <input
                    v-model="selectedIds"
                    type="checkbox"
                    :value="order.id"
                    :aria-label="`选择采购单 ${purchaseName(order)}`"
                  >
                </td>
                <td>
                  <button
                    type="button"
                    class="text-link purchase-name-link"
                    :data-order-id="order.id"
                    @click="detailId=order.id"
                  >
                    <strong>{{ purchaseName(order) }}</strong>
                  </button><small><button
                    class="text-link"
                    :disabled="!order.supplierId"
                    @click="supplierDetailId=order.supplierId"
                  >{{ order.supplierName }}</button> <button
                    v-if="order.supplierId"
                    class="text-link"
                    @click="filters.supplier=order.supplierId;applyFilters()"
                  >筛选</button></small><small>{{ order.orderNo }}</small><small>{{ beijingDate(order.createdAt) }} 创建</small>
                </td>
                <td>
                  <div
                    v-for="line in displayLines(order)"
                    :key="line.id"
                    class="purchase-list-product"
                  >
                    <PurchaseItemImage
                      :image-asset-id="line.imageAssetId"
                      :image-url="line.imageUrl"
                      :alt="line.itemName"
                    />
                    <div :title="line.itemName">
                      <span>{{ line.itemName }}</span>
                    </div>
                  </div>
                  <button
                    v-if="order.lines.length>2"
                    class="text-link"
                    :aria-expanded="!!rowExpanded[order.id]"
                    @click="rowExpanded[order.id]=!rowExpanded[order.id]"
                  >
                    {{ rowExpanded[order.id]?'收起':`展开全部 ${order.lines.length} 项` }}
                  </button><small v-else>共 {{ order.lines.length }} 项</small>
                </td>
                <td>
                  <div
                    v-for="line in displayLines(order)"
                    :key="line.id"
                    class="purchase-list-measure"
                  >
                    {{ line.orderedQuantity }} {{ line.unit }}
                  </div><small>&nbsp;</small>
                </td>
                <td>
                  <span>{{ order.carrier || '未填写物流公司' }}</span><small>{{ order.trackingNumber || '未填写运单号' }}</small><button
                    v-if="order.trackingNumber || order.waybillImageId || maintainable"
                    type="button"
                    class="text-link"
                    @click="logisticsOrder = order"
                  >
                    {{ order.trackingNumber || order.waybillImageId ? '原物流资料' : '填写物流' }}
                  </button>
                </td>
                <td>{{ order.currency }} {{ total(order) }}</td>
                <td>{{ order.lines.filter(line => line.remainingQuantity === 0).length }} / {{ order.lines.length }} 项已收齐<small>{{ order.status === 'CANCELLED' ? '未收余量已关闭' : '分项数量见详情' }}</small></td>
                <td :class="{ 'purchase-delivery-date': overdue(order) }">
                  {{ order.expectedDeliveryDate || '未约定' }}<small v-if="overdue(order)">逾期未收齐</small>
                </td>
                <td>
                  <span
                    class="purchase-status"
                    :data-status="order.status"
                  >{{ statusLabels[order.status] }}</span>
                </td>
                <td class="table-actions">
                  <button
                    type="button"
                    class="text-link"
                    @click="detailId=order.id"
                  >
                    详情
                  </button><button
                    v-if="canReceive&&['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.status)"
                    @click="listAction='receive';actionQueue=[order.id]"
                  >
                    收货
                  </button><RowMoreActions :label="`${order.orderNo}更多操作`">
                    <button
                      v-if="writable"
                      @click="openEdit(order,true)"
                    >
                      再次采购
                    </button><button @click="shipmentId=order.id">
                      发货批次 / 多运单
                    </button><button
                      v-if="maintainable && (order.status==='SUBMITTED'||writable&&order.status==='DRAFT')"
                      type="button"
                      class="secondary-button"
                      @click="openEdit(order)"
                    >
                      编辑
                    </button><button
                      v-if="canCancel && !['COMPLETED','CANCELLED'].includes(order.status)"
                      type="button"
                      class="danger-link"
                      :disabled="!!actionQueue.length"
                      @click="startCancel(order)"
                    >
                      取消
                    </button><button
                      v-if="removable"
                      type="button"
                      class="danger-link"
                      :disabled="!!deleting || hasReceipts(order)"
                      :title="hasReceipts(order) ? '已有收货记录，不能删除' : '删除采购单'"
                      @click="removeOrder(order)"
                    >
                      {{ deleting === order.id ? '处理中…' : '删除' }}
                    </button>
                  </RowMoreActions>
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
      </div>
      <ListPagination
        v-model:page-size="size"
        class="purchase-pagination"
        :page="page"
        :total="totalCount"
        :disabled="loading"
        @update:page="page = $event"
      />
    </section>
    <AccessDialog
      v-if="showOrderForm"
      ref="formDialog"
      :title="copyFrom?'再次采购':editing?'编辑采购单':'新建采购单'"
      wide
      dialog-class="purchase-ui purchase-editor"
      :busy="saving||uploading"
      :dirty="dirty&&!pendingSave"
      @close="closeForm"
    >
      <p v-if="editingLoading">
        正在读取最新单据…
      </p><p
        v-if="pendingSave"
        class="purchase-warning"
      >
        保存结果待确认，草稿已锁定，请使用原请求核对。
      </p>
      <section
        v-if="conflict"
        class="purchase-warning"
      >
        <strong>采购单已被更新，请核对差异</strong><table class="data-table">
          <thead><tr><th>字段</th><th>我的草稿</th><th>最新单据</th></tr></thead><tbody>
            <tr><td>供应商 / 交期</td><td>{{ form.supplierName }} / {{ form.expectedDeliveryDate }}</td><td>{{ conflict.supplierName }} / {{ conflict.expectedDeliveryDate }}</td></tr><tr>
              <td>明细 / 数量 / 单价</td><td>
                <p
                  v-for="(line,i) in form.lines"
                  :key="i"
                >
                  {{ line.itemName||references.find(v=>v.id===line.itemId)?.name }} · {{ line.quantity }} × {{ line.unitPrice }}
                </p>
              </td><td>
                <p
                  v-for="line in conflict.lines"
                  :key="line.id"
                >
                  {{ line.itemName }} · {{ line.orderedQuantity }} × {{ line.unitPrice }}
                </p>
              </td>
            </tr><tr><td>备注 / 收货信息</td><td>{{ form.remark }} / {{ form.deliveryAddress }}</td><td>{{ conflict.remark }} / {{ conflict.deliveryAddress }}</td></tr>
          </tbody>
        </table><button @click="adoptLatest">
          采用最新资料
        </button><button
          :disabled="!['DRAFT','SUBMITTED'].includes(conflict.status)"
          @click="keepDraft"
        >
          保留草稿重新核对
        </button>
      </section>
      <form
        id="purchase-editor-form"
        @submit.prevent="saveOrder"
      >
        <p class="form-wide purchase-form-hint">
          {{ form.orderType==='MANUAL' ? '直接填写物品及图片，无需维护 SKU 或辅料；供应商 / 购买渠道可选填。' : '一单一供应商，产品与辅料不混单。' }}保存即确认下单，进入待收货；系统不会向供应商自动发送订单。
        </p>
        <p
          v-if="formError"
          class="error-message form-wide"
          role="alert"
        >
          {{ formError }}
        </p>
        <fieldset
          class="purchase-form-fields form-wide"
          :disabled="locked"
        >
          <label v-if="form.orderType==='MANUAL'">供应商 / 购买渠道<input
            v-model="form.supplierName"
            maxlength="200"
            placeholder="选填，如五金店、采购平台"
          ></label>
          <label v-else>供应商<button
            type="button"
            class="purchase-picker-button"
            :disabled="locked"
            @click="pickSupplier=true"
          >{{ suppliers.find(v=>v.id===form.supplierId)?.name||form.supplierName||'搜索并选择供应商' }}</button></label>
          <label>预计到货日期<DatePicker
            v-model="form.expectedDeliveryDate"
            type="date"
            min="2000-01-01"
            max="9999-12-31"
          /></label>
          <label>收货信息<EditableSelect
            :model-value="form.deliveryAddress ?? ''"
            :options="deliveryOptions"
            label="收货信息"
            :max-length="500"
            :disabled="removingDelivery"
            @remove="removeDeliveryOption"
            @update:model-value="form.deliveryAddress = $event"
          /></label>
          <p
            v-if="form.orderType!=='MANUAL' && form.supplierId && !referenceLoading && !references.length"
            class="form-wide empty-inline"
          >
            该供应商尚未关联启用的{{ form.orderType === 'PRODUCT' ? 'SKU' : '辅料' }}，请先到对应档案维护供货关系。
          </p>
          <div class="form-wide table-scroll">
            <TableViewport>
              <table
                class="data-table purchase-edit-lines"
                :class="{'manual-purchase-lines':form.orderType==='MANUAL'}"
              >
                <thead>
                  <tr>
                    <th>
                      图片
                    </th><th>采购项目</th><th>数量 / 单位</th><th>包裹数</th><th>不含税单价</th><th>是否含税</th><th v-if="showTaxRate">
                      税率（%）
                    </th><th>金额</th><th>操作</th>
                  </tr>
                </thead><tbody>
                  <tr
                    v-for="(line,index) in form.lines"
                    :key="index"
                  >
                    <td v-if="form.orderType!=='MANUAL'">
                      <PurchaseItemImage
                        :image-asset-id="references.find(item=>item.id===line.itemId)?.imageAssetId"
                        :image-url="references.find(item=>item.id===line.itemId)?.imageUrl"
                        :alt="references.find(item=>item.id===line.itemId)?.name||'采购项目'"
                      />
                    </td>
                    <td
                      v-else
                      class="manual-image-cell"
                    >
                      <PurchaseItemImage
                        v-if="line.imageAssetId"
                        :image-asset-id="line.imageAssetId"
                        :alt="line.itemName||'采购物品'"
                      />
                      <button
                        v-if="line.imageAssetId"
                        type="button"
                        class="ui-image-remove"
                        @click="line.imageAssetId=null"
                      >
                        移除图片
                      </button>
                      <button
                        type="button"
                        class="secondary-button"
                        @click="uploadTarget=index"
                      >
                        {{ line.imageAssetId ? '更换图片' : '上传图片' }}
                      </button>
                    </td>
                    <td>
                      <template v-if="form.orderType==='MANUAL'">
                        <input
                          v-model="line.itemName"
                          required
                          maxlength="200"
                          :aria-label="`物品名称 ${index+1}`"
                          placeholder="名称，如纸巾、灯泡"
                        ><input
                          v-model="line.specification"
                          maxlength="500"
                          aria-label="物品规格"
                          placeholder="规格 / 型号（选填）"
                        >
                      </template>
                      <button
                        v-else
                        type="button"
                        class="purchase-picker-button"
                        @click="pickLine=index"
                      >
                        {{ references.find(v=>v.id===line.itemId)?.name||`第 ${index+1} 行：选择项目` }}
                      </button>
                    </td>
                    <td>
                      <input
                        v-model.number="line.quantity"
                        required
                        type="number"
                        min="0.0001"
                        step="0.0001"
                        aria-label="采购数量"
                      >
                      <input
                        v-if="form.orderType==='MANUAL'"
                        v-model="line.unit"
                        required
                        maxlength="32"
                        aria-label="物品单位"
                        placeholder="个 / 包 / 箱"
                      ><template v-else>
                        {{ references.find(item => item.id === line.itemId)?.unit || '—' }}
                      </template>
                    </td>
                    <td>
                      <input
                        v-model.number="line.packageCount"
                        type="number"
                        min="0"
                        max="2147483647"
                        step="1"
                        aria-label="包裹数"
                        placeholder="选填"
                      >
                    </td>
                    <td>
                      <input
                        v-model.number="line.unitPrice"
                        required
                        type="number"
                        min="0"
                        step="0.0001"
                        aria-label="采购单价"
                      >
                    </td>
                    <td>
                      <select
                        v-model="line.taxIncluded"
                        v-single-select
                        :aria-label="`是否含税 ${index+1}`"
                      >
                        <option :value="false">
                          否
                        </option><option :value="true">
                          是
                        </option>
                      </select>
                    </td>
                    <td v-if="showTaxRate">
                      <input
                        v-if="line.taxIncluded"
                        v-model.number="line.taxRate"
                        required
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        :aria-label="`税率 ${index+1}`"
                      ><span v-else>—</span>
                    </td>
                    <td>{{ (purchaseLineAmount(line)).toFixed(2) }}</td>
                    <td>
                      <button
                        type="button"
                        class="secondary-button"
                        :disabled="form.lines.length <= 1"
                        @click="form.lines.splice(index, 1)"
                      >
                        移除
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </TableViewport>
          </div>
          <div class="form-wide purchase-section-heading">
            <button
              type="button"
              class="secondary-button"
              :disabled="(form.orderType!=='MANUAL' && !references.length) || form.lines.length >= 200"
              @click="addLine"
            >
              增加明细
            </button><strong>合计 {{ form.currency.toUpperCase() }} {{ formTotal }}</strong>
          </div>
          <label class="form-wide">采购备注<textarea
            v-model="form.remark"
            maxlength="500"
            placeholder="包装要求、供货约定等，可换行"
          /></label>
        </fieldset>
      </form>
      <template #footer>
        <strong>合计 {{ form.currency }} {{ formTotal }}</strong><button
          type="button"
          :disabled="saving||uploading"
          @click="formDialog?.requestClose()"
        >
          取消
        </button><button
          type="submit"
          form="purchase-editor-form"
          :disabled="saving||uploading||!!conflict||editingLoading||editLoadFailed"
        >
          {{ saving?'保存中…':pendingSave?'确认原请求':editing?'保存修改':'创建并确认下单' }}
        </button>
      </template>
    </AccessDialog>
    <PurchasePicker
      v-if="pickSupplier"
      :type="form.orderType"
      @close="pickSupplier=false"
      @supplier="pickedSupplier"
    />
    <PurchasePicker
      v-if="pickLine!==undefined"
      :type="form.orderType"
      :supplier-id="form.supplierId"
      @close="pickLine=undefined"
      @item="pickedItem"
    />
    <SupplierDetailDialog
      v-if="supplierDetailId"
      :id="supplierDetailId"
      @close="supplierDetailId=''"
    />
    <PurchaseShipmentsDialog
      v-if="shipmentId"
      :id="shipmentId"
      :writable="maintainable"
      @close="shipmentId='';load()"
    />
    <AccessDialog
      v-if="uploadTarget!==undefined"
      title="采购物品图片"
      :busy="uploading"
      @close="uploadTarget=undefined"
    >
      <SingleFileUpload
        title="物品图片"
        accept=".png,.jpg,.jpeg,.webp"
        :max-mb="5"
        :disabled="saving||uploading"
        :upload="file=>uploadItem(file,uploadTarget!)"
      />
    </AccessDialog>
    <PurchaseExportDialog
      v-if="exportOrders.length"
      :orders="exportOrders"
      @close="exportOrders=[]"
    />
  </section>
  <AccessDialog
    v-if="detailId"
    title="采购单详情"
    wide
    @close="closeDetail"
  >
    <PurchaseOrderDetailView
      ref="detailView"
      :order-id="detailId"
      embedded
    />
    <template #footer>
      <button
        type="button"
        @click="closeDetail"
      >
        关闭
      </button>
    </template>
  </AccessDialog>
  <PurchaseOrderDetailView
    v-if="actionQueue[0]"
    :key="actionQueue[0]"
    :order-id="actionQueue[0]"
    :action="listAction"
    embedded
    @completed="actionCompleted"
    @close="closeAction"
  />
  <PurchaseLogisticsDialog
    v-if="logisticsOrder"
    :order="logisticsOrder"
    :writable="maintainable && logisticsOrder.status !== 'CANCELLED'"
    @close="logisticsOrder = undefined"
    @saved="logisticsSaved"
  />
</template>

<style scoped>
.purchase-list-actions{display:flex;align-items:center;gap:8px;padding:8px 16px;flex-wrap:wrap}
.purchase-list-actions .purchase-batch-action{min-height:32px;padding:5px 14px;border-radius:4px;border:1px solid #b7dbc5;background:#edf7f1;color:#21834a;font-size:13px;cursor:pointer}
.purchase-list-actions .purchase-batch-action:disabled{background:#f1f2f4;border-color:#e0e3e8;color:#98a2b3;cursor:not-allowed;opacity:1}

.purchase-name-link{color:#2864c5!important;border:0;background:transparent!important;padding:0;text-align:left;cursor:pointer;}
.purchase-delivery-date { color: #dc2626; }
.manual-image-cell { min-width:110px; max-width:130px; white-space:normal; }.manual-image-cell > button { display:block; margin:5px 0; }.manual-purchase-lines { min-width:850px; }.manual-purchase-lines td:nth-child(2) { min-width:230px; }.manual-purchase-lines td:nth-child(2) input { display:block; width:100%; max-width:none; }.manual-purchase-lines td:nth-child(4) input { width:70px; }.purchase-edit-lines td input + input { margin-top:6px; }
.purchase-header-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.purchase-list-measure{height:84px;display:flex;align-items:center;margin-bottom:8px;white-space:nowrap}.purchase-list-product{height:84px;display:flex;align-items:center;gap:10px;min-width:240px;max-width:420px;white-space:normal;margin-bottom:8px}.purchase-list-product>div{min-width:0;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.purchase-list-product>:first-child{flex:none}
</style>
