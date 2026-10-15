<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { dashboardApi } from '@/api/dashboard'

const props = withDefaults(defineProps<{ imageId: string | undefined; variant?: 'thumbnail' | 'original'; alt: string }>(), {
  variant: 'thumbnail',
})
const source = ref(''); const failed = ref(false)

function revoke() {
  if (source.value) URL.revokeObjectURL(source.value)
  source.value = ''
}
watch(() => [props.imageId, props.variant] as const, async ([imageId, variant], _old, onCleanup) => {
  revoke(); failed.value = false
  if (!imageId) return
  let active = true
  onCleanup(() => { active = false })
  try {
    const blob = await dashboardApi.imageBlob(imageId, variant)
    if (active) source.value = URL.createObjectURL(blob)
  }
  catch { if (active) failed.value = true }
}, { immediate: true })
onBeforeUnmount(revoke)
</script>

<template>
  <span
    v-if="!imageId || failed"
    class="dashboard-image dashboard-image--placeholder"
    role="img"
    :aria-label="`${alt}（无可用图片）`"
  >图</span>
  <img
    v-else-if="source"
    class="dashboard-image"
    :src="source"
    :alt="alt"
    loading="lazy"
  >
  <span
    v-else
    class="dashboard-image dashboard-image--loading"
    role="status"
    aria-label="图片加载中"
  >…</span>
</template>
