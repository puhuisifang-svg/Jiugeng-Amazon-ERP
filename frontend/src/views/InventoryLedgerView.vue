<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { domainApi, type LedgerEntryDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const entries = ref<LedgerEntryDto[]>([]); const loading = ref(true); const busy = ref(''); const error = ref(''); const filter = ref('')
const mockMode = runtimeConfig.authMode === 'mock'
const writable = computed(() => !mockMode && session.canAccess(['inventory.write']))
const filtered = computed(() => entries.value.filter(item => !filter.value || item.materialCode.toLowerCase().includes(filter.value.toLowerCase())))
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
          ERP-013 · append-only
        </p><h1>库存流水</h1><p>流水不可编辑、不可删除；纠错通过引用原流水的冲销完成。</p>
      </div><RouterLink
        class="primary-link"
        to="/inventory/balances"
      >
        返回库存余额
      </RouterLink>
    </div>
    <div class="panel inline-actions">
      <label>筛选辅料编码 <input
        v-model="filter"
        placeholder="输入编码"
      ></label>
    </div>
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
      <table class="data-table">
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
    </article>
  </section>
</template>
