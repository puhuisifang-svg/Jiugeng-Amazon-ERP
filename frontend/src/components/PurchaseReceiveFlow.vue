<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref,reactive} from 'vue'
import {purchaseApi,type PurchaseOrder} from '@/api/purchases'
import {purchaseManagementApi as api,type PurchaseShipment} from '@/api/purchaseManagement'
import {readPurchasePending,writePurchasePending} from '@/ui/purchasePending'
import {validMaterialQuantity} from '@/ui/materialQuantity'
import {ApiError} from '@/api/client'
import {beijingDateTimeInput,beijingInputToIso} from '@/utils/dateTime'
import {session} from '@/state/session'
import {vSingleSelect} from '@/directives/singleSelect'
import AccessDialog from './AccessDialog.vue'
import PurchaseReceiptDialog from './PurchaseReceiptDialog.vue'
import DatePicker from './DatePicker.vue'
const props=defineProps<{id:string}>(),emit=defineEmits<{close:[];completed:[]}>()
type Payload=Parameters<typeof purchaseApi.receive>[1]
const order=ref<PurchaseOrder>(),shipments=ref<PurchaseShipment[]>([]),shipment=ref(''),warnings=ref<string[]>([]),loading=ref(true),loadError=ref(''),error=ref(''),saving=ref(false),quantities=reactive<Record<string,number|''>>({}),remark=ref(''),date=ref(beijingDateTimeInput()),pending=ref<Payload|undefined>(readPurchasePending('receipt',props.id))
const locked=computed(()=>saving.value||!!pending.value),dirty=computed(()=>Object.values(quantities).some(v=>Number(v)>0)||!!remark.value)
const receiptOrder=computed(()=>{if(!order.value)return undefined;const batch=shipments.value.find(v=>v.id===shipment.value);return {...order.value,lines:order.value.lines.map(l=>({...l,remainingQuantity:batch?Math.min(l.remainingQuantity,Math.max(0,(batch.lines.find(v=>v.orderLineId===l.id)?.quantity??0)-(batch.lines.find(v=>v.orderLineId===l.id)?.receivedQuantity??0))):shipments.value.length?0:l.remainingQuantity}))}})
let alive=true
async function load(){loading.value=true;loadError.value='';try{const [o,s,w]=await Promise.all([purchaseApi.order(props.id),api.shipments(props.id),api.checks(props.id)]);if(!alive)return;order.value=o;shipments.value=s;warnings.value=w;if(!pending.value&&!['SUBMITTED','PARTIALLY_RECEIVED'].includes(o.status))throw Error('单据已完成或关闭，请返回列表核对');if(pending.value){shipment.value=pending.value.shipmentId??'';pending.value.lines.forEach(l=>quantities[l.orderLineId]=l.quantity);remark.value=pending.value.remark??'';date.value=beijingDateTimeInput(pending.value.receivedAt)}else if(!s.some(v=>v.id===shipment.value))shipment.value=s.find(v=>v.lines.some(l=>l.quantity>l.receivedQuantity))?.id??''}catch(e){loadError.value=e instanceof Error?e.message:'采购单读取失败'}finally{loading.value=false}}
async function receive(){if(!order.value||saving.value||!session.canAccess(['purchases.receive']))return;error.value='';if(!pending.value){const available=receiptOrder.value!.lines.filter(v=>v.remainingQuantity>0);if(shipments.value.length&&!shipment.value){error.value='请选择收货批次';return}const chosen=available.filter(l=>Number(quantities[l.id])>0);if(!chosen.length){error.value='请至少填写一项本次实收数量';return}if(available.some(l=>quantities[l.id]!==undefined&&quantities[l.id]!==''&&Number(quantities[l.id])!==0&&(!Number.isFinite(Number(quantities[l.id]))||Number(quantities[l.id])<0||!/^\d+(\.\d{1,4})?$/.test(String(quantities[l.id]))||Number(quantities[l.id])>l.remainingQuantity||(order.value!.orderType!=='MANUAL'&&!validMaterialQuantity(quantities[l.id]!,order.value!.orderType==='PRODUCT'?'套':l.unit))))){error.value='请核对数量及单位，不得超过本批待收数量';return}const received=new Date(beijingInputToIso(date.value));if(!Number.isFinite(received.getTime())||received.getTime()>Date.now()+300000){error.value='实际收货时间无效或在未来';return}pending.value={requestId:crypto.randomUUID(),receivedAt:received.toISOString(),remark:remark.value,lines:chosen.map(l=>({orderLineId:l.id,quantity:Number(quantities[l.id])})),...(shipment.value?{shipmentId:shipment.value}:{})};writePurchasePending('receipt',pending.value,props.id)}saving.value=true;try{await purchaseApi.receive(props.id,pending.value);pending.value=undefined;writePurchasePending('receipt',null,props.id);emit('completed')}catch(e){error.value=e instanceof Error?e.message:'收货结果待确认';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;writePurchasePending('receipt',null,props.id);await load()}else error.value='结果待确认，请用原请求重试；刷新页面后仍可恢复。'+error.value}finally{saving.value=false}}
function unload(e:BeforeUnloadEvent){if(dirty.value||locked.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{void load();window.addEventListener('beforeunload',unload)});onBeforeUnmount(()=>{alive=false;window.removeEventListener('beforeunload',unload)})
defineExpose({busy:saving,dirty,pending})
</script>
<template>
  <AccessDialog
    v-if="loading||loadError||!receiptOrder"
    title="采购收货"
    :busy="loading"
    @close="emit('close')"
  >
    <p>{{ loadError||'正在读取采购单…' }}</p><button
      v-if="loadError"
      @click="load"
    >
      重新读取
    </button>
  </AccessDialog>
  <PurchaseReceiptDialog
    v-else
    :order="receiptOrder"
    :quantities="quantities"
    :remark="remark"
    :receipt-error="error"
    :locked="locked"
    :saving="saving"
    :pending="!!pending"
    :dirty="dirty"
    @close="emit('close')"
    @submit="receive"
    @quantity="(id,value)=>quantities[id]=value===''?'':Number(value)"
    @remark="remark=$event"
  >
    <p
      v-for="warning in warnings"
      :key="warning"
      class="purchase-warning"
    >
      {{ warning }}
    </p>
    <label v-if="shipments.length">收货批次<select
      v-model="shipment"
      v-single-select
      :disabled="locked"
      @change="Object.keys(quantities).forEach(k=>delete quantities[k])"
    ><option value="">请选择</option><option
      v-for="(batch,i) in shipments"
      :key="batch.id"
      :value="batch.id"
    >第 {{ i+1 }} 批 · {{ batch.shippedOn }} · {{ batch.trackingNumber||'图片运单' }}</option></select></label>
    <label>实际收货时间<DatePicker
      v-model="date"
      type="datetime-local"
      :disabled="locked"
    /></label>
    <p
      v-if="pending"
      class="purchase-warning"
    >
      原请求已保留，点击重试不会重复入账。
    </p>
  </PurchaseReceiptDialog>
</template>
