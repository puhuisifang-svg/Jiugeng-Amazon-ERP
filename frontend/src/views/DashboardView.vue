<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { loadDashboardSummary, type DashboardSummaryItem } from '@/api/dashboard'

const summaries = ref<DashboardSummaryItem[]>([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const summary = await loadDashboardSummary()
    summaries.value = summary.items
  }
  catch {
    errorMessage.value = '模拟数据服务暂不可用，请确认后端已使用 mock profile 启动。'
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page-stack">
    <div class="notice">
      <strong>本地模拟数据模式</strong>
      <span>所有数字均为 SYNTHETIC 模拟数据，仅用于功能联调，不代表真实业务验收。</span>
    </div>

    <p
      v-if="loading"
      class="data-state"
    >
      正在加载模拟数据…
    </p>

    <p
      v-else-if="errorMessage"
      class="data-state data-state--error"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <div
      v-else
      class="summary-grid"
      aria-label="模拟业务摘要"
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
      <h2>本地前后端调用链已进入联调</h2>
      <p>当前仅接入模拟摘要；真实 API、IAM、数据库事务和业务样本仍按正式门禁验收。</p>
    </article>
  </section>
</template>
