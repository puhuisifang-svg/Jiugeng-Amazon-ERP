<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
defineProps<{ label: string }>()
const trigger = ref<HTMLButtonElement>(), menu = ref<HTMLElement>()
const opened = ref(false), left = ref(0), top = ref(0)
function close() { opened.value = false }
async function toggle() {
  if (opened.value) { close(); return }
  const rect = trigger.value!.getBoundingClientRect()
  left.value = Math.max(8, Math.min(rect.right - 144, window.innerWidth - 152))
  top.value = rect.bottom + 4
  opened.value = true
  await nextTick()
  top.value = Math.max(8, Math.min(top.value, window.innerHeight - (menu.value?.offsetHeight ?? 144) - 8))
  menu.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus()
}
function outside(event: Event) { if (event.target instanceof Node && !menu.value?.contains(event.target) && !trigger.value?.contains(event.target)) close() }
function key(event: KeyboardEvent) { if (event.key === 'Escape' && opened.value) { close(); trigger.value?.focus() } }
function choose(event: MouseEvent) { if ((event.target as HTMLElement).closest('button:not(:disabled), a')) close() }
onMounted(() => { document.addEventListener('pointerdown', outside); document.addEventListener('keydown', key); window.addEventListener('resize', close); document.addEventListener('scroll', close, true) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', key); window.removeEventListener('resize', close); document.removeEventListener('scroll', close, true) })
</script>
<template>
  <span class="row-more-actions">
    <button
      ref="trigger"
      type="button"
      :aria-label="label"
      :aria-expanded="opened"
      class="row-more-actions__trigger"
      @click.stop="toggle"
    >⋯</button>
    <Teleport
      to="body"
      :disabled="!opened"
    >
      <div
        v-show="opened"
        ref="menu"
        class="row-more-actions__menu"
        :style="{ left: `${left}px`, top: `${top}px` }"
        :aria-label="label"
        @click.stop="choose"
      ><slot /></div>
    </Teleport>
  </span>
</template>
<style scoped>
.row-more-actions { display: inline-flex; }
.row-more-actions__menu { position: fixed; z-index: 24; width: 144px; padding: 4px; background: white; border: 1px solid var(--line); border-radius: 6px; box-shadow: 0 6px 24px #17203326; }
.row-more-actions__menu :deep(button) { display: block; width: 100%; min-height: 32px; padding: 0 10px; border: 0; border-radius: 4px; color: var(--ink); background: white; text-align: left; font-size: 13px; cursor: pointer; }
.row-more-actions__menu :deep(button:hover:not(:disabled)) { background: var(--accent-soft); }
</style>
