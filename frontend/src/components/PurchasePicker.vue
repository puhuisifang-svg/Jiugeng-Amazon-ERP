<script setup lang="ts">
import '@/styles/purchaseManagement.css'
import {ref,onMounted,onBeforeUnmount} from 'vue'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import PurchaseItemImage from './PurchaseItemImage.vue'
import {domainApi,type SupplierDto} from '@/api/domain'
import {purchaseManagementApi as api} from '@/api/purchaseManagement'
import type {PurchaseReferenceItem,PurchaseOrderType} from '@/api/purchases'
const props=defineProps<{type:PurchaseOrderType;supplierId?:string;filterSuppliers?:boolean}>(),emit=defineEmits<{close:[];supplier:[SupplierDto];item:[PurchaseReferenceItem]}>()
const q=ref(''),page=ref(1),total=ref(0),suppliers=ref<SupplierDto[]>([]),items=ref<PurchaseReferenceItem[]>([]),loading=ref(false),error=ref('');let seq=0
async function load(){const token=++seq;loading.value=true;error.value='';try{if(props.supplierId){const result=await api.references(props.type,props.supplierId,q.value,page.value-1);if(token===seq){items.value=result.items;total.value=result.total}}else{const result=await domainApi.suppliers({q:q.value,...(props.filterSuppliers?{}:{status:'ACTIVE' as const,category:props.type==='MATERIAL'?'MATERIAL' as const:'PRODUCT' as const}),page:page.value-1,size:20});if(token===seq){suppliers.value=result.items;total.value=result.totalCount}}}catch(e){if(token===seq)error.value=e instanceof Error?e.message:'读取失败'}finally{if(token===seq)loading.value=false}}
onMounted(load);onBeforeUnmount(()=>seq++)
</script>
<template>
  <AccessDialog
    :title="supplierId?'选择采购项目':'选择供应商'"
    wide
    dialog-class="purchase-ui purchase-picker-dialog"
    @close="emit('close')"
  >
    <form
      class="purchase-search"
      @submit.prevent="page=1;load()"
    >
      <input
        v-model="q"
        placeholder="输入名称或编码搜索"
        aria-label="搜索档案"
      ><button :disabled="loading">
        搜索
      </button>
    </form>
    <div class="purchase-picker-results">
      <p
        v-if="error"
        role="alert"
      >
        {{ error }} <button @click="load">
          重试
        </button>
      </p><p v-else-if="loading">
        正在读取…
      </p>
      <table
        v-else
        class="data-table"
      >
        <thead>
          <tr>
            <th
              v-if="supplierId"
              class="purchase-picker-image"
            >
              图片
            </th><th>{{ supplierId?'项目名称 / 单位':'供应商名称' }}</th><th class="purchase-picker-action">
              操作
            </th>
          </tr>
        </thead><tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td>
              <PurchaseItemImage
                :image-asset-id="item.imageAssetId"
                :image-url="item.imageUrl"
                :alt="item.name"
              />
            </td><td>{{ item.name }}<small>{{ item.unit }} {{ item.primarySupplier?'· 主供应商':'' }}</small></td><td>
              <button
                type="button"
                class="purchase-picker-select"
                @click="emit('item',item)"
              >
                选择
              </button>
            </td>
          </tr>
          <tr
            v-for="supplier in suppliers"
            :key="supplier.id"
          >
            <td>{{ supplier.name }}</td><td>
              <button
                type="button"
                class="purchase-picker-select"
                @click="emit('supplier',supplier)"
              >
                选择
              </button>
            </td>
          </tr>
          <tr v-if="!total">
            <td :colspan="supplierId?3:2">
              {{ filterSuppliers ? '没有匹配的供应商' : '没有匹配的启用档案' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #pagination>
      <ListPagination
        :page="page"
        :page-size="20"
        :total="total"
        :disabled="loading"
        @update:page="page=$event;load()"
      />
    </template>
  </AccessDialog>
</template>
