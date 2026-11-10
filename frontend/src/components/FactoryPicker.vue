<script setup lang="ts">
import PurchaseItemImage from './PurchaseItemImage.vue'
import {factoryProject} from '@/utils/factoryPresentation'
import { ref,watch } from 'vue'
import AccessDialog from './AccessDialog.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { domainApi } from '@/api/domain'
import {factoryApi,type FactoryKind,type FactoryRecord} from '@/api/factory'
const props=defineProps<{kind:FactoryKind|'products';label:string;value?:string;disabled?:boolean}>()
const parent=ref(''),appliedParent=ref(''),parents=ref<{id:string;name:string}[]>([]),parentError=ref(''),parentsBusy=ref(false)
async function loadParents(){parentsBusy.value=true;parentError.value='';try{const r=await domainApi.products();parents.value=r.items.filter(p=>p.status==='ACTIVE'&&p.variants.some(v=>v.status==='ACTIVE')).map(p=>({id:p.id,name:`${p.productName} / ${p.productCode}`}))}catch(e){parentError.value=e instanceof Error?e.message:'父体选项加载失败'}finally{parentsBusy.value=false}}
const emit=defineEmits<{select:[FactoryRecord]}>()
const open=ref(false),q=ref(''),applied=ref(''),page=ref(1),size=ref(20),items=ref<FactoryRecord[]>([]),total=ref(0),busy=ref(false),error=ref('')
let sequence=0
async function load(){const current=++sequence;busy.value=true;error.value='';try{const r=props.kind==='products'?await productRows():await factoryApi.list(props.kind,{q:applied.value,page:page.value-1,size:size.value});if(current===sequence){items.value=r.items;total.value=r.totalCount}}catch(e){if(current===sequence)error.value=e instanceof Error?e.message:'加载失败'}finally{if(current===sequence)busy.value=false}}
async function productRows(){const r=await domainApi.skus({productId:appliedParent.value,field:'KEYWORD',exact:false,q:applied.value,page:page.value-1,size:size.value,status:'ACTIVE',productStatus:'ACTIVE'});return {items:r.items.map(x=>({id:x.variant.id!,version:0,status:'ACTIVE',date:'',project:x.productName,name:x.variant.variantName,imageAssetId:x.variant.imageAssetId??null,imageUrl:x.variant.imageUrl??null,skuCode:x.variant.skuCode,productId:x.productId,variantId:x.variant.id!})),totalCount:r.totalCount}}
function search(){applied.value=q.value;appliedParent.value=parent.value;page.value=1;void load()}
watch(open,v=>{if(v){void load();if(props.kind==='products')void loadParents()}else sequence++})
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
      <label
        v-if="kind==='products'"
        class="factory-parent-filter"
      >父体<select
        v-model="parent"
        :disabled="parentsBusy"
        aria-label="父体筛选"
      ><option value="">全部父体</option><option
        v-for="p in parents"
        :key="p.id"
        :value="p.id"
      >{{ p.name }}</option></select></label>
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
      v-if="parentError"
      role="alert"
    >
      父体选项加载失败：{{ parentError }} <button
        type="button"
        @click="loadParents"
      >
        重试
      </button>
    </p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <TableViewport>
      <table class="data-table">
        <thead>
          <tr>
            <th v-if="kind==='products'">
              图片
            </th><th>{{ kind==='employees'?'员工 / 工号':kind==='products'?'SKU 名称 / 父体':'加工项目' }}</th><th>{{ kind==='employees'?'班组 / 状态':kind==='products'?'SKU 编码':'工价 / 单位' }}</th><th>{{ kind==='employees'?'入职日期':kind==='products'?'状态':'生效日期 / 适用员工' }}</th><th>操作</th>
          </tr>
        </thead><tbody>
          <tr
            v-for="r in items"
            :key="r.id"
          >
            <td v-if="kind==='products'">
              <PurchaseItemImage
                :image-asset-id="r.imageAssetId"
                :image-url="r.imageUrl"
                :alt="r.name||r.skuCode||'产品'"
              />
            </td><td><strong>{{ r.name || factoryProject(r) }}</strong><small>{{ kind==='products'?r.project:r.number }}</small></td><td>{{ kind==='employees'?r.team:kind==='products'?r.skuCode:`${r.price} 元 / ${r.unit}` }}<small>{{ kind==='employees'?(r.endDate?`离职 ${r.endDate}`:'在职'):kind==='products'?'':r.skuCode }}</small></td><td>{{ kind==='products'?'启用':r.date }}<small v-if="kind==='rates'">{{ r.employeeName }}</small></td><td>
              <button
                type="button"
                @click="choose(r)"
              >
                选择
              </button>
            </td>
          </tr><tr v-if="!items.length">
            <td :colspan="kind==='products'?5:4">
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

<style scoped>
.factory-picker-search{display:flex;flex-wrap:wrap;gap:8px;align-items:center}.factory-parent-filter{display:flex;flex:0 0 280px;white-space:nowrap;gap:6px;align-items:center;font-size:13px;min-width:0}.factory-parent-filter select{width:240px;max-width:100%;height:32px;border:1px solid var(--line);border-radius:4px;background:white;padding:0 8px;font-size:13px}@media(max-width:600px){.factory-parent-filter{width:100%;flex-basis:100%}.factory-parent-filter select{flex:1;min-width:0}.factory-picker-search>input{min-width:0;flex:1}}
</style>
