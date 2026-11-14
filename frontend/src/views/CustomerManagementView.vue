<script setup lang="ts">
import { vPhone } from '@/directives/phone'
import AddressRecognition from '@/components/AddressRecognition.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import {catalogApi} from '@/api/catalog'

import { vSingleSelect } from '@/directives/singleSelect'
import CustomerSkuPicker from '@/components/CustomerSkuPicker.vue'
import ListPagination from '@/components/ListPagination.vue'
const listSize = ref(20)
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, RouterLink, useRoute } from 'vue-router'
import { customersApi, customerAddresses, type Customer, type CustomerInput, type CustomerProduct, type CustomerProductInput, type CustomerStatus, type InvoiceTitle } from '@/api/customers'
import { type SkuListItemDto } from '@/api/domain'
import { session } from '@/state/session'
import AccessDialog from '@/components/AccessDialog.vue'
import SkuDocumentsPanel from '@/components/SkuDocumentsPanel.vue'
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
const labelProduct = ref<CustomerProduct>()
const labelBusy = ref(false), showLegacyLabels = ref(false)
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
const blankCustomer = (): CustomerInput => ({ wechatImageId: '', addresses: [], invoiceTitles: [blankTitle()], name: '', account1688: '', contactPerson: '', phone: '', wechatId: '', invoiceTitle: '', taxNumber: '', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '', defaultCustomer: false, address: '', remark: '', status: 'ACTIVE', version: undefined })
const blankProduct = (): CustomerProductInput => ({ customerId: '', productId: '', variantId: '', supplyPrice: '', priceUnit: '件', customerProductCode: '', requirements: '', status: 'ACTIVE', version: undefined })
const customerForm = reactive(blankCustomer()), productForm = reactive(blankProduct())
function removeAddress(index:number) {
  customerForm.addresses?.splice(index,1)
  if(customerForm.addresses?.length && !customerForm.addresses.some(a=>a.defaultAddress)) customerForm.addresses[0]!.defaultAddress=true
}
const wechatPreview=ref<string>()
async function uploadWechat(file:File){if(saving.value)return false;saving.value=true;formError.value='';try{customerForm.wechatImageId=(await catalogApi.uploadImage('CUSTOMER_WECHAT',file)).id}catch(e){formError.value=message(e);return false}finally{saving.value=false}}
const snapshot = () => JSON.stringify(isProduct.value ? {form:productForm,rows:batchProducts.value} : customerForm)
const routeCustomerId = computed(() => typeof route.query.customerId === 'string' ? route.query.customerId : '')
const customerFilter = ref(''), customerOptions = ref<Customer[]>([]), customersLoading = ref(false), customersError = ref('')
const selectedSku = ref<SkuListItemDto>(), skuPickerOpen = ref(false)
const batchProducts=ref<{sku:SkuListItemDto;form:CustomerProductInput}[]>([]), batchSaved=ref(0)
function addSkus(rows:SkuListItemDto[]){for(const sku of rows){if(!sku.variant.id||batchProducts.value.some(r=>r.sku.variant.id===sku.variant.id))continue;batchProducts.value.push({sku,form:{...blankProduct(),productId:sku.productId,variantId:sku.variant.id}})}skuPickerOpen.value=false}
const activeCustomers = computed(() => customerOptions.value.filter(item => item.status === 'ACTIVE'))
const currentCustomer = computed(() => customerOptions.value.find(item => item.id === customerFilter.value))
let loadSequence = 0, directorySequence = 0
async function loadCustomers() {
  const sequence = ++directorySequence; customersLoading.value = true; customersError.value = ''
  try {
    const all: Customer[] = []
    for (let index = 0; ; index++) {
      const result = await customersApi.list({ page: index, size: 100 })
      if (sequence !== directorySequence) return
      all.push(...result.items)
      if (!result.items.length || all.length >= result.totalCount) break
    }
    customerOptions.value = all
    customerFilter.value = all.some(item => item.id === routeCustomerId.value) ? routeCustomerId.value : (all.find(item => item.defaultCustomer && item.status === 'ACTIVE')?.id ?? '')
    await load()
  } catch (e) { if (sequence === directorySequence) customersError.value = message(e) }
  finally { if (sequence === directorySequence) customersLoading.value = false }
}
function changeCustomer() { productRows.value = []; total.value = 0; page.value = 0; query.value = ''; status.value = ''; void load() }
function pickSku(row: SkuListItemDto) { selectedSku.value = row; productForm.productId = row.productId; productForm.variantId = row.variant.id ?? ''; addSkus([row]); skuPickerOpen.value = false }
function message(exception: unknown) { return exception instanceof Error ? exception.message : '请求失败，请重试。' }
async function load() {
  const sequence = ++loadSequence; loading.value = true; error.value = ''
  if (isProduct.value && !customerFilter.value) { productRows.value = []; total.value = 0; loading.value = false; return }
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
async function open(item?: Customer | CustomerProduct) {
  editingId.value = item?.id; discard.value = false; formError.value = ''; success.value = ''
  Object.assign(customerForm, blankCustomer()); Object.assign(productForm, blankProduct())
  batchProducts.value=[];batchSaved.value=0;selectedSku.value = undefined; selectionSummary.value = ''; skuPickerOpen.value = false
  if (isProduct.value && !item) productForm.customerId = activeCustomers.value.find(customer => customer.defaultCustomer)?.id ?? ''
  if (item && 'customerId' in item) {
    Object.assign(productForm, { customerId: item.customerId, productId: item.productId, variantId: item.variantId,
      supplyPrice: item.supplyPrice == null ? '' : String(item.supplyPrice), priceUnit: item.priceUnit, customerProductCode: item.customerProductCode,
      requirements: item.requirements, status: item.status, version: item.version })
    selectionSummary.value = `${item.customerName} · ${item.productName} · SKU：${item.skuCode}`
  } else if (item) {
    Object.assign(customerForm, { wechatImageId: item.wechatImageId ?? '', addresses: customerAddresses(item), invoiceTitles: item.invoiceTitles?.map(t => ({ ...t })) ?? [{ ...blankTitle(), invoiceTitle: item.invoiceTitle ?? '', taxNumber: item.taxNumber ?? '', registeredAddress: item.registeredAddress ?? '', registeredPhone: item.registeredPhone ?? '', bankName: item.bankName ?? '', bankAccount: item.bankAccount ?? '' }], name: item.name, account1688: item.account1688, contactPerson: item.contactPerson,
      phone: item.phone, wechatId: item.wechatId, invoiceTitle: item.invoiceTitle ?? '', taxNumber: item.taxNumber ?? '', registeredAddress: item.registeredAddress ?? '', registeredPhone: item.registeredPhone ?? '', bankName: item.bankName ?? '', bankAccount: item.bankAccount ?? '', defaultCustomer: item.defaultCustomer ?? false, address: item.address, remark: item.remark, status: item.status, version: item.version })
  }
  initial.value = snapshot(); opened.value = true
  await nextTick(); dialog.value?.showModal(); dialog.value?.querySelector<HTMLInputElement>('input')?.focus()
}
async function chooseDefault(item: Customer) { await open(item); customerForm.defaultCustomer = true }
function close() { skuPickerOpen.value = false; dialog.value?.close(); opened.value = false; discard.value = false }
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
  if (isProduct.value && (!productForm.customerId || (editingId.value ? !productForm.variantId : !batchProducts.value.length))) { formError.value = '请选择客户及具体 SKU。'; return }
  if (isProduct.value && productForm.supplyPrice !== '' && productForm.supplyPrice != null && !/^\d{1,12}(\.\d{1,2})?$/.test(String(productForm.supplyPrice))) { formError.value = '供货价须为非负金额，最多两位小数。'; return }
  if(isProduct.value && !editingId.value && batchProducts.value.some(row=>!row.form.priceUnit.trim() || (row.form.supplyPrice!=='' && row.form.supplyPrice!=null && !/^\d{1,12}(\.\d{1,2})?$/.test(String(row.form.supplyPrice))))){formError.value='请填写每项计价单位；供货价须为非负金额，最多两位小数。';return}
  saving.value = true
  try {
    if(isProduct.value && !editingId.value){
      while(batchProducts.value.length){const row=batchProducts.value[0]!;try{await customersApi.saveProduct(undefined,{...row.form,customerId:productForm.customerId,supplyPrice:row.form.supplyPrice===''?null:row.form.supplyPrice});batchProducts.value.shift();batchSaved.value++}catch(e){throw new Error(`已保存 ${batchSaved.value} 项，${row.sku.variant.variantName} 保存失败：${message(e)}。已保存项已从待保存列表移除，请处理剩余项。`,{cause:e})}}
    }
    else if (isProduct.value) await customersApi.saveProduct(editingId.value, { ...productForm, supplyPrice: productForm.supplyPrice === '' ? null : productForm.supplyPrice })
    else {
      const invoiceTitles = (customerForm.invoiceTitles ?? []).filter(t => Object.values(t).some(v => v.trim()))
      if (invoiceTitles.some(t => !t.invoiceTitle.trim())) { formError.value = '请填写每组开票信息的抬头，或移除不用的信息。'; return }
      await customersApi.save(editingId.value, { ...customerForm, ...(invoiceTitles[0] ?? blankTitle()), invoiceTitles, name: customerForm.name.trim() })
    }
    if (isProduct.value) { customerFilter.value = productForm.customerId; page.value = 0 }
    close(); success.value = `${title.value}已保存。`; await load()
  } catch (exception) { formError.value = message(exception) }
  finally { saving.value = false }
}
watch(() => [props.kind, routeCustomerId.value], () => {
  query.value = ''; status.value = ''; page.value = 0; customerRows.value = []; productRows.value = []; total.value = 0
  success.value = ''; loadSequence++; directorySequence++; customerFilter.value = ''; if (isProduct.value) void loadCustomers(); else void load()
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
    <div
      v-if="isProduct"
      class="panel customer-workspace-selector"
    >
      <label>当前客户<select
        v-model="customerFilter"
        v-single-select
        aria-label="当前客户"
        :disabled="customersLoading"
        @change="changeCustomer"
      ><option value="">请选择客户</option><option
        v-for="customer in customerOptions"
        :key="customer.id"
        :value="customer.id"
      >{{ customer.name }}{{ customer.defaultCustomer ? '（默认）' : '' }}{{ customer.status === 'DISABLED' ? '（停用）' : '' }}</option></select></label>
      <span>{{ currentCustomer ? '仅显示该客户的商品、报价和标签' : '请先选择客户，再管理其商品' }}</span>
      <p
        v-if="customersError"
        role="alert"
      >
        {{ customersError }} <button
          type="button"
          @click="loadCustomers"
        >
          重新加载客户
        </button>
      </p>
    </div>
    <div class="material-toolbar">
      <strong>{{ isProduct && currentCustomer ? currentCustomer.name + '的商品' : title }} · 共 {{ total }} 条</strong><button
        v-if="writable"
        type="button"
        :disabled="isProduct && (customersLoading || !!customersError || !activeCustomers.length)"
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
        :placeholder="isProduct ? '搜索 SKU、客户产品编码或特殊需求' : '搜索客户名称、1688账号、联系人或手机号'"
      ></label>
      <label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
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
                <th>产品图片</th><th>产品名称 / SKU</th><th>供货价（人民币）</th><th>客户产品编码</th><th>产品特殊需求</th><th>状态</th><th
                  v-if="isProduct || writable || session.canAccess(['customers.delete'])"
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
                </td><td>
                  {{ item.wechatId || '—' }}<button
                    v-if="item.wechatImageId"
                    type="button"
                    class="customer-wechat-link"
                    @click="wechatPreview=item.wechatImageId"
                  >
                    查看名片
                  </button>
                </td><td class="customer-long-text">
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
                  v-if="isProduct || writable || session.canAccess(['customers.delete'])"
                  class="customer-actions"
                >
                  <button
                    v-if="writable"
                    type="button"
                    @click="open(item)"
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    @click="showLegacyLabels = false; labelProduct = item"
                  >
                    商品标签
                  </button>
                  <button
                    v-if="session.canAccess(['customers.delete'])"
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
        {{ isProduct && !customerFilter ? '请选择客户后查看商品。' : error ? '加载失败，请点击查询重试。' : '暂无符合条件的记录。' }}
      </p>
      <ListPagination
        v-model:page-size="listSize"
        :page="page + 1"
        :total="total"
        :disabled="loading"
        @update:page="turnPage($event - 1 - page)"
      />
    </article>
    <AccessDialog
      v-if="labelProduct"
      :key="labelProduct.id"
      title="商品标签管理"
      dialog-class="customer-product-label-dialog"
      :busy="labelBusy"
      wide
      @close="labelProduct = undefined"
    >
      <p class="customer-label-context">
        {{ labelProduct.customerName }} · {{ labelProduct.productName }} · SKU：{{ labelProduct.skuCode }}
      </p>
      <p class="document-hint">
        标签仅属于当前客户商品，同一 SKU 的其他客户不会共用。
      </p>
      <SkuDocumentsPanel
        :customer-product-id="labelProduct.id"
        :product-id="labelProduct.productId"
        :variant-id="labelProduct.variantId"
        kind="LABEL"
        @busy="labelBusy = $event"
      />
      <template v-if="session.canAccess(['products.read'])">
        <button
          type="button"
          class="secondary-button"
          :disabled="labelBusy"
          @click="showLegacyLabels = !showLegacyLabels"
        >
          {{ showLegacyLabels ? '收起历史 SKU 标签' : '查看历史 SKU 标签（未分配客户）' }}
        </button>
        <section v-if="showLegacyLabels">
          <p class="document-hint">
            历史文件只读保留。确认属于当前客户后，可下载并上传到上方客户商品标签中。
          </p>
          <SkuDocumentsPanel
            :product-id="labelProduct.productId"
            :variant-id="labelProduct.variantId"
            kind="LABEL"
            readonly
          />
        </section>
      </template>
    </AccessDialog>
    <dialog
      v-if="opened"
      ref="dialog"
      class="customer-dialog"
      :class="{'customer-dialog--batch':isProduct && !editingId}"
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
              v-phone="key === 'registeredPhone' ? 'contact' : false"
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
          </h3>          <label>联系人<input
            v-model="customerForm.contactPerson"
            maxlength="64"
            :disabled="saving"
          ></label><label>手机号<input
            v-model="customerForm.phone"
            v-phone="'mobile'"
            type="tel"
            maxlength="32"
            :disabled="saving"
          ></label>
          <label>微信<input
            v-model="customerForm.wechatId"
            maxlength="100"
            :disabled="saving"
          ></label><label>1688账号<input
            v-model="customerForm.account1688"
            maxlength="100"
            :disabled="saving"
            placeholder="客户的1688会员账号"
          ></label>

          <div class="form-wide customer-wechat-upload">
            <label>微信名片</label>
            <div
              v-if="customerForm.wechatImageId"
              class="customer-wechat-preview"
            >
              <button
                type="button"
                class="image-button"
                @click="wechatPreview=customerForm.wechatImageId || undefined"
              >
                <AuthenticatedCatalogImage
                  :image-id="customerForm.wechatImageId"
                  alt="微信名片"
                />
              </button><button
                type="button"
                class="ui-image-remove"
                :disabled="saving"
                @click="customerForm.wechatImageId=''"
              >
                移除图片
              </button>
            </div>
            <SingleFileUpload
              :key="editingId || 'new'"
              title="上传微信名片"
              hint="选择或粘贴后立即上传，保存客户后关联"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="saving"
              :upload="uploadWechat"
            />
          </div>
          <h3 class="form-wide">
            收货地址 <small>可维护多个地址，订单可直接选用</small>
          </h3>
          <fieldset
            v-for="(entry,index) in customerForm.addresses"
            :key="index"
            class="form-wide customer-title-card customer-address-card"
            :disabled="saving"
          >
            <legend>地址 {{ index + 1 }}</legend>
            <AddressRecognition
              class="form-wide"
              :disabled="saving"
              @recognize="Object.assign(entry,$event)"
            />
            <label>联系人<input
              v-model="entry.contactPerson"
              maxlength="64"
            ></label>
            <label>联系电话<input
              v-model="entry.phone"
              v-phone
              type="tel"
              maxlength="32"
            ></label>
            <label class="form-wide">详细地址 *<input
              v-model="entry.address"
              required
              maxlength="500"
            ></label>
            <div class="form-wide customer-address-actions">
              <label><input
                type="radio"
                name="default-address"
                :checked="entry.defaultAddress"
                @change="customerForm.addresses?.forEach((a,i)=>a.defaultAddress=i===index)"
              > 默认地址</label>
              <button
                type="button"
                class="danger-button"
                @click="removeAddress(index)"
              >
                移除地址
              </button>
            </div>
          </fieldset>
          <button
            type="button"
            class="secondary-button form-wide"
            :disabled="saving || (customerForm.addresses?.length ?? 0)>=50"
            @click="customerForm.addresses?.push({contactPerson:'',phone:'',address:'',defaultAddress:!customerForm.addresses.length})"
          >
            添加收货地址
          </button>
          <label class="form-wide">备注<input
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
            <label class="form-wide">客户 *<select
              v-model="productForm.customerId"
              v-single-select
              aria-label="选择客户"
              required
              :disabled="saving || customersLoading || batchSaved>0"
            ><option value="">请选择启用客户</option><option
              v-for="customer in activeCustomers"
              :key="customer.id"
              :value="customer.id"
            >{{ customer.name }}{{ customer.defaultCustomer ? '（默认）' : '' }}</option></select></label>
            <div class="form-wide customer-sku-selection">
              <button
                type="button"
                class="primary-button"
                :disabled="saving"
                @click="skuPickerOpen = true"
              >
                选择产品 SKU（可多选）
              </button>
            </div>
            <div class="form-wide customer-batch-products">
              <table class="data-table">
                <thead><tr><th>图片</th><th>商品 / 父体</th><th>供货价（人民币）</th><th>单位 *</th><th>客户产品编码</th><th>产品特殊需求</th><th>操作</th></tr></thead><tbody>
                  <tr
                    v-for="(row,index) in batchProducts"
                    :key="row.sku.variant.id"
                  >
                    <td>
                      <ShipmentProductImage
                        :product-id="row.sku.productId"
                        :variant-id="row.sku.variant.id"
                        :alt="row.sku.variant.variantName"
                      />
                    </td>
                    <td>{{ row.sku.variant.variantName }}<small>父体：{{ row.sku.productName }}</small></td>
                    <td>
                      <input
                        v-model="row.form.supplyPrice"
                        type="number"
                        min="0"
                        step="0.01"
                        :disabled="saving"
                        :aria-label="`商品${index+1}供货价`"
                        placeholder="可留空"
                      >
                    </td>
                    <td>
                      <input
                        v-model="row.form.priceUnit"
                        required
                        maxlength="16"
                        :disabled="saving"
                        :aria-label="`商品${index+1}计价单位`"
                      >
                    </td>
                    <td>
                      <input
                        v-model="row.form.customerProductCode"
                        maxlength="128"
                        :disabled="saving"
                        :aria-label="`商品${index+1}客户产品编码`"
                      >
                    </td>
                    <td>
                      <input
                        v-model="row.form.requirements"
                        maxlength="2000"
                        :disabled="saving"
                        :aria-label="`商品${index+1}特殊需求`"
                      >
                    </td>
                    <td>
                      <button
                        type="button"
                        :disabled="saving"
                        class="customer-batch-remove"
                        @click="batchProducts.splice(index,1)"
                      >
                        移除
                      </button>
                    </td>
                  </tr><tr v-if="!batchProducts.length">
                    <td colspan="7">
                      请选择产品 SKU，可一次添加多项
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <template v-if="editingId">
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
              v-single-select
              :disabled="saving"
            ><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
            <label class="form-wide">产品特殊需求<input
              v-model="productForm.requirements"
              maxlength="2000"
              :disabled="saving"
              placeholder="如包装、颜色、贴标、配件要求；税费及运费约定也可注明"
            ></label>
          </template>
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
        <div class="operation-form__actions ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="requestClose"
          >
            取消
          </button><button
            type="submit"
            :disabled="saving"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </dialog>
    <CustomerSkuPicker
      v-if="skuPickerOpen"
      :selected-id="productForm.variantId"
      :multiple="!editingId"
      :existing-ids="batchProducts.map(row=>row.sku.variant.id!)"
      @confirm="addSkus"
      @close="skuPickerOpen = false"
      @select="pickSku"
    />
  </section>
  <AccessDialog
    v-if="wechatPreview"
    title="微信名片"
    @close="wechatPreview=undefined"
  >
    <AuthenticatedCatalogImage
      :image-id="wechatPreview"
      variant="original"
      alt="微信名片"
      class="customer-wechat-full"
    />
  </AccessDialog>
</template>

<style scoped>
.customer-workspace-selector{display:flex;align-items:center;flex-wrap:wrap;gap:12px;min-height:0;padding:12px 14px}.customer-workspace-selector label{display:flex;align-items:center;gap:10px;font-size:13px;font-weight:600}.customer-workspace-selector :deep(.single-select-trigger){width:260px;max-width:65vw}.customer-workspace-selector>span{font-size:12px;color:var(--muted)}.customer-workspace-selector>p{width:100%;margin:0;color:#b42318}.customer-sku-selection{display:grid;gap:8px;min-width:0}.customer-sku-selection>label{font-size:13px}.customer-sku-selection>button{justify-self:start;background:#2864c5;color:white;border:1px solid #2864c5;border-radius:5px;padding:8px 14px;font-size:13px}.customer-sku-selection small{margin-top:5px}

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

<style scoped>
.customer-address-card{grid-template-columns:repeat(2,minmax(0,1fr))}.customer-address-actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.customer-address-actions label{display:flex;align-items:center;gap:6px}.customer-address-actions input{width:auto;min-height:0;height:auto}.customer-title-card small{font-weight:400;color:var(--muted)}
@media(max-width:640px){.customer-address-card{grid-template-columns:minmax(0,1fr)}}
</style>

<style scoped>
.customer-wechat-upload{display:grid;gap:8px;min-width:0}.customer-wechat-preview{display:flex;align-items:center;gap:12px}.customer-wechat-preview :deep(img){width:64px;height:64px;object-fit:contain}.customer-wechat-full{max-width:100%;max-height:65vh;object-fit:contain}.customer-wechat-link{display:block;background:transparent;color:#2673c9;padding:4px 0}
</style>

<style scoped>
.customer-dialog.customer-dialog--batch{width:min(1200px,calc(100vw - 24px))}.customer-batch-products{overflow:auto;max-height:55vh;min-height:200px}.customer-batch-products table{width:100%;min-width:1000px}.customer-batch-products th{white-space:nowrap}.customer-batch-products td:nth-child(2){min-width:180px;white-space:normal}.customer-batch-products td:nth-child(3){width:120px}.customer-batch-products td:nth-child(4){width:80px}.customer-batch-products small{display:block;color:var(--muted);margin-top:5px;font-size:12px}.customer-batch-products input{width:100%;min-width:70px;box-sizing:border-box}.customer-batch-products thead{position:sticky;top:0;background:#f8fafc;z-index:1}
</style>

<style scoped>
.customer-dialog:focus{outline:none}
.customer-batch-products .customer-batch-remove{background:#fff1f2!important;color:#b42335!important;border:1px solid #f1ccd1;border-radius:4px;padding:5px 10px;font-size:12px;min-height:28px}
</style>
