<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import AccessDialog from './AccessDialog.vue'
import CustomsWarehouseManager from './CustomsWarehouseManager.vue'
import DatePicker from './DatePicker.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { customsWarehousesApi, type CustomsWarehouse, customsShippersApi, type CustomsShipper } from '@/api/customsDocuments'
import { customsDocumentsApi, type CustomsDocument, type CustomsInput } from '@/api/customsDocuments'
import { documentElementFields, profileFields, profileElements, serializeElements, customsApi, type CustomsRow } from '@/api/customs'
import { brokerFields, shipmentDefaults, type DocumentField, customsSheetFields, fixedReceiver, contractNumber, cartonTotal, itemFields, blankItem, missingFields } from '@/ui/customsDocumentFields'
import { confirmAction } from '@/state/uiConfirmation'
const props=defineProps<{document?:CustomsDocument|undefined;copy?:boolean;writable:boolean;exportable:boolean}>()
const emit=defineEmits<{close:[];saved:[CustomsDocument]}>()
const source=props.document,localDate=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const form=ref<CustomsInput>({documentNo:props.copy?'':source?.documentNo??'',status:props.copy?'DRAFT':source?.status==='READY'?'READY':'DRAFT',fields:source?{...source.fields}:{date:localDate()},items:source?source.items.map(r=>({...r})):[],version:props.copy?undefined:source?.version})
const autoFreight=!source||!!props.copy
const savedId=ref(props.copy?undefined:source?.id),initial=ref(JSON.stringify(form.value)),tab=ref('declaration'),busy=ref(false),error=ref(''),notice=ref(''),dialog=ref<InstanceType<typeof AccessDialog>>()
const readOnly=computed(()=>!props.writable||(!props.copy&&(source?.status==='VOID'||source?.fileStatus==='COMPLETED'))),dirty=computed(()=>JSON.stringify(form.value)!==initial.value),missing=computed(()=>missingFields(form.value.fields,form.value.items))
const tabs=[['declaration','报关单'],['elements','申报要素'],['contract','合同'],['invoice','发票'],['packing','装箱单']]
const sheetFields=computed(()=>(customsSheetFields[tab.value]??[]).filter(f=>tab.value!=='declaration'||!['fbaNo','warehouse','relatedParty','priceInfluence','royalty','selfTax','declarant','declarationCompany','sourcePlace','exemption'].includes(f.key)))
const totals=computed(()=>cartonTotal(form.value.items))
const sheetTitle=computed(()=>({declaration:'中华人民共和国海关出口货物报关单',elements:'申报要素',contract:'合同 / CONTRACT',invoice:'发票 / INVOICE',packing:'装箱单 / PACKING LIST'} as Record<string,string>)[tab.value])
const shippers=ref<CustomsShipper[]>([]),shipperId=ref(source?.fields.shipperId??''),shipperOpen=ref(false),shipperBusy=ref(false),shipperError=ref(''),shipperLoadError=ref('')
const shipperDraft=ref({name:'',prefix:'',address:'',phone:'',fax:''})
const shipperEditing=ref(''),shipperInitial=ref('')
const showChecks=ref(false)
async function loadShippers(){shipperLoadError.value='';try{shippers.value=await customsShippersApi.list();if(!shipperId.value&&form.value.fields.seller)shipperId.value=shippers.value.find(s=>s.name===form.value.fields.seller)?.id??''}catch(e){shipperLoadError.value=message(e)}}
function chooseShipper(){const s=shippers.value.find(s=>s.id===shipperId.value);if(!s)return;Object.assign(form.value.fields,{shipperId:s.id,seller:s.name,sellerPrefix:s.prefix,sellerAddress:s.address,sellerPhone:s.phone,sellerFax:s.fax});}
function editShipper(){const s=shippers.value.find(s=>s.id===shipperId.value);if(!s)return;const {id,...draft}=s;shipperEditing.value=id;shipperDraft.value={...draft};shipperInitial.value=JSON.stringify(shipperDraft.value);shipperError.value='';shipperOpen.value=true}
function newShipper(){shipperEditing.value='';shipperDraft.value={name:'',prefix:'',address:'',phone:'',fax:''};shipperInitial.value=JSON.stringify(shipperDraft.value);shipperError.value='';shipperOpen.value=true}
async function addShipper(){shipperBusy.value=true;shipperError.value='';try{const s=shipperEditing.value?await customsShippersApi.update(shipperEditing.value,shipperDraft.value):await customsShippersApi.add(shipperDraft.value);shippers.value=shipperEditing.value?shippers.value.map(row=>row.id===s.id?s:row):[...shippers.value,s];shipperId.value=s.id;chooseShipper();shipperOpen.value=false}catch(e){shipperError.value=message(e)}finally{shipperBusy.value=false}}
const warehouseManager=ref(false),warehouseLoading=ref(false),warehouseMessage=ref(''),warehouseError=ref(''),warehouseMatch=ref<CustomsWarehouse>()
let warehouseSequence=0,lastAutoAddress=''
async function applyWarehouse(row:CustomsWarehouse,seq:number){
 const code=(form.value.fields.warehouse??'').trim().toUpperCase()
 if(seq!==warehouseSequence||code!==row.code||readOnly.value)return
 const current=form.value.fields.buyerAddress??''
 if(current.trim()&&current!==lastAutoAddress&&current!==row.address){
  const confirmed=await confirmAction(`收货人地址已填写。是否替换为仓库 ${row.code} 的地址？\n当前：${current}\n仓库地址：${row.address}`)
  if(seq!==warehouseSequence||form.value.fields.warehouse!==row.code||form.value.fields.buyerAddress!==current)return
  if(!confirmed){warehouseMessage.value='已保留本票地址，请核对是否与当前仓库一致';return}
 }
 form.value.fields.buyerAddress=row.address;lastAutoAddress=row.address;warehouseMessage.value=`已带入 ${row.code} 地址${row.verifiedOn?' · 核对日期 '+row.verifiedOn:' · 尚未记录核对日期'}`
}
async function lookupWarehouse(){
 if(readOnly.value)return
 const code=(form.value.fields.warehouse??'').trim().toUpperCase();form.value.fields.warehouse=code
 const seq=++warehouseSequence;warehouseMatch.value=undefined;warehouseError.value='';warehouseMessage.value=''
 if(!code){warehouseLoading.value=false;return}
 warehouseLoading.value=true
 try{const rows=await customsWarehousesApi.list();if(seq!==warehouseSequence||form.value.fields.warehouse!==code)return;const found=rows.find(r=>r.code===code);warehouseMatch.value=found
 if(found)await applyWarehouse(found,seq)
 else{if(lastAutoAddress&&form.value.fields.buyerAddress===lastAutoAddress){form.value.fields.buyerAddress='';lastAutoAddress=''}warehouseMessage.value='未维护该仓库，请新增仓库地址或手动填写本票地址'}
 }catch(e){if(seq===warehouseSequence)warehouseError.value=message(e)}finally{if(seq===warehouseSequence)warehouseLoading.value=false}
}
function warehouseChanged(){++warehouseSequence;warehouseLoading.value=false;warehouseMessage.value='';warehouseError.value='';warehouseMatch.value=undefined}
async function warehouseSaved(row:CustomsWarehouse){if(row.code!==(form.value.fields.warehouse??'').trim().toUpperCase())return;warehouseMatch.value=row;warehouseManager.value=false;form.value.fields.warehouse=row.code;await applyWarehouse(row,++warehouseSequence)}
const linkedSheet=computed(()=>['contract','invoice','packing'].includes(tab.value))
const linkedField=(key:string)=>linkedSheet.value&&(['seller','sellerAddress','sellerPhone','sellerFax','buyer','buyerAddress','date','contractNo','invoiceNo','currency','signPlace','destination','fbaNo','warehouse','grossWeight','netWeight'].includes(key)||(tab.value==='packing'&&key==='payment'))
const linkedHint=(key:string)=>key==='payment'?'由合同页付款条件自动带入':key==='sellerAddress'||key==='sellerPhone'||key==='sellerFax'?'由境内发货人档案自动带入，请在报关单页编辑发货人':key==='signPlace'?'由报关单境内货源地自动带入':'由报关单资料自动带入，请在报关单页修改'
const automatic=(key:string)=>(key==='freight'&&autoFreight)||linkedField(key)||['seller','buyer','producer','contractNo','orderNo','cartonsTotal','invoiceNo','currency','signPlace','grossWeight','netWeight'].includes(key)
if(!source||props.copy){for(const [key,value] of Object.entries(shipmentDefaults))if(!form.value.fields[key]?.trim())form.value.fields[key]=value;form.value.fields.currency='USD';form.value.fields.invoiceNo=form.value.documentNo;form.value.fields.ruleVersion='2';form.value.fields.buyer=fixedReceiver;form.value.fields.date=localDate();if(props.copy){form.value.fields.fbaNo='';form.value.fields.contractNo='';form.value.fields.orderNo=''}}
// Show the same default used by exports without changing explicitly entered percentages.
if(!form.value.fields.payment?.trim())form.value.fields.payment='T/T'
if(!form.value.fields.tolerance?.trim())form.value.fields.tolerance='5'
// Existing documents keep their stored values until edited; new rules apply on the next save.
watch(()=>[form.value.fields.seller,form.value.fields.sellerPrefix,form.value.fields.date,form.value.fields.fbaNo],(values,previous)=>{if(readOnly.value||values.every((value,index)=>value===previous?.[index]))return;form.value.fields.producer=form.value.fields.seller??'';form.value.fields.buyer=fixedReceiver;form.value.fields.ruleVersion='2';const no=contractNumber(form.value.fields);form.value.fields.contractNo=no;form.value.fields.orderNo=no;if(no)form.value.documentNo=no;},{flush:'sync'})
initial.value=JSON.stringify(form.value)
watch(()=>form.value.documentNo,no=>{if(readOnly.value)return;form.value.fields.invoiceNo=no;form.value.fields.currency='USD'},{immediate:true,flush:'sync'})
const lineFields=computed<DocumentField[]>(()=>['1','2'].includes(item.value?.elementsVersion??'')?[...itemFields.filter(f=>f.key!=='declarationElements'),...documentElementFields(item.value!).map(([key,label])=>({key,label,type:key==='other'?'textarea' as const:undefined,required:false}))]:itemFields)
const itemColumns=computed(()=>({declaration:['sku','hsCode','name','quantity','unit','price','amount','cartons','grossWeight','netWeight'],elements:['sku','hsCode','name','declarationElements'],contract:['name','sku','quantity','unit','price','amount'],invoice:['name','sku','quantity','unit','price','amount'],packing:['cartonNo','name','sku','cartons','quantity','unit','grossWeight','netWeight']} as Record<string,string[]>)[tab.value]??[])
const inlineFields=new Set(['quantity','unit','price','cartons','grossWeight','netWeight','quantity2','unit2','origin','destination','sourcePlace','exemption'])
const requiredLineKeys=new Set(['quantity','unit','price','cartons','grossWeight','netWeight'])
const sharedItemFields=itemFields.filter(f=>['origin','destination','sourcePlace','exemption'].includes(f.key))
const itemDefaults={origin:'中国',destination:'美国',sourcePlace:'厦门市',exemption:'照章征税'}
const unitOptions=['件','个','套']
const sharedDraft=ref<Record<string,string>>(source?{}:{...itemDefaults})
function sharedValue(key:string){const values=[...new Set(form.value.items.map(r=>r[key]||''))];return values.length===1?values[0]??'':sharedDraft.value[key]??''}
function sharedMixed(key:string){return new Set(form.value.items.map(r=>r[key]||'')).size>1}
function updateShared(key:string,value:string){sharedDraft.value[key]=value;for(const row of form.value.items)row[key]=value}
const signingPlace=computed(()=>form.value.items[0]?.sourcePlace||form.value.fields.sourcePlace||sharedDraft.value.sourcePlace||'厦门市')
function weightTotal(key:string){return String(Math.round(form.value.items.reduce((sum,r)=>sum+(Number(r[key])||0),0)*10000)/10000)}
function syncWeights(){form.value.fields.grossWeight=weightTotal('grossWeight');form.value.fields.netWeight=weightTotal('netWeight');if(autoFreight)form.value.fields.freight=`USD/${form.value.fields.netWeight}`}
if(autoFreight)syncWeights()
watch(()=>form.value.items.map(r=>[r.grossWeight,r.netWeight]),()=>{if(!readOnly.value)syncWeights()},{deep:true,flush:'sync'})
const freightAmount=computed({get:()=>{const raw=(form.value.fields.freight??'').trim();return /^(?:USD\s*\/\s*)?\d+(?:\.\d+)?$/i.test(raw)?raw.replace(/^USD\s*\/\s*/i,''):''},set:(value:string)=>{form.value.fields.freight=value?`USD/${value}`:''}})
function pageValidation(){
 if(!form.value.fields.seller?.trim())return '请选择境内发货人。'
 if(!freightAmount.value||!Number.isFinite(Number(freightAmount.value))||Number(freightAmount.value)<0)return '请填写运费金额，币制固定为 USD，金额须为非负数字。'
 for(const [index,row] of form.value.items.entries())for(const key of requiredLineKeys){const value=row[key]?.trim();if(!value||(key!=='unit'&&(!Number.isFinite(Number(value))||Number(value)<=0))||(key==='cartons'&&!Number.isInteger(Number(value))))return `商品 ${index+1}：请填写${columnLabel(key)}${key==='unit'?'':key==='cartons'?'（正整数）':'（大于 0）'}。`}
 for(const [index,row] of form.value.items.entries())if(Number(row.netWeight)>Number(row.grossWeight))return `商品 ${index+1}：总净重不能大于总毛重。`
 return ''
}
const numericFields=new Set(itemFields.filter(f=>f.type==='number').map(f=>f.key))
const columnLabel=(key:string)=>key==='amount'?'总金额':itemFields.find(f=>f.key===key)?.label??key
const money=(n:number)=>Number.isFinite(n)?n.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}):'—'
const total=computed(()=>form.value.items.reduce((sum,r)=>sum+Math.round(Number(r.quantity||0)*Number(r.price||0)*100)/100,0))
const item=ref<Record<string,string>>(),itemIndex=ref(-1),itemInitial=ref(''),itemError=ref(''),itemDialog=ref<InstanceType<typeof AccessDialog>>()
const coreItemKeys=new Set(['sku','name','nameEn','hsCode','quantity','unit','price','cartons','grossWeight','netWeight'])
const extraItemKeys=new Set(['origin','destination','sourcePlace','exemption','measurementUnit','quantity2','unit2','quantity3','unit3'])
const lineGroups=computed(()=>[
 {title:'商品与装箱信息',collapsed:false,fields:lineFields.value.filter(f=>coreItemKeys.has(f.key))},
 {title:'申报要素',collapsed:true,fields:lineFields.value.filter(f=>f.key!=='cartonNo'&&!coreItemKeys.has(f.key)&&!extraItemKeys.has(f.key))},
 {title:'补充资料（按需填写）',collapsed:true,fields:lineFields.value.filter(f=>extraItemKeys.has(f.key))},
])
const itemDirty=computed(()=>!!item.value && !readOnly.value && JSON.stringify(item.value)!==itemInitial.value)
function editItem(index:number){itemIndex.value=index;item.value=index<0?{...blankItem(),unit:'件',...Object.fromEntries(sharedItemFields.map(f=>[f.key,sharedValue(f.key)||itemDefaults[f.key as keyof typeof itemDefaults]]))}:{...form.value.items[index]};itemInitial.value=JSON.stringify(item.value);itemError.value=''}
function keepItem(){if(!item.value)return;if(!item.value.sku?.trim()||!item.value.name?.trim()){itemError.value='请至少填写 SKU 和中文申报品名，其余资料可先存草稿。';return}if(['1','2'].includes(item.value.elementsVersion??''))item.value.declarationElements=serializeElements(item.value,documentElementFields(item.value));if(itemIndex.value<0)form.value.items.push({...item.value});else form.value.items[itemIndex.value]={...item.value};item.value=undefined}
async function removeItem(index:number){if(await confirmAction('从本票单据移除这项商品？产品资料不会删除。'))form.value.items.splice(index,1)}
const picker=ref(false),query=ref(''),pickerPage=ref(1),pickerSize=ref(10),pickerRows=ref<CustomsRow[]>([]),pickerTotal=ref(0),pickerLoading=ref(false),pickerError=ref(''),selected=ref<CustomsRow[]>([])
let request=0
async function loadPicker(){const seq=++request;pickerLoading.value=true;pickerError.value='';try{const r=await customsApi.list(query.value,'',pickerPage.value-1,pickerSize.value);if(seq===request){pickerRows.value=r.items;pickerTotal.value=r.totalCount}}catch(e){if(seq===request)pickerError.value=message(e)}finally{if(seq===request)pickerLoading.value=false}}
function openPicker(){picker.value=true;selected.value=[];query.value='';pickerPage.value=1;void loadPicker()}
function select(row:CustomsRow){selected.value=selected.value.some(r=>r.variantId===row.variantId)?selected.value.filter(r=>r.variantId!==row.variantId):[...selected.value,row]}
function addSelected(){for(const row of selected.value){const c=row.customs;form.value.items.push({...blankItem(),variantId:row.variantId,sku:row.skuCode,name:c?.nameZh??'',nameEn:c?.nameEn??'',hsCode:c?.chinaCode??'',...itemDefaults,unit:'件',...(c?{...profileElements(c),elementsVersion:c.elementFields?'2':'1',elementFields:JSON.stringify(c.elementFields??[]),schemaCode:c.schemaCode??'',measurementUnit:c.measurementUnit??'',declarationElements:serializeElements(profileElements(c),profileFields(c))}:{} ),...Object.fromEntries(sharedItemFields.filter(f=>f.key in sharedDraft.value).map(f=>[f.key,sharedDraft.value[f.key]]))})}picker.value=false}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
async function save(){if(busy.value||warehouseLoading.value||readOnly.value)return;if(!form.value.fields.fbaNo?.trim()||!form.value.fields.warehouse?.trim()){tab.value='declaration';error.value='请填写 FBA 编号和 FBA 仓库代码后保存。';return}const validation=pageValidation();if(validation){tab.value='declaration';error.value=validation;return}syncWeights();form.value.fields.signPlace=signingPlace.value;form.value.items.forEach((r,i)=>{r.cartonNo=String(i+1)});form.value.fields.freight=`USD/${freightAmount.value}`;busy.value=true;error.value='';try{const d=await customsDocumentsApi.save(form.value,savedId.value);savedId.value=d.id;form.value={documentNo:d.documentNo,status:d.status==='READY'?'READY':'DRAFT',fields:{...d.fields},items:d.items.map(r=>({...r})),version:d.version};initial.value=JSON.stringify(form.value);notice.value='单据已保存';emit('saved',d)}catch(e){error.value=message(e)}finally{busy.value=false}}
async function exportFile(){if(!savedId.value||busy.value||dirty.value)return;busy.value=true;error.value='';try{await customsDocumentsApi.export(savedId.value,form.value.documentNo);notice.value='五张报关单证已导出'}catch(e){error.value=message(e);showChecks.value=true}finally{busy.value=false}}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value||itemDirty.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{window.addEventListener('beforeunload',beforeUnload);void loadShippers()});onBeforeUnmount(()=>{++warehouseSequence;++request;window.removeEventListener('beforeunload',beforeUnload)})
onBeforeRouteLeave(()=>{if(busy.value)return false;if(itemDirty.value){itemDialog.value?.requestClose();return false}if(dirty.value){dialog.value?.requestClose();return false}return true})
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <AccessDialog
    ref="dialog"
    :title="readOnly?'报关单据详情':savedId?'编辑报关单据':'新建报关单据'"
    wide
    dialog-class="customs-document-editor"
    :dirty="dirty"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="customs-document-summary">
      <span v-if="!copy && source?.fileStatus==='COMPLETED'" class="muted">已完成 · 仅可查看</span><strong>{{ form.documentNo || '新的一票报关资料' }}</strong><span>{{ form.items.length }} 项商品</span><span>{{ form.fields.currency || '币制待填写' }} {{ money(total) }}</span>
    </div>
    <div class="ui-section-tabs">
      <button
        v-for="[key,label] in tabs"
        :key="key"
        type="button"
        :class="{active:tab===key}"
        :aria-pressed="tab===key"
        @click="tab=key!"
      >
        {{ label }}
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p><p
      v-if="notice && !dirty"
      role="status"
      class="customs-save-notice"
    >
      {{ notice }}
    </p>
    <div class="customs-paper">
      <header class="customs-paper-heading"><h2>{{ sheetTitle }}</h2><span>{{ linkedSheet?'标注“自动”的字段仅供查看；共享资料请在报关单页修改。':'五张单证共享资料，修改后同步更新' }}</span></header>
      <fieldset class="customs-fields" :disabled="busy || readOnly">
        <div v-if="tab==='declaration'" class="customs-paper-meta">
          <label class="field-fbaNo">FBA 编号<span class="customs-required" aria-label="必填"> *</span><input v-model="form.fields.fbaNo" required aria-required="true" aria-label="FBA 编号" maxlength="100" placeholder="请输入 FBA 编号"></label>
          <label class="field-warehouse">FBA 仓库代码<span class="customs-required" aria-label="必填"> *</span><input v-model="form.fields.warehouse" required aria-required="true" aria-label="FBA 仓库代码" maxlength="100" placeholder="输入编码自动匹配地址" @input="warehouseChanged" @change="lookupWarehouse"></label>
          <label>单证日期<DatePicker v-model="form.fields.date" type="date" :disabled="busy || readOnly" /></label>
          <label class="document-number">单据编号<input v-model="form.documentNo" maxlength="128" placeholder="选择发货人并填写日期、FBA 编号后生成"></label>
        </div>
        <div v-if="tab==='declaration'" class="warehouse-address-block">
          <label>收货人地址（发票 / 合同 / 装箱单共用）<textarea v-model="form.fields.buyerAddress" rows="2" maxlength="4000" placeholder="填写仓库编码后自动带出，也可填写本票地址" /></label>
          <div class="warehouse-address-actions">
            <span v-if="warehouseLoading" role="status">正在匹配仓库地址…</span><span v-else-if="warehouseError" role="alert">{{ warehouseError }}</span><span v-else role="status">{{ warehouseMessage }}</span>
            <button v-if="!readOnly" type="button" class="text-action" :disabled="busy || warehouseLoading || !form.fields.warehouse?.trim()" @click="lookupWarehouse">{{ warehouseError?'重试查询':'匹配仓库地址' }}</button>
            <button v-if="!readOnly" type="button" class="text-action" :disabled="busy || warehouseLoading || !form.fields.warehouse?.trim()" @click="warehouseManager=true">{{ warehouseMatch?'维护仓库地址':'新增 / 维护仓库地址' }}</button>
          </div>
        </div>
        <div :key="tab" class="customs-sheet-grid" :class="{'declaration-grid':tab==='declaration'}">
          <label v-for="f in sheetFields" :key="f.key" :class="{['field-'+f.key]:true,wide:f.type==='textarea', 'party-cell':['seller','buyer','producer'].includes(f.key)}">
            <span>{{ f.label }}<span v-if="['seller','freight'].includes(f.key)" class="customs-required" aria-label="必填"> *</span><em v-if="brokerFields.has(f.key)" class="broker-hint">免填写 · 报关行填写</em><em v-else-if="linkedField(f.key)" :title="linkedHint(f.key)">自动</em><em v-else-if="automatic(f.key)"> {{ ['buyer','currency'].includes(f.key)?'固定':f.key==='seller'?'档案选择':'自动' }}</em></span>
            <textarea v-if="linkedField(f.key) && f.type==='textarea'" :value="form.fields[f.key]" readonly rows="2" :aria-label="f.label" :title="linkedHint(f.key)" /><input v-else-if="linkedField(f.key)" :value="f.key==='signPlace'?signingPlace:['grossWeight','netWeight'].includes(f.key)?weightTotal(f.key):form.fields[f.key]" readonly :aria-label="f.label" :title="linkedHint(f.key)"><div v-else-if="f.key==='seller'" class="customs-shipper-control"><select v-model="shipperId" v-single-select aria-label="境内发货人" required aria-required="true" @change="chooseShipper"><option value="">{{ form.fields.seller || '请选择发货人' }}</option><option v-for="s in shippers" :key="s.id" :value="s.id">{{ s.name }}</option></select><button v-if="!readOnly" type="button" @click="newShipper">＋ 新增</button><button v-if="!readOnly && shipperId" type="button" class="text-action" @click="editShipper">编辑</button></div>
            <div v-else-if="f.key==='freight'" class="customs-freight-input"><span>USD/</span><input :value="freightAmount" type="number" min="0" step="0.01" required aria-required="true" aria-label="运费金额" :readonly="autoFreight" :title="autoFreight?'按净重合计自动生成':undefined" :placeholder="autoFreight?'自动取净重合计':'请输入金额'" @input="!autoFreight && (freightAmount=($event.target as HTMLInputElement).value)"><small v-if="form.fields.freight && !freightAmount">原值：{{ form.fields.freight }}，请核对后填写 USD 金额</small></div>
            <input v-else-if="brokerFields.has(f.key)" :value="form.fields[f.key]" disabled placeholder="免填写" :aria-label="f.label">
            <select v-else-if="f.key==='transport'" v-model="form.fields.transport" v-single-select aria-label="运输方式"><option v-if="form.fields.transport && !['水路运输','空运'].includes(form.fields.transport)" :value="form.fields.transport">{{ form.fields.transport }}（原记录）</option><option value="水路运输">水路运输</option><option value="空运">空运</option></select>
            <DatePicker v-else-if="f.type==='date'" v-model="form.fields[f.key]" type="date" :disabled="busy || readOnly" />
            <textarea v-else-if="f.type==='textarea'" v-model="form.fields[f.key]" :class="{'customs-documents-red':f.key==='documents'}" :rows="f.key==='documents'?3:2" maxlength="4000" />
            <input v-else :value="f.key==='cartonsTotal'?totals:f.key==='signPlace'?signingPlace:['grossWeight','netWeight'].includes(f.key)?weightTotal(f.key):form.fields[f.key]" :readonly="automatic(f.key)" :type="f.type==='number'?'number':'text'" :min="f.type==='number'?0:undefined" step="0.0001" maxlength="4000" :placeholder="f.key==='contractNo'||f.key==='orderNo'?'选择发货人并填写日期、FBA 编号后生成':''" @input="form.fields[f.key]=($event.target as HTMLInputElement).value; if(f.key==='warehouse') warehouseChanged()" @change="f.key==='warehouse' && lookupWarehouse()">
          </label>
          <label v-for="f in tab==='declaration'?sharedItemFields:[]" :key="'shared-'+f.key" :class="'field-item-'+f.key"><span>{{ f.label }}</span><input :value="sharedValue(f.key)" :aria-label="f.label" :placeholder="sharedMixed(f.key)?'各 SKU 不同，可在更多资料查看':f.key==='destination'?'留空沿用运抵国':'填写后应用到本票所有 SKU'" @input="updateShared(f.key,($event.target as HTMLInputElement).value)"><small v-if="sharedMixed(f.key)">当前各 SKU 不同；填写后统一应用到所有 SKU</small></label>
        </div>
      </fieldset>
      <p v-if="shipperLoadError" class="data-state--error">{{ shipperLoadError }} <button type="button" @click="loadShippers">重试加载发货人</button></p>
      <div class="customs-item-toolbar"><button v-if="!readOnly && tab==='declaration'" type="button" class="customs-select-sku" :disabled="busy || form.items.length>=100" @click="openPicker">选择产品 SKU</button><button v-if="!readOnly && tab==='declaration'" type="button" class="customs-add-item" :disabled="busy || form.items.length>=100" @click="editItem(-1)">手动添加商品</button><span>共 {{ form.items.length }} 项 · 件数 {{ totals }}；同一商品在五张单证中同步。</span></div>
      <div v-if="tab==='elements' && form.items.length" class="customs-element-sheets"><section v-for="(r,i) in form.items" :key="i"><header><strong>{{ i+1 }}. {{ r.sku }} · {{ r.name }}</strong><button type="button" class="text-action" :disabled="busy" @click="editItem(i)">{{ readOnly?'查看':'编辑本票要素' }}</button></header><table class="customs-element-table"><tbody><tr><th>HS</th><td>{{ r.hsCode || '—' }}</td></tr><tr><th>品名</th><td>{{ r.name || '—' }}</td></tr><template v-if="['1','2'].includes(r.elementsVersion??'')"><tr v-for="[key,label] in documentElementFields(r)" :key="key"><th>{{ label }}</th><td>{{ r[key] || '—' }}</td></tr></template><tr v-else><th>申报要素</th><td>{{ r.declarationElements || '—' }}</td></tr><tr><th>计量单位</th><td>{{ r.measurementUnit || '—' }}</td></tr></tbody></table></section></div>
      <TableViewport v-else>
        <table :key="tab" class="data-table customs-items-table">
          <thead><tr><th>项号<em v-if="linkedSheet" class="customs-auto-hint">自动</em></th><th v-for="key in itemColumns" :key="key">{{ columnLabel(key) }}<em v-if="linkedSheet" class="customs-auto-hint">自动</em><span v-if="!linkedSheet && requiredLineKeys.has(key)" class="customs-required" aria-label="必填"> *</span></th><th v-if="!linkedSheet">操作</th></tr></thead><tbody>
            <tr v-for="(r,i) in form.items" :key="i"><td>{{ i+1 }}</td><td v-for="key in itemColumns" :key="key" :class="{'customs-element-content':key==='declarationElements'}"><select v-if="!linkedSheet && !readOnly && key==='unit'" v-model="r.unit" v-single-select class="customs-inline-unit" :disabled="busy" required aria-required="true" :aria-label="`商品 ${i+1} 成交单位`"><option v-if="r.unit && !unitOptions.includes(r.unit)" :value="r.unit">{{ r.unit }}（原记录）</option><option v-if="!r.unit" value="" disabled>请选择</option><option v-for="unit in unitOptions" :key="unit" :value="unit">{{ unit }}</option></select><input v-else-if="!linkedSheet && !readOnly && inlineFields.has(key)" class="customs-inline-input" :required="requiredLineKeys.has(key)" :aria-required="requiredLineKeys.has(key)||undefined" :value="r[key]" :disabled="busy" :aria-label="`商品 ${i+1} ${columnLabel(key)}`" :type="numericFields.has(key)?'number':'text'" :min="numericFields.has(key)?0:undefined" :step="key==='cartons'?1:'0.0001'" :maxlength="numericFields.has(key)?undefined:4000" :placeholder="['destination','sourcePlace','exemption'].includes(key)?'沿用本票默认':'请输入'" @input="r[key]=($event.target as HTMLInputElement).value"><span v-else>{{ key==='cartonNo'?i+1:key==='amount'?money(Number(r.quantity||0)*Number(r.price||0)):r[key]||'—' }}</span></td><td v-if="!linkedSheet"><button type="button" class="text-action" :disabled="busy" @click="editItem(i)">{{ readOnly?'查看':'更多资料' }}</button><button v-if="!readOnly" type="button" class="text-action danger" :disabled="busy" @click="removeItem(i)">移除</button></td></tr>
            <tr v-if="!form.items.length"><td :colspan="itemColumns.length+(linkedSheet?1:2)" class="data-state">{{ tab==='declaration'?'选择产品 SKU 或手动添加商品，数量、单价及装箱数据可在列表中填写。':'请在“报关单”页签添加商品，五张单证共享同一份明细。' }}</td></tr>
          </tbody>
        </table>
      </TableViewport>
      <div class="customs-paper-total"><span>件数：{{ totals }}</span><span>毛重：{{ weightTotal('grossWeight') }} kg</span><span>净重：{{ weightTotal('netWeight') }} kg</span><strong>合计：{{ form.fields.currency }} {{ money(total) }}</strong></div>
    </div>
    <div class="customs-checkbar"><button type="button" @click="showChecks=!showChecks">{{ showChecks?'收起':'查看' }}导出检查（{{ missing.length }}）</button><label class="customs-reviewed"><input v-model="form.status" type="checkbox" true-value="READY" false-value="DRAFT" :disabled="busy || readOnly">已人工核对本票资料</label></div>
    <ul v-if="showChecks && missing.length" class="customs-missing"><li v-for="m in missing" :key="m">{{ m }}</li></ul>
    <template #footer>
      <span class="customs-footer-note">{{ dirty?'有未保存修改':savedId?'已保存':'可先存草稿，随后补齐资料' }}</span><button
        type="button"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        关闭
      </button><button
        v-if="exportable && (copy || source?.status!=='VOID')"
        type="button"
        :disabled="busy || dirty || !savedId || !!missing.length"
        @click="exportFile"
      >
        导出 Excel
      </button><button
        v-if="!readOnly"
        type="button"
        class="ui-button-primary"
        :disabled="busy || warehouseLoading || (!dirty && !!savedId)"
        @click="save"
      >
        {{ busy?'处理中…':form.status==='READY'?'保存已核对资料':'保存草稿' }}
      </button>
    </template>
  </AccessDialog>
  <CustomsWarehouseManager v-if="warehouseManager" :writable="!readOnly" :initial-code="form.fields.warehouse ?? ''" @saved="warehouseSaved" @close="warehouseManager=false" />
  <AccessDialog v-if="shipperOpen" :title="shipperEditing?'编辑境内发货人':'新增境内发货人'" :busy="shipperBusy" :dirty="JSON.stringify(shipperDraft)!==shipperInitial" @close="shipperOpen=false">
    <form id="customs-shipper-form" class="access-form" @submit.prevent="addShipper"><p v-if="shipperError" role="alert" class="access-danger">{{ shipperError }}</p><label>公司名称<input v-model.trim="shipperDraft.name" required maxlength="300"></label><label>公司简称<input v-model.trim="shipperDraft.prefix" required pattern="[A-Za-z]{1,20}" maxlength="20" placeholder="例如 XMH，用于生成合同协议号"></label><label>公司地址<input v-model.trim="shipperDraft.address" maxlength="1000"></label><label>联系电话<input v-model.trim="shipperDraft.phone" maxlength="100"></label><label>传真（选填）<input v-model.trim="shipperDraft.fax" maxlength="100"></label></form><template #footer><button form="customs-shipper-form" type="submit" class="ui-button-primary" :disabled="shipperBusy">{{ shipperBusy?'保存中…':shipperEditing?'保存修改':'保存并选择' }}</button></template>
  </AccessDialog>
  <AccessDialog
    v-if="item"
    ref="itemDialog"
    :title="`商品 ${itemIndex<0?form.items.length+1:itemIndex+1} · 申报与装箱资料`"
    wide
    dialog-class="customs-line-editor"
    :dirty="!readOnly && JSON.stringify(item)!==itemInitial"
    @close="item=undefined"
  >
    <p
      v-if="itemError"
      role="alert"
      class="access-danger"
    >
      {{ itemError }}
    </p><fieldset
      class="customs-fields"
      :disabled="readOnly"
    >
      <p class="muted">先填写商品与装箱信息；原产国、目的国、货源地和征免沿用本票设置，可在补充资料中单独调整。</p>
      <component :is="group.collapsed?'details':'section'" v-for="group in lineGroups" :key="group.title" class="customs-line-group">
        <summary v-if="group.collapsed">{{ group.title }}</summary><h3 v-else>{{ group.title }}</h3>
        <div class="customs-form-grid">
          <label
            v-for="f in group.fields"
            :key="f.key"
            :class="{wide:f.type==='textarea'}"
          >{{ f.label }}<span
            v-if="f.required"
            class="required"
          > *</span><textarea
            v-if="f.type==='textarea'"
            :value="item[f.key]"
            :maxlength="f.key==='other'?8000:16000"
            rows="5"
            @input="item[f.key]=($event.target as HTMLInputElement).value"
          /><select v-else-if="f.key==='unit'" v-model="item.unit" v-single-select required aria-label="成交单位"><option v-if="item.unit && !unitOptions.includes(item.unit)" :value="item.unit">{{ item.unit }}（原记录）</option><option v-if="!item.unit" value="" disabled>请选择</option><option v-for="unit in unitOptions" :key="unit" :value="unit">{{ unit }}</option></select><input
            v-else
            :value="item[f.key]"
            :type="f.type==='number'?'number':'text'"
            :min="f.type==='number'?0:undefined"
            :step="f.key==='cartons'?1:'0.0001'"
            maxlength="4000"
            @input="item[f.key]=($event.target as HTMLInputElement).value"
          ><small
            v-if="f.hint"
            class="muted"
          >{{ f.hint }}</small></label>
        </div>
      </component>
    </fieldset>
    <template #footer>
      <button
        v-if="readOnly"
        type="button"
        @click="item=undefined"
      >
        关闭
      </button><button
        v-else
        type="button"
        class="ui-button-primary"
        @click="keepItem"
      >
        保存到本票
      </button>
    </template>
  </AccessDialog>
  <AccessDialog
    v-if="picker"
    title="选择产品 SKU"
    wide
    dialog-class="customs-sku-picker"
    @close="picker=false"
  >
    <form
      class="customs-picker-filter"
      @submit.prevent="pickerPage=1;loadPicker()"
    >
      <input
        v-model="query"
        placeholder="搜索 SKU、产品名称或商品编号"
        maxlength="128"
      ><button
        type="submit"
        :disabled="pickerLoading"
      >
        搜索
      </button><span>已选 {{ selected.length }} 项</span>
    </form>
    <p
      v-if="pickerError"
      role="alert"
      class="access-danger"
    >
      {{ pickerError }}
    </p><TableViewport>
      <table class="data-table">
        <thead><tr><th>选择</th><th>SKU / 产品</th><th>申报品名</th><th>商品编号</th><th>资料状态</th></tr></thead><tbody>
          <tr
            v-for="r in pickerRows"
            :key="r.variantId"
          >
            <td>
              <input
                type="checkbox"
                :aria-label="`选择 ${r.skuCode}`"
                :checked="selected.some(x=>x.variantId===r.variantId)"
                @change="select(r)"
              >
            </td><td>{{ r.skuCode }}<small>{{ r.skuName }}</small></td><td>{{ r.customs?.nameZh || '待维护' }}</td><td>{{ r.customs?.chinaCode || '—' }}</td><td>{{ r.customs?.state==='REVIEWED'?'已核对':'待核对' }}</td>
          </tr><tr v-if="!pickerRows.length">
            <td colspan="5">
              {{ pickerLoading?'加载中…':'无匹配产品' }}
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport><ListPagination
      :page="pickerPage"
      :page-size="pickerSize"
      :total="pickerTotal"
      :disabled="pickerLoading"
      @update:page="if(pickerPage!==$event){pickerPage=$event;loadPicker()}"
      @update:page-size="pickerSize=$event;pickerPage=1;loadPicker()"
    />
    <template #footer>
      <button
        type="button"
        @click="picker=false"
      >
        取消
      </button><button
        type="button"
        class="ui-button-primary"
        :disabled="!selected.length || form.items.length+selected.length>100 || pickerLoading"
        @click="addSelected"
      >
        添加所选（{{ selected.length }}）
      </button>
    </template>
  </AccessDialog>
</template>
<style>
.customs-sheet-grid .customs-required{color:#c62828;font-weight:600}
#app .customs-document-editor{width:min(1180px,calc(100vw - 24px));height:min(880px,calc(100dvh - 24px))}.customs-document-editor .access-dialog-body{flex:1;min-height:0}.customs-document-summary{display:flex;gap:18px;align-items:center;margin-bottom:14px;font-size:13px}.customs-document-summary strong{font-size:16px}.customs-document-summary span{color:var(--muted)}.customs-fields{border:0;padding:0;margin:0;min-width:0}.customs-form-section{padding:14px 0;border-bottom:1px solid var(--line)}.customs-form-section h3{font-size:14px;margin:0 0 12px}.customs-form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.customs-form-grid label,.document-number{display:block;font-size:13px}.customs-form-grid .wide{grid-column:1/-1}#app .customs-form-grid input,#app .customs-form-grid textarea,#app .document-number input{display:block;margin-top:6px;width:100%;height:36px;padding:7px 10px;border:1px solid var(--line);border-radius:5px;font-size:13px;box-sizing:border-box}#app .customs-form-grid textarea{height:auto;resize:vertical}.customs-form-grid .date-picker{margin-top:6px}.customs-fields .required{color:#b42318}.document-number{max-width:420px;padding-top:12px}.customs-footer-note{margin-right:auto;font-size:12px;color:var(--muted)}.customs-item-toolbar{display:flex;gap:8px;align-items:center;margin:12px 0;flex-wrap:wrap}.customs-item-toolbar span{font-size:12px;color:var(--muted)}.customs-items-table{min-width:960px}.customs-items-table small,.customs-sku-picker small{display:block;color:var(--muted);margin-top:4px}.customs-items-table td{font-size:13px;padding:10px}.customs-export-summary{padding:14px;border:1px solid var(--line);background:#f7f9fc;border-radius:6px;margin:14px 0;font-size:13px}.customs-export-summary h3{margin-top:0}.customs-missing{columns:2;font-size:13px;color:#b54708;line-height:1.9}.customs-reviewed{display:flex;gap:8px;align-items:center;font-size:13px}.customs-save-notice{font-size:13px;color:#21834a}.customs-picker-filter{display:flex;gap:10px;align-items:center;margin-bottom:12px}.customs-picker-filter input{flex:1;min-width:0;height:34px;padding:6px 10px}.customs-sku-picker .table-viewport{max-height:50vh}.customs-line-editor .access-dialog-body{max-height:70vh}.customs-sku-picker td{padding:10px}.customs-document-editor .text-action{border:0;background:none;color:#2864c5}.customs-document-editor .danger{color:#b42318}@media(max-width:760px){.customs-form-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.customs-document-summary{flex-wrap:wrap;gap:8px}.customs-footer-note{flex-basis:100%}}@media(max-width:480px){.customs-form-grid{grid-template-columns:1fr}.customs-missing{columns:1}}
.customs-paper{border:1px solid #cbd2dc;background:white;margin-top:12px;padding:16px}.customs-paper-heading{text-align:center;padding:4px 0 16px}.customs-paper-heading h2{font-size:20px;letter-spacing:2px;margin:0 0 8px}.customs-paper-heading span{font-size:12px;color:var(--muted)}.customs-paper-meta{display:flex;gap:12px;align-items:end;margin-bottom:12px;flex-wrap:wrap}.customs-paper-meta label{font-size:12px;flex:1;min-width:150px}.customs-paper-meta .document-number{padding:0}.customs-paper-meta input{width:100%;height:32px;border:1px solid var(--line);padding:4px 8px}.customs-sheet-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));border-top:1px solid #cbd2dc;border-left:1px solid #cbd2dc}.customs-sheet-grid>label{min-width:0;grid-column:span 2;border-right:1px solid #cbd2dc;border-bottom:1px solid #cbd2dc;padding:8px;display:flex;flex-direction:column;gap:6px;font-size:12px}.customs-sheet-grid .wide{grid-column:1/-1}.customs-sheet-grid input,.customs-sheet-grid textarea{width:100%;min-width:0;border:1px solid transparent;border-radius:3px;background:#f7f9fc;padding:6px 8px;font-size:13px;box-sizing:border-box;min-height:34px}.customs-sheet-grid input:focus,.customs-sheet-grid textarea:focus{border-color:#2864c5;outline:0}.customs-sheet-grid input[readonly]{background:transparent;color:#344054}.customs-sheet-grid em{font-size:10px;font-style:normal;color:#667085;margin-left:6px}.customs-shipper-control{display:flex;gap:6px;align-items:center}.customs-shipper-control .single-select-trigger{flex:1;min-width:0}.customs-shipper-control button{white-space:nowrap}.customs-paper-total{display:flex;gap:20px;justify-content:flex-end;padding:14px 0 0;flex-wrap:wrap;font-size:13px}.customs-element-content{white-space:pre-wrap;min-width:320px;max-width:540px}.customs-checkbar{display:flex;gap:18px;align-items:center;margin-top:14px}.customs-checkbar>button{border:0;background:none;color:#2864c5}@media(max-width:760px){.customs-sheet-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.customs-sheet-grid>label{grid-column:span 1}.customs-paper{padding:8px}.customs-paper-heading h2{font-size:16px}.customs-paper-total{justify-content:flex-start;gap:10px}}
.customs-document-editor .ui-section-tabs{position:sticky;top:0;background:#fff;z-index:4}.customs-document-summary{margin-bottom:6px}.customs-paper-heading{padding:0 0 10px}.customs-paper-heading h2{font-size:18px;margin-bottom:4px}.customs-paper-heading span{display:none}.customs-sheet-grid>label{padding:6px;gap:3px}.declaration-grid{grid-template-columns:repeat(12,minmax(0,1fr))}.declaration-grid>label{grid-column:span 2}.declaration-grid .field-seller,.declaration-grid .field-buyer,.declaration-grid .field-producer,.declaration-grid .field-contractNo,.declaration-grid .field-orderNo,.declaration-grid .field-fbaNo,.declaration-grid .field-warehouse{grid-column:span 4}.declaration-grid .field-voyage,.declaration-grid .field-billNo,.declaration-grid .field-taxNature,.declaration-grid .field-license{grid-column:span 3}.declaration-grid .wide{grid-column:1/-1}.declaration-grid .field-packaging{grid-column:span 3}.declaration-grid .field-cartonsTotal{grid-column:span 1}.declaration-grid .field-grossWeight,.declaration-grid .field-netWeight{grid-column:span 2}.declaration-grid .field-declarant,.declaration-grid .field-declarationCompany{grid-column:span 6}.customs-sheet-grid .single-select-trigger{max-width:100%}.customs-sheet-grid .date-picker{min-width:0;width:100%}@media(max-width:900px){.declaration-grid{grid-template-columns:repeat(6,minmax(0,1fr))}.declaration-grid>label{grid-column:span 2}.declaration-grid .field-seller,.declaration-grid .field-buyer,.declaration-grid .field-producer,.declaration-grid .field-contractNo,.declaration-grid .field-orderNo,.declaration-grid .field-fbaNo,.declaration-grid .field-warehouse{grid-column:span 3}.declaration-grid .wide{grid-column:1/-1}}@media(max-width:560px){.declaration-grid{display:flex;flex-direction:column}.customs-checkbar{flex-wrap:wrap}}
.customs-element-sheets{display:grid;gap:16px}.customs-element-sheets header{display:flex;align-items:center;justify-content:space-between;padding:10px 0;font-size:13px}.customs-element-table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:13px}.customs-element-table th,.customs-element-table td{border:1px solid var(--line);padding:8px 12px;text-align:left;white-space:pre-wrap;overflow-wrap:anywhere}.customs-element-table th{width:170px;background:#f7f9fc;font-weight:500}
</style>

<style scoped>
.customs-freight-input{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.customs-freight-input>span{font-size:13px;flex:none}.customs-freight-input input{flex:1;min-width:80px}.customs-freight-input small{flex-basis:100%;color:#b45309}

.customs-sheet-grid .customs-documents-red{color:#c62828;line-height:1.7}.customs-sheet-grid .broker-hint{color:#42805a}.customs-sheet-grid input:disabled{background:#f0f2f5;color:#667085;cursor:not-allowed}
</style>

<style scoped>

.customs-item-toolbar .customs-select-sku,.customs-item-toolbar .customs-add-item{min-height:34px;padding:0 14px;border-radius:5px;font-size:13px;font-weight:500;white-space:nowrap}.customs-item-toolbar .customs-select-sku{background:var(--accent,#2675d8);color:white;border:1px solid var(--accent,#2675d8)}.customs-item-toolbar .customs-select-sku:hover{filter:brightness(.94)}.customs-item-toolbar .customs-add-item{background:white;color:var(--ink);border:1px solid var(--line)}.customs-item-toolbar button:disabled{background:#f0f2f5;color:#98a2b3;border-color:var(--line);cursor:not-allowed}.customs-items-table .customs-inline-input{width:118px;min-width:118px;min-height:36px;padding:6px 8px;border:1px solid #cbd2dc;border-radius:4px;background:white;font-size:13px;box-sizing:border-box}.customs-items-table .customs-inline-input:focus{outline:2px solid var(--accent-soft);border-color:var(--accent)}.customs-items-table .customs-inline-input:disabled{background:#f5f7fa}.customs-paper-meta .customs-required{color:#b42318}.customs-paper-meta input{box-sizing:border-box;border-radius:4px;margin-top:6px}.customs-paper-meta label{min-width:0;flex:1 1 190px}
</style>

<style scoped>

.warehouse-address-block{margin:0 0 12px;border:1px solid var(--line);border-radius:5px;padding:10px;background:#f8fafc}.warehouse-address-block label{display:block;font-size:13px}.warehouse-address-block textarea{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:8px;border:1px solid var(--line);border-radius:4px;font-size:13px}.warehouse-address-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap;font-size:12px;margin-top:6px;color:var(--muted)}
</style>

<style scoped>
.customs-line-group{border:1px solid var(--line);border-radius:6px;margin-bottom:10px;padding:10px 12px}.customs-line-group h3,.customs-line-group summary{font-size:13px;margin:0;font-weight:600}.customs-line-group summary{cursor:pointer;min-height:24px;line-height:24px}.customs-line-group .customs-form-grid{margin-top:10px}.customs-inline-unit{min-width:90px}
</style>
<style>
#app .customs-line-editor .customs-form-grid .single-select-trigger{display:flex;width:100%;height:36px;margin-top:6px;box-sizing:border-box}
</style>

<style scoped>
.customs-paper-meta .field-fbaNo{flex:2 1 300px}@media(max-width:560px){.customs-paper-meta .field-fbaNo{flex-basis:100%}}
</style>

<style scoped>
.customs-auto-hint{font-size:11px;font-style:normal;font-weight:400;color:var(--muted);margin-left:5px}.customs-sheet-grid input[readonly],.customs-sheet-grid textarea[readonly]{background:#f5f7fa;color:#475569;cursor:default}
</style>
