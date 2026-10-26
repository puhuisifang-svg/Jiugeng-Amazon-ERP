<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ordersApi, type OrderInvoice } from '@/api/orders'
import { session } from '@/state/session'
import LegacyInvoices from './InvoiceManagementView.vue'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import OrderDocuments from '@/components/OrderDocuments.vue'
import ProductName from '@/components/ProductName.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
const route = useRoute(), router = useRouter(), legacy = ref(false)
const writable = computed(() => session.canAccess(['customers.write']))
const rows = ref<OrderInvoice[]>([]), selected = ref<OrderInvoice>(), page = ref(1), size = ref(20), total = ref(0), query = ref('')
const busy = ref(false), saving = ref(false), error = ref(''), success = ref(''), discard = ref(false), initial = ref('')
const form = reactive({ invoiced: false, invoiceNumber: '', invoiceDate: '', refreshFromOrder: false })
let sequence = 0
async function load() { const seq = ++sequence; busy.value = true; error.value = ''; try { const r = await ordersApi.invoices({ q: query.value, page: page.value - 1, size: size.value }); if (seq === sequence) { rows.value = r.items; total.value = r.totalCount } } catch (e) { if (seq === sequence) error.value = String(e) } finally { if (seq === sequence) busy.value = false } }
function open(item: OrderInvoice) { selected.value = item; Object.assign(form, { invoiced: item.invoiced, invoiceNumber: item.invoiceNumber, invoiceDate: item.invoiceDate ?? '', refreshFromOrder: false }); initial.value = JSON.stringify(form); discard.value = false; error.value = '' }
function close() { if (saving.value) return; if (JSON.stringify(form) !== initial.value) discard.value = true; else selected.value = undefined }
async function save() { if (!selected.value || saving.value) return; saving.value = true; try { const item = await ordersApi.updateInvoice(selected.value.id, { ...form, invoiceDate: form.invoiceDate || null, version: selected.value.version }); open(item); success.value = '发票信息已保存。'; await load() } catch (e) { error.value = String(e) } finally { saving.value = false } }
async function openFromRoute() { if (!route.query.invoiceId) return; legacy.value = false; try { open(await ordersApi.invoice(String(route.query.invoiceId))) } catch (e) { error.value = String(e) } }
onBeforeRouteLeave(() => { if (saving.value) return false; if (selected.value && JSON.stringify(form) !== initial.value) { discard.value = true; return false } return true })
watch(() => route.query.invoiceId, openFromRoute)
onMounted(async () => { await load(); await openFromRoute() })
</script>
<template>
  <section class="domain-grid order-invoices">
    <div
      class="ui-section-tabs"
      aria-label="发票台账类型"
    >
      <button
        type="button"
        :aria-pressed="!legacy"
        @click="legacy=false"
      >
        订单发票
      </button><button
        type="button"
        :aria-pressed="legacy"
        @click="legacy=true"
      >
        历史手工台账
      </button>
    </div>
    <LegacyInvoices v-if="legacy" />
    <template v-else>
      <SuccessToast :message="success" /><SuccessToast
        :message="error"
        tone="error"
      />
      <form
        class="panel filter-panel operation-form ui-filter-row"
        @submit.prevent="page=1; load()"
      >
        <label>关键词<input
          v-model="query"
          placeholder="订单编号、客户或发票号码"
          maxlength="128"
        ></label><button :disabled="busy">
          查询
        </button><button
          type="button"
          class="filter-reset-button"
          @click="query=''; page=1; load()"
        >
          重置
        </button><RouterLink to="/orders/customers">
          从客户订单生成发票
        </RouterLink>
      </form>
      <TableViewport>
        <table class="data-table ui-action-table">
          <thead><tr><th>来源订单</th><th>客户名称</th><th>发票抬头</th><th>商品明细</th><th>不含税金额</th><th>税额</th><th>含税总价 RMB</th><th>开票状态</th><th>发票号码</th><th>开票日期</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="item in rows"
              :key="item.id"
            >
              <td>
                <RouterLink :to="`/orders/customers/${item.orderId}`">
                  {{ item.snapshot.orderNumber }}
                </RouterLink>
              </td><td>{{ item.snapshot.customerName }}</td><td>{{ item.snapshot.invoiceTitle.invoiceTitle }}</td><td>{{ item.snapshot.lines.length }} 项</td><td>{{ item.snapshot.netAmount }}</td><td>{{ item.snapshot.taxAmount }}</td><td>{{ item.snapshot.totalAmount }}</td><td>{{ item.invoiced ? '已开票' : '待开票' }}</td><td>{{ item.invoiceNumber || '—' }}</td><td>{{ item.invoiceDate || '—' }}</td><td>
                <button @click="open(item)">
                  详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <p
        v-if="!busy && !rows.length"
        class="data-state"
      >
        暂无订单发票，请从需开票的客户订单生成。
      </p>
      <ListPagination
        v-model:page-size="size"
        :page="page"
        :total="total"
        :disabled="busy"
        @update:page="page=$event; load()"
      />
    </template>
    <div
      v-if="selected"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="订单发票详情"
      @keydown.esc.stop="close"
    >
      <form
        class="panel operation-dialog__card invoice-card"
        @submit.prevent="save"
      >
        <h2>订单发票</h2><UnsavedChangesDialog
          v-if="discard"
          @keep="discard=false"
          @discard="selected=undefined; discard=false"
        />
        <p>
          来源订单：<button
            type="button"
            class="filter-reset-button"
            @click="router.push(`/orders/customers/${selected.orderId}`)"
          >
            {{ selected.snapshot.orderNumber }}
          </button> · 客户：{{ selected.snapshot.customerName }}
        </p>
        <p>抬头：{{ selected.snapshot.invoiceTitle.invoiceTitle }} · 税号：{{ selected.snapshot.invoiceTitle.taxNumber }}</p><p>注册地址：{{ selected.snapshot.invoiceTitle.registeredAddress }} · 电话：{{ selected.snapshot.invoiceTitle.registeredPhone }}</p><p>开户银行：{{ selected.snapshot.invoiceTitle.bankName }} · 账号：{{ selected.snapshot.invoiceTitle.bankAccount }}</p>
        <div class="invoice-lines">
          <table class="data-table">
            <thead><tr><th>品名</th><th>规格及型号</th><th>单位</th><th>数量</th><th>不含税单价</th><th>税率%</th><th>不含税金额</th><th>税额</th><th>含税金额</th></tr></thead><tbody>
              <tr
                v-for="l in selected.snapshot.lines"
                :key="l.id"
              >
                <td>
                  <ProductName
                    :text="l.details.name"
                    :styles="l.namePresentation?.styles"
                    :highlighted="l.namePresentation?.highlighted"
                  />
                </td><td>{{ l.details.specification }}</td><td>{{ l.details.unit }}</td><td>{{ l.details.quantity }}</td><td>{{ l.details.netUnitPrice }}</td><td>{{ l.details.taxRate }}</td><td>{{ l.netAmount }}</td><td>{{ l.taxAmount }}</td><td>{{ l.totalAmount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>不含税合计 ¥{{ selected.snapshot.netAmount }} · 税额 ¥{{ selected.snapshot.taxAmount }} · 含税合计 ¥{{ selected.snapshot.totalAmount }}</p>
        <fieldset
          class="operation-form"
          :disabled="saving || !writable || selected.invoiced"
        >
          <label>开票状态<select v-model="form.invoiced"><option :value="false">待开票</option><option :value="true">已开票</option></select></label><label>发票号码<input
            v-model="form.invoiceNumber"
            :required="form.invoiced"
            maxlength="100"
          ></label><label>开票日期<input
            v-model="form.invoiceDate"
            type="date"
            :required="form.invoiced"
          ></label><label><input
            v-model="form.refreshFromOrder"
            type="checkbox"
          >从订单更新未开票内容</label>
        </fieldset>
        <OrderDocuments
          :order-id="selected.orderId"
          scope="INVOICE"
          :writable="writable"
        />
        <p>此处登记实际开票结果，不调用税务开票接口；已开票后锁定内容。</p><p
          v-if="error"
          role="alert"
          class="data-state--error"
        >
          {{ error }}
        </p><footer class="form-footer">
          <button
            type="button"
            class="secondary-action"
            :disabled="saving"
            @click="close"
          >
            关闭
          </button><button
            v-if="writable && !selected.invoiced"
            :disabled="saving"
          >
            保存
          </button>
        </footer>
      </form>
    </div>
  </section>
</template>
<style scoped>
.order-invoices{min-width:0;grid-template-columns:minmax(0,1fr)}.data-table{min-width:1050px}.invoice-card{box-sizing:border-box;width:min(1200px,100%);max-width:100%;min-width:0;margin:0;max-height:calc(100dvh - 32px);overflow:auto}.invoice-lines{overflow:auto}.invoice-card p{overflow-wrap:anywhere}.invoice-card .form-footer{position:sticky;bottom:-24px;background:white;padding:12px 0}
</style>
