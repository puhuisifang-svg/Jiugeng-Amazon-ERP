<script setup lang="ts">
import '@/styles/productManagement.css'
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import ProductUiState from './ProductUiState.vue'
import { productManagementApi, type ProductStockCommand, type ProductStockEntry } from '@/api/productManagement'
import { ApiError } from '@/api/client'
import { session } from '@/state/session'
import { beijingDateTime } from '@/utils/dateTime'
const props = defineProps<{ variantId: string; name: string; parentName?: string; readOnly?:boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const inbound = computed(() => !props.readOnly&&session.canAccess(['inventory.inbound']))
const outbound = computed(() => !props.readOnly&&session.canAccess(['inventory.outbound']))
const operations = [
  { key: 'INBOUND', label: '入库', sign: '+', verb: '确认入库' },
  { key: 'OUTBOUND', label: '出库', sign: '−', verb: '确认出库' },
  { key: 'ADJUST_IN', label: '盘盈', sign: '+', verb: '确认盘盈' },
  { key: 'ADJUST_OUT', label: '盘亏', sign: '−', verb: '确认盘亏' },
]
const types = computed(() => operations.filter(item => ['INBOUND', 'ADJUST_IN'].includes(item.key) ? inbound.value : outbound.value))
const type = ref(inbound.value ? 'INBOUND' : 'OUTBOUND'), quantity = ref<number>(), reason = ref('')
const current = ref<number>(), initialized = ref(false), busy = ref(false), loading = ref(false)
const error = ref(''), loadError = ref(''), notice = ref(''), submitted = ref(false)
const page = ref(1), total = ref(0), entries = ref<ProductStockEntry[]>([])
const pending = ref<ProductStockCommand>()
const pendingKey = `jiugeng.product-stock.pending.${session.user.value?.id ?? 'current'}.${props.variantId}`
try {
  const saved = JSON.parse(sessionStorage.getItem(pendingKey) ?? 'null') as ProductStockCommand | null
  if (saved?.requestId && operations.some(item => item.key === saved.type) && Number.isSafeInteger(saved.quantity) && saved.quantity > 0 && typeof saved.reason === 'string') {
    pending.value = saved; type.value = saved.type; quantity.value = saved.quantity; reason.value = saved.reason
  }
} catch { /* Corrupt local state is ignored. */ }
watch(pending, value => { try { if (value) sessionStorage.setItem(pendingKey, JSON.stringify(value)); else sessionStorage.removeItem(pendingKey) } catch { /* Retain in memory for safe retries. */ } }, { flush: 'sync' })
const locked = computed(() => busy.value || !!pending.value)
const dirty = computed(() => !!pending.value || !!quantity.value || !!reason.value)
const decreasing = computed(() => ['OUTBOUND', 'ADJUST_OUT'].includes(type.value))
const validQuantity = computed(() => Number.isSafeInteger(quantity.value) && Number(quantity.value) > 0 && Number(quantity.value) <= 999999999999999)
const projected = computed(() => current.value == null || !validQuantity.value ? undefined : current.value + (decreasing.value ? -1 : 1) * Number(quantity.value))
const quantityError = computed(() => {
  if (pending.value) return ''
  if (!validQuantity.value) return submitted.value || (quantity.value != null && String(quantity.value) !== '') ? '请输入 1–999999999999999 的整数件数' : ''
  if (projected.value != null && projected.value < 0) return `库存不足，最多可减少 ${format(current.value)} 件`
  if (projected.value != null && projected.value > 999999999999999) return '操作后库存超过可记录上限'
  return ''
})
const reasonError = computed(() => submitted.value && !reason.value.trim() ? '请填写本次操作原因' : '')
const action = computed(() => operations.find(item => item.key === type.value)!)
const maySubmit = computed(() => types.value.some(item => item.key === type.value))
function format(value?: number) { return value == null ? '—' : value.toLocaleString('zh-CN') }
function typeLabel(value: string) { return operations.find(item => item.key === value)?.label ?? value }
function guardUnload(event: BeforeUnloadEvent) { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => window.addEventListener('beforeunload', guardUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', guardUnload))
let loadSequence = 0
async function load() {
  const sequence = ++loadSequence
  loading.value = true; loadError.value = ''
  try {
    const [balances, history] = await Promise.all([productManagementApi.balances([props.variantId]), productManagementApi.stockHistory(props.variantId, page.value - 1)])
    if (sequence !== loadSequence) return
    const balance = balances.find(item => item.variantId === props.variantId)
    current.value = balance?.quantity ?? 0; initialized.value = !!balance
    entries.value = history.items; total.value = history.total
  } catch (e) { if (sequence === loadSequence) loadError.value = e instanceof Error ? e.message : '库存加载失败，请重试' }
  finally { if (sequence === loadSequence) loading.value = false }
}
onBeforeUnmount(() => { loadSequence++ })
async function submit() {
  if (busy.value || !maySubmit.value || (!pending.value && (loading.value || loadError.value || current.value == null))) return
  submitted.value = true
  if (!pending.value) {
    if (!validQuantity.value || quantityError.value || !reason.value.trim()) return
    pending.value = { requestId: crypto.randomUUID(), type: type.value, quantity: Number(quantity.value), reason: reason.value.trim() }
  }
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await productManagementApi.transact(props.variantId, pending.value)
    const entry = result.item
    notice.value = result.replayed ? '已确认该操作此前成功，没有重复记账。' : `已${typeLabel(entry.type)} ${format(entry.quantity)} 件，操作后库存 ${format(entry.balanceAfter)} 件。`
    current.value = entry.balanceAfter; initialized.value = true
    pending.value = undefined; quantity.value = undefined; reason.value = ''; submitted.value = false; page.value = 1
    emit('changed'); await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '网络异常，请确认上次操作结果'
    if (e instanceof ApiError && e.status < 500 && e.status !== 408) pending.value = undefined
  } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <AccessDialog
    title="库存操作与流水"
    dialog-class="product-ui product-stock-dialog"
    wide
    :busy="locked"
    :dirty="dirty"
    @close="emit('close')"
  >
    <div class="product-stock-identity">
      <div><strong>{{ name }}</strong><small v-if="parentName">所属父体 · {{ parentName }}</small></div>
      <div class="product-stock-balance">
        <span>当前库存 <strong>{{ format(current) }}</strong> 件</span><small v-if="current != null && !initialized">未建账</small>
      </div>
      <button
        type="button"
        class="product-text-button"
        :disabled="busy || loading"
        @click="load"
      >
        刷新
      </button>
    </div>
    <form
      v-if="types.length || pending"
      class="stock-command"
      novalidate
      @submit.prevent="submit"
    >
      <fieldset
        class="stock-operation-types"
        :disabled="locked"
        aria-label="库存操作类型"
      >
        <legend class="sr-only">
          操作类型
        </legend>
        <button
          v-for="item in types"
          :key="item.key"
          type="button"
          :aria-pressed="type === item.key"
          :class="{ active: type === item.key }"
          @click="type = item.key; error = ''; notice = ''"
        >
          <span aria-hidden="true">{{ item.sign }}</span> {{ item.label }}
        </button>
      </fieldset>
      <p
        v-if="pending && !maySubmit"
        class="product-feedback product-feedback--warning"
      >
        当前账号已无此操作权限，请联系管理员核实上次操作结果。
      </p>
      <div class="stock-command-fields">
        <label>数量（件）<input
          v-model.number="quantity"
          type="number"
          min="1"
          max="999999999999999"
          step="1"
          required
          :disabled="locked"
          :aria-invalid="!!quantityError"
          aria-describedby="stock-quantity-error"
          placeholder="填写数量"
        ><span
          v-if="quantityError"
          id="stock-quantity-error"
          class="product-field-error"
        >{{ quantityError }}</span></label>
        <label>操作原因<input
          v-model="reason"
          maxlength="500"
          required
          :disabled="locked"
          :aria-invalid="!!reasonError"
          aria-describedby="stock-reason-error"
          placeholder="如：期初入库、领用出库、盘点差异"
        ><span
          v-if="reasonError"
          id="stock-reason-error"
          class="product-field-error"
        >{{ reasonError }}</span></label>
      </div>
      <div class="stock-command-bottom">
        <div
          class="stock-projection"
          aria-live="polite"
        >
          <template v-if="pending">
            <span>待确认操作</span><strong>{{ typeLabel(pending.type) }} {{ format(pending.quantity) }} 件</strong>
          </template>
          <template v-else-if="projected != null && !quantityError">
            <span>当前 {{ format(current) }}</span><span aria-hidden="true">→</span><span :class="decreasing ? 'stock-decrease' : 'stock-increase'">{{ action.sign }}{{ format(quantity) }}</span><span aria-hidden="true">→</span><strong>预计 {{ format(projected) }} 件</strong><small>以提交结果为准</small>
          </template>
          <span v-else>填写数量后预览预计结存</span>
        </div>
        <button
          type="submit"
          class="primary-button"
          :disabled="busy || !maySubmit || (!pending && (loading || !!loadError || current == null || !!quantityError))"
        >
          {{ busy ? '提交中…' : pending ? '确认上次操作结果' : action.verb }}
        </button>
      </div>
    </form>
    <p
      v-else
      class="product-inline-note"
    >
      当前账号可查看库存与流水，暂无库存操作权限。
    </p>
    <div
      v-if="pending && !busy"
      role="alert"
      class="product-feedback product-feedback--warning"
    >
      <strong>上次操作结果待确认</strong><span>已保留原操作并锁定输入，请点击“确认上次操作结果”。确认不会重复记账。</span><small v-if="error">{{ error }}</small>
    </div>
    <p
      v-else-if="error"
      role="alert"
      class="product-feedback product-feedback--error"
    >
      {{ error }}，请核对后重试。
    </p>
    <p
      v-if="notice"
      role="status"
      class="product-feedback product-feedback--success"
    >
      {{ notice }}
    </p>
    <div class="product-section-heading">
      <h3>库存流水</h3><span>最新记录在前 · 单位：件</span>
    </div>
    <ProductUiState
      v-if="loading"
      loading
      title="正在加载库存…"
    />
    <ProductUiState
      v-else-if="loadError"
      error
      title="库存数据暂不可用"
      :description="loadError"
      @retry="load"
    />
    <TableViewport v-else-if="entries.length">
      <table class="data-table product-ledger-table">
        <thead>
          <tr>
            <th>时间 / 操作人</th><th>类型</th><th class="numeric">
              变动
            </th><th class="numeric">
              结存
            </th><th>原因</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in entries"
            :key="item.id"
            :class="{ 'product-latest-row': page === 1 && index === 0 }"
          >
            <td>
              {{ beijingDateTime(item.occurredAt) }}<small>{{ item.actorLoginName }} <span
                v-if="page === 1 && index === 0"
                class="product-latest-label"
              >最新</span></small>
            </td>
            <td>
              <span
                class="product-operation-badge"
                :class="item.signedQuantity > 0 ? 'stock-increase' : 'stock-decrease'"
              >{{ typeLabel(item.type) }}</span>
            </td>
            <td
              class="numeric"
              :class="item.signedQuantity > 0 ? 'stock-increase' : 'stock-decrease'"
            >
              {{ item.signedQuantity > 0 ? '+' : '−' }}{{ format(Math.abs(item.signedQuantity)) }}
            </td><td class="numeric">
              {{ format(item.balanceAfter) }}
            </td>
            <td class="stock-note">
              <details v-if="item.reason.length > 44">
                <summary>{{ item.reason.slice(0, 44) }}… <span>展开</span></summary><p>{{ item.reason }}</p>
              </details><span v-else>{{ item.reason }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ProductUiState
      v-else
      title="暂无库存流水"
      description="完成首次库存操作后，记录将显示在这里。"
    />
    <template #pagination>
      <ListPagination
        :page="page"
        :page-size="20"
        :sizes="[20]"
        :total="total"
        :disabled="loading || busy"
        @update:page="page = $event; load()"
      />
    </template>
  </AccessDialog>
</template>
