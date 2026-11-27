<script setup lang="ts">
import type { ProductHistoryDto } from '@/api/domain'
import { productActionLabel, productHistoryChanges } from '@/utils/productHistory'
import type { PurchaseLine } from '@/api/purchases'
import { purchaseHistoryChanges } from '@/utils/purchaseHistory'
import { materialHistoryChanges } from '@/utils/materialHistory'
import { beijingDateTime } from '@/utils/dateTime'
import TableViewport from './TableViewport.vue'
import ProductUiState from './ProductUiState.vue'
const props = defineProps<{ entries: ProductHistoryDto[]; loading?: boolean; error?: string; material?: boolean; purchase?: boolean; purchaseLines?: PurchaseLine[] }>()
defineEmits<{ retry: [] }>()
function changes(event:ProductHistoryDto){return props.purchase?purchaseHistoryChanges(event,props.purchaseLines):props.material?materialHistoryChanges(event):productHistoryChanges(event).map(change=>({...change,initial:false}))}
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
    <table
      class="data-table product-ledger-table product-history-table"
      :class="{ 'purchase-business-history': purchase }"
    >
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
            <p v-if="(!material && !purchase) || !changes(event).length">
              {{ purchase ? '无可展示的业务字段变更' : event.detail || '—' }}
            </p>
            <dl
              v-if="changes(event).length"
              class="product-history-changes"
            >
              <template
                v-for="change in changes(event)"
                :key="change.key"
              >
                <dt>{{ change.label }}</dt><dd>
                  <span
                    v-if="!change.initial"
                    class="product-history-before"
                  >{{ change.before }}</span><span
                    v-if="!change.initial"
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

<style scoped>
.purchase-business-history td{vertical-align:top!important;white-space:normal;padding:10px 12px!important}
.purchase-business-history td:first-child{width:150px;white-space:nowrap}
.purchase-business-history td:nth-child(2){width:105px;white-space:nowrap}
.purchase-business-history .product-history-changes{display:grid;grid-template-columns:minmax(70px,160px) minmax(0,1fr);gap:6px 14px;margin:0;font-size:12px;line-height:1.7}
.purchase-business-history .product-history-changes :is(dt,dd){margin:0;min-width:0;overflow-wrap:anywhere}
.purchase-business-history dt{color:#748197}
.purchase-business-history dd{display:flex;align-items:baseline;gap:4px 8px;flex-wrap:wrap;white-space:pre-wrap}
.purchase-business-history strong{font-weight:500;color:#243247}
.purchase-business-history .product-history-before{color:#8a94a5}
.purchase-business-history .product-history-arrow{color:#99a3b3}
</style>
