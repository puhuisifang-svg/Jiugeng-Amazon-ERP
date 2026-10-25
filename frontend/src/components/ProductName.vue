<script setup lang="ts">
import { computed } from 'vue'
import { nameRuns, type NameStyleSpan } from '@/utils/productName'
const props = defineProps<{ text: string; styles?: NameStyleSpan[] | null | undefined; highlighted?: boolean | undefined }>()
const runs = computed(() => nameRuns(props.text, props.styles, props.highlighted))
</script>
<template>
  <span class="product-display-name"><span
    v-for="run in runs"
    :key="run.start"
    :style="{ color: run.color === 'RED' ? '#c62828' : '#172033', fontWeight: run.bold ? '700' : '400' }"
  >{{ run.text }}</span></span>
</template>
