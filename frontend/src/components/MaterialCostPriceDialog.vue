<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { costsApi, type MaterialPrice } from '@/api/costs'
import { session } from '@/state/session'
import '@/styles/productCosts.css'
const props = defineProps<{ material: { id: string; name: string; unit: string } }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const input = ref<MaterialPrice & { confirmedNet: boolean }>({ netPrice: '', unit: props.material.unit, source: 'MANUAL', referenceId: null, reason: '', version: -1, confirmedNet: false })
const references = ref<Awaited<ReturnType<typeof costsApi.purchases>>>([])
const error = ref(''), busy = ref(true), loaded = ref(false)
const writable = session.canAccess(['costs.write', 'materials.write'])
onMounted(async () => { try { const value = await costsApi.price(props.material.id); if (value) input.value = { ...value, unit: props.material.unit, confirmedNet: false }; loaded.value = true; if (session.canAccess(['purchases.read'])) references.value = await costsApi.purchases(props.material.id) } catch (e) { error.value = String(e) } finally { busy.value = false } })
function selectReference() { const selected = references.value.find(r => r.id === input.value.referenceId); if (selected) { input.value.netPrice = selected.price; input.value.confirmedNet = false } }
async function save() { if (busy.value) return; busy.value = true; error.value = ''; try { await costsApi.savePrice(props.material.id, input.value); emit('saved') } catch (e) { error.value = e instanceof Error ? e.message : '保存失败' } finally { busy.value = false } }
</script>
<template>
  <div
    class="operation-dialog cost-ui cost-price-dialog"
    role="dialog"
    aria-modal="true"
    aria-label="辅料成本价格"
  >
    <form
      class="cost-sheet cost-sheet--compact"
      @submit.prevent="save"
    >
      <header class="cost-sheet__header">
        <div>
          <p class="cost-eyebrow">
            辅料标准成本
          </p><h2>{{ material.name }} · 不含税成本价</h2>
        </div>
      </header>
      <div class="cost-sheet__body cost-compact-body cost-price-fields">
        <p class="cost-meta">
          仅用于标准成本核算，不修改采购单、库存金额或已生效成本。
        </p>
        <label>来源<select
          v-model="input.source"
          :disabled="!writable || busy"
        ><option value="MANUAL">手工核定</option><option
          v-if="session.canAccess(['purchases.read'])"
          value="PURCHASE"
        >引用有效采购单</option></select></label>
        <label v-if="input.source === 'PURCHASE'">采购明细<select
          v-model="input.referenceId"
          required
          :disabled="!writable || busy"
          @change="selectReference"
        ><option :value="null">请选择</option><option
          v-for="r in references"
          :key="r.id"
          :value="r.id"
        >{{ r.orderNumber }} · ¥{{ r.price }}/{{ r.unit }}</option></select></label>
        <label>人民币 / {{ material.unit }}（不含税）<input
          v-model="input.netPrice"
          required
          type="number"
          min="0"
          step="0.000001"
          :disabled="!writable || busy || input.source === 'PURCHASE'"
        ></label>
        <label>核定原因<input
          v-model="input.reason"
          required
          maxlength="500"
          :disabled="!writable || busy"
        ></label>
        <label class="cost-price-confirm"><input
          v-model="input.confirmedNet"
          type="checkbox"
          required
          :disabled="!writable || busy"
        >确认价格为人民币不含税单价（采购引用不自动去税）</label>
        <p
          v-if="error"
          role="alert"
        >
          {{ error }}
        </p>
      </div>
      <footer class="cost-sheet__footer">
        <button
          class="cost-button"
          type="button"
          :disabled="busy"
          @click="emit('close')"
        >
          关闭
        </button><button
          v-if="writable"
          class="cost-button cost-button--primary"
          type="submit"
          :disabled="busy || !loaded"
        >
          保存成本价
        </button>
      </footer>
    </form>
  </div>
</template>
