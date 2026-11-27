<script setup lang="ts">
import {copyPurchaseLogistics} from '@/ui/copyPurchaseLogistics'
import {createRequestId} from '@/utils/requestId'
import {confirmAction} from '@/state/uiConfirmation'
import {computed,onMounted,onBeforeUnmount,reactive,ref} from 'vue'
import AccessDialog from './AccessDialog.vue'
import DatePicker from './DatePicker.vue'
import SingleFileUpload from './SingleFileUpload.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import TableViewport from './TableViewport.vue'
import {purchaseManagementApi as api,type PurchaseShipment,type ShipmentInput} from '@/api/purchaseManagement'
import {purchaseApi,type PurchaseOrder} from '@/api/purchases'
import {catalogApi} from '@/api/catalog'
import {readPurchasePending,writePurchasePending} from '@/ui/purchasePending'
import {ApiError} from '@/api/client'
import {beijingDate} from '@/utils/dateTime'
const props=defineProps<{id:string;writable:boolean}>(),emit=defineEmits<{close:[]}>()
interface PendingShipment extends ShipmentInput {shipmentId?:string;shipmentVersion?:number}
const dialog=ref<InstanceType<typeof AccessDialog>>(),order=ref<PurchaseOrder>(),rows=ref<PurchaseShipment[]>([]),loading=ref(false),error=ref(''),busy=ref(false),uploading=ref(false),adding=ref(false),copied=ref(''),editingId=ref(''),initial=ref(''),quantities=reactive<Record<string,number|''>>({}),pending=ref<PendingShipment|undefined>(readPurchasePending('shipment',props.id))
const form=reactive({carrier:'',trackingNumber:'',imageId:null as string|null,shippedOn:beijingDate(),packageCount:1,remark:''})
const snapshot=()=>JSON.stringify({form,quantities})
const dirty=computed(()=>adding.value&&snapshot()!==initial.value),locked=computed(()=>busy.value||uploading.value||!!pending.value)
const canAdd=computed(()=>props.writable&&!!order.value&&['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.value.status))
const hasRemaining=computed(()=>order.value?.lines.some(line=>unallocated(line.id)>0)??false)
const productNames=computed(()=>order.value?.lines.map(line=>line.itemName).join('、')||'暂无商品信息')
function unallocated(id:string){const line=order.value?.lines.find(v=>v.id===id);return Math.max(0,(line?.remainingQuantity??0)-rows.value.reduce((sum,r)=>sum+r.lines.filter(l=>l.orderLineId===id).reduce((s,l)=>s+l.quantity-l.receivedQuantity,0),0))}
function minimum(id:string){return rows.value.find(r=>r.id===editingId.value)?.lines.find(l=>l.orderLineId===id)?.receivedQuantity??0}
function remaining(id:string){return unallocated(id)+(rows.value.find(r=>r.id===editingId.value)?.lines.find(l=>l.orderLineId===id)?.quantity??0)}
function clearDraft(){editingId.value='';Object.keys(quantities).forEach(k=>delete quantities[k]);Object.assign(form,{carrier:'',trackingNumber:'',imageId:null,shippedOn:beijingDate(),packageCount:1,remark:''})}
function startNew(){if(!canAdd.value||!hasRemaining.value||locked.value)return;clearDraft();if(!rows.value.length&&order.value)Object.assign(form,{carrier:order.value.carrier||'',trackingNumber:order.value.trackingNumber||'',imageId:order.value.waybillImageId||null});adding.value=true;initial.value=snapshot()}
function startEdit(row:PurchaseShipment){if(!props.writable||order.value?.status==='CANCELLED'||locked.value)return;clearDraft();editingId.value=row.id;Object.assign(form,{carrier:row.carrier||'',trackingNumber:row.trackingNumber||'',imageId:row.imageId||null,shippedOn:row.shippedOn,packageCount:row.packageCount,remark:row.remark||''});row.lines.forEach(l=>quantities[l.orderLineId]=l.quantity);adding.value=true;initial.value=snapshot()}
function fillRemaining(){if(locked.value)return;order.value?.lines.forEach(line=>quantities[line.id]=remaining(line.id))}
async function cancelAdding(){if(locked.value)return;if(dirty.value&&!await confirmAction('本次填写的运单信息尚未保存。',{unsaved:true}))return;adding.value=false;clearDraft()}
let alive=true
async function load(){loading.value=true;error.value='';try{const [o,r]=await Promise.all([purchaseApi.order(props.id),api.shipments(props.id)]);if(alive){order.value=o;rows.value=r}}catch(e){error.value=e instanceof Error?e.message:'读取失败'}finally{loading.value=false}}
async function upload(file:File){uploading.value=true;try{const r=await catalogApi.uploadImage('PURCHASE_WAYBILL',file);if(alive)form.imageId=r.id}finally{uploading.value=false}}
async function save(){if(!order.value||busy.value||uploading.value||!props.writable)return;error.value='';if(!pending.value){
 const lines=Object.entries(quantities).filter(([,q])=>Number(q)>0).map(([orderLineId,q])=>({orderLineId,quantity:Number(q)}));
 if(!lines.length||order.value.lines.some(l=>{const q=Number(quantities[l.id]||0);return !Number.isFinite(q)||q<minimum(l.id)||q>remaining(l.id)||q<0})){error.value='请核对未分配余量，运单数量不能少于已收数量或超过可分配数量';return}
 if(!form.trackingNumber.trim()&&!form.imageId){error.value='请填写运单号或上传运单图片';return}
 const existing=rows.value.find(r=>r.id===editingId.value);
 pending.value={...form,requestId:createRequestId(),version:order.value.version,lines,...(existing?{shipmentId:existing.id,shipmentVersion:existing.version}:{})};writePurchasePending('shipment',pending.value,props.id)
 }busy.value=true;try{
 const {shipmentId,shipmentVersion,...input}=pending.value;
 if(shipmentId)await api.editShipment(props.id,shipmentId,{...input,shipmentVersion:shipmentVersion!});else await api.shipment(props.id,input);
 pending.value=undefined;writePurchasePending('shipment',null,props.id);adding.value=false;clearDraft();await load()
 }catch(e){error.value=e instanceof Error?e.message:'结果待确认，请重试原运单请求';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;writePurchasePending('shipment',null,props.id);const text=error.value;await load();error.value=text+'；草稿保留，请核对最新余量后重新保存'}}finally{busy.value=false}}

async function copy(row:PurchaseShipment){try{await copyPurchaseLogistics(row.carrier,row.trackingNumber);copied.value=row.id}catch{error.value='复制失败，可直接选择运单文字复制'}}
function unload(e:BeforeUnloadEvent){if(dirty.value||locked.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{if(pending.value){Object.assign(form,pending.value);pending.value.lines.forEach(l=>quantities[l.orderLineId]=l.quantity);editingId.value=pending.value.shipmentId||'';adding.value=true;initial.value=snapshot()}void load();window.addEventListener('beforeunload',unload)});onBeforeUnmount(()=>{alive=false;window.removeEventListener('beforeunload',unload)})
</script>
<template>
  <AccessDialog
    ref="dialog"
    title="管理运单"
    wide
    dialog-class="purchase-ui purchase-shipments-dialog"
    :busy="busy||uploading"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <p
      v-if="error"
      class="shipment-error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="shipment-hint"
    >
      正在读取发货资料…
    </p>
    <template v-if="order&&!loading">
      <section
        class="shipment-order-summary"
        aria-label="采购商品与供应商"
      >
        <div><strong :title="productNames">{{ productNames }}</strong><span>{{ order.supplierName || '未填写供应商' }}</span></div>
        <span class="shipment-count">{{ rows.length }} 张运单</span>
      </section>
      <details
        v-if="order.trackingNumber||order.waybillImageId"
        class="shipment-legacy"
      >
        <summary>历史物流资料{{ rows.length ? '（只读留存）' : '（登记首张运单时带入）' }}</summary>
        <p>{{ order.carrier }} {{ order.trackingNumber || '图片运单' }} · 登记运单前的资料保留，不作为另一张待收运单。</p>
        <PurchaseItemImage
          v-if="order.waybillImageId"
          :image-asset-id="order.waybillImageId"
          alt="历史运单"
        />
      </details>
      <div
        v-if="!adding"
        class="shipment-section-heading"
      >
        <h3>运单记录</h3>
        <button
          v-if="canAdd&&rows.length"
          class="shipment-primary"
          :disabled="!hasRemaining"
          @click="startNew"
        >
          新增运单
        </button>
      </div>
      <div
        v-if="!rows.length&&!adding"
        class="shipment-empty"
      >
        <svg
          viewBox="0 0 32 32"
          aria-hidden="true"
        ><path d="M5 10 16 4l11 6v13l-11 6-11-6Z M5 10l11 6 11-6M16 16v13M10 7l11 6v5" /></svg>
        <strong>尚未登记运单</strong>
        <p>每张运单对应本次发出的商品和数量，收货时按运单确认。</p>
        <button
          v-if="canAdd"
          class="shipment-primary"
          :disabled="!hasRemaining"
          @click="startNew"
        >
          登记运单
        </button>
      </div>
      <div
        v-if="!adding"
        class="shipment-list"
      >
        <article
          v-for="(row,i) in rows"
          :key="row.id"
          class="shipment-card"
        >
          <header>
            <div><strong>运单 {{ i+1 }}</strong><span>{{ row.shippedOn }}</span><span>{{ row.packageCount }} 个包裹</span></div><button
              v-if="writable&&order.status!=='CANCELLED'"
              type="button"
              class="shipment-link"
              @click="startEdit(row)"
            >
              编辑运单
            </button><span
              class="shipment-progress"
              :class="{'is-complete':row.lines.every(l=>l.receivedQuantity>=l.quantity)}"
            >{{ row.lines.every(l=>l.receivedQuantity>=l.quantity)?'已收齐':'待收齐' }}</span>
          </header>
          <div class="shipment-carrier">
            <PurchaseItemImage
              v-if="row.imageId"
              :image-asset-id="row.imageId"
              alt="批次运单"
            />
            <div>
              <strong>{{ row.carrier || '物流单' }}</strong><span
                v-if="row.trackingNumber"
                class="shipment-tracking"
              >{{ row.trackingNumber }}</span>
            </div>
            <button
              v-if="row.trackingNumber"
              class="shipment-link"
              @click="copy(row)"
            >
              {{ copied===row.id?'已复制':'复制物流信息' }}
            </button>
          </div>
          <div class="shipment-line-head">
            <span>商品</span><span>运单发货数量</span><span>已收</span>
          </div>
          <div
            v-for="line in row.lines"
            :key="line.orderLineId"
            class="shipment-line-row"
          >
            <span>{{ order.lines.find(l=>l.id===line.orderLineId)?.itemName || '历史商品' }}</span><span>{{ line.quantity }} {{ order.lines.find(l=>l.id===line.orderLineId)?.unit }}</span><span>{{ line.receivedQuantity }} {{ order.lines.find(l=>l.id===line.orderLineId)?.unit }}</span>
          </div>
          <p
            v-if="row.remark"
            class="shipment-remark"
          >
            <span>备注</span>{{ row.remark }}
          </p>
        </article>
      </div>
      <form
        v-if="adding"
        id="purchase-shipment-form"
        class="shipment-form"
        @submit.prevent="save"
      >
        <div class="shipment-section-heading">
          <h3>{{ editingId?'编辑运单':'登记新运单' }}</h3><span>填写这张运单对应的实际发货数量</span>
        </div>
        <p
          v-if="pending&&!busy"
          class="purchase-warning"
        >
          原运单请求待确认，重试不会重复登记。
        </p>
        <fieldset
          :disabled="locked"
          class="purchase-fields"
        >
          <label>物流公司<input
            v-model="form.carrier"
            maxlength="100"
            placeholder="例如：顺丰、德邦"
          ></label>
          <label>运单号<input
            v-model="form.trackingNumber"
            maxlength="64"
            pattern="[A-Za-z0-9-]{1,64}"
            placeholder="填写运单号，或上传下方运单图片"
          ></label>
          <label>发货日期<DatePicker
            v-model="form.shippedOn"
            type="date"
            :max="beijingDate()"
            required
            :disabled="locked"
          /></label>
          <label>包裹数<input
            v-model.number="form.packageCount"
            type="number"
            min="1"
            max="2147483647"
            step="1"
            required
          ></label>
          <div class="purchase-full shipment-upload">
            <PurchaseItemImage
              v-if="form.imageId"
              :image-asset-id="form.imageId"
              alt="新运单"
            />
            <SingleFileUpload
              title="运单图片（与运单号至少填写一项）"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="locked"
              :upload="upload"
            />
            <button
              v-if="form.imageId"
              type="button"
              class="shipment-remove"
              @click="form.imageId=null"
            >
              移除图片
            </button>
          </div>
          <div class="purchase-full">
            <div class="shipment-section-heading">
              <h3>运单商品</h3><button
                type="button"
                class="shipment-fill-button"
                :disabled="locked||!hasRemaining"
                @click="fillRemaining"
              >
                填入全部剩余数量
              </button>
            </div>
            <TableViewport>
              <table class="data-table shipment-quantity-table">
                <thead><tr><th>商品</th><th>可分配数量</th><th>运单发货数量</th></tr></thead><tbody>
                  <tr
                    v-for="line in order.lines"
                    :key="line.id"
                  >
                    <td>{{ line.itemName }}</td><td>{{ remaining(line.id) }} {{ line.unit }}</td><td>
                      <div class="shipment-quantity-input">
                        <input
                          v-model.number="quantities[line.id]"
                          type="number"
                          :min="minimum(line.id)"
                          :max="remaining(line.id)"
                          step="0.0001"
                          :disabled="remaining(line.id)<=0"
                          placeholder="0"
                          :aria-label="`${line.itemName}运单发货数量`"
                        ><span>{{ line.unit }}</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </TableViewport>
          </div>
          <label class="purchase-full">备注<textarea
            v-model="form.remark"
            maxlength="500"
            rows="2"
            placeholder="选填，本运单的补充说明"
          /></label>
        </fieldset>
      </form>
    </template>
    <template #footer>
      <button
        v-if="adding&&!pending"
        :disabled="busy||uploading"
        @click="cancelAdding"
      >
        取消编辑
      </button>
      <button
        v-else
        :disabled="busy||uploading"
        @click="dialog?.requestClose()"
      >
        {{ pending?'稍后确认':'关闭' }}
      </button>
      <button
        v-if="adding&&writable"
        class="shipment-primary"
        form="purchase-shipment-form"
        type="submit"
        :disabled="busy||uploading||loading||!order||(!pending&&!!editingId&&!dirty)"
      >
        {{ busy?'保存中…':pending?'确认原运单请求':'保存运单' }}
      </button>
    </template>
  </AccessDialog>
</template>
<style>
#app .purchase-shipments-dialog{width:min(900px,calc(100vw - 24px))}
#app .purchase-shipments-dialog>header>button{min-width:28px;min-height:28px;padding:0;border:0;background:transparent;box-shadow:none}
.purchase-shipments-dialog .shipment-order-summary{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 14px;border:1px solid #e5eaf1;border-radius:6px;background:#f8fafc}
.shipment-order-summary>div{display:grid;gap:4px;min-width:0}
.shipment-order-summary strong{font-size:14px;line-height:21px;overflow-wrap:anywhere}
.shipment-order-summary>div>span,.shipment-count{font-size:12px;color:#6b778a}
.shipment-count{white-space:nowrap}
.shipment-legacy{font-size:12px;color:#6b778a;margin:10px 0;line-height:1.6}
.shipment-legacy>span{color:#8b96a7}
.shipment-section-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin:14px 0 10px}
.shipment-section-heading h3{margin:0;font-size:14px;color:#243247}
.shipment-section-heading>span{font-size:12px;color:#7b8799}
#app .purchase-shipments-dialog .shipment-primary{min-height:32px;padding:5px 14px;background:#21834a;border:1px solid #21834a;border-radius:4px;color:white;font-size:13px;cursor:pointer}
#app .purchase-shipments-dialog .shipment-primary:hover:not(:disabled){background:#196c3b}
#app .purchase-shipments-dialog button:disabled{opacity:.5;cursor:not-allowed}
#app .purchase-shipments-dialog .shipment-link{min-height:24px;padding:0;border:0;background:transparent;color:#2864c5;font-size:12px;align-self:center;cursor:pointer}
.shipment-empty{display:flex;flex-direction:column;align-items:center;gap:10px;padding:24px 16px;border:1px dashed #dce3ec;border-radius:6px;background:#fbfcfe;text-align:center}
.shipment-empty svg{width:32px;height:32px;fill:none;stroke:#899bb4;stroke-width:1.5}
.shipment-empty strong{font-size:14px;color:#526075}
.shipment-empty p{margin:0;font-size:12px;color:#7b8799;line-height:1.7}
.shipment-list{display:grid;gap:12px}
.shipment-card{border:1px solid #e1e7ef;border-radius:6px;overflow:hidden}
.shipment-card>header{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px 12px;background:#f7f9fc;border-bottom:1px solid #e7ecf2}
.shipment-card>header>div{display:flex;gap:12px;flex-wrap:wrap;align-items:center}
.shipment-card>header strong{font-size:13px}
.shipment-card>header span{font-size:12px;color:#6b778a}
.shipment-card>header .shipment-progress{background:#edf4ff;color:#2864c5;padding:2px 6px;border-radius:4px;white-space:nowrap}
.shipment-card>header .is-complete{background:#edf7f1;color:#21834a}
.shipment-carrier{display:flex;align-items:center;gap:12px;padding:10px 12px}
.shipment-carrier>div{display:grid;gap:4px;font-size:13px;min-width:0}
.shipment-tracking{font-family:ui-monospace,monospace;font-size:12px;color:#69788d;overflow-wrap:anywhere}
#app .purchase-shipments-dialog .image-button,#app .purchase-shipments-dialog .catalog-image{width:48px;height:48px;min-height:48px;object-fit:contain;flex:none;border-radius:4px}
#app .purchase-shipments-dialog .image-button{padding:0;border:0;background:transparent}
.shipment-line-head,.shipment-line-row{display:grid;grid-template-columns:minmax(0,1fr) 120px 120px;gap:12px;padding:8px 12px;font-size:12px;line-height:20px}
.shipment-line-head{color:#7b8799;background:#fafbfd}
.shipment-line-row{border-top:1px solid #edf0f5;overflow-wrap:anywhere}
.shipment-remark{margin:0;padding:8px 12px;border-top:1px solid #edf0f5;font-size:12px;white-space:pre-wrap;overflow-wrap:anywhere}
.shipment-remark>span{margin-right:10px;color:#7b8799}
.shipment-upload{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.shipment-upload>.single-file-upload{flex:1;min-width:220px}
#app .purchase-shipments-dialog .shipment-remove{color:#b42318;background:transparent;border:0;font-size:12px;min-height:24px;cursor:pointer}
.shipment-quantity-table{min-width:480px}
.shipment-quantity-table th:first-child{width:50%}
.shipment-quantity-input{display:flex;align-items:center;gap:8px}
.shipment-quantity-input>span{color:#7b8799;font-size:12px;white-space:nowrap}
.shipment-error{color:#b42318;background:#fff5f4;padding:8px 10px;border-radius:4px;font-size:13px;line-height:1.7}
.shipment-hint{font-size:13px;color:#7b8799}
#app .purchase-shipments-dialog .shipment-fill-button{display:inline-flex;align-items:center;justify-content:center;min-height:32px;padding:5px 12px;border:1px solid #b8ccef;border-radius:4px;background:#fff;color:#2864c5;font-size:13px;line-height:20px;white-space:nowrap;cursor:pointer}
#app .purchase-shipments-dialog .shipment-fill-button:hover:not(:disabled){background:#f3f7ff;border-color:#8badde}
#app .purchase-shipments-dialog .shipment-fill-button:focus-visible{outline:2px solid #2864c5;outline-offset:2px}
#app .purchase-shipments-dialog .shipment-fill-button:disabled{opacity:.5;cursor:not-allowed}
@media(max-width:600px){.shipment-line-head,.shipment-line-row{grid-template-columns:minmax(0,1fr) 70px 70px;gap:8px}.shipment-order-summary{align-items:flex-start!important}.shipment-count{display:none}.shipment-card>header>div{gap:6px}}
</style>
