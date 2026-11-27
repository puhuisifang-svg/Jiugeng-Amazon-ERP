<script setup lang="ts">
import '@/styles/purchaseManagement.css'
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import {useRoute,RouterLink,onBeforeRouteLeave} from 'vue-router'
import {purchaseApi,type PurchaseOrder,type PurchaseReceipt} from '@/api/purchases'
import {purchaseManagementApi as api,type PurchaseShipment} from '@/api/purchaseManagement'
import type {ProductHistoryDto} from '@/api/domain'
import {session} from '@/state/session'
import {beijingDateTime} from '@/utils/dateTime'
import {purchaseLineAmount,purchasePrice} from '@/ui/purchaseAmounts'
import {confirmAction} from '@/state/uiConfirmation'
import AccessDialog from '@/components/AccessDialog.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import ProductHistoryTable from '@/components/ProductHistoryTable.vue'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import PurchaseReceiveFlow from '@/components/PurchaseReceiveFlow.vue'
import PurchaseShipmentsDialog from '@/components/PurchaseShipmentsDialog.vue'
import SupplierDetailDialog from '@/components/SupplierDetailDialog.vue'
import PurchaseRelatedItem from '@/components/PurchaseRelatedItem.vue'
const props=defineProps<{orderId?:string;embedded?:boolean;action?:'receive'|'cancel'}>(),emit=defineEmits<{close:[];completed:[]}>(),route=useRoute()
const id=computed(()=>String(props.orderId??route.params.orderId)),order=ref<PurchaseOrder>(),loading=ref(false),error=ref(''),tab=ref('lines'),page=ref(1),size=ref(20),total=ref(0),events=ref<ProductHistoryDto[]>([]),receipts=ref<{receipt:PurchaseReceipt;shipmentId:string|null}[]>([]),receiptShipments=ref<PurchaseShipment[]>([]),warnings=ref<string[]>([]),showReceive=ref(false),showCancel=ref(false),shipments=ref(false),supplier=ref(false),cancelReason=ref(''),saving=ref(false),receiveFlow=ref<InstanceType<typeof PurchaseReceiveFlow>>()
const receiptNotice=ref(''),receiptNoticeKey=ref(0)
function receiptUpdated(){void load()}
const labels={DRAFT:'草稿',SUBMITTED:'待收货',PARTIALLY_RECEIVED:'部分收货',COMPLETED:'已完成',CANCELLED:'已取消 / 余量关闭'}
let seq=0
async function load(){const n=++seq;loading.value=true;error.value='';try{const [o,w]=await Promise.all([purchaseApi.order(id.value),api.checks(id.value)]);if(n!==seq)return;order.value=o;warnings.value=w;if(tab.value==='history'){const r=await api.history(id.value,page.value-1,size.value);if(n===seq){events.value=r.items;total.value=r.total}}else if(tab.value==='receipts'){const [r,batches]=await Promise.all([api.receipts(id.value,page.value-1,size.value),api.shipments(id.value)]);if(n===seq){receipts.value=r.items;receiptShipments.value=batches;total.value=r.total}}}catch(e){if(n===seq)error.value=e instanceof Error?e.message:'读取失败'}finally{if(n===seq)loading.value=false}}
async function completed(message:string){showReceive.value=false;if(props.action)emit('completed');else{receiptNotice.value=message;receiptNoticeKey.value++;await load()}}
async function cancel(){if(!order.value||saving.value||!cancelReason.value.trim())return;saving.value=true;error.value='';try{await purchaseApi.cancel(id.value,order.value.version,cancelReason.value);showCancel.value=false;if(props.action)emit('completed');else await load()}catch(e){error.value=e instanceof Error?e.message:'关闭结果待确认，请刷新核对'}finally{saving.value=false}}
async function submit(){if(!order.value||!await confirmAction('确认下单后进入待收货；尚未发货或收货前仍可编辑，系统不会自动向供应商发送订单。'))return;saving.value=true;try{await purchaseApi.submit(id.value,order.value.version);await load()}catch(e){error.value=e instanceof Error?e.message:'下单结果待确认'}finally{saving.value=false}}
async function mayLeave(){return !saving.value&&!receiveFlow.value?.busy&&(!receiveFlow.value?.dirty||!!receiveFlow.value?.pending||await confirmAction('收货资料未保存，确定离开？', { unsaved: true, discardLabel: '放弃并离开' }))}
onBeforeRouteLeave(mayLeave);defineExpose({mayLeave})
watch(id,async()=>{order.value=undefined;showReceive.value=false;showCancel.value=false;await load();if(props.action==='receive')showReceive.value=true;if(props.action==='cancel'&&order.value)showCancel.value=true},{immediate:true})
watch([tab,page,size],load);onBeforeUnmount(()=>seq++)
function shipmentLabel(shipmentId:string){const batch=receiptShipments.value.find(v=>v.id===shipmentId);return batch?`发货 ${batch.shippedOn} · ${batch.carrier||'物流未填'} · ${batch.trackingNumber||'图片运单'}`:'关联发货批次暂不可用'}
function chooseTab(value:string){tab.value=value;page.value=1}
</script>
<template>
  <section class="purchase-ui purchase-detail">
    <SuccessToast
      :key="receiptNoticeKey"
      :message="receiptNotice"
    />
    <template v-if="!action">
      <div
        v-if="order"
        class="purchase-detail-overview"
      >
        <div class="purchase-detail-identity">
          <div class="purchase-detail-heading">
            <strong>{{ order.orderNo }}</strong><span
              class="purchase-status"
              :data-status="order.status"
            >{{ labels[order.status] }}</span>
          </div>
          <button
            v-if="order.supplierId"
            class="purchase-detail-supplier"
            @click="supplier=true"
          >
            {{ order.supplierName }}
          </button><span
            v-else
            class="purchase-detail-supplier-name"
          >{{ order.supplierName }}</span>
        </div>
        <div class="purchase-detail-total">
          <span>采购总额</span><strong><small>{{ order.currency }}</small>{{ order.lines.reduce((s,l)=>s+purchaseLineAmount(l),0).toFixed(2) }}</strong>
        </div>
        <button
          class="purchase-detail-reload"
          :disabled="loading"
          @click="load"
        >
          {{ loading?'读取中…':'刷新' }}
        </button>
        <RouterLink
          v-if="!embedded"
          :to="{path:'/purchases/orders',query:{type:order.orderType}}"
        >
          返回采购管理
        </RouterLink>
      </div>
      <p
        v-if="error"
        role="alert"
      >
        {{ error }}（保留的内容为上次读取结果）
      </p><p v-if="loading&&!order">
        正在读取…
      </p>
      <template v-if="order">
        <dl class="purchase-summary-fields">
          <div><dt>预计到货</dt><dd>{{ order.expectedDeliveryDate||'未约定' }}</dd></div><div><dt>收货信息</dt><dd>{{ order.deliveryAddress||'未填写' }}</dd></div><div><dt>创建时间</dt><dd>{{ beijingDateTime(order.createdAt) }}</dd></div><div
            v-if="order.remark"
            class="purchase-detail-note"
          >
            <dt>整单备注</dt><dd>{{ order.remark }}</dd>
          </div><div v-if="order.cancellationReason">
            <dt>关闭原因</dt><dd>{{ order.cancellationReason }}</dd>
          </div>
        </dl>
        <details
          v-if="warnings.length"
          class="purchase-warning"
        >
          <summary>{{ warnings.length }} 项关联资料需核对</summary><p
            v-for="warning in warnings"
            :key="warning"
          >
            {{ warning }}
          </p>
        </details>
        <div class="purchase-detail-toolbar">
          <div class="purchase-detail-actions">
            <button
              v-if="session.canAccess(['purchases.receive'])&&['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.status)"
              class="purchase-detail-primary"
              @click="showReceive=true"
            >
              登记收货
            </button><button
              class="purchase-detail-secondary"
              @click="shipments=true"
            >
              管理运单
            </button><button
              v-if="session.canAccess(['purchases.submit'])&&order.status==='DRAFT'"
              class="purchase-detail-primary"
              :disabled="saving"
              @click="submit"
            >
              确认下单
            </button><button
              v-if="session.canAccess(['purchases.cancel'])&&!['COMPLETED','CANCELLED'].includes(order.status)"
              class="purchase-detail-danger"
              @click="showCancel=true"
            >
              关闭未收余量
            </button>
          </div>
          <nav
            class="access-tabs"
            aria-label="采购单详情页签"
          >
            <button
              v-for="(label,key) in {lines:'采购明细',receipts:'收货记录',history:'变更历史'}"
              :key="key"
              :class="{active:tab===key}"
              @click="chooseTab(key)"
            >
              {{ label }}
            </button>
          </nav>
        </div>
        <div class="purchase-detail-content">
          <TableViewport v-if="tab==='lines'">
            <table class="data-table purchase-detail-table">
              <colgroup><col class="detail-col-image"><col class="detail-col-product"><col class="detail-col-count"><col class="detail-col-count"><col class="detail-col-count"><col class="detail-col-price"><col class="detail-col-tax"><col class="detail-col-remark"><col class="detail-col-amount"></colgroup>
              <thead>
                <tr>
                  <th>图片</th><th>商品 / 规格</th><th class="detail-number">
                    采购数量
                  </th><th class="detail-number">
                    已收
                  </th><th class="detail-number">
                    {{ order.status==='CANCELLED'?'关闭余量':'待收' }}
                  </th><th class="detail-number">
                    未税单价
                  </th><th>税率</th><th>备注</th><th class="detail-number">
                    应付金额
                  </th>
                </tr>
              </thead><tbody>
                <tr
                  v-for="line in order.lines"
                  :key="line.id"
                >
                  <td>
                    <PurchaseItemImage
                      :image-asset-id="line.imageAssetId"
                      :image-url="line.imageUrl"
                      :alt="line.itemName"
                    /><small>{{ line.snapshotSaved?'下单留存':'当前图片 · 历史未留图' }}</small>
                  </td><td>
                    <PurchaseRelatedItem
                      v-if="order.orderType!=='MANUAL'"
                      :order-id="id"
                      :item-id="line.itemId"
                      :type="order.orderType"
                      :name="line.itemName"
                    /><strong v-else>{{ line.itemName }}</strong><small>{{ line.specification||'—' }}</small><small v-if="line.packageCount!=null">{{ line.packageCount }} 个包裹</small>
                  </td><td class="detail-number">
                    {{ line.orderedQuantity }}<small>{{ line.unit }}</small>
                  </td><td class="detail-number detail-received">
                    {{ line.receivedQuantity }}
                  </td><td class="detail-number">
                    <strong>{{ line.remainingQuantity }}</strong>
                  </td><td class="detail-number">
                    {{ purchasePrice(line.unitPrice) }}
                  </td><td>{{ line.taxIncluded?`${line.taxRate??1}%`:'不计税' }}</td><td class="purchase-item-remark">
                    {{ line.remark||'—' }}
                  </td><td class="detail-number detail-amount">
                    {{ purchaseLineAmount(line).toFixed(2) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
          <template v-if="tab==='receipts'">
            <p v-if="!receipts.length&&!loading">
              尚未登记收货
            </p><div
              v-for="row in receipts"
              :key="row.receipt.id"
              class="purchase-receipt"
            >
              <strong>{{ beijingDateTime(row.receipt.receivedAt) }} · {{ row.receipt.actorLoginName }}</strong><span>{{ row.shipmentId?shipmentLabel(row.shipmentId):'普通 / 历史收货' }}</span><p
                v-for="line in row.receipt.lines"
                :key="line.orderLineId"
              >
                {{ line.itemName }} × {{ line.quantity }} <PurchaseRelatedItem
                  v-if="line.inventoryTransactionId&&order.orderType!=='MANUAL'"
                  :order-id="id"
                  :item-id="order.lines.find(v=>v.id===line.orderLineId)?.itemId??''"
                  :type="order.orderType"
                  :name="line.itemName"
                  inventory
                />
              </p><small>{{ row.receipt.remark||'无备注' }}</small>
            </div>
          </template>
          <ProductHistoryTable
            v-if="tab==='history'"
            purchase
            :purchase-lines="order?.lines"
            :entries="events"
            :loading="loading"
            :error="error"
            @retry="load"
          />
        </div>
        <ListPagination
          v-if="tab!=='lines'"
          v-model:page="page"
          v-model:page-size="size"
          :total="total"
          :disabled="loading"
        />
      </template>
    </template>
    <AccessDialog
      v-if="action&&!showReceive&&!showCancel"
      title="采购操作"
      :busy="loading"
      @close="emit('close')"
    >
      <p>{{ loading?'正在读取…':error }}</p>
    </AccessDialog>
    <PurchaseReceiveFlow
      v-if="showReceive"
      :id="id"
      ref="receiveFlow"
      @close="showReceive=false;if(action)emit('close')"
      @completed="completed"
      @updated="receiptUpdated"
    />
    <AccessDialog
      v-if="showCancel&&order"
      title="关闭未收余量"
      :busy="saving"
      :dirty="!!cancelReason"
      @close="showCancel=false;if(action)emit('close')"
    >
      <p class="purchase-warning">
        关闭后停止收货；已收货记录和库存保留。此操作不能撤回。
      </p><p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p><label>关闭原因<textarea
        v-model="cancelReason"
        maxlength="500"
        rows="3"
        :disabled="saving"
      /></label><template #footer>
        <button
          :disabled="saving||!cancelReason.trim()"
          @click="cancel"
        >
          确认关闭
        </button>
      </template>
    </AccessDialog>
    <PurchaseShipmentsDialog
      v-if="shipments"
      :id="id"
      :writable="session.canAccess(['purchases.write'])"
      @close="shipments=false;load()"
    />
    <SupplierDetailDialog
      v-if="supplier&&order?.supplierId"
      :id="order.supplierId"
      @close="supplier=false"
    />
  </section>
</template>
