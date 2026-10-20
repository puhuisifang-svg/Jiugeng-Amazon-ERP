<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { customersApi, type Customer, type CustomerProduct } from '@/api/customers'
import type { ShippingCustomerProduct } from '@/api/dashboard'
const props = defineProps<{ initial?: ShippingCustomerProduct | null | undefined; productName?: string; disabled?: boolean; editing?: boolean; labelPrefix?: string }>()
const emit = defineEmits<{ select: [value: CustomerProduct | undefined] }>()
const customers = ref<Customer[]>([]), products = ref<CustomerProduct[]>([])
const customerId = ref(props.initial?.customerId ?? ''), productId = ref(props.initial?.id ?? '')
const customerQuery = ref(''), productQuery = ref(''), customerPage = ref(0), productPage = ref(0)
const customerCount = ref(0), productCount = ref(0), busy = ref(false), error = ref('')
const selectedCustomer = ref<{ id: string; name: string } | undefined>(props.initial ? { id: props.initial.customerId, name: props.initial.customerName } : undefined)
const original = ref(props.initial)
let sequence = 0
async function load(kind: 'customer' | 'product', reset = false) {
  const current = ++sequence; busy.value = true; error.value = ''
  try {
    if (kind === 'customer') {
      if (reset) customerPage.value = 0
      const result = await customersApi.list({ q: customerQuery.value, status: 'ACTIVE', page: customerPage.value, size: 20 })
      if (current !== sequence) return
      customers.value = result.items; customerCount.value = result.totalCount
    } else {
      if (reset) productPage.value = 0
      if (!customerId.value) { products.value = []; productCount.value = 0; return }
      const result = await customersApi.products({ q: productQuery.value, customerId: customerId.value, status: 'ACTIVE', page: productPage.value, size: 20 })
      if (current !== sequence) return
      products.value = result.items; productCount.value = result.totalCount
    }
  } catch (exception) { if (current === sequence) error.value = exception instanceof Error ? exception.message : '客户商品加载失败，请重试。' }
  finally { if (current === sequence) busy.value = false }
}
async function changeCustomer() {
  selectedCustomer.value = customers.value.find(item => item.id === customerId.value) ?? selectedCustomer.value
  productId.value = ''; original.value = undefined; emit('select', undefined)
  products.value = []; productQuery.value = ''; await load('product', true)
}
function selectProduct() { emit('select', products.value.find(item => item.id === productId.value)) }
onMounted(async () => {
  await load('customer')
  if (!props.editing) {
    busy.value = true
    try {
      const value = await customersApi.defaultCustomer()
      if (value?.status === 'ACTIVE') { customerId.value = value.id; selectedCustomer.value = value }
      else error.value = '尚未设置默认客户，请选择客户或前往客户信息设置。'
    } catch { error.value = '默认客户加载失败，请手动选择客户。' }
    finally { busy.value = false }
  }
  if (customerId.value) await load('product', true)
})
</script>
<template>
  <fieldset
    class="shipment-customer-picker form-wide"
    :disabled="disabled || busy"
  >
    <legend>客户与客户商品</legend>
    <div class="picker-search">
      <input
        v-model="customerQuery"
        placeholder="搜索客户名称或账号"
        maxlength="128"
        @keydown.enter.prevent="load('customer', true)"
      ><button
        type="button"
        @click="load('customer', true)"
      >
        搜索客户
      </button>
    </div>
    <label>客户<select
      v-model="customerId"
      required
      :aria-label="`${labelPrefix ?? '发货'}客户`"
      @change="changeCustomer"
    ><option value="">请选择客户</option><option
      v-if="selectedCustomer && !customers.some(item => item.id === selectedCustomer?.id)"
      :value="selectedCustomer.id"
    >{{ selectedCustomer.name }}</option><option
      v-for="item in customers"
      :key="item.id"
      :value="item.id"
    >{{ item.name }}{{ item.defaultCustomer ? '（默认）' : '' }}</option></select></label>
    <div class="picker-pages">
      <button
        type="button"
        :disabled="customerPage === 0"
        @click="customerPage--; load('customer')"
      >
        上一页客户
      </button><small>{{ customerPage + 1 }} / {{ Math.max(1, Math.ceil(customerCount / 20)) }}</small><button
        type="button"
        :disabled="(customerPage + 1) * 20 >= customerCount"
        @click="customerPage++; load('customer')"
      >
        下一页客户
      </button>
    </div>
    <div class="picker-search">
      <input
        v-model="productQuery"
        placeholder="搜索客户产品编码、SKU或名称"
        maxlength="128"
        @keydown.enter.prevent="load('product', true)"
      ><button
        type="button"
        @click="load('product', true)"
      >
        搜索客户商品
      </button>
    </div>
    <label>客户商品<select
      v-model="productId"
      required
      :aria-label="`${labelPrefix ?? '发货'}客户商品`"
      @change="selectProduct"
    ><option value="">请选择该客户的商品</option><option
      v-if="original && !products.some(item => item.id === original?.id)"
      :value="original.id"
    >{{ productName }} · {{ original.customerProductCode || '未填客户编码' }}（原记录）</option><option
      v-for="item in products"
      :key="item.id"
      :value="item.id"
    >{{ item.productName }} · SKU：{{ item.skuCode }} · 客户编码：{{ item.customerProductCode || '未填' }}</option></select></label>
    <div class="picker-pages">
      <button
        type="button"
        :disabled="productPage === 0"
        @click="productPage--; load('product')"
      >
        上一页商品
      </button><small>{{ productPage + 1 }} / {{ Math.max(1, Math.ceil(productCount / 20)) }}</small><button
        type="button"
        :disabled="(productPage + 1) * 20 >= productCount"
        @click="productPage++; load('product')"
      >
        下一页商品
      </button>
    </div>
    <small v-if="customerId && !productCount && !busy">该客户暂无启用商品，请先在客户商品中关联 SKU。</small>
    <p
      v-if="error"
      role="alert"
      class="data-state--error"
    >
      {{ error }}
    </p>
  </fieldset>
</template>
<style scoped>
.shipment-customer-picker { min-width: 0; display: grid; gap: 8px; border: 1px solid #dbe1ea; border-radius: 8px; padding: 12px; }
.picker-search, .picker-pages { display: flex; gap: 8px; align-items: center; }
.picker-search input { min-width: 0; flex: 1; }
.picker-search button { flex-shrink: 0; }
.picker-pages { justify-content: space-between; flex-wrap: wrap; }
.picker-pages button { min-height: 28px; font-size: 12px; }
</style>
