<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
defineOptions({ inheritAttrs: false })
const pagination = ref<HTMLElement>()
const dock = ref<HTMLElement>()
onMounted(() => {
  const element = pagination.value
  if (!element || element.closest('dialog, [role="dialog"], .operation-dialog')) return
  const shell = element.closest('.erp-shell')
  if (element.closest('.erp-content')) dock.value = shell?.querySelector<HTMLElement>('#workspace-pagination') ?? undefined
})
const props = withDefaults(defineProps<{ total: number; page: number; pageSize: number; disabled?: boolean; sizes?: number[] }>(), { disabled: false, sizes: () => [10, 20, 50, 100] })
const emit = defineEmits<{ 'update:page': [number]; 'update:pageSize': [number] }>()
const pages = computed(() => Math.max(1, Math.ceil(Math.max(0, props.total) / Math.max(1, props.pageSize))))
const current = computed(() => Math.min(pages.value, Math.max(1, props.page)))
const jump = ref(String(current.value))
watch(current, value => { jump.value = String(value) })
const numbers = computed(() => {
  const values = new Set([1, pages.value, current.value - 1, current.value, current.value + 1])
  if (current.value <= 3) [2, 3, 4].forEach(value => values.add(value))
  if (current.value >= pages.value - 2) [pages.value - 3, pages.value - 2, pages.value - 1].forEach(value => values.add(value))
  const sorted = [...values].filter(value => value >= 1 && value <= pages.value).sort((a, b) => a - b)
  const result: (number | string)[] = []
  sorted.forEach((value, index) => { if (index && value - sorted[index - 1]! > 1) result.push(`gap-${value}`); result.push(value) })
  return result
})
function go(value: number) {
  if (props.disabled || !Number.isInteger(value)) return
  const target = Math.min(pages.value, Math.max(1, value))
  jump.value = String(target)
  if (target !== current.value) emit('update:page', target)
}
function jumpTo() {
  if (!/^\d+$/.test(jump.value.trim())) { jump.value = String(current.value); return }
  go(Number(jump.value))
}
function resize(event: Event) {
  if (props.disabled) return
  const value = Number((event.target as HTMLSelectElement).value)
  if (!props.sizes.includes(value) || value === props.pageSize) return
  emit('update:pageSize', value)
  emit('update:page', 1)
}
</script>

<template>
  <Teleport
    :to="dock ?? 'body'"
    :disabled="!dock"
  >
    <nav
      ref="pagination"
      v-bind="$attrs"
      class="pagination list-pagination"
      aria-label="列表分页"
    >
      <div class="list-pagination__summary">
        <slot name="summary">
          共 {{ total }} 条
        </slot>
      </div>
      <div class="list-pagination__controls">
        <button
          type="button"
          :disabled="disabled || current <= 1"
          aria-label="上一页"
          @click="go(current - 1)"
        >
          <span aria-hidden="true">‹</span>
        </button>
        <template
          v-for="number in numbers"
          :key="number"
        >
          <button
            v-if="typeof number === 'number'"
            type="button"
            :disabled="disabled"
            :aria-label="`第 ${number} 页`"
            :aria-current="number === current ? 'page' : undefined"
            @click="go(number)"
          >
            {{ number }}
          </button>
          <span
            v-else
            class="list-pagination__ellipsis"
            aria-hidden="true"
          >…</span>
        </template>
        <button
          type="button"
          :disabled="disabled || current >= pages"
          aria-label="下一页"
          @click="go(current + 1)"
        >
          <span aria-hidden="true">›</span>
        </button>
        <select
          :value="pageSize"
          aria-label="每页条数"
          :disabled="disabled || sizes.length < 2"
          @change="resize"
        >
          <option
            v-for="size in sizes"
            :key="size"
            :value="size"
          >
            {{ size }} 条/页
          </option>
        </select>
        <span class="sr-only">{{ current }} / {{ pages }}</span>
        <label class="list-pagination__jump">前往<input
          v-model="jump"
          inputmode="numeric"
          aria-label="跳转页码"
          :disabled="disabled || pages <= 1"
          @keydown.enter.prevent="jumpTo"
          @change="jumpTo"
        >页</label>
      </div>
    </nav>
  </Teleport>
</template>

<style scoped>
.pagination.list-pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px 16px; flex-wrap: wrap; min-height: 49px; padding: 8px 12px; border-top: 1px solid var(--line); background: white; font-size: 13px; color: var(--muted); }
.list-pagination__summary { min-width: 0; overflow-wrap: anywhere; }
.list-pagination__controls { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 6px; margin-left: auto; }
.pagination.list-pagination button { display: inline-grid; place-items: center; min-width: 32px; min-height: 32px; padding: 0 8px; border: 1px solid transparent; border-radius: 4px; background: transparent; color: var(--ink); font-size: 13px; cursor: pointer; }
.pagination.list-pagination button:hover:not(:disabled) { color: #a64b0b; background: var(--accent-soft); }
.pagination.list-pagination button[aria-current="page"] { color: #a64b0b; background: var(--accent-soft); font-weight: 700; }
.pagination.list-pagination button:disabled { color: #a0a7b3; background: transparent; cursor: not-allowed; }
.pagination.list-pagination :is(input, select) { height: 32px; min-height: 32px; padding: 0 8px; border: 1px solid var(--line); border-radius: 4px; background: white; color: var(--ink); font-size: 13px; }
.pagination.list-pagination select { width: auto; max-width: 120px; }
.list-pagination__jump { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.pagination.list-pagination input { width: 48px; text-align: center; }
.list-pagination__ellipsis { padding-inline: 2px; }
@media (max-width: 640px) { .list-pagination__controls { justify-content: flex-start; margin-left: 0; width: 100%; } }
</style>
