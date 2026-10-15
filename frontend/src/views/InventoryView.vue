<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { domainApi, type InventoryBalanceViewDto, type InventoryCommandType, type MaterialDetailDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const balances = ref<InventoryBalanceViewDto[]>([])
const materials = ref<MaterialDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const materialId = ref(''); const materialCode = ref(''); const type = ref<InventoryCommandType | 'OPENING' | 'ADJUSTMENT'>('INBOUND')
const quantity = ref(0); const reason = ref('日常库存操作'); const safetyValues = ref<Record<string, number>>({})
const mockMode = runtimeConfig.authMode === 'mock'
const writable = computed(() => mockMode || session.canAccess(['inventory.write']))
const selectedMaterial = computed(() => materials.value.find(item => item.id === materialId.value))

function newRequestId() { return `web-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() {
  loading.value = true; error.value = ''
  try {
    const [balanceResponse, materialResponse] = await Promise.all([domainApi.balances(), domainApi.materials()])
    balances.value = balanceResponse.items
    materials.value = materialResponse.items.filter(item => item.status === 'ACTIVE')
    safetyValues.value = Object.fromEntries(balances.value.filter(item => item.materialId).map(item => [item.materialId as string, item.safetyStock ?? 0]))
  }
  catch { error.value = '库存数据加载失败。' }
  finally { loading.value = false }
}
async function submit() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.transact(mockMode
      ? { requestId: newRequestId(), materialCode: materialCode.value.trim(), type: type.value, quantity: quantity.value, reason: reason.value.trim() }
      : { requestId: newRequestId(), materialId: materialId.value, type: type.value, quantity: quantity.value,
          unit: selectedMaterial.value?.unit ?? 'PCS', reason: reason.value.trim() })
    const responseBalance: InventoryBalanceViewDto = response.balance
    const key = responseBalance.materialId ?? responseBalance.materialCode
    const index = balances.value.findIndex(item => (item.materialId ?? item.materialCode) === key)
    if (index >= 0) balances.value[index] = responseBalance; else balances.value.unshift(responseBalance)
    success.value = response.replayed ? '请求已幂等复用。' : `${mockMode ? '模拟' : '正式'}库存流水已提交。`
  }
  catch { error.value = '提交失败：请检查数量、启用状态和负库存约束。' }
  finally { saving.value = false }
}
async function saveSafety(item: InventoryBalanceViewDto) {
  if (!item.materialId) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const updated = await domainApi.updateSafetyStock(item.materialId, safetyValues.value[item.materialId] ?? 0)
    const index = balances.value.findIndex(value => value.materialId === item.materialId)
    if (index >= 0) balances.value[index] = updated
    success.value = '安全库存已更新。'
  }
  catch { error.value = '安全库存更新失败。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="mockMode" />
    <div class="page-heading">
      <div>
        <p class="eyebrow">
          库存管理 · 默认仓
        </p><h1>辅料库存</h1><p>余额与不可变流水同事务记账，默认禁止负库存。</p>
      </div>
      <RouterLink
        class="primary-link"
        to="/inventory/ledger"
      >
        查看库存流水
      </RouterLink>
    </div>
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="submit"
    >
      <h2>提交{{ mockMode ? '模拟' : '正式' }}库存命令</h2>
      <label v-if="mockMode">辅料编码<input
        v-model="materialCode"
        required
      ></label>
      <label v-else>辅料<select
        v-model="materialId"
        required
      ><option
        value=""
        disabled
      >请选择</option><option
        v-for="item in materials"
        :key="item.id"
        :value="item.id"
      >{{ item.materialCode }} · {{ item.name }}</option></select></label>
      <label>类型<select v-model="type"><template v-if="mockMode"><option>OPENING</option><option>INBOUND</option><option>OUTBOUND</option><option>ADJUSTMENT</option></template><template v-else><option>INBOUND</option><option>OUTBOUND</option><option>ADJUST_IN</option><option>ADJUST_OUT</option></template></select></label>
      <label>数量<input
        v-model.number="quantity"
        required
        min="0.0001"
        type="number"
        step="0.0001"
      ></label>
      <label class="form-wide">原因<input
        v-model="reason"
        required
        maxlength="500"
      ></label>
      <button
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
      v-else
      class="panel table-panel"
    >
      <h2 class="table-title">
        当前余额
      </h2>
      <table class="data-table">
        <thead>
          <tr>
            <th>仓库</th><th>辅料</th><th>名称</th><th>数量</th><th>安全库存</th><th>单位</th><th>状态</th><th v-if="!mockMode && writable">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in balances"
            :key="`${item.warehouseCode}-${item.materialCode}`"
          >
            <td>{{ item.warehouseCode }}</td><td>{{ item.materialCode }}</td><td>{{ item.materialName }}</td><td>{{ item.quantity }}</td>
            <td>
              <input
                v-if="!mockMode && writable && item.materialId"
                v-model.number="safetyValues[item.materialId]"
                min="0"
                type="number"
                step="0.0001"
              ><span v-else>{{ item.safetyStock ?? '—' }}</span>
            </td>
            <td>{{ item.unit }}</td><td :class="item.lowStock ? 'status-failure' : 'status-success'">
              {{ item.lowStock ? '低库存' : '正常' }}
            </td>
            <td v-if="!mockMode && writable">
              <button
                :disabled="saving"
                type="button"
                @click="saveSafety(item)"
              >
                保存阈值
              </button>
            </td>
          </tr>
          <tr v-if="!balances.length">
            <td :colspan="!mockMode && writable ? 8 : 7">
              尚无库存余额；首次正式入库会自动建立余额。
            </td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
