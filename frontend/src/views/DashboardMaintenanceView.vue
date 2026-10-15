<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { dashboardApi, type DashboardHistoryEvent, type DashboardItem, type DashboardKind, type PendingProduct, type PendingProductInput, type PendingShipment, type ShipmentInput } from '@/api/dashboard'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedDashboardImage from '@/components/AuthenticatedDashboardImage.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'

const props = defineProps<{ kind: DashboardKind }>()
const formal = runtimeConfig.authMode === 'password'
const items = ref<DashboardItem[]>([]); const products = ref<ProductDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const page = ref(0); const totalPages = ref(0); const total = ref(0); const editing = ref<DashboardItem | null>(null)
const filters = reactive({ keyword: '', status: '', from: '', to: '' })
const shipment = reactive<ShipmentInput>({ date: '', productId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
const pendingProduct = reactive<PendingProductInput>({ productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
const correctionReason = ref(''); const imageId = ref(''); const imageFile = ref<File>(); const previewUrl = ref('')
const history = ref<DashboardHistoryEvent[]>([]); const historyTitle = ref('')

const isProduct = computed(() => props.kind === 'pending-products')
const title = computed(() => props.kind === 'today-shipments' ? '今日发货管理' : props.kind === 'pending-shipments' ? '待发货管理' : '新品待开发管理')
const activeStatus = computed(() => props.kind === 'today-shipments' ? 'ACTIVE' : 'PENDING')
const dateLabel = computed(() => props.kind === 'today-shipments' ? '发货日期' : '计划发货日期')
const remarkModel = computed({ get: () => isProduct.value ? pendingProduct.remark : shipment.remark, set: value => { if (isProduct.value) pendingProduct.remark = value ?? ''; else shipment.remark = value ?? '' } })
function localDateTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—' }
function reset() {
  editing.value = null; imageId.value = ''; imageFile.value = undefined; previewUrl.value = ''; correctionReason.value = ''
  Object.assign(shipment, { date: '', productId: '', quantity: 1, boxCount: 1, remark: '', latestShipTime: '' })
  Object.assign(pendingProduct, { productName: '', expectedQuantity: 1, remark: '', expectedCompletionDate: '' })
}
function edit(item: DashboardItem) {
  editing.value = item; imageId.value = item.imageId ?? ''; imageFile.value = undefined; previewUrl.value = ''; error.value = ''; success.value = ''
  if ('expectedQuantity' in item) Object.assign(pendingProduct, { productName: item.productName, expectedQuantity: item.expectedQuantity, remark: item.remark ?? '', expectedCompletionDate: item.expectedCompletionDate })
  else Object.assign(shipment, { date: 'shipDate' in item ? item.shipDate : item.plannedShipDate, productId: item.productId, quantity: item.quantity, boxCount: item.boxCount, remark: item.remark ?? '', latestShipTime: item.latestShipAt.slice(0, 16) })
}
async function load() {
  if (!formal) { loading.value = false; return }
  loading.value = true; error.value = ''
  try {
    const result = await dashboardApi.page<DashboardItem>(props.kind, { ...filters, page: page.value, size: 20 })
    items.value = result.items; totalPages.value = result.totalPages; total.value = result.totalElements
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '工作清单加载失败。' }
  finally { loading.value = false }
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
    const uploadedId = await uploadIfNeeded()
    if (isProduct.value) {
      const input = { ...pendingProduct, imageId: uploadedId, productName: pendingProduct.productName.trim(), remark: pendingProduct.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updatePendingProduct(editing.value.id, input, editing.value.version)
      else await dashboardApi.createPendingProduct(input)
    } else {
      const input = { ...shipment, imageId: uploadedId, remark: shipment.remark?.trim() || undefined }
      if (editing.value) await dashboardApi.updateShipment(props.kind as Exclude<DashboardKind, 'pending-products'>, editing.value.id, input, editing.value.version, correctionReason.value.trim() || undefined)
      else await dashboardApi.createShipment(props.kind as Exclude<DashboardKind, 'pending-products'>, input)
    }
    success.value = editing.value ? '记录已更新。' : '记录已创建。'; reset(); await load()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败，请检查字段和图片。' }
  finally { saving.value = false }
}
async function archive(item: DashboardItem) {
  const reason = window.prompt('请输入归档原因')?.trim(); if (!reason) return
  saving.value = true
  try { await dashboardApi.archive(props.kind, item.id, item.version, reason); success.value = '记录已归档。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '归档失败。' }
  finally { saving.value = false }
}
async function complete(item: PendingProduct) {
  if (!window.confirm('确认将该新品标记为已完成？')) return
  saving.value = true
  try { await dashboardApi.completePendingProduct(item.id, item.version); success.value = '新品已标记完成。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '操作失败。' }
  finally { saving.value = false }
}
async function transfer(item: PendingShipment) {
  if (!window.confirm('确认按当前数量与箱数转入今日发货安排？')) return
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const latestShipTime = `${today}T23:59`
  saving.value = true
  try { await dashboardApi.transferToToday(item.id, { quantity: item.quantity, boxCount: item.boxCount, remark: item.remark, latestShipTime, imageId: item.imageId, version: item.version }); success.value = '已转入今日发货。'; await load() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '转入失败。' }
  finally { saving.value = false }
}
async function showHistory(item: DashboardItem) {
  error.value = ''
  try { history.value = await dashboardApi.history(props.kind, item.id); historyTitle.value = item.productName }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '历史记录加载失败。' }
}
function statusLabel(value: string) { return ({ ACTIVE: '有效', PENDING: '待处理', TRANSFERRED_TO_TODAY: '已转入', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[value] ?? value }
watch(() => props.kind, () => { page.value = 0; reset(); void load() })
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
      <form
        class="panel operation-form"
        @submit.prevent="save"
      >
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
          >{{ product.productCode }} · {{ product.nameZh }}</option></select></label>
          <label>{{ dateLabel }}<input
            v-model="shipment.date"
            required
            type="date"
          ></label>
          <label>最晚发货时间<input
            v-model="shipment.latestShipTime"
            required
            type="datetime-local"
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
        <label>产品图案<input
          accept="image/jpeg,image/png,image/webp"
          type="file"
          @change="chooseImage"
        ></label>
        <div class="dashboard-image-preview">
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
        <label class="form-wide">备注<textarea
          v-model="remarkModel"
          maxlength="500"
        /></label>
        <button
          :disabled="saving"
          type="submit"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button
          v-if="editing"
          class="secondary-button"
          type="button"
          @click="reset"
        >
          取消
        </button>
      </form>
      <form
        class="panel dashboard-filters"
        @submit.prevent="page = 0; load()"
      >
        <label>关键字<input
          v-model="filters.keyword"
          maxlength="128"
        ></label>
        <label>状态<select v-model="filters.status"><option value="">全部</option><option :value="activeStatus">{{ statusLabel(activeStatus) }}</option><option
          v-if="isProduct"
          value="COMPLETED"
        >已完成</option><option
          v-if="kind === 'pending-shipments'"
          value="TRANSFERRED_TO_TODAY"
        >已转入</option><option value="ARCHIVED">已归档</option></select></label>
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
      <p
        v-if="success"
        class="data-state data-state--success"
        role="status"
      >
        {{ success }}
      </p>
      <p
        v-if="error"
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
          <table class="data-table">
            <thead><tr><th>图案</th><th>产品</th><th>日期</th><th>数量</th><th>箱数</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="item in items"
                :key="item.id"
              >
                <td>
                  <AuthenticatedDashboardImage
                    :image-id="item.imageId"
                    :alt="item.productName"
                  />
                </td><td>{{ item.productName }}<small v-if="'productCode' in item">{{ item.productCode }}</small></td><td>{{ 'shipDate' in item ? item.shipDate : 'plannedShipDate' in item ? item.plannedShipDate : item.expectedCompletionDate }}</td><td>{{ ('expectedQuantity' in item ? item.expectedQuantity : item.quantity).toLocaleString() }} 套</td><td>{{ 'boxCount' in item ? item.boxCount : '—' }}</td><td>{{ statusLabel(item.status) }}</td><td>{{ localDateTime(item.updatedAt) }}</td><td class="table-actions">
                  <button
                    v-if="item.status === activeStatus"
                    type="button"
                    @click="edit(item)"
                  >
                    编辑
                  </button><button
                    type="button"
                    @click="showHistory(item)"
                  >
                    历史
                  </button><button
                    v-if="kind === 'pending-shipments' && item.status === 'PENDING'"
                    type="button"
                    @click="transfer(item as PendingShipment)"
                  >
                    转入今日
                  </button><button
                    v-if="isProduct && item.status === 'PENDING'"
                    type="button"
                    @click="complete(item as PendingProduct)"
                  >
                    完成
                  </button><button
                    v-if="item.status === activeStatus"
                    class="danger-button"
                    type="button"
                    @click="archive(item)"
                  >
                    归档
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
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
    </template>
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
            <span>{{ event.actorDisplayName || event.actorLoginName }} · {{ localDateTime(event.occurredAt) }}</span>
          </li>
        </ol>
      </article>
    </div>
  </section>
</template>
