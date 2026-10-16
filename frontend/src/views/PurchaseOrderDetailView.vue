<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { purchaseApi, type PurchaseOrderDetail, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const route = useRoute(); const detail = ref<PurchaseOrderDetail>(); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const showReceipt = ref(false); const remark = ref('')
const quantities = reactive<Record<string, number | ''>>({})
const receiptError = ref(''); const receivedAt = ref(''); const cancelReason = ref(''); const showCancel = ref(false)
type ReceiptPayload = Parameters<typeof purchaseApi.receive>[1]
const pendingReceipt = ref<ReceiptPayload>()
const receiptUncertain = ref(false)
const returnPath = computed(() => ({ path: '/purchases/orders', query: { type: detail.value?.order.orderType || 'PRODUCT' } }))
const writable = computed(() => session.canAccess(['purchases.write'])); const receivable = computed(() => session.canAccess(['purchases.receive']))
const statusLabels: Record<PurchaseStatus,string> = { DRAFT: '草稿', SUBMITTED: '待收货', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消 / 余量关闭' }
const actionLabels: Record<string,string> = { PURCHASE_ORDER_CREATE: '创建采购单', PURCHASE_ORDER_UPDATE: '编辑采购单', PURCHASE_ORDER_SUBMIT: '提交采购单', PURCHASE_ORDER_CANCEL: '取消采购单', PURCHASE_RECEIPT_CREATE: '采购收货' }
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
let loadSequence = 0
async function load() {
  const sequence = ++loadSequence; loading.value = true; error.value = ''
  try { const result = await purchaseApi.detail(String(route.params.orderId)); if (sequence === loadSequence) detail.value = result }
  catch (exception) { if (sequence === loadSequence) error.value = message(exception, '采购单详情加载失败，请刷新重试。') }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function submitOrder() {
  if (!detail.value || saving.value || !window.confirm('确认下单后将锁定供应商、项目、数量和价格。请确认已与供应商核对；系统不会自动向供应商发送订单。')) return
  saving.value = true; error.value = ''; success.value = ''
  try { await purchaseApi.submit(detail.value.order.id, detail.value.order.version); success.value = '采购单已确认，可分批登记收货。'; await load() }
  catch (exception) { error.value = message(exception, '下单结果未确认，请刷新单据检查状态。') }
  finally { saving.value = false }
}
async function cancelOrder() {
  if (!detail.value || saving.value) return
  if (!cancelReason.value.trim()) { error.value = '请填写取消 / 关闭原因。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try { await purchaseApi.cancel(detail.value.order.id, detail.value.order.version, cancelReason.value.trim()); showCancel.value = false; success.value = '未收余量已关闭；已有收货和库存保持不变。'; await load() }
  catch (exception) { error.value = message(exception, '取消结果未确认，请刷新单据检查状态。') }
  finally { saving.value = false }
}
function openReceipt() {
  if (!detail.value || saving.value) return
  if (!pendingReceipt.value) {
    Object.keys(quantities).forEach(key => delete quantities[key]); remark.value = ''; receiptError.value = ''
    const now = new Date(); receivedAt.value = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
  }
  showReceipt.value = true
}
function closeReceipt() { if (!saving.value) showReceipt.value = false }
async function receive() {
  if (!detail.value || saving.value) return
  receiptError.value = ''
  if (!pendingReceipt.value) {
    const available = detail.value.order.lines.filter(line => line.remainingQuantity > 0)
    if (available.some(line => quantities[line.id] !== undefined && quantities[line.id] !== '' && (!Number.isFinite(Number(quantities[line.id])) || Number(quantities[line.id]) < 0 || Number(quantities[line.id]) > line.remainingQuantity))) { receiptError.value = '实收数量必须在 0 与待收数量之间。'; return }
    const lines = available.filter(line => Number(quantities[line.id]) > 0).map(line => ({ orderLineId: line.id, quantity: Number(quantities[line.id]) }))
    if (!lines.length) { receiptError.value = '请至少填写一项本次实收数量。'; return }
    const date = new Date(receivedAt.value)
    if (!Number.isFinite(date.getTime()) || date.getTime() > Date.now() + 300000) { receiptError.value = '请填写有效且不在未来的实际收货时间。'; return }
    pendingReceipt.value = { requestId: crypto.randomUUID(), receivedAt: date.toISOString(), ...(remark.value ? { remark: remark.value } : {}), lines }
  }
  saving.value = true
  try {
    await purchaseApi.receive(detail.value.order.id, pendingReceipt.value)
    pendingReceipt.value = undefined; receiptUncertain.value = false; showReceipt.value = false; success.value = '本次收货已入账。'; await load()
  } catch (exception) {
    if (exception instanceof ApiError && exception.status >= 400 && exception.status < 500) {
      pendingReceipt.value = undefined; receiptUncertain.value = false; receiptError.value = exception.message
    } else { receiptUncertain.value = true; receiptError.value = '收货结果尚未确认，请保持本页并点击“重试本次收货”。将使用原请求号核对，不会重复入账。' }
  } finally { saving.value = false }
}
watch(() => route.params.orderId, () => { detail.value = undefined; showReceipt.value = false; showCancel.value = false; pendingReceipt.value = undefined; receiptUncertain.value = false; success.value = ''; void load() }, { immediate: true })
function mayLeave() { return !saving.value && (!pendingReceipt.value || window.confirm('本次收货结果未确认。离开后请先核对收货历史，勿直接重复登记。确定离开？')) }
function beforeUnload(event: BeforeUnloadEvent) { if (saving.value || pendingReceipt.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(mayLeave); onBeforeRouteUpdate(mayLeave)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="page-stack purchase-page">
    <header class="panel purchase-header">
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
          返回采购单
        </RouterLink>
      </div>
    </header>
    <p
      v-if="error"
      class="error-message"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="success"
      class="success-message"
      role="status"
    >
      {{ success }}
    </p><p v-if="loading">
      正在加载…
    </p>
    <template v-else-if="detail">
      <section class="panel purchase-summary">
        <dl>
          <div><dt>状态</dt><dd>{{ statusLabels[detail.order.status] }}</dd></div><div><dt>采购类型</dt><dd>{{ detail.order.orderType === 'PRODUCT' ? '产品采购' : '辅料采购' }}</dd></div><div><dt>供应商</dt><dd>{{ detail.order.supplierName }}</dd></div><div><dt>采购金额</dt><dd>{{ detail.order.currency }} {{ detail.order.lines.reduce((sum,line) => sum + line.orderedQuantity * line.unitPrice, 0).toFixed(2) }}</dd></div><div><dt>创建时间</dt><dd>{{ new Date(detail.order.createdAt).toLocaleString() }}</dd></div><div><dt>预计到货日期</dt><dd>{{ detail.order.expectedDeliveryDate || '未约定' }}</dd></div><div><dt>收货信息</dt><dd>{{ detail.order.deliveryAddress || '未填写' }}</dd></div><div><dt>备注</dt><dd>{{ detail.order.remark || '—' }}</dd></div><div v-if="detail.order.status === 'CANCELLED'">
            <dt>取消 / 关闭原因</dt><dd>{{ detail.order.cancellationReason || '历史单据未记录' }}</dd>
          </div>
        </dl><div class="purchase-detail-actions">
          <button
            v-if="writable && detail.order.status === 'DRAFT'"
            :disabled="saving"
            type="button"
            @click="submitOrder"
          >
            确认下单
          </button><button
            v-if="receivable && ['SUBMITTED','PARTIALLY_RECEIVED'].includes(detail.order.status)"
            :disabled="saving"
            type="button"
            @click="openReceipt"
          >
            登记收货
          </button><button
            v-if="writable && !['COMPLETED','CANCELLED'].includes(detail.order.status)"
            :disabled="saving || Boolean(pendingReceipt)"
            type="button"
            class="secondary-button"
            @click="showCancel = true; cancelReason = ''; error = ''"
          >
            {{ detail.order.status === 'PARTIALLY_RECEIVED' ? '关闭未收余量' : '取消采购单' }}
          </button>
        </div>
      </section>
      <section class="panel">
        <h2>采购明细</h2><div class="table-scroll">
          <table>
            <thead><tr><th>项目</th><th>单位</th><th>采购数量</th><th>已收</th><th>{{ detail.order.status === 'CANCELLED' ? '已关闭余量' : '待收' }}</th><th>单价</th><th>金额</th></tr></thead><tbody>
              <tr
                v-for="line in detail.order.lines"
                :key="line.id"
              >
                <td><strong>{{ line.itemCode }}</strong><small>{{ line.itemName }}</small></td><td>{{ line.unit }}</td><td>{{ line.orderedQuantity }}</td><td>{{ line.receivedQuantity }}</td><td>{{ line.remainingQuantity }}</td><td>{{ detail.order.currency }} {{ line.unitPrice.toFixed(2) }}</td><td>{{ detail.order.currency }} {{ (line.orderedQuantity * line.unitPrice).toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
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
              <strong>{{ new Date(receipt.receivedAt).toLocaleString() }}</strong><span>{{ receipt.actorLoginName }} · {{ receipt.lines.map(line => `${line.itemCode} × ${line.quantity}`).join('；') }}</span><small>{{ receipt.remark || '无备注' }}</small>
            </li>
          </ol>
        </article><article class="panel">
          <h2>操作历史</h2><ol class="purchase-timeline">
            <li
              v-for="event in detail.history"
              :key="event.id"
            >
              <strong>{{ actionLabels[event.action] || event.action }}</strong><span>{{ event.actorDisplayName || event.actorLoginName }}</span><small>{{ new Date(event.occurredAt).toLocaleString() }}</small>
            </li>
          </ol>
        </article>
      </section>
    </template>
    <div
      v-if="showReceipt && detail"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="receipt-title"
      @click.self="closeReceipt"
      @keydown.esc="closeReceipt"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="receive"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭"
          :disabled="saving"
          @click="closeReceipt"
        >
          ×
        </button><h2 id="receipt-title">
          登记收货 · {{ detail.order.orderNo }}
        </h2><p class="form-wide purchase-form-hint">
          只填写本次实际收到的数量；留空或 0 不入账。{{ detail.order.orderType === 'MATERIAL' ? '辅料收货将同步增加固定仓库存。' : '产品收货只记录到货数量，不生成成品库存。' }}
        </p><p
          v-if="receiptError"
          class="form-wide error-message"
          role="alert"
        >
          {{ receiptError }}
        </p>
        <label class="form-wide">实际收货时间<input
          v-model="receivedAt"
          type="datetime-local"
          step="1"
          required
          :disabled="saving || receiptUncertain"
        ></label>
        <label
          v-for="line in detail.order.lines.filter(item => item.remainingQuantity > 0)"
          :key="line.id"
          class="form-wide"
        >{{ line.itemCode }} · 待收 {{ line.remainingQuantity }} {{ line.unit }}<input
          v-model.number="quantities[line.id]"
          :disabled="saving || receiptUncertain"
          type="number"
          min="0"
          :max="line.remainingQuantity"
          step="0.0001"
        ></label><label class="form-wide">本次收货备注<textarea
          v-model="remark"
          :disabled="saving || receiptUncertain"
          maxlength="500"
        /></label><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="closeReceipt"
          >
            取消
          </button><button
            :disabled="saving"
            type="submit"
          >
            {{ receiptUncertain ? '重试本次收货' : '确认本次收货' }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="showCancel && detail"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-title"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="cancelOrder"
      >
        <h2 id="cancel-title">
          取消采购单 / 关闭未收余量
        </h2>
        <p class="form-wide purchase-form-hint">
          此操作不可撤回，单据将停止收货；已收货数量、历史记录和库存不会撤销。需要退货时请另行处理，不要用取消代替退货。
        </p>
        <p
          v-if="error"
          class="form-wide error-message"
          role="alert"
        >
          {{ error }}
        </p>
        <label class="form-wide">取消 / 关闭原因<textarea
          v-model="cancelReason"
          required
          maxlength="500"
          :disabled="saving"
        /></label>
        <div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="showCancel = false"
          >
            返回
          </button><button
            type="submit"
            :disabled="saving || !cancelReason.trim()"
          >
            确认关闭
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
