<script setup lang="ts">
import {ref} from 'vue'
defineProps<{value?:string|undefined;phone?:boolean}>()
const feedback=ref('')
async function copy(value:string){try{await navigator.clipboard.writeText(value);feedback.value='已复制'}catch{feedback.value='复制失败，请选中文字复制'}}
</script>
<template>
  <span class="supplier-contact-value"><a
    v-if="phone&&value"
    :href="`tel:${value}`"
  >{{ value }}</a><span v-else>{{ value||'—' }}</span><button
    v-if="value"
    type="button"
    :aria-label="`复制${value}`"
    @click="copy(value)"
  >复制</button><small
    v-if="feedback"
    role="status"
  >{{ feedback }}</small></span>
</template>
<style scoped>.supplier-contact-value{display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap;overflow-wrap:anywhere}.supplier-contact-value button{border:0;background:transparent;color:#2864c5;min-height:28px;padding:2px 4px;font-size:12px}.supplier-contact-value small{font-size:12px;color:var(--muted)}</style>
