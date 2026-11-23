<script setup lang="ts">
import { ref, watch } from 'vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
import ImagePreviewDialog from './ImagePreviewDialog.vue'
const props=defineProps<{imageId?:string|null|undefined;imageUrl?:string|null|undefined;alt:string}>()
const failed=ref(false),preview=ref(false),attempt=ref(0)
watch(()=>[props.imageId,props.imageUrl],()=>{failed.value=false;preview.value=false;attempt.value=0})
</script>
<template>
  <div
    v-if="failed"
    class="sample-image-failure"
  >
    <span>图片加载失败</span><button
      type="button"
      @click="failed=false;attempt++"
    >
      重试
    </button>
  </div>
  <button
    v-else-if="imageId || imageUrl"
    type="button"
    class="image-button"
    :aria-label="`预览${alt}`"
    @click="preview=true"
  >
    <AuthenticatedCatalogImage
      v-if="imageId"
      :image-id="imageId"
      :alt="alt"
    />
    <AuthenticatedCatalogImage
      v-else
      :key="attempt"
      :image-url="imageUrl ?? undefined"
      :alt="alt"
      class="catalog-image"
    />
  </button>
  <span
    v-else
    class="sample-image-empty"
    role="img"
    :aria-label="`${alt}：暂无图片`"
  >暂无图片</span>
  <ImagePreviewDialog
    v-if="preview"
    title="样品图片"
    :description="alt"
    @close="preview=false"
  >
    <AuthenticatedCatalogImage
      v-if="imageId"
      :image-id="imageId"
      variant="original"
      :alt="alt"
    />
    <AuthenticatedCatalogImage
      v-else
      variant="original"
      :image-url="imageUrl ?? undefined"
      :alt="alt"
    />
  </ImagePreviewDialog>
</template>
