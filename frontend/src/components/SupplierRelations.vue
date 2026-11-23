<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {supplierManagementApi,type SupplierRelationType} from '@/api/supplierManagement'
import type {SupplierDependency} from '@/api/domain'
import {session} from '@/state/session'
import ListPagination from './ListPagination.vue'
import TableViewport from './TableViewport.vue'
const props=defineProps<{id:string;deleting?:boolean;revision?:number;busy?:boolean}>()
defineEmits<{unlink:[SupplierDependency]}>()
const types=computed(()=>([{id:'PRODUCT',name:'产品子体',permission:'products.read'},{id:'MATERIAL',name:'辅料',permission:'materials.read'},{id:'PURCHASE',name:'采购单',permission:'purchases.read'}] as const).filter(t=>session.canAccess([t.permission])))
const statusLabel=(value:string)=>({DRAFT:'草稿',SUBMITTED:'已提交',PARTIALLY_RECEIVED:'部分收货',COMPLETED:'已完成',CANCELLED:'已取消'} as Record<string,string>)[value]??value
const type=ref<SupplierRelationType>(types.value[0]?.id??'PRODUCT'),page=ref(1),size=ref(20),total=ref(0),rows=ref<SupplierDependency[]>([]),loading=ref(false),error=ref('');let generation=0
async function load(){if(!types.value.length)return;const seq=++generation;loading.value=true;error.value='';try{const r=await supplierManagementApi.relations(props.id,type.value,page.value-1,size.value);if(seq===generation){rows.value=r.items;total.value=r.totalCount}}catch(e){if(seq===generation)error.value=e instanceof Error?e.message:'关联读取失败'}finally{if(seq===generation)loading.value=false}}
function link(r:SupplierDependency){if(r.archived)return '';if(r.type==='PURCHASE')return `/purchases/orders/${encodeURIComponent(r.id)}`;if(r.type==='PRODUCT')return `/products/${encodeURIComponent(r.parentId)}`;return `/materials?viewMaterial=${encodeURIComponent(r.id)}`}
function editLink(r:SupplierDependency){if(r.archived||r.type==='PURCHASE'||!session.canAccess(['suppliers.write',r.type==='PRODUCT'?'products.write':'materials.write']))return '';return r.type==='PRODUCT'?`/products?editProduct=${encodeURIComponent(r.parentId)}&variantId=${encodeURIComponent(r.id)}`:`/materials?editMaterial=${encodeURIComponent(r.id)}`}
function canUnlink(r:SupplierDependency){return props.deleting&&r.archived&&r.type==='PRODUCT'&&session.canAccess(['suppliers.delete','suppliers.write','products.write'])}
watch([type,page,size,()=>props.revision],load);onMounted(load);onBeforeUnmount(()=>generation++)
</script>
<template>
  <div class="supplier-relations">
    <nav class="supplier-tabs">
      <button
        v-for="t in types"
        :key="t.id"
        :aria-pressed="type===t.id"
        :disabled="busy"
        @click="type=t.id;page=1"
      >
        {{ t.name }}
      </button>
    </nav><p v-if="!types.length">
      暂无关联业务查看权限。
    </p><p v-else-if="loading">
      正在读取关联…
    </p><p
      v-else-if="error"
      role="alert"
    >
      {{ error }} <button @click="load">
        重试
      </button>
    </p><template v-else>
      <TableViewport>
        <table class="data-table">
          <thead><tr><th>关联记录</th><th>状态 / 操作</th></tr></thead><tbody>
            <tr
              v-for="r in rows"
              :key="r.id"
            >
              <td><strong>{{ r.type==='PURCHASE'?r.code:r.name }}</strong><small v-if="r.type==='PURCHASE'">{{ statusLabel(r.name) }}</small></td><td>
                <span v-if="r.archived">已删除 · 历史关联</span><a
                  v-else
                  :href="link(r)"
                  target="_blank"
                  rel="noopener noreferrer"
                >查看 ↗</a><a
                  v-if="deleting&&editLink(r)"
                  :href="editLink(r)"
                  target="_blank"
                  rel="noopener noreferrer"
                >编辑关联 ↗</a><button
                  v-if="canUnlink(r)"
                  :disabled="loading||busy"
                  @click="$emit('unlink',r)"
                >
                  解除历史关联
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport><p v-if="!rows.length">
        暂无此类关联记录。
      </p><ListPagination
        :page="page"
        :page-size="size"
        :total="total"
        :disabled="loading||busy"
        @update:page="page=$event"
        @update:page-size="size=$event;page=1"
      />
    </template>
  </div>
</template>
