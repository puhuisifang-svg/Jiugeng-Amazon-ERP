<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { confirmAction } from '@/state/uiConfirmation'
defineOptions({inheritAttrs:false})
const props=defineProps<{dirty?:boolean;busy?:boolean;actionsVisible?:boolean}>()
onBeforeRouteLeave(async()=>!props.busy&&(!props.dirty||await confirmAction('仓库资料尚未保存，确定放弃修改并离开？')))
function beforeUnload(event:BeforeUnloadEvent){if(props.dirty){event.preventDefault();event.returnValue=''}}
onMounted(()=>window.addEventListener('beforeunload',beforeUnload))
onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload))
</script>
<template>
  <section class="page-stack warehouse-page">
    <article class="panel warehouse-page-content">
      <slot />
    </article>
    <footer
      v-if="actionsVisible"
      class="panel ui-dialog-actions warehouse-page-actions"
    >
      <slot name="footer" />
    </footer>
  </section>
</template>
<style scoped>
.warehouse-page-content{padding:10px;min-width:0}.warehouse-page-actions{position:sticky;bottom:0;display:flex;justify-content:flex-end;gap:8px;padding:8px 12px;background:white;z-index:4}
</style>
