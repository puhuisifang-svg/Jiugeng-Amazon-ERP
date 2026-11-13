<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ordersApi, type CustomerOrder, type ArrangementItem } from '@/api/orders'
import { session } from '@/state/session'
import { createRequestId } from '@/utils/requestId'
import { confirmAction } from '@/state/uiConfirmation'
import DatePicker from './DatePicker.vue'
import ProductName from './ProductName.vue'
import CustomerProductImage from './CustomerProductImage.vue'
const props=defineProps<{orderId:string}>()
const emit=defineEmits<{close:[];saved:[]}>()
const arranging=ref<CustomerOrder>(),arrangeItems=ref<Array<ArrangementItem & {selected:boolean;name:string;available:number;unit:string}>>([])
const recipient=ref(''),phone=ref(''),address=ref(''),formError=ref(''),saving=ref(false),loading=ref(true),submitted=ref(false)
const requestKey=createRequestId()
let payload:Parameters<typeof ordersApi.arrange>[1]|undefined
async function load(){loading.value=true;formError.value='';try{
 if(!session.canAccess(['orders.read','customers.write','dashboard.shipments.manage']))throw Error('没有安排订单发货的权限。')
 const fresh=await ordersApi.get(props.orderId)
 if(fresh.status!=='CONFIRMED')throw Error('请先确认客户订单，再新增待发货。')
 arranging.value=fresh;recipient.value=fresh.details.recipient;phone.value=fresh.details.phone;address.value=fresh.details.address
 arrangeItems.value=fresh.lines.map(l=>{const available=Math.max(0,Number(l.details.quantity)-Number(l.allocatedQuantity)-Number(l.shippedQuantity));return {lineId:l.id,selected:available>0,name:l.details.name,unit:l.details.unit,available,quantity:available,boxCount:1,plannedDate:l.details.deliveryDate||fresh.details.deliveryDate,latestDate:l.details.deliveryDate||fresh.details.deliveryDate,remark:l.details.remark}})
}catch(e){formError.value=e instanceof Error?e.message:'订单加载失败，请重试'}finally{loading.value=false}}
async function close(){if(saving.value)return;if(arranging.value&&!await confirmAction('关闭后未保存的发货安排将丢失，是否关闭？'))return;emit('close')}
async function submitArrange(){
 if(!arranging.value||saving.value)return
 const selected=arrangeItems.value.filter(i=>i.selected)
 if(!selected.length||selected.some(i=>!Number.isFinite(i.quantity)||i.quantity<=0||i.quantity>i.available)){formError.value='请选择商品，填写不超过可安排数量的本次数量。';return}
 if(selected.some(i=>!Number.isInteger(i.boxCount)||i.boxCount<1||!i.plannedDate||!i.latestDate||i.latestDate<i.plannedDate)){formError.value='请填写有效箱数、计划日期和最晚日期。';return}
 if(!recipient.value.trim()||!phone.value.trim()||!address.value.trim()){formError.value='请填写完整收货信息。';return}
 saving.value=true;formError.value=''
 payload ??= {version:arranging.value.version,items:selected.map(({lineId,quantity,boxCount,plannedDate,latestDate,remark})=>({lineId,quantity,boxCount,plannedDate,latestDate,remark})),recipient:recipient.value,phone:phone.value,address:address.value}
 submitted.value=true
 try{await ordersApi.arrange(props.orderId,payload,requestKey);emit('saved')}catch(e){formError.value=e instanceof Error?e.message:'保存失败，请重试'}finally{saving.value=false}
}
onMounted(load)
</script>
<template>
  <div
    v-if="arranging"
    class="operation-dialog"
    role="dialog"
    aria-modal="true"
    aria-label="安排待发货"
  >
    <form
      class="panel operation-form operation-dialog__card order-arrange"
      @submit.prevent="submitArrange"
    >
      <h2>新增待发货 · {{ arranging.details.externalNumber || arranging.orderNumber }}</h2><p class="form-wide">
        客户：{{ arranging.customerName }}
      </p><label>收货人<input
        v-model="recipient"
        :disabled="saving || submitted"
        required
        maxlength="100"
      ></label><label>联系电话<input
        v-model="phone"
        :disabled="saving || submitted"
        required
        maxlength="32"
      ></label><label class="form-wide">收货地址<input
        v-model="address"
        :disabled="saving || submitted"
        required
        maxlength="500"
      ></label><div class="form-wide order-scroll">
        <table class="data-table">
          <thead><tr><th>选择</th><th>图片 / 商品</th><th>客户编码</th><th>可安排</th><th>本次数量</th><th>箱数</th><th>计划日期</th><th>最晚日期</th><th>备注</th></tr></thead><tbody>
            <tr
              v-for="a in arrangeItems"
              :key="a.lineId"
            >
              <td>
                <input
                  v-model="a.selected"
                  type="checkbox"
                  :aria-label="`选择${a.name}`"
                  :disabled="saving || submitted || a.available <= 0"
                >
              </td><td>
                <div class="order-item-preview">
                  <CustomerProductImage
                    :product-id="arranging.lines.find(l=>l.id===a.lineId)?.product?.productId ?? ''"
                    :variant-id="arranging.lines.find(l=>l.id===a.lineId)?.product?.variantId ?? ''"
                    :alt="a.name"
                  />
                  <ProductName
                    :text="a.name"
                    :styles="arranging.lines.find(l=>l.id===a.lineId)?.namePresentation?.styles"
                    :highlighted="arranging.lines.find(l=>l.id===a.lineId)?.namePresentation?.highlighted"
                  />
                </div>
              </td><td>{{ arranging.lines.find(l=>l.id===a.lineId)?.product?.customerProductCode || '未填写' }}</td><td>{{ a.available }} {{ a.unit }}</td><td>
                <input
                  v-model.number="a.quantity"
                  aria-label="本次数量"
                  type="number"
                  step="0.001"
                  min="0.001"
                  :max="a.available"
                  :disabled="saving || submitted || !a.selected"
                  :required="a.selected"
                >
              </td><td>
                <input
                  v-model.number="a.boxCount"
                  aria-label="箱数"
                  type="number"
                  step="1"
                  min="1"
                  :disabled="saving || submitted || !a.selected"
                  :required="a.selected"
                >
              </td><td>
                <DatePicker
                  v-model="a.plannedDate"
                  aria-label="计划日期"
                  type="date"
                  :disabled="saving || submitted || !a.selected"
                  :required="a.selected"
                />
              </td><td>
                <DatePicker
                  v-model="a.latestDate"
                  aria-label="最晚日期"
                  type="date"
                  :min="a.plannedDate"
                  :disabled="saving || submitted || !a.selected"
                  :required="a.selected"
                />
              </td><td>
                <input
                  v-model="a.remark"
                  aria-label="发货备注"
                  maxlength="500"
                  :disabled="saving || submitted || !a.selected"
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div><p
        v-if="formError"
        role="alert"
        class="data-state--error"
      >
        {{ formError }}
      </p><div class="form-footer">
        <button
          type="button"
          :disabled="saving"
          @click="close"
        >
          取消
        </button><button :disabled="saving">
          {{ saving ? '处理中…' : '确认新增待发货' }}
        </button>
      </div>
    </form>
  </div>
  <div
    v-else
    class="operation-dialog"
    role="dialog"
    aria-label="安排待发货"
  >
    <section class="panel order-arrange">
      <h2>新增待发货</h2><p v-if="loading">
        正在加载订单…
      </p><p
        v-else
        role="alert"
      >
        {{ formError }}
      </p><div class="form-footer">
        <button
          type="button"
          @click="emit('close')"
        >
          关闭
        </button><button
          v-if="!loading"
          @click="load"
        >
          重新加载
        </button>
      </div>
    </section>
  </div>
</template>
<style scoped>
.operation-dialog{position:fixed;inset:0;background:#0f172a66;display:flex;align-items:center;justify-content:center;padding:16px;z-index:100}
.order-arrange{width:min(1180px,100%);max-height:calc(100dvh - 32px);overflow:auto;box-sizing:border-box;margin:0;padding:18px;display:grid;grid-template-columns:1fr 1fr;gap:12px;border-radius:8px;background:white}.order-arrange h2{grid-column:1/-1;margin:0;font-size:16px}.form-wide,.form-footer{grid-column:1/-1}.order-arrange label{display:flex;flex-direction:column;gap:6px;font-size:13px}.order-arrange input{min-height:40px;min-width:0;font-size:13px;padding:8px;border:1px solid var(--line);border-radius:4px}.order-scroll{overflow:auto;min-width:0}.data-table{min-width:1400px;width:100%;table-layout:fixed}.data-table th:nth-child(1){width:48px}.data-table th:nth-child(2){width:250px}.data-table th:nth-child(3){width:110px}.data-table th:nth-child(4){width:90px}.data-table th:nth-child(5){width:105px}.data-table th:nth-child(6){width:85px}.data-table th:nth-child(7),.data-table th:nth-child(8){width:170px}.order-item-preview{display:flex;align-items:center;gap:10px;white-space:normal}.order-item-preview :deep(.customer-product-image){flex-shrink:0}.order-scroll input:not([type=checkbox]){width:100%!important;box-sizing:border-box}.order-scroll :deep(.date-picker){min-width:155px}.data-table td{vertical-align:middle}.order-scroll input:not([type=checkbox]){width:100px}.order-scroll input[type=checkbox]{min-height:16px}.form-footer{position:sticky;bottom:-18px;background:white;border-top:1px solid var(--line);padding:12px 0;display:flex;justify-content:flex-end;gap:8px}.form-footer button{min-height:36px;padding:8px 16px;border-radius:4px}.form-footer button[type=button]{background:#f1f5f9;color:#334155;border:1px solid var(--line)}.form-footer button:not([type=button]){background:#21834a;color:white;border:1px solid #21834a}.order-arrange p{margin:0;font-size:13px}.data-state--error{grid-column:1/-1}@media(max-width:600px){.order-arrange{grid-template-columns:1fr;padding:12px}}
</style>
