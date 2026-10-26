<script setup lang="ts">
import ListPagination from '@/components/ListPagination.vue'
const listSize = ref(20)
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { invoicesApi, type Invoice, type InvoiceInput } from '@/api/invoices'
import type { CustomerProduct, InvoiceTitle } from '@/api/customers'
import { invoiceAmounts } from '@/ui/invoiceAmounts'
import { session } from '@/state/session'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import ShipmentCustomerPicker from '@/components/ShipmentCustomerPicker.vue'

const writable = computed(() => session.canAccess(['customers.write']))
const rows = ref<Invoice[]>([]), total = ref(0), page = ref(0)
const query = ref(''), shipped = ref(''), invoiced = ref('')
const loading = ref(false), saving = ref(false), selecting = ref(false), opened = ref(false), discard = ref(false)
const error = ref(''), success = ref(''), formError = ref(''), editingId = ref<string>(), customerName = ref('')
const dialog = ref<HTMLDialogElement>(), initial = ref('')
const blank = (): InvoiceInput => ({ customerProductId: '', invoiceTitle: '', taxNumber: '', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '', itemName: '', specification: '', unit: '件', quantity: 1, netUnitPrice: null, taxRate: 1, shipped: false, invoiced: false, logistics: '', version: undefined })
const form = reactive(blank())
const invoiceTitles = ref<InvoiceTitle[]>([]), selectedTitle = ref('')
function chooseTitle() {
  const entry = invoiceTitles.value[Number(selectedTitle.value)]
  for (const [key] of billingFields) form[key] = selectedTitle.value !== '' && entry ? entry[key] : ''
}
const amounts = computed(() => invoiceAmounts(form.quantity, form.netUnitPrice, form.taxRate))
const billingFields = [ ['invoiceTitle', '发票抬头', 200], ['taxNumber', '纳税人识别号', 64], ['registeredAddress', '注册地址', 500], ['registeredPhone', '注册电话', 32], ['bankName', '开户银行', 200], ['bankAccount', '银行账号', 64] ] as const
let loadSequence = 0, selectSequence = 0
const message = (e: unknown) => e instanceof Error ? e.message : '请求失败，请重试。'
async function load() {
  const seq = ++loadSequence; loading.value = true; error.value = ''
  try {
    const result = await invoicesApi.list({ q: query.value.trim(), shipped: shipped.value, invoiced: invoiced.value, page: page.value, size: listSize.value })
    if (seq !== loadSequence) return
    rows.value = result.items; total.value = result.totalCount
  } catch (e) { if (seq === loadSequence) { rows.value = []; total.value = 0; error.value = message(e) } }
  finally { if (seq === loadSequence) loading.value = false }
}
function search() { page.value = 0; void load() }
function turnPage(delta: number) { page.value += delta; void load() }
async function open(item?: Invoice) {
  invoiceTitles.value = []; selectedTitle.value = ''
  Object.assign(form, item ? item.details : blank()); editingId.value = item?.id; customerName.value = item?.customerName ?? ''
  formError.value = ''; discard.value = false; initial.value = JSON.stringify(form); opened.value = true
  await nextTick(); dialog.value?.showModal()
}
async function selectProduct(item?: CustomerProduct) {
  const seq = ++selectSequence
  invoiceTitles.value = []; selectedTitle.value = ''
  Object.assign(form, blank()); customerName.value = ''; formError.value = ''; selecting.value = Boolean(item)
  if (!item) return
  try {
    const draft = await invoicesApi.draft(item.id)
    if (seq !== selectSequence || !opened.value) return
    const { invoiceTitles: options, ...details } = draft
    invoiceTitles.value = options ?? []; selectedTitle.value = invoiceTitles.value.length === 1 ? '0' : ''
    Object.assign(form, details); chooseTitle(); customerName.value = item.customerName
  } catch (e) { if (seq === selectSequence) formError.value = message(e) }
  finally { if (seq === selectSequence) selecting.value = false }
}
function close() { ++selectSequence; selecting.value = false; dialog.value?.close(); opened.value = false; discard.value = false }
function requestClose() {
  if (saving.value) return
  if (initial.value !== JSON.stringify(form)) discard.value = true
  else close()
}
async function save() {
  if (saving.value || selecting.value) return
  formError.value = ''; success.value = ''
  if (!form.customerProductId) { formError.value = '请选择客户商品。'; return }
  if (!editingId.value && selectedTitle.value === '') { formError.value = '请选择客户已维护的发票抬头。'; return }
  if (!form.invoiceTitle.trim() || !form.itemName.trim() || !form.unit.trim()) { formError.value = '请填写发票抬头、品名和单位。'; return }
  if (!amounts.value) { formError.value = '请检查数量（大于0，最多3位小数）、不含税单价（非负，最多6位小数）和税率（0–100%，最多2位小数）。'; return }
  saving.value = true
  try {
    await invoicesApi.save(editingId.value, { ...form }); close(); success.value = '发票信息已保存。'; await load()
  } catch (e) { formError.value = message(e) }
  finally { saving.value = false }
}
onBeforeRouteLeave(() => {
  if (!opened.value) return true
  if (saving.value) return false
  if (initial.value !== JSON.stringify(form)) { discard.value = true; return false }
  close(); return true
})
onMounted(load)
</script>

<template>
  <section class="domain-grid invoice-management">
    <SuccessToast :message="success" />
    <SuccessToast
      :message="error"
      tone="error"
    />
    <div class="material-toolbar">
      <p>维护需要开票的客户商品信息；此处为台账，不执行税务开票或发货。</p><button
        v-if="writable"
        type="button"
        @click="open()"
      >
        新增发票信息
      </button>
    </div>
    <form
      class="panel operation-form filter-panel filter-bar"
      @submit.prevent="search"
    >
      <label>关键字<input
        v-model="query"
        maxlength="128"
        placeholder="搜索客户、抬头、品名或规格"
      ></label>
      <label>开票状态<select v-model="invoiced"><option value="">全部</option><option value="false">未开票</option><option value="true">已开票</option></select></label>
      <button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询' }}
      </button>
    </form>
    <article
      class="panel table-panel"
      :aria-busy="loading"
    >
      <p
        v-if="loading"
        class="data-state"
      >
        正在加载…
      </p>
      <TableViewport v-else>
        <table class="data-table">
          <thead>
            <tr>
              <th>客户名称</th><th>发票抬头信息</th><th>品名</th><th>规格及型号</th><th>单位</th><th>数量</th><th>不含税单价（RMB）</th><th>税率</th><th>总价（含税 RMB）</th><th>是否已开票</th><th
                v-if="writable"
                class="invoice-actions"
              >
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in rows"
              :key="item.id"
            >
              <td>{{ item.customerName }}</td><td class="invoice-long">
                <strong>{{ item.details.invoiceTitle }}</strong><details>
                  <summary>查看抬头详情</summary><p
                    v-for="[key, label] in billingFields.slice(1)"
                    :key="key"
                  >
                    {{ label }}：{{ item.details[key] || '—' }}
                  </p>
                </details>
              </td>
              <td>{{ item.details.itemName }}</td><td>{{ item.details.specification || '—' }}</td><td>{{ item.details.unit }}</td><td>{{ item.details.quantity }}</td><td>{{ item.details.netUnitPrice }}</td><td>{{ item.details.taxRate }}%</td>
              <td class="invoice-money">
                <strong>¥{{ item.totalAmount }}</strong><small>不含税 ¥{{ item.netAmount }}</small><small>税额 ¥{{ item.taxAmount }}</small>
              </td>
              <td><span :class="item.details.invoiced ? 'state-done' : 'state-pending'">{{ item.details.invoiced ? '已开票' : '未开票' }}</span></td>
              <td
                v-if="writable"
                class="invoice-actions"
              >
                <button
                  type="button"
                  @click="open(item)"
                >
                  编辑
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
        {{ error ? '加载失败，请重试。' : '暂无发票信息。' }}
      </p>
      <ListPagination
        v-model:page-size="listSize"
        :page="page + 1"
        :total="total"
        :disabled="loading"
        @update:page="turnPage($event - 1 - page)"
      />
    </article>
    <dialog
      v-if="opened"
      ref="dialog"
      class="invoice-dialog"
      aria-label="发票信息"
      @cancel.prevent="discard ? discard = false : requestClose()"
    >
      <div
        v-if="discard"
        class="panel operation-form operation-dialog__card"
      >
        <h2 class="form-wide">
          放弃未保存的修改？
        </h2><p class="form-wide">
          已填写的信息尚未保存。
        </p><button
          type="button"
          @click="discard = false"
        >
          继续填写
        </button><button
          type="button"
          class="danger-button"
          @click="close"
        >
          放弃修改
        </button>
      </div>
      <form
        v-show="!discard"
        class="panel operation-form operation-dialog__card"
        @submit.prevent="save"
      >
        <h2 class="form-wide">
          {{ editingId ? '编辑' : '新增' }}发票信息
        </h2><button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭表单"
          :disabled="saving"
          @click="requestClose"
        >
          ×
        </button>
        <ShipmentCustomerPicker
          v-if="!editingId"
          :disabled="saving || selecting"
          label-prefix="开票"
          @select="selectProduct"
        />
        <p class="form-wide">
          客户：{{ customerName || '请选择客户商品' }}<small v-if="editingId">客户商品不可更换；修改客户主档不会覆盖此处的开票信息。</small>
        </p>
        <fieldset
          class="invoice-fields form-wide"
          :disabled="saving || selecting"
        >
          <legend>开票信息</legend>
          <label
            v-if="!editingId"
            class="form-wide"
          >选择发票抬头 *
            <select
              v-model="selectedTitle"
              required
              :disabled="!invoiceTitles.length"
              @change="chooseTitle"
            >
              <option value="">请选择发票抬头</option>
              <option
                v-for="(entry, index) in invoiceTitles"
                :key="index"
                :value="String(index)"
              >{{ entry.invoiceTitle }}{{ entry.taxNumber ? ' · ' + entry.taxNumber : '' }}</option>
            </select>
            <small>{{ invoiceTitles.length === 1 ? '该客户只有一个抬头，已自动带出。' : invoiceTitles.length > 1 ? '该客户有多个抬头，请选择本次开票抬头。' : '请先选择客户商品；如客户尚无抬头，请到客户信息中维护。' }}</small>
          </label>
          <label
            v-for="[key, label, max] in billingFields"
            :key="key"
          >{{ label }}{{ key === 'invoiceTitle' ? ' *' : '' }}<input
            v-model="form[key]"
            readonly
            :required="key === 'invoiceTitle'"
            :maxlength="max"
          ></label>
          <label>品名 *<input
            v-model="form.itemName"
            required
            maxlength="200"
          ></label><label>规格及型号<input
            v-model="form.specification"
            maxlength="200"
            placeholder="填写实际开票规格及型号"
          ></label>
          <label>单位 *<input
            v-model="form.unit"
            required
            maxlength="16"
          ></label><label>数量 *<input
            v-model="form.quantity"
            type="number"
            min="0.001"
            max="999999999.999"
            step="0.001"
            required
          ></label>
          <label>不含税单价（RMB）*<input
            v-model="form.netUnitPrice"
            type="number"
            min="0"
            max="99999999.999999"
            step="0.000001"
            required
            placeholder="请确认实际不含税价格"
          ></label><label>税率（%）*<input
            v-model="form.taxRate"
            type="number"
            min="0"
            max="100"
            step="0.01"
            required
          ></label>
          <p class="form-wide invoice-totals">
            不含税金额：¥{{ amounts?.net ?? '—' }} 税额：¥{{ amounts?.tax ?? '—' }} <strong>总价（含税）：¥{{ amounts?.total ?? '—' }}</strong>
          </p>
          <p class="form-wide invoice-help">
            金额按人民币四舍五入到分。税率默认 1%，请按本次业务确认；供货价未约定含税口径，因此不自动作为不含税单价。
          </p>
          <label>是否已开票<select v-model="form.invoiced"><option :value="false">未开票</option><option :value="true">已开票</option></select></label>
        </fieldset>
        <p
          v-if="formError"
          class="form-wide data-state--error"
          role="alert"
        >
          {{ formError }}
        </p>
        <div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="requestClose"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving || selecting"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.invoice-management { min-width: 0; grid-template-columns: minmax(0, 1fr); }
.invoice-management .filter-bar { grid-template-columns: minmax(220px, 2fr) repeat(2, minmax(120px, 1fr)) auto; }
.table-panel { min-width: 0; max-width: 100%; }
.data-table { min-width: 1800px; }
.invoice-actions { position: sticky; right: 0; min-width: 80px; background: white; z-index: 2; box-shadow: -5px 0 8px -5px #9aa4b5; }
thead .invoice-actions { z-index: 5; background: #f8f9fb; }
.invoice-long { min-width: 220px; max-width: 320px; white-space: pre-wrap; overflow-wrap: anywhere; }
.invoice-long p { margin: 6px 0; }
.invoice-money { white-space: nowrap; }
small { display: block; color: #68758c; margin-top: 6px; }
.state-done { color: #168252; }.state-pending { color: #a35d12; }
.invoice-dialog { padding: 0; border: 0; border-radius: 14px; width: min(850px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); overflow: auto; }
.invoice-dialog::backdrop { background: #17213b66; }
.invoice-dialog .operation-dialog__card { width: 100%; max-height: none; margin: 0; box-sizing: border-box; }
.invoice-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; min-width: 0; border: 1px solid #dbe1ea; border-radius: 8px; padding: 16px; }
.invoice-help { color: #68758c; font-size: 13px; margin: 0; }
.invoice-totals { background: #f3f6fc; border-radius: 8px; padding: 12px; }
.pagination { flex-wrap: wrap; }
@media (max-width: 900px) { .invoice-management .filter-bar { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .invoice-fields, .invoice-management .filter-bar { grid-template-columns: minmax(0, 1fr); } }
</style>
