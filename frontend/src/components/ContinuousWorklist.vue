<script setup lang="ts" generic="T extends { id: string }">
import { computed, onBeforeUnmount, onMounted, ref, useSlots } from 'vue'

const props = defineProps<{ items: T[]; label: string }>()
const slots = useSlots(); const paused = ref(false); const reducedMotion = ref(false); const pageHidden = ref(false)
const needsScroll = computed(() => props.items.length > 6 && !reducedMotion.value)
const animatedItems = computed(() => needsScroll.value ? [...props.items, ...props.items] : props.items)
const animationDuration = computed(() => `${Math.max(18, props.items.length * 4)}s`)
let resumeTimer: number | undefined
function pause() { window.clearTimeout(resumeTimer); paused.value = true }
function resumeSoon() { window.clearTimeout(resumeTimer); resumeTimer = window.setTimeout(() => { paused.value = false }, 3000) }
function visibilityChanged() { pageHidden.value = document.hidden }
let media: MediaQueryList | undefined
function mediaChanged(event: MediaQueryListEvent | MediaQueryList) { reducedMotion.value = event.matches }
onMounted(() => {
  media = window.matchMedia('(prefers-reduced-motion: reduce)'); mediaChanged(media)
  media.addEventListener('change', mediaChanged); document.addEventListener('visibilitychange', visibilityChanged)
})
onBeforeUnmount(() => {
  window.clearTimeout(resumeTimer); media?.removeEventListener('change', mediaChanged)
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<template>
  <div
    class="worklist-viewport"
    :class="{ 'worklist-viewport--static': !needsScroll }"
    :aria-label="label"
    tabindex="0"
    @mouseenter="pause"
    @mouseleave="resumeSoon"
    @focusin="pause"
    @focusout="resumeSoon"
    @wheel="pause"
  >
    <div
      class="worklist-track"
      :class="{ 'worklist-track--animated': needsScroll, 'worklist-track--paused': paused || pageHidden }"
      :style="{ '--worklist-duration': animationDuration }"
    >
      <article
        v-for="(item, index) in animatedItems"
        :key="`${item.id}-${index >= items.length ? 'clone' : 'primary'}`"
        class="worklist-row"
        :aria-hidden="index >= items.length"
      >
        <slot
          v-if="slots.default"
          :item="item"
        />
      </article>
    </div>
  </div>
</template>
