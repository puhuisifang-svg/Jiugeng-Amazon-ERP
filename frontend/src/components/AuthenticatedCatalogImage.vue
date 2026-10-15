<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { catalogApi } from '@/api/catalog'

const props = withDefaults(defineProps<{ imageId: string | undefined; variant?: 'thumbnail' | 'original'; alt: string }>(), { variant: 'thumbnail' })
const source = ref(''); const failed = ref(false)
function revoke() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
watch(() => [props.imageId, props.variant] as const, async ([id, variant], _old, onCleanup) => {
  revoke(); failed.value = false
  if (!id) return
  let active = true; onCleanup(() => { active = false })
  try { const blob = await catalogApi.imageBlob(id, variant); if (active) source.value = URL.createObjectURL(blob) }
  catch { if (active) failed.value = true }
}, { immediate: true })
onBeforeUnmount(revoke)
</script>

<template>
  <span
    v-if="!imageId || failed"
    class="catalog-image catalog-image--placeholder"
    role="img"
    :aria-label="`${alt}（无可用图片）`"
  >图</span>
  <img
    v-else-if="source"
    class="catalog-image"
    :src="source"
    :alt="alt"
    loading="lazy"
  >
  <span
    v-else
    class="catalog-image catalog-image--loading"
    role="status"
    aria-label="图片加载中"
  >…</span>
</template>
