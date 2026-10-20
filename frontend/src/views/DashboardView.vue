<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { dashboardApi, loadDashboardSummary, type DashboardItem, type DashboardKind, type DashboardSummaryItem, type HomeSlice, type PendingProduct, type PendingShipment, type TodayShipment } from '@/api/dashboard'
import AuthenticatedDashboardImage from '@/components/AuthenticatedDashboardImage.vue'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'
import DashboardShipmentRow from '@/components/DashboardShipmentRow.vue'
import TableViewport from '@/components/TableViewport.vue'
import ContinuousWorklist from '@/components/ContinuousWorklist.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'
import { ApiError } from '@/api/client'
import CartonLabelExport from '@/components/CartonLabelExport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import { createShipmentImage } from '@/ui/shipmentExport'

const formal = runtimeConfig.authMode === 'password'
const summaries = ref<DashboardSummaryItem[]>([]); const loading = ref(true); const errorMessage = ref('')
const active = ref<DashboardKind>('today-shipments'); const previewId = ref('')
const slices = ref<Record<DashboardKind, HomeSlice<DashboardItem> | null>>({
  'today-shipments': null, 'pending-shipments': null, 'pending-products': null,
})
const tabs: Array<{ kind: DashboardKind; label: string; route: string; empty: string }> = [
  { kind: 'today-shipments', label: '今日发货', route: '/dashboard-maintenance/today-shipments', empty: '今日暂无发货安排' },
  { kind: 'pending-shipments', label: '待发货', route: '/dashboard-maintenance/pending-shipments', empty: '暂无待发货记录' },
  { kind: 'pending-products', label: '新品待开发', route: '/dashboard-maintenance/pending-products', empty: '暂无待开发新品' },
]
const currentTab = computed(() => tabs.find(tab => tab.kind === active.value) ?? tabs[0]!)
const currentSlice = computed(() => slices.value[active.value])
let refreshTimer: number | undefined
let loadSequence = 0
const canDispatch = computed(() => session.canAccess(['dashboard.shipments.manage']))
const selectedIds = ref<string[]>([]); const dispatchTarget = ref<TodayShipment[]>(); const dispatchKeys = new Map<string, string>()
const dispatching = ref(false); const dispatchError = ref(''); const dispatchSuccess = ref('')
const todayItems = computed(() => (slices.value['today-shipments']?.items ?? []) as TodayShipment[])
const selectableShipments = computed(() => todayItems.value.filter(item => item.variantId && item.customerProduct))
const selectedShipments = computed(() => selectableShipments.value.filter(item => selectedIds.value.includes(item.id)))
const allSelected = computed(() => selectableShipments.value.length > 0 && selectedShipments.value.length === selectableShipments.value.length)
const labelIds = ref<string[]>()
const exporting = ref(false); const exportUrl = ref(''); const exportName = ref(''); const exportWarning = ref(''); const exportError = ref('')
function toggleSelection(id: string) { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(value => value !== id) : [...selectedIds.value, id] }
function selectAll() { selectedIds.value = allSelected.value ? [] : selectableShipments.value.map(item => item.id) }
function closeExport() { if (exportUrl.value) URL.revokeObjectURL(exportUrl.value); exportUrl.value = '' }
async function exportImage() {
  if (exporting.value || !selectedShipments.value.length) return
  exporting.value = true; exportError.value = ''; closeExport()
  const items = selectedShipments.value.map(item => ({ ...item }))
  const date = slices.value['today-shipments']!.businessDate
  try {
    const { blob, missingImages } = await createShipmentImage(items, date)
    exportUrl.value = URL.createObjectURL(blob); exportName.value = `今日发货清单_${date}.png`
    exportWarning.value = missingImages ? `${missingImages} 条产品图片不可用，已在图片中标注；请检查后再发送。` : ''
  } catch (e) { exportError.value = e instanceof Error ? e.message : '图片导出失败，请重试。' }
  finally { exporting.value = false }
}
watch(active, () => { selectedIds.value = []; if (!dispatching.value) dispatchTarget.value = undefined })
function openDispatch() {
  if (!selectedShipments.value.length || dispatching.value || !canDispatch.value) return
  dispatchTarget.value = selectedShipments.value.map(item => ({ ...item })); dispatchKeys.clear()
  dispatchTarget.value.forEach(item => dispatchKeys.set(item.id, crypto.randomUUID())); dispatchError.value = ''
}
async function confirmDispatch() {
  if (!dispatchTarget.value || dispatching.value) return
  dispatching.value = true; dispatchError.value = ''; dispatchSuccess.value = ''
  try {
    for (const item of [...dispatchTarget.value]) {
      await dashboardApi.dispatch(item.id, item.version, dispatchKeys.get(item.id)!)
      ++loadSequence
      const today = slices.value['today-shipments']
      if (today) { today.items = today.items.filter(row => row.id !== item.id); today.totalCount = Math.max(0, today.totalCount - 1) }
      selectedIds.value = selectedIds.value.filter(id => id !== item.id)
      dispatchTarget.value = dispatchTarget.value.filter(row => row.id !== item.id)
    }
    dispatchTarget.value = undefined; selectedIds.value = []; dispatchSuccess.value = '发货已确认，已计入发货统计。'; await load(true)
  } catch (e) { dispatchError.value = `${e instanceof ApiError ? e.message : '发货确认失败，请重试。'} 已成功的记录已移除，下面仅保留未完成记录。` }
  finally { dispatching.value = false }
}

function number(value: number) { return new Intl.NumberFormat('zh-CN').format(value) }
function shanghaiDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'short', hour12: false }).format(new Date(value))
}
function dateOf(item: DashboardItem) {
  if ('shipDate' in item) return item.shipDate
  if ('plannedShipDate' in item) return item.plannedShipDate
  return item.expectedCompletionDate
}
function quantityOf(item: DashboardItem) { return 'expectedQuantity' in item ? item.expectedQuantity : item.quantity }
function isLate(item: DashboardItem, businessDate?: string) {
  if ('latestShipAt' in item && 'shipDate' in item) return Date.now() > new Date(item.latestShipAt).getTime()
  return dateOf(item) < (businessDate ?? new Date().toISOString().slice(0, 10))
}
async function load(silent = false) {
  const sequence = ++loadSequence
  if (!silent) loading.value = true
  try {
    if (!formal) { summaries.value = (await loadDashboardSummary()).items; return }
    const [today, pending, products] = await Promise.all([
      dashboardApi.home<TodayShipment>('today-shipments'),
      dashboardApi.home<PendingShipment>('pending-shipments'),
      dashboardApi.home<PendingProduct>('pending-products'),
    ])
    if (sequence !== loadSequence) return
    slices.value = { 'today-shipments': today, 'pending-shipments': pending, 'pending-products': products }
    selectedIds.value = selectedIds.value.filter(id => today.items.some(item => item.id === id))
    errorMessage.value = ''
  }
  catch {
    if (sequence !== loadSequence) return
    errorMessage.value = formal ? '工作台数据加载失败。已保留上次成功结果，请稍后重试。' : '模拟数据服务暂不可用，请确认后端已使用 mock profile 启动。'
  }
  finally { if (!silent && sequence === loadSequence) loading.value = false }
}
function onFocus() { if (formal && !dispatching.value && !dispatchTarget.value) void load(true) }
onMounted(() => {
  void load(); window.addEventListener('focus', onFocus)
  if (formal) refreshTimer = window.setInterval(onFocus, 60_000)
})
onBeforeUnmount(() => { window.clearInterval(refreshTimer); window.removeEventListener('focus', onFocus); closeExport() })
</script>

<template>
  <section class="page-stack">
    <SuccessToast :message="dispatchSuccess" />
    <SuccessToast
      :message="exportError"
      tone="error"
    />
    <template v-if="!formal">
      <div class="notice">
        <strong>本地模拟数据模式</strong><span>所有数字均为 SYNTHETIC 模拟数据，仅用于功能联调，不代表真实业务验收。</span>
      </div>
      <p
        v-if="loading"
        class="data-state"
      >
        正在加载模拟数据…
      </p>
      <p
        v-else-if="errorMessage"
        class="data-state data-state--error"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <div
        v-else
        class="summary-grid"
        aria-label="模拟业务摘要"
      >
        <article
          v-for="item in summaries"
          :key="item.label"
          class="summary-card"
        >
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small>
        </article>
      </div>
    </template>

    <template v-else>
      <div class="page-actions">
        <p>今日发货表示当日安排，不代表已确认出库或物流发运。</p>
        <button
          class="secondary-action"
          type="button"
          :disabled="loading"
          @click="load()"
        >
          刷新
        </button>
      </div>
      <p
        v-if="errorMessage"
        class="data-state data-state--error"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <article class="panel dashboard-worklist-panel">
        <div
          class="dashboard-tabs"
          role="tablist"
          aria-label="首页工作清单"
        >
          <button
            v-for="tab in tabs"
            :id="`tab-${tab.kind}`"
            :key="tab.kind"
            type="button"
            role="tab"
            :aria-selected="active === tab.kind"
            :class="{ 'is-active': active === tab.kind }"
            @click="active = tab.kind"
          >
            {{ tab.label }} <span>{{ slices[tab.kind]?.totalCount ?? 0 }}</span>
          </button>
        </div>
        <p
          v-if="loading"
          class="data-state"
        >
          正在加载工作清单…
        </p>
        <div
          v-else-if="!currentSlice?.items.length"
          class="empty-state dashboard-empty"
        >
          <h2>{{ currentTab.empty }}</h2>
        </div>
        <template v-else>
          <TableViewport v-if="active !== 'pending-products'">
            <table
              class="data-table dashboard-shipment-table"
              :aria-label="currentTab.label"
            >
              <thead>
                <tr>
                  <th
                    v-if="active === 'today-shipments'"
                    class="shipment-select-cell"
                  >
                    <input
                      type="checkbox"
                      aria-label="全选当前今日发货列表"
                      :checked="allSelected"
                      :indeterminate="selectedShipments.length > 0 && !allSelected"
                      :disabled="dispatching || exporting"
                      @change="selectAll"
                    >
                  </th><th v-if="active === 'pending-shipments'">
                    计划 / 最晚日期
                  </th><th>产品图片</th><th>产品名称 / SKU</th><th>发货数量</th><th>发货箱数</th><th>备注</th><th>商品标签</th><th>外箱标签</th><th>说明书下载</th>
                </tr>
              </thead>
              <tbody>
                <DashboardShipmentRow
                  v-for="item in currentSlice.items"
                  :key="item.id"
                  :item="(item as TodayShipment | PendingShipment)"
                  :scope="active"
                  :selectable="true"
                  :selected="selectedIds.includes(item.id)"
                  :disabled="dispatching || exporting"
                  @select="toggleSelection"
                />
              </tbody>
            </table>
          </TableViewport>
          <ContinuousWorklist
            v-else
            :key="active"
            :items="currentSlice.items"
            :label="currentTab.label"
          >
            <template #default="{ item }">
              <ShipmentProductImage
                v-if="'productId' in item"
                :product-id="item.productId"
                :alt="item.productName"
              />
              <button
                v-else-if="item.imageId"
                class="image-button"
                type="button"
                aria-label="预览产品图案"
                @click="previewId = item.imageId"
              >
                <AuthenticatedDashboardImage
                  :image-id="item.imageId"
                  :alt="item.productName"
                />
              </button>
              <AuthenticatedDashboardImage
                v-else
                :image-id="undefined"
                :alt="item.productName"
              />
              <div class="worklist-row__main">
                <strong>{{ item.productName }}</strong><small v-if="'productCode' in item">{{ item.productCode }}</small><small>{{ item.remark || '无备注' }}</small>
              </div>
              <div><span class="worklist-label">{{ 'expectedQuantity' in item ? '预计数量' : '发货数量' }}</span><strong>{{ number(quantityOf(item)) }} 套</strong></div>
              <div v-if="'boxCount' in item">
                <span class="worklist-label">箱数</span><strong>{{ item.boxCount }}</strong>
              </div>
              <div><span class="worklist-label">{{ 'expectedCompletionDate' in item ? '预计完成' : 'latestShipAt' in item ? '最晚时间' : '日期' }}</span><strong>{{ 'latestShipAt' in item ? shanghaiDateTime(item.latestShipAt) : dateOf(item) }}</strong></div>
              <span
                v-if="isLate(item, currentSlice?.businessDate)"
                class="overdue-badge"
              >{{ 'shipDate' in item ? '已超时' : '已逾期' }}</span>
            </template>
          </ContinuousWorklist>
        </template>
        <div class="dashboard-worklist-footer shipment-home-footer">
          <div class="shipment-home-actions">
            <RouterLink :to="currentTab.route">
              前往{{ currentTab.label }}管理
            </RouterLink>
            <button
              v-if="active === 'today-shipments' && canDispatch"
              type="button"
              class="primary-link"
              :disabled="!selectedShipments.length || dispatching || loading || exporting"
              @click="openDispatch"
            >
              一键发货
            </button>
            <button
              v-if="active === 'today-shipments'"
              type="button"
              class="secondary-action shipment-export-button"
              :class="{ 'is-selected': selectedShipments.length > 0 }"
              :disabled="!selectedShipments.length || exporting || dispatching || loading"
              @click="exportImage"
            >
              {{ exporting ? '生成图片中…' : '导出' }}
            </button>
            <button
              v-if="active === 'today-shipments' && canDispatch"
              type="button"
              :disabled="!selectedShipments.length || loading || dispatching"
              @click="labelIds = selectedShipments.map(item => item.id)"
            >
              导出外箱标 PDF
            </button>
            <CartonLabelExport
              v-if="labelIds"
              :shipment-ids="labelIds"
              @close="labelIds = undefined"
            />
            <small v-if="active === 'today-shipments'">{{ selectedShipments.length ? `已选 ${selectedShipments.length} 条` : '请先勾选需要导出的记录' }}（最多展示10条）</small>
          </div>
          <span v-if="currentSlice?.truncated">仅展示前 10 条，前往维护页查看全部</span>
        </div>
      </article>
    </template>

    <div
      v-if="dispatchTarget"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dispatch-confirm-title"
      @click.self="!dispatching && (dispatchTarget = undefined)"
      @keydown.esc="!dispatching && (dispatchTarget = undefined)"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact shipment-transfer-dialog"
        @submit.prevent="confirmDispatch"
      >
        <button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭发货确认"
          :disabled="dispatching"
          @click="dispatchTarget = undefined"
        >
          ×
        </button>
        <h2
          id="dispatch-confirm-title"
          class="form-wide"
        >
          确认发货
        </h2>
        <p
          v-for="item in dispatchTarget"
          :key="item.id"
          class="form-wide shipment-transfer-summary"
        >
          产品名：{{ item.productName }}<br>发货数量：{{ item.quantity }} 套 · 发货箱数：{{ item.boxCount }}<br>备注：{{ item.remark || '—' }}
        </p>
        <p class="form-wide">
          请确认所列 {{ dispatchTarget.length }} 条记录实际已发货。系统逐条确认，成功后移除并计入统计，失败时保留未完成记录供重试；不会自动扣减库存或下单物流。
        </p>
        <p
          v-if="dispatchError"
          role="alert"
          class="form-wide data-state data-state--error"
        >
          {{ dispatchError }}
        </p>
        <div class="form-footer form-wide">
          <button
            type="button"
            class="secondary-button"
            :disabled="dispatching"
            @click="dispatchTarget = undefined"
          >
            取消
          </button><button
            type="submit"
            :disabled="dispatching"
          >
            {{ dispatching ? '确认中…' : '确认已发货' }}
          </button>
        </div>
      </form>
    </div>

    <div
      v-if="exportUrl"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="发货图片导出预览"
      @click.self="closeExport"
      @keydown.esc="closeExport"
    >
      <article class="panel shipment-export-preview">
        <div class="shipment-home-actions">
          <h2>发货清单图片</h2><a
            class="primary-link"
            :href="exportUrl"
            :download="exportName"
          >下载图片</a><button
            type="button"
            class="secondary-action"
            @click="closeExport"
          >
            关闭
          </button>
        </div>
        <p
          v-if="exportWarning"
          role="alert"
        >
          {{ exportWarning }}
        </p>
        <img
          :src="exportUrl"
          alt="发货清单导出图片，包含日期、产品图片、产品名称、发货数量、发货箱数和备注"
        >
      </article>
    </div>

    <div
      v-if="previewId"
      class="image-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="产品图案预览"
      @click.self="previewId = ''"
    >
      <div>
        <button
          type="button"
          aria-label="关闭预览"
          @click="previewId = ''"
        >
          ×
        </button><AuthenticatedDashboardImage
          :image-id="previewId"
          variant="original"
          alt="产品图案原图"
        />
      </div>
    </div>
  </section>
</template>
