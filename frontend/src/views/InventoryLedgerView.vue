<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { domainApi, type LedgerEntryDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const entries = ref<LedgerEntryDto[]>([]); const loading = ref(true); const busy = ref(''); const error = ref('')
const reversalTarget = ref<LedgerEntryDto | null>(null); const reversalReason = ref(''); const reversalError = ref('')
const filters = reactive({ keyword: '', type: '' })
const mockMode = runtimeConfig.authMode === 'mock'
const writable = computed(() => !mockMode && session.canAccess(['inventory.write']))
const transactionTypes = computed(() => [...new Set(entries.value.map(item => item.type))].sort())
const filtered = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return entries.value.filter(item => {
    const keywordMatches = !keyword || [item.materialCode, item.materialName, item.reason, item.actorLoginName, item.referenceTransactionId]
      .some(value => value?.toLowerCase().includes(keyword))
    return keywordMatches && (!filters.type || item.type === filters.type)
  })
})
function resetFilters() { Object.assign(filters, { keyword: '', type: '' }) }
function newRequestId() { return `reverse-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() { loading.value = true; error.value = ''; try { entries.value = (await domainApi.ledger()).items } catch { error.value = '库存流水加载失败。' } finally { loading.value = false } }
function openReversal(item: LedgerEntryDto) { reversalTarget.value = item; reversalReason.value = ''; reversalError.value = '' }
function closeReversal() { if (busy.value) return; reversalTarget.value = null; reversalReason.value = ''; reversalError.value = '' }
async function confirmReversal() {
  const item = reversalTarget.value; const reason = reversalReason.value.trim()
  if (!item || !item.materialId || !item.unit) return
  if (!reason) { reversalError.value = '请输入冲销原因。'; return }
  busy.value = item.id; error.value = ''
  try {
    await domainApi.transact({ requestId: newRequestId(), materialId: item.materialId, type: 'REVERSAL', unit: item.unit,
      reason, referenceTransactionId: item.id })
    reversalTarget.value = null; reversalReason.value = ''; reversalError.value = ''
    await load()
  }
  catch { reversalError.value = '冲销失败：流水可能已冲销，或冲销后将产生负库存。' }
  finally { busy.value = '' }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="mockMode" />
    <div class="page-heading ledger-page-heading">
      <div class="ledger-page-heading__main">
        <RouterLink
          class="ledger-back-link"
          to="/inventory/balances"
        >
          ← 返回库存余额
        </RouterLink><div>
          <p class="eyebrow">
            库存审计 · 只增不改
          </p><h1>库存流水</h1><p>流水不可编辑、不可删除；纠错通过引用原流水的冲销完成。</p>
        </div>
      </div>
    </div>
    <form
      class="panel filter-panel ledger-filters"
      aria-label="库存流水筛选"
      @submit.prevent
    >
      <label>关键词<input
        v-model="filters.keyword"
        placeholder="辅料编码、名称、原因或操作人"
      ></label>
      <label>流水类型<select v-model="filters.type"><option value="">全部类型</option><option
        v-for="item in transactionTypes"
        :key="item"
        :value="item"
      >{{ item }}</option></select></label>
      <button
        type="button"
        @click="resetFilters"
      >
        重置
      </button>
    </form>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-else
      class="panel table-panel"
    >
      <div class="table-scroll">
        <table class="data-table inventory-ledger-table">
          <thead>
            <tr>
              <th>时间</th><th>辅料</th><th>类型</th><th>变动</th><th>结存</th><th>原因</th><th>操作人</th><th v-if="writable">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="item in filtered"
              :key="item.id"
            >
              <td>{{ item.occurredAt }}</td><td>{{ item.materialCode }}</td><td>{{ item.type }}</td><td>{{ item.signedQuantity ?? item.quantity }}</td><td>{{ item.balanceAfter ?? '—' }}</td><td>{{ item.reason }}</td><td>{{ item.actorLoginName ?? '—' }}</td><td v-if="writable">
                <button
                  v-if="item.type !== 'REVERSAL'"
                  :disabled="busy === item.id"
                  type="button"
                  @click="openReversal(item)"
                >
                  冲销
                </button>
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td :colspan="writable ? 8 : 7">
                暂无库存流水。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
    <div
      v-if="reversalTarget"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reversal-dialog-title"
      @click.self="closeReversal"
      @keydown.esc="closeReversal"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="confirmReversal"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭冲销确认"
          :disabled="Boolean(busy)"
          @click="closeReversal"
        >
          ×
        </button><h2 id="reversal-dialog-title">
          确认冲销库存流水
        </h2><p class="form-wide reversal-summary">
          将冲销 {{ reversalTarget.materialCode }} 的 {{ reversalTarget.type }} 流水（{{ reversalTarget.id }}）。该操作会新增一条反向流水，原流水不会被删除。
        </p><label class="form-wide">冲销原因<textarea
          v-model="reversalReason"
          required
          maxlength="500"
          placeholder="请输入本次冲销的具体原因"
        /></label><p
          v-if="reversalError"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ reversalError }}
        </p><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="Boolean(busy)"
            @click="closeReversal"
          >
            取消
          </button><button
            type="submit"
            class="danger-button"
            :disabled="Boolean(busy) || !reversalReason.trim()"
          >
            {{ busy ? '冲销中…' : '确认冲销' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
