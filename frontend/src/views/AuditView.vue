<script setup lang="ts">
import { beijingDateTime, beijingInputToIso } from '@/utils/dateTime'

import { vSingleSelect } from '@/directives/singleSelect'
import AuditEventDialog from '@/components/AuditEventDialog.vue'
import DatePicker from '@/components/DatePicker.vue'
import FilterMultiSelect from '@/components/FilterMultiSelect.vue'
import { actionLabel, categoryLabel, resourceLabel } from '@/utils/auditLabels'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, ref } from 'vue'

import { auditApi, type AuditEvent, type AuditOutcome, type AuditPage, type AuditOptions } from '@/api/audit'
import { ApiError } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const emptyPage = (): AuditPage => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const result = ref<AuditPage>(emptyPage())
const selectedEvent = ref<AuditEvent | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const actorLoginName = ref<string[]>([])
const category = ref<string[]>([])
const action = ref<string[]>([])
const resourceType = ref<string[]>([])
const outcome = ref<AuditOutcome | ''>('')
const from = ref('')
const to = ref('')
const canReadAll = computed(() => session.canAccess(['audit.read.all']))

const options = ref<AuditOptions>({ actors: [], categories: [], actions: [], resources: [] })
const optionError = ref('')
const actorOptions = computed(() => options.value.actors.map(o => ({ value: o.value, label: o.label && o.label !== o.value ? `${o.label}（${o.value}）` : o.value })))
function named(values: string[], label: (code: string) => string) { return values.map(value => ({ value, label: label(value).startsWith('其他') ? `${label(value)}（${value}）` : label(value) })) }
async function loadOptions() {
  optionError.value = ''
  try { options.value = await auditApi.options() }
  catch { optionError.value = '筛选选项加载失败，请重试。' }
}

function asInstant(value: string) {
  return value ? beijingInputToIso(value) : undefined
}

async function load(page = 0) {
  if (runtimeConfig.authMode === 'mock') return
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await auditApi.events({
      actorLoginName: canReadAll.value ? actorLoginName.value : undefined,
      category: category.value,
      action: action.value,
      resourceType: resourceType.value,
      outcome: outcome.value,
      from: asInstant(from.value),
      to: asInstant(to.value),
      page,
      size: result.value.size,
    })
  }
  catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '审计记录加载失败，请稍后重试。'
  }
  finally {
    loading.value = false
  }
}

function resetFilters() {
  actorLoginName.value = []
  category.value = []
  action.value = []
  resourceType.value = []
  outcome.value = ''
  from.value = ''
  to.value = ''
  void load(0)
}

function formatTime(value: string) {
  return beijingDateTime(value)
}

onMounted(() => { void load(); if (runtimeConfig.authMode !== 'mock') void loadOptions() })
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack">
    <div v-if="runtimeConfig.authMode === 'mock'" class="notice">
      <strong>SYNTHETIC</strong><span>模拟模式不生成正式审计记录；请在 password 模式下查看持久化审计。</span>
    </div>
    <template v-else>
      <form class="panel audit-filters" @submit.prevent="load(0)">
        <label v-if="canReadAll">操作账号<FilterMultiSelect v-model="actorLoginName" :options="actorOptions" label="操作账号" /></label>
        <label>类别<FilterMultiSelect v-model="category" :options="named(options.categories, categoryLabel)" label="类别" /></label>
        <label>动作<FilterMultiSelect v-model="action" :options="named(options.actions, actionLabel)" label="动作" /></label>
        <label>资源<FilterMultiSelect v-model="resourceType" :options="named(options.resources, resourceLabel)" label="资源" /></label>
        <label>结果
          <select v-model="outcome" v-single-select>
            <option value="">全部</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILURE">失败</option>
          </select>
        </label>
        <label class="date-range-filter">时间范围<DatePicker v-model="from" v-model:end="to" range type="datetime-local" start-label="开始时间" end-label="结束时间" /></label>
        <div class="audit-filter-actions">
          <button :disabled="loading" type="submit">查询</button>
          <button class="filter-reset-button" type="button" @click="resetFilters">清空</button>
        </div>
      </form>

      <p v-if="optionError" class="data-state data-state--error" role="alert">{{ optionError }} <button type="button" @click="loadOptions">重试</button></p>
      <p v-if="!canReadAll" class="data-state">当前角色仅可查看本人操作记录。</p>
      <p v-if="errorMessage" class="data-state data-state--error" role="alert">{{ errorMessage }}</p>
      <div class="panel table-panel">
        <h2 class="table-title">审计记录（{{ result.totalElements }}）</h2>
        <p v-if="loading" class="data-state">正在加载…</p>
        <p v-else-if="!result.items.length" class="data-state">没有符合条件的审计记录。</p>
        <div v-else class="table-scroll">
          <TableViewport>
            <table class="data-table audit-table">
              <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>资源</th><th>结果</th><th>详情</th></tr></thead>
              <tbody>
                <tr v-for="event in result.items" :key="event.id">
                  <td>{{ formatTime(event.occurredAt) }}</td>
                  <td>{{ event.actorDisplayName || event.actorLoginName }}<small>{{ event.actorLoginName }}</small></td>
                  <td><span class="audit-category" :title="event.category">{{ categoryLabel(event.category) }}</span><span :title="event.action">{{ actionLabel(event.action) }}</span></td>
                  <td><span :title="event.resourceType">{{ resourceLabel(event.resourceType) }}</span><small>{{ event.resourceId || '—' }}</small></td>
                  <td><span :class="event.outcome === 'SUCCESS' ? 'status-success' : 'status-failure'">{{ event.outcome === 'SUCCESS' ? '成功' : '失败' }}</span></td>
                  <td>
                    <button type="button" class="audit-view-button" :aria-label="`查看${actionLabel(event.action)}详情`" @click="selectedEvent = event">查看</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <ListPagination v-model:page-size="result.size" :page="result.page + 1" :total="result.totalElements" :disabled="loading" @update:page="load($event - 1)" />
      </div>
      <AuditEventDialog v-if="selectedEvent" :event="selectedEvent" @close="selectedEvent = null" />
    </template>
  </section>
</template>

<style scoped>
.audit-view-button { border:0; border-radius:4px; padding:4px 8px; min-height:28px; background:transparent; color:#2864c5; font-size:13px; cursor:pointer; white-space:nowrap; }
.audit-view-button:hover { background:#edf4ff; }
.audit-view-button:focus-visible { outline:2px solid #2864c5; outline-offset:1px; }
</style>
