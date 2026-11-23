<script setup lang="ts">
import SupplierDetailDialog from './SupplierDetailDialog.vue'
defineProps<{id:string}>()
defineEmits<{close:[]}>()
</script>
<template>
  <SupplierDetailDialog
    :id="id"
    @close="$emit('close')"
  />
</template>
