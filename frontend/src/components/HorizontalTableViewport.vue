<script setup lang="ts">
import {nextTick,onBeforeUnmount,onMounted,ref} from 'vue'
import TableViewport from './TableViewport.vue'
const host=ref<HTMLElement>(),left=ref(0),maximum=ref(0)
let scroll:HTMLElement|undefined,observer:ResizeObserver|undefined
function sync(){if(!scroll)return;maximum.value=Math.max(0,scroll.scrollWidth-scroll.clientWidth);left.value=scroll.scrollLeft}
function move(value:number){if(!scroll)return;scroll.scrollLeft=Math.max(0,Math.min(value,maximum.value));sync()}
function input(event:Event){move(Number((event.target as HTMLInputElement).value))}
function step(direction:number){move(left.value+direction*Math.max(160,(scroll?.clientWidth??400)/2))}
onMounted(async()=>{await nextTick();scroll=host.value?.querySelector<HTMLElement>('.table-viewport')??undefined;scroll?.addEventListener('scroll',sync);if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(sync);if(scroll)observer.observe(scroll);const table=scroll?.querySelector('table');if(table)observer.observe(table)}window.addEventListener('resize',sync);sync()})
onBeforeUnmount(()=>{scroll?.removeEventListener('scroll',sync);observer?.disconnect();window.removeEventListener('resize',sync)})
</script>
<template>
  <div
    ref="host"
    class="horizontal-table-viewport"
  >
    <TableViewport :reserve-bottom="44">
      <slot />
    </TableViewport>
    <div
      v-if="maximum>0"
      class="horizontal-table-controls"
      aria-label="列表横向滚动"
    >
      <button
        type="button"
        aria-label="向左滚动列表"
        :disabled="left<=0"
        @click="step(-1)"
      >
        ‹
      </button>
      <input
        type="range"
        aria-label="左右滚动查看全部列"
        min="0"
        :max="maximum"
        :value="left"
        @input="input"
      >
      <button
        type="button"
        aria-label="向右滚动列表"
        :disabled="left>=maximum-1"
        @click="step(1)"
      >
        ›
      </button>
      <span>左右滚动查看全部列</span>
    </div>
  </div>
</template>
<style scoped>
.horizontal-table-viewport{min-width:0;max-width:100%;overflow:hidden}.horizontal-table-controls{display:flex;align-items:center;gap:8px;padding:6px 12px;background:#f6f8fc;border-top:1px solid #e1e7ef;min-height:40px;box-sizing:border-box}.horizontal-table-controls input[type=range]{width:0;flex:1;min-width:40px;min-height:24px;height:24px;padding:0;border:0;accent-color:#7188a7;cursor:ew-resize}.horizontal-table-controls button{width:28px;min-width:28px;height:28px;min-height:28px;padding:0;border:1px solid #dce2ea;border-radius:4px;background:white;color:#4c668a;font-size:22px}.horizontal-table-controls button:disabled{opacity:.35}.horizontal-table-controls span{font-size:12px;color:#718096;white-space:nowrap}@media(max-width:600px){.horizontal-table-controls span{display:none}}
</style>
