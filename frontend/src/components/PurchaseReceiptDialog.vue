<script setup lang="ts">
import {ref} from 'vue'
import type { PurchaseOrder } from '@/api/purchases'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
defineProps<{order:PurchaseOrder;quantities:Record<string,number|''>;remark:string;receiptError:string;locked:boolean;saving:boolean;pending:boolean;dirty?:boolean}>()
const dialog=ref<InstanceType<typeof AccessDialog>>()
const emit=defineEmits<{close:[];submit:[];quantity:[id:string,value:string|number];remark:[value:string]}>()
function purchaseName(order:PurchaseOrder){return (order.lines[0]?.itemName || '未命名采购')+(order.lines.length>1?` 等 ${order.lines.length} 项`:'')}
</script>
<template>
  <AccessDialog
    ref="dialog"
    title="确认采购收货"
    wide
    :busy="saving"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <div class="receipt-summary">
      <strong>{{ purchaseName(order) }}</strong><span>{{ order.supplierName }}</span><span>{{ order.deliveryAddress || '未填写收货地址' }}</span>
    </div>
    <p class="receipt-hint">
      核对实物后填写本次实收数量，未到货的项目留空。确认后登记收货；产品和辅料采购同步计入对应库存；日常物品只登记收货。
    </p>
    <slot />
    <TableViewport>
      <table class="data-table receipt-confirm-table">
        <thead><tr><th>图片</th><th>商品</th><th>采购</th><th>已收</th><th>待收</th><th>备注</th><th>本次实收</th><th>操作</th></tr></thead>
        <tbody>
          <tr
            v-for="line in order.lines.filter(item => item.remainingQuantity > 0)"
            :key="line.id"
          >
            <td>
              <PurchaseItemImage
                :image-asset-id="line.imageAssetId"
                :image-url="line.imageUrl"
                :alt="line.itemName"
              />
            </td>
            <td>{{ line.itemName }}<small v-if="line.specification">{{ line.specification }}</small></td><td>{{ line.orderedQuantity }} {{ line.unit }}</td><td>{{ line.receivedQuantity }}</td><td>{{ line.remainingQuantity }}</td><td class="receipt-item-remark">
              {{ line.remark || '—' }}
            </td>
            <td>
              <input
                :value="quantities[line.id]"
                type="number"
                min="0"
                :max="line.remainingQuantity"
                step="0.0001"
                :aria-label="`${line.itemName}本次实收`"
                :disabled="locked"
                placeholder="填写实收数量"
                @input="emit('quantity',line.id,($event.target as HTMLInputElement).value)"
              >
            </td><td>
              <button
                type="button"
                class="secondary-button"
                :disabled="locked"
                @click="emit('quantity',line.id,line.remainingQuantity)"
              >
                全部接收
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <label class="receipt-remark">收货备注<input
      :value="remark"
      :disabled="locked"
      maxlength="500"
      placeholder="可选，如外包装情况、少货说明"
      @input="emit('remark',($event.target as HTMLInputElement).value)"
    ></label>
    <p
      v-if="receiptError"
      role="alert"
      class="error-message"
    >
      {{ receiptError }}
    </p>
    <template #footer>
      <button
        type="button"
        class="secondary-button"
        :disabled="saving"
        @click="dialog?.requestClose()"
      >
        取消
      </button><button
        type="button"
        class="primary-button"
        :disabled="saving"
        @click="emit('submit')"
      >
        {{ saving ? '确认中…' : pending ? '重试本次收货' : '确认本次收货' }}
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.receipt-summary { display:flex; flex-wrap:wrap; gap:8px 20px; font-size:13px; }
.receipt-hint { color:var(--muted); font-size:13px; }
.receipt-confirm-table { min-width:800px; }
.receipt-confirm-table .receipt-item-remark{min-width:120px;max-width:220px;color:#2864c5;white-space:pre-wrap;overflow-wrap:anywhere;text-align:left}
.receipt-confirm-table input { width:140px; }
.receipt-remark { display:flex; gap:10px; align-items:center; margin-top:12px; font-size:13px; }
.receipt-remark input { flex:1; min-width:0; height:36px;box-sizing:border-box;border:1px solid var(--line);border-radius:4px;padding:7px 10px;font:inherit;background:white;color:var(--ink); }

.receipt-confirm-table small{display:block;color:var(--muted);margin-top:4px}.receipt-confirm-table td{vertical-align:middle}.receipt-confirm-table .secondary-button{white-space:nowrap}.receipt-summary{padding:10px 12px;background:#f7f9fc;border:1px solid var(--line);border-radius:4px}
</style>
