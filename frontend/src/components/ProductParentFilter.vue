<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { domainApi } from '@/api/domain'
import { vSingleSelect } from '@/directives/singleSelect'
defineProps<{modelValue:string;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:string]}>()
const rows=ref<{id:string;name:string}[]>([]),error=ref(''),loading=ref(false)
async function load(){loading.value=true;error.value='';try{const result=await domainApi.products();rows.value=result.items.map(p=>({id:p.id,name:p.productName}))}catch{error.value='父体加载失败'}finally{loading.value=false}}
onMounted(load)
</script>
<template>
  <label class="product-parent-filter">所属父体<select
    v-single-select
    :value="modelValue"
    aria-label="所属父体筛选"
    :disabled="disabled||loading"
    @change="emit('update:modelValue',($event.target as HTMLSelectElement).value)"
  ><option value="">全部父体</option><option
    v-for="row in rows"
    :key="row.id"
    :value="row.id"
  >{{ row.name }}</option></select><button
    v-if="error"
    type="button"
    @click="load"
  >{{ error }}，重试</button></label>
</template>
<style scoped>
.product-parent-filter{display:flex;align-items:center;gap:8px;min-width:0;font-size:13px;white-space:nowrap}.product-parent-filter :deep(.single-select-trigger){width:220px;max-width:50vw;height:32px;min-height:32px}.product-parent-filter button{font-size:12px;color:#b42318}
</style>
