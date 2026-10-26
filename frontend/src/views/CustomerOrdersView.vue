<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ordersApi, type CustomerOrder, type OrderInput, type OrderAllocation, type ArrangementItem } from '@/api/orders'
import type { DashboardHistoryEvent } from '@/api/dashboard'
import { session } from '@/state/session'
import CustomerOrderEditor from '@/components/CustomerOrderEditor.vue'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import ProductName from '@/components/ProductName.vue'
import OrderDocuments from '@/components/OrderDocuments.vue'
import { orderHistoryChanges } from '@/ui/orderHistory'
const route = useRoute(), router = useRouter()
const saveKey = ref('')
const writable = computed(() => session.canAccess(['customers.write']))
const canArrange = computed(() => writable.value && session.canAccess(['dashboard.shipments.manage']))
const rows = ref<CustomerOrder[]>([]), detail = ref<CustomerOrder>(), allocations = ref<OrderAllocation[]>([]), history = ref<DashboardHistoryEvent[]>([])
const loading = ref(false), saving = ref(false), error = ref(''), success = ref(''), formError = ref(''), editing = ref(false), editTarget = ref<CustomerOrder>()
const page = ref(1), size = ref(20), total = ref(0), query = reactive({ q: '', status: '', source: '', from: '', to: '', deliveryFrom: '', deliveryTo: '', shipping: '', invoiceStatus: '' })
const transition = ref<{ order: CustomerOrder; action: string }>(), reason = ref('')
const arranging = ref<CustomerOrder>(), arrangeItems = ref<Array<ArrangementItem & { selected: boolean; name: string; available: number; unit: string }>>([])
const recipient = ref(''), phone = ref(''), address = ref(''), requestKey = ref('')
const logistics = ref<OrderAllocation>(), logisticsText = ref('')
const labels: Record<string,string> = { DRAFT: '草稿', CONFIRMED: '已确认', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭', PENDING: '待发货', TODAY: '今日发货', SHIPPED: '已发货', CONFIRM: '确认订单', CANCEL: '取消订单', CLOSE: '关闭订单', DELETE: '删除草稿' }
const message = (e: unknown) => e instanceof Error ? e.message : '请求失败，请重试。'
let sequence = 0
async function load() {
  const seq = ++sequence; loading.value = true; error.value = ''
  try {
    if (route.params.id) {
      const id = String(route.params.id); const [order, batches, events] = await Promise.all([ordersApi.get(id), ordersApi.allocations(id), ordersApi.history(id)])
      if (seq !== sequence) return
      detail.value = order; allocations.value = batches; history.value = events
    } else {
      detail.value = undefined
      const result = await ordersApi.list({ ...query, page: page.value - 1, size: size.value }); if (seq !== sequence) return
      rows.value = result.items; total.value = result.totalCount
    }
  } catch (e) { if (seq === sequence) error.value = message(e) } finally { if (seq === sequence) loading.value = false }
}
function search() { page.value = 1; void load() }
function edit(order?: CustomerOrder) { editTarget.value = order; formError.value = ''; saveKey.value = crypto.randomUUID(); editing.value = true }
async function save(input: OrderInput) {
  if (saving.value) return; saving.value = true; formError.value = ''
  try { await ordersApi.save(editTarget.value?.id, input, saveKey.value); editing.value = false; success.value = '订单已保存。'; await load() }
  catch (e) { formError.value = message(e) } finally { saving.value = false }
}
function ask(order: CustomerOrder, action: string) { transition.value = { order, action }; reason.value = ''; formError.value = '' }
async function confirm() {
  if (!transition.value || saving.value) return; saving.value = true; formError.value = ''
  const { order, action } = transition.value
  try { await ordersApi.transition(order.id, order.version, action, reason.value); transition.value = undefined; success.value = `${labels[action]}成功。`; if (action === 'DELETE' && route.params.id) await router.push('/orders/customers'); else await load() }
  catch (e) { formError.value = message(e) } finally { saving.value = false }
}
async function arrange(order: CustomerOrder) {
  error.value = ''; try {
    const fresh = await ordersApi.get(order.id); arranging.value = fresh; formError.value = ''; requestKey.value = crypto.randomUUID()
    recipient.value = fresh.details.recipient; phone.value = fresh.details.phone; address.value = fresh.details.address
    arrangeItems.value = fresh.lines.map(l => ({ lineId: l.id, selected: false, name: l.details.name, unit: l.details.unit, available: Math.max(0, Number(l.details.quantity) - Number(l.allocatedQuantity) - Number(l.shippedQuantity)), quantity: 0, boxCount: 1, plannedDate: l.details.deliveryDate, latestDate: l.details.deliveryDate, remark: l.details.remark }))
  } catch (e) { error.value = message(e) }
}
async function submitArrange() {
  if (!arranging.value || saving.value) return
  const selected = arrangeItems.value.filter(i => i.selected)
  if (!selected.length || selected.some(i => i.quantity <= 0 || i.quantity > i.available)) { formError.value = '请选择商品，填写不超过可安排数量的本次数量。'; return }
  saving.value = true; formError.value = ''
  try {
    const id = arranging.value.id
    await ordersApi.arrange(id, { version: arranging.value.version, items: selected.map(({ lineId, quantity, boxCount, plannedDate, latestDate, remark }) => ({ lineId, quantity, boxCount, plannedDate, latestDate, remark })), recipient: recipient.value, phone: phone.value, address: address.value }, requestKey.value)
    arranging.value = undefined; success.value = '已生成待发货安排。'; await router.push({ path: '/dashboard-maintenance/pending-shipments', query: { orderId: id } })
  } catch (e) { formError.value = message(e) } finally { saving.value = false }
}
async function generate(order: CustomerOrder) { if (saving.value) return; saving.value = true; try { const invoice = await ordersApi.generateInvoice(order.id); await router.push({ path: '/customers/invoices', query: { invoiceId: invoice.id } }) } catch (e) { error.value = message(e) } finally { saving.value = false } }
async function saveLogistics() { if (!logistics.value || saving.value) return; saving.value = true; try { await ordersApi.logistics(logistics.value.id, logistics.value.version, logisticsText.value); logistics.value = undefined; success.value = '物流信息已保存。'; await load() } catch (e) { formError.value = message(e) } finally { saving.value = false } }
watch(() => route.params.id, load); onMounted(load)
</script>
<template>
  <section class="domain-grid customer-orders">
    <SuccessToast :message="success" /><SuccessToast
      :message="error"
      tone="error"
    />
    <template v-if="!route.params.id">
      <form
        class="panel filter-panel operation-form"
        @submit.prevent="search"
      >
        <label>关键词<input
          v-model="query.q"
          placeholder="订单编号、客户订单号或客户名称"
          maxlength="128"
        ></label><label>来源<select v-model="query.source"><option value="">全部</option><option value="1688">1688</option><option value="OFFLINE">线下</option></select></label>
        <label>状态<select v-model="query.status"><option value="">全部</option><option
          v-for="s in ['DRAFT','CONFIRMED','COMPLETED','CANCELLED','CLOSED']"
          :key="s"
          :value="s"
        >{{ labels[s] }}</option></select></label>
        <label>下单开始日期<input
          v-model="query.from"
          type="date"
        ></label><label>下单结束日期<input
          v-model="query.to"
          type="date"
        ></label>
        <label>交期开始<input
          v-model="query.deliveryFrom"
          type="date"
        ></label><label>交期结束<input
          v-model="query.deliveryTo"
          type="date"
        ></label>
        <label>发货进度<select v-model="query.shipping"><option value="">全部</option><option value="UNSHIPPED">未发货</option><option value="PARTIAL">部分发货</option><option value="SHIPPED">全部发货</option></select></label>
        <label>开票状态<select v-model="query.invoiceStatus"><option value="">全部</option><option value="NONE">无需开票</option><option value="NOT_GENERATED">待生成</option><option value="PENDING">待开票</option><option value="INVOICED">已开票</option></select></label>
        <button :disabled="loading">
          查询
        </button><button
          type="button"
          class="filter-reset-button"
          @click="Object.assign(query,{q:'',status:'',source:'',from:'',to:'',deliveryFrom:'',deliveryTo:'',shipping:'',invoiceStatus:''}); search()"
        >
          重置
        </button>
      </form>
      <div class="material-toolbar">
        <button
          v-if="writable"
          type="button"
          @click="edit()"
        >
          新增客户订单
        </button>
      </div>
      <TableViewport>
        <table class="data-table order-main-table">
          <thead><tr><th>订单编号 / 客户订单号</th><th>客户</th><th>商品</th><th>下单日期</th><th>交期</th><th>含税总价 RMB</th><th>状态 / 发货进度</th><th>开票</th><th>备注</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="o in rows"
              :key="o.id"
            >
              <td>
                <RouterLink :to="`/orders/customers/${o.id}`">
                  {{ o.orderNumber }}
                </RouterLink><small>{{ o.details.externalNumber || '—' }}</small>
              </td><td>{{ o.customerName }}</td><td>
                <ProductName
                  v-if="o.lines[0]"
                  :text="o.lines[0].details.name"
                  :styles="o.lines[0].namePresentation?.styles"
                  :highlighted="o.lines[0].namePresentation?.highlighted"
                /><small>共 {{ o.lines.length }} 项</small>
              </td><td>{{ o.details.orderDate }}</td><td>{{ o.details.deliveryDate }}</td><td>{{ o.totalAmount }}</td><td>{{ labels[o.status] }}<small>发完 {{ o.lines.filter(l => Number(l.shippedQuantity) >= Number(l.details.quantity)).length }}/{{ o.lines.length }} 项</small><small v-if="o.lines.some(l=>Number(l.shippedQuantity)>0) && o.lines.some(l=>Number(l.shippedQuantity)<Number(l.details.quantity))">部分发货</small></td><td>{{ !o.details.invoiceRequired ? '无需开票' : o.invoiced ? '已开票' : o.invoiceId ? '待开票' : '待生成' }}</td><td>{{ o.details.remark || '—' }}</td><td class="table-actions">
                <RouterLink :to="`/orders/customers/${o.id}`">
                  详情
                </RouterLink><button
                  v-if="canArrange && o.status === 'CONFIRMED'"
                  :disabled="saving"
                  @click="arrange(o)"
                >
                  安排发货
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <p
        v-if="!loading && !rows.length"
        class="data-state"
      >
        暂无订单
      </p>
      <ListPagination
        v-model:page-size="size"
        :page="page"
        :total="total"
        :disabled="loading"
        @update:page="page=$event; load()"
      />
    </template>
    <template v-else-if="detail">
      <div class="material-toolbar">
        <strong>{{ detail.orderNumber }} · {{ labels[detail.status] }}</strong><button
          v-if="writable && ['DRAFT','CONFIRMED'].includes(detail.status)"
          :disabled="saving"
          @click="edit(detail)"
        >
          编辑
        </button><button
          v-if="writable && detail.status === 'DRAFT'"
          @click="ask(detail,'CONFIRM')"
        >
          确认订单
        </button><button
          v-if="canArrange && detail.status === 'CONFIRMED'"
          @click="arrange(detail)"
        >
          安排发货
        </button><button
          v-if="writable && detail.details.invoiceRequired && detail.status === 'CONFIRMED'"
          :disabled="saving"
          @click="generate(detail)"
        >
          {{ detail.invoiceId ? '查看发票' : '生成发票' }}
        </button><RouterLink
          v-else-if="detail.invoiceId"
          :to="{path:'/customers/invoices',query:{invoiceId:detail.invoiceId}}"
        >
          查看发票
        </RouterLink><button
          v-if="writable && ['DRAFT','CONFIRMED'].includes(detail.status)"
          @click="ask(detail,'CANCEL')"
        >
          取消订单
        </button><button
          v-if="writable && detail.status === 'CONFIRMED'"
          @click="ask(detail,'CLOSE')"
        >
          关闭剩余订单
        </button><button
          v-if="writable && detail.status === 'DRAFT'"
          class="danger-button"
          @click="ask(detail,'DELETE')"
        >
          删除草稿
        </button>
      </div>
      <article class="panel order-information">
        <h2>订单信息</h2><p>客户：{{ detail.customerName }} · 来源：{{ detail.details.source === '1688' ? '1688' : '线下' }}</p><p>客户订单号：{{ detail.details.externalNumber || '—' }} · 合同号：{{ detail.details.contractNumber || '—' }}</p><p>下单日期：{{ detail.details.orderDate }} · 交期：{{ detail.details.deliveryDate }}</p><p>收货：{{ detail.details.recipient }} · {{ detail.details.phone }} · {{ detail.details.address }}</p><p>开票抬头：{{ detail.details.invoiceRequired ? detail.invoiceTitle.invoiceTitle || '尚未选择' : '无需开票' }}</p><p>备注：{{ detail.details.remark || '—' }}</p>
      </article>
      <TableViewport>
        <table class="data-table">
          <thead><tr><th>品名 / SKU</th><th>规格型号</th><th>数量 / 单位</th><th>不含税单价</th><th>税率%</th><th>含税金额</th><th>交期</th><th>已安排未发</th><th>已发货</th><th>特殊需求/备注</th></tr></thead><tbody>
            <tr
              v-for="l in detail.lines"
              :key="l.id"
            >
              <td>
                <ProductName
                  :text="l.details.name"
                  :styles="l.namePresentation?.styles"
                  :highlighted="l.namePresentation?.highlighted"
                /><small>{{ l.skuCode }}</small>
              </td><td>{{ l.details.specification }}</td><td>{{ l.details.quantity }} {{ l.details.unit }}</td><td>{{ l.details.netUnitPrice }}</td><td>{{ l.details.taxRate }}</td><td>{{ l.totalAmount }}</td><td>{{ l.details.deliveryDate }}</td><td>{{ l.allocatedQuantity }}</td><td>{{ l.shippedQuantity }}</td><td>{{ l.details.remark }}</td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <p>不含税金额 ¥{{ detail.netAmount }} · 税额 ¥{{ detail.taxAmount }} · 含税总价 ¥{{ detail.totalAmount }}</p>
      <article class="panel order-information">
        <h2>发货批次</h2><p v-if="!allocations.length">
          尚未安排发货
        </p><div
          v-for="a in allocations"
          :key="a.id"
          class="order-batch"
        >
          <strong>{{ a.name }} · {{ a.details.quantity }} {{ a.unit }} · {{ a.details.boxCount }} 箱 · {{ labels[a.state] }}</strong><p>{{ a.recipient }} · {{ a.phone }} · {{ a.address }}</p><p>计划 {{ a.details.plannedDate }} · 最晚 {{ a.details.latestDate }} · 备注：{{ a.details.remark || '—' }} · 物流：{{ a.logistics || '未登记' }}</p><RouterLink :to="{path:a.state === 'TODAY' ? '/dashboard-maintenance/today-shipments' : a.state === 'SHIPPED' ? '/shipping/statistics' : '/dashboard-maintenance/pending-shipments',query:{orderId:detail.id}}">
            查看发货记录
          </RouterLink><button
            v-if="canArrange && a.state === 'SHIPPED'"
            @click="logistics=a; logisticsText=a.logistics; formError=''"
          >
            登记物流
          </button>
        </div>
      </article>
      <article class="panel order-information">
        <h2>变更记录</h2><p
          v-for="h in history"
          :key="h.id"
        >
          {{ h.occurredAt }} · {{ h.actorDisplayName || h.actorLoginName }} · {{ h.detail }}
          <small
            v-for="(change,index) in orderHistoryChanges(h)"
            :key="index"
          >{{ change }}</small>
        </p>
      </article>
    </template>
    <OrderDocuments
      v-if="detail"
      :order-id="detail.id"
      scope="ORDER"
      :writable="writable"
    />
    <CustomerOrderEditor
      v-if="editing"
      :initial="editTarget"
      :busy="saving"
      :error="formError"
      @save="save"
      @close="editing=false"
    />
    <div
      v-if="transition"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="labels[transition.action]"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="confirm"
      >
        <h2>{{ labels[transition.action] }}</h2><p class="form-wide">
          订单：{{ transition.order.orderNumber }}。此操作会保留历史记录。
        </p><label
          v-if="['CANCEL','CLOSE'].includes(transition.action)"
          class="form-wide"
        >原因<input
          v-model="reason"
          required
          maxlength="500"
        ></label><p
          v-if="formError"
          class="data-state--error"
          role="alert"
        >
          {{ formError }}
        </p><div class="form-footer">
          <button
            type="button"
            :disabled="saving"
            @click="transition=undefined"
          >
            返回
          </button><button :disabled="saving">
            确认
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="arranging"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="安排待发货"
    >
      <form
        class="panel operation-form operation-dialog__card order-arrange"
        @submit.prevent="submitArrange"
      >
        <h2>安排待发货 · {{ arranging.orderNumber }}</h2><label>收货人<input
          v-model="recipient"
          required
          maxlength="100"
        ></label><label>联系电话<input
          v-model="phone"
          required
          maxlength="32"
        ></label><label class="form-wide">发货地址<input
          v-model="address"
          required
          maxlength="500"
        ></label><div class="form-wide order-scroll">
          <table class="data-table">
            <thead><tr><th>选择</th><th>商品</th><th>可安排</th><th>本次数量</th><th>箱数</th><th>计划日期</th><th>最晚日期</th><th>备注</th></tr></thead><tbody>
              <tr
                v-for="a in arrangeItems"
                :key="a.lineId"
              >
                <td>
                  <input
                    v-model="a.selected"
                    type="checkbox"
                    :aria-label="`选择${a.name}`"
                    :disabled="a.available <= 0"
                  >
                </td><td>
                  <ProductName
                    :text="a.name"
                    :styles="arranging.lines.find(l=>l.id===a.lineId)?.namePresentation?.styles"
                    :highlighted="arranging.lines.find(l=>l.id===a.lineId)?.namePresentation?.highlighted"
                  />
                </td><td>{{ a.available }} {{ a.unit }}</td><td>
                  <input
                    v-model.number="a.quantity"
                    aria-label="本次数量"
                    type="number"
                    step="0.001"
                    min="0.001"
                    :max="a.available"
                    :disabled="!a.selected"
                    :required="a.selected"
                  >
                </td><td>
                  <input
                    v-model.number="a.boxCount"
                    aria-label="箱数"
                    type="number"
                    step="1"
                    min="1"
                    :disabled="!a.selected"
                    :required="a.selected"
                  >
                </td><td>
                  <input
                    v-model="a.plannedDate"
                    aria-label="计划日期"
                    type="date"
                    :disabled="!a.selected"
                    :required="a.selected"
                  >
                </td><td>
                  <input
                    v-model="a.latestDate"
                    aria-label="最晚日期"
                    type="date"
                    :min="a.plannedDate"
                    :disabled="!a.selected"
                    :required="a.selected"
                  >
                </td><td>
                  <input
                    v-model="a.remark"
                    aria-label="发货备注"
                    maxlength="500"
                    :disabled="!a.selected"
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div><p
          v-if="formError"
          role="alert"
          class="data-state--error"
        >
          {{ formError }}
        </p><div class="form-footer">
          <button
            type="button"
            :disabled="saving"
            @click="arranging=undefined"
          >
            取消
          </button><button :disabled="saving">
            {{ saving ? '处理中…' : '确认并前往待发货' }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="logistics"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="登记物流"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="saveLogistics"
      >
        <h2>登记发货批次物流</h2><label class="form-wide">物流公司 / 运单号<input
          v-model="logisticsText"
          maxlength="1000"
        ></label><p
          v-if="formError"
          role="alert"
        >
          {{ formError }}
        </p><button
          type="button"
          :disabled="saving"
          @click="logistics=undefined"
        >
          取消
        </button><button :disabled="saving">
          保存
        </button>
      </form>
    </div>
  </section>
</template>
<style scoped>
.customer-orders{min-width:0;grid-template-columns:minmax(0,1fr)}.customer-orders small{display:block;color:#68758c;font-size:12px}.data-table{min-width:1100px}.order-information{padding:12px}.order-information p{white-space:pre-wrap;overflow-wrap:anywhere}.order-batch{border-top:1px solid #e2e7ee;padding:12px 0}.order-arrange{box-sizing:border-box;width:min(1200px,100%);max-width:100%;min-width:0;margin:0;max-height:calc(100dvh - 32px);overflow:auto}.order-scroll{overflow:auto;min-width:0}.order-scroll input{min-width:90px}.order-scroll input[type=checkbox]{min-width:0}
.order-main-table th:last-child,.order-main-table td:last-child{position:sticky;right:0;min-width:128px;background:#fff;box-shadow:-2px 0 4px #18263b0d}.order-main-table th:last-child{z-index:3;background:#f7f8fa}.customer-orders>.filter-panel{display:flex;flex-wrap:wrap;align-items:center}.customer-orders>.filter-panel label{flex:0 1 auto}.customer-orders>.filter-panel input{width:150px}.customer-orders>.filter-panel input:not([type=date]){width:210px}.customer-orders>.filter-panel select{width:110px}
.order-information{min-height:0}.order-information h2{font-size:17px;margin:0 0 10px}.order-information p{margin:8px 0}.customer-orders>.material-toolbar{justify-content:flex-start;gap:8px}.order-arrange .form-footer{grid-column:1 / -1;position:sticky;bottom:-24px;background:#fff;padding:12px 0;z-index:2}
</style>
