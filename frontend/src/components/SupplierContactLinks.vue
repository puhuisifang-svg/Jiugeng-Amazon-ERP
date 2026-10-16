<script setup lang="ts">
import { computed } from 'vue'
import type { SupplierDto } from '@/api/domain'

const props = defineProps<{ supplier?: SupplierDto | undefined; purchaseUrl?: string | undefined }>()
const purchaseLink = computed(() => {
  const value = props.purchaseUrl || props.supplier?.sourceUrl
  if (!value) return undefined
  try { return ['https:', 'http:'].includes(new URL(value).protocol) ? value : undefined } catch { return undefined }
})
</script>

<template>
  <span>
    <a
      v-if="purchaseLink"
      :href="purchaseLink"
      target="_blank"
      rel="noopener noreferrer"
    >采购链接</a>
    <span v-else>未填写采购链接</span>
  </span>
  <span>
    <a
      v-if="supplier?.phone"
      :href="`tel:${supplier.phone}`"
    >{{ supplier.phone }}</a>
    <span v-else>未填写手机号</span>
  </span>
</template>
