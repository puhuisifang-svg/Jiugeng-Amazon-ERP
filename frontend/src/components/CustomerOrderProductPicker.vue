<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { customersApi, type CustomerProduct } from '@/api/customers'
import CustomerProductImage from './CustomerProductImage.vue'
import ListPagination from './ListPagination.vue'
const props=defineProps<{customerId:string; existing:string[]}>()
const emit=defineEmits<{confirm:[products:CustomerProduct[]];close:[]}>()
const dialog=ref<HTMLDialogElement>(), rows=ref<CustomerProduct[]>([]), selected=ref<CustomerProduct[]>([])
const query=ref(''), applied=ref(''), page=ref(1), size=ref(20), total=ref(0), busy=ref(false), error=ref('')
let sequence=0
async function load(){const current=++sequence;busy.value=true;error.value='';try{const result=await customersApi.products({customerId:props.customerId,q:applied.value,status:'ACTIVE',page:page.value-1,size:size.value});if(current===sequence){rows.value=result.items;total.value=result.totalCount}}catch(e){if(current===sequence){rows.value=[];error.value=e instanceof Error?e.message:'商品加载失败'}}finally{if(current===sequence)busy.value=false}}
function toggle(product:CustomerProduct,checked:boolean){selected.value=selected.value.filter(p=>p.id!==product.id);if(checked)selected.value.push(product)}
function search(){applied.value=query.value.trim();page.value=1;void load()}
function resize(value:number){size.value=value;page.value=1;void load()}
onMounted(()=>{dialog.value?.showModal();void load()})
</script>
<template>
  <dialog
    ref="dialog"
    class="customer-product-picker"
    aria-label="选择客户商品"
    @cancel.prevent="emit('close')"
    @keydown.esc.stop
  >
    <header>
      <h2>选择客户商品</h2><button
        type="button"
        aria-label="关闭商品选择"
        @click="emit('close')"
      >
        ×
      </button>
    </header>
    <form
      class="picker-search"
      @submit.prevent="search"
    >
      <input
        v-model="query"
        aria-label="搜索客户商品"
        placeholder="品名、SKU或客户商品编码"
      ><button :disabled="busy">
        搜索
      </button>
    </form>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }} <button
        type="button"
        @click="load"
      >
        重试
      </button>
    </p>
    <div
      class="picker-table"
      tabindex="0"
      aria-label="客户商品列表"
    >
      <table class="data-table">
        <thead><tr><th>选择</th><th>图片</th><th>品名 / SKU</th><th>客户商品编码</th><th>单位</th><th>客户要求</th></tr></thead><tbody>
          <tr
            v-for="product in rows"
            :key="product.id"
          >
            <td>
              <input
                type="checkbox"
                :aria-label="`选择 ${product.productName}`"
                :disabled="busy||existing.includes(product.id)"
                :checked="existing.includes(product.id)||selected.some(p=>p.id===product.id)"
                @change="toggle(product,($event.target as HTMLInputElement).checked)"
              ><small v-if="existing.includes(product.id)">已添加</small>
            </td><td>
              <CustomerProductImage
                :product-id="product.productId"
                :variant-id="product.variantId"
                :alt="product.productName"
              />
            </td><td>{{ product.productName }}<small>{{ product.skuCode }}</small></td><td>{{ product.customerProductCode||'—' }}</td><td>{{ product.priceUnit||'—' }}</td><td>{{ product.requirements||'—' }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="6">
              {{ busy?'正在加载…':error?'加载失败，请重试':'暂无匹配的客户商品' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <ListPagination
      :total="total"
      :page="page"
      :page-size="size"
      :disabled="busy"
      @update:page="page=$event;load()"
      @update:page-size="resize"
    />
    <footer class="ui-dialog-actions">
      <span>已选 {{ selected.length }} 项</span><button
        type="button"
        class="secondary-action"
        @click="emit('close')"
      >
        取消
      </button><button
        class="ui-button-primary"
        type="button"
        :disabled="busy||!selected.length"
        @click="emit('confirm',selected)"
      >
        添加所选（{{ selected.length }}）
      </button>
    </footer>
  </dialog>
</template>
<style scoped>
.customer-product-picker{width:min(1100px,calc(100vw - 32px));max-width:calc(100vw - 32px);height:min(800px,calc(100dvh - 32px));max-height:calc(100dvh - 32px);overflow:hidden;box-sizing:border-box;padding:0;border:1px solid var(--line);border-radius:8px;color:var(--ink)}
.customer-product-picker[open]{display:flex;flex-direction:column}.customer-product-picker::backdrop{background:#14223b66}header,footer,.picker-search{display:flex;align-items:center;gap:10px;padding:8px 16px;flex-shrink:0}header{border-bottom:1px solid var(--line)}h2{font-size:16px;margin:0;flex:1}header button{background:transparent;color:var(--muted);padding:0 8px;font-size:22px}.picker-search input{flex:1;min-width:0;height:36px;border:1px solid var(--line);border-radius:4px;padding:0 10px}.picker-table{min-height:0;overflow:auto;flex:1 1 0;overscroll-behavior:contain;scrollbar-gutter:stable}.picker-table table{min-width:700px;width:100%}.picker-table th{position:sticky;top:0;z-index:1;background:#f5f7fa}.picker-table td{padding:8px 12px;white-space:pre-wrap;max-width:300px;overflow-wrap:anywhere}.picker-table small{display:block;color:var(--muted);font-size:12px}footer{border-top:1px solid var(--line)}footer span{margin-right:auto}p[role=alert]{color:#b42318;padding:0 16px}.customer-product-picker :deep(.list-pagination){flex-shrink:0}@media(max-width:640px){.customer-product-picker{width:calc(100vw - 16px);max-width:calc(100vw - 16px);height:calc(100dvh - 16px);max-height:calc(100dvh - 16px)}header,footer,.picker-search{padding:8px 10px}footer{flex-wrap:wrap}}
</style>
