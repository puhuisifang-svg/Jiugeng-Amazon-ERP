<script setup lang="ts">
import '@/styles/supplierManagement.css'
import {onMounted,onBeforeUnmount,ref} from 'vue'
import AccessDialog from './AccessDialog.vue'
import SupplierImpactSummary from './SupplierImpactSummary.vue'
import SupplierRelations from './SupplierRelations.vue'
import {domainApi,type SupplierDto,type SupplierDependency} from '@/api/domain'
import {supplierManagementApi as api,type SupplierImpact} from '@/api/supplierManagement'
const props=defineProps<{supplier:SupplierDto}>(),emit=defineEmits<{close:[];deleted:[]}>()
const current=ref(props.supplier),impact=ref<SupplierImpact>(),loading=ref(false),saving=ref(false),error=ref(''),revision=ref(0),pendingUnlink=ref<SupplierDependency>();let alive=true
async function refresh(){if(loading.value||saving.value)return;loading.value=true;error.value='';impact.value=undefined;try{const [s,i]=await Promise.all([domainApi.supplier(props.supplier.id),api.impact(props.supplier.id)]);if(alive){current.value=s;impact.value=i;revision.value++}}catch(e){if(alive)error.value=e instanceof Error?e.message:'关联检查失败'}finally{if(alive)loading.value=false}}
async function remove(){if(!impact.value||impact.value.total||loading.value||saving.value)return;saving.value=true;try{await domainApi.deleteSupplier(current.value.id,current.value.version);emit('deleted')}catch(e){error.value=e instanceof Error?e.message:'删除失败';impact.value=undefined}finally{saving.value=false}}
async function unlink(){if(!pendingUnlink.value||saving.value)return;saving.value=true;let ok=false;try{await domainApi.unlinkArchivedSupplierVariant(current.value.id,pendingUnlink.value.id,current.value.version);ok=true;pendingUnlink.value=undefined}catch(e){error.value=e instanceof Error?e.message:'解除失败';impact.value=undefined}finally{saving.value=false}if(ok)await refresh()}
onMounted(()=>{void refresh();window.addEventListener('focus',refresh)});onBeforeUnmount(()=>{alive=false;window.removeEventListener('focus',refresh)})
</script>
<template>
  <AccessDialog
    title="删除供应商 · 关联检查"
    wide
    dialog-class="supplier-ui"
    :busy="saving"
    @close="emit('close')"
  >
    <div class="supplier-section-heading">
      <strong>{{ supplier.name }}</strong><button
        :disabled="loading||saving"
        @click="refresh"
      >
        刷新清单
      </button>
    </div><p v-if="loading">
      正在检查关联…
    </p><template v-if="impact">
      <SupplierImpactSummary :impact="impact" /><p class="supplier-warning">
        {{ impact.total?'仍有关联，不能删除。可查看或编辑关联，已有业务历史建议保留供应商，需停用请使用列表中的独立停用操作。':'没有关联记录，删除后无法恢复，请确认不再使用。' }}
      </p><SupplierRelations
        v-if="impact.total"
        :id="supplier.id"
        deleting
        :busy="saving"
        :revision="revision"
        @unlink="pendingUnlink=$event"
      />
    </template><p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p><div
      v-if="pendingUnlink"
      class="supplier-warning"
    >
      确认解除 {{ pendingUnlink.name }} 的历史供应商关联？产品及业务记录保留。<button
        :disabled="saving"
        @click="pendingUnlink=undefined"
      >
        取消
      </button><button
        :disabled="saving"
        @click="unlink"
      >
        确认解除
      </button>
    </div><template #footer>
      <button
        :disabled="saving"
        @click="emit('close')"
      >
        关闭
      </button><button
        class="supplier-danger"
        :disabled="loading||saving||!impact||impact.total>0"
        @click="remove"
      >
        确认删除
      </button>
    </template>
  </AccessDialog>
</template>
