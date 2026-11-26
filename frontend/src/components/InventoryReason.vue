<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { LedgerEntryDto } from '@/api/domain'
import { purchaseApi } from '@/api/purchases'
import { session } from '@/state/session'

const props=defineProps<{entry:LedgerEntryDto;disabled?:boolean}>()
const router=useRouter(),busy=ref(false),error=ref('')
const orderLabel=computed(()=>props.entry.requestId?.startsWith('PURCHASE:')?props.entry.reason?.match(/^采购收货\s+(.+)$/)?.[1]:undefined)
const canOpen=computed(()=>!!orderLabel.value&&session.canAccess(['purchases.read','inventory.read']))
async function openOrder(){
  if(busy.value||props.disabled||!canOpen.value)return
  busy.value=true;error.value=''
  try{const order=await purchaseApi.inventoryOrder(props.entry.id);await router.push(`/purchases/orders/${encodeURIComponent(order.orderId)}`)}
  catch(e){error.value=e instanceof Error?e.message:'采购单读取失败，请重试'}
  finally{busy.value=false}
}
</script>
<template>
  <span class="inventory-reason">
    <template v-if="canOpen">
      采购收货 <button
        type="button"
        class="inventory-order-link"
        :disabled="busy||disabled"
        :aria-label="`查看采购单 ${orderLabel}`"
        @click="openOrder"
      >{{ orderLabel }}<span v-if="busy"> · 正在打开</span></button>
    </template>
    <template v-else>{{ entry.reason || '—' }}</template>
    <span
      v-if="error"
      class="inventory-reason-error"
      role="alert"
    >{{ error }}</span>
  </span>
</template>
<style scoped>
.inventory-reason{white-space:pre-wrap;overflow-wrap:anywhere}
.inventory-reason .inventory-order-link{display:inline;padding:2px 0;min-height:28px;border:0;background:transparent;color:#2864c5;font:inherit;text-align:left;white-space:normal;overflow-wrap:anywhere;cursor:pointer}
.inventory-reason .inventory-order-link:disabled{color:#718096;cursor:default}
.inventory-reason-error{display:block;color:#b42318;font-size:12px;margin-top:4px}
</style>
