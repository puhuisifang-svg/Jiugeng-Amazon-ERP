<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
const props = defineProps<{ modelValue: string[]; options: { value: string; label: string }[]; label: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()
const root = ref<HTMLElement>(), panel = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>()
const open = ref(false), search = ref(''), position = ref({ left: '0px', top: '0px', maxHeight: '360px' })
const filtered = computed(() => props.options.filter(o => `${o.label} ${o.value}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const summary = computed(() => props.modelValue.length ? props.modelValue.length === 1 ? props.options.find(o => o.value === props.modelValue[0])?.label || props.modelValue[0] : `已选 ${props.modelValue.length} 项` : `全部${props.label}`)
const all = computed(() => filtered.value.length > 0 && filtered.value.every(o => props.modelValue.includes(o.value)))
function toggle(value: string) { emit('update:modelValue', props.modelValue.includes(value) ? props.modelValue.filter(v => v !== value) : [...props.modelValue, value]) }
function selectAll() { const visible = new Set(filtered.value.map(o => o.value)); emit('update:modelValue', all.value ? props.modelValue.filter(v => !visible.has(v)) : [...new Set([...props.modelValue, ...visible])]) }
function place() { if (!root.value || !panel.value) return; const r = root.value.getBoundingClientRect(); const h = panel.value.offsetHeight; const top = Math.max(8, Math.min(r.bottom + 4, innerHeight - h - 8)); position.value = { left: `${Math.max(8, Math.min(r.left, innerWidth - panel.value.offsetWidth - 8))}px`, top: `${top}px`, maxHeight: `${innerHeight-top-8}px` } }
function outside(e: Event) { if (!root.value?.contains(e.target as Node)) close() }
function close(focus = false) { panel.value?.hidePopover?.(); open.value = false; document.removeEventListener('pointerdown', outside, true); window.removeEventListener('resize', place); document.removeEventListener('scroll', place, true); if (focus) trigger.value?.focus() }
async function show() { if (props.disabled) return; if (open.value) { close(); return }; search.value = ''; open.value = true; await nextTick(); panel.value?.showPopover?.(); place(); panel.value?.querySelector('input')?.focus(); document.addEventListener('pointerdown', outside, true); window.addEventListener('resize', place); document.addEventListener('scroll', place, true) }
onBeforeUnmount(() => close())
</script>
<template>
  <span
    ref="root"
    class="filter-multi"
  >
    <button
      ref="trigger"
      type="button"
      class="filter-multi-trigger"
      :disabled="disabled"
      :aria-label="label"
      :aria-expanded="open"
      aria-haspopup="dialog"
      @click="show"
    ><span>{{ summary }}</span><span aria-hidden="true">⌄</span></button>
    <div
      v-if="open"
      ref="panel"
      popover="manual"
      class="filter-multi-panel"
      :style="position"
      role="dialog"
      :aria-label="`${label}多选`"
      @keydown.esc.stop.prevent="close(true)"
    >
      <input
        v-model="search"
        type="search"
        :aria-label="`搜索${label}`"
        placeholder="搜索名称或编码"
      >
      <div class="filter-multi-tools"><label><input
        type="checkbox"
        :checked="all"
        :indeterminate="!all && filtered.some(o => modelValue.includes(o.value))"
        :disabled="!filtered.length"
        @change="selectAll"
      >全选</label><button
        type="button"
        @click="emit('update:modelValue', [])"
      >清空</button></div>
      <div class="filter-multi-options"><label
        v-for="option in filtered"
        :key="option.value"
        :title="option.value"
      ><input
        type="checkbox"
        :checked="modelValue.includes(option.value)"
        @change="toggle(option.value)"
      ><span>{{ option.label }}</span></label><p v-if="!filtered.length">没有匹配选项</p></div>
      <footer><span>已选 {{ modelValue.length }} 项</span><button
        type="button"
        @click="close(true)"
      >完成</button></footer>
    </div>
  </span>
</template>
<style>
.filter-multi { display:inline-flex; flex:1; min-width:0; width:100%; }
#app .filter-multi-trigger { display:flex; align-items:center; justify-content:space-between; gap:8px; width:100%; min-width:0; height:32px; padding:0 10px; border:1px solid var(--line); border-radius:4px; background:white; color:var(--ink); font-size:13px; font-weight:400; cursor:pointer; }
.filter-multi-trigger > span:first-child { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.filter-multi-panel { position:fixed; inset:auto; margin:0; padding:10px; width:300px; max-width:calc(100vw - 16px); border:1px solid var(--line); border-radius:6px; box-shadow:0 6px 24px #17203326; background:white; color:var(--ink); overflow:auto; font:13px/1.5 system-ui,sans-serif; }
#app #main-content .filter-multi-panel > input[type=search] { flex:none; width:100%; height:32px; min-height:32px; border:1px solid var(--line); border-radius:4px; padding:0 8px; }
#app .filter-multi-panel label { display:flex; flex-direction:row; justify-content:flex-start; align-items:center; gap:8px; padding:6px 4px; white-space:normal; font-weight:400; color:var(--ink); font-size:13px; cursor:pointer; }
#app .filter-multi-panel input[type=checkbox] { flex:none; width:15px; height:15px; min-height:15px; margin:0; accent-color:#2864c5; }
#app #main-content .filter-multi-tools > label { color:#2864c5; }
.filter-multi-options { max-height:220px; overflow:auto; }
.filter-multi-options label:hover { background:#edf4ff; }
.filter-multi-tools,.filter-multi-panel footer { display:flex; align-items:center; justify-content:space-between; gap:8px; border-bottom:1px solid var(--line); padding:4px 0; }
.filter-multi-panel footer { border-bottom:0; border-top:1px solid var(--line); color:var(--muted); }
#app .filter-multi-panel button { height:28px; min-height:28px; padding:2px 8px; border:0; border-radius:4px; color:#2864c5; background:#edf4ff; font:inherit; cursor:pointer; }
</style>
