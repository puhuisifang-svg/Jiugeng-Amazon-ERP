<script setup lang="ts">
import type {SupplierImpact} from '@/api/supplierManagement'
defineProps<{impact:SupplierImpact}>()
const labels={PRODUCT:'产品子体',MATERIAL:'辅料',PURCHASE:'采购单'}
</script>
<template>
  <div class="supplier-impact-summary">
    <span
      v-for="(count,type) in impact.counts"
      :key="type"
    >{{ labels[type] }} {{ count }} 项</span><span v-if="impact.restrictedCount">另有 {{ impact.restrictedCount }} 项受限关联</span><p v-if="impact.drafts">
      有 {{ impact.drafts }} 张采购草稿，停用或取消对应分类后可能无法提交。
    </p><p v-if="impact.pendingReceipts">
      另有 {{ impact.pendingReceipts }} 张待收货采购单；停用不撤销既有单据及收货，新增或增加采购须重新核对供货资格。
    </p><p>历史记录与既有关联保留；后续新增关联按供应商状态及分类校验。</p>
  </div>
</template>
