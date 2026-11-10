<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink } from 'vue-router'
import { purchaseApi, type PurchaseOrder } from '@/api/purchases'
import { ApiError } from '@/api/client'
import { session } from '@/state/session'
import { createRequestId } from '@/utils/requestId'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import SuccessToast from './SuccessToast.vue'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ count: [number]; busy: [boolean] }>()
const canRead = computed(() => session.canAccess(['purchases.read']))
const canReceive = computed(() => session.canAccess(['purchases.receive']))
const orders = ref<PurchaseOrder[]>([]), loading = ref(false), saving = ref(false), error = ref(''), success = ref('')
const query = ref(''), page = ref(1), pageSize = ref(10)
const target = ref<PurchaseOrder>(), opening = ref(false), receiptError = ref(''), remark = ref('')
const quantities = reactive<Record<string, number | ''>>({})
type Payload = Parameters<typeof purchaseApi.receive>[1]
const pending = ref<Payload>()
const locked = computed(() => saving.value || !!pending.value)
watch(locked, value => emit('busy', value))
const filtered = computed(() => orders.value.filter(order => [order.orderNo, order.supplierName, order.trackingNumber, ...order.lines.flatMap(line => [line.itemName, line.itemCode])].join(' ').toLowerCase().includes(query.value.trim().toLowerCase())))
const rows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
watch([query, pageSize], () => { page.value = 1 })
watch(() => filtered.value.length, total => { page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize.value))) })
async function reload() {
  if (!canRead.value || loading.value || locked.value) return
  loading.value = true; error.value = ''
  try {
    const results = await Promise.all([purchaseApi.orders('SUBMITTED'), purchaseApi.orders('PARTIALLY_RECEIVED')])
    orders.value = [...new Map(results.flat().filter(order => ['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(order.status)).map(order => [order.id, order])).values()]
      .sort((a, b) => (a.expectedDeliveryDate || '9999').localeCompare(b.expectedDeliveryDate || '9999') || a.createdAt.localeCompare(b.createdAt))
    emit('count', orders.value.length)
  } catch (e) { error.value = e instanceof ApiError ? e.message : '待接收采购单加载失败，请刷新重试。' }
  finally { loading.value = false }
}
async function open(order: PurchaseOrder) {
  if (opening.value || locked.value || !canReceive.value) return
  opening.value = true; error.value = ''
  try {
    const fresh = await purchaseApi.order(order.id)
    if (!['SUBMITTED', 'PARTIALLY_RECEIVED'].includes(fresh.status)) { await reload(); error.value = '采购单状态已变化，已刷新待接收列表。'; return }
    target.value = fresh; receiptError.value = ''; remark.value = ''
    Object.keys(quantities).forEach(key => delete quantities[key])
  } catch (e) { error.value = e instanceof ApiError ? e.message : '采购单读取失败，请重试。' }
  finally { opening.value = false }
}
async function receive() {
  if (!target.value || saving.value || !canReceive.value) return
  receiptError.value = ''
  if (!pending.value) {
    const available = target.value.lines.filter(line => line.remainingQuantity > 0)
    if (available.some(line => {
      const value = quantities[line.id]
      return value !== undefined && value !== '' && (!Number.isFinite(Number(value)) || Number(value) < 0 || Number(value) > line.remainingQuantity || !/^\d+(\.\d{1,4})?$/.test(String(value)))
    })) { receiptError.value = '实收数量须在 0 与待收数量之间，最多四位小数。'; return }
    const lines = available.filter(line => Number(quantities[line.id]) > 0).map(line => ({ orderLineId: line.id, quantity: Number(quantities[line.id]) }))
    if (!lines.length) { receiptError.value = '请至少填写一项本次实收数量。'; return }
    pending.value = { requestId: createRequestId(), receivedAt: new Date().toISOString(), remark: remark.value.trim(), lines }
  }
  const orderId = target.value.id
  saving.value = true
  try {
    await purchaseApi.receive(orderId, pending.value)
    pending.value = undefined; target.value = undefined
    success.value = '收货已确认，采购进度已更新。'
  } catch (e) {
    if (e instanceof ApiError && e.status >= 400 && e.status < 500) {
      pending.value = undefined; receiptError.value = e.message
      // Concurrent receipts may change the remaining quantities.
      try { target.value = await purchaseApi.order(orderId) } catch { /* Keep the original form for correction. */ }
    } else { receiptError.value = '收货结果尚未确认，请点击“重试本次收货”。沿用原请求号核对，不会重复入账。' }
  } finally { saving.value = false }
  if (!target.value) await reload()
}
function close() { if (!locked.value) target.value = undefined }
function beforeUnload(event: BeforeUnloadEvent) { if (locked.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(() => !locked.value)
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
watch(() => props.visible, visible => { if (visible) void reload() }, { immediate: true })
defineExpose({ reload })
</script>

<template>
  <section
    aria-label="采购待接收"
    class="receipt-worklist"
  >
    <SuccessToast :message="success" />
    <p
      v-if="!canRead"
      class="data-state"
    >
      暂无采购查看权限，请联系管理员分配“采购查看”；确认收货还需“采购收货”权限。
    </p>
    <template v-else>
      <div class="receipt-toolbar">
        <label>查找采购单<input
          v-model="query"
          type="search"
          placeholder="采购单号 / 供应商 / SKU / 运单号"
        ></label>
        <span>按实际到货数量确认，支持分批收货。</span>
      </div>
      <p
        v-if="error"
        class="error-message"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="loading"
        class="data-state"
      >
        正在加载待接收采购单…
      </p>
      <TableViewport v-else-if="rows.length">
        <table class="data-table receipt-table">
          <thead><tr><th>采购单 / 供应商</th><th>采购内容</th><th>预计到货</th><th>物流信息</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr
              v-for="order in rows"
              :key="order.id"
            >
              <td>
                <RouterLink :to="`/purchases/orders/${order.id}`">
                  {{ order.orderNo }}
                </RouterLink><small>{{ order.supplierName }}</small><small>{{ order.orderType === 'MANUAL' ? '日常物品采购' : order.orderType === 'MATERIAL' ? '辅料采购' : '产品采购' }}</small>
              </td>
              <td>
                <div
                  v-for="line in order.lines"
                  :key="line.id"
                  class="receipt-item"
                >
                  <PurchaseItemImage
                    :image-asset-id="line.imageAssetId"
                    :image-url="line.imageUrl"
                    :alt="line.itemName"
                  />
                  <div>{{ line.itemName }}<small>{{ line.itemCode || line.specification || '手动物品' }}</small><small>已收 {{ line.receivedQuantity }} / 采购 {{ line.orderedQuantity }} {{ line.unit }} · 待收 <strong>{{ line.remainingQuantity }}</strong> {{ line.unit }}</small></div>
                </div>
              </td>
              <td>{{ order.expectedDeliveryDate || '未约定' }}</td>
              <td>{{ order.carrier || '—' }}<small>{{ order.trackingNumber || '未填写运单号' }}</small></td>
              <td><span class="receipt-status">{{ order.status === 'PARTIALLY_RECEIVED' ? '部分收货' : '待收货' }}</span></td>
              <td>
                <button
                  v-if="canReceive"
                  type="button"
                  class="primary-button"
                  :disabled="opening || locked"
                  @click="open(order)"
                >
                  确认收货
                </button><span v-else>无收货权限</span>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <p
        v-else
        class="data-state"
      >
        {{ query ? '没有匹配的采购单' : '暂无待接收采购单' }}
      </p>
      <ListPagination
        v-if="visible"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
        :disabled="loading || locked"
      />
    </template>
    <AccessDialog
      v-if="target"
      title="确认采购收货"
      wide
      :busy="locked"
      @close="close"
    >
      <div class="receipt-summary">
        <strong>{{ target.orderNo }}</strong><span>{{ target.supplierName }}</span><span>{{ target.deliveryAddress || '未填写收货地址' }}</span>
      </div>
      <p class="receipt-hint">
        核对实物后填写本次实收数量，未到货的项目留空。确认后登记收货；辅料采购同时计入辅料库存。
      </p>
      <TableViewport>
        <table class="data-table receipt-confirm-table">
          <thead><tr><th>图片</th><th>商品 / SKU</th><th>采购</th><th>已收</th><th>待收</th><th>本次实收</th></tr></thead>
          <tbody>
            <tr
              v-for="line in target.lines.filter(item => item.remainingQuantity > 0)"
              :key="line.id"
            >
              <td>
                <PurchaseItemImage
                  :image-asset-id="line.imageAssetId"
                  :image-url="line.imageUrl"
                  :alt="line.itemName"
                />
              </td>
              <td>{{ line.itemName }}<small>{{ line.itemCode || line.specification || '手动物品' }}</small></td><td>{{ line.orderedQuantity }} {{ line.unit }}</td><td>{{ line.receivedQuantity }}</td><td>{{ line.remainingQuantity }}</td>
              <td>
                <input
                  v-model="quantities[line.id]"
                  type="number"
                  min="0"
                  :max="line.remainingQuantity"
                  step="0.0001"
                  :aria-label="`${line.itemCode}本次实收`"
                  :disabled="locked"
                  placeholder="填写实收数量"
                >
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <label class="receipt-remark">收货备注<input
        v-model="remark"
        :disabled="locked"
        maxlength="500"
        placeholder="可选，如外包装情况、少货说明"
      ></label>
      <p
        v-if="receiptError"
        role="alert"
        class="error-message"
      >
        {{ receiptError }}
      </p>
      <template #footer>
        <button
          type="button"
          class="secondary-button"
          :disabled="locked"
          @click="close"
        >
          取消
        </button><button
          type="button"
          class="primary-button"
          :disabled="saving"
          @click="receive"
        >
          {{ saving ? '确认中…' : pending ? '重试本次收货' : '确认本次收货' }}
        </button>
      </template>
    </AccessDialog>
  </section>
</template>
<style scoped>
.receipt-worklist { min-width:0; }
.receipt-toolbar { display:flex; align-items:center; gap:12px; flex-wrap:wrap; padding:10px; color:var(--muted); font-size:13px; }
.receipt-toolbar label { display:flex; align-items:center; gap:8px; color:var(--ink); }
.receipt-toolbar input { width:280px; max-width:60vw; }
input { min-height:32px; border:1px solid var(--line); border-radius:4px; padding:5px 8px; font:inherit; box-sizing:border-box; }
.receipt-table { min-width:950px; }
.receipt-table td { vertical-align:top; }
small { display:block; color:var(--muted); margin-top:3px; font-size:12px; }
.receipt-item { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
.receipt-item:last-child { margin-bottom:0; }
.receipt-status { color:#a35b00; background:#fff4df; border-radius:4px; padding:3px 7px; white-space:nowrap; }
.receipt-summary { display:flex; flex-wrap:wrap; gap:8px 20px; font-size:13px; }
.receipt-hint { color:var(--muted); font-size:13px; }
.receipt-confirm-table { min-width:660px; }
.receipt-confirm-table input { width:140px; }
.receipt-remark { display:flex; gap:10px; align-items:center; margin-top:12px; font-size:13px; }
.receipt-remark input { flex:1; min-width:0; }
</style>
