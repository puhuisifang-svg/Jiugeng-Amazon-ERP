<script setup lang="ts">
import type { ProductHistoryDto } from '@/api/domain'
import { productActionLabel, productHistoryChanges } from '@/utils/productHistory'
import { beijingDateTime } from '@/utils/dateTime'
import TableViewport from './TableViewport.vue'
import ProductUiState from './ProductUiState.vue'
defineProps<{ entries: ProductHistoryDto[]; loading?: boolean; error?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <ProductUiState
    v-if="loading"
    loading
    title="正在加载操作记录…"
  />
  <ProductUiState
    v-else-if="error"
    error
    title="操作记录加载失败"
    :description="error"
    @retry="$emit('retry')"
  />
  <ProductUiState
    v-else-if="!entries.length"
    title="暂无操作记录"
    description="资料变更后，可在这里查看操作人、时间及变更内容。"
  />
  <TableViewport v-else>
    <table class="data-table product-ledger-table product-history-table">
      <thead><tr><th>时间 / 操作人</th><th>操作</th><th>变更内容</th></tr></thead>
      <tbody>
        <tr
          v-for="event in entries"
          :key="event.id"
        >
          <td>{{ beijingDateTime(event.occurredAt) }}<small>{{ event.actorDisplayName || event.actorLoginName }}</small></td>
          <td :title="event.action">
            {{ productActionLabel(event.action) }}
          </td>
          <td class="product-history-detail">
            <p>{{ event.detail || '—' }}</p>
            <dl
              v-if="productHistoryChanges(event).length"
              class="product-history-changes"
            >
              <template
                v-for="change in productHistoryChanges(event)"
                :key="change.key"
              >
                <dt>{{ change.label }}</dt><dd>
                  <span class="product-history-before">{{ change.before }}</span><span
                    class="product-history-arrow"
                    aria-label="变更为"
                  >→</span><strong>{{ change.after }}</strong>
                </dd>
              </template>
            </dl>
          </td>
        </tr>
      </tbody>
    </table>
  </TableViewport>
</template>
