<script setup lang="ts">
import { ref } from 'vue'
defineOptions({inheritAttrs:false})
defineProps<{ src:string; alt?:string }>()
const dialog=ref<HTMLDialogElement>(),trigger=ref<HTMLButtonElement>(),failed=ref(false)
function open(){failed.value=false;dialog.value?.showModal()}
function close(){dialog.value?.close();trigger.value?.focus({preventScroll:true})}
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
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="product-image-dialog"
      aria-label="商品图片预览"
      @cancel.prevent.stop="close"
      @click="($event.target === dialog) && close()"
    >
      <header>
        <strong>{{ alt || '商品图片' }}</strong><button
          type="button"
          aria-label="关闭图片预览"
          autofocus
          @click="close"
        >
          ×
        </button>
      </header>
      <div class="product-image-stage">
        <p
          v-if="failed"
          role="alert"
        >
          图片加载失败，请关闭后重试。
        </p><img
          v-else
          :src="src"
          :alt="alt || '商品大图'"
          referrerpolicy="no-referrer"
          @error="failed=true"
        >
      </div>
    </dialog>
  </Teleport>
</template>
<style scoped>
.product-image-trigger{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;min-width:0!important;min-height:0!important;padding:0!important;border:0!important;background:transparent!important;cursor:zoom-in;max-width:100%;border-radius:4px}.product-image-trigger img{object-fit:contain;max-width:100%}.product-image-trigger.pmd-image{width:112px;height:112px;flex:0 0 112px;overflow:hidden}.product-image-trigger.pmd-image img{width:100%;height:100%}.product-image-trigger:focus-visible{outline:2px solid #2864c5;outline-offset:2px}.product-image-dialog{width:min(1000px,calc(100vw - 24px));max-height:calc(100dvh - 24px);padding:0;border:0;border-radius:8px;color:#24334b;box-shadow:0 16px 60px #17203340}.product-image-dialog::backdrop{background:#17203399}.product-image-dialog[open]{display:flex;flex-direction:column}.product-image-dialog header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 14px;border-bottom:1px solid #e5e8ef;flex:none}.product-image-dialog strong{font-size:14px;overflow-wrap:anywhere}.product-image-dialog header button{width:32px;height:32px;padding:0;border:0;background:transparent;font-size:24px;cursor:pointer;flex:none}.product-image-stage{padding:12px;min-height:0;overflow:auto;display:flex;justify-content:center;align-items:center}.product-image-stage img{display:block;max-width:100%;max-height:calc(100dvh - 110px);object-fit:contain}.product-image-stage p{padding:32px;color:#b42318}
</style>
