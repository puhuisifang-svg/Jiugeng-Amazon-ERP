<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ total: number; page: number; pageSize: number }>()
const emit = defineEmits<{ 'update:page': [number]; 'update:pageSize': [number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
</script>

<template>
  <nav
    class="pagination list-pagination"
    aria-label="列表分页"
  >
    <span>共 {{ total }} 条</span>
    <label>每页<select
      :value="pageSize"
      aria-label="每页条数"
      @change="emit('update:pageSize', Number(($event.target as HTMLSelectElement).value)); emit('update:page', 1)"
    ><option
      v-for="size in [10, 20, 50, 100]"
      :key="size"
      :value="size"
    >{{ size }} 条</option></select></label>
    <button
      type="button"
      :disabled="page <= 1"
      @click="emit('update:page', page - 1)"
    >
      上一页
    </button>
    <span>{{ page }} / {{ pages }}</span>
    <button
      type="button"
      :disabled="page >= pages"
      @click="emit('update:page', page + 1)"
    >
      下一页
    </button>
  </nav>
</template>
