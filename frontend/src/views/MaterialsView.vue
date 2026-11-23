<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { runtimeConfig } from '@/config/runtime'
import MaterialsManagement from './MaterialsManagement.vue'
const MaterialsMockView=defineAsyncComponent(()=>import('./MaterialsMockView.vue'))
</script>
<template><MaterialsMockView v-if="runtimeConfig.authMode==='mock'" /><MaterialsManagement v-else /></template>
