<script setup lang="ts">
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
const dialog=ref<InstanceType<typeof AccessDialog>>(),order=ref<PurchaseOrder>(),rows=ref<PurchaseShipment[]>([]),loading=ref(false),error=ref(''),busy=ref(false),uploading=ref(false),adding=ref(false),copied=ref(''),quantities=reactive<Record<string,number|''>>({}),pending=ref<ShipmentInput|undefined>(readPurchasePending('shipment',props.id))
const form=reactive({carrier:'',trackingNumber:'',imageId:null as string|null,shippedOn:beijingDate(),packageCount:1,remark:''}),dirty=computed(()=>adding.value&&(!!form.carrier||!!form.trackingNumber||!!form.imageId||Object.values(quantities).some(v=>Number(v)>0))),locked=computed(()=>busy.value||uploading.value||!!pending.value)
let alive=true
function remaining(id:string){const line=order.value?.lines.find(v=>v.id===id);return Math.max(0,(line?.remainingQuantity??0)-rows.value.reduce((sum,r)=>sum+r.lines.filter(l=>l.orderLineId===id).reduce((s,l)=>s+l.quantity-l.receivedQuantity,0),0))}
async function load(){loading.value=true;error.value='';try{const [o,r]=await Promise.all([purchaseApi.order(props.id),api.shipments(props.id)]);if(alive){order.value=o;rows.value=r}}catch(e){error.value=e instanceof Error?e.message:'读取失败'}finally{loading.value=false}}
async function upload(file:File){uploading.value=true;try{const r=await catalogApi.uploadImage('PURCHASE_WAYBILL',file);if(alive)form.imageId=r.id}finally{uploading.value=false}}
async function save(){if(!order.value||busy.value||uploading.value)return;error.value='';if(!pending.value){const lines=Object.entries(quantities).filter(([,q])=>Number(q)>0).map(([orderLineId,q])=>({orderLineId,quantity:Number(q)}));if(!lines.length||lines.some(l=>l.quantity>remaining(l.orderLineId))){error.value='请选择本批商品并填写不超过未分配余量的数量';return}pending.value={...form,requestId:crypto.randomUUID(),version:order.value.version,lines};writePurchasePending('shipment',pending.value,props.id)}busy.value=true;try{await api.shipment(props.id,pending.value);pending.value=undefined;writePurchasePending('shipment',null,props.id);adding.value=false;Object.keys(quantities).forEach(k=>delete quantities[k]);Object.assign(form,{carrier:'',trackingNumber:'',imageId:null,shippedOn:beijingDate(),packageCount:1,remark:''});await load()}catch(e){error.value=e instanceof Error?e.message:'结果待确认，请重试原发货请求';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;writePurchasePending('shipment',null,props.id);const text=error.value;await load();error.value=text+'；草稿保留，请核对最新余量后重新保存'}}finally{busy.value=false}}
async function copy(text:string){try{await navigator.clipboard.writeText(text);copied.value=text}catch{error.value='复制失败，可直接选择运单文字复制'}}
function unload(e:BeforeUnloadEvent){if(dirty.value||locked.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{if(pending.value){Object.assign(form,pending.value);pending.value.lines.forEach(l=>quantities[l.orderLineId]=l.quantity);adding.value=true}void load();window.addEventListener('beforeunload',unload)});onBeforeUnmount(()=>{alive=false;window.removeEventListener('beforeunload',unload)})
</script>
<template>
  <AccessDialog
    ref="dialog"
    title="发货批次 / 多运单"
    wide
    dialog-class="purchase-ui"
    :busy="busy||uploading"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p><p v-if="loading">
      正在读取…
    </p>
    <template v-if="order&&!loading">
      <p>{{ order.orderNo }} · {{ order.supplierName }}</p><p
        v-if="order.trackingNumber||order.waybillImageId"
        class="purchase-warning"
      >
        原物流资料保留：{{ order.carrier }} {{ order.trackingNumber||'图片运单' }}，未自动拆成历史批次。
      </p>
      <div
        v-for="(row,i) in rows"
        :key="row.id"
        class="purchase-shipment"
      >
        <strong>第 {{ i+1 }} 批 · {{ row.shippedOn }} · {{ row.packageCount }} 个包裹</strong><span>{{ row.carrier }} {{ row.trackingNumber }}</span><button
          v-if="row.trackingNumber"
          @click="copy(row.trackingNumber)"
        >
          {{ copied===row.trackingNumber?'已复制':'复制运单' }}
        </button><PurchaseItemImage
          v-if="row.imageId"
          :image-asset-id="row.imageId"
          alt="批次运单"
        /><p
          v-for="line in row.lines"
          :key="line.orderLineId"
        >
          {{ order.lines.find(l=>l.id===line.orderLineId)?.itemName }} · 发出 {{ line.quantity }} / 已收 {{ line.receivedQuantity }} {{ order.lines.find(l=>l.id===line.orderLineId)?.unit }}
        </p><small>{{ row.remark }}</small>
      </div>
      <p v-if="!rows.length">
        尚未登记发货批次；可继续普通收货。登记批次后，收货需选择对应批次。
      </p>
      <button
        v-if="writable&&!adding&&['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.status)"
        @click="adding=true"
      >
        登记下一批发货
      </button>
      <form
        v-if="adding"
        id="purchase-shipment-form"
        @submit.prevent="save"
      >
        <p
          v-if="pending"
          class="purchase-warning"
        >
          原发货请求待确认，刷新后可继续恢复。
        </p><fieldset
          :disabled="locked"
          class="purchase-fields"
        >
          <label>物流公司<input
            v-model="form.carrier"
            maxlength="100"
          ></label><label>运单号<input
            v-model="form.trackingNumber"
            maxlength="64"
            pattern="[A-Za-z0-9-]{1,64}"
          ></label><label>发货日期<DatePicker
            v-model="form.shippedOn"
            type="date"
            :max="beijingDate()"
            required
          /></label><label>本批包裹数<input
            v-model.number="form.packageCount"
            type="number"
            min="1"
            max="2147483647"
            step="1"
            required
          ></label><div class="purchase-full">
            <SingleFileUpload
              title="运单图片（运单号与图片至少一项）"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="locked"
              :upload="upload"
            /><PurchaseItemImage
              v-if="form.imageId"
              :image-asset-id="form.imageId"
              alt="新运单"
            />
          </div><div class="purchase-full">
            <TableViewport>
              <table class="data-table">
                <thead><tr><th>商品</th><th>尚未分配</th><th>本批发出</th></tr></thead><tbody>
                  <tr
                    v-for="line in order.lines"
                    :key="line.id"
                  >
                    <td>{{ line.itemName }} · {{ line.unit }}</td><td>{{ remaining(line.id) }}</td><td>
                      <input
                        v-model.number="quantities[line.id]"
                        type="number"
                        min="0"
                        :max="remaining(line.id)"
                        step="0.0001"
                        :aria-label="`${line.itemName}本批发出`"
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </TableViewport>
          </div><label class="purchase-full">备注<textarea
            v-model="form.remark"
            maxlength="500"
            rows="2"
          /></label>
        </fieldset>
      </form>
    </template>
    <template #footer>
      <button
        :disabled="busy||uploading"
        @click="dialog?.requestClose()"
      >
        关闭
      </button><button
        v-if="adding&&writable"
        form="purchase-shipment-form"
        type="submit"
        :disabled="busy||uploading||loading||!order"
      >
        {{ pending?'确认原发货请求':'保存发货批次' }}
      </button>
    </template>
  </AccessDialog>
</template>
