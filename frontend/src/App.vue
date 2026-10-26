<script setup lang="ts">
import ReleaseUpdateNotice from './components/ReleaseUpdateNotice.vue'
import UiConfirmationHost from './components/UiConfirmationHost.vue'
</script>
<template>
  <RouterView />
  <ReleaseUpdateNotice />
  <UiConfirmationHost />
</template>
