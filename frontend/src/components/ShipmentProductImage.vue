<script setup lang="ts">
import ImagePreviewDialog from './ImagePreviewDialog.vue'
import { computed, ref, watch } from 'vue'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'

const props = defineProps<{ productId: string; variantId?: string | null | undefined; product?: ProductDetailDto | undefined; alt: string }>()
const loaded = ref<ProductDetailDto>()
const preview = ref(false)
watch(() => [props.productId, props.product] as const, async ([id, product], _old, onCleanup) => {
  loaded.value = undefined
  preview.value = false
  if (!id || product) return
  let active = true
  onCleanup(() => { active = false })
  try { const value = await domainApi.product(id); if (active) loaded.value = value } catch { /* Show missing image state. */ }
}, { immediate: true })
watch(() => props.variantId, () => { preview.value = false })
const variant = computed(() => props.variantId ? (props.product ?? loaded.value)?.variants?.find(item => item.id === props.variantId) : undefined)
</script>

<template>
  <button
    v-if="variant?.imageAssetId || variant?.imageUrl"
    type="button"
    class="image-button"
    :aria-label="`预览产品图片：${alt}`"
    @click="preview = true"
  >
    <AuthenticatedCatalogImage
      v-if="variant.imageAssetId"
      :image-id="variant.imageAssetId"
      :alt="alt"
    />
    <AuthenticatedCatalogImage
      v-else
      class="catalog-image"
      :image-url="variant.imageUrl"
      :alt="alt"
    />
  </button>
  <span
    v-else
    role="img"
    :aria-label="`${alt}（未设置产品图片）`"
  >{{ variantId ? '未设置产品图片' : '待补选 SKU' }}</span>
  <ImagePreviewDialog
    v-if="preview"
    title="产品图片预览"
    :description="alt"
    @close="preview = false"
  >
    <AuthenticatedCatalogImage
      v-if="variant?.imageAssetId"
      :image-id="variant.imageAssetId"
      variant="original"
      :alt="alt"
    />
    <AuthenticatedCatalogImage
      v-else
      variant="original"
      :image-url="variant?.imageUrl"
      :alt="alt"
    />
  </ImagePreviewDialog>
</template>
