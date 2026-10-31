<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useAttrs } from 'vue'
defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ modelValue?: string | null | undefined; end?: string | null | undefined; range?: boolean; type?: 'date' | 'datetime-local' | 'time'; startLabel?: string; endLabel?: string; disabled?: boolean; required?: boolean; min?: string; max?: string }>(), { modelValue: '', end: '', range: false, type: 'date', startLabel: '开始日期', endLabel: '结束日期', disabled: false, required: false, min: '', max: '' })
const emit = defineEmits<{ 'update:modelValue': [string]; 'update:end': [string]; change: [Event] }>()
const attrs = useAttrs()
const fieldLabel = ref('')
onMounted(() => {
  const label = root.value?.closest('label')
  fieldLabel.value = label ? Array.from(label.childNodes).filter(node => node.nodeType === Node.TEXT_NODE).map(node => node.textContent?.trim()).join(' ').trim() : ''
})
const trigger = ref<HTMLButtonElement>()
const root = ref<HTMLElement>(), panel = ref<HTMLElement>(), first = ref<HTMLInputElement>(), last = ref<HTMLInputElement>()
const open = ref(false), draft = ref(''), finish = ref(''), choosingEnd = ref(false), hover = ref('')
const month = ref(new Date()), position = ref({ left: '0px', top: '0px', maxHeight: '500px' })
const pad = (n: number) => String(n).padStart(2, '0')
function day(d: Date) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` }
const today = () => day(new Date())
const months = computed(() => Array.from({ length: props.range ? 2 : 1 }, (_, i) => new Date(month.value.getFullYear(), month.value.getMonth() + i, 1)))
function cells(m: Date) { const start = new Date(m.getFullYear(), m.getMonth(), 1 - m.getDay()); return Array.from({ length: 42 }, (_, i) => { const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i); return { key: day(date), date: date.getDate(), other: date.getMonth() !== m.getMonth() } }) }
function allowed(value: string) { return (!props.min || value >= props.min.slice(0, 10)) && (!props.max || value <= props.max.slice(0, 10)) }
function move(n: number) { month.value = new Date(month.value.getFullYear(), month.value.getMonth() + n, 1) }
function updatePosition() {
  if (!root.value || !panel.value) return
  const r = root.value.getBoundingClientRect(), height = panel.value.offsetHeight, width = panel.value.offsetWidth
  const below = innerHeight - r.bottom - 8, above = r.top - 8
  const top = innerWidth <= 640 ? Math.max(8, Math.min(r.bottom + 4, innerHeight - height - 8)) : below >= height || below >= above ? r.bottom + 4 : Math.max(8, r.top - height - 4)
  position.value = { left: `${Math.max(8, Math.min(r.left, innerWidth - width - 8))}px`, top: `${top}px`, maxHeight: `${Math.max(120, innerHeight - top - 8)}px` }
}
async function show() {
  if (props.disabled || first.value?.matches(':disabled') || open.value) return
  draft.value = props.modelValue || ''; finish.value = props.end || ''; choosingEnd.value = false; hover.value = ''
  const initial = props.modelValue ? new Date(props.modelValue.slice(0, 10) + 'T12:00:00') : new Date()
  month.value = new Date(initial.getFullYear(), initial.getMonth(), 1)
  open.value = true; await nextTick(); panel.value?.showPopover?.(); updatePosition()
  panel.value?.querySelector<HTMLButtonElement>('[data-day]:not(:disabled)')?.focus()
  document.addEventListener('pointerdown', outside, true); window.addEventListener('resize', updatePosition); document.addEventListener('scroll', updatePosition, true)
}
function close(focus = false) {
  panel.value?.hidePopover?.(); open.value = false
  document.removeEventListener('pointerdown', outside, true); window.removeEventListener('resize', updatePosition); document.removeEventListener('scroll', updatePosition, true)
  // Restore focus to the explicit trigger, avoiding native date-input activation.
  if (focus) trigger.value?.focus()
}
function outside(e: Event) { if (!root.value?.contains(e.target as Node)) close() }
function commit(start = draft.value, end = finish.value) {
  emit('update:modelValue', start); if (props.range) emit('update:end', end)
  emit('change', new Event('change')); close(true)
}
function choose(value: string) {
  const time = (old: string, fallback: string) => props.type === 'datetime-local' ? `T${old.split('T')[1] || fallback}` : ''
  if (!props.range) { draft.value = value + time(draft.value, '00:00'); if (props.type === 'date') commit(); return }
  if (!choosingEnd.value) { draft.value = value + time(draft.value, '00:00'); finish.value = ''; choosingEnd.value = true }
  else { const start = draft.value.slice(0, 10); const a = value < start ? value : start, b = value < start ? start : value
    draft.value = a + time(draft.value, '00:00'); finish.value = b + time(finish.value, '23:59:59'); choosingEnd.value = false
    if (props.type === 'date') commit()
  }
}
function selected(value: string) { return value === draft.value.slice(0, 10) || (props.range && value === finish.value.slice(0, 10)) }
function between(value: string) { const a = draft.value.slice(0, 10), b = finish.value.slice(0, 10) || hover.value; return props.range && a && b && value >= (a < b ? a : b) && value <= (a < b ? b : a) }
const presets = ['今天', '昨天', '最近7天', '最近30天', '本月', '上月', '最近6个月', '今年']
function shortcut(index: number) {
  const now = new Date(), a = new Date(now.getFullYear(), now.getMonth(), now.getDate()), b = new Date(a)
  if (index === 1) { a.setDate(a.getDate() - 1); b.setDate(b.getDate() - 1) }
  if (index === 2 || index === 3) a.setDate(a.getDate() - (index === 2 ? 6 : 29))
  if (index === 4) a.setDate(1)
  if (index === 5) { a.setDate(1); a.setMonth(a.getMonth() - 1); b.setDate(0) }
  if (index === 6) { a.setDate(1); a.setMonth(a.getMonth() - 5) }
  if (index === 7) { a.setMonth(0, 1) }
  if (!allowed(day(a)) || !allowed(day(b))) return
  commit(day(a) + (props.type === 'datetime-local' ? 'T00:00' : ''), day(b) + (props.type === 'datetime-local' ? 'T23:59:59' : ''))
}
function input(event: Event, end = false) {
  const value = (event.target as HTMLInputElement).value
  if (end) emit('update:end', value); else emit('update:modelValue', value); emit('change', event)
}
function timeInput(event: Event, end = false) {
  const value = (event.target as HTMLInputElement).value, current = end ? finish.value : draft.value
  const next = props.type === 'time' ? value : `${current.slice(0, 10) || today()}T${value}`
  if (end) finish.value = next; else draft.value = next
}
const valid = computed(() => !!draft.value && (!props.range || (!!finish.value && draft.value <= finish.value)) && (!props.min || draft.value >= props.min) && (!props.max || (props.range ? finish.value : draft.value) <= props.max))
function keyboard(e: KeyboardEvent) {
  if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(true); return }
  const el = e.target as HTMLElement
  if (!el.dataset.day) return
  const offset = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 } as Record<string, number>)[e.key]
  if (!offset) return
  e.preventDefault(); const date = new Date(el.dataset.day + 'T12:00:00'); date.setDate(date.getDate() + offset)
  const key = day(date); const focus = () => panel.value?.querySelector<HTMLButtonElement>(`[data-day="${key}"]:not(:disabled)`)?.focus()
  if (panel.value?.querySelector(`[data-day="${key}"]:not(:disabled)`)) focus()
  else { month.value = new Date(date.getFullYear(), date.getMonth(), 1); void nextTick(focus) }
}
onBeforeUnmount(() => close())
</script>

<template>
  <span
    ref="root"
    class="date-picker"
    :class="{ 'date-picker--range': range, 'date-picker--datetime': type === 'datetime-local', 'is-disabled': disabled }"
  >
    <button
      ref="trigger"
      type="button"
      class="date-picker-icon"
      :disabled="disabled"
      :aria-label="type === 'time' ? '打开时间选择器' : '打开日期选择器'"
      :aria-expanded="open"
      @click="show"
    ><svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      width="16"
      height="16"
      fill="none"
      stroke="currentColor"
      stroke-width="1.6"
    ><rect
      x="3"
      y="5"
      width="18"
      height="16"
      rx="2"
    /><path d="M7 3v4m10-4v4M3 10h18m-13 4h3m3 0h3m-9 3h3" /></svg></button>
    <span
      class="date-picker-value"
      :class="{ 'is-empty': !modelValue }"
    >
      <input
        ref="first"
        v-bind="attrs"
        :value="modelValue"
        :type="type"
        :step="type === 'datetime-local' ? 1 : undefined"
        :disabled="disabled"
        :required="required"
        :min="min || undefined"
        :max="(range && end ? end : max) || undefined"
        :aria-label="range ? startLabel : String(attrs['aria-label'] || fieldLabel || (type === 'time' ? '时间' : '日期'))"
        @input="input($event)"
        @click.prevent="show"
        @keydown.alt.down.prevent="show"
      >
      <span
        v-if="!modelValue"
        class="date-picker-placeholder"
        aria-hidden="true"
      >{{ range ? startLabel : type === 'time' ? '选择时间' : '选择日期' }}</span>
    </span>
    <template v-if="range">
      <span class="date-picker-separator">～</span>
      <span
        class="date-picker-value"
        :class="{ 'is-empty': !end }"
      >
        <input
          ref="last"
          :value="end"
          :type="type"
          :step="type === 'datetime-local' ? 1 : undefined"
          :disabled="disabled"
          :required="required"
          :min="modelValue || min || undefined"
          :max="max || undefined"
          :aria-label="endLabel"
          @input="input($event, true)"
          @click.prevent="show"
          @keydown.alt.down.prevent="show"
        >
        <span
          v-if="!end"
          class="date-picker-placeholder"
          aria-hidden="true"
        >{{ endLabel }}</span>
      </span>
    </template>
    <button
      v-if="(modelValue || end) && !disabled"
      type="button"
      class="date-picker-clear"
      aria-label="清空日期"
      @click.prevent="commit('', '')"
    >×</button>
    <div
      v-if="open"
      ref="panel"
      popover="manual"
      class="date-picker-panel"
      :class="{ 'date-picker-panel--range': range }"
      :style="position"
      role="dialog"
      aria-label="日期选择"
      @keydown="keyboard"
      @click.stop
    >
      <aside
        v-if="range"
        class="date-picker-shortcuts"
      ><button
        v-for="(item, index) in presets"
        :key="item"
        type="button"
        @click.prevent="shortcut(index)"
      >{{ item }}</button></aside>
      <div class="date-picker-main">
        <div
          v-if="type !== 'time'"
          class="date-picker-months"
        >
          <section
            v-for="(m, index) in months"
            :key="index"
            class="date-picker-month"
          >
            <header><button
              type="button"
              aria-label="上一年"
              @click="move(-12)"
            >«</button><button
              type="button"
              aria-label="上个月"
              @click="move(-1)"
            >‹</button><strong>{{ m.getFullYear() }} 年 {{ m.getMonth()+1 }} 月</strong><button
              type="button"
              aria-label="下个月"
              @click="move(1)"
            >›</button><button
              type="button"
              aria-label="下一年"
              @click="move(12)"
            >»</button></header>
            <div class="date-picker-grid"><span
              v-for="w in ['日','一','二','三','四','五','六']"
              :key="w"
            >{{ w }}</span><button
              v-for="cell in cells(m)"
              :key="cell.key"
              type="button"
              :data-day="cell.key"
              :aria-label="cell.key"
              :aria-pressed="selected(cell.key)"
              :disabled="!allowed(cell.key)"
              :class="{ other: cell.other, selected: selected(cell.key), between: between(cell.key), today: cell.key === today() }"
              @mouseenter="hover = cell.key"
              @click.prevent="choose(cell.key)"
            >{{ cell.date }}</button></div>
          </section>
        </div>
        <div
          v-if="type !== 'date'"
          class="date-picker-time"
        ><label>{{ range ? '开始时间' : '时间' }}<input
          type="time"
          :step="type === 'datetime-local' ? 1 : 60"
          :value="type === 'time' ? draft : draft.split('T')[1]"
          @input="timeInput($event)"
        ></label><label v-if="range">结束时间<input
          type="time"
          :step="type === 'datetime-local' ? 1 : 60"
          :value="finish.split('T')[1]"
          @input="timeInput($event, true)"
        ></label></div>
        <footer><span v-if="range">{{ choosingEnd ? '请选择结束日期' : '请选择开始日期' }}</span><button
          v-if="!range && type !== 'time'"
          type="button"
          @click.prevent="choose(today())"
        >今天</button><button
          type="button"
          @click.prevent="close(true)"
        >取消</button><button
          v-if="type !== 'date'"
          type="button"
          class="confirm"
          :disabled="!valid"
          @click.prevent="commit()"
        >确定</button></footer>
      </div>
    </div>
  </span>
</template>

<style>
/* Explicit selectors isolate the picker from legacy form/button rules. Popover top layer avoids clipping inside dialogs and sticky filters. */
.date-picker { display:inline-flex; align-items:center; gap:4px; min-width:0; width:100%; min-height:var(--ui-control-height); padding:0 4px; border:1px solid #d9dee7; border-radius:4px; background:white; color:var(--ink); vertical-align:middle; }
.date-picker-value { position:relative; display:flex; flex:1; min-width:0; width:0; }
.date-picker-placeholder { position:absolute; inset:0 2px; display:flex; align-items:center; pointer-events:none; color:#9aa3b0; font-size:13px; font-weight:400; white-space:nowrap; }
#app .date-picker-value.is-empty > input:not(:focus) { color:transparent; }
.date-picker-value > input:focus + .date-picker-placeholder { display:none; }
.date-picker:focus-within { border-color:#3975db; box-shadow:0 0 0 2px #3975db18; }
#app form:not(.filter-panel):not(.audit-filters):not(.dashboard-filters):not(.monitor-filters) .date-picker:not(.date-picker--range) { min-height:40px; }
#app form:not(.filter-panel):not(.audit-filters):not(.dashboard-filters):not(.monitor-filters) .date-picker:not(.date-picker--range) .date-picker-value > input { height:38px; min-height:38px; }
.date-picker.is-disabled { background:#f5f6f8; opacity:.65; }
#app .date-picker .date-picker-value > input { flex:1; width:100%; min-width:0; height:30px; min-height:30px; padding:0 2px; border:0; border-radius:0; box-shadow:none; outline:0; background:transparent; color:inherit; font-size:13px; }
.date-picker input::-webkit-calendar-picker-indicator { display:none; }
#app .date-picker > button { flex:none; width:24px; min-width:24px; height:28px; min-height:28px; padding:0; margin:0; border:0; border-radius:3px; background:transparent; color:#8792a2; font-size:18px; box-shadow:none; cursor:pointer; }
.date-picker-separator { color:#8a94a5; font-size:12px; }
.date-picker--range { min-width:260px; }
.date-picker--datetime.date-picker--range { min-width:370px; }
.date-picker-panel { position:fixed; inset:auto; margin:0; padding:0; width:300px; max-width:calc(100vw - 16px); border:1px solid #dce1e9; border-radius:6px; background:white; color:#253047; box-shadow:0 6px 24px #17203326; z-index:1000; overflow:auto; font:13px/1.5 Inter,system-ui,sans-serif; }
.date-picker-panel:popover-open { display:flex; }
.date-picker-panel--range { width:690px; }
.date-picker-shortcuts { flex:0 0 100px; padding:10px 6px; border-right:1px solid #e7eaf0; }
.date-picker-panel button { color:#526075; background:white; border:0; border-radius:3px; margin:0; padding:4px 6px; min-height:28px; font:inherit; font-weight:400; cursor:pointer; box-shadow:none; }
.date-picker-panel button:hover { color:#2864c5; background:#edf4ff; }
.date-picker-panel button:focus-visible { outline:2px solid #2864c5; outline-offset:-2px; }
.date-picker-panel button:disabled { opacity:.35; cursor:not-allowed; }
.date-picker-shortcuts button { display:block; width:100%; text-align:left; white-space:nowrap; margin-bottom:4px; }
.date-picker-main { flex:1; min-width:0; }
.date-picker-months { display:flex; }
.date-picker-month { flex:1; min-width:0; padding:8px; }
.date-picker-month + .date-picker-month { border-left:1px solid #e7eaf0; }
.date-picker-month header { display:flex; align-items:center; gap:2px; margin-bottom:8px; }
.date-picker-month header strong { flex:1; text-align:center; white-space:nowrap; font-weight:600; }
.date-picker-month header button { padding:0; min-width:24px; font-size:18px; }
.date-picker-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:2px 0; text-align:center; }
.date-picker-grid > span { padding:4px 0 8px; font-size:12px; color:#7a8596; }
.date-picker-grid button { min-width:0; min-height:30px; padding:3px 0; }
.date-picker-grid .other { color:#acb3bf; }
.date-picker-grid .today { font-weight:700; color:#2864c5; }
.date-picker-grid .between { background:#edf4ff; border-radius:0; }
.date-picker-grid .selected { background:#2864c5; color:white; border-radius:4px; }
.date-picker-panel footer { display:flex; justify-content:flex-end; align-items:center; gap:8px; border-top:1px solid #e7eaf0; padding:6px 10px; }
.date-picker-panel footer span { margin-right:auto; color:#8892a1; font-size:12px; }
.date-picker-panel .confirm { background:#2864c5; color:white; }
.date-picker-time { display:flex; gap:10px; padding:8px 10px; }
.date-picker-time label { display:flex; flex:1; flex-wrap:wrap; gap:4px; min-width:0; font-size:12px; }
.date-picker-time input { width:100%; min-width:0; height:32px; border:1px solid #dce1e9; border-radius:4px; padding:4px; background:white; color:#253047; }
#app label:has(> .date-picker--range) { grid-column:span 2; flex:0 1 380px; min-width:0; }
@media(max-width:640px) { .date-picker-panel--range:popover-open { display:block; } .date-picker-shortcuts { display:flex; flex-wrap:wrap; gap:4px; border-right:0; border-bottom:1px solid #e7eaf0; padding:6px; } .date-picker-shortcuts button { width:auto; margin:0; font-size:12px; } .date-picker-months { flex-direction:column; } .date-picker-month + .date-picker-month { display:none; } .date-picker--datetime.date-picker--range { display:grid; grid-template-columns:24px minmax(0,1fr) 24px; } #app .date-picker--datetime.date-picker--range > .date-picker-value { grid-column:2; width:100%; } .date-picker--datetime.date-picker--range .date-picker-icon { grid-column:1; grid-row:1 / 3; } .date-picker--datetime.date-picker--range .date-picker-clear { grid-column:3; grid-row:1 / 3; } .date-picker--datetime.date-picker--range .date-picker-separator { display:none; } .date-picker-month + .date-picker-month { border-left:0; border-top:1px solid #e7eaf0; } .date-picker--range,.date-picker--datetime.date-picker--range { min-width:0; } #app label:has(> .date-picker--range) { grid-column:1 / -1; flex-basis:100%; } }
</style>
