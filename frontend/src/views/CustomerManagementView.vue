<script setup lang="ts">
import ListPagination from '@/components/ListPagination.vue'
const listSize = ref(20)
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, RouterLink, useRoute } from 'vue-router'
import { customersApi, type Customer, type CustomerInput, type CustomerProduct, type CustomerProductInput, type CustomerStatus, type InvoiceTitle } from '@/api/customers'
import { domainApi, type SkuListItemDto } from '@/api/domain'
import { session } from '@/state/session'
import CustomerLabelTemplates from '@/components/CustomerLabelTemplates.vue'
import CatalogDeleteDialog from '@/components/CatalogDeleteDialog.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'

const props = defineProps<{ kind: 'info' | 'products' }>()
const route = useRoute()
const isProduct = computed(() => props.kind === 'products')
const title = computed(() => isProduct.value ? '客户商品' : '客户信息')
const writable = computed(() => session.canAccess(['customers.write']))
const customerRows = ref<Customer[]>([]), productRows = ref<CustomerProduct[]>([])
const query = ref(''), status = ref<CustomerStatus | ''>(''), page = ref(0), total = ref(0)
const loading = ref(false), saving = ref(false), success = ref(''), error = ref(''), formError = ref('')
const templateCustomer = ref<Customer>()
const deleteTarget = ref<CustomerProduct>(), deleting = ref(false), deleteError = ref('')
function requestDelete(item: CustomerProduct) { deleteTarget.value = item; deleteError.value = '' }
async function confirmDelete() {
  if (deleting.value || !deleteTarget.value) return
  deleting.value = true; deleteError.value = ''
  try {
    await customersApi.deleteProduct(deleteTarget.value.id, deleteTarget.value.version)
    deleteTarget.value = undefined; page.value = 0; success.value = '客户商品已删除，源产品及 SKU 保留。'; await load()
  } catch (e) { deleteError.value = message(e) }
  finally { deleting.value = false }
}
const opened = ref(false), discard = ref(false), editingId = ref<string>()
const dialog = ref<HTMLDialogElement>(), continueButton = ref<HTMLButtonElement>()
const selectionSummary = ref(''), initial = ref('')
const blankTitle = (): InvoiceTitle => ({ invoiceTitle: '', taxNumber: '', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '' })
const titleFields = [['invoiceTitle', '开票抬头', 200], ['taxNumber', '纳税人识别号', 64], ['registeredAddress', '注册地址', 500], ['registeredPhone', '注册电话', 32], ['bankName', '开户银行', 200], ['bankAccount', '银行账号', 64]] as const
const blankCustomer = (): CustomerInput => ({ invoiceTitles: [blankTitle()], name: '', account1688: '', contactPerson: '', phone: '', wechatId: '', invoiceTitle: '', taxNumber: '', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '', defaultCustomer: false, address: '', remark: '', status: 'ACTIVE', version: undefined })
const blankProduct = (): CustomerProductInput => ({ customerId: '', productId: '', variantId: '', supplyPrice: '', priceUnit: '件', customerProductCode: '', requirements: '', status: 'ACTIVE', version: undefined })
const customerForm = reactive(blankCustomer()), productForm = reactive(blankProduct())
const snapshot = () => JSON.stringify(isProduct.value ? productForm : customerForm)
const customerFilter = computed(() => typeof route.query.customerId === 'string' ? route.query.customerId : '')
const customerQuery = ref(''), skuQuery = ref(''), customerPage = ref(0), skuPage = ref(0)
const customerOptions = ref<Customer[]>([]), skuOptions = ref<SkuListItemDto[]>([])
const customerCount = ref(0), skuCount = ref(0), picking = ref(false)
const selectedCustomer = ref<Customer>(), selectedSku = ref<SkuListItemDto>()
let loadSequence = 0, pickerSequence = 0
function message(exception: unknown) { return exception instanceof Error ? exception.message : '请求失败，请重试。' }
async function load() {
  const sequence = ++loadSequence; loading.value = true; error.value = ''
  try {
    const filters = { q: query.value.trim(), status: status.value, page: page.value, size: listSize.value }
    const result = isProduct.value ? await customersApi.products({ ...filters, customerId: customerFilter.value }) : await customersApi.list(filters)
    if (sequence !== loadSequence) return
    if (isProduct.value) productRows.value = result.items as CustomerProduct[]
    else customerRows.value = result.items as Customer[]
    total.value = result.totalCount
  } catch (exception) {
    if (sequence === loadSequence) { error.value = message(exception); customerRows.value = []; productRows.value = []; total.value = 0 }
  }
  finally { if (sequence === loadSequence) loading.value = false }
}
function search() { page.value = 0; void load() }
function turnPage(delta: number) { page.value += delta; void load() }
async function findOptions(kind: 'customer' | 'sku', reset = false) {
  const sequence = ++pickerSequence; picking.value = true; formError.value = ''
  try {
    if (kind === 'customer') {
      if (reset) customerPage.value = 0
      const result = await customersApi.list({ q: customerQuery.value.trim(), status: 'ACTIVE', page: customerPage.value, size: 20 })
      if (sequence !== pickerSequence) return
      customerOptions.value = result.items; customerCount.value = result.totalCount
    } else {
      if (reset) skuPage.value = 0
      const result = await domainApi.skus({ q: skuQuery.value.trim(), exact: false, status: 'ACTIVE', productStatus: 'ACTIVE', page: skuPage.value, size: 20 })
      if (sequence !== pickerSequence) return
      skuOptions.value = result.items; skuCount.value = result.totalCount
    }
  } catch (exception) { if (sequence === pickerSequence) formError.value = message(exception) }
  finally { if (sequence === pickerSequence) picking.value = false }
}
function pickCustomer(event: Event) {
  selectedCustomer.value = customerOptions.value.find(item => item.id === (event.target as HTMLSelectElement).value)
  productForm.customerId = selectedCustomer.value?.id ?? ''
}
function pickSku(event: Event) {
  selectedSku.value = skuOptions.value.find(item => item.variant.id === (event.target as HTMLSelectElement).value)
  productForm.productId = selectedSku.value?.productId ?? ''; productForm.variantId = selectedSku.value?.variant.id ?? ''
}
async function open(item?: Customer | CustomerProduct) {
  editingId.value = item?.id; discard.value = false; formError.value = ''; success.value = ''
  Object.assign(customerForm, blankCustomer()); Object.assign(productForm, blankProduct())
  customerOptions.value = []; skuOptions.value = []; customerCount.value = 0; skuCount.value = 0
  customerPage.value = 0; skuPage.value = 0; customerQuery.value = ''; skuQuery.value = ''
  selectedCustomer.value = undefined; selectedSku.value = undefined; selectionSummary.value = ''
  if (item && 'customerId' in item) {
    Object.assign(productForm, { customerId: item.customerId, productId: item.productId, variantId: item.variantId,
      supplyPrice: item.supplyPrice == null ? '' : String(item.supplyPrice), priceUnit: item.priceUnit, customerProductCode: item.customerProductCode,
      requirements: item.requirements, status: item.status, version: item.version })
    selectionSummary.value = `${item.customerName} · ${item.productName} · SKU：${item.skuCode}`
  } else if (item) {
    Object.assign(customerForm, { invoiceTitles: item.invoiceTitles?.map(t => ({ ...t })) ?? [{ ...blankTitle(), invoiceTitle: item.invoiceTitle ?? '', taxNumber: item.taxNumber ?? '', registeredAddress: item.registeredAddress ?? '', registeredPhone: item.registeredPhone ?? '', bankName: item.bankName ?? '', bankAccount: item.bankAccount ?? '' }], name: item.name, account1688: item.account1688, contactPerson: item.contactPerson,
      phone: item.phone, wechatId: item.wechatId, invoiceTitle: item.invoiceTitle ?? '', taxNumber: item.taxNumber ?? '', registeredAddress: item.registeredAddress ?? '', registeredPhone: item.registeredPhone ?? '', bankName: item.bankName ?? '', bankAccount: item.bankAccount ?? '', defaultCustomer: item.defaultCustomer ?? false, address: item.address, remark: item.remark, status: item.status, version: item.version })
  }
  initial.value = snapshot(); opened.value = true
  await nextTick(); dialog.value?.showModal(); dialog.value?.querySelector<HTMLInputElement>('input')?.focus()
  if (isProduct.value && !item) { await findOptions('customer'); if (opened.value) await findOptions('sku') }
}
async function chooseDefault(item: Customer) { await open(item); customerForm.defaultCustomer = true }
function close() { pickerSequence++; picking.value = false; dialog.value?.close(); opened.value = false; discard.value = false }
async function requestClose() {
  if (saving.value) return
  if (snapshot() === initial.value) { close(); return }
  discard.value = true; await nextTick(); continueButton.value?.focus()
}
async function keepEditing() { discard.value = false; await nextTick(); dialog.value?.querySelector<HTMLInputElement>('input')?.focus() }
function escape() { if (discard.value) void keepEditing(); else void requestClose() }
function guardNavigation() {
  if (!opened.value) return true
  if (saving.value) return false
  if (snapshot() !== initial.value) { void requestClose(); return false }
  close(); return true
}
onBeforeRouteLeave(guardNavigation); onBeforeRouteUpdate(guardNavigation)
async function save() {
  if (saving.value || discard.value) return
  formError.value = ''; success.value = ''
  if (isProduct.value && (!productForm.customerId || !productForm.variantId)) { formError.value = '请选择客户及具体 SKU。'; return }
  if (isProduct.value && productForm.supplyPrice !== '' && productForm.supplyPrice != null && !/^\d{1,12}(\.\d{1,2})?$/.test(String(productForm.supplyPrice))) { formError.value = '供货价须为非负金额，最多两位小数。'; return }
  saving.value = true
  try {
    if (isProduct.value) await customersApi.saveProduct(editingId.value, { ...productForm, supplyPrice: productForm.supplyPrice === '' ? null : productForm.supplyPrice })
    else {
      const invoiceTitles = (customerForm.invoiceTitles ?? []).filter(t => Object.values(t).some(v => v.trim()))
      if (invoiceTitles.some(t => !t.invoiceTitle.trim())) { formError.value = '请填写每组开票信息的抬头，或移除不用的信息。'; return }
      await customersApi.save(editingId.value, { ...customerForm, ...(invoiceTitles[0] ?? blankTitle()), invoiceTitles, name: customerForm.name.trim() })
    }
    close(); success.value = `${title.value}已保存。`; await load()
  } catch (exception) { formError.value = message(exception) }
  finally { saving.value = false }
}
watch(() => [props.kind, customerFilter.value], () => {
  query.value = ''; status.value = ''; page.value = 0; customerRows.value = []; productRows.value = []; total.value = 0
  success.value = ''; void load()
}, { immediate: true })
</script>

<template>
  <section class="page-stack customer-management">
    <CustomerLabelTemplates
      v-if="templateCustomer"
      :customer-id="templateCustomer.id"
      :customer-name="templateCustomer.name"
      @close="templateCustomer = undefined"
    />
    <CatalogDeleteDialog
      v-if="deleteTarget"
      title="删除客户商品"
      :name="deleteTarget.customerName + ' · ' + deleteTarget.skuCode + ' · ' + deleteTarget.productName"
      description="删除后不再显示于客户商品列表，也不能用于新建单据；不删除源产品、SKU 或其他客户的商品。"
      notice="历史单据及当时的商品信息保留，已有单据可继续处理。后台保留删除记录，页面暂不提供恢复入口。"
      :busy="deleting"
      :error="deleteError"
      @cancel="!deleting && (deleteTarget = undefined)"
      @confirm="confirmDelete"
    />
    <SuccessToast :message="success" /><SuccessToast
      :message="error"
      tone="error"
    />
    <div class="material-toolbar">
      <strong>{{ title }} · 共 {{ total }} 条</strong><button
        v-if="writable"
        type="button"
        @click="open()"
      >
        {{ isProduct ? '新增客户商品' : '新增客户' }}
      </button>
    </div>
    <form
      class="panel operation-form filter-panel supplier-filters filter-bar"
      @submit.prevent="search"
    >
      <label>关键字<input
        v-model="query"
        maxlength="128"
        :placeholder="isProduct ? '搜索客户、SKU、客户产品编码或特殊需求' : '搜索客户名称、1688账号、联系人或手机号'"
      ></label>
      <label>状态<select v-model="status"><option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
      <button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <RouterLink
        v-if="isProduct && customerFilter"
        to="/customers/products"
      >
        查看全部客户商品
      </RouterLink>
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
      <div
        v-else
        class="table-scroll"
      >
        <TableViewport>
          <table class="data-table">
            <thead>
              <tr v-if="!isProduct">
                <th>客户名称</th><th>1688账号</th><th>联系人</th><th>手机号</th><th>微信</th><th>收货地址</th><th>备注</th><th>状态</th><th class="customer-actions">
                  操作
                </th>
              </tr>
              <tr v-else>
                <th>客户</th><th>产品图片</th><th>产品名称 / SKU</th><th>供货价（人民币）</th><th>客户产品编码</th><th>产品特殊需求</th><th>状态</th><th
                  v-if="writable"
                  class="customer-actions"
                >
                  操作
                </th>
              </tr>
            </thead>
            <tbody v-if="!isProduct">
              <tr
                v-for="item in customerRows"
                :key="item.id"
              >
                <td>
                  {{ item.name }}<small
                    v-if="item.defaultCustomer"
                    class="customer-default-badge"
                  >默认客户</small>
                </td><td>{{ item.account1688 || '—' }}</td><td>{{ item.contactPerson || '—' }}</td><td>
                  <a
                    v-if="item.phone"
                    :href="`tel:${encodeURIComponent(item.phone)}`"
                  >{{ item.phone }}</a><span v-else>—</span>
                </td><td>{{ item.wechatId || '—' }}</td><td class="customer-long-text">
                  {{ item.address || '—' }}
                </td><td class="customer-long-text">
                  {{ item.remark || '—' }}
                </td><td>{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</td>
                <td class="customer-actions">
                  <div class="customer-action-buttons">
                    <button
                      v-if="writable"
                      type="button"
                      @click="open(item)"
                    >
                      编辑
                    </button><button
                      v-if="writable"
                      type="button"
                      @click="templateCustomer = item"
                    >
                      外箱标模板
                    </button><button
                      v-if="writable && item.status === 'ACTIVE' && !item.defaultCustomer"
                      type="button"
                      @click="chooseDefault(item)"
                    >
                      设为默认
                    </button><RouterLink :to="{ path: '/customers/products', query: { customerId: item.id } }">
                      客户商品
                    </RouterLink>
                  </div>
                </td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr
                v-for="item in productRows"
                :key="item.id"
              >
                <td>
                  {{ item.customerName }}<small
                    v-if="item.customerStatus === 'DISABLED'"
                    class="data-state--error"
                  >客户已停用</small>
                </td>
                <td>
                  <ShipmentProductImage
                    :product-id="item.productId"
                    :variant-id="item.variantId"
                    :alt="item.productName"
                  />
                </td>
                <td>{{ item.productName }}<small>SKU：{{ item.skuCode }}</small></td><td class="customer-price">
                  {{ item.supplyPrice == null ? '未定价' : `¥${Number(item.supplyPrice).toFixed(2)}` }} / {{ item.priceUnit }}
                </td><td>{{ item.customerProductCode || '—' }}</td><td class="customer-long-text">
                  {{ item.requirements || '—' }}
                </td><td>{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</td><td
                  v-if="writable"
                  class="customer-actions"
                >
                  <button
                    type="button"
                    @click="open(item)"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    class="secondary-button"
                    :disabled="deleting"
                    @click="requestDelete(item)"
                  >
                    删除
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
      </div>
      <p
        v-if="!loading && !(isProduct ? productRows.length : customerRows.length)"
        class="data-state"
      >
        {{ error ? '加载失败，请点击查询重试。' : '暂无符合条件的记录。' }}
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
      class="customer-dialog"
      :aria-label="discard ? '放弃未保存的修改？' : `${editingId ? '编辑' : '新增'}${title}`"
      @cancel.prevent="escape"
    >
      <div
        v-if="discard"
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
      >
        <h2 class="form-wide">
          放弃未保存的修改？
        </h2><p class="form-wide">
          已填写的内容尚未保存，放弃后需要重新填写。
        </p><button
          ref="continueButton"
          type="button"
          @click="keepEditing"
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
        v-else
        class="panel operation-form operation-dialog__card"
        @submit.prevent="save"
      >
        <h2 class="form-wide">
          {{ editingId ? '编辑' : '新增' }}{{ title }}
        </h2><button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭表单"
          :disabled="saving"
          @click="requestClose"
        >
          ×
        </button>
        <template v-if="!isProduct">
          <label class="form-wide"><span><input
            v-model="customerForm.defaultCustomer"
            type="checkbox"
            :disabled="saving"
          > 设为默认客户（新增发货自动选中）</span></label>
          <label>客户名称 *<input
            v-model="customerForm.name"
            required
            maxlength="128"
            :disabled="saving"
            placeholder="公司或客户名称"
          ></label>
          <h3 class="form-wide">
            开票信息（可维护多个抬头）
          </h3>
          <fieldset
            v-for="(entry, index) in customerForm.invoiceTitles"
            :key="index"
            class="form-wide customer-title-card"
            :disabled="saving"
          >
            <legend>抬头 {{ index + 1 }}</legend>
            <label
              v-for="[key, label, max] in titleFields"
              :key="key"
            >{{ label }}<input
              v-model="entry[key]"
              :maxlength="max"
            ></label>
            <button
              type="button"
              class="secondary-button"
              @click="customerForm.invoiceTitles?.splice(index, 1)"
            >
              移除此抬头
            </button>
          </fieldset>
          <button
            type="button"
            class="secondary-button form-wide"
            :disabled="saving || (customerForm.invoiceTitles?.length ?? 0) >= 50"
            @click="customerForm.invoiceTitles?.push(blankTitle())"
          >
            添加发票抬头
          </button>
          <h3 class="form-wide">
            联系信息
          </h3><label>1688账号<input
            v-model="customerForm.account1688"
            maxlength="100"
            :disabled="saving"
            placeholder="客户的1688会员账号"
          ></label>
          <label>联系人<input
            v-model="customerForm.contactPerson"
            maxlength="64"
            :disabled="saving"
          ></label><label>手机号<input
            v-model="customerForm.phone"
            type="tel"
            maxlength="32"
            :disabled="saving"
          ></label>
          <label>微信<input
            v-model="customerForm.wechatId"
            maxlength="100"
            :disabled="saving"
          ></label><label>状态<select
            v-model="customerForm.status"
            :disabled="saving"
          ><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
          <label class="form-wide">收货地址<input
            v-model="customerForm.address"
            maxlength="500"
            :disabled="saving"
          ></label><label class="form-wide">备注<input
            v-model="customerForm.remark"
            maxlength="500"
            :disabled="saving"
          ></label>
          <p class="form-wide customer-help">
            默认客户不能直接停用，请先将其他客户设为默认。停用客户后保留既有商品记录，但不可新增或启用该客户的报价。
          </p>
        </template>
        <template v-else>
          <p
            v-if="editingId"
            class="form-wide customer-selection"
          >
            {{ selectionSummary }}<small>客户与 SKU 不可更换，其他客户或 SKU 请另行新增。</small>
          </p>
          <template v-else>
            <fieldset
              class="form-wide customer-picker"
              :disabled="saving || picking"
            >
              <legend>选择客户 *</legend>
              <div class="customer-picker-search">
                <input
                  v-model="customerQuery"
                  aria-label="搜索客户"
                  placeholder="客户名称、1688账号或手机号"
                  maxlength="128"
                  @keydown.enter.prevent="findOptions('customer', true)"
                ><button
                  type="button"
                  @click="findOptions('customer', true)"
                >
                  搜索客户
                </button>
              </div>
              <select
                :value="productForm.customerId"
                aria-label="选择客户"
                @change="pickCustomer"
              >
                <option value="">
                  请选择启用客户
                </option><option
                  v-if="selectedCustomer && !customerOptions.some(item => item.id === selectedCustomer?.id)"
                  :value="selectedCustomer.id"
                >
                  {{ selectedCustomer.name }}
                </option><option
                  v-for="item in customerOptions"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.name }}{{ item.account1688 ? ` · ${item.account1688}` : '' }}
                </option>
              </select>
              <div class="customer-picker-pages">
                <button
                  type="button"
                  :disabled="customerPage === 0"
                  @click="customerPage--; findOptions('customer')"
                >
                  上一页客户
                </button><span>{{ customerPage + 1 }} / {{ Math.max(1, Math.ceil(customerCount / 20)) }} 页 · {{ customerCount }} 条</span><button
                  type="button"
                  :disabled="(customerPage + 1) * 20 >= customerCount"
                  @click="customerPage++; findOptions('customer')"
                >
                  下一页客户
                </button>
              </div>
            </fieldset>
            <fieldset
              class="form-wide customer-picker"
              :disabled="saving || picking"
            >
              <legend>选择产品 SKU *</legend>
              <div class="customer-picker-search">
                <input
                  v-model="skuQuery"
                  aria-label="搜索SKU"
                  placeholder="搜索 SKU 或产品名称"
                  maxlength="128"
                  @keydown.enter.prevent="findOptions('sku', true)"
                ><button
                  type="button"
                  @click="findOptions('sku', true)"
                >
                  搜索SKU
                </button>
              </div>
              <select
                :value="productForm.variantId"
                aria-label="选择SKU"
                @change="pickSku"
              >
                <option value="">
                  请选择启用 SKU
                </option><option
                  v-if="selectedSku && !skuOptions.some(item => item.variant.id === selectedSku?.variant.id)"
                  :value="selectedSku.variant.id"
                >
                  {{ selectedSku.variant.variantName }} · {{ selectedSku.variant.skuCode }}
                </option><option
                  v-for="item in skuOptions"
                  :key="item.variant.id"
                  :value="item.variant.id"
                >
                  {{ item.variant.variantName }} · SKU：{{ item.variant.skuCode }}
                </option>
              </select>
              <div class="customer-picker-pages">
                <button
                  type="button"
                  :disabled="skuPage === 0"
                  @click="skuPage--; findOptions('sku')"
                >
                  上一页SKU
                </button><span>{{ skuPage + 1 }} / {{ Math.max(1, Math.ceil(skuCount / 20)) }} 页 · {{ skuCount }} 条</span><button
                  type="button"
                  :disabled="(skuPage + 1) * 20 >= skuCount"
                  @click="skuPage++; findOptions('sku')"
                >
                  下一页SKU
                </button>
              </div>
            </fieldset>
          </template>
          <label>供货价（人民币，可留空）<input
            v-model="productForm.supplyPrice"
            type="number"
            min="0"
            max="999999999999.99"
            step="0.01"
            :disabled="saving"
            placeholder="0.00"
          ></label>
          <label>计价单位 *<input
            v-model="productForm.priceUnit"
            required
            maxlength="16"
            :disabled="saving"
            placeholder="如：件、套、箱"
          ></label>
          <label>客户产品编码<input
            v-model="productForm.customerProductCode"
            maxlength="128"
            :disabled="saving"
            placeholder="客户内部的产品编号"
          ></label>
          <label>状态<select
            v-model="productForm.status"
            :disabled="saving"
          ><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
          <label class="form-wide">产品特殊需求<input
            v-model="productForm.requirements"
            maxlength="2000"
            :disabled="saving"
            placeholder="如包装、颜色、贴标、配件要求；税费及运费约定也可注明"
          ></label>
          <p class="form-wide customer-help">
            每个客户与 SKU 维护一条当前报价；不同客户可设置不同供货价。报价不自动生成订单。
          </p>
        </template>
        <p
          v-if="formError"
          class="form-wide data-state data-state--error"
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
            :disabled="saving || picking"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.customer-title-card { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; min-width: 0; border: 1px solid #dbe1ea; border-radius: 8px; padding: 16px; }
.customer-management { min-width: 0; grid-template-columns: minmax(0, 1fr); }
.customer-management :is(.table-panel, .table-scroll) { min-width: 0; max-width: 100%; }
.data-table { min-width: 1100px; }
.customer-actions { position: sticky; right: 0; min-width: 100px; background: white; z-index: 2; box-shadow: -5px 0 8px -5px #9aa4b5; }
thead .customer-actions { z-index: 5; background: #f8f9fb; }
.customer-action-buttons { display: grid; gap: 8px; }
.customer-long-text { min-width: 180px; max-width: 340px; white-space: pre-wrap; overflow-wrap: anywhere; }
.customer-default-badge { color: #168252; font-weight: 700; }
.customer-price { font-weight: 700; white-space: nowrap; }
small { display: block; margin-top: 6px; color: #68758c; }
.pagination { flex-wrap: wrap; }
.customer-dialog { padding: 0; border: 0; border-radius: 14px; width: min(760px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); overflow: auto; }
.customer-dialog::backdrop { background: #17213b66; }
.customer-dialog .operation-dialog__card { width: 100%; max-height: none; margin: 0; box-sizing: border-box; }
.customer-help { color: #68758c; font-size: 13px; margin: 0; }
.customer-selection { background: #f3f6fc; padding: 12px; border-radius: 6px; }
.customer-picker { border: 1px solid #dbe1ea; border-radius: 8px; min-width: 0; display: grid; gap: 8px; padding: 12px; }
.customer-picker legend { color: #53617a; font-size: 13px; }
.customer-picker-search { display: flex; gap: 8px; }
.customer-picker-search input { flex: 1; min-width: 0; }
.customer-picker-search button { white-space: nowrap; }
.customer-picker-pages { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; font-size: 12px; }
.customer-picker-pages button { padding: 4px 8px; min-height: 28px; font-size: 12px; }
</style>
