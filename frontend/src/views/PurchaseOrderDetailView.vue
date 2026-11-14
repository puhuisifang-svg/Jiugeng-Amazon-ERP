<script setup lang="ts">
import { beijingDateTime, beijingDateTimeInput, beijingInputToIso } from '@/utils/dateTime'


import { purchaseLineAmount } from '@/ui/purchaseAmounts'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import { createRequestId } from '@/utils/requestId'

import DatePicker from '@/components/DatePicker.vue'
import PurchaseReceiptDialog from '@/components/PurchaseReceiptDialog.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import { confirmAction } from '@/state/uiConfirmation'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { purchaseApi, type PurchaseOrderDetail, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const props=defineProps<{orderId?:string;embedded?:boolean;action?:'receive'|'cancel'}>()
const emit = defineEmits<{close:[];completed:[]}>()
const route = useRoute(); const detail = ref<PurchaseOrderDetail>(); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const showReceipt = ref(false); const remark = ref('')
const quantities = reactive<Record<string, number | ''>>({})
const receiptError = ref(''); const receivedAt = ref(''); const cancelReason = ref(''); const showCancel = ref(false)
type ReceiptPayload = Parameters<typeof purchaseApi.receive>[1]
const pendingReceipt = ref<ReceiptPayload>()
const receiptUncertain = ref(false)
const returnPath = computed(() => ({ path: '/purchases/orders', query: { type: detail.value?.order.orderType || 'PRODUCT' } }))
const receivable = computed(() => session.canAccess(['purchases.receive']))
const statusLabels: Record<PurchaseStatus,string> = { DRAFT: '草稿', SUBMITTED: '待收货', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消 / 余量关闭' }
const actionLabels: Record<string,string> = { PURCHASE_ORDER_CREATE: '创建采购单', PURCHASE_ORDER_UPDATE: '编辑采购单', PURCHASE_LOGISTICS_UPDATE: '更新物流资料', PURCHASE_ORDER_SUBMIT: '提交采购单', PURCHASE_ORDER_CANCEL: '取消采购单', PURCHASE_RECEIPT_CREATE: '采购收货' }
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
let loadSequence = 0
async function load() {
  const sequence = ++loadSequence; loading.value = true; error.value = ''
  try { const result = await purchaseApi.detail(String(props.orderId??route.params.orderId)); if (sequence === loadSequence) detail.value = result }
  catch (exception) { if (sequence === loadSequence) error.value = message(exception, '采购单详情加载失败，请刷新重试。') }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function submitOrder() {
  if (!detail.value || saving.value || !await confirmAction('确认下单后将锁定供应商、项目、数量和价格。请确认已与供应商核对；系统不会自动向供应商发送订单。')) return
  saving.value = true; error.value = ''; success.value = ''
  try { await purchaseApi.submit(detail.value.order.id, detail.value.order.version); success.value = '采购单已确认，可分批登记收货。'; await load() }
  catch (exception) { error.value = message(exception, '下单结果未确认，请刷新单据检查状态。') }
  finally { saving.value = false }
}
async function cancelOrder() {
  if (!detail.value || saving.value) return
  if (!cancelReason.value.trim()) { error.value = '请填写取消 / 关闭原因。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try { await purchaseApi.cancel(detail.value.order.id, detail.value.order.version, cancelReason.value.trim()); showCancel.value = false; success.value = '未收余量已关闭；已有收货和库存保持不变。'; if(props.action) emit('completed'); else await load() }
  catch (exception) { error.value = message(exception, '取消结果未确认，请刷新单据检查状态。') }
  finally { saving.value = false }
}
function openReceipt() {
  if (!detail.value || saving.value) return
  if (!pendingReceipt.value) {
    Object.keys(quantities).forEach(key => delete quantities[key]); remark.value = ''; receiptError.value = ''
    receivedAt.value = beijingDateTimeInput()
  }
  showReceipt.value = true
}
async function closeReceipt() { if (await mayLeave()) { showReceipt.value = false; if(props.action) emit('close') } }
function closeCancel() { if(!saving.value) { showCancel.value=false; if(props.action) emit('close') } }
async function receive() {
  if (!detail.value || saving.value) return
  success.value = ''
  receiptError.value = ''
  if (!pendingReceipt.value) {
    const available = detail.value.order.lines.filter(line => line.remainingQuantity > 0)
    if (available.some(line => quantities[line.id] !== undefined && quantities[line.id] !== '' && (!Number.isFinite(Number(quantities[line.id])) || Number(quantities[line.id]) < 0 || Number(quantities[line.id]) > line.remainingQuantity))) { receiptError.value = '实收数量必须在 0 与待收数量之间。'; return }
    const lines = available.filter(line => Number(quantities[line.id]) > 0).map(line => ({ orderLineId: line.id, quantity: Number(quantities[line.id]) }))
    if (!lines.length) { receiptError.value = '请至少填写一项本次实收数量。'; return }
    const date = new Date(beijingInputToIso(receivedAt.value))
    if (!Number.isFinite(date.getTime()) || date.getTime() > Date.now() + 300000) { receiptError.value = '请填写有效且不在未来的实际收货时间。'; return }
    pendingReceipt.value = { requestId: createRequestId(), receivedAt: date.toISOString(), ...(remark.value ? { remark: remark.value } : {}), lines }
  }
  saving.value = true
  try {
    await purchaseApi.receive(detail.value.order.id, pendingReceipt.value)
    pendingReceipt.value = undefined; receiptUncertain.value = false; showReceipt.value = false; success.value = '本次收货已入账。'; if(props.action) emit('completed'); else await load()
  } catch (exception) {
    if (exception instanceof ApiError && exception.status >= 400 && exception.status < 500) {
      pendingReceipt.value = undefined; receiptUncertain.value = false; receiptError.value = exception.message
    } else { receiptUncertain.value = true; receiptError.value = '收货结果尚未确认，请保持本页并点击“重试本次收货”。将使用原请求号核对，不会重复入账。' }
  } finally { saving.value = false }
}
function openRequestedAction() {
  if(!detail.value || !props.action) return
  if(props.action==='receive' && receivable.value && ['SUBMITTED','PARTIALLY_RECEIVED'].includes(detail.value.order.status)) openReceipt()
  else if(props.action==='cancel' && session.canAccess(['purchases.cancel']) && !['COMPLETED','CANCELLED'].includes(detail.value.order.status)) {cancelReason.value='';showCancel.value=true}
  else error.value='单据状态或权限已变化，请关闭并刷新列表。'
}
watch(() => props.orderId??route.params.orderId, async () => {
  detail.value = undefined; showReceipt.value = false; showCancel.value = false; pendingReceipt.value = undefined; receiptUncertain.value = false; success.value = ''
  await load(); openRequestedAction()
}, { immediate: true })
async function mayLeave() { return !saving.value && (!pendingReceipt.value || await confirmAction('本次收货结果未确认。离开后请先核对收货历史，勿直接重复登记。确定离开？')) }
function beforeUnload(event: BeforeUnloadEvent) { if (saving.value || pendingReceipt.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(mayLeave); onBeforeRouteUpdate(mayLeave)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
defineExpose({mayLeave})
</script>

<template>
  <section
    class="page-stack purchase-page"
    :class="{'purchase-detail-embedded':embedded}"
  >
    <template v-if="!action">
      <header
        v-if="!embedded"
        class="panel purchase-header"
      >
        <div>
          <p class="eyebrow">
            PURCHASE ORDER
          </p><h1>{{ detail?.order.orderNo || '采购单详情' }}</h1><p>采购单、收货记录与操作历史统一展示。</p>
        </div><div class="purchase-detail-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving || loading"
            @click="load"
          >
            刷新单据
          </button><RouterLink
            class="secondary-action"
            :to="returnPath"
          >
            返回采购管理
          </RouterLink>
        </div>
      </header>
      <p
        v-if="error"
        class="error-message"
        role="alert"
      >
        {{ error }}
      </p><SuccessToast :message="success" /><p v-if="loading">
        正在加载…
      </p>
      <template v-else-if="detail">
        <section class="panel purchase-summary">
          <dl>
            <div><dt>状态</dt><dd>{{ statusLabels[detail.order.status] }}</dd></div><div><dt>采购类型</dt><dd>{{ detail.order.orderType === 'MANUAL' ? '日常物品采购' : detail.order.orderType === 'PRODUCT' ? '产品采购' : '辅料采购' }}</dd></div><div><dt>供应商</dt><dd>{{ detail.order.supplierName }}</dd></div><div><dt>采购金额</dt><dd>{{ detail.order.currency }} {{ detail.order.lines.reduce((sum,line) => sum + purchaseLineAmount(line), 0).toFixed(2) }}</dd></div><div><dt>创建时间</dt><dd>{{ beijingDateTime(detail.order.createdAt) }}</dd></div><div><dt>预计到货日期</dt><dd>{{ detail.order.expectedDeliveryDate || '未约定' }}</dd></div><div><dt>物流公司</dt><dd>{{ detail.order.carrier || '未填写' }}</dd></div><div><dt>运单号</dt><dd>{{ detail.order.trackingNumber || '未填写' }}</dd></div><div><dt>收货信息</dt><dd>{{ detail.order.deliveryAddress || '未填写' }}</dd></div><div><dt>备注</dt><dd>{{ detail.order.remark || '—' }}</dd></div><div v-if="detail.order.status === 'CANCELLED'">
              <dt>取消 / 关闭原因</dt><dd>{{ detail.order.cancellationReason || '历史单据未记录' }}</dd>
            </div>
          </dl><div class="purchase-detail-actions">
            <button
              v-if="!embedded && session.canAccess(['purchases.submit']) && detail.order.status === 'DRAFT'"
              :disabled="saving"
              type="button"
              @click="submitOrder"
            >
              确认下单
            </button>
          </div>
        </section>
        <section class="panel">
          <h2>采购明细</h2><div class="table-scroll">
            <TableViewport>
              <table class="data-table">
                <thead><tr><th>图片</th><th>项目</th><th>单位</th><th>采购数量</th><th>包裹数</th><th>已收</th><th>{{ detail.order.status === 'CANCELLED' ? '已关闭余量' : '待收' }}</th><th>不含税单价</th><th>是否含税 / 税率</th><th>金额</th></tr></thead><tbody>
                  <tr
                    v-for="line in detail.order.lines"
                    :key="line.id"
                  >
                    <td>
                      <PurchaseItemImage
                        :image-asset-id="line.imageAssetId"
                        :image-url="line.imageUrl"
                        :alt="line.itemName"
                      />
                    </td><td><strong>{{ line.itemName }}</strong><small>{{ line.specification || '—' }}</small></td><td>{{ line.unit }}</td><td>{{ line.orderedQuantity }}</td><td>{{ line.packageCount ?? '—' }}</td><td>{{ line.receivedQuantity }}</td><td>{{ line.remainingQuantity }}</td><td>{{ detail.order.currency }} {{ line.unitPrice.toFixed(2) }}</td><td>{{ line.taxIncluded ? `是 / ${line.taxRate ?? 1}%` : '否' }}</td><td>{{ detail.order.currency }} {{ (purchaseLineAmount(line)).toFixed(2) }}</td>
                  </tr>
                </tbody>
              </table>
            </TableViewport>
          </div>
        </section>
        <section class="purchase-detail-grid">
          <article class="panel">
            <h2>收货记录</h2><div
              v-if="!detail.receipts.length"
              class="empty-state empty-state--compact"
            >
              <p>尚未收货。</p>
            </div><ol
              v-else
              class="purchase-timeline"
            >
              <li
                v-for="receipt in detail.receipts"
                :key="receipt.id"
              >
                <strong>{{ beijingDateTime(receipt.receivedAt) }}</strong><span>{{ receipt.actorLoginName }} · {{ receipt.lines.map(line => `${line.itemName} × ${line.quantity}`).join('；') }}</span><small>{{ receipt.remark || '无备注' }}</small>
              </li>
            </ol>
          </article><article class="panel">
            <h2>操作历史</h2><ol class="purchase-timeline">
              <li
                v-for="event in detail.history"
                :key="event.id"
              >
                <strong>{{ actionLabels[event.action] || event.action }}</strong><span>{{ event.actorDisplayName || event.actorLoginName }}</span><small>{{ beijingDateTime(event.occurredAt) }}</small>
              </li>
            </ol>
          </article>
        </section>
      </template>
    </template>
    <AccessDialog
      v-if="action && !showReceipt && !showCancel"
      title="采购操作"
      :busy="loading || saving"
      @close="emit('close')"
    >
      <p role="status">
        {{ loading ? '正在读取采购单…' : error }}
      </p><button
        v-if="!loading"
        type="button"
        @click="emit('close')"
      >
        关闭并刷新列表
      </button>
    </AccessDialog>
    <PurchaseReceiptDialog
      v-if="showReceipt && detail"
      :order="detail.order"
      :quantities="quantities"
      :remark="remark"
      :receipt-error="receiptError"
      :locked="saving || receiptUncertain"
      :saving="saving"
      :pending="receiptUncertain"
      @close="closeReceipt"
      @submit="receive"
      @quantity="(id,value)=>quantities[id]=value===''?'':Number(value)"
      @remark="remark=$event"
    >
      <label class="receipt-date">实际收货时间<DatePicker
        v-model="receivedAt"
        type="datetime-local"
        step="1"
        required
        :disabled="saving || receiptUncertain"
      /></label>
    </PurchaseReceiptDialog>
    <AccessDialog
      v-if="showCancel && detail"
      title="取消采购单 / 关闭未收余量"
      :busy="saving"
      @close="closeCancel"
    >
      <p class="purchase-cancel-notice">
        此操作不可撤回，单据将停止收货；已收货数量、历史记录和库存不会撤销。需要退货时请另行处理，不要用取消代替退货。
      </p>
      <p
        v-if="error"
        class="error-message"
        role="alert"
      >
        {{ error }}
      </p>
      <label class="purchase-cancel-reason">取消 / 关闭原因<textarea
        v-model="cancelReason"
        required
        maxlength="500"
        :disabled="saving"
        placeholder="请填写取消原因"
      /></label>
      <template #footer>
        <button
          type="button"
          class="secondary-button"
          :disabled="saving"
          @click="closeCancel"
        >
          返回
        </button><button
          type="button"
          class="purchase-cancel-button"
          :disabled="saving || !cancelReason.trim()"
          @click="cancelOrder"
        >
          {{ saving ? '处理中…' : '确认关闭' }}
        </button>
      </template>
    </AccessDialog>
  </section>
</template>

<style scoped>
.purchase-cancel-button{background:#fff1f0!important;color:#b42318!important;border:1px solid #f1c6c2!important;box-shadow:none;min-height:32px;padding:6px 12px;border-radius:4px;font-size:13px}.purchase-cancel-button:hover:not(:disabled){background:#ffe4e1!important;border-color:#df8f87!important}.purchase-cancel-button:disabled{opacity:.5;cursor:not-allowed}.purchase-cancel-notice{background:#fff8ed;border:1px solid #f1dec0;border-radius:4px;padding:10px 12px;font-size:13px;line-height:1.7;color:#8a581e}.purchase-cancel-reason{display:flex;flex-direction:column;gap:8px;font-size:13px}.purchase-cancel-reason textarea{min-height:96px;box-sizing:border-box;width:100%;border:1px solid var(--line);border-radius:4px;padding:8px 10px;font:inherit;resize:vertical}.receipt-date{white-space:nowrap;display:flex;align-items:center;gap:10px;margin-top:12px;font-size:13px}

.purchase-detail-embedded .panel{border:1px solid var(--line);box-shadow:none;padding:12px;border-radius:6px;background:#fff}
.purchase-detail-embedded .purchase-summary{display:flex;flex-direction:column;gap:10px}
.purchase-detail-embedded .purchase-summary dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px 18px;margin:0}
.purchase-detail-embedded .purchase-summary dl>div{display:flex;flex-direction:row;align-items:baseline;gap:8px;min-width:0}
.purchase-detail-embedded dt{color:var(--muted);font-size:12px;white-space:nowrap;font-weight:400}
.purchase-detail-embedded dd{margin:0;padding:0;background:none;border:0;font-size:13px;overflow-wrap:anywhere}
.purchase-detail-embedded .purchase-detail-actions{display:flex;justify-content:flex-end;gap:8px;border-top:1px solid var(--line);padding-top:10px}
.purchase-detail-embedded h2{font-size:14px;margin:0 0 10px}
.purchase-detail-embedded .data-table{min-width:800px}
.purchase-detail-embedded .purchase-detail-grid{display:grid;grid-template-columns:2fr 1fr;gap:12px}
.purchase-detail-embedded .purchase-timeline{list-style:none;padding:0;margin:0;max-height:240px;overflow:auto}
.purchase-detail-embedded .purchase-timeline li{display:grid;gap:5px;padding:10px 12px;background:#f7f9fc;border:1px solid var(--line);border-radius:4px;margin-bottom:8px}
.purchase-detail-embedded .purchase-timeline strong{font-size:13px;color:#334155}
.purchase-detail-embedded .purchase-timeline small{color:var(--muted)}
@media(max-width:760px){.purchase-detail-embedded .purchase-summary dl{grid-template-columns:repeat(2,minmax(0,1fr))}.purchase-detail-embedded .purchase-detail-grid{grid-template-columns:1fr}}
@media(max-width:480px){.purchase-detail-embedded .purchase-summary dl{grid-template-columns:1fr}}
</style>
