<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { purchaseApi, type PurchaseOrderDetail, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const route = useRoute(); const detail = ref<PurchaseOrderDetail>(); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const showReceipt = ref(false); const remark = ref('')
const quantities = reactive<Record<string, number>>({})
const writable = computed(() => session.canAccess(['purchases.write'])); const receivable = computed(() => session.canAccess(['purchases.receive']))
const statusLabels: Record<PurchaseStatus,string> = { DRAFT: '草稿', SUBMITTED: '已提交', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消' }
const actionLabels: Record<string,string> = { PURCHASE_ORDER_CREATE: '创建采购单', PURCHASE_ORDER_UPDATE: '编辑采购单', PURCHASE_ORDER_SUBMIT: '提交采购单', PURCHASE_ORDER_CANCEL: '取消采购单', PURCHASE_RECEIPT_CREATE: '采购收货' }
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
async function load() { loading.value = true; try { detail.value = await purchaseApi.detail(String(route.params.orderId)) } catch (exception) { error.value = message(exception, '采购单详情加载失败。') } finally { loading.value = false } }
async function submitOrder() { if (!detail.value) return; saving.value = true; try { await purchaseApi.submit(detail.value.order.id, detail.value.order.version); success.value = '采购单已提交，可开始收货。'; await load() } catch (exception) { error.value = message(exception, '提交失败。') } finally { saving.value = false } }
async function cancelOrder() { if (!detail.value || !window.confirm(`确认取消采购单 ${detail.value.order.orderNo}？`)) return; saving.value = true; try { await purchaseApi.cancel(detail.value.order.id, detail.value.order.version); success.value = '采购单已取消。'; await load() } catch (exception) { error.value = message(exception, '取消失败。') } finally { saving.value = false } }
function openReceipt() { if (!detail.value) return; Object.keys(quantities).forEach(key => delete quantities[key]); detail.value.order.lines.filter(line => line.remainingQuantity > 0).forEach(line => { quantities[line.id] = 0 }); remark.value = ''; showReceipt.value = true }
async function receive() { if (!detail.value) return; const lines = detail.value.order.lines.filter(line => Number(quantities[line.id]) > 0).map(line => ({ orderLineId: line.id, quantity: Number(quantities[line.id]) })); if (!lines.length) { error.value = '请至少填写一项本次实收数量。'; return } saving.value = true; try { await purchaseApi.receive(detail.value.order.id, { requestId: crypto.randomUUID(), receivedAt: new Date().toISOString(), ...(remark.value ? { remark: remark.value } : {}), lines }); showReceipt.value = false; success.value = '本次收货已入账。'; await load() } catch (exception) { error.value = message(exception, '收货失败。') } finally { saving.value = false } }
onMounted(load)
</script>

<template>
  <section class="page-stack purchase-page">
    <header class="panel purchase-header">
      <div>
        <p class="eyebrow">
          PURCHASE ORDER
        </p><h1>{{ detail?.order.orderNo || '采购单详情' }}</h1><p>采购单、收货记录与操作历史统一展示。</p>
      </div><RouterLink
        class="secondary-action"
        to="/purchases/orders"
      >
        返回采购单
      </RouterLink>
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
        <dl><div><dt>状态</dt><dd>{{ statusLabels[detail.order.status] }}</dd></div><div><dt>采购类型</dt><dd>{{ detail.order.orderType === 'PRODUCT' ? '产品采购' : '辅料采购' }}</dd></div><div><dt>供应商</dt><dd>{{ detail.order.supplierName }}</dd></div><div><dt>币种</dt><dd>{{ detail.order.currency }}</dd></div><div><dt>创建时间</dt><dd>{{ new Date(detail.order.createdAt).toLocaleString() }}</dd></div><div><dt>备注</dt><dd>{{ detail.order.remark || '—' }}</dd></div></dl><div class="purchase-detail-actions">
          <button
            v-if="writable && detail.order.status === 'DRAFT'"
            :disabled="saving"
            type="button"
            @click="submitOrder"
          >
            提交采购单
          </button><button
            v-if="receivable && ['SUBMITTED','PARTIALLY_RECEIVED'].includes(detail.order.status)"
            :disabled="saving"
            type="button"
            @click="openReceipt"
          >
            登记收货
          </button><button
            v-if="writable && !['COMPLETED','CANCELLED'].includes(detail.order.status)"
            :disabled="saving"
            type="button"
            class="secondary-button"
            @click="cancelOrder"
          >
            取消采购单
          </button>
        </div>
      </section>
      <section class="panel">
        <h2>采购明细</h2><div class="table-scroll">
          <table>
            <thead><tr><th>项目</th><th>单位</th><th>采购数量</th><th>已收</th><th>待收</th><th>单价</th><th>金额</th></tr></thead><tbody>
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
      @click.self="showReceipt = false"
      @keydown.esc="showReceipt = false"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="receive"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭"
          @click="showReceipt = false"
        >
          ×
        </button><h2 id="receipt-title">
          登记收货 · {{ detail.order.orderNo }}
        </h2><p class="form-wide purchase-form-hint">
          只填写本次实际收到的数量；留空或 0 的明细不会入账。
        </p><label
          v-for="line in detail.order.lines.filter(item => item.remainingQuantity > 0)"
          :key="line.id"
          class="form-wide"
        >{{ line.itemCode }} · 待收 {{ line.remainingQuantity }} {{ line.unit }}<input
          v-model.number="quantities[line.id]"
          type="number"
          min="0"
          :max="line.remainingQuantity"
          step="0.0001"
        ></label><label class="form-wide">本次收货备注<textarea
          v-model="remark"
          maxlength="500"
        /></label><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            @click="showReceipt = false"
          >
            取消
          </button><button
            :disabled="saving"
            type="submit"
          >
            确认本次收货
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
