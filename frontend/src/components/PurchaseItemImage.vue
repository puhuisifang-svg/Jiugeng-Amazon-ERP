<script setup lang="ts">
import { ref } from 'vue'
import ImagePreviewDialog from './ImagePreviewDialog.vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
defineProps<{ imageAssetId?: string | null | undefined; imageUrl?: string | null | undefined; alt: string }>()
const preview = ref(false),failed=ref(false),retry=ref(0)
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
    /><span
      v-else-if="failed"
      role="button"
      tabindex="0"
      @click.stop="failed=false;retry++"
      @keydown.enter.stop="failed=false;retry++"
    >加载失败 · 重试</span><img
      v-else
      :key="retry"
      loading="lazy"
      class="catalog-image"
      :src="imageUrl ?? undefined"
      :alt="alt"
      referrerpolicy="no-referrer"
      @error="failed=true"
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
    <span
      v-else-if="failed"
      role="button"
      tabindex="0"
      @click.stop="failed=false;retry++"
      @keydown.enter.stop="failed=false;retry++"
    >加载失败 · 重试</span><img
      v-else
      :key="retry"
      loading="lazy"
      :src="imageUrl ?? undefined"
      :alt="alt"
      referrerpolicy="no-referrer"
      @error="failed=true"
    >
  </ImagePreviewDialog>
</template>
<style scoped>
.image-button .catalog-image{object-fit:contain}
</style>
