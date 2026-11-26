<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import TableViewport from './TableViewport.vue'
import MaterialImage from './MaterialImage.vue'
import EditableSelect from './EditableSelect.vue'
import InventoryReason from './InventoryReason.vue'
import { inventoryOptionsApi } from '@/api/inventoryOptions'
import { confirmAction } from '@/state/uiConfirmation'
import { createRequestId } from '@/utils/requestId'
import { materialManagementApi, type MaterialRecord } from '@/api/materialManagement'
import { type InventoryBalanceViewDto, type LedgerEntryDto, type InventoryCommandType } from '@/api/domain'
import { apiRequest, ApiError } from '@/api/client'
import { session } from '@/state/session'
import { materialNumber, materialQuantityScale, validMaterialQuantity } from '@/ui/materialQuantity'
import { inventoryTransactionTypeLabel } from '@/ui/inventory'
import { beijingDateTime } from '@/utils/dateTime'
const props=defineProps<{material:MaterialRecord;readOnly?:boolean}>(),emit=defineEmits<{close:[];changed:[]}>()
const dialog=ref<InstanceType<typeof AccessDialog>>()
const balance=ref<InventoryBalanceViewDto|null>(null),entries=ref<LedgerEntryDto[]>([]),total=ref(0),page=ref(1),size=ref(20)
const loading=ref(true),busy=ref(false),loadError=ref(''),error=ref(''),notice=ref(''),quantity=ref(''),reason=ref(''),reasonOptions=ref<string[]>([]),optionsError=ref(''),optionsLoading=ref(false)
const ops=[{type:'INBOUND',label:'入库',permission:'inventory.inbound'},{type:'OUTBOUND',label:'出库',permission:'inventory.outbound'}] as const
const operations=computed(()=>ops.filter(o=>session.canAccess([o.permission])))
const type=ref<string>(operations.value[0]?.type??'INBOUND')
interface Command {requestId:string;materialId:string;type:InventoryCommandType;quantity:string;unit:string;reason:string}
const key=`jiugeng.material-stock.pending.${session.user.value?.id??'current'}.${props.material.id}`
const pending=ref<Command>()
try{const saved=JSON.parse(sessionStorage.getItem(key)??'null') as Command|null;if(saved?.materialId===props.material.id&&saved.requestId){pending.value=saved;type.value=saved.type;quantity.value=String(saved.quantity);reason.value=saved.reason}}catch{/* Corrupt recovery state is ignored. */}
watch(pending,p=>{try{if(p)sessionStorage.setItem(key,JSON.stringify(p));else sessionStorage.removeItem(key)}catch{/* Keep the in-memory original request. */}},{flush:'sync'})
const locked=computed(()=>busy.value||!!pending.value),canOperate=computed(()=>!props.readOnly&&props.material.status==='ACTIVE'&&operations.value.length>0)
const baseQuantity=computed(()=>quantity.value)
const current=computed(()=>loading.value||loadError.value?undefined:balance.value?.quantity??0)
const decreased=computed(()=>['OUTBOUND','ADJUST_OUT'].includes(type.value))
const valid=computed(()=>validMaterialQuantity(baseQuantity.value,props.material.unit))
const projected=computed(()=>current.value==null||!valid.value?undefined:current.value+(decreased.value?-1:1)*Number(baseQuantity.value))
const dirty=computed(()=>!!quantity.value||!!reason.value)
let sequence=0
async function load(){const seq=++sequence;loading.value=true;loadError.value='';try{const r=await materialManagementApi.inventory(props.material.id,page.value-1,size.value);if(seq!==sequence)return;balance.value=r.balance;entries.value=r.ledger.items;total.value=r.ledger.totalCount}catch(e){if(seq===sequence)loadError.value=e instanceof Error?e.message:'库存读取失败'}finally{if(seq===sequence)loading.value=false}}
async function submit(){
 if(busy.value)return
 error.value='';notice.value=''
 if(!pending.value){
  if(!canOperate.value||loading.value||loadError.value)return
  if(!valid.value){error.value=`数量必须大于零；${props.material.unit}${materialQuantityScale(props.material.unit)?'最多四位小数':'必须为整数'}，最多 14 位整数`;return}
  if(projected.value!=null&&projected.value<0){error.value='库存不足，请核对数量';return}
  if(!reason.value.trim()){error.value='请填写本次操作原因';return}
  pending.value={requestId:createRequestId(),materialId:props.material.id,type:type.value as InventoryCommandType,quantity:baseQuantity.value,unit:props.material.unit,reason:reason.value.trim()}
 }
 busy.value=true
 try{await apiRequest('/v1/inventory/transactions',{method:'POST',body:JSON.stringify(pending.value)});pending.value=undefined;quantity.value='';reason.value='';notice.value='库存已更新';emit('changed');await load();void loadReasons()}
 catch(e){error.value=e instanceof Error?e.message:'网络异常';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status))pending.value=undefined;else error.value='提交结果待确认，请使用原请求重试。'+error.value}
 finally{busy.value=false}
}
async function loadReasons(){optionsLoading.value=true;optionsError.value='';try{reasonOptions.value=await inventoryOptionsApi.reasons()}catch{optionsError.value='历史原因读取失败，可直接输入或重试'}finally{optionsLoading.value=false}}
async function removeReason(name:string){
 if(locked.value||!canOperate.value)return
 if(!await confirmAction(`从候选项移除“${name}”？历史库存流水不受影响。`))return
 if(locked.value||!canOperate.value)return
 busy.value=true;optionsError.value=''
 try{await inventoryOptionsApi.removeReason(name);await loadReasons()}catch(e){optionsError.value=e instanceof Error?e.message:'历史原因移除失败'}finally{busy.value=false}
}
function unload(e:BeforeUnloadEvent){if(busy.value||dirty.value||pending.value){e.preventDefault();e.returnValue=''}}
watch([page,size],load)
onMounted(()=>{void load();if(canOperate.value)void loadReasons();window.addEventListener('beforeunload',unload)})
onBeforeUnmount(()=>{sequence++;window.removeEventListener('beforeunload',unload)})
defineExpose({busy,dirty})
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="readOnly?'辅料库存流水':'辅料库存操作'"
    wide
    dialog-class="materials-ui material-stock"
    :busy="busy"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <div class="material-summary">
      <MaterialImage
        :name="material.name"
        :image-asset-id="material.imageAssetId"
        :image-url="material.imageUrl"
      /><div><strong>{{ material.name }}</strong><p>{{ material.specification || material.materialType }} · {{ material.unit }}</p><p>当前库存 <strong>{{ materialNumber(current) }}</strong> {{ material.unit }} <small v-if="!loading&&!loadError&&!balance">未建账</small></p></div>
    </div>
    <p
      v-if="loadError"
      role="alert"
    >
      库存读取失败，不能据此记账。{{ loadError }} <button @click="load">
        重试
      </button>
    </p>
    <p
      v-if="pending"
      class="material-warning"
      role="status"
    >
      存在待确认的库存请求。填写内容已锁定，重试不会重复记账。
    </p>
    <form
      v-if="canOperate||pending"
      id="material-stock-form"
      class="material-stock-form"
      @submit.prevent="submit"
    >
      <div class="material-operation-tabs">
        <button
          v-for="o in operations"
          :key="o.type"
          type="button"
          :aria-pressed="type===o.type"
          :disabled="locked"
          @click="type=o.type"
        >
          {{ o.label }}
        </button>
      </div>
      <fieldset
        :disabled="locked||loading||!!loadError"
        class="material-fields"
      >
        <div class="material-quantity-line material-full">
          <label>数量（{{ material.unit }}）<input
            v-model="quantity"
            aria-label="操作数量"
            inputmode="decimal"
            maxlength="19"
            required
          ></label>
          <p
            class="material-stock-projection"
            aria-live="polite"
            :class="{'material-decrease':projected!=null&&projected<0}"
          >
            预计结存 <strong>{{ materialNumber(projected) }}</strong> {{ material.unit }}
          </p>
        </div>
        <label class="material-full">操作原因<EditableSelect
          v-model="reason"
          :options="reasonOptions"
          label="操作原因"
          :disabled="locked||loading||!!loadError"
          :max-length="500"
          required
          @remove="removeReason"
        /></label>
        <p
          v-if="optionsLoading"
          class="material-full material-reason-status"
        >
          正在读取历史原因…
        </p>
        <p
          v-else-if="optionsError"
          class="material-full material-reason-status"
          role="alert"
        >
          {{ optionsError }} <button
            type="button"
            class="material-link"
            :disabled="locked"
            @click="loadReasons"
          >
            重试
          </button>
        </p>
      </fieldset>
    </form>
    <p
      v-if="error"
      role="alert"
      class="error-message"
    >
      {{ error }}
    </p><p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <h3>库存流水 <small>只追加，不修改历史</small></h3>
    <p v-if="loading">
      正在读取库存…
    </p><p v-else-if="!entries.length&&!loadError">
      暂无库存流水
    </p>
    <TableViewport
      v-else-if="!loadError"
      :reserve-bottom="100"
    >
      <table class="data-table">
        <thead><tr><th>时间 / 操作人</th><th>操作</th><th>变动</th><th>结存</th><th>原因</th></tr></thead><tbody>
          <tr
            v-for="entry in entries"
            :key="entry.id"
          >
            <td>{{ beijingDateTime(entry.occurredAt) }}<small>{{ entry.actorLoginName }}</small></td><td>{{ inventoryTransactionTypeLabel(entry.type) }}</td><td
              class="material-number"
              :class="Number(entry.signedQuantity)<0?'material-decrease':'material-increase'"
            >
              {{ Number(entry.signedQuantity)>0?'+':'' }}{{ materialNumber(entry.signedQuantity) }} {{ entry.unit }}
            </td><td class="material-number">
              {{ materialNumber(entry.balanceAfter) }}
            </td><td>
              <InventoryReason
                :entry="entry"
                :disabled="busy"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      v-model:page="page"
      v-model:page-size="size"
      :total="total"
      :disabled="loading||busy"
    />
    <template #footer>
      <button
        type="button"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        {{ pending?'稍后确认':'关闭' }}
      </button><button
        v-if="canOperate||pending"
        class="ui-button-primary"
        type="submit"
        form="material-stock-form"
        :disabled="busy||(!pending&&(loading||!!loadError))"
      >
        {{ busy?'提交中…':pending?'确认原请求':'确认'+(ops.find(o=>o.type===type)?.label??'操作') }}
      </button>
    </template>
  </AccessDialog>
</template>
