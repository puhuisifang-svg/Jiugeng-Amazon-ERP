<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ screenshotUploaded?: boolean | undefined; invoiceUploaded?: boolean | undefined; loading?: boolean; stacked?: boolean }>()
const states = computed(() => [
  { label: '申报订单截图', uploaded: props.screenshotUploaded },
  { label: '发票', uploaded: props.invoiceUploaded },
])
</script>
<template>
  <div
    class="customs-file-status"
    :class="{ stacked }"
    aria-label="文件上传状态"
  >
    <span
      v-for="item in states"
      :key="item.label"
      class="customs-file-badge"
      :class="loading || item.uploaded === undefined ? 'unknown' : item.uploaded ? 'uploaded' : 'pending'"
    >
      <span>{{ item.label }}</span>
      <span
        v-if="loading || item.uploaded === undefined"
        class="badge-state"
      >{{ loading ? '加载中' : '待确认' }}</span>
      <span
        v-else
        class="badge-state"
      ><span aria-hidden="true">{{ item.uploaded ? '✓' : '◷' }}</span> {{ item.uploaded ? '已上传' : '待上传' }}</span>
    </span>
  </div>
</template>
<style scoped>
.customs-file-status { display:flex; flex-wrap:wrap; gap:5px 8px; }
.customs-file-status.stacked { flex-direction:column; align-items:flex-start; gap:4px; }
.customs-file-badge { display:inline-flex; align-items:center; gap:8px; padding:3px 7px; border:1px solid transparent; border-radius:4px; font-size:12px; line-height:18px; white-space:nowrap; }
.customs-file-badge.uploaded { color:#21834a; background:#edf7f0; border-color:#d6ebdc; }
.customs-file-badge.pending { color:#986b16; background:#fff7e6; border-color:#f3e3b8; }
.customs-file-badge.unknown { color:var(--muted); background:#f3f5f7; border-color:#e5e9ee; }
.badge-state { font-weight:500; }
</style>
