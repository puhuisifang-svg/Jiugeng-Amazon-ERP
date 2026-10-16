<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const viewport = ref<HTMLElement>()
const height = ref('55vh')
let observer: ResizeObserver | undefined
let frame = 0

function measure() {
  const element = viewport.value
  if (!element) return
  const boundary = element.closest<HTMLElement>('.operation-dialog__card, dialog, .erp-content')
  const bottom = Math.min(window.innerHeight, boundary?.getBoundingClientRect().bottom ?? window.innerHeight)
  // Ignore outer scrolling: scrolling the page must not resize the table under the pointer.
  const top = element.getBoundingClientRect().top + (boundary?.scrollTop ?? 0)
  height.value = `${Math.max(180, bottom - top - 72)}px`
}
function schedule() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(measure)
}
onMounted(() => {
  measure()
  window.addEventListener('resize', schedule)
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(schedule)
    // Ancestors also change when filters wrap, notices appear or a dialog opens.
    for (let parent = viewport.value?.parentElement; parent; parent = parent.parentElement) {
      observer.observe(parent)
    }
  }
})
onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(frame)
  window.removeEventListener('resize', schedule)
})
</script>

<template>
  <div
    ref="viewport"
    class="table-viewport"
    :style="{ maxHeight: height }"
    tabindex="0"
    role="region"
    aria-label="可滚动数据列表"
  >
    <slot />
  </div>
</template>
