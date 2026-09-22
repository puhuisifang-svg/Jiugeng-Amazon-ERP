<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { loadResource, type ResourceDto } from '@/api/resources'

interface Column { key: string; label: string }
const props = defineProps<{ endpoint: string; columns: Column[]; emptyText: string }>()

const items = ref<ResourceDto[]>([])
const loading = ref(true)
const errorMessage = ref('')

function cell(item: ResourceDto, key: string) {
  const value = (item as unknown as Record<string, unknown>)[key]
  return value === undefined || value === null || value === '' ? '—' : String(value)
}

onMounted(async () => {
  try {
    const response = await loadResource(props.endpoint)
    items.value = response.items
  }
  catch {
    errorMessage.value = '模拟数据加载失败，请确认本地后端已使用 mock profile 启动。'
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page-stack">
    <div
      class="notice"
      role="status"
    >
      <strong>模拟数据 · SYNTHETIC</strong>
      <span>以下内容只用于本地 MVP 演示，不代表真实店铺、库存或业务验收。</span>
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
    <article
      v-else
      class="panel table-panel"
    >
      <p
        v-if="!items.length"
        class="data-state"
      >
        {{ emptyText }}
      </p>
      <div
        v-else
        class="table-scroll"
      >
        <table class="data-table">
          <thead>
            <tr>
              <th
                v-for="column in columns"
                :key="column.key"
              >
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in items"
              :key="String(('id' in item && item.id) || index)"
            >
              <td
                v-for="column in columns"
                :key="column.key"
              >
                {{ cell(item, column.key) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
