<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, useId } from 'vue'
withDefaults(defineProps<{ message?: string }>(), { message: '本次填写的内容尚未保存。关闭后将丢失这些修改，已保存的数据不受影响。' })
const emit = defineEmits<{ keep: []; discard: [] }>()
const dialog = ref<HTMLDialogElement>(), keepButton = ref<HTMLButtonElement>()
const id = useId()
let previousFocus: HTMLElement | null = null
onMounted(() => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  dialog.value?.showModal()
  keepButton.value?.focus()
})
onBeforeUnmount(() => { dialog.value?.close(); previousFocus?.focus() })
</script>
<template>
  <dialog
    ref="dialog"
    class="unsaved-dialog"
    role="alertdialog"
    :aria-labelledby="`${id}-title`"
    :aria-describedby="`${id}-message`"
    @cancel.stop.prevent="emit('keep')"
    @keydown.esc.stop
  >
    <header>
      <span
        class="unsaved-icon"
        aria-hidden="true"
      >!</span>
      <h2 :id="`${id}-title`">
        要放弃本次填写吗？
      </h2>
    </header>
    <p :id="`${id}-message`">
      {{ message }}
    </p>
    <footer>
      <button
        type="button"
        class="unsaved-discard"
        @click="emit('discard')"
      >
        放弃并关闭
      </button>
      <button
        ref="keepButton"
        type="button"
        class="unsaved-keep"
        @click="emit('keep')"
      >
        继续填写
      </button>
    </footer>
  </dialog>
</template>
<style scoped>
.unsaved-dialog{box-sizing:border-box;width:min(440px,calc(100vw - 32px));max-height:calc(100dvh - 32px);margin:auto;padding:0;overflow:auto;border:1px solid #e2e8f0;border-radius:12px;background:#fff;color:#172033;box-shadow:0 24px 64px #17203340}
.unsaved-dialog::backdrop{background:#0b0f1973}
.unsaved-dialog header{display:flex;align-items:center;justify-content:flex-start;flex-wrap:nowrap;gap:12px;margin:0;padding:22px 24px 0}
.unsaved-icon{display:grid;place-items:center;flex:none;width:32px;height:32px;border-radius:50%;background:#fff4e6;color:#a64b0b;font-size:20px;font-weight:700}
.unsaved-dialog h2{margin:0;font-size:17px;line-height:1.5}
.unsaved-dialog p{margin:0;padding:16px 24px 24px;color:#64748b;font-size:14px;line-height:1.8;overflow-wrap:anywhere}
.unsaved-dialog footer{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px;margin:0;padding:16px 24px;border-top:1px solid #e2e8f0;background:#f8fafc}
.unsaved-dialog button{min-height:38px;padding:8px 16px;border:1px solid #e2e8f0;border-radius:6px;font:inherit;font-size:14px;cursor:pointer}
.unsaved-discard{background:#fff;color:#b42318}.unsaved-keep{background:var(--accent,#ef8b2c);border-color:var(--accent,#ef8b2c);color:#fff;font-weight:600}
button:focus-visible{outline:2px solid var(--accent,#ef8b2c);outline-offset:3px}
@media(max-width:480px){.unsaved-dialog header{padding:20px 18px 0}.unsaved-dialog p{padding:14px 18px 20px}.unsaved-dialog footer{padding:14px 18px}.unsaved-dialog button{flex:1}}
</style>
