<script setup lang="ts">
import { ref } from 'vue'
import ImagePreviewDialog from './ImagePreviewDialog.vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
defineProps<{ imageAssetId?: string | null | undefined; imageUrl?: string | null | undefined; alt: string }>()
const preview = ref(false)
</script>
<template>
  <button
    v-if="imageAssetId || imageUrl"
    type="button"
    class="image-button"
    :aria-label="`查看${alt}图片`"
    @click="preview = true"
  >
    <AuthenticatedCatalogImage
      v-if="imageAssetId"
      :image-id="imageAssetId"
      :alt="alt"
    /><img
      v-else
      class="catalog-image"
      :src="imageUrl ?? undefined"
      :alt="alt"
      referrerpolicy="no-referrer"
    >
  </button>
  <span
    v-else
    class="catalog-image catalog-image--placeholder"
    role="img"
    :aria-label="`${alt}暂无图片`"
  >图</span>
  <ImagePreviewDialog
    v-if="preview"
    title="商品图片预览"
    :description="alt"
    @close="preview = false"
  >
    <AuthenticatedCatalogImage
      v-if="imageAssetId"
      :image-id="imageAssetId"
      variant="original"
      :alt="alt"
    />
    <img
      v-else
      :src="imageUrl ?? undefined"
      :alt="alt"
      referrerpolicy="no-referrer"
    >
  </ImagePreviewDialog>
</template>
<style scoped>
.image-button .catalog-image{object-fit:contain}
</style>
