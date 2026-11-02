<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
const props=defineProps<{productId:string;variantId:string;alt:string}>()
const product=ref<ProductDetailDto>(),loading=ref(false),dialog=ref<HTMLDialogElement>(),preview=ref(false)
watch(()=>props.productId,async (id,_old,onCleanup)=>{let active=true;onCleanup(()=>{active=false});product.value=undefined;if(!id)return;loading.value=true;try{const result=await domainApi.product(id);if(active)product.value=result}catch{/* Missing or inaccessible images use the placeholder. */}finally{if(active)loading.value=false}},{immediate:true})
const variant=computed(()=>product.value?.variants.find(v=>v.id===props.variantId))
const hasImage=computed(()=>Boolean(variant.value?.imageAssetId||variant.value?.imageUrl))
</script>
<template>
  <button
    v-if="hasImage"
    type="button"
    class="customer-product-image"
    :aria-label="`放大查看${alt}`"
    @click.stop="preview=true;dialog?.showModal()"
  >
    <AuthenticatedCatalogImage
      v-if="variant?.imageAssetId"
      :image-id="variant.imageAssetId"
      :alt="alt"
    />
    <img
      v-else
      :src="variant?.imageUrl"
      :alt="alt"
      loading="lazy"
      referrerpolicy="no-referrer"
    >
  </button>
  <span
    v-else
    class="customer-product-image customer-product-image--empty"
  >{{ loading?'加载中':'暂无图片' }}</span>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="customer-product-image-preview"
      aria-label="产品图片预览"
      @cancel.prevent.stop="dialog?.close()"
      @keydown.esc.stop
    >
      <header>
        <strong>{{ alt }}</strong><button
          type="button"
          aria-label="关闭图片预览"
          @click="dialog?.close()"
        >
          ×
        </button>
      </header>
      <AuthenticatedCatalogImage
        v-if="variant?.imageAssetId"
        :image-id="preview?variant.imageAssetId:undefined"
        variant="original"
        :alt="alt"
      />
      <img
        v-else
        :src="variant?.imageUrl"
        :alt="alt"
        referrerpolicy="no-referrer"
      >
    </dialog>
  </Teleport>
</template>
<style scoped>
.customer-product-image{display:inline-flex;width:64px;height:64px;min-height:64px;padding:0;border:1px solid var(--line);border-radius:4px;background:white;align-items:center;justify-content:center;cursor:zoom-in;overflow:hidden}.customer-product-image :deep(img){width:100%;height:100%;object-fit:contain}.customer-product-image--empty{font-size:12px;color:var(--muted);background:#f5f7fa;cursor:default}.customer-product-image-preview{width:min(800px,calc(100vw - 32px));max-height:calc(100dvh - 32px);padding:14px;border:0;border-radius:8px}.customer-product-image-preview::backdrop{background:#14223b99}.customer-product-image-preview header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.customer-product-image-preview header button{background:transparent;color:var(--ink);font-size:22px;padding:0 8px}.customer-product-image-preview :deep(img){display:block;width:100%;height:auto;max-height:calc(100dvh - 120px);object-fit:contain}
</style>
