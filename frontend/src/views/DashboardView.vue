<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { dashboardApi, loadDashboardSummary, type DashboardItem, type DashboardKind, type DashboardSummaryItem, type HomeSlice, type PendingProduct, type PendingShipment, type TodayShipment } from '@/api/dashboard'
import AuthenticatedDashboardImage from '@/components/AuthenticatedDashboardImage.vue'
import ContinuousWorklist from '@/components/ContinuousWorklist.vue'
import { runtimeConfig } from '@/config/runtime'

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
  if (!silent) loading.value = true
  try {
    if (!formal) { summaries.value = (await loadDashboardSummary()).items; return }
    const [today, pending, products] = await Promise.all([
      dashboardApi.home<TodayShipment>('today-shipments'),
      dashboardApi.home<PendingShipment>('pending-shipments'),
      dashboardApi.home<PendingProduct>('pending-products'),
    ])
    slices.value = { 'today-shipments': today, 'pending-shipments': pending, 'pending-products': products }
    errorMessage.value = ''
  }
  catch {
    errorMessage.value = formal ? '工作台数据加载失败。已保留上次成功结果，请稍后重试。' : '模拟数据服务暂不可用，请确认后端已使用 mock profile 启动。'
  }
  finally { if (!silent) loading.value = false }
}
function onFocus() { if (formal) void load(true) }
onMounted(() => {
  void load(); window.addEventListener('focus', onFocus)
  if (formal) refreshTimer = window.setInterval(() => void load(true), 60_000)
})
onBeforeUnmount(() => { window.clearInterval(refreshTimer); window.removeEventListener('focus', onFocus) })
</script>

<template>
  <section class="page-stack">
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
          <ContinuousWorklist
            :key="active"
            :items="currentSlice.items"
            :label="currentTab.label"
          >
            <template #default="{ item }">
              <button
                v-if="item.imageId"
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
          <div class="dashboard-worklist-footer">
            <span v-if="currentSlice.truncated">仅展示前 10 条，前往维护页查看全部</span>
            <RouterLink :to="currentTab.route">
              前往{{ currentTab.label }}管理
            </RouterLink>
          </div>
        </template>
      </article>
    </template>

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
