<script setup lang="ts">
import { ref,watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { domainApi } from '@/api/domain'
import {factoryApi,type FactoryKind,type FactoryRecord} from '@/api/factory'
const props=defineProps<{kind:FactoryKind|'products';label:string;value?:string;disabled?:boolean}>()
const emit=defineEmits<{select:[FactoryRecord]}>()
const open=ref(false),q=ref(''),applied=ref(''),page=ref(1),size=ref(20),items=ref<FactoryRecord[]>([]),total=ref(0),busy=ref(false),error=ref('')
let sequence=0
async function load(){const current=++sequence;busy.value=true;error.value='';try{const r=props.kind==='products'?await productRows():await factoryApi.list(props.kind,{q:applied.value,page:page.value-1,size:size.value});if(current===sequence){items.value=r.items;total.value=r.totalCount}}catch(e){if(current===sequence)error.value=e instanceof Error?e.message:'加载失败'}finally{if(current===sequence)busy.value=false}}
async function productRows(){const r=await domainApi.skus({q:applied.value,page:page.value-1,size:size.value,status:'ACTIVE',productStatus:'ACTIVE'});return {items:r.items.map(x=>({id:x.variant.id!,version:0,status:'ACTIVE',date:'',project:x.productName,operation:x.variant.variantName,skuCode:x.variant.skuCode,productId:x.productId,variantId:x.variant.id!})),totalCount:r.totalCount}}
function search(){applied.value=q.value;page.value=1;void load()}
watch(open,v=>{if(v)void load();else sequence++})
function choose(r:FactoryRecord){emit('select',r);open.value=false}
</script>
<template>
  <button
    type="button"
    class="factory-picker-button"
    :aria-label="label"
    :disabled="disabled"
    @click="open=true"
  >
    {{ value || label }}<span aria-hidden="true">⌕</span>
  </button>
  <AccessDialog
    v-if="open"
    :title="label"
    wide
    @close="open=false"
  >
    <form
      class="factory-picker-search"
      @submit.prevent="search"
    >
      <input
        v-model="q"
        :aria-label="`${label}关键词`"
        placeholder="输入关键词搜索"
      ><button
        type="submit"
        :disabled="busy"
      >
        搜索
      </button>
    </form>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <TableViewport>
      <table class="data-table">
        <thead><tr><th>{{ kind==='employees'?'员工 / 工号':'加工项目 / 工序' }}</th><th>{{ kind==='employees'?'班组 / 状态':'工价 / 单位' }}</th><th>{{ kind==='employees'?'入职日期':'生效日期 / 适用员工' }}</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="r in items"
            :key="r.id"
          >
            <td><strong>{{ r.name || r.project }}</strong><small>{{ r.number || r.operation }}</small></td><td>{{ kind==='employees'?r.team:kind==='products'?r.skuCode:`${r.price} 元 / ${r.unit}` }}<small>{{ kind==='employees'?(r.endDate?`离职 ${r.endDate}`:'在职'):r.skuCode }}</small></td><td>{{ r.date }}<small v-if="kind==='rates'">{{ r.employeeName }}</small></td><td>
              <button
                type="button"
                @click="choose(r)"
              >
                选择
              </button>
            </td>
          </tr><tr v-if="!items.length">
            <td colspan="4">
              {{ busy?'正在加载…':'暂无记录，请先维护档案或工价' }}
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <template #pagination>
      <ListPagination
        :total="total"
        :page="page"
        :page-size="size"
        :disabled="busy"
        @update:page="page=$event;load()"
        @update:page-size="size=$event;page=1;load()"
      />
    </template>
  </AccessDialog>
</template>
