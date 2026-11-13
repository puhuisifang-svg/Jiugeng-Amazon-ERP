<script setup lang="ts">
import { beijingDateTime, beijingDate, beijingDateTimeInput } from '@/utils/dateTime'


import { createRequestId } from '@/utils/requestId'

import OrderShipmentDialog from '@/components/OrderShipmentDialog.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import DatePicker from '@/components/DatePicker.vue'
import { confirmAction, promptReason } from '@/state/uiConfirmation'
import { shipmentUnit } from '@/ui/shipmentUnits'
import ProductName from '@/components/ProductName.vue'
import ListPagination from '@/components/ListPagination.vue'
const listSize = ref(20)
import CartonLabelExport from '@/components/CartonLabelExport.vue'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { dashboardApi, type DashboardHistoryEvent, type DashboardItem, type DashboardKind, type PendingProduct, type PendingProductInput, type PendingShipment, type ShipmentInput, type TodayShipment } from '@/api/dashboard'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedDashboardImage from '@/components/AuthenticatedDashboardImage.vue'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'
import ShipmentFiles from '@/components/ShipmentFiles.vue'
import CustomerProductImage from '@/components/CustomerProductImage.vue'
import ShipmentCustomerPicker from '@/components/ShipmentCustomerPicker.vue'
import type { CustomerProduct } from '@/api/customers'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'

const props = defineProps<{ kind: DashboardKind }>()
const route = useRoute(), router=useRouter()
const orderArrangeOpen=ref(false)
watch(()=>[props.kind,route?.query.orderId,route?.query.arrange],()=>{orderArrangeOpen.value=props.kind==='pending-shipments'&&!!route?.query.orderId&&route?.query.arrange==='1'},{immediate:true})
async function closeOrderArrange(){orderArrangeOpen.value=false;if(route?.query.arrange){const query={...route.query};delete query.arrange;await router.replace({query})}}
async function orderArrangementSaved(){await closeOrderArrange();await load();success.value='已按客户订单新增待发货，发货进度将同步到订单。'}
const formal = runtimeConfig.authMode === 'password'
const items = ref<DashboardItem[]>([]); const products = ref<ProductDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const page = ref(0); const totalPages = ref(0); const total = ref(0); const editing = ref<DashboardItem | null>(null)
const filters = reactive({ keyword: '', status: props.kind === 'today-shipments' ? 'ACTIVE' : '', from: '', to: '' })
const shipment = reactive<ShipmentInput>({ date: '', productId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
const shipmentLines = ref<{product:CustomerProduct;quantity:number;boxCount:number;key:string;done:boolean;attempted:boolean;input?:ShipmentInput;error?:string}[]>([])
const batchStarted=computed(()=>shipmentLines.value.some(line=>line.attempted))
const saveProgress=ref('')
function addShipmentProducts(values:CustomerProduct[]){
  for(const product of values)if(!shipmentLines.value.some(line=>line.product.id===product.id))shipmentLines.value.push({product,quantity:1,boxCount:1,key:createRequestId(),done:false,attempted:false})
}
function selectCustomerProduct(value: CustomerProduct | undefined) {
  shipment.customerProductId = value?.id ?? ''; shipment.variantId = value?.variantId ?? ''; shipment.productId = value?.productId ?? ''
  if(!editing.value){shipmentLines.value=[];if(value)addShipmentProducts([value])}
}
const pendingProduct = reactive<PendingProductInput>({ productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
const correctionReason = ref(''); const imageId = ref(''); const imageFile = ref<File>(); const previewUrl = ref('')
const history = ref<DashboardHistoryEvent[]>([]); const historyTitle = ref('')
const formOpen = ref(false)
const discardOpen = ref(false)
const initialForm = ref('')
const shipmentForm = ref<HTMLFormElement>()
const continueButton = ref<HTMLButtonElement>()
function formSnapshot() { return JSON.stringify([shipment, latestShipDate.value, correctionReason.value, shipmentLines.value]) }
function openNew() { if(props.kind==='pending-shipments'&&route?.query.orderId){orderArrangeOpen.value=true;return} reset(); error.value = ''; initialForm.value = formSnapshot(); formOpen.value = true }
async function requestClose() {
  if (saving.value) return
  if (isProduct.value || formSnapshot() === initialForm.value) { reset(); return }
  discardOpen.value = true
  await nextTick(); continueButton.value?.focus()
}
async function continueEditing() {
  discardOpen.value = false
  await nextTick(); shipmentForm.value?.querySelector<HTMLElement>('select, input')?.focus()
}
function escapeForm() {
  if (isProduct.value || saving.value) return
  if (discardOpen.value) void continueEditing()
  else void requestClose()
}
const latestShipDate = ref('')
const pendingTransferTarget = ref<DashboardItem>()
const transferReason = ref('')
const deleteTarget = ref<DashboardItem>()
const todayTransferTarget = ref<PendingShipment>()
const validationToast = ref('')
const validationToastKey = ref(0)
const labelIds = ref<string[]>(), labelSelected = ref<string[]>([])
const labelEligible = computed(() => items.value.filter(item => 'shipDate' in item && item.customerProduct && item.status !== 'ARCHIVED'))
const labelAll = computed(() => labelEligible.value.length > 0 && labelEligible.value.every(item => labelSelected.value.includes(item.id)))
function toggleLabels() { labelSelected.value = labelAll.value ? [] : labelEligible.value.map(item => item.id) }
watch(items, () => { labelSelected.value = labelSelected.value.filter(id => labelEligible.value.some(item => item.id === id)) })
const dispatchTarget = ref<TodayShipment>()
const dispatchError = ref('')
const dispatchKeys = new Map<string, string>()
function openDispatch(item: TodayShipment) {
  if (saving.value) return
  if (!item.variantId || !item.customerProduct) { validationToastKey.value++; validationToast.value = '请先编辑补选客户商品后再发货。'; return }
  dispatchTarget.value = { ...item }; dispatchError.value = ''
  const identity = `${item.id}:${item.version}`
  if (!dispatchKeys.has(identity)) dispatchKeys.set(identity, createRequestId())
}
function closeDispatch() { if (!saving.value) dispatchTarget.value = undefined }
async function confirmDispatch() {
  const target = dispatchTarget.value
  if (!target || saving.value) return
  saving.value = true; dispatchError.value = ''; success.value = ''
  try {
    await dashboardApi.dispatch(target.id, target.version, dispatchKeys.get(`${target.id}:${target.version}`)!)
    dispatchTarget.value = undefined; success.value = '发货已确认，已按实际确认日期计入发货统计。'; await load()
  } catch (exception) { dispatchError.value = exception instanceof ApiError ? exception.message : '发货确认失败，请重试。' }
  finally { saving.value = false }
}
let loadSequence = 0

const isProduct = computed(() => props.kind === 'pending-products')
const title = computed(() => props.kind === 'today-shipments' ? '今日发货管理' : props.kind === 'pending-shipments' ? '待发货管理' : '样品待开发管理')
const activeStatus = computed(() => props.kind === 'today-shipments' ? 'ACTIVE' : 'PENDING')
const dateLabel = computed(() => props.kind === 'today-shipments' ? '发货日期' : '计划发货日期')
const remarkModel = computed({ get: () => isProduct.value ? pendingProduct.remark : shipment.remark, set: value => { if (isProduct.value) pendingProduct.remark = value ?? ''; else shipment.remark = value ?? '' } })
function localDateTime(value: string) { return value ? beijingDateTime(value) : '—' }
function localDate(value: string) { return value ? beijingDate(value) : '—' }
function historyChanges(event: DashboardHistoryEvent) {
  function snapshot(value: unknown): Record<string, unknown> {
    if (typeof value === 'string') { try { return JSON.parse(value) as Record<string, unknown> } catch { return {} } }
    return value && typeof value === 'object' ? value as Record<string, unknown> : {}
  }
  if (!event.beforeValue || !event.afterValue) return []
  const flatten = (value: unknown): Record<string, unknown> => { const row = snapshot(value); const customer = snapshot(row.customerProduct); return { ...row, customerName: customer.customerName, customerProductCode: customer.customerProductCode, customerRequirements: customer.requirements } }
  const before = flatten(event.beforeValue); const after = flatten(event.afterValue)
  const fields: Record<string, string> = { customerName: '客户', customerProductCode: '客户产品编码', customerRequirements: '客户特殊需求', productName: '产品名', productCode: 'SKU 编号', variantId: 'SKU 绑定', quantity: '发货数量', boxCount: '箱数', remark: '备注', plannedShipDate: '计划发货日期', latestShipAt: '最晚发货日期', status: '状态' }
  const display = (key: string, value: unknown) => value == null || value === '' ? '未填写' : key === 'latestShipAt' ? localDate(String(value)) : key === 'status' ? statusLabel(String(value)) : String(value)
  return Object.entries(fields).filter(([key]) => key in before && key in after && before[key] !== after[key])
    .map(([key, label]) => `${label}：${display(key, before[key])} → ${display(key, after[key])}`)
}
function itemDate(item: DashboardItem) { return 'shipDate' in item ? item.shipDate : 'plannedShipDate' in item ? item.plannedShipDate : item.expectedCompletionDate }
function dateRowspan(index: number) {
  const date = itemDate(items.value[index]!)
  if (index > 0 && itemDate(items.value[index - 1]!) === date) return 0
  let count = 1
  while (index + count < items.value.length && itemDate(items.value[index + count]!) === date) count++
  return count
}
function reset() {
  discardOpen.value = false
  shipmentLines.value=[];saveProgress.value=''
  formOpen.value = false; latestShipDate.value = ''
  editing.value = null; imageId.value = ''; imageFile.value = undefined; previewUrl.value = ''; correctionReason.value = ''
  Object.assign(shipment, { date: '', productId: '', variantId: '', customerProductId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
  Object.assign(pendingProduct, { productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
}
function edit(item: DashboardItem) {
  formOpen.value = true
  if ('latestShipAt' in item) latestShipDate.value = beijingDate(item.latestShipAt)
  editing.value = item; imageId.value = item.imageId ?? ''; imageFile.value = undefined; previewUrl.value = ''; error.value = ''; success.value = ''
  if ('expectedQuantity' in item) Object.assign(pendingProduct, { productName: item.productName, expectedQuantity: item.expectedQuantity, remark: item.remark ?? '', expectedCompletionDate: item.expectedCompletionDate })
  else Object.assign(shipment, { date: 'shipDate' in item ? item.shipDate : item.plannedShipDate, productId: item.productId, variantId: item.variantId ?? '', customerProductId: item.customerProduct?.id ?? '', quantity: item.quantity, boxCount: item.boxCount, remark: item.remark ?? '', latestShipTime: beijingDateTimeInput(item.latestShipAt).slice(0, 16) })
  initialForm.value = formSnapshot()
}
async function load() {
  if (!formal) { loading.value = false; return }
  const sequence = ++loadSequence
  validationToast.value = ''; error.value = ''
  if (filters.from && filters.to && filters.from > filters.to) {
    validationToastKey.value++; validationToast.value = '开始日期不能晚于结束日期'; loading.value = false; return
  }
  loading.value = true; error.value = ''
  try {
    const result = await dashboardApi.page<DashboardItem>(props.kind, { ...filters, ...(route?.query.orderId ? { orderId: String(route.query.orderId) } : {}), page: page.value, size: listSize.value })
    if (sequence !== loadSequence) return
    items.value = result.items; totalPages.value = result.totalPages; total.value = result.totalElements
  } catch (exception) {
    if (sequence !== loadSequence) return
    const message = exception instanceof ApiError ? exception.message : '工作清单加载失败。'
    if (message === '开始日期不能晚于结束日期') { validationToastKey.value++; validationToast.value = message }
    else error.value = message
  }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function chooseImage(file: File) {
  if (file.size > 5 * 1024 * 1024) { error.value = '图片不能超过 5 MB。'; return false }
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); imageFile.value = file; previewUrl.value = URL.createObjectURL(file)
}
async function uploadIfNeeded() {
  if (!imageFile.value) return imageId.value || undefined
  const receipt = await dashboardApi.uploadImage(isProduct.value ? 'PENDING_PRODUCT' : 'SHIPMENT', imageFile.value)
  return receipt.id
}
async function save() {
  if (saving.value || discardOpen.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const uploadedId = isProduct.value ? await uploadIfNeeded() : undefined
    if (isProduct.value) {
      const input = { ...pendingProduct, imageId: uploadedId, productName: pendingProduct.productName.trim(), remark: pendingProduct.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updatePendingProduct(editing.value.id, input, editing.value.version)
      else await dashboardApi.createPendingProduct(input)
    } else {
      if (editing.value ? !shipment.variantId || !shipment.customerProductId : !shipmentLines.value.length) { error.value = '请选择客户商品；历史记录需补选后保存。'; return }
      const today = beijingDate()
      const shipDate = editing.value && 'shipDate' in editing.value ? editing.value.shipDate : today
      const input = { ...shipment, ...(props.kind === 'today-shipments' ? { date: shipDate, latestShipTime: `${shipDate}T23:59` } : { latestShipTime: `${latestShipDate.value}T23:59` }), imageId: uploadedId, remark: shipment.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updateShipment(props.kind as Exclude<DashboardKind, 'pending-products'>, editing.value.id, input, editing.value.version, correctionReason.value.trim() || undefined)
      else {
        if(shipmentLines.value.some(line=>!Number.isFinite(line.quantity)||line.quantity<=0||!Number.isInteger(line.boxCount)||line.boxCount<1)){error.value='请填写每个商品的有效发货数量和箱数。';return}
        let failures=0
        for(const [index,line] of shipmentLines.value.entries()){
          if(line.done)continue
          saveProgress.value=`正在保存 ${index+1} / ${shipmentLines.value.length}`
          line.attempted=true
          line.input ??= {...input,productId:line.product.productId,variantId:line.product.variantId,customerProductId:line.product.id,quantity:line.quantity,boxCount:line.boxCount}
          try{
            await dashboardApi.createShipment(props.kind as Exclude<DashboardKind, 'pending-products'>,line.input,line.key)
            line.done=true
          }catch(e){failures++;line.error=e instanceof ApiError?e.message:'保存失败，请重试'}
        }
        if(failures){error.value=`已添加 ${shipmentLines.value.filter(line=>line.done).length} 项，${failures} 项保存失败。请点击保存重试，已成功项不会重复添加。`;return}
      }
    }
    success.value = editing.value ? '记录已更新。' : '记录已创建。'; reset(); await load()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败，请检查字段和图片。' }
  finally { saving.value = false;saveProgress.value='' }
}
async function archive(item: DashboardItem, confirmed = false) {
  if (saving.value) return
  const deleting = !isProduct.value
  if (deleting && !confirmed) { deleteTarget.value = item; error.value = ''; return }
  const reason = deleting ? '用户删除' : (await promptReason('请输入归档原因'))?.trim()
  if (!reason) return
  saving.value = true
  success.value = ''; error.value = ''
  try { await dashboardApi.archive(props.kind, item.id, item.version, reason); deleteTarget.value = undefined; success.value = deleting ? '记录已删除。' : '记录已归档。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : deleting ? '删除失败。' : '归档失败。' }
  finally { saving.value = false }
}
async function complete(item: PendingProduct) {
  if (!await confirmAction('确认将该新品标记为已完成？')) return
  saving.value = true; success.value = ''; error.value = ''
  try { await dashboardApi.completePendingProduct(item.id, item.version); success.value = '新品已标记完成。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '操作失败。' }
  finally { saving.value = false }
}
async function transfer(item: PendingShipment, confirmed = false) {
  if (saving.value) return
  if (!item.variantId || !item.customerProduct) { validationToastKey.value++; validationToast.value = '该历史记录尚未绑定客户商品，请先编辑补选。'; return }
  if (!confirmed) { todayTransferTarget.value = item; error.value = ''; return }
  const today = beijingDate()
  const latestShipTime = `${today}T23:59`
  saving.value = true; success.value = ''; error.value = ''
  try { await dashboardApi.transferToToday(item.id, { quantity: item.quantity, boxCount: item.boxCount, remark: item.remark, latestShipTime, imageId: item.imageId, version: item.version }); todayTransferTarget.value = undefined; success.value = '已转入今日发货。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '转入失败。' }
  finally { saving.value = false }
}
function openTransferToPending(item: DashboardItem) {
  pendingTransferTarget.value = item; transferReason.value = ''; error.value = ''
}
function closeTransferToPending() {
  if (!saving.value) { pendingTransferTarget.value = undefined; transferReason.value = '' }
}
async function transferToPending() {
  const item = pendingTransferTarget.value
  if (saving.value) return
  if (!item || !transferReason.value.trim()) { error.value = '请输入转为待发货的原因。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try { await dashboardApi.transferToPending(item.id, item.version, transferReason.value.trim()); success.value = '已转为待发货。'; pendingTransferTarget.value = undefined; transferReason.value = ''; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '转为待发货失败。' }
  finally { saving.value = false }
}
async function showHistory(item: DashboardItem) {
  error.value = ''
  try { history.value = await dashboardApi.history(props.kind, item.id); historyTitle.value = item.productName }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '历史记录加载失败。' }
}
function statusLabel(value: string) { return ({ ACTIVE: '有效', SHIPPED: '已发货', PENDING: '待处理', TRANSFERRED_TO_TODAY: '已转入', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[value] ?? value }
watch(() => [props.kind, route?.query.orderId], () => {
  page.value = 0
  Object.assign(filters, { keyword: '', status: props.kind === 'today-shipments' ? 'ACTIVE' : '', from: '', to: '' })
  items.value = []; total.value = 0; totalPages.value = 0
  error.value = ''; success.value = ''; validationToast.value = ''
  pendingTransferTarget.value = undefined; transferReason.value = ''; historyTitle.value = ''; history.value = []
  deleteTarget.value = undefined
  todayTransferTarget.value = undefined
  dispatchTarget.value = undefined
  reset(); void load()
})
onMounted(async () => {
  if (!formal) { loading.value = false; return }
  if (!isProduct.value) products.value = (await domainApi.products()).items.filter(item => item.status !== 'DISABLED')
  await load()
})
</script>

<template>
  <section
    class="page-stack"
    :class="{ 'shipping-maintenance': !isProduct }"
  >
    <template v-if="!formal">
      <SyntheticNotice />
      <article class="panel empty-state">
        <h2>正式工作台维护仅在密码登录模式启用</h2>
        <p>mock 模式继续提供明确标识的合成摘要，不会将模拟记录写入正式工作台。</p>
      </article>
    </template>
    <template v-else>
      <OrderShipmentDialog
        v-if="orderArrangeOpen && route?.query.orderId"
        :key="String(route.query.orderId)"
        :order-id="String(route.query.orderId)"
        @close="closeOrderArrange"
        @saved="orderArrangementSaved"
      />
      <div class="page-actions">
        <RouterLink
          v-if="route?.query.orderId"
          :to="`/orders/customers/${route.query.orderId}`"
        >
          当前按来源订单筛选 · 返回订单
        </RouterLink>
        <p>{{ title }} · 共 {{ total }} 条</p><RouterLink
          class="secondary-action"
          to="/home"
        >
          返回首页
        </RouterLink>
      </div>
      <div
        v-if="!isProduct"
        class="panel material-toolbar"
      >
        <button
          type="button"
          @click="openNew"
        >
          {{ kind === 'pending-shipments' ? '新增待发货' : '新增今日发货' }}
        </button>
      </div>
      <div
        v-if="isProduct || formOpen"
        :class="isProduct ? '' : 'operation-dialog'"
        :role="isProduct ? undefined : discardOpen ? 'alertdialog' : 'dialog'"
        :aria-modal="isProduct ? undefined : true"
        :aria-label="discardOpen ? '放弃未保存的修改？' : '发货记录'"
        @keydown.esc.stop.prevent="escapeForm"
      >
        <div
          v-if="discardOpen"
          class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        >
          <h2 class="form-wide">
            放弃未保存的修改？
          </h2>
          <p class="form-wide">
            当前填写的内容尚未保存。放弃后内容将丢失，是否继续填写？
          </p>
          <button
            ref="continueButton"
            type="button"
            @click="continueEditing"
          >
            继续填写
          </button>
          <button
            type="button"
            class="danger-button"
            @click="reset"
          >
            放弃修改
          </button>
        </div>
        <form
          v-show="!discardOpen"
          ref="shipmentForm"
          class="panel operation-form"
          :class="{ 'operation-dialog__card shipping-editor': !isProduct }"
          @submit.prevent="save"
        >
          <button
            v-if="!isProduct"
            class="operation-dialog__close"
            type="button"
            aria-label="关闭发货弹窗"
            :disabled="saving"
            @click="requestClose"
          >
            ×
          </button>
          <h2 class="shipping-editor-title">
            {{ editing ? '编辑' : '新增' }}{{ isProduct ? '待开发产品' : kind === 'today-shipments' ? '今日发货' : '待发货' }}
          </h2>
          <div :class="isProduct ? 'shipping-editor-pass-through' : 'shipping-editor-body'">
            <template v-if="isProduct">
              <label>产品名称<input
                v-model="pendingProduct.productName"
                required
                maxlength="128"
              ></label>
              <label>预计数量（套）<input
                v-model.number="pendingProduct.expectedQuantity"
                required
                type="number"
                min="1"
              ></label>
              <label>预计完成日期<DatePicker
                v-model="pendingProduct.expectedCompletionDate"
                required
                type="date"
              /></label>
            </template>
            <template v-else>
              <ShipmentCustomerPicker
                :key="editing?.id ?? 'new'"
                compact
                :multiple="!editing"
                :existing="shipmentLines.map(line=>line.product.id)"
                :initial="editing && 'customerProduct' in editing ? editing.customerProduct : undefined"
                :product-name="editing?.productName ?? ''"
                :editing="!!editing"
                :disabled="saving || batchStarted || Boolean(editing && 'orderAllocation' in editing && editing.orderAllocation)"
                @add="addShipmentProducts"
                @select="selectCustomerProduct"
              />
              <div
                v-if="!editing && shipmentLines.length"
                class="shipping-lines"
              >
                <table class="data-table">
                  <thead><tr><th>图片</th><th>商品 / 客户编码</th><th>发货数量（套）<em>*</em></th><th>箱数<em>*</em></th><th>操作</th></tr></thead><tbody>
                    <tr
                      v-for="(line,index) in shipmentLines"
                      :key="line.product.id"
                    >
                      <td>
                        <CustomerProductImage
                          :product-id="line.product.productId"
                          :variant-id="line.product.variantId"
                          :alt="line.product.productName"
                        />
                      </td>
                      <td>{{ line.product.productName }}<small>{{ line.product.customerProductCode || '未填写客户编码' }}</small></td>
                      <td>
                        <input
                          v-model.number="line.quantity"
                          :aria-label="`商品${index+1}发货数量`"
                          type="number"
                          required
                          min="0.001"
                          step="0.001"
                          :disabled="saving || batchStarted"
                        >
                      </td>
                      <td>
                        <input
                          v-model.number="line.boxCount"
                          :aria-label="`商品${index+1}箱数`"
                          type="number"
                          required
                          min="1"
                          step="1"
                          :disabled="saving || batchStarted"
                        >
                      </td>
                      <td>
                        <span
                          v-if="line.done"
                          class="shipping-line-done"
                        >已添加</span><button
                          v-else-if="!batchStarted"
                          type="button"
                          class="shipping-line-remove"
                          :disabled="saving"
                          @click="shipmentLines.splice(index,1)"
                        >
                          移除
                        </button><span
                          v-else
                          :title="line.error"
                        >待重试</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <small>共 {{ shipmentLines.length }} 项 · 日期和备注统一应用于本次商品</small>
              </div>
              <small v-if="editing && !shipment.variantId">历史记录未绑定客户商品，请明确选择；原标签不会自动归属到新客户商品。</small>
              <section class="shipping-plan">
                <h3>发货安排</h3><div class="shipping-plan-fields">
                  <label
                    v-if="kind === 'today-shipments'"
                    class="shipping-date-readonly"
                  >发货日期<span>{{ shipment.date || beijingDate() }} · {{ editing ? '原安排日期' : '按今日安排' }}</span></label>
                  <label v-if="kind !== 'today-shipments'">{{ dateLabel }}<DatePicker
                    v-model="shipment.date"
                    :disabled="saving || batchStarted"
                    required
                    type="date"
                  /></label>
                  <label v-if="kind !== 'today-shipments'">最晚发货日期<DatePicker
                    v-model="latestShipDate"
                    :disabled="saving || batchStarted"
                    required
                    type="date"
                    :min="shipment.date"
                  /></label>
                  <label v-if="editing">发货数量（{{ shipmentUnit(editing && 'quantityUnit' in editing ? editing.quantityUnit : 'SET') }}）<input
                    v-model.number="shipment.quantity"
                    required
                    type="number"
                    min="0.001"
                    step="0.001"
                  ></label>
                  <label v-if="editing">箱数<input
                    v-model.number="shipment.boxCount"
                    required
                    type="number"
                    min="1"
                  ></label>
                  <label v-if="editing && kind === 'today-shipments'">更正原因<input
                    v-model="correctionReason"
                    maxlength="500"
                  ></label>
                </div>
              </section>
            </template>
            <SingleFileUpload
              v-if="isProduct"
              deferred
              title="产品图案"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="saving"
              :upload="chooseImage"
            />
            <div
              v-if="isProduct"
              class="dashboard-image-preview"
            >
              <img
                v-if="previewUrl"
                :src="previewUrl"
                alt="待上传产品图案"
              ><AuthenticatedDashboardImage
                v-else
                :image-id="imageId || undefined"
                alt="当前产品图案"
              />
            </div>
            <label class="form-wide shipping-remark">备注<textarea
              v-model="remarkModel"
              :disabled="saving || batchStarted"
              maxlength="500"
              rows="2"
              placeholder="选填，如包装要求、发货注意事项"
            /></label>
            <p
              v-if="error && !isProduct"
              class="data-state data-state--error form-wide"
              role="alert"
            >
              {{ error }}
            </p>
          </div>
          <div class="operation-form__actions ui-dialog-actions">
            <button
              class="shipping-save"
              :disabled="saving"
              type="submit"
            >
              {{ saving ? saveProgress || '保存中…' : '保存' }}
            </button>
            <button
              v-if="editing || !isProduct"
              class="secondary-button"
              type="button"
              :disabled="saving"
              @click="requestClose"
            >
              取消
            </button>
          </div>
        </form>
      </div>
      <form
        class="panel dashboard-filters"
        @submit.prevent="page = 0; load()"
      >
        <label>关键字<input
          v-model="filters.keyword"
          maxlength="128"
          :placeholder="isProduct ? '搜索产品名称' : '搜索客户、客户产品编码、SKU或备注'"
        ></label>
        <label>状态<select
          v-model="filters.status"
          v-single-select
        ><option value="">全部</option><option :value="activeStatus">{{ statusLabel(activeStatus) }}</option><option
          v-if="isProduct"
          value="COMPLETED"
        >已完成</option><option
          v-if="kind === 'pending-shipments'"
          value="TRANSFERRED_TO_TODAY"
        >已转入</option><option
          v-if="kind === 'today-shipments'"
          value="SHIPPED"
        >已发货</option><option value="ARCHIVED">已归档</option></select></label>
        <label class="date-range-filter">日期范围<DatePicker
          v-model="filters.from"
          v-model:end="filters.to"
          range
          type="date"
        /></label>
        <button type="submit">
          筛选
        </button>
      </form>
      <SuccessToast :message="success" />
      <SuccessToast
        :key="validationToastKey"
        :message="validationToast"
        tone="error"
      />
      <p
        v-if="error && !formOpen"
        class="data-state data-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="loading"
        class="data-state"
      >
        正在加载…
      </p>
      <article
        v-else-if="!items.length"
        class="panel empty-state"
      >
        <h2>暂无记录</h2>
      </article>
      <article
        v-else
        class="panel table-panel"
      >
        <div
          v-if="kind === 'today-shipments'"
          class="material-toolbar"
        >
          <button
            type="button"
            :disabled="!labelSelected.length"
            @click="labelIds = [...labelSelected]"
          >
            导出外箱标 PDF（已选 {{ labelSelected.length }} 条）
          </button>
        </div>
        <div class="table-scroll">
          <TableViewport>
            <table class="data-table">
              <thead>
                <tr>
                  <th v-if="kind === 'today-shipments'">
                    <input
                      type="checkbox"
                      aria-label="全选本页外箱标"
                      :checked="labelAll"
                      @change="toggleLabels"
                    >
                  </th><th>{{ kind === 'today-shipments' ? '发货日期' : kind === 'pending-shipments' ? '计划发货日期' : '预计完成日期' }}</th><th>产品图片</th><th>产品</th><th v-if="kind === 'pending-shipments'">
                    最晚发货日期
                  </th><th>数量</th><th>箱数</th><th>备注</th><th v-if="kind === 'today-shipments'">
                    商品标签
                  </th><th v-if="kind === 'today-shipments'">
                    外箱标签
                  </th><th>状态</th><th>更新时间</th><th :class="{ 'shipping-actions-cell': !isProduct }">
                    操作
                  </th>
                </tr>
              </thead><tbody>
                <tr
                  v-for="(item, index) in items"
                  :key="item.id"
                >
                  <td v-if="kind === 'today-shipments'">
                    <input
                      v-model="labelSelected"
                      type="checkbox"
                      :value="item.id"
                      :disabled="!labelEligible.some(row => row.id === item.id)"
                      :aria-label="'选择外箱标 ' + item.id"
                    >
                  </td>
                  <td
                    v-if="dateRowspan(index)"
                    class="shipment-date-cell"
                    :rowspan="dateRowspan(index)"
                  >
                    {{ itemDate(item) }}
                  </td><td>
                    <AuthenticatedDashboardImage
                      v-if="'expectedQuantity' in item"
                      :image-id="item.imageId"
                      :alt="item.productName"
                    />
                    <ShipmentProductImage
                      v-else
                      :product-id="item.productId"
                      :variant-id="item.variantId"
                      :product="products.find(product => product.id === item.productId)"
                      :alt="item.productName"
                    />
                  </td><td class="shipment-product-cell">
                    <ProductName
                      :text="item.productName"
                      :styles="'namePresentation' in item ? item.namePresentation?.styles : undefined"
                      :highlighted="'namePresentation' in item ? item.namePresentation?.highlighted : false"
                    /><small v-if="'customerProduct' in item && item.customerProduct">客户：{{ item.customerProduct.customerName }} · 客户编码：{{ item.customerProduct.customerProductCode || '未填' }}<br>特殊需求：{{ item.customerProduct.requirements || '无' }}</small><small v-if="'productCode' in item">{{ item.variantId ? `SKU：${item.productCode}` : '历史父体记录 · 待补选 SKU' }}</small>
                    <small v-if="'orderAllocation' in item && item.orderAllocation"><RouterLink :to="`/orders/customers/${item.orderAllocation.orderId}`">订单：{{ item.orderAllocation.orderNumber }}</RouterLink></small>
                  </td><td v-if="kind === 'pending-shipments'">
                    {{ 'latestShipAt' in item ? localDate(item.latestShipAt) : '—' }}
                  </td><td>{{ ('expectedQuantity' in item ? item.expectedQuantity : item.quantity).toLocaleString() }} {{ shipmentUnit(item.quantityUnit) }}</td><td>{{ 'boxCount' in item ? item.boxCount : '—' }}</td><td class="shipment-remark">
                    {{ item.remark || '—' }}
                  </td><td v-if="kind === 'today-shipments'">
                    <ShipmentFiles
                      :key="`${item.id}:${'variantId' in item ? item.variantId : ''}:${'customerProduct' in item ? item.customerProduct?.id : ''}:LABEL`"
                      :shipment-id="item.id"
                      scope="today-shipments"
                      kind="LABEL"
                      :editable="item.status === 'ACTIVE' && 'variantId' in item && !!item.variantId && 'customerProduct' in item && !!item.customerProduct"
                    />
                  </td><td v-if="kind === 'today-shipments'">
                    <ShipmentFiles
                      :key="`${item.id}:${'variantId' in item ? item.variantId : ''}:${'customerProduct' in item ? item.customerProduct?.id : ''}:CARTON_LABEL`"
                      :shipment-id="item.id"
                      scope="today-shipments"
                      kind="CARTON_LABEL"
                      :editable="item.status === 'ACTIVE' && 'variantId' in item && !!item.variantId && 'customerProduct' in item && !!item.customerProduct"
                    />
                  </td><td>{{ statusLabel(item.status) }}</td><td>{{ localDateTime(item.updatedAt) }}</td><td
                    class="table-actions"
                    :class="{ 'shipping-actions-cell': !isProduct }"
                  >
                    <div :class="{ 'shipping-action-buttons': !isProduct }">
                      <button
                        v-if="kind === 'today-shipments' && 'customerProduct' in item && item.customerProduct"
                        type="button"
                        @click="labelIds = [item.id]"
                      >
                        外箱标 PDF
                      </button><button
                        v-if="kind === 'today-shipments' && item.status === 'ACTIVE'"
                        type="button"
                        :disabled="saving"
                        @click="openDispatch(item as TodayShipment)"
                      >
                        发货
                      </button>
                      <button
                        v-if="item.status === activeStatus"
                        type="button"
                        :disabled="saving"
                        @click="edit(item)"
                      >
                        编辑
                      </button><button
                        v-if="kind !== 'today-shipments'"
                        class="shipment-history-button"
                        type="button"
                        @click="showHistory(item)"
                      >
                        历史
                      </button><button
                        v-if="kind === 'today-shipments' && item.status === 'ACTIVE'"
                        type="button"
                        :disabled="saving"
                        @click="openTransferToPending(item)"
                      >
                        转为待发货
                      </button><button
                        v-if="kind === 'pending-shipments' && item.status === 'PENDING'"
                        type="button"
                        :disabled="saving"
                        @click="transfer(item as PendingShipment)"
                      >
                        转到今日发货
                      </button><button
                        v-if="isProduct && item.status === 'PENDING'"
                        type="button"
                        @click="complete(item as PendingProduct)"
                      >
                        完成
                      </button><button
                        v-if="item.status === activeStatus || (kind === 'pending-shipments' && item.status === 'TRANSFERRED_TO_TODAY')"
                        class="danger-button shipping-delete-button"
                        type="button"
                        :disabled="saving"
                        @click="archive(item)"
                      >
                        {{ isProduct ? '归档' : '删除' }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div><ListPagination
          v-model:page-size="listSize"
          :page="page + 1"
          :total="total"
          :disabled="loading"
          @update:page="page = $event - 1; load()"
        />
      </article>
      <CartonLabelExport
        v-if="labelIds"
        :shipment-ids="labelIds"
        @close="labelIds = undefined"
      />
      <div
        v-if="pendingTransferTarget"
        class="operation-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="transfer-pending-title"
        @click.self="closeTransferToPending"
        @keydown.esc="closeTransferToPending"
      >
        <form
          class="panel operation-form operation-dialog__card operation-dialog__card--compact shipment-transfer-dialog"
          @submit.prevent="transferToPending"
        >
          <button
            type="button"
            class="operation-dialog__close"
            aria-label="关闭转为待发货弹窗"
            :disabled="saving"
            @click="closeTransferToPending"
          >
            ×
          </button>
          <h2
            id="transfer-pending-title"
            class="form-wide"
          >
            转为待发货
          </h2>
          <p class="form-wide shipment-transfer-summary">
            产品名：{{ pendingTransferTarget.productName }}
          </p>
          <label class="form-wide">转为待发货原因<input
            v-model="transferReason"
            maxlength="500"
            placeholder="请输入转为待发货的原因"
            autofocus
          ></label>
          <div class="form-footer form-wide">
            <button
              type="button"
              class="secondary-button"
              :disabled="saving"
              @click="closeTransferToPending"
            >
              取消
            </button>
            <button
              type="submit"
              :disabled="saving || !transferReason.trim()"
            >
              {{ saving ? '处理中…' : '确认转为待发货' }}
            </button>
          </div>
          <p
            v-if="error"
            class="data-state data-state--error form-wide"
            role="alert"
          >
            {{ error }}
          </p>
        </form>
      </div>
    </template>
    <div
      v-if="todayTransferTarget"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="transfer-today-title"
      @click.self="!saving && (todayTransferTarget = undefined)"
      @keydown.esc="!saving && (todayTransferTarget = undefined)"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact shipment-transfer-dialog"
        @submit.prevent="transfer(todayTransferTarget, true)"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭转到今日发货弹窗"
          :disabled="saving"
          @click="todayTransferTarget = undefined"
        >
          ×
        </button>
        <h2
          id="transfer-today-title"
          class="form-wide"
        >
          转到今日发货
        </h2>
        <p class="form-wide shipment-transfer-summary">
          客户：{{ todayTransferTarget.customerProduct?.customerName || '未绑定' }}<br>产品名：{{ todayTransferTarget.productName }}<br>发货数量：{{ todayTransferTarget.quantity }} {{ shipmentUnit(todayTransferTarget.quantityUnit) }} · 发货箱数：{{ todayTransferTarget.boxCount }}
        </p>
        <p class="form-wide">
          确认后将加入今天的发货安排，待发货记录保留转移历史，不代表已实际发运。
        </p>
        <p
          v-if="error"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p>
        <div class="form-footer form-wide">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="todayTransferTarget = undefined"
          >
            取消
          </button>
          <button
            type="submit"
            :disabled="saving"
          >
            {{ saving ? '处理中…' : '确认转到今日发货' }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="deleteTarget"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-shipment-title"
      @click.self="!saving && (deleteTarget = undefined)"
      @keydown.esc="!saving && (deleteTarget = undefined)"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact shipment-transfer-dialog"
        @submit.prevent="archive(deleteTarget, true)"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭删除弹窗"
          :disabled="saving"
          @click="deleteTarget = undefined"
        >
          ×
        </button>
        <h2 id="delete-shipment-title">
          确认删除发货记录？
        </h2>
        <p class="form-wide shipment-transfer-summary">
          产品名：{{ deleteTarget.productName }}<br>日期：{{ itemDate(deleteTarget) }}
        </p>
        <p class="form-wide">
          删除后，该记录将从列表移除。{{ kind === 'pending-shipments' && deleteTarget.status === 'TRANSFERRED_TO_TODAY' ? '已转入的今日发货记录不受影响。' : '' }}
        </p>
        <p
          v-if="error"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ error }}
        </p>
        <div class="form-footer form-wide">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="deleteTarget = undefined"
          >
            取消
          </button><button
            type="submit"
            class="danger-button"
            :disabled="saving"
          >
            {{ saving ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="historyTitle"
      class="image-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="资源历史记录"
      @click.self="historyTitle = ''"
    >
      <article class="panel dashboard-history">
        <button
          type="button"
          aria-label="关闭历史记录"
          @click="historyTitle = ''"
        >
          ×
        </button>
        <h2>{{ historyTitle }} · 历史记录</h2>
        <p v-if="!history.length">
          暂无历史记录。
        </p>
        <ol v-else>
          <li
            v-for="event in history"
            :key="event.id"
          >
            <strong>{{ event.detail || event.action }}</strong>
            <p
              v-for="change in historyChanges(event)"
              :key="change"
              class="shipment-history-change"
            >
              {{ change }}
            </p>
            <span>{{ event.actorDisplayName || event.actorLoginName }} · {{ localDateTime(event.occurredAt) }}</span>
          </li>
        </ol>
      </article>
    </div>
    <div
      v-if="dispatchTarget"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="确认发货"
      @keydown.esc="closeDispatch"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="confirmDispatch"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭发货确认"
          :disabled="saving"
          @click="closeDispatch"
        >
          ×
        </button>
        <h2 class="form-wide">
          确认已发货？
        </h2>
        <p class="form-wide shipment-transfer-summary">
          客户：{{ dispatchTarget.customerProduct?.customerName || '未绑定' }}<br>产品名：{{ dispatchTarget.productName }}<br>SKU：{{ dispatchTarget.productCode }}<br>原计划发货日期：{{ dispatchTarget.shipDate }}<br>发货数量：{{ dispatchTarget.quantity }} {{ shipmentUnit(dispatchTarget.quantityUnit) }} · 发货箱数：{{ dispatchTarget.boxCount }} 箱<br>备注：{{ dispatchTarget.remark || '—' }}
        </p>
        <p class="form-wide">
          确认后将按实际确认日期（北京时间）计入发货统计，保留原计划日期。不扣减库存、不触发物流下单，确认后不可编辑或删除。请确认货物确已发出。
        </p>
        <p
          v-if="dispatchError"
          class="data-state data-state--error form-wide"
          role="alert"
        >
          {{ dispatchError }}
        </p>
        <button
          type="button"
          class="secondary-button"
          :disabled="saving"
          @click="closeDispatch"
        >
          取消
        </button>
        <button
          type="submit"
          :disabled="saving"
        >
          {{ saving ? '确认中…' : '确认已发货' }}
        </button>
      </form>
    </div>
  </section>
</template>

<style scoped>
.shipping-editor-pass-through{display:contents}
.shipping-maintenance .shipping-action-buttons .shipping-delete-button{background:#fff1f0!important;color:#b42318!important;border:1px solid #f1c6c2!important;box-shadow:none;border-radius:4px}.shipping-maintenance .shipping-action-buttons .shipping-delete-button:hover:not(:disabled){background:#ffe4e1!important;border-color:#df8f87!important}.shipping-maintenance .shipping-action-buttons .shipping-delete-button:disabled{opacity:.5;cursor:not-allowed}
.shipping-editor.operation-form{display:flex;flex-direction:column;gap:0;padding:0;width:min(860px,calc(100vw - 24px));max-width:calc(100vw - 24px);max-height:calc(100dvh - 32px);overflow:hidden;border-radius:8px}.shipping-editor .shipping-editor-title{flex:none;margin:0;padding:14px 48px 14px 18px;border-bottom:1px solid var(--line);font-size:16px}.shipping-editor-body{overflow:auto;min-height:0;padding:16px 18px;display:grid;gap:16px;overscroll-behavior:contain}.shipping-plan{border:1px solid var(--line);padding:12px;border-radius:5px}.shipping-plan h3{margin:0 0 12px;font-size:14px}.shipping-plan-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 16px}.shipping-editor label{display:flex;flex-direction:column;gap:6px;min-width:0;font-size:13px}.shipping-editor input,.shipping-editor textarea{width:100%;box-sizing:border-box;border:1px solid var(--line);border-radius:4px;padding:8px 10px;font:inherit}.shipping-editor input{height:40px}.shipping-editor textarea{min-height:64px;resize:vertical}.shipping-date-readonly span{height:40px;display:flex;align-items:center;border-radius:4px;background:#f7f9fc;padding:0 10px;color:#64748b;font-size:13px}.shipping-editor .operation-form__actions{flex:none;border-top:1px solid var(--line);padding:12px 18px;margin:0;display:flex;justify-content:flex-end;gap:8px}.shipping-editor .shipping-save{order:2;background:#21834a!important;color:white!important;min-width:88px}.shipping-editor .data-state--error{margin:0}.shipping-editor :deep(.shipment-customer-picker){grid-column:auto}.shipping-editor .shipping-remark{grid-column:auto}@media(max-width:600px){.shipping-editor-body{padding:12px}.shipping-plan-fields{grid-template-columns:1fr}.shipping-editor .shipping-editor-title{padding:12px 44px 12px 12px}}
</style>

<style scoped>
.shipping-lines{min-width:0;overflow:auto}.shipping-lines table{min-width:620px;width:100%}.shipping-lines th{font-size:12px;white-space:nowrap}.shipping-lines td{font-size:13px;vertical-align:middle}.shipping-lines td:nth-child(3){width:140px}.shipping-lines td:nth-child(4){width:110px}.shipping-lines td:last-child{width:70px}.shipping-lines small{display:block;color:var(--muted);font-size:12px;margin-top:6px}.shipping-lines em{color:#c24141;font-style:normal;margin-left:3px}.shipping-line-done{color:#21834a}.shipping-lines .shipping-line-remove{background:#fff1f2;color:#b42335;border:1px solid #f1ccd1;border-radius:4px;padding:5px 10px;font-size:12px;min-height:28px}
</style>
