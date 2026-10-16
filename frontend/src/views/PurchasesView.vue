<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { domainApi, type MaterialDetailDto, type SkuListItemDto, type SupplierDto } from '@/api/domain'
import { purchaseApi, type PurchaseDashboard, type PurchaseOrder, type PurchaseOrderInput, type PurchaseReceipt, type PurchaseStatus } from '@/api/purchases'
import { session } from '@/state/session'

defineProps<{ mode: 'dashboard' | 'orders' | 'receipts' }>()
const orders = ref<PurchaseOrder[]>([]); const receipts = ref<PurchaseReceipt[]>([]); const dashboard = ref<PurchaseDashboard>()
const suppliers = ref<SupplierDto[]>([]); const skus = ref<SkuListItemDto[]>([]); const materials = ref<MaterialDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const status = ref<PurchaseStatus | ''>('')
const editing = ref<PurchaseOrder>(); const showOrderForm = ref(false); const receiving = ref<PurchaseOrder>()
const form = reactive<PurchaseOrderInput>({ orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0 }] })
const receiptQuantities = reactive<Record<string, number>>({}); const receiptRemark = ref('')
const writable = computed(() => session.canAccess(['purchases.write'])); const receivable = computed(() => session.canAccess(['purchases.receive']))
const itemOptions = computed(() => form.orderType === 'PRODUCT'
  ? skus.value.map(item => ({ id: item.variant.id!, code: item.variant.skuCode, name: item.variant.variantName, unit: '套' }))
  : materials.value.filter(item => item.status === 'ACTIVE').map(item => ({ id: item.id, code: item.materialCode, name: item.name, unit: item.unit })))
const statusLabels: Record<PurchaseStatus,string> = { DRAFT: '草稿', SUBMITTED: '已提交', PARTIALLY_RECEIVED: '部分收货', COMPLETED: '已完成', CANCELLED: '已取消' }
function message(exception: unknown, fallback: string) { return exception instanceof ApiError ? exception.message : fallback }
function resetForm() { Object.assign(form, { orderType: 'PRODUCT', supplierId: '', currency: 'CNY', remark: '', lines: [{ itemId: '', quantity: 1, unitPrice: 0 }] }); editing.value = undefined; showOrderForm.value = false }
function openCreate() { resetForm(); showOrderForm.value = true; error.value = ''; success.value = '' }
function openEdit(order: PurchaseOrder) { editing.value = order; Object.assign(form, { orderType: order.orderType, supplierId: order.supplierId, currency: order.currency, remark: order.remark ?? '', version: order.version, lines: order.lines.map(line => ({ itemId: line.itemId, quantity: line.orderedQuantity, unitPrice: line.unitPrice })) }); showOrderForm.value = true }
function addLine() { form.lines.push({ itemId: '', quantity: 1, unitPrice: 0 }) }
function removeLine(index: number) { if (form.lines.length > 1) form.lines.splice(index, 1) }
function switchType() { form.lines = [{ itemId: '', quantity: 1, unitPrice: 0 }] }
async function load() { loading.value = true; error.value = ''; try { const [orderValues, receiptValues, dashboardValue] = await Promise.all([purchaseApi.orders(status.value || undefined), purchaseApi.receipts(), purchaseApi.dashboard()]); orders.value = orderValues; receipts.value = receiptValues; dashboard.value = dashboardValue } catch (exception) { error.value = message(exception, '采购数据加载失败。') } finally { loading.value = false } }
async function loadReferences() { try { const [supplierPage, skuPage, materialPage] = await Promise.all([domainApi.suppliers({ status: 'ACTIVE', size: 100 }), domainApi.skus({ size: 100 }), domainApi.materials()]); suppliers.value = supplierPage.items; skus.value = skuPage.items; materials.value = materialPage.items as MaterialDetailDto[] } catch { error.value = '采购基础资料加载失败。' } }
async function saveOrder() { saving.value = true; error.value = ''; try { const payload = { ...form, lines: form.lines.map(line => ({ ...line })) }; const value = editing.value ? await purchaseApi.update(editing.value.id, payload) : await purchaseApi.create(payload); success.value = editing.value ? '采购单已更新。' : '采购单已创建。'; resetForm(); await load(); if (!orders.value.some(order => order.id === value.id)) orders.value.unshift(value) } catch (exception) { error.value = message(exception, '采购单保存失败。') } finally { saving.value = false } }
async function submitOrder(order: PurchaseOrder) { saving.value = true; try { await purchaseApi.submit(order.id, order.version); success.value = '采购单已提交。'; await load() } catch (exception) { error.value = message(exception, '采购单提交失败。') } finally { saving.value = false } }
async function cancelOrder(order: PurchaseOrder) { if (!window.confirm(`确认取消采购单 ${order.orderNo}？`)) return; saving.value = true; try { await purchaseApi.cancel(order.id, order.version); success.value = '采购单已取消。'; await load() } catch (exception) { error.value = message(exception, '采购单取消失败。') } finally { saving.value = false } }
function openReceipt(order: PurchaseOrder) { receiving.value = order; receiptRemark.value = ''; Object.keys(receiptQuantities).forEach(key => delete receiptQuantities[key]); order.lines.filter(line => line.remainingQuantity > 0).forEach(line => { receiptQuantities[line.id] = line.remainingQuantity }) }
async function receiveOrder() { if (!receiving.value) return; const lines = receiving.value.lines.filter(line => Number(receiptQuantities[line.id]) > 0).map(line => ({ orderLineId: line.id, quantity: Number(receiptQuantities[line.id]) })); saving.value = true; error.value = ''; try { await purchaseApi.receive(receiving.value.id, { requestId: crypto.randomUUID(), receivedAt: new Date().toISOString(), ...(receiptRemark.value ? { remark: receiptRemark.value } : {}), lines }); success.value = '收货已记账。'; receiving.value = undefined; await load() } catch (exception) { error.value = message(exception, '收货失败。') } finally { saving.value = false } }
watch(status, load); onMounted(async () => { await Promise.all([load(), loadReferences()]) })
</script>

<template>
  <section class="page-stack purchase-page">
    <header class="panel purchase-header">
      <div>
        <p class="eyebrow">
          PURCHASE MANAGEMENT
        </p><h1>采购管理</h1><p>统一管理产品与辅料采购、分批收货和库存联动。</p>
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
        <article class="panel">
          <strong>{{ dashboard?.draft ?? 0 }}</strong><span>草稿</span>
        </article><article class="panel">
          <strong>{{ dashboard?.pendingReceipt ?? 0 }}</strong><span>待收货</span>
        </article><article class="panel">
          <strong>{{ dashboard?.partial ?? 0 }}</strong><span>部分收货</span>
        </article><article class="panel">
          <strong>{{ dashboard?.completed ?? 0 }}</strong><span>已完成</span>
        </article>
      </section>
      <section class="panel">
        <h2>近期采购单</h2><table>
          <thead><tr><th>单号</th><th>类型</th><th>供应商</th><th>状态</th><th>更新时间</th></tr></thead><tbody>
            <tr
              v-for="order in dashboard?.recent"
              :key="order.id"
            >
              <td>{{ order.orderNo }}</td><td>{{ order.orderType === 'PRODUCT' ? '产品' : '辅料' }}</td><td>{{ order.supplierName }}</td><td>{{ statusLabels[order.status] }}</td><td>{{ new Date(order.updatedAt).toLocaleString() }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
    <template v-else-if="mode === 'orders'">
      <section class="panel filter-panel">
        <label>状态<select v-model="status"><option value="">全部</option><option
          v-for="(label,key) in statusLabels"
          :key="key"
          :value="key"
        >{{ label }}</option></select></label>
      </section>
      <section class="panel">
        <p v-if="loading">
          正在加载…
        </p><table v-else>
          <thead><tr><th>采购单</th><th>类型</th><th>供应商</th><th>金额</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="order in orders"
              :key="order.id"
            >
              <td><strong>{{ order.orderNo }}</strong><small>{{ order.lines.length }} 项明细</small></td><td>{{ order.orderType === 'PRODUCT' ? '产品' : '辅料' }}</td><td>{{ order.supplierName }}</td><td>{{ order.currency }} {{ order.lines.reduce((sum,line) => sum + line.orderedQuantity * line.unitPrice, 0).toFixed(2) }}</td><td>{{ statusLabels[order.status] }}</td><td class="table-actions">
                <button
                  v-if="writable && order.status === 'DRAFT'"
                  type="button"
                  @click="openEdit(order)"
                >
                  编辑
                </button><button
                  v-if="writable && order.status === 'DRAFT'"
                  type="button"
                  @click="submitOrder(order)"
                >
                  提交
                </button><button
                  v-if="receivable && ['SUBMITTED','PARTIALLY_RECEIVED'].includes(order.status)"
                  type="button"
                  @click="openReceipt(order)"
                >
                  收货
                </button><button
                  v-if="writable && !['COMPLETED','CANCELLED'].includes(order.status)"
                  type="button"
                  class="secondary-button"
                  @click="cancelOrder(order)"
                >
                  取消
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
    <section
      v-else
      class="panel"
    >
      <h2>收货记录</h2><table>
        <thead><tr><th>采购单</th><th>请求号</th><th>收货时间</th><th>操作人</th><th>明细</th></tr></thead><tbody>
          <tr
            v-for="receipt in receipts"
            :key="receipt.id"
          >
            <td>{{ receipt.orderNo }}</td><td>{{ receipt.requestId }}</td><td>{{ new Date(receipt.receivedAt).toLocaleString() }}</td><td>{{ receipt.actorLoginName }}</td><td>{{ receipt.lines.map(line => `${line.itemCode} × ${line.quantity}`).join('；') }}</td>
          </tr>
        </tbody>
      </table>
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
        </h2><label>采购类型<select
          v-model="form.orderType"
          :disabled="Boolean(editing)"
          @change="switchType"
        ><option value="PRODUCT">产品采购</option><option value="MATERIAL">辅料采购</option></select></label><label>供应商<select
          v-model="form.supplierId"
          required
        ><option
          value=""
          disabled
        >请选择</option><option
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
        /></label><div class="form-wide purchase-lines">
          <div
            v-for="(line,index) in form.lines"
            :key="index"
            class="purchase-line"
          >
            <select
              v-model="line.itemId"
              required
            >
              <option
                value=""
                disabled
              >
                选择{{ form.orderType === 'PRODUCT' ? 'SKU' : '辅料' }}
              </option><option
                v-for="item in itemOptions"
                :key="item.id"
                :value="item.id"
              >
                {{ item.code }} · {{ item.name }}（{{ item.unit }}）
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
            :disabled="saving"
            type="submit"
          >
            保存草稿
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="receiving"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="receipt-title"
      @click.self="receiving = undefined"
      @keydown.esc="receiving = undefined"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="receiveOrder"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭"
          @click="receiving = undefined"
        >
          ×
        </button><h2 id="receipt-title">
          收货 · {{ receiving.orderNo }}
        </h2><label
          v-for="line in receiving.lines.filter(value => value.remainingQuantity > 0)"
          :key="line.id"
          class="form-wide"
        >{{ line.itemCode }} · 剩余 {{ line.remainingQuantity }} {{ line.unit }}<input
          v-model.number="receiptQuantities[line.id]"
          type="number"
          min="0"
          :max="line.remainingQuantity"
          step="0.0001"
        ></label><label class="form-wide">收货备注<textarea
          v-model="receiptRemark"
          maxlength="500"
        /></label><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            @click="receiving = undefined"
          >
            取消
          </button><button
            :disabled="saving"
            type="submit"
          >
            确认收货
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
