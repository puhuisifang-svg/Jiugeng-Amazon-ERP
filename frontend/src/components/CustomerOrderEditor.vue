<script setup lang="ts">
import { vSingleSelect } from '@/directives/singleSelect'
import CustomerOrderProductPicker from './CustomerOrderProductPicker.vue'
import DatePicker from '@/components/DatePicker.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
import { onBeforeRouteLeave } from 'vue-router'
import { customersApi, type Customer, type CustomerProduct } from '@/api/customers'
import type { CustomerOrder, OrderInput } from '@/api/orders'
import { invoiceAmounts } from '@/ui/invoiceAmounts'
const props = defineProps<{ initial?: CustomerOrder | undefined; busy: boolean; error: string }>()
const emit = defineEmits<{ save: [input: OrderInput]; close: [] }>()
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const form = reactive<OrderInput>(props.initial ? { ...JSON.parse(JSON.stringify(props.initial.details)), lines: props.initial.lines.map(l => ({ ...l.details, id: l.id })), version: props.initial.version } : { customerId: '', source: 'OFFLINE', externalNumber: '', contractNumber: '', orderDate: today, deliveryDate: today, invoiceRequired: false, invoiceTitle: '', taxNumber: '', recipient: '', phone: '', address: '', remark: '', lines: [] })
const initialSnapshot = ref(JSON.stringify(form)), discard = ref(false), localError = ref(''), selecting = ref(false)
const customers = ref<Customer[]>([]), chosen = ref<Customer>(), pickerOpen=ref(false)
const titleIndex = ref('')
const orderReference=computed({get:()=>form.externalNumber||form.contractNumber||'',set:(value:string)=>{form.externalNumber=value;form.contractNumber=''}})
const titles = computed(() => chosen.value?.invoiceTitles ?? [])
async function searchCustomers() {
  selecting.value = true
  try {const all:Customer[]=[];let page=0;while(true){const result=await customersApi.list({status:'ACTIVE',size:100,page});all.push(...result.items);if(all.length>=result.totalCount||!result.items.length)break;page++}customers.value=all}
  catch (e) { localError.value = String(e) } finally { selecting.value = false }
}
async function changeCustomer() {
  chosen.value = customers.value.find(c => c.id === form.customerId)
  form.lines = []; pickerOpen.value=false; titleIndex.value = ''; form.invoiceTitle = ''; form.taxNumber = ''
  form.recipient = chosen.value?.contactPerson ?? ''; form.phone = chosen.value?.phone ?? ''; form.address = chosen.value?.address ?? ''
  if (titles.value.length === 1) { titleIndex.value = '0'; chooseTitle() }
}
function chooseTitle() { const t = titleIndex.value === 'snapshot' ? props.initial?.invoiceTitle : titleIndex.value === '' ? undefined : titles.value[Number(titleIndex.value)]; form.invoiceTitle = t?.invoiceTitle ?? ''; form.taxNumber = t?.taxNumber ?? '' }
function addProducts(products:CustomerProduct[]) {
 for(const p of products){if(form.lines.some(line=>line.customerProductId===p.id))continue
  form.lines.push({ customerProductId: p.id, name: p.productName, specification: '', unit: p.priceUnit || '套', quantity: 1, netUnitPrice: '', taxRate: 1, deliveryDate: form.deliveryDate, remark: p.requirements || '' })
 }
 pickerOpen.value=false
}
function close() { if (props.busy) return; if (initialSnapshot.value !== JSON.stringify(form)) discard.value = true; else emit('close') }
function submit() {
  if (props.busy || selecting.value) return
  if (!form.lines.length || form.lines.some(l => !invoiceAmounts(l.quantity, l.netUnitPrice, l.taxRate))) { localError.value = '请添加商品并检查数量、单价和税率。'; return }
  emit('save', structuredClone({ ...form, lines: form.lines.map(l => ({ ...l })) }))
}
onBeforeRouteLeave(() => { if (props.busy) return false; if (initialSnapshot.value !== JSON.stringify(form)) { discard.value = true; return false } return true })
onMounted(async () => {
  await searchCustomers()
  if (!props.initial) {
    try { const customer = await customersApi.defaultCustomer(); if (customer?.status === 'ACTIVE') { if (!customers.value.some(c => c.id === customer.id)) customers.value.push(customer); form.customerId = customer.id; await changeCustomer(); initialSnapshot.value = JSON.stringify(form) } } catch (e) { localError.value = String(e) }
  } else {
    chosen.value = customers.value.find(c => c.id === form.customerId)
    if (!chosen.value) { const matches = await customersApi.list({ q: props.initial.customerName, size: 100 }); chosen.value = matches.items.find(c => c.id === form.customerId); if (chosen.value) customers.value.push(chosen.value) }
    const index = titles.value.findIndex(t => t.invoiceTitle === form.invoiceTitle && t.taxNumber === form.taxNumber)
    titleIndex.value = index < 0 && form.invoiceTitle ? 'snapshot' : index < 0 ? '' : String(index)
  }
})
</script>
<template>
  <div
    class="operation-dialog"
    role="dialog"
    aria-modal="true"
    aria-label="客户订单表单"
    @keydown.esc.stop="close"
  >
    <form
      class="panel operation-dialog__card order-editor"
      @submit.prevent="submit"
    >
      <h2>{{ props.initial ? '编辑客户订单' : '新增客户订单' }}</h2>
      <UnsavedChangesDialog
        v-if="discard"
        @keep="discard = false"
        @discard="emit('close')"
      />
      <div class="order-editor-body">
        <fieldset
          class="operation-form"
          :disabled="selecting || props.busy"
        >
          <h3 class="order-section-title">
            订单信息
          </h3>
          <label>客户 *<select
            v-model="form.customerId"
            v-single-select
            required
            @change="changeCustomer"
          ><option value="">请选择客户</option><option
            v-for="c in customers"
            :key="c.id"
            :value="c.id"
          >{{ c.name }}</option></select></label>
          <label>来源<select
            v-model="form.source"
            v-single-select
          ><option value="OFFLINE">线下</option><option value="1688">1688</option></select></label>
          <label>客户订单号 / 合同号<input
            v-model="orderReference"
            maxlength="100"
            :required="form.source === '1688'"
          ></label>
          <label>下单日期<DatePicker
            v-model="form.orderDate"
            type="date"
            required
          /></label><label>交期<DatePicker
            v-model="form.deliveryDate"
            type="date"
            required
          /></label>
          <label>是否需要开票<select
            v-model="form.invoiceRequired"
            v-single-select
          ><option :value="false">否</option><option :value="true">是</option></select></label>
          <label v-if="form.invoiceRequired">发票抬头<select
            v-model="titleIndex"
            v-single-select
            @change="chooseTitle"
          ><option value="">请选择（无抬头可先存草稿）</option><option
            v-if="props.initial?.invoiceTitle.invoiceTitle"
            value="snapshot"
          >原订单抬头：{{ props.initial.invoiceTitle.invoiceTitle }}</option><option
            v-for="(t,i) in titles"
            :key="i"
            :value="String(i)"
          >{{ t.invoiceTitle }} · {{ t.taxNumber }}</option></select></label>
          <h3 class="order-section-title">
            发货信息
          </h3>
          <label>收货人<input
            v-model="form.recipient"
            maxlength="100"
          ></label><label>联系电话<input
            v-model="form.phone"
            maxlength="32"
          ></label><label class="shipping-address">发货地址<input
            v-model="form.address"
            maxlength="500"
          ></label>
          <div class="order-line-toolbar">
            <h3>订单商品 <small>共 {{ form.lines.length }} 项</small></h3><button
              type="button"
              :disabled="!form.customerId"
              @click="pickerOpen=true"
            >
              选择客户商品
            </button>
          </div>
          <p class="order-line-hint">
            请填写本单不含税单价；宽表可左右滚动查看全部字段。
          </p>
          <div
            class="form-wide order-lines-scroll"
            tabindex="0"
            aria-label="订单商品明细，可左右滚动"
          >
            <table class="data-table">
              <colgroup><col style="width:220px"><col style="width:160px"><col style="width:80px"><col style="width:110px"><col style="width:130px"><col style="width:90px"><col style="width:120px"><col style="width:160px"><col style="width:220px"><col style="width:76px"></colgroup>
              <thead><tr><th>品名</th><th>规格型号</th><th>单位</th><th>数量</th><th>不含税单价</th><th>税率%</th><th>含税金额</th><th>交期</th><th>特殊需求/备注</th><th>操作</th></tr></thead><tbody>
                <tr
                  v-for="(l,i) in form.lines"
                  :key="i"
                >
                  <td>
                    <textarea
                      v-model="l.name"
                      aria-label="品名"
                      required
                      maxlength="128"
                      rows="2"
                    />
                  </td><td>
                    <input
                      v-model="l.specification"
                      aria-label="规格型号"
                      maxlength="200"
                    >
                  </td><td>
                    <input
                      v-model="l.unit"
                      aria-label="单位"
                      required
                      maxlength="16"
                    >
                  </td><td>
                    <input
                      v-model="l.quantity"
                      aria-label="数量"
                      required
                      type="number"
                      min="0.001"
                      max="999999999.999"
                      step="0.001"
                    >
                  </td><td>
                    <input
                      v-model="l.netUnitPrice"
                      aria-label="不含税单价"
                      required
                      type="number"
                      min="0"
                      step="0.000001"
                    >
                  </td><td>
                    <input
                      v-model="l.taxRate"
                      aria-label="税率"
                      required
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                    >
                  </td><td>{{ invoiceAmounts(l.quantity,l.netUnitPrice,l.taxRate)?.total ?? '—' }}</td><td>
                    <DatePicker
                      v-model="l.deliveryDate"
                      aria-label="商品交期"
                      required
                      type="date"
                    />
                  </td><td>
                    <input
                      v-model="l.remark"
                      aria-label="明细备注"
                      maxlength="500"
                    >
                  </td><td>
                    <button
                      type="button"
                      class="order-line-remove"
                      @click="form.lines.splice(i,1)"
                    >
                      移除
                    </button>
                  </td>
                </tr>
                <tr v-if="!form.lines.length">
                  <td
                    colspan="10"
                    class="order-lines-empty"
                  >
                    请点击“选择客户商品”添加订单明细
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <label class="form-wide">订单备注<textarea
            v-model="form.remark"
            maxlength="1000"
            rows="2"
          /></label>
        </fieldset>
      </div>
      <p
        v-if="localError || props.error"
        role="alert"
        class="data-state data-state--error"
      >
        {{ props.error || localError }}
      </p>
      <footer class="form-footer">
        <button
          type="button"
          class="secondary-action"
          :disabled="props.busy"
          @click="close"
        >
          取消
        </button><button
          type="submit"
          :disabled="props.busy || selecting"
        >
          {{ props.busy ? '保存中…' : props.initial ? '保存修改' : '保存' }}
        </button>
      </footer>
    </form>
    <CustomerOrderProductPicker
      v-if="pickerOpen"
      :customer-id="form.customerId"
      :existing="form.lines.map(line=>line.customerProductId)"
      @close="pickerOpen=false"
      @confirm="addProducts"
    />
  </div>
</template>
<style scoped>
.order-editor{box-sizing:border-box;width:min(1400px,100%);max-width:100%;min-width:0;min-height:0;margin:0;max-height:calc(100dvh - 64px);display:flex;flex-direction:column;overflow:hidden}.order-editor-body{flex:1;min-height:0;min-width:0;overflow:auto}.order-editor fieldset{min-width:0;border:0;padding:8px;margin:0}.order-editor label{align-self:start;min-width:0}.order-lines-scroll{overflow:auto;min-width:0}.order-lines-scroll table{min-width:1300px}.order-lines-scroll input{min-width:0;width:100%;box-sizing:border-box}.order-lines-scroll textarea{min-width:0;width:100%;box-sizing:border-box;padding:8px!important;min-height:64px!important;height:64px}.form-footer{flex-shrink:0;background:white;padding-top:12px}.order-editor h2{margin:8px 0;flex-shrink:0}
.order-editor .form-wide,.order-section-title,.order-line-toolbar,.order-line-hint{grid-column:1 / -1}.order-section-title{border-bottom:1px solid var(--line);padding:6px 0 8px;margin:0;font-size:14px}.order-line-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.order-line-toolbar h3{margin:0}.order-line-toolbar small{font-weight:400;color:var(--muted);margin-left:8px}.order-line-hint{margin:0;color:var(--muted);font-size:12px}.order-lines-scroll{max-height:380px}.order-lines-scroll table{table-layout:fixed;width:1466px}.order-lines-scroll th{position:sticky;top:0;background:#f5f7fa;z-index:2}.order-lines-scroll td:first-child,.order-lines-scroll th:first-child{position:sticky;left:0;background:white;z-index:1}.order-lines-scroll td:last-child,.order-lines-scroll th:last-child{position:sticky;right:0;background:white;z-index:1}.order-lines-scroll th:first-child,.order-lines-scroll th:last-child{background:#f5f7fa;z-index:3}.order-editor .order-line-remove{background:transparent;color:#b42318;padding:0 4px;min-height:28px}.order-lines-empty{text-align:center;color:var(--muted);height:80px}.order-editor .shipping-address{grid-column:3}.order-editor .operation-form{grid-template-columns:repeat(3,minmax(0,1fr))}
@media(max-width:800px){.order-editor .operation-form{grid-template-columns:repeat(2,minmax(0,1fr))}.order-editor .shipping-address{grid-column:1 / -1}.order-lines-scroll td:first-child,.order-lines-scroll th:first-child,.order-lines-scroll td:last-child,.order-lines-scroll th:last-child{position:static}}
@media(max-width:640px){.order-editor{max-height:calc(100dvh - 24px);padding:12px}}
</style>
