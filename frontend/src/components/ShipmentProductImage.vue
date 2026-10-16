<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'

const props = defineProps<{ productId: string; product?: ProductDetailDto | undefined; alt: string }>()
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
const variant = computed(() => (props.product ?? loaded.value)?.variants?.find(item => item.imageAssetId || item.imageUrl))
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
    <img
      v-else
      class="catalog-image"
      :src="variant.imageUrl"
      :alt="alt"
      referrerpolicy="no-referrer"
    >
  </button>
  <span
    v-else
    role="img"
    :aria-label="`${alt}（未设置产品图片）`"
  >未设置产品图片</span>
  <Teleport to="body">
    <div
      v-if="preview"
      class="image-dialog material-preview"
      role="dialog"
      aria-modal="true"
      aria-label="产品图片预览"
      @click.self="preview = false"
      @keydown.esc="preview = false"
    >
      <div>
        <button
          type="button"
          aria-label="关闭图片预览"
          autofocus
          @click="preview = false"
        >
          ×
        </button>
        <AuthenticatedCatalogImage
          v-if="variant?.imageAssetId"
          :image-id="variant.imageAssetId"
          variant="original"
          :alt="alt"
        />
        <img
          v-else
          class="material-preview__image"
          :src="variant?.imageUrl"
          :alt="alt"
          referrerpolicy="no-referrer"
        >
      </div>
    </div>
  </Teleport>
</template>
