<script setup lang="ts">
import { ref, watch } from 'vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
import ImagePreviewDialog from './ImagePreviewDialog.vue'
const props=defineProps<{imageAssetId?:string|undefined;imageUrl?:string|undefined;name:string}>()
const failed=ref(false),preview=ref(false),attempt=ref(0)
watch(()=>[props.imageUrl,props.imageAssetId],()=>{failed.value=false;attempt.value++})
</script>
<template>
  <div class="supplier-thumb">
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
      <img
        v-else
        :key="attempt"
        :src="imageUrl"
        :alt="name"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="failed=true"
      >
    </button>
    <button
      v-else-if="failed"
      type="button"
      class="supplier-image-error"
      @click="failed=false;attempt++"
    >
      加载失败<br>点击重试
    </button>
    <span
      v-else
      class="supplier-image-empty"
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
      /><img
        v-else
        :src="imageUrl"
        :alt="name"
        referrerpolicy="no-referrer"
      >
    </ImagePreviewDialog>
  </div>
</template>

<style scoped>
.supplier-thumb,.supplier-image-empty{width:64px;height:64px;flex:none}.supplier-thumb>button{width:100%;height:100%;display:grid;place-items:center;border:1px solid var(--line);border-radius:5px;padding:0;background:white;overflow:hidden}.supplier-thumb :deep(img),.supplier-thumb :deep(.catalog-image){width:100%;height:100%;object-fit:contain}.supplier-image-empty{display:grid;place-items:center;background:#f5f7fa;color:var(--muted);font-size:12px;border-radius:5px}.supplier-image-error{font-size:12px;color:#98630b}
</style>
