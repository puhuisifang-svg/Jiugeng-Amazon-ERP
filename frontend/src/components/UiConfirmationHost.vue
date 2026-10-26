<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { uiConfirmation, settleConfirmation } from '@/state/uiConfirmation'
const dialog = ref<HTMLDialogElement>(), cancel = ref<HTMLButtonElement>(), input = ref<HTMLInputElement>()
const reason = ref(''), route = useRoute()
let previousFocus: HTMLElement | undefined
watch(uiConfirmation, async value => {
  reason.value = ''
  if (!value) { dialog.value?.close(); previousFocus?.focus(); return }
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  await nextTick()
  if (uiConfirmation.value !== value) return
  dialog.value?.showModal(); (value.input ? input.value : cancel.value)?.focus()
}, { flush: 'post' })
watch(() => route.fullPath, () => settleConfirmation(null))
onBeforeUnmount(() => settleConfirmation(null))
function submit() { if (uiConfirmation.value?.input && !reason.value.trim()) return; settleConfirmation(reason.value.trim()) }
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="ui-confirm-dialog"
      aria-labelledby="ui-confirm-title"
      aria-describedby="ui-confirm-message"
      @cancel.prevent="settleConfirmation(null)"
    >
      <form
        v-if="uiConfirmation"
        @submit.prevent="submit"
      >
        <header>
          <span aria-hidden="true">!</span><h2 id="ui-confirm-title">
            {{ uiConfirmation.title }}
          </h2>
        </header>
        <div class="ui-confirm-body">
          <p id="ui-confirm-message">
            {{ uiConfirmation.message }}
          </p>
          <label v-if="uiConfirmation.input">操作原因<input
            ref="input"
            v-model="reason"
            required
            maxlength="500"
            placeholder="请输入原因，便于后续追溯"
          ></label>
        </div>
        <footer>
          <button
            ref="cancel"
            type="button"
            @click="settleConfirmation(null)"
          >
            取消
          </button><button
            class="ui-confirm-primary"
            :disabled="uiConfirmation.input && !reason.trim()"
          >
            确认
          </button>
        </footer>
      </form>
    </dialog>
  </Teleport>
</template>
<style scoped>
.ui-confirm-dialog{width:min(480px,calc(100vw - 24px));max-height:calc(100dvh - 24px);padding:0;border:1px solid var(--line);border-radius:10px;color:var(--ink);box-shadow:0 24px 64px #17203340;overflow:auto}.ui-confirm-dialog::backdrop{background:#0b0f199e}form{margin:0}header{display:flex;align-items:center;gap:10px;padding:18px 20px;border-bottom:1px solid var(--line)}header span{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:var(--accent-soft);color:#a64b0b;font-weight:700}h2{margin:0;font-size:17px}.ui-confirm-body{padding:20px}p{margin:0;color:#526174;font-size:14px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}label{display:grid;gap:8px;margin-top:16px;font-size:13px}input{width:100%;min-height:40px;border:1px solid var(--line);border-radius:6px;padding:8px 10px}footer{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px;padding:14px 20px;border-top:1px solid var(--line);background:white;position:sticky;bottom:0}button{min-height:36px;padding:7px 18px;border:1px solid var(--line);border-radius:6px;background:white;color:var(--ink);cursor:pointer}.ui-confirm-primary{background:var(--accent);color:white;border-color:var(--accent)}button:disabled{opacity:.5;cursor:not-allowed}button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
</style>
