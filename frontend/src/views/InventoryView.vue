<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { domainApi, type InventoryTransactionType, type LedgerEntryDto } from '@/api/domain'
import type { InventoryBalanceDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'

const balances = ref<InventoryBalanceDto[]>([]); const ledger = ref<LedgerEntryDto[]>([]); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const materialCode = ref(''); const type = ref<InventoryTransactionType>('OPENING'); const quantity = ref(0); const reason = ref('本地模拟操作')
function newRequestId() { return `web-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() { loading.value = true; error.value = ''; try { const [balanceResponse, ledgerResponse] = await Promise.all([domainApi.balances(), domainApi.ledger()]); balances.value = balanceResponse.items; ledger.value = ledgerResponse.items } catch { error.value = '库存模拟数据加载失败。' } finally { loading.value = false } }
async function submit() {
  saving.value = true; error.value = ''; success.value = ''
  try { const response = await domainApi.transact({ requestId: newRequestId(), materialCode: materialCode.value.trim(), type: type.value, quantity: quantity.value, reason: reason.value.trim() }); const index = balances.value.findIndex(item => item.materialCode === response.balance.materialCode); if (index >= 0) balances.value[index] = response.balance; else balances.value.unshift(response.balance); ledger.value.unshift(response.item); success.value = response.replayed ? '请求已幂等复用。' : '模拟库存流水已提交，流水不可编辑。' }
  catch { error.value = '提交失败：请检查期初唯一性、数量和负库存约束。' } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <SyntheticNotice />
    <form
      class="panel operation-form"
      @submit.prevent="submit"
    >
      <h2>提交模拟库存流水</h2><label>辅料编码<input
        v-model="materialCode"
        required
      ></label><label>类型<select v-model="type"><option>OPENING</option><option>INBOUND</option><option>OUTBOUND</option><option>ADJUSTMENT</option></select></label><label>数量<input
        v-model.number="quantity"
        required
        type="number"
        step="0.0001"
      ></label><label class="form-wide">原因<input
        v-model="reason"
        required
      ></label><button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '提交中…' : '提交流水' }}
      </button>
    </form>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p><p
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
    <template v-else>
      <article class="panel table-panel">
        <h2 class="table-title">
          当前余额
        </h2><table class="data-table">
          <thead><tr><th>仓库</th><th>辅料</th><th>名称</th><th>数量</th><th>单位</th></tr></thead><tbody>
            <tr
              v-for="item in balances"
              :key="`${item.warehouseCode}-${item.materialCode}`"
            >
              <td>{{ item.warehouseCode }}</td><td>{{ item.materialCode }}</td><td>{{ item.materialName }}</td><td>{{ item.quantity }}</td><td>{{ item.unit }}</td>
            </tr>
          </tbody>
        </table>
      </article>
      <article class="panel table-panel">
        <h2 class="table-title">
          不可变库存流水
        </h2><table class="data-table">
          <thead><tr><th>时间</th><th>辅料</th><th>类型</th><th>数量</th><th>原因</th><th>Request ID</th></tr></thead><tbody>
            <tr
              v-for="item in ledger"
              :key="item.id"
            >
              <td>{{ item.occurredAt }}</td><td>{{ item.materialCode }}</td><td>{{ item.type }}</td><td>{{ item.quantity }}</td><td>{{ item.reason }}</td><td>{{ item.requestId }}</td>
            </tr>
          </tbody>
        </table>
      </article>
    </template>
  </section>
</template>
