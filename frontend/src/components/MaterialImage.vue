<script setup lang="ts">
import { ref, watch } from 'vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
import ImagePreviewDialog from './ImagePreviewDialog.vue'
const props=defineProps<{imageAssetId?:string|undefined;imageUrl?:string|undefined;name:string}>()
const failed=ref(false),preview=ref(false),attempt=ref(0)
watch(()=>[props.imageUrl,props.imageAssetId],()=>{failed.value=false;attempt.value++})
</script>
<template>
  <div class="material-thumb">
    <button
      v-if="imageAssetId || (imageUrl && !failed)"
      type="button"
      :aria-label="`查看${name}大图`"
      @click="preview=true"
    >
      <AuthenticatedCatalogImage
        v-if="imageAssetId"
        :image-id="imageAssetId"
        :alt="name"
      />
      <AuthenticatedCatalogImage
        v-else
        :key="attempt"
        draft-permission="materials.read"
        :image-url="imageUrl"
        :alt="name"
      />
    </button>
    <button
      v-else-if="failed"
      type="button"
      class="material-image-error"
      @click="failed=false;attempt++"
    >
      加载失败<br>点击重试
    </button>
    <span
      v-else
      class="material-image-empty"
    >暂无图片</span>
    <ImagePreviewDialog
      v-if="preview"
      :title="name"
      @close="preview=false"
    >
      <AuthenticatedCatalogImage
        v-if="imageAssetId"
        :image-id="imageAssetId"
        :alt="name"
        variant="original"
      /><AuthenticatedCatalogImage
        v-else
        variant="original"
        draft-permission="materials.read"
        :image-url="imageUrl"
        :alt="name"
      />
    </ImagePreviewDialog>
  </div>
</template>
