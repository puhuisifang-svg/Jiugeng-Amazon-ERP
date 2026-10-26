<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { customersApi, type Customer, type CustomerProduct } from '@/api/customers'
import type { CustomerOrder, OrderInput } from '@/api/orders'
import { invoiceAmounts } from '@/ui/invoiceAmounts'
const props = defineProps<{ initial?: CustomerOrder | undefined; busy: boolean; error: string }>()
const emit = defineEmits<{ save: [input: OrderInput]; close: [] }>()
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
const form = reactive<OrderInput>(props.initial ? { ...JSON.parse(JSON.stringify(props.initial.details)), lines: props.initial.lines.map(l => ({ ...l.details, id: l.id })), version: props.initial.version } : { customerId: '', source: 'OFFLINE', externalNumber: '', contractNumber: '', orderDate: today, deliveryDate: today, invoiceRequired: false, invoiceTitle: '', taxNumber: '', recipient: '', phone: '', address: '', remark: '', lines: [] })
const initialSnapshot = ref(JSON.stringify(form)), discard = ref(false), localError = ref(''), selecting = ref(false)
const customers = ref<Customer[]>([]), products = ref<CustomerProduct[]>([]), chosen = ref<Customer>()
const customerQuery = ref(''), productQuery = ref(''), selectedProduct = ref(''), titleIndex = ref('')
let sequence = 0
const titles = computed(() => chosen.value?.invoiceTitles ?? [])
async function searchCustomers() {
  selecting.value = true
  try { customers.value = (await customersApi.list({ q: customerQuery.value, status: 'ACTIVE', size: 100 })).items }
  catch (e) { localError.value = String(e) } finally { selecting.value = false }
}
async function searchProducts() {
  const seq = ++sequence; selecting.value = true; localError.value = ''
  try { const result = await customersApi.products({ customerId: form.customerId, q: productQuery.value, status: 'ACTIVE', size: 100 }); if (seq === sequence) products.value = result.items }
  catch (e) { if (seq === sequence) localError.value = String(e) } finally { if (seq === sequence) selecting.value = false }
}
async function changeCustomer() {
  chosen.value = customers.value.find(c => c.id === form.customerId)
  form.lines = []; products.value = []; selectedProduct.value = ''; titleIndex.value = ''; form.invoiceTitle = ''; form.taxNumber = ''
  form.recipient = chosen.value?.contactPerson ?? ''; form.phone = chosen.value?.phone ?? ''; form.address = chosen.value?.address ?? ''
  if (titles.value.length === 1) { titleIndex.value = '0'; chooseTitle() }
  if (form.customerId) await searchProducts()
}
function chooseTitle() { const t = titleIndex.value === 'snapshot' ? props.initial?.invoiceTitle : titleIndex.value === '' ? undefined : titles.value[Number(titleIndex.value)]; form.invoiceTitle = t?.invoiceTitle ?? ''; form.taxNumber = t?.taxNumber ?? '' }
function addLine() {
  const p = products.value.find(p => p.id === selectedProduct.value); if (!p) return
  form.lines.push({ customerProductId: p.id, name: p.productName, specification: '', unit: p.priceUnit || '套', quantity: 1, netUnitPrice: '', taxRate: 1, deliveryDate: form.deliveryDate, remark: p.requirements || '' })
  selectedProduct.value = ''
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
    titleIndex.value = index < 0 && form.invoiceTitle ? 'snapshot' : index < 0 ? '' : String(index); await searchProducts()
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
      <p
        v-if="discard"
        role="alert"
      >
        是否放弃未保存内容？<button
          type="button"
          @click="discard = false"
        >
          继续填写
        </button><button
          type="button"
          class="danger-button"
          @click="emit('close')"
        >
          放弃修改
        </button>
      </p>
      <div class="order-editor-body">
        <fieldset
          class="operation-form"
          :disabled="selecting || props.busy"
        >
          <label>搜索客户<input
            v-model="customerQuery"
            placeholder="客户名称或1688账号"
          ><button
            type="button"
            @click="searchCustomers"
          >搜索</button></label>
          <label>客户 *<select
            v-model="form.customerId"
            required
            @change="changeCustomer"
          ><option value="">请选择客户</option><option
            v-for="c in customers"
            :key="c.id"
            :value="c.id"
          >{{ c.name }}</option></select></label>
          <label>来源<select v-model="form.source"><option value="OFFLINE">线下</option><option value="1688">1688</option></select></label>
          <label>客户订单号<input
            v-model="form.externalNumber"
            maxlength="100"
            :required="form.source === '1688'"
          ></label>
          <label>合同号<input
            v-model="form.contractNumber"
            maxlength="100"
          ></label>
          <label>下单日期<input
            v-model="form.orderDate"
            type="date"
            required
          ></label><label>交期<input
            v-model="form.deliveryDate"
            type="date"
            required
          ></label>
          <label>是否需要开票<select v-model="form.invoiceRequired"><option :value="false">否</option><option :value="true">是</option></select></label>
          <label v-if="form.invoiceRequired">发票抬头<select
            v-model="titleIndex"
            @change="chooseTitle"
          ><option value="">请选择（无抬头可先存草稿）</option><option
            v-if="props.initial?.invoiceTitle.invoiceTitle"
            value="snapshot"
          >原订单抬头：{{ props.initial.invoiceTitle.invoiceTitle }}</option><option
            v-for="(t,i) in titles"
            :key="i"
            :value="String(i)"
          >{{ t.invoiceTitle }} · {{ t.taxNumber }}</option></select></label>
          <label>收货人<input
            v-model="form.recipient"
            maxlength="100"
          ></label><label>联系电话<input
            v-model="form.phone"
            maxlength="32"
          ></label><label class="form-wide">发货地址<input
            v-model="form.address"
            maxlength="500"
          ></label>
          <label>搜索客户商品<input
            v-model="productQuery"
            placeholder="品名或客户商品编码"
          ><button
            type="button"
            :disabled="!form.customerId"
            @click="searchProducts"
          >搜索商品</button></label>
          <label>添加商品<select v-model="selectedProduct"><option value="">请选择客户商品</option><option
            v-for="p in products"
            :key="p.id"
            :value="p.id"
          >{{ p.productName }} · {{ p.customerProductCode || p.skuCode }}</option></select><button
            type="button"
            :disabled="!selectedProduct"
            @click="addLine"
          >添加明细</button></label>
          <p class="form-wide">
            每次检索最多展示100项，可输入关键词缩小范围。供货价未约定含税口径，请明确填写本单不含税单价。
          </p>
          <div class="form-wide order-lines-scroll">
            <table class="data-table">
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
                    <input
                      v-model="l.deliveryDate"
                      aria-label="商品交期"
                      required
                      type="date"
                    >
                  </td><td>
                    <input
                      v-model="l.remark"
                      aria-label="明细备注"
                      maxlength="500"
                    >
                  </td><td>
                    <button
                      type="button"
                      @click="form.lines.splice(i,1)"
                    >
                      移除
                    </button>
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
          {{ props.busy ? '保存中…' : props.initial ? '保存修改' : '保存草稿' }}
        </button>
      </footer>
    </form>
  </div>
</template>
<style scoped>
.order-editor{box-sizing:border-box;width:min(1400px,100%);max-width:100%;min-width:0;min-height:0;margin:0;max-height:calc(100dvh - 64px);display:flex;flex-direction:column;overflow:hidden}.order-editor-body{flex:1;min-height:0;min-width:0;overflow:auto}.order-editor fieldset{min-width:0;border:0;padding:8px;margin:0}.order-editor label{align-self:start;min-width:0}.order-lines-scroll{overflow:auto;min-width:0}.order-lines-scroll table{min-width:1300px}.order-lines-scroll input{min-width:95px;width:100%}.order-lines-scroll textarea{min-width:200px}.form-footer{flex-shrink:0;background:white;padding-top:12px}.order-editor h2{margin:8px 0;flex-shrink:0}
@media(max-width:640px){.order-editor{max-height:calc(100dvh - 24px);padding:12px}}
</style>
