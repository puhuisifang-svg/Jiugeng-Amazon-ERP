<script setup lang="ts">
import '@/styles/purchaseManagement.css'
import {computed,onMounted,onBeforeUnmount,ref,watch} from 'vue'
import {RouterLink,onBeforeRouteLeave} from 'vue-router'
import {purchaseManagementApi as api,blankPurchaseFilters} from '@/api/purchaseManagement'
import type {PurchaseOrder} from '@/api/purchases'
import {session} from '@/state/session'
import {confirmAction} from '@/state/uiConfirmation'
import PurchaseReceiveFlow from './PurchaseReceiveFlow.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
const props=defineProps<{visible:boolean}>(),emit=defineEmits<{count:[number];busy:[boolean]}>()
const orders=ref<PurchaseOrder[]>([]),loading=ref(false),error=ref(''),query=ref(''),applied=ref(''),page=ref(1),size=ref(10),total=ref(0),target=ref(''),flow=ref<InstanceType<typeof PurchaseReceiveFlow>>()
const canRead=computed(()=>session.canAccess(['purchases.read'])),canReceive=computed(()=>session.canAccess(['purchases.receive']))
let seq=0
async function reload(){if(!canRead.value)return;const n=++seq;loading.value=true;error.value='';try{const r=await api.page({...blankPurchaseFilters(),type:'',status:'OPEN',q:applied.value},page.value-1,size.value);if(n===seq){orders.value=r.items;total.value=r.total;emit('count',r.total)}}catch(e){if(n===seq)error.value=(e instanceof Error?e.message:'读取失败')+(orders.value.length?'；保留上次读取结果':'')}finally{if(n===seq)loading.value=false}}
async function completed(){target.value='';await reload()}
watch([page,size],reload);watch(()=>props.visible,visible=>{if(visible)void reload()});watch(()=>!!flow.value?.busy,v=>emit('busy',v))
onBeforeRouteLeave(async()=>!flow.value?.busy&&(!flow.value?.dirty||!!flow.value?.pending||await confirmAction('收货资料尚未保存，确定离开？', { unsaved: true, discardLabel: '放弃并离开' })))
onMounted(reload);onBeforeUnmount(()=>seq++);defineExpose({reload})
</script>
<template>
  <section
    v-if="visible"
    class="purchase-ui"
    aria-label="采购待接收"
  >
    <p v-if="!canRead">
      暂无采购查看权限
    </p><template v-else>
      <form
        class="purchase-search"
        @submit.prevent="applied=query;page=1;reload()"
      >
        <input
          v-model="query"
          type="search"
          placeholder="采购单 / 商品 / 运单号"
          aria-label="查找采购单"
        ><button :disabled="loading">
          搜索
        </button>
      </form><p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p><p v-if="loading">
        正在读取…
      </p><TableViewport v-else>
        <table class="data-table">
          <thead><tr><th>采购 / 供应商</th><th>商品摘要</th><th>预计到货</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="order in orders"
              :key="order.id"
            >
              <td>
                <RouterLink :to="`/purchases/orders/${order.id}`">
                  {{ order.orderNo }}
                </RouterLink><small>{{ order.supplierName }}</small>
              </td><td>
                <div
                  v-for="line in order.lines.slice(0,2)"
                  :key="line.id"
                  class="purchase-list-product"
                >
                  <PurchaseItemImage
                    :image-asset-id="line.imageAssetId"
                    :image-url="line.imageUrl"
                    :alt="line.itemName"
                  /><span>{{ line.itemName }}<small>已收 {{ line.receivedQuantity }} / {{ line.orderedQuantity }} {{ line.unit }}</small></span>
                </div><small v-if="order.lines.length>2">共 {{ order.lines.length }} 项，进入收货查看全部</small>
              </td><td>{{ order.expectedDeliveryDate||'未约定' }}</td><td>{{ order.status==='PARTIALLY_RECEIVED'?'部分收货':'待收货' }}</td><td>
                <button
                  v-if="canReceive"
                  @click="target=order.id"
                >
                  确认收货
                </button>
              </td>
            </tr><tr v-if="!orders.length">
              <td colspan="5">
                {{ applied?'没有匹配的采购单':'暂无待接收采购单' }}
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport><ListPagination
        v-model:page="page"
        v-model:page-size="size"
        :total="total"
        :disabled="loading"
      />
    </template><PurchaseReceiveFlow
      v-if="target"
      :id="target"
      ref="flow"
      @close="target='';reload()"
      @completed="completed"
    />
  </section>
</template>
