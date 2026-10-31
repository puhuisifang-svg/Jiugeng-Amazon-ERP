<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props = defineProps<{ title: string; dirty?: boolean; busy?: boolean; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>(), confirm = ref(false)
let focus: HTMLElement | null = null
defineExpose({ requestClose: () => cancel() })
function cancel() { if (props.busy) return; if (props.dirty) confirm.value = true; else emit('close') }
onMounted(() => { focus = document.activeElement as HTMLElement; dialog.value?.showModal() })
onBeforeUnmount(() => { dialog.value?.close(); focus?.focus() })
</script>
<template>
  <dialog
    ref="dialog"
    class="access-dialog"
    :class="{ 'access-dialog--wide': wide, 'access-dialog--paginated': !!$slots.pagination }"
    :aria-label="title"
    @cancel.prevent.stop="cancel"
  >
    <header>
      <h2>{{ title }}</h2><button
        type="button"
        aria-label="关闭"
        :disabled="busy"
        @click="cancel"
      >
        ×
      </button>
    </header>
    <div class="access-dialog-body">
      <slot />
    </div>
    <footer :class="{ 'access-dialog-pagination': !!$slots.pagination }">
      <slot
        v-if="$slots.pagination"
        name="pagination"
      />
      <slot
        v-else
        name="footer"
      >
        <button
          type="button"
          :disabled="busy"
          @click="cancel"
        >
          关闭
        </button>
      </slot>
    </footer>
  </dialog>
  <UnsavedChangesDialog
    v-if="confirm"
    @keep="confirm = false"
    @discard="emit('close')"
  />
</template>
<style>
.access-dialog { width:min(560px,calc(100vw - 24px)); max-height:calc(100dvh - 24px); padding:0; border:1px solid var(--line); border-radius:8px; color:var(--ink); }
.access-dialog--wide { width:min(1000px,calc(100vw - 24px)); }
.access-dialog[open] { display:flex; flex-direction:column; }
.access-dialog::backdrop { background:#17203366; }
.access-dialog > header,.access-dialog > footer { display:flex; align-items:center; justify-content:space-between; flex:none; padding:10px 14px; gap:10px; border-bottom:1px solid var(--line); }
.access-dialog > header h2 { font-size:16px; margin:0; }.access-dialog > header button { background:transparent; border:0; font-size:22px; }
.access-dialog > footer { justify-content:flex-end; border-bottom:0; border-top:1px solid var(--line); flex-wrap:wrap; }
.access-dialog-body { padding:14px; overflow:auto; min-height:0; }
.access-dialog button { min-height:32px; padding:4px 10px; border:1px solid var(--line); border-radius:4px; cursor:pointer; }
.access-dialog .primary-button { background:var(--accent); color:white; border-color:var(--accent); }
.access-dialog button:disabled { opacity:.5; cursor:not-allowed; }
.access-form { display:grid; gap:12px; }.access-form > label { display:grid; gap:6px; font-size:13px; }
.access-form input:not([type=checkbox]),.access-form select,.access-form textarea { width:100%; min-height:40px; border:1px solid var(--line); border-radius:4px; padding:8px; }
.access-form input[type=checkbox] { width:16px; height:16px; accent-color:#2864c5; }
.access-form .role-choice { display:flex; align-items:center; gap:8px; padding:8px; border:1px solid var(--line); border-radius:4px; }
.access-muted { color:var(--muted); font-size:12px; }.access-danger { color:#b42318; }.access-tags { display:flex; gap:4px; flex-wrap:wrap; }.access-tag { padding:2px 6px; background:#edf4ff; color:#2864c5; border-radius:3px; font-size:12px; }
.access-page button { cursor:pointer; }.access-page .data-table td { vertical-align:middle; padding:8px 10px; line-height:18px; font-size:13px; }.access-page .data-table td.table-actions { min-width:112px; white-space:nowrap; }.access-page .table-actions > button,.access-page .table-actions > .row-more-actions { display:inline-flex; vertical-align:middle; }.access-page .table-actions button { background:transparent; border:0; color:#2864c5; padding:4px 6px; min-height:28px; }
.access-page .filter-panel { display:flex; gap:12px; align-items:center; flex-wrap:wrap; }.access-page .filter-panel label { display:flex; align-items:center; gap:6px; flex:0 1 240px; min-width:0; }.access-page .filter-panel input { width:160px; min-width:0; }
.access-page .filter-panel .filter-multi { width:150px; }.access-tabs { display:flex; gap:6px; border-bottom:1px solid var(--line); margin:0 0 12px; padding-bottom:6px; }.access-tabs button { border:0; background:transparent; color:var(--muted); }.access-tabs .active { background:#edf4ff; color:#2864c5; }
.access-form .password-field{width:100%;min-width:0}.access-form .password-field input{height:40px;padding-right:44px;font-size:13px}
.access-form .password-field .password-toggle{top:0;right:0;width:40px;height:40px;min-height:40px;padding:10px;border:0;background:transparent;display:flex;align-items:center;justify-content:center}.access-form .password-toggle svg{width:20px;height:20px;fill:currentColor}
.access-dialog--paginated{height:min(850px,calc(100dvh - 24px));overflow:hidden}
.access-dialog--paginated .access-dialog-body{flex:1;min-height:0}
.access-dialog > .access-dialog-pagination{min-height:48px;padding:0;display:flex;align-items:center;flex-shrink:0}
.access-dialog-pagination .list-pagination{width:100%;border-top:0;box-sizing:border-box}
</style>
