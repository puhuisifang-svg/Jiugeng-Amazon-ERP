<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,ref,reactive} from 'vue'
import {purchaseApi,type PurchaseOrder} from '@/api/purchases'
import {purchaseManagementApi as api,type PurchaseShipment} from '@/api/purchaseManagement'
import {readPurchasePending,writePurchasePending} from '@/ui/purchasePending'
import {validMaterialQuantity} from '@/ui/materialQuantity'
import {ApiError} from '@/api/client'
import {beijingDateTimeInput,beijingInputToIso} from '@/utils/dateTime'
import {createRequestId} from '@/utils/requestId'
import {session} from '@/state/session'
import AccessDialog from './AccessDialog.vue'
import PurchaseReceiptDialog from './PurchaseReceiptDialog.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import PurchaseShipmentsDialog from './PurchaseShipmentsDialog.vue'
import TableViewport from './TableViewport.vue'
import SuccessToast from './SuccessToast.vue'
import DatePicker from './DatePicker.vue'
const dialog=ref<InstanceType<typeof AccessDialog>>(),feedbackKey=ref(0)
const props=defineProps<{id:string}>(),emit=defineEmits<{close:[];completed:[message:string];updated:[message:string]}>()
type Payload=Parameters<typeof purchaseApi.receive>[1]
interface Draft {quantities:Record<string,number|''>;remark:string;date:string;initialDate:string}
const order=ref<PurchaseOrder>(),shipments=ref<PurchaseShipment[]>([]),warnings=ref<string[]>([]),loading=ref(true),loadError=ref(''),error=ref(''),saving=ref(false),savingKey=ref(''),manage=ref(false),success=ref(''),pending=ref<Payload|undefined>(readPurchasePending('receipt',props.id))
const drafts=reactive<Record<string,Draft>>({})
function draft(key:string){if(!drafts[key]){const date=beijingDateTimeInput();drafts[key]={quantities:{},remark:'',date,initialDate:date}}return drafts[key]!}
const pendingKey=computed(()=>pending.value?.shipmentId??'')
const locked=computed(()=>saving.value||!!pending.value||loading.value||!!loadError.value)
const dirty=computed(()=>Object.values(drafts).some(d=>Object.values(d.quantities).some(v=>v!==''&&v!==0)||!!d.remark||d.date!==d.initialDate))
const canReceive=computed(()=>session.canAccess(['purchases.receive']))
const canManage=computed(()=>session.canAccess(['purchases.write']))
function receiptOrder(key:string):PurchaseOrder|undefined{if(!order.value)return;const batch=shipments.value.find(s=>s.id===key);if(!batch)return {...order.value,lines:key?[]:order.value.lines};return {...order.value,lines:order.value.lines.flatMap(line=>{const item=batch.lines.find(l=>l.orderLineId===line.id);return item?[{...line,orderedQuantity:item.quantity,receivedQuantity:item.receivedQuantity,remainingQuantity:Math.min(line.remainingQuantity,Math.max(0,item.quantity-item.receivedQuantity))}]:[]})}}
const groups=computed(()=>{const result:{key:string;batch?:PurchaseShipment;order:PurchaseOrder}[]=shipments.value.map(s=>({key:s.id,batch:s,order:receiptOrder(s.id)!}));if(pending.value&&!result.some(g=>g.key===pendingKey.value))result.push({key:pendingKey.value,order:receiptOrder(pendingKey.value)!});return result})
const unallocated=computed(()=>order.value?.lines.filter(l=>l.remainingQuantity-shipments.value.reduce((s,b)=>s+b.lines.filter(x=>x.orderLineId===l.id).reduce((n,x)=>n+x.quantity-x.receivedQuantity,0),0)>0)??[])
let alive=true
async function load(){loading.value=true;loadError.value='';try{const [o,s,w]=await Promise.all([purchaseApi.order(props.id),api.shipments(props.id),api.checks(props.id)]);if(!alive)return;order.value=o;shipments.value=s;warnings.value=w;if(pending.value){const d=draft(pendingKey.value);pending.value.lines.forEach(l=>d.quantities[l.orderLineId]=l.quantity);d.remark=pending.value.remark??'';d.date=beijingDateTimeInput(pending.value.receivedAt)}else if(!['SUBMITTED','PARTIALLY_RECEIVED','COMPLETED'].includes(o.status))throw Error('单据已关闭，请返回列表核对')}catch(e){loadError.value=e instanceof Error?e.message:'采购单读取失败'}finally{loading.value=false}}
async function receive(key=''){
 if(!order.value||saving.value||loading.value||!canReceive.value||!!loadError.value||(pending.value&&pendingKey.value!==key))return
 feedbackKey.value++;error.value='';success.value='';const d=draft(key)
 if(!pending.value){
  const available=receiptOrder(key)!.lines.filter(l=>l.remainingQuantity>0),chosen=available.filter(l=>Number(d.quantities[l.id])>0)
  if(shipments.value.length&&!key){error.value='请在对应运单内确认收货';return}
  if(!chosen.length){error.value='请至少填写一项本次实收数量';return}
  if(available.some(l=>d.quantities[l.id]!==undefined&&d.quantities[l.id]!==''&&Number(d.quantities[l.id])!==0&&(!Number.isFinite(Number(d.quantities[l.id]))||Number(d.quantities[l.id])<0||!/^\d+(\.\d{1,4})?$/.test(String(d.quantities[l.id]))||Number(d.quantities[l.id])>l.remainingQuantity||(order.value!.orderType!=='MANUAL'&&!validMaterialQuantity(d.quantities[l.id]!,order.value!.orderType==='PRODUCT'?'套':l.unit))))){error.value='请核对数量及单位，不得超过本运单待收数量';return}
  const received=new Date(beijingInputToIso(d.date));if(!Number.isFinite(received.getTime())||received.getTime()>Date.now()+300000){error.value='实际收货时间无效或在未来';return}
  pending.value={requestId:createRequestId(),receivedAt:received.toISOString(),remark:d.remark,lines:chosen.map(l=>({orderLineId:l.id,quantity:Number(d.quantities[l.id])})),...(key?{shipmentId:key}:{})};writePurchasePending('receipt',pending.value,props.id)
 }
 saving.value=true;savingKey.value=key
 try{const receipt=await purchaseApi.receive(props.id,pending.value);const itemCount=pending.value.lines.length;pending.value=undefined;writePurchasePending('receipt',null,props.id);delete drafts[key];success.value=receipt.replayed?'收货已确认，未重复入账。':`收货成功，已登记本次 ${itemCount} 项商品。`;emit('updated',success.value);
  if(!shipments.value.length){emit('completed',success.value);return}
  await load();if(!loadError.value&&order.value?.status==='COMPLETED')emit('completed',success.value)
 }catch(e){error.value=e instanceof Error?e.message:'收货结果待确认';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;writePurchasePending('receipt',null,props.id);error.value='收货失败：'+error.value;await load()}else error.value='结果待确认，请重试对应运单的原请求，不会重复入账。'+error.value}finally{saving.value=false;savingKey.value=''}
}
function fill(key:string){if(locked.value)return;receiptOrder(key)?.lines.forEach(l=>{if(l.remainingQuantity>0)draft(key).quantities[l.id]=l.remainingQuantity})}
function unload(e:BeforeUnloadEvent){if(dirty.value||locked.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{void load();window.addEventListener('beforeunload',unload)});onBeforeUnmount(()=>{alive=false;window.removeEventListener('beforeunload',unload)})
defineExpose({busy:saving,dirty,pending})
</script>
<template>
  <AccessDialog
    v-if="!order"
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
    v-else-if="!shipments.length"
    :order="order"
    :quantities="draft('').quantities"
    :remark="draft('').remark"
    :receipt-error="error || loadError"
    :locked="locked||!canReceive"
    :saving="saving"
    :pending="!!pending"
    :dirty="dirty"
    @close="emit('close')"
    @submit="receive()"
    @quantity="(id,value)=>draft('').quantities[id]=value===''?'':Number(value)"
    @remark="draft('').remark=$event"
  >
    <SuccessToast
      :key="feedbackKey"
      :message="error || success"
      :tone="error?'error':'success'"
    />
    <p
      v-if="loadError"
      class="purchase-warning"
      role="alert"
    >
      最新收货数据读取失败，请刷新核对。<button
        type="button"
        :disabled="loading"
        @click="load"
      >
        重新读取
      </button>
    </p>
    <p
      v-for="warning in warnings"
      :key="warning"
      class="purchase-warning"
    >
      {{ warning }}
    </p>
    <div class="waybill-legacy-hint">
      <span>{{ order.trackingNumber ? `历史运单：${order.carrier||''} ${order.trackingNumber}` : '未登记运单，可先登记运单，也可按实际到货直接收货。' }}</span><button
        v-if="canManage&&!locked"
        type="button"
        @click="manage=true"
      >
        管理运单
      </button>
    </div>
    <label>实际收货时间<DatePicker
      v-model="draft('').date"
      type="datetime-local"
      :disabled="locked"
    /></label>
    <p
      v-if="pending&&!saving"
      class="purchase-warning"
    >
      原请求已保留，点击重试不会重复入账。
    </p>
  </PurchaseReceiptDialog>
  <AccessDialog
    v-else
    ref="dialog"
    title="按运单收货"
    wide
    dialog-class="purchase-ui waybill-receipt-dialog"
    :busy="saving"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <div class="waybill-order-summary">
      <strong>{{ order.lines.map(l=>l.itemName).join('、') }}</strong><span>{{ order.supplierName }}</span><span v-if="order.deliveryAddress">{{ order.deliveryAddress }}</span>
    </div>
    <p class="waybill-receipt-hint">
      核对运单后分别填写实收数量，每张运单独立确认，支持分次收货。
    </p>
    <SuccessToast
      :key="feedbackKey"
      :message="error || success"
      :tone="error?'error':'success'"
    />
    <p
      v-if="loadError"
      class="purchase-warning"
      role="alert"
    >
      最新收货数据读取失败，请刷新核对。<button
        type="button"
        :disabled="loading"
        @click="load"
      >
        重新读取
      </button>
    </p>
    <p
      v-for="warning in warnings"
      :key="warning"
      class="purchase-warning"
    >
      {{ warning }}
    </p>
    <p
      v-if="error"
      class="error-message"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="success"
      class="waybill-success"
      role="status"
    >
      {{ success }}
    </p>
    <p
      v-if="pending&&!saving"
      class="purchase-warning"
    >
      有一张运单的收货结果待确认，请先重试该运单的原请求。
    </p>
    <section
      v-for="(group,index) in groups"
      :key="group.key"
      class="waybill-receipt-block"
      :data-shipment-id="group.key"
    >
      <header>
        <div><strong>{{ group.batch?.carrier || '运单' }} <span>{{ group.batch?.trackingNumber || (group.batch?'图片运单':'原收货请求') }}</span></strong><small>运单 {{ index+1 }}<template v-if="group.batch"> · {{ group.batch.shippedOn }} · {{ group.batch.packageCount }} 个包裹</template></small></div><span
          v-if="!group.order.lines.some(l=>l.remainingQuantity>0)"
          class="waybill-received"
        >已收齐</span>
      </header>
      <div
        v-if="group.batch?.imageId"
        class="waybill-preview"
      >
        <PurchaseItemImage
          :image-asset-id="group.batch.imageId"
          alt="收货运单"
        />
      </div>
      <template v-if="group.order.lines.some(l=>l.remainingQuantity>0)||(pending&&pendingKey===group.key)">
        <TableViewport>
          <table class="data-table waybill-receipt-table">
            <colgroup><col class="waybill-col-product"><col class="waybill-col-total"><col class="waybill-col-count"><col class="waybill-col-count"><col class="waybill-col-remark"><col class="waybill-col-input"></colgroup>
            <thead>
              <tr>
                <th>商品</th><th>运单数量</th><th>已收</th><th>待收</th><th class="waybill-remark-heading">
                  备注
                </th><th>本次实收</th>
              </tr>
            </thead><tbody>
              <tr
                v-for="line in group.order.lines"
                :key="line.id"
              >
                <td>
                  <div class="waybill-product">
                    <PurchaseItemImage
                      :image-asset-id="line.imageAssetId"
                      :image-url="line.imageUrl"
                      :alt="line.itemName"
                    /><span>{{ line.itemName }}<small>{{ line.specification }}</small></span>
                  </div>
                </td><td>{{ line.orderedQuantity }} {{ line.unit }}</td><td>{{ line.receivedQuantity }}</td><td>{{ line.remainingQuantity }}</td><td class="waybill-item-remark">
                  {{ line.remark || '—' }}
                </td><td>
                  <input
                    v-model.number="draft(group.key).quantities[line.id]"
                    type="number"
                    min="0"
                    :max="line.remainingQuantity"
                    step="0.0001"
                    :aria-label="`${group.batch?.trackingNumber||index+1} ${line.itemName}本次实收`"
                    :disabled="locked||!canReceive||line.remainingQuantity<=0"
                    placeholder="0"
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
        <div class="waybill-receipt-fields">
          <label>实际收货时间<DatePicker
            v-model="draft(group.key).date"
            type="datetime-local"
            :disabled="locked||!canReceive"
          /></label><label>收货备注<input
            v-model="draft(group.key).remark"
            maxlength="500"
            placeholder="选填，如少货、外包装情况"
            :disabled="locked||!canReceive"
          ></label>
        </div>
        <footer>
          <button
            type="button"
            class="waybill-fill"
            :disabled="locked||!canReceive"
            @click="fill(group.key)"
          >
            填入本运单待收数量
          </button><button
            v-if="canReceive"
            type="button"
            class="waybill-confirm"
            :disabled="saving||loading||!!loadError||(!!pending&&pendingKey!==group.key)"
            @click="receive(group.key)"
          >
            {{ saving&&savingKey===group.key?'确认中…':pending&&pendingKey===group.key?'重试本次收货':'确认本次收货' }}
          </button>
        </footer>
      </template>
      <p
        v-else
        class="waybill-receipt-hint"
      >
        本运单商品已全部收货。
      </p>
    </section>
    <div
      v-if="unallocated.length"
      class="waybill-unallocated"
    >
      <span>还有 {{ unallocated.length }} 项商品有未分配到运单的余量，请登记对应运单后收货。</span><button
        v-if="canManage&&!locked"
        type="button"
        @click="manage=true"
      >
        管理运单
      </button>
    </div>
    <template #footer>
      <button
        :disabled="saving"
        @click="dialog?.requestClose()"
      >
        {{ pending?'稍后确认':'关闭' }}
      </button>
    </template>
  </AccessDialog>
  <PurchaseShipmentsDialog
    v-if="manage"
    :id="id"
    :writable="canManage"
    @close="manage=false;load()"
  />
</template>
<style>
#app .waybill-receipt-dialog{width:min(1080px,calc(100vw - 24px))}
.waybill-order-summary{display:flex;flex-wrap:wrap;gap:6px 16px;padding:10px 12px;background:#f7f9fc;border:1px solid #e2e8f0;border-radius:5px;font-size:13px}
.waybill-order-summary span{color:#69788d}
.waybill-receipt-hint{color:#7b8799;font-size:12px;line-height:1.7;margin:10px 12px}
.waybill-receipt-block{border:1px solid #dfe6ef;border-radius:6px;margin-top:12px;overflow:hidden}
.waybill-receipt-block>header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;background:#f7f9fc;border-bottom:1px solid #e7ecf2}
.waybill-receipt-block header strong{font-size:13px}.waybill-receipt-block header strong>span{font-weight:400;margin-left:8px;font-family:ui-monospace,monospace;overflow-wrap:anywhere}
.waybill-receipt-block header small{display:block;color:#7b8799;font-size:12px;margin-top:4px}
.waybill-received,.waybill-success{color:#21834a;font-size:12px}
.waybill-receipt-table{min-width:820px;table-layout:fixed;width:100%}
.waybill-col-product{width:auto}.waybill-col-total{width:130px}.waybill-col-count{width:85px}.waybill-col-input{width:150px}
#app .waybill-receipt-table th,#app .waybill-receipt-table td{padding:10px 12px;vertical-align:middle}
#app .waybill-receipt-table th:not(:first-child),#app .waybill-receipt-table td:not(:first-child){text-align:right;font-variant-numeric:tabular-nums}
#app .waybill-receipt-table .waybill-remark-heading,#app .waybill-receipt-table .waybill-item-remark{text-align:left}
#app .waybill-receipt-table .waybill-item-remark{color:#2864c5;white-space:pre-wrap;overflow-wrap:anywhere}
.waybill-col-remark{width:140px}
#app .waybill-receipt-table td>input{width:100%;min-width:0;height:40px;min-height:40px;box-sizing:border-box;text-align:right}
.waybill-product small{display:block;margin-top:3px;color:#7b8799;font-size:12px}

.waybill-product{display:flex;gap:10px;align-items:center;min-width:220px}.waybill-product>span{white-space:normal}
#app .waybill-receipt-dialog .image-button,#app .waybill-receipt-dialog .catalog-image{width:40px;height:40px;min-height:40px;object-fit:contain;padding:0;border:0;background:transparent;flex:none}
.waybill-receipt-fields{display:grid;grid-template-columns:minmax(260px,1fr) minmax(240px,1fr);gap:12px;padding:10px 12px}
.waybill-receipt-fields>label{display:flex;flex-direction:column;gap:5px;font-size:12px;color:#69788d}
#app .waybill-receipt-fields>label{min-width:0;line-height:20px;margin:0}
#app .waybill-receipt-fields>label>input,#app .waybill-receipt-fields .date-picker{width:100%;height:40px;min-height:40px;box-sizing:border-box;margin:0;border-radius:5px}
#app .waybill-receipt-fields>label>input{padding:8px 10px;font-size:13px;line-height:22px}
#app .waybill-receipt-fields .date-picker .date-picker-value>input{height:38px;min-height:38px}
.waybill-receipt-block>footer{display:flex;justify-content:flex-end;align-items:center;gap:16px;padding:8px 12px;border-top:1px solid #e7ecf2}
#app .waybill-receipt-dialog .waybill-fill{display:inline-flex;align-items:center;justify-content:center;color:#2864c5;background:#fff;border:1px solid #b8ccef;border-radius:4px;padding:5px 12px;min-height:32px;font-size:13px;line-height:20px;white-space:nowrap;cursor:pointer;text-decoration:none}
#app .waybill-receipt-dialog .waybill-fill:hover:not(:disabled){background:#f3f7ff;border-color:#8badde}
#app .waybill-receipt-dialog .waybill-fill:focus-visible{outline:2px solid #2864c5;outline-offset:2px}
#app .waybill-receipt-dialog .waybill-confirm{color:white;background:#21834a;border:1px solid #21834a;border-radius:4px;padding:5px 12px;min-height:32px;font-size:13px;cursor:pointer}
#app .waybill-receipt-dialog button:disabled{opacity:.5;cursor:not-allowed}
.waybill-legacy-hint,.waybill-unallocated{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px 10px;background:#fff9ee;color:#806626;font-size:12px;margin-top:10px;border-radius:4px;line-height:1.6}
.waybill-legacy-hint button,.waybill-unallocated button{border:0;background:transparent;color:#2864c5;white-space:nowrap;cursor:pointer}
.waybill-preview{padding:8px 12px}
@media(max-width:700px){.waybill-receipt-fields{grid-template-columns:minmax(0,1fr)}}
</style>
