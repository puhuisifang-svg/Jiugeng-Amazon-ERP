<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { dashboardApi, type DashboardHistoryEvent, type DashboardItem, type DashboardKind, type PendingProduct, type PendingProductInput, type PendingShipment, type ShipmentInput } from '@/api/dashboard'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedDashboardImage from '@/components/AuthenticatedDashboardImage.vue'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'
import ShipmentFiles from '@/components/ShipmentFiles.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'

const props = defineProps<{ kind: DashboardKind }>()
const formal = runtimeConfig.authMode === 'password'
const items = ref<DashboardItem[]>([]); const products = ref<ProductDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const page = ref(0); const totalPages = ref(0); const total = ref(0); const editing = ref<DashboardItem | null>(null)
const filters = reactive({ keyword: '', status: props.kind === 'today-shipments' ? 'ACTIVE' : '', from: '', to: '' })
const shipment = reactive<ShipmentInput>({ date: '', productId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
const pendingProduct = reactive<PendingProductInput>({ productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
const correctionReason = ref(''); const imageId = ref(''); const imageFile = ref<File>(); const previewUrl = ref('')
const history = ref<DashboardHistoryEvent[]>([]); const historyTitle = ref('')
const formOpen = ref(false)
const latestShipDate = ref('')
const pendingTransferTarget = ref<DashboardItem>()
const transferReason = ref('')
const deleteTarget = ref<DashboardItem>()
const todayTransferTarget = ref<PendingShipment>()
const validationToast = ref('')
const validationToastKey = ref(0)
let loadSequence = 0

const isProduct = computed(() => props.kind === 'pending-products')
const title = computed(() => props.kind === 'today-shipments' ? '今日发货管理' : props.kind === 'pending-shipments' ? '待发货管理' : '新品待开发管理')
const activeStatus = computed(() => props.kind === 'today-shipments' ? 'ACTIVE' : 'PENDING')
const dateLabel = computed(() => props.kind === 'today-shipments' ? '发货日期' : '计划发货日期')
const remarkModel = computed({ get: () => isProduct.value ? pendingProduct.remark : shipment.remark, set: value => { if (isProduct.value) pendingProduct.remark = value ?? ''; else shipment.remark = value ?? '' } })
function localDateTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—' }
function localDate(value: string) { return value ? new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date(value)) : '—' }
function historyChanges(event: DashboardHistoryEvent) {
  function snapshot(value: unknown): Record<string, unknown> {
    if (typeof value === 'string') { try { return JSON.parse(value) as Record<string, unknown> } catch { return {} } }
    return value && typeof value === 'object' ? value as Record<string, unknown> : {}
  }
  if (!event.beforeValue || !event.afterValue) return []
  const before = snapshot(event.beforeValue); const after = snapshot(event.afterValue)
  const fields: Record<string, string> = { productName: '产品名', quantity: '发货数量', boxCount: '箱数', remark: '备注', plannedShipDate: '计划发货日期', latestShipAt: '最晚发货日期', status: '状态' }
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
  formOpen.value = false; latestShipDate.value = ''
  editing.value = null; imageId.value = ''; imageFile.value = undefined; previewUrl.value = ''; correctionReason.value = ''
  Object.assign(shipment, { date: '', productId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
  Object.assign(pendingProduct, { productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
}
function edit(item: DashboardItem) {
  formOpen.value = true
  if ('latestShipAt' in item) latestShipDate.value = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date(item.latestShipAt))
  editing.value = item; imageId.value = item.imageId ?? ''; imageFile.value = undefined; previewUrl.value = ''; error.value = ''; success.value = ''
  if ('expectedQuantity' in item) Object.assign(pendingProduct, { productName: item.productName, expectedQuantity: item.expectedQuantity, remark: item.remark ?? '', expectedCompletionDate: item.expectedCompletionDate })
  else Object.assign(shipment, { date: 'shipDate' in item ? item.shipDate : item.plannedShipDate, productId: item.productId, quantity: item.quantity, boxCount: item.boxCount, remark: item.remark ?? '', latestShipTime: item.latestShipAt.slice(0, 16) })
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
    const result = await dashboardApi.page<DashboardItem>(props.kind, { ...filters, page: page.value, size: 20 })
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
async function chooseImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) { error.value = '图片不能超过 5 MB。'; return }
  imageFile.value = file; previewUrl.value = URL.createObjectURL(file)
}
async function uploadIfNeeded() {
  if (!imageFile.value) return imageId.value || undefined
  const receipt = await dashboardApi.uploadImage(isProduct.value ? 'PENDING_PRODUCT' : 'SHIPMENT', imageFile.value)
  return receipt.id
}
async function save() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const uploadedId = isProduct.value ? await uploadIfNeeded() : undefined
    if (isProduct.value) {
      const input = { ...pendingProduct, imageId: uploadedId, productName: pendingProduct.productName.trim(), remark: pendingProduct.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updatePendingProduct(editing.value.id, input, editing.value.version)
      else await dashboardApi.createPendingProduct(input)
    } else {
      const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
      const shipDate = editing.value && 'shipDate' in editing.value ? editing.value.shipDate : today
      const input = { ...shipment, ...(props.kind === 'today-shipments' ? { date: shipDate, latestShipTime: `${shipDate}T23:59` } : { latestShipTime: `${latestShipDate.value}T23:59` }), imageId: uploadedId, remark: shipment.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updateShipment(props.kind as Exclude<DashboardKind, 'pending-products'>, editing.value.id, input, editing.value.version, correctionReason.value.trim() || undefined)
      else await dashboardApi.createShipment(props.kind as Exclude<DashboardKind, 'pending-products'>, input)
    }
    success.value = editing.value ? '记录已更新。' : '记录已创建。'; reset(); await load()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败，请检查字段和图片。' }
  finally { saving.value = false }
}
async function archive(item: DashboardItem, confirmed = false) {
  if (saving.value) return
  const deleting = !isProduct.value
  if (deleting && !confirmed) { deleteTarget.value = item; error.value = ''; return }
  const reason = deleting ? '用户删除' : window.prompt('请输入归档原因')?.trim()
  if (!reason) return
  saving.value = true
  success.value = ''; error.value = ''
  try { await dashboardApi.archive(props.kind, item.id, item.version, reason); deleteTarget.value = undefined; success.value = deleting ? '记录已删除。' : '记录已归档。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : deleting ? '删除失败。' : '归档失败。' }
  finally { saving.value = false }
}
async function complete(item: PendingProduct) {
  if (!window.confirm('确认将该新品标记为已完成？')) return
  saving.value = true; success.value = ''; error.value = ''
  try { await dashboardApi.completePendingProduct(item.id, item.version); success.value = '新品已标记完成。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '操作失败。' }
  finally { saving.value = false }
}
async function transfer(item: PendingShipment, confirmed = false) {
  if (saving.value) return
  if (!confirmed) { todayTransferTarget.value = item; error.value = ''; return }
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
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
watch(() => props.kind, () => {
  page.value = 0
  Object.assign(filters, { keyword: '', status: props.kind === 'today-shipments' ? 'ACTIVE' : '', from: '', to: '' })
  items.value = []; total.value = 0; totalPages.value = 0
  error.value = ''; success.value = ''; validationToast.value = ''
  pendingTransferTarget.value = undefined; transferReason.value = ''; historyTitle.value = ''; history.value = []
  deleteTarget.value = undefined
  todayTransferTarget.value = undefined
  reset(); void load()
})
onMounted(async () => {
  if (!formal) { loading.value = false; return }
  if (!isProduct.value) products.value = (await domainApi.products()).items.filter(item => item.status !== 'DISABLED')
  await load()
})
</script>

<template>
  <section class="page-stack">
    <template v-if="!formal">
      <SyntheticNotice />
      <article class="panel empty-state">
        <h2>正式工作台维护仅在密码登录模式启用</h2>
        <p>mock 模式继续提供明确标识的合成摘要，不会将模拟记录写入正式工作台。</p>
      </article>
    </template>
    <template v-else>
      <div class="page-actions">
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
          @click="reset(); error = ''; formOpen = true"
        >
          {{ kind === 'pending-shipments' ? '新增待发货' : '新增今日发货' }}
        </button>
      </div>
      <div
        v-if="isProduct || formOpen"
        :class="isProduct ? '' : 'operation-dialog'"
        :role="isProduct ? undefined : 'dialog'"
        :aria-modal="isProduct ? undefined : true"
        aria-label="发货记录"
        @click.self="!saving && !isProduct && reset()"
        @keydown.esc="!saving && !isProduct && reset()"
      >
        <form
          class="panel operation-form"
          :class="{ 'operation-dialog__card operation-dialog__card--compact': !isProduct }"
          @submit.prevent="save"
        >
          <button
            v-if="!isProduct"
            class="operation-dialog__close"
            type="button"
            aria-label="关闭发货弹窗"
            :disabled="saving"
            @click="reset()"
          >
            ×
          </button>
          <h2>{{ editing ? '编辑记录' : '新增记录' }}</h2>
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
            <label>预计完成日期<input
              v-model="pendingProduct.expectedCompletionDate"
              required
              type="date"
            ></label>
          </template>
          <template v-else>
            <label>产品编号<select
              v-model="shipment.productId"
              required
            ><option value="">请选择</option><option
              v-for="product in products"
              :key="product.id"
              :value="product.id"
            >{{ product.productCode }} · {{ product.productName }}</option></select></label>
            <label v-if="kind !== 'today-shipments'">{{ dateLabel }}<input
              v-model="shipment.date"
              required
              type="date"
            ></label>
            <label v-if="kind !== 'today-shipments'">最晚发货日期<input
              v-model="latestShipDate"
              required
              type="date"
              :min="shipment.date"
            ></label>
            <label>发货数量（套）<input
              v-model.number="shipment.quantity"
              required
              type="number"
              min="1"
            ></label>
            <label>箱数<input
              v-model.number="shipment.boxCount"
              required
              type="number"
              min="1"
            ></label>
            <label v-if="editing && kind === 'today-shipments'">更正原因<input
              v-model="correctionReason"
              maxlength="500"
            ></label>
          </template>
          <label v-if="isProduct">产品图案<input
            accept="image/jpeg,image/png,image/webp"
            type="file"
            @change="chooseImage"
          ></label>
          <div class="dashboard-image-preview">
            <ShipmentProductImage
              v-if="!isProduct"
              :product-id="shipment.productId"
              :product="products.find(item => item.id === shipment.productId)"
              alt="所选产品图案"
            />
            <img
              v-else-if="previewUrl"
              :src="previewUrl"
              alt="待上传产品图案"
            ><AuthenticatedDashboardImage
              v-else
              :image-id="imageId || undefined"
              alt="当前产品图案"
            />
          </div>
          <label class="form-wide">备注<input
            v-model="remarkModel"
            maxlength="500"
          ></label>
          <button
            :disabled="saving"
            type="submit"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
          <button
            v-if="editing || !isProduct"
            class="secondary-button"
            type="button"
            @click="reset"
          >
            取消
          </button>
          <p
            v-if="error && !isProduct"
            class="data-state data-state--error form-wide"
            role="alert"
          >
            {{ error }}
          </p>
        </form>
      </div>
      <form
        class="panel dashboard-filters"
        @submit.prevent="page = 0; load()"
      >
        <label>关键字<input
          v-model="filters.keyword"
          maxlength="128"
          :placeholder="isProduct ? '搜索产品名称' : '搜索产品编码、产品名称或备注'"
        ></label>
        <label>状态<select v-model="filters.status"><option value="">全部</option><option :value="activeStatus">{{ statusLabel(activeStatus) }}</option><option
          v-if="isProduct"
          value="COMPLETED"
        >已完成</option><option
          v-if="kind === 'pending-shipments'"
          value="TRANSFERRED_TO_TODAY"
        >已转入</option><option
          v-if="kind === 'today-shipments'"
          value="SHIPPED"
        >已发货</option><option value="ARCHIVED">已归档</option></select></label>
        <label>开始日期<input
          v-model="filters.from"
          type="date"
        ></label><label>结束日期<input
          v-model="filters.to"
          type="date"
        ></label>
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
        <div class="table-scroll">
          <TableViewport>
            <table class="data-table">
              <thead>
                <tr>
                  <th>{{ kind === 'today-shipments' ? '发货日期' : kind === 'pending-shipments' ? '计划发货日期' : '预计完成日期' }}</th><th>产品图片</th><th>产品</th><th v-if="kind === 'pending-shipments'">
                    最晚发货日期
                  </th><th>数量</th><th>箱数</th><th>备注</th><th v-if="kind === 'today-shipments'">
                    商品标签
                  </th><th v-if="kind === 'today-shipments'">
                    外箱标签
                  </th><th>状态</th><th>更新时间</th><th>操作</th>
                </tr>
              </thead><tbody>
                <tr
                  v-for="(item, index) in items"
                  :key="item.id"
                >
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
                      :product="products.find(product => product.id === item.productId)"
                      :alt="item.productName"
                    />
                  </td><td>{{ item.productName }}<small v-if="'productCode' in item">{{ item.productCode }}</small></td><td v-if="kind === 'pending-shipments'">
                    {{ 'latestShipAt' in item ? localDate(item.latestShipAt) : '—' }}
                  </td><td>{{ ('expectedQuantity' in item ? item.expectedQuantity : item.quantity).toLocaleString() }} 套</td><td>{{ 'boxCount' in item ? item.boxCount : '—' }}</td><td class="shipment-remark">
                    {{ item.remark || '—' }}
                  </td><td v-if="kind === 'today-shipments'">
                    <ShipmentFiles
                      :shipment-id="item.id"
                      scope="today-shipments"
                      kind="LABEL"
                      :editable="item.status === 'ACTIVE'"
                    />
                  </td><td v-if="kind === 'today-shipments'">
                    <ShipmentFiles
                      :shipment-id="item.id"
                      scope="today-shipments"
                      kind="CARTON_LABEL"
                      :editable="item.status === 'ACTIVE'"
                    />
                  </td><td>{{ statusLabel(item.status) }}</td><td>{{ localDateTime(item.updatedAt) }}</td><td class="table-actions">
                    <button
                      v-if="item.status === activeStatus"
                      type="button"
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
                      class="danger-button"
                      type="button"
                      :disabled="saving"
                      @click="archive(item)"
                    >
                      {{ isProduct ? '归档' : '删除' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div><div class="pagination">
          <button
            :disabled="page === 0"
            @click="page--; load()"
          >
            上一页
          </button><span>第 {{ page + 1 }} / {{ Math.max(totalPages, 1) }} 页</span><button
            :disabled="page + 1 >= totalPages"
            @click="page++; load()"
          >
            下一页
          </button>
        </div>
      </article>
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
          产品名：{{ todayTransferTarget.productName }}<br>发货数量：{{ todayTransferTarget.quantity }} 套 · 发货箱数：{{ todayTransferTarget.boxCount }}
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
  </section>
</template>
