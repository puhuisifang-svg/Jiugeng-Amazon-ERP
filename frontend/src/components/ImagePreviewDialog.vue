<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ title: string; description?: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
let previousFocus: HTMLElement | null = null
onMounted(() => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
})
onBeforeUnmount(() => {
  dialog.value?.close()
  if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="image-preview-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      @cancel.prevent.stop="emit('close')"
      @click.self="emit('close')"
    >
      <header class="image-preview-dialog__header">
        <h2 :title="description || title">
          {{ description || title }}
        </h2>
        <button
          type="button"
          class="image-preview-dialog__close"
          aria-label="关闭图片预览"
          autofocus
          @click="emit('close')"
        >
          ×
        </button>
      </header>
      <div class="image-preview-dialog__stage">
        <slot />
      </div>
      <footer class="image-preview-dialog__footer">
        <div
          v-if="$slots.message"
          class="image-preview-dialog__message"
        >
          <slot name="message" />
        </div>
        <slot name="actions" />
        <button
          type="button"
          @click="emit('close')"
        >
          关闭
        </button>
      </footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.image-preview-dialog { box-sizing: border-box; width: min(840px, calc(100vw - 24px)); height: min(680px, calc(100dvh - 24px)); max-width: calc(100vw - 24px); max-height: calc(100dvh - 24px); padding: 0; border: 1px solid var(--line); border-radius: var(--ui-radius, 8px); color: var(--ink); background: white; overflow: hidden; }
.image-preview-dialog[open] { display: flex; flex-direction: column; }
.image-preview-dialog::backdrop { background: #17203399; }
.image-preview-dialog > .image-preview-dialog__header { display: flex; flex: none; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; border-bottom: 1px solid var(--line); }
.image-preview-dialog__header h2 { margin: 0; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px; }
.image-preview-dialog .image-preview-dialog__close { position: static; flex: none; width: 32px; height: 32px; min-height: 32px; padding: 0; border: 0; border-radius: 4px; color: var(--ink); background: transparent; font-size: 24px; cursor: pointer; }
.image-preview-dialog__stage { flex: 1; min-height: 0; min-width: 0; display: grid; grid-template: minmax(0, 1fr) / minmax(0, 1fr); place-items: center; padding: 12px; overflow: hidden; background: #f5f7fa; }
.image-preview-dialog__stage :deep(img), .image-preview-dialog__stage :deep(.catalog-image), .image-preview-dialog__stage :deep(.dashboard-image) { display: block; box-sizing: border-box; width: 100%; height: 100%; min-width: 0; min-height: 0; max-width: 100%; max-height: 100%; object-fit: contain; border: 0; border-radius: 0; background: transparent; }
.image-preview-dialog__stage :deep([role="status"]), .image-preview-dialog__stage :deep([role="img"]), .image-preview-dialog__stage :deep([role="alert"]) { display: grid; place-items: center; margin: 0; color: var(--muted); }
.image-preview-dialog > .image-preview-dialog__footer { display: flex; flex: none; justify-content: flex-end; align-items: center; flex-wrap: wrap; gap: 8px; padding: 10px 14px; border-top: 1px solid var(--line); max-height: 35%; overflow: auto; }
.image-preview-dialog__footer :deep(button) { position: static; width: auto; height: auto; min-height: 32px; padding: 4px 12px; color: var(--ink); background: white; border: 1px solid var(--line); border-radius: 4px; font-size: 13px; cursor: pointer; }
.image-preview-dialog__footer :deep(button:disabled) { opacity: .5; cursor: default; }
.image-preview-dialog__message { flex-basis: 100%; overflow-wrap: anywhere; }
.image-preview-dialog__message :deep(p) { margin: 0; color: #b42318; font-size: 13px; }
</style>
