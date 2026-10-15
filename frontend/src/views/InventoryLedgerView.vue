<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { domainApi, type LedgerEntryDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const entries = ref<LedgerEntryDto[]>([]); const loading = ref(true); const busy = ref(''); const error = ref('')
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
async function reverse(item: LedgerEntryDto) {
  if (!item.materialId || !item.unit) return
  busy.value = item.id; error.value = ''
  try {
    await domainApi.transact({ requestId: newRequestId(), materialId: item.materialId, type: 'REVERSAL', unit: item.unit,
      reason: `冲销流水 ${item.id}`, referenceTransactionId: item.id })
    await load()
  }
  catch { error.value = '冲销失败：流水可能已冲销，或冲销后将产生负库存。' }
  finally { busy.value = '' }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="mockMode" />
    <div class="page-heading">
      <div>
        <p class="eyebrow">
          库存审计 · 只增不改
        </p><h1>库存流水</h1><p>流水不可编辑、不可删除；纠错通过引用原流水的冲销完成。</p>
      </div><RouterLink
        class="primary-link"
        to="/inventory/balances"
      >
        返回库存余额
      </RouterLink>
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
                  @click="reverse(item)"
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
  </section>
</template>
