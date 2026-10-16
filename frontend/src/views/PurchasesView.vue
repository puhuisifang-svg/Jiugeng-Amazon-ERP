<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { domainApi, type SupplierDto } from '@/api/domain'
import { purchaseApi, type PurchaseDashboard, type PurchaseOrder, type PurchaseOrderInput, type PurchaseReceipt, type PurchaseReferenceItem, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

const props = defineProps<{ mode: 'dashboard' | 'orders' | 'receipts' }>()
const orders = ref<PurchaseOrder[]>([]); const receipts = ref<PurchaseReceipt[]>([]); const dashboard = ref<PurchaseDashboard>()
const suppliers = ref<SupplierDto[]>([]); const references = ref<PurchaseReferenceItem[]>([])
const loading = ref(false); const referenceLoading = ref(false); const saving = ref(false); const error = ref(''); const success = ref(''); const status = ref<PurchaseStatus | ''>('')
const editing = ref<PurchaseOrder>(); const showOrderForm = ref(false)
const form = reactive<PurchaseOrderInput>({ orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0 }] })
const writable = computed(() => session.canAccess(['purchases.write']))
const statusLabels: Record<PurchaseStatus,string> = { DRAFT: '草稿', SUBMITTED: '已提交', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消' }
const typeLabels = { PRODUCT: '产品采购', MATERIAL: '辅料采购' }
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
function resetForm() { Object.assign(form, { orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0 }] }); references.value = []; editing.value = undefined; showOrderForm.value = false }
async function load() {
  loading.value = true; error.value = ''
  try {
    if (props.mode === 'dashboard') dashboard.value = await purchaseApi.dashboard()
    else if (props.mode === 'orders') orders.value = await purchaseApi.orders(status.value || undefined)
    else receipts.value = await purchaseApi.receipts()
  } catch (exception) { error.value = message(exception, '采购数据加载失败。') } finally { loading.value = false }
}
async function loadSuppliers() {
  if (suppliers.value.length) return
  try { suppliers.value = (await domainApi.suppliers({ status: 'ACTIVE', size: 100 })).items }
  catch (exception) { error.value = message(exception, '供应商加载失败，请先确认供应商档案权限。') }
}
async function loadReferences(resetLines = true) {
  references.value = []; if (resetLines) form.lines = [{ itemId: '', quantity: 1, unitPrice: 0 }]
  if (!form.supplierId) return
  referenceLoading.value = true
  try { references.value = await purchaseApi.references(form.orderType, form.supplierId) }
  catch (exception) { error.value = message(exception, '可采购商品加载失败。') } finally { referenceLoading.value = false }
}
async function openCreate() { resetForm(); showOrderForm.value = true; await loadSuppliers() }
async function openEdit(order: PurchaseOrder) {
  editing.value = order; showOrderForm.value = true; await loadSuppliers()
  Object.assign(form, { orderType: order.orderType, supplierId: order.supplierId, currency: order.currency, remark: order.remark ?? '', version: order.version, lines: order.lines.map(line => ({ itemId: line.itemId, quantity: line.orderedQuantity, unitPrice: line.unitPrice })) })
  await loadReferences(false)
}
function addLine() { form.lines.push({ itemId: '', quantity: 1, unitPrice: 0 }) }
function removeLine(index: number) { if (form.lines.length > 1) form.lines.splice(index, 1) }
async function saveOrder() {
  saving.value = true; error.value = ''; success.value = ''
  try { const payload = { ...form, lines: form.lines.map(line => ({ ...line })) }; if (editing.value) await purchaseApi.update(editing.value.id, payload); else await purchaseApi.create(payload); success.value = editing.value ? '采购单已更新。' : '采购单草稿已创建。'; resetForm(); await load() }
  catch (exception) { error.value = message(exception, '采购单保存失败。') } finally { saving.value = false }
}
watch(() => props.mode, load); watch(status, () => { if (props.mode === 'orders') load() }); onMounted(load)
</script>

<template>
  <section class="page-stack purchase-page">
    <header class="panel purchase-header">
      <div>
        <p class="eyebrow">
          PURCHASE MANAGEMENT
        </p><h1>{{ mode === 'dashboard' ? '采购看板' : mode === 'orders' ? '采购单' : '收货记录' }}</h1><p>从供应商关系选择可采购商品，完整跟踪下单、分批收货与库存入库。</p>
      </div><button
        v-if="mode === 'orders' && writable"
        type="button"
        @click="openCreate"
      >
        新建采购单
      </button>
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
    </p>
    <template v-if="mode === 'dashboard'">
      <section
        class="metric-grid"
        aria-label="采购统计"
      >
        <RouterLink
          class="panel purchase-metric"
          to="/purchases/orders"
        >
          <strong>{{ dashboard?.draft ?? 0 }}</strong><span>草稿</span>
        </RouterLink><RouterLink
          class="panel purchase-metric"
          to="/purchases/orders"
        >
          <strong>{{ dashboard?.pendingReceipt ?? 0 }}</strong><span>待收货</span>
        </RouterLink><RouterLink
          class="panel purchase-metric"
          to="/purchases/orders"
        >
          <strong>{{ dashboard?.partial ?? 0 }}</strong><span>部分收货</span>
        </RouterLink><RouterLink
          class="panel purchase-metric"
          to="/purchases/orders"
        >
          <strong>{{ dashboard?.completed ?? 0 }}</strong><span>已完成</span>
        </RouterLink>
      </section>
      <section class="panel">
        <div class="purchase-section-heading">
          <h2>近期采购单</h2><RouterLink to="/purchases/orders">
            查看全部
          </RouterLink>
        </div><div
          v-if="!loading && !dashboard?.recent.length"
          class="empty-state"
        >
          <span class="empty-state__icon">购</span><h2>还没有采购单</h2><p>前往采购单页面，从已关联供应商的产品或辅料创建第一张采购单。</p><RouterLink
            class="toolbar-link"
            to="/purchases/orders"
          >
            开始建单
          </RouterLink>
        </div><div
          v-else
          class="table-scroll"
        >
          <table>
            <thead><tr><th>单号</th><th>类型</th><th>供应商</th><th>状态</th><th>更新时间</th></tr></thead><tbody>
              <tr
                v-for="order in dashboard?.recent"
                :key="order.id"
              >
                <td>
                  <RouterLink
                    class="text-link"
                    :to="`/purchases/orders/${order.id}`"
                  >
                    {{ order.orderNo }}
                  </RouterLink>
                </td><td>{{ typeLabels[order.orderType] }}</td><td>{{ order.supplierName }}</td><td>{{ statusLabels[order.status] }}</td><td>{{ new Date(order.updatedAt).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
    <template v-else-if="mode === 'orders'">
      <section class="panel filter-panel purchase-filters">
        <label>状态<select v-model="status"><option value="">全部状态</option><option
          v-for="(label,key) in statusLabels"
          :key="key"
          :value="key"
        >{{ label }}</option></select></label>
      </section>
      <section class="panel">
        <p v-if="loading">
          正在加载…
        </p><div
          v-else-if="!orders.length"
          class="empty-state"
        >
          <span class="empty-state__icon">单</span><h2>{{ status ? '当前状态没有采购单' : '还没有采购单' }}</h2><p>建单前请先在产品/辅料档案中关联启用供应商。采购单只显示该供应商可供货的项目。</p><button
            v-if="writable"
            type="button"
            @click="openCreate"
          >
            新建采购单
          </button>
        </div><div
          v-else
          class="table-scroll"
        >
          <table>
            <thead><tr><th>采购单</th><th>类型</th><th>供应商</th><th>金额</th><th>收货进度</th><th>状态</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="order in orders"
                :key="order.id"
              >
                <td>
                  <RouterLink
                    class="text-link"
                    :to="`/purchases/orders/${order.id}`"
                  >
                    <strong>{{ order.orderNo }}</strong>
                  </RouterLink><small>{{ order.lines.length }} 项明细</small>
                </td><td>{{ typeLabels[order.orderType] }}</td><td>{{ order.supplierName }}</td><td>{{ order.currency }} {{ order.lines.reduce((sum,line) => sum + line.orderedQuantity * line.unitPrice, 0).toFixed(2) }}</td><td>{{ order.lines.reduce((sum,line) => sum + line.receivedQuantity, 0) }} / {{ order.lines.reduce((sum,line) => sum + line.orderedQuantity, 0) }}</td><td>{{ statusLabels[order.status] }}</td><td class="table-actions">
                  <RouterLink
                    class="primary-link"
                    :to="`/purchases/orders/${order.id}`"
                  >
                    查看
                  </RouterLink><button
                    v-if="writable && order.status === 'DRAFT'"
                    type="button"
                    class="secondary-button"
                    @click="openEdit(order)"
                  >
                    编辑
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
    <section
      v-else
      class="panel"
    >
      <div
        v-if="!loading && !receipts.length"
        class="empty-state"
      >
        <span class="empty-state__icon">收</span><h2>还没有收货记录</h2><p>采购单提交后，请在采购单详情中执行分批收货。</p><RouterLink
          class="toolbar-link"
          to="/purchases/orders"
        >
          查看采购单
        </RouterLink>
      </div><div
        v-else
        class="table-scroll"
      >
        <table>
          <thead><tr><th>采购单</th><th>收货时间</th><th>操作人</th><th>明细</th><th>备注</th></tr></thead><tbody>
            <tr
              v-for="receipt in receipts"
              :key="receipt.id"
            >
              <td>
                <RouterLink
                  class="text-link"
                  :to="`/purchases/orders/${receipt.orderId}`"
                >
                  {{ receipt.orderNo }}
                </RouterLink>
              </td><td>{{ new Date(receipt.receivedAt).toLocaleString() }}</td><td>{{ receipt.actorLoginName }}</td><td>{{ receipt.lines.map(line => `${line.itemCode} × ${line.quantity}`).join('；') }}</td><td>{{ receipt.remark || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div
      v-if="showOrderForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="purchase-form-title"
      @click.self="resetForm"
      @keydown.esc="resetForm"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="saveOrder"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭"
          @click="resetForm"
        >
          ×
        </button><h2 id="purchase-form-title">
          {{ editing ? '编辑采购单' : '新建采购单' }}
        </h2><p class="form-wide purchase-form-hint">
          先选择采购类型与供应商，再从该供应商已关联的项目中添加明细。
        </p><label>采购类型<select
          v-model="form.orderType"
          :disabled="Boolean(editing)"
          @change="loadReferences()"
        ><option value="PRODUCT">产品采购</option><option value="MATERIAL">辅料采购</option></select></label><label>供应商<select
          v-model="form.supplierId"
          required
          @change="loadReferences()"
        ><option
          value=""
          disabled
        >请选择启用供应商</option><option
          v-for="supplier in suppliers"
          :key="supplier.id"
          :value="supplier.id"
        >{{ supplier.name }}</option></select></label><label>币种<input
          v-model="form.currency"
          required
          maxlength="3"
        ></label><label class="form-wide">备注<textarea
          v-model="form.remark"
          maxlength="500"
        /></label><div
          v-if="form.supplierId && !referenceLoading && !references.length"
          class="form-wide empty-inline"
          role="status"
        >
          该供应商尚未关联可采购的{{ form.orderType === 'PRODUCT' ? '启用 SKU' : '启用辅料' }}。请先到产品或辅料管理维护供应商关系。
        </div><div class="form-wide purchase-lines">
          <div
            v-for="(line,index) in form.lines"
            :key="index"
            class="purchase-line"
          >
            <select
              v-model="line.itemId"
              required
              :disabled="!references.length"
            >
              <option
                value=""
                disabled
              >
                {{ referenceLoading ? '正在加载…' : '选择可采购项目' }}
              </option><option
                v-for="item in references"
                :key="item.id"
                :value="item.id"
              >
                {{ item.primarySupplier ? '★ ' : '' }}{{ item.code }} · {{ item.name }}（{{ item.unit }}）
              </option>
            </select><input
              v-model.number="line.quantity"
              type="number"
              min="0.0001"
              step="0.0001"
              aria-label="采购数量"
            ><input
              v-model.number="line.unitPrice"
              type="number"
              min="0"
              step="0.0001"
              aria-label="采购单价"
            ><button
              type="button"
              class="secondary-button"
              @click="removeLine(index)"
            >
              删除
            </button>
          </div><button
            type="button"
            class="secondary-button"
            :disabled="!references.length"
            @click="addLine"
          >
            增加明细
          </button>
        </div><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            @click="resetForm"
          >
            取消
          </button><button
            :disabled="saving || !references.length"
            type="submit"
          >
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
