<script setup lang="ts">
const summaries = [
  { label: '待处理导入', value: '—', note: '等待后端契约' },
  { label: '库存预警', value: '—', note: '等待业务数据' },
  { label: '待复核新品', value: '—', note: '等待监控结果' },
]
</script>

<template>
  <section class="page-stack">
    <div class="notice">
      <strong>Sprint 0 管理端壳</strong>
      <span>当前仅验证信息架构、布局和工程链路，不包含正式业务数据或功能。</span>
    </div>

    <div
      class="summary-grid"
      aria-label="业务摘要占位"
    >
      <article
        v-for="item in summaries"
        :key="item.label"
        class="summary-card"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </div>

    <article class="panel empty-state">
      <div class="empty-state__icon">
        ◎
      </div>
      <h2>待办与异常摘要将在联调后展示</h2>
      <p>页面会在 UI 信息架构定稿后增量调整，路由模块边界保持稳定。</p>
    </article>
  </section>
</template>
