<script setup lang="ts">
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, ref } from 'vue'

import { auditApi, type AuditEvent, type AuditOutcome, type AuditPage } from '@/api/audit'
import { ApiError } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const emptyPage = (): AuditPage => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const result = ref<AuditPage>(emptyPage())
const loading = ref(false)
const errorMessage = ref('')
const actorLoginName = ref('')
const category = ref('')
const action = ref('')
const resourceType = ref('')
const outcome = ref<AuditOutcome | ''>('')
const from = ref('')
const to = ref('')
const canReadAll = computed(() => session.canAccess(['audit.read.all']))

function asInstant(value: string) {
  return value ? new Date(value).toISOString() : undefined
}

async function load(page = 0) {
  if (runtimeConfig.authMode === 'mock') return
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = await auditApi.events({
      actorLoginName: canReadAll.value ? actorLoginName.value.trim() : undefined,
      category: category.value || undefined,
      action: action.value.trim().toUpperCase() || undefined,
      resourceType: resourceType.value || undefined,
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
  actorLoginName.value = ''
  category.value = ''
  action.value = ''
  resourceType.value = ''
  outcome.value = ''
  from.value = ''
  to.value = ''
  void load(0)
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))
}

function snapshot(value: AuditEvent['beforeValue']) {
  return value ? JSON.stringify(value, null, 2) : '无'
}

onMounted(() => load())
</script>

<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="page-stack">
    <div v-if="runtimeConfig.authMode === 'mock'" class="notice">
      <strong>SYNTHETIC</strong><span>模拟模式不生成正式审计记录；请在 password 模式下查看持久化审计。</span>
    </div>
    <template v-else>
      <form class="panel audit-filters" @submit.prevent="load(0)">
        <h2>审计筛选</h2>
        <label v-if="canReadAll">操作账号<input v-model.trim="actorLoginName" maxlength="128"></label>
        <label>类别
          <select v-model="category">
            <option value="">全部</option>
            <option value="SECURITY">安全</option>
            <option value="ACCOUNT">账号</option>
            <option value="BUSINESS">业务</option>
          </select>
        </label>
        <label>动作<input v-model.trim="action" maxlength="64" placeholder="例如 PASSWORD_RESET"></label>
        <label>资源
          <select v-model="resourceType">
            <option value="">全部</option>
            <option value="ACCOUNT">账号</option>
            <option value="PRODUCT">产品</option>
            <option value="MATERIAL">辅料</option>
            <option value="INVENTORY">库存</option>
            <option value="SELECTION">候选</option>
            <option value="WATCH">监控</option>
            <option value="IMPORT">导入</option>
          </select>
        </label>
        <label>结果
          <select v-model="outcome">
            <option value="">全部</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILURE">失败</option>
          </select>
        </label>
        <label>开始时间<input v-model="from" type="datetime-local"></label>
        <label>结束时间<input v-model="to" type="datetime-local"></label>
        <div class="audit-filter-actions">
          <button :disabled="loading" type="submit">查询</button>
          <button class="secondary-button" type="button" @click="resetFilters">清空</button>
        </div>
      </form>

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
                  <td><span class="audit-category">{{ event.category }}</span>{{ event.action }}</td>
                  <td>{{ event.resourceType }}<small>{{ event.resourceId || '—' }}</small></td>
                  <td><span :class="event.outcome === 'SUCCESS' ? 'status-success' : 'status-failure'">{{ event.outcome === 'SUCCESS' ? '成功' : '失败' }}</span></td>
                  <td>
                    <details>
                      <summary>查看</summary>
                      <p v-if="event.detail">{{ event.detail }}</p>
                      <strong>变更前</strong><pre>{{ snapshot(event.beforeValue) }}</pre>
                      <strong>变更后</strong><pre>{{ snapshot(event.afterValue) }}</pre>
                    </details>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <div v-if="result.totalPages > 1" class="pagination">
          <button :disabled="loading || result.page === 0" type="button" @click="load(result.page - 1)">上一页</button>
          <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页</span>
          <button :disabled="loading || result.page + 1 >= result.totalPages" type="button" @click="load(result.page + 1)">下一页</button>
        </div>
      </div>
    </template>
  </section>
</template>
