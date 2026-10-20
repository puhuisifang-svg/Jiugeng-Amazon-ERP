<script setup lang="ts">
import { onMounted, ref } from 'vue'
defineProps<{ title: string; name: string; description: string; busy: boolean; error: string; notice?: string }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
const dialog = ref<HTMLDialogElement>()
onMounted(() => dialog.value?.showModal())
</script>

<template>
  <dialog
    ref="dialog"
    class="catalog-delete-dialog"
    :aria-label="title"
    @cancel.prevent="!busy && emit('cancel')"
  >
    <div class="catalog-delete-card">
      <h2>{{ title }}</h2>
      <p class="catalog-delete-name">
        {{ name }}
      </p>
      <p>{{ description }}</p>
      <p>{{ notice ?? '存在发货、发票、采购或收货记录时将拒绝删除。删除后无法在页面恢复，操作会记录审计日志。' }}</p>
      <p
        v-if="error"
        class="data-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <div class="catalog-delete-actions">
        <button
          type="button"
          class="secondary-button"
          :disabled="busy"
          @click="emit('cancel')"
        >
          取消
        </button>
        <button
          type="button"
          class="danger-button"
          :disabled="busy"
          @click="emit('confirm')"
        >
          {{ busy ? '删除中…' : '确认删除' }}
        </button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.catalog-delete-dialog { padding: 0; width: min(500px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); border: 0; border-radius: 14px; overflow: auto; box-shadow: 0 18px 60px #17213b33; }
.catalog-delete-dialog::backdrop { background: #17213b66; }
.catalog-delete-card { padding: 24px; }
h2 { margin: 0 0 18px; font-size: 20px; }
p { line-height: 1.7; color: var(--muted); overflow-wrap: anywhere; }
.catalog-delete-name { color: var(--ink); font-weight: 700; }
.catalog-delete-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
button { border: 0; border-radius: 7px; padding: 10px 20px; cursor: pointer; }
.secondary-button { background: #eef0f4; color: var(--muted); }
.danger-button { background: #b83838; color: white; }
button:disabled { opacity: .6; cursor: not-allowed; }
</style>
