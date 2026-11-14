<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { installModalKeyboard } from './utils/modalKeyboard'
import ReleaseUpdateNotice from './components/ReleaseUpdateNotice.vue'
import UiConfirmationHost from './components/UiConfirmationHost.vue'
let removeModalKeyboard: (() => void) | undefined
onMounted(() => { removeModalKeyboard = installModalKeyboard() })
onBeforeUnmount(() => removeModalKeyboard?.())
</script>
<template>
  <RouterView />
  <ReleaseUpdateNotice />
  <UiConfirmationHost />
</template>
