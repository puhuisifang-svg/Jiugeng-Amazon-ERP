<script setup lang="ts">
import '@/styles/productManagement.css'
import ProductUiState from './ProductUiState.vue'
import { computed, onMounted, ref } from 'vue'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { apiRequest } from '@/api/client'
import { session } from '@/state/session'
import { beijingDateTime } from '@/utils/dateTime'
interface Deleted { kind: string; id: string; productId: string; name: string; parentName: string; version: number; deletedAt: string }
const emit=defineEmits<{ close: []; changed: [] }>()
const rows=ref<Deleted[]>([]), page=ref(1), total=ref(0), loading=ref(false), busy=ref(false), error=ref(''), notice=ref(''), target=ref<Deleted>()
const restoreError=ref('')
const writable=computed(()=>session.canAccess(['products.write','products.delete']))
async function load(){loading.value=true;error.value='';try{const result=await apiRequest<{items:Deleted[];total:number;page:number}>(`/v1/products/deleted?page=${page.value-1}&size=20`);rows.value=result.items;total.value=result.total;page.value=result.page+1}catch(e){error.value=e instanceof Error?e.message:'加载失败'}finally{loading.value=false}}
async function restore(){if(!target.value||busy.value)return;busy.value=true;restoreError.value='';try{await apiRequest(`/v1/products/deleted/${target.value.kind}/${encodeURIComponent(target.value.id)}/restore`,{method:'POST',body:JSON.stringify({version:target.value.version})});notice.value=`${target.value.name} 已恢复`;target.value=undefined;emit('changed');await load()}catch(e){restoreError.value=e instanceof Error?e.message:'恢复失败，资料未更改'}finally{busy.value=false}}
onMounted(load)
</script>
<template>
  <AccessDialog
    title="已删除的产品与子体"
    dialog-class="product-ui product-restore-dialog"
    wide
    :busy="busy"
    @close="emit('close')"
  >
    <p class="product-feedback restore-hint">
      先恢复父体，再恢复子体。恢复保留原有资料、库存和历史；已删除的客户商品需重新关联。
    </p>
    <p
      v-if="notice"
      role="status"
      class="product-feedback product-feedback--success"
    >
      {{ notice }}
    </p>
    <ProductUiState
      v-if="loading"
      loading
      title="正在加载已删除记录…"
    />
    <ProductUiState
      v-else-if="error"
      error
      title="已删除记录加载失败"
      :description="error"
      @retry="load"
    />
    <TableViewport v-else-if="rows.length">
      <table class="data-table product-ledger-table restore-table">
        <thead><tr><th>名称 / 所属父体</th><th>类型</th><th>删除时间</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.kind+row.id"
          >
            <td><span class="restore-name">{{ row.name }}</span><small v-if="row.kind === 'SKU'">所属父体 · {{ row.parentName }}</small></td><td><span class="restore-type">{{ row.kind === 'PRODUCT' ? '父体' : '子体' }}</span></td><td>{{ beijingDateTime(row.deletedAt) }}</td><td>
              <button
                v-if="writable"
                :disabled="busy"
                class="product-text-button"
                @click="target=row; restoreError=''"
              >
                恢复
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ProductUiState
      v-else
      title="暂无已删除记录。"
      description="删除的父体和子体会显示在这里，可按需恢复。"
    />
    <template #pagination>
      <ListPagination
        :page="page"
        :page-size="20"
        :sizes="[20]"
        :total="total"
        :disabled="loading || busy"
        @update:page="page=$event;load()"
      />
    </template>
    <AccessDialog
      v-if="target"
      title="确认恢复"
      dialog-class="product-ui"
      :busy="busy"
      @close="target=undefined"
    >
      <p>恢复“{{ target.name }}”及其原有资料？</p><p class="product-inline-note">
        {{ target.kind === 'SKU' ? `所属父体：${target.parentName}。请先确保父体已恢复。` : '恢复父体后，可继续恢复其子体。' }} 已删除的客户商品需重新关联。
      </p><p
        v-if="restoreError"
        role="alert"
        class="product-feedback product-feedback--error"
      >
        {{ restoreError }}
      </p><template #footer>
        <button
          :disabled="busy"
          @click="target=undefined"
        >
          取消
        </button><button
          class="primary-button"
          :disabled="busy"
          @click="restore"
        >
          {{ busy?'恢复中…':'确认恢复' }}
        </button>
      </template>
    </AccessDialog>
  </AccessDialog>
</template>
<style scoped>
.restore-hint{font-size:12px;color:var(--muted);margin:0 0 8px}.data-table th,.data-table td{padding:6px 10px;font-size:13px}.data-table td:first-child{white-space:normal;overflow-wrap:anywhere;min-width:140px}
</style>
