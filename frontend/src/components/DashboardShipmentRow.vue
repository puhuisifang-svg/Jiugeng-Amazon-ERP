<script setup lang="ts">
import { ref, watch } from 'vue'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import type { TodayShipment, PendingShipment } from '@/api/dashboard'
import ShipmentProductImage from './ShipmentProductImage.vue'
import ShipmentFiles from './ShipmentFiles.vue'
import ShipmentManuals from './ShipmentManuals.vue'
const props = defineProps<{ item: TodayShipment | PendingShipment; scope: 'today-shipments' | 'pending-shipments'; selected?: boolean; selectable?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ select: [id: string] }>()
const product = ref<ProductDetailDto>(); const error = ref('')
watch(() => props.item.productId, async (id, _old, cleanup) => {
  product.value = undefined; error.value = ''; let active = true; cleanup(() => { active = false })
  try { const result = await domainApi.product(id); if (active) product.value = result }
  catch { if (active) error.value = '产品详情加载失败，请刷新重试。' }
}, { immediate: true })
function date(value: string) { return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date(value)) }
</script>
<template>
  <tr :class="{ 'shipment-row--selected': selected }">
    <td
      v-if="scope === 'today-shipments' && selectable"
      class="shipment-select-cell"
    >
      <input
        type="checkbox"
        name="today-dispatch"
        :aria-label="`选择发货：${item.productName}`"
        :checked="selected"
        :disabled="disabled || !item.variantId || !item.customerProduct"
        @change="emit('select', item.id)"
      >
    </td>
    <td
      v-if="scope === 'pending-shipments'"
      class="shipment-date-cell"
    >
      {{ 'plannedShipDate' in item ? item.plannedShipDate : '' }}<small class="shipment-deadline">最晚 {{ date(item.latestShipAt) }}</small>
    </td>
    <td>
      <ShipmentProductImage
        v-if="product"
        :product-id="item.productId"
        :variant-id="item.variantId"
        :product="product"
        :alt="item.productName"
      /><span
        v-else
        class="muted"
      >{{ error ? '图片不可用' : '加载中…' }}</span>
    </td>
    <td class="shipment-product-cell">
      <strong>{{ item.productName }}</strong><small v-if="item.customerProduct">客户：{{ item.customerProduct.customerName }} · 客户编码：{{ item.customerProduct.customerProductCode || '未填' }}</small><small v-else>历史记录 · 未绑定客户商品</small><small>{{ item.variantId ? `SKU：${item.productCode}` : '历史父体记录 · 待补选 SKU' }}</small><small
        v-if="error"
        role="alert"
      >{{ error }}</small>
    </td>
    <td class="shipment-quantity">
      {{ item.quantity.toLocaleString('zh-CN') }} <small>套</small>
    </td>
    <td class="shipment-quantity">
      {{ item.boxCount }} <small>箱</small>
    </td>
    <td class="shipment-remark">
      {{ item.remark || '—' }}<small v-if="item.customerProduct?.requirements">特殊需求：{{ item.customerProduct.requirements }}</small>
    </td>
    <td>
      <ShipmentFiles
        :key="`${item.id}:${item.variantId}:${item.customerProduct?.id}:label`"
        :shipment-id="item.id"
        :scope="scope"
        kind="LABEL"
      />
    </td>
    <td>
      <ShipmentFiles
        :key="`${item.id}:${item.variantId}:${item.customerProduct?.id}:carton`"
        :shipment-id="item.id"
        :scope="scope"
        kind="CARTON_LABEL"
      />
    </td>
    <td>
      <ShipmentManuals
        v-if="product"
        :product="product"
        :variant-id="item.variantId"
      /><span
        v-else
        class="muted"
      >{{ error ? '无法读取说明书' : '加载中…' }}</span>
    </td>
  </tr>
</template>
