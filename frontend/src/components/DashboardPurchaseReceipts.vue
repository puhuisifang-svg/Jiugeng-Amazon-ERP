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
import SuccessToast from './SuccessToast.vue'
import {beijingDate} from '@/utils/dateTime'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
const props=defineProps<{visible:boolean}>(),emit=defineEmits<{count:[number];busy:[boolean]}>()
const success=ref(''),toastKey=ref(0)
const orders=ref<PurchaseOrder[]>([]),loading=ref(false),error=ref(''),query=ref(''),applied=ref(''),page=ref(1),size=ref(10),total=ref(0),target=ref(''),flow=ref<InstanceType<typeof PurchaseReceiveFlow>>()
const canRead=computed(()=>session.canAccess(['purchases.read'])),canReceive=computed(()=>session.canAccess(['purchases.receive']))
let seq=0
async function reload(){if(!canRead.value)return;const n=++seq;loading.value=true;error.value='';try{const r=await api.page({...blankPurchaseFilters(),type:'',status:'OPEN',q:applied.value},page.value-1,size.value);if(n===seq){orders.value=r.items;total.value=r.total;emit('count',r.total)}}catch(e){if(n===seq)error.value=(e instanceof Error?e.message:'读取失败')+(orders.value.length?'；保留上次读取结果':'')}finally{if(n===seq)loading.value=false}}
function received(){void reload()}
function completed(message:string){target.value='';success.value=message;toastKey.value++}
watch([page,size],reload);watch(()=>props.visible,visible=>{if(visible)void reload()});watch(()=>!!flow.value?.busy,v=>emit('busy',v))
onBeforeRouteLeave(async()=>!flow.value?.busy&&(!flow.value?.dirty||!!flow.value?.pending||await confirmAction('收货资料尚未保存，确定离开？', { unsaved: true, discardLabel: '放弃并离开' })))
onMounted(reload);onBeforeUnmount(()=>seq++);defineExpose({reload})
</script>
<template>
  <section
    v-if="visible"
    class="purchase-ui dashboard-purchases"
    aria-label="采购待接收"
  >
    <SuccessToast
      :key="toastKey"
      :message="success"
    />
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
        <table class="data-table dashboard-purchase-table purchase-grouped-table">
          <colgroup><col class="dashboard-purchase-order"><col><col class="dashboard-purchase-remark"><col class="dashboard-purchase-date"><col class="dashboard-purchase-status"><col class="dashboard-purchase-action"></colgroup>
          <thead><tr><th>采购日期 / 供应商</th><th>商品摘要</th><th>备注</th><th>预计到货</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <template
              v-for="(order, orderIndex) in orders"
              :key="order.id"
            >
              <tr
                v-for="(line,lineIndex) in (order.lines.length ? order.lines.slice(0,2) : [null])"
                :key="line?.id || order.id"
                :class="{'purchase-group-tinted':orderIndex%2===0}"
              >
                <td
                  v-if="lineIndex===0"
                  :rowspan="Math.max(1,Math.min(2,order.lines.length))"
                >
                  <RouterLink :to="`/purchases/orders/${order.id}`">
                    {{ beijingDate(order.createdAt) }}
                  </RouterLink><small>{{ order.supplierName }}</small>
                </td><td class="purchase-group-product">
                  <div
                    v-if="line"
                    class="dashboard-purchase-product"
                  >
                    <PurchaseItemImage
                      :image-asset-id="line.imageAssetId"
                      :image-url="line.imageUrl"
                      :alt="line.itemName"
                    /><div class="dashboard-purchase-product-copy">
                      <span class="dashboard-purchase-product-name">{{ line.itemName }}</span><small>已收 <strong>{{ line.receivedQuantity }}</strong> / {{ line.orderedQuantity }} {{ line.unit }}</small>
                    </div>
                  </div><small v-if="order.lines.length>2 && lineIndex===1">共 {{ order.lines.length }} 项，进入收货查看全部</small>
                </td><td class="purchase-group-note">
                  <span>{{ line?.remark || '—' }}</span>
                  <small
                    v-if="order.remark && lineIndex===Math.max(0,Math.min(2,order.lines.length)-1)"
                    class="dashboard-purchase-order-note"
                  >整单：{{ order.remark }}</small>
                </td><td
                  v-if="lineIndex===0"
                  :rowspan="Math.max(1,Math.min(2,order.lines.length))"
                  class="dashboard-purchase-due"
                >
                  {{ order.expectedDeliveryDate||'未约定' }}
                </td><td
                  v-if="lineIndex===0"
                  :rowspan="Math.max(1,Math.min(2,order.lines.length))"
                >
                  <span
                    class="purchase-status"
                    :data-status="order.status"
                  >{{ order.status==='PARTIALLY_RECEIVED'?'部分收货':'待收货' }}</span>
                </td><td
                  v-if="lineIndex===0"
                  :rowspan="Math.max(1,Math.min(2,order.lines.length))"
                >
                  <button
                    v-if="canReceive"
                    class="dashboard-purchase-receive"
                    @click="target=order.id"
                  >
                    确认收货
                  </button>
                </td>
              </tr>
            </template><tr v-if="!orders.length">
              <td colspan="6">
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
      @updated="received"
    />
  </section>
</template>
