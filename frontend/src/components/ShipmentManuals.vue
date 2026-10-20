<script setup lang="ts">
import { ref, watch } from 'vue'
import { catalogApi, type SkuDocument } from '@/api/catalog'
import type { ProductDetailDto } from '@/api/domain'
const props = defineProps<{ product: ProductDetailDto | undefined; variantId?: string | null | undefined }>()
const files = ref<Array<{ variantId: string; sku: string; document: SkuDocument }>>([])
const error = ref(''); const busy = ref(false); const loading = ref(false)
watch(() => [props.product, props.variantId] as const, async ([product, variantId], _old, cleanup) => {
  files.value = []; error.value = ''; loading.value = false
  if (!product || !variantId) return
  let active = true; cleanup(() => { active = false }); loading.value = true
  try {
    const result = await Promise.all(product.variants.filter(v => v.id === variantId).map(async v => (await catalogApi.documents(product.id, v.id!, 'MANUAL')).map(document => ({ variantId: v.id!, sku: v.skuCode, document }))))
    if (active) files.value = result.flat()
  } catch { if (active) error.value = '说明书加载失败，请刷新重试。' }
  finally { if (active) loading.value = false }
}, { immediate: true })
async function download(file: typeof files.value[number]) {
  if (!props.product) return
  busy.value = true; error.value = ''
  try { await catalogApi.downloadDocument(props.product.id, file.variantId, file.document) }
  catch { error.value = '说明书下载失败，请重试。' }
  finally { busy.value = false }
}
</script>
<template>
  <div class="shipment-files">
    <small v-if="loading">加载中…</small>
    <span
      v-else-if="!files.length && !error"
      class="muted"
    >未上传</span>
    <button
      v-for="file in files"
      :key="file.document.id"
      type="button"
      class="shipment-file-link"
      :disabled="busy"
      @click="download(file)"
    >
      ↓ {{ file.sku }} · {{ file.document.fileName }}
    </button>
    <p
      v-if="error"
      class="shipment-file-error"
      role="alert"
    >
      {{ error }}
    </p>
  </div>
</template>
