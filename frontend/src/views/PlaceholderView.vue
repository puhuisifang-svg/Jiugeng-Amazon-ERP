<script setup lang="ts">
defineProps<{ description: string }>()
</script>

<template>
  <article class="panel empty-state">
    <div class="placeholder-badge">
      模块占位
    </div>
    <h2>{{ description }}</h2>
    <p>此页面仅建立路由与模块边界；字段、交互和权限行为将在相应需求进入 Ready 后实现。</p>
  </article>
</template>
