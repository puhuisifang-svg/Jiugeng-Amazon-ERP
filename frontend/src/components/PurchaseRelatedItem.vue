<script setup lang="ts">
import {ref} from 'vue'
import {apiRequest} from '@/api/client'
import {domainApi,type SkuListItemDto} from '@/api/domain'
import {materialManagementApi,type MaterialRecord} from '@/api/materialManagement'
import SkuDetailDialog from './SkuDetailDialog.vue'
import MaterialDetailDialog from './MaterialDetailDialog.vue'
import MaterialInventoryDialog from './MaterialInventoryDialog.vue'
import ProductInventoryDialog from './ProductInventoryDialog.vue'
const props=defineProps<{orderId:string;itemId:string;name:string;type:string;inventory?:boolean}>()
const sku=ref<SkuListItemDto>(),material=ref<MaterialRecord>(),materialOpen=ref(false),stock=ref(false),busy=ref(false),error=ref('')
async function open(){busy.value=true;error.value='';try{if(props.inventory){if(props.type==='MATERIAL')material.value=(await materialManagementApi.detail(props.itemId)).material;else stock.value=true}else if(props.type==='MATERIAL')materialOpen.value=true;else{const {parentId}=await apiRequest<{parentId:string}>(`/v1/purchases/manage/${props.orderId}/product-parent?item=${encodeURIComponent(props.itemId)}`),p=await domainApi.product(parentId),v=p.variants.find(v=>v.id===props.itemId);if(!v)throw Error('当前商品不可用，采购留存资料仍可查看');sku.value={productId:p.id,productCode:p.productCode,productName:p.productName,productStatus:p.status,variant:v,businessAvailable:false}}}catch(e){error.value=e instanceof Error?e.message:'关联资料读取失败'}finally{busy.value=false}}
</script>
<template>
  <button
    class="text-link"
    :disabled="busy"
    @click="open"
  >
    {{ inventory?'查看库存流水':name }}
  </button><small
    v-if="error"
    role="alert"
  >{{ error }}</small><SkuDetailDialog
    v-if="sku"
    :detail-row="sku"
    :writable="false"
    @close="sku=undefined"
  /><MaterialDetailDialog
    v-if="materialOpen"
    :id="itemId"
    read-only
    @close="materialOpen=false"
  /><MaterialInventoryDialog
    v-if="material"
    :material="material"
    read-only
    @close="material=undefined"
  /><ProductInventoryDialog
    v-if="stock"
    :variant-id="itemId"
    :name="name"
    read-only
    @close="stock=false"
  />
</template>
