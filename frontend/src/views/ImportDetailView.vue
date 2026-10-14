<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { importsApi, type ImportBatch, type ImportIssue, type ImportPreview, type ImportResult } from '@/api/imports'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute(); const router = useRouter(); const id = computed(() => String(route.params.batchId))
const batch = ref<ImportBatch | null>(null); const issues = ref<ImportIssue[]>([]); const preview = ref<ImportPreview | null>(null)
const result = ref<ImportResult | null>(null); const warningsAccepted = ref(false)
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const formal = runtimeConfig.authMode === 'password'
const canCancel = computed(() => session.canAccess(['imports.cancel']) && batch.value && ['QUEUED', 'VALIDATING', 'VALIDATED'].includes(batch.value.status))
const expired = computed(() => batch.value?.status === 'VALIDATED' && Boolean(batch.value.previewExpiresAt) && new Date(batch.value!.previewExpiresAt!).getTime() <= Date.now())

async function load() {
  if (!formal) return
  loading.value = true; error.value = ''
  try {
    batch.value = await importsApi.get(id.value)
    issues.value = await importsApi.issues(id.value)
    preview.value = batch.value.status === 'VALIDATED' && !expired.value ? await importsApi.preview(id.value) : null
    result.value = ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(batch.value.status) ? await importsApi.result(id.value) : null
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '批次详情加载失败。' }
  finally { loading.value = false }
}
async function commit() {
  if (!batch.value?.previewVersion) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    batch.value = await importsApi.commit(id.value, batch.value.previewVersion, batch.value.fileHash, warningsAccepted.value)
    preview.value = null; result.value = await importsApi.result(id.value); success.value = '批次提交完成。'
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '提交失败。' }
  finally { saving.value = false }
}
async function cancel() {
  saving.value = true; error.value = ''
  try { batch.value = await importsApi.cancel(id.value); success.value = '批次已取消。'; preview.value = null }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '取消失败。' }
  finally { saving.value = false }
}
async function revalidate() {
  saving.value = true; error.value = ''
  try { const receipt = await importsApi.revalidate(id.value); await router.push(`/imports/${receipt.batch.id}`) }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '重新校验失败。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <p class="eyebrow">
          ERP-015 · 产品与 SKU 导入
        </p><h1>批次详情</h1><p>逐工作表、行和字段定位问题；产品与 SKU 批次通过全量校验后原子提交。</p>
      </div><RouterLink
        class="primary-link"
        to="/imports"
      >
        返回导入中心
      </RouterLink>
    </div>
    <article
      v-if="!formal"
      class="panel empty-state"
    >
      <h2>模拟模式不提供批次详情</h2><p>此页面只呈现正式导入运行时。</p>
    </article>
    <p
      v-else-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <template v-else-if="batch">
      <article class="panel import-summary">
        <h2>{{ batch.importType }}</h2><p>状态：<strong>{{ batch.status }}</strong> · 阶段：{{ batch.phase }} · {{ batch.percent }}%</p><p>数据 {{ batch.totalRows }} 行；错误 {{ batch.errorCount }}；警告 {{ batch.warningCount }}</p>
        <div class="inline-actions">
          <button
            v-if="canCancel"
            :disabled="saving"
            type="button"
            @click="cancel"
          >
            取消批次
          </button><button
            v-if="expired && session.canAccess(['imports.validate'])"
            :disabled="saving"
            type="button"
            @click="revalidate"
          >
            重新校验
          </button>
          <label v-if="batch.status === 'VALIDATED' && batch.warningCount > 0"><input
            v-model="warningsAccepted"
            type="checkbox"
          >我已确认警告</label>
          <button
            v-if="batch.status === 'VALIDATED' && batch.commitAllowed && session.canAccess(['imports.commit'])"
            :disabled="saving || (batch.warningCount > 0 && !warningsAccepted)"
            type="button"
            @click="commit"
          >
            提交导入
          </button>
        </div>
      </article>
      <article
        v-if="result"
        class="panel import-summary"
      >
        <h2>提交结果</h2><p>新增 {{ result.createdRows }} · 更新 {{ result.updatedRows }} · 未变化 {{ result.unchangedRows }} · 实际写入 {{ result.writtenRows }}</p>
      </article>
      <p
        v-if="success"
        class="data-state data-state--success"
        role="status"
      >
        {{ success }}
      </p>
      <article class="panel table-panel">
        <h2 class="table-title">
          校验问题
        </h2><table class="data-table">
          <thead><tr><th>级别</th><th>位置</th><th>代码</th><th>原因</th><th>建议</th></tr></thead><tbody>
            <tr
              v-for="item in issues"
              :key="item.id"
            >
              <td>{{ item.severity }}</td><td>{{ item.sheet || '全局' }}{{ item.row ? ` / ${item.row}` : '' }}{{ item.field ? ` / ${item.field}` : '' }}</td><td>{{ item.code }}</td><td>{{ item.reason }}</td><td>{{ item.suggestion || '—' }}</td>
            </tr><tr v-if="!issues.length">
              <td colspan="5">
                没有校验问题。
              </td>
            </tr>
          </tbody>
        </table>
      </article>
      <article
        v-if="preview"
        class="panel table-panel"
      >
        <h2 class="table-title">
          预览（{{ preview.summary.inputRows ?? preview.rows.length }} 行）
        </h2><table class="data-table">
          <thead><tr><th>工作表</th><th>行</th><th>业务键</th><th>数据</th></tr></thead><tbody>
            <tr
              v-for="item in preview.rows"
              :key="item.id"
            >
              <td>{{ item.sheet }}</td><td>{{ item.row }}</td><td>{{ item.businessKey }}</td><td>{{ JSON.stringify(item.data) }}</td>
            </tr><tr v-if="!preview.rows.length">
              <td colspan="4">
                模板中没有数据行。
              </td>
            </tr>
          </tbody>
        </table>
      </article>
    </template>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
  </section>
</template>
