<script setup lang="ts">
import { computed } from 'vue'
import { session } from '@/state/session'
import CustomsWarehouseManager from '@/components/CustomsWarehouseManager.vue'
const writable=computed(()=>session.canAccess(['products.write']))
</script>
<template>
  <CustomsWarehouseManager
    standalone
    :writable="writable"
  />
</template>
