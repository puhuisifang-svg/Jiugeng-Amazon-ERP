<script setup lang="ts">
import { ref } from 'vue'
import AccessDialog from './AccessDialog.vue'
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
  <AccessDialog
    v-if="preview"
    title="商品图片预览"
    wide
    @close="preview = false"
  >
    <div class="purchase-image-preview">
      <AuthenticatedCatalogImage
        v-if="imageAssetId"
        :image-id="imageAssetId"
        variant="original"
        :alt="alt"
      /><img
        v-else
        :src="imageUrl ?? undefined"
        :alt="alt"
        referrerpolicy="no-referrer"
      >
    </div>
  </AccessDialog>
</template>
<style scoped>
.purchase-image-preview{display:flex;justify-content:center}.purchase-image-preview :deep(img){width:auto;height:auto;max-width:100%;max-height:70vh;object-fit:contain}.image-button .catalog-image{object-fit:contain}
</style>
