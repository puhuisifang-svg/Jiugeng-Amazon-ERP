<script setup lang="ts">
import ImagePreviewDialog from './ImagePreviewDialog.vue'
import { ref } from 'vue'
defineOptions({inheritAttrs:false})
defineProps<{ src:string; alt?:string }>()
const preview=ref(false),trigger=ref<HTMLButtonElement>(),failed=ref(false)
function open(){trigger.value?.focus({preventScroll:true});failed.value=false;preview.value=true}
function close(){preview.value=false}
</script>
<template>
  <button
    ref="trigger"
    v-bind="$attrs"
    type="button"
    class="product-image-trigger"
    :aria-label="`放大查看${alt || '商品图片'}`"
    @click.stop="open"
  >
    <img
      :src="src"
      :alt="alt || '商品主图'"
      loading="lazy"
      referrerpolicy="no-referrer"
    >
  </button>
  <ImagePreviewDialog
    v-if="preview"
    title="商品图片预览"
    :description="alt || '商品图片'"
    @close="close"
  >
    <p
      v-if="failed"
      role="alert"
    >
      图片加载失败，请关闭后重试。
    </p>
    <img
      v-else
      :src="src"
      :alt="alt || '商品大图'"
      referrerpolicy="no-referrer"
      @error="failed=true"
    >
  </ImagePreviewDialog>
</template>
<style scoped>
.product-image-trigger{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;min-width:0!important;min-height:0!important;padding:0!important;border:0!important;background:transparent!important;cursor:zoom-in;max-width:100%;border-radius:4px}.product-image-trigger img{object-fit:contain;max-width:100%}.product-image-trigger.pmd-image{width:112px;height:112px;flex:0 0 112px;overflow:hidden}.product-image-trigger.pmd-image img{width:100%;height:100%}.product-image-trigger:focus-visible{outline:2px solid #2864c5;outline-offset:2px}</style>
