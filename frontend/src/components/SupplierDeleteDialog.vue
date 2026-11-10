<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref} from 'vue'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import {domainApi,type SupplierDto,type SupplierDependency} from '@/api/domain'
import {session} from '@/state/session'
const props=defineProps<{supplier:SupplierDto}>()
const emit=defineEmits<{close:[];deleted:[];disabled:[]}>()
const rows=ref<SupplierDependency[]>([]),loading=ref(false),saving=ref(false),error=ref(''),checked=ref(false),current=ref(props.supplier),page=ref(1),size=ref(20)
const pendingUnlink=ref<SupplierDependency>()
const hasPurchase=computed(()=>rows.value.some(r=>r.type==='PURCHASE'))
const visible=computed(()=>rows.value.slice((page.value-1)*size.value,page.value*size.value))
const labels={PRODUCT:'产品 SKU',MATERIAL:'辅料',PURCHASE:'采购单'}
async function refresh(){if(loading.value||saving.value)return;loading.value=true;checked.value=false;error.value='';try{const [deps,supplier]=await Promise.all([domainApi.supplierDependencies(props.supplier.id),domainApi.supplier(props.supplier.id)]);rows.value=deps;current.value=supplier;page.value=1;checked.value=true}catch(e){error.value=e instanceof Error?e.message:'关联检查失败，请重试'}finally{loading.value=false}}
function link(r:SupplierDependency){if(r.archived||!r.accessible)return '';if(r.type==='PURCHASE')return `/purchases/orders/${encodeURIComponent(r.id)}`;const capability=r.type==='PRODUCT'?'products.write':'materials.write';if(!session.canAccess([capability,'suppliers.write']))return '';return r.type==='PRODUCT'?`/products?editProduct=${encodeURIComponent(r.parentId)}&variantId=${encodeURIComponent(r.id)}`:`/materials?editMaterial=${encodeURIComponent(r.id)}`}
function canUnlink(r:SupplierDependency){return r.accessible&&r.archived&&r.type==='PRODUCT'&&session.canAccess(['products.write','suppliers.write','suppliers.delete'])}
async function unlink(r:SupplierDependency){if(saving.value||loading.value||!canUnlink(r))return;saving.value=true;error.value='';let ok=false;try{await domainApi.unlinkArchivedSupplierVariant(current.value.id,r.id,current.value.version);ok=true}catch(e){error.value=e instanceof Error?e.message:'解除失败，请刷新后重试'}finally{saving.value=false}if(ok){pendingUnlink.value=undefined;await refresh()}}
async function remove(){if(!checked.value||loading.value||saving.value||rows.value.length)return;saving.value=true;error.value='';try{await domainApi.deleteSupplier(current.value.id,current.value.version);emit('deleted')}catch(e){error.value=e instanceof Error?e.message:'删除失败，请刷新关联检查';checked.value=false}finally{saving.value=false}}
async function disable(){if(saving.value||loading.value)return;saving.value=true;error.value='';try{await domainApi.disableSupplier(current.value.id,current.value.version);emit('disabled')}catch(e){error.value=e instanceof Error?e.message:'停用失败，请重试'}finally{saving.value=false}}
onMounted(()=>{void refresh();window.addEventListener('focus',refresh)});onUnmounted(()=>window.removeEventListener('focus',refresh))
</script>
<template>
  <AccessDialog
    title="删除供应商 · 关联检查"
    wide
    :busy="saving"
    @close="emit('close')"
  >
    <div class="dependency-summary">
      <div><strong>{{ supplier.name }}</strong><p>{{ loading?'正在检查关联…':checked?rows.length?`还有 ${rows.length} 项关联，请按下方指引处理`:'没有关联记录，可以确认删除':'请先完成关联检查' }}</p></div><button
        type="button"
        :disabled="loading||saving"
        @click="refresh"
      >
        刷新清单
      </button>
    </div>
    <p
      v-if="hasPurchase"
      class="dependency-warning"
    >
      仍有关联采购单。未收货的单据可在采购列表删除；已有收货记录的单据需保留，建议使用“停用供应商”。
    </p>
    <p
      v-else-if="rows.length"
      class="dependency-help"
    >
      点击“前往处理”在新标签页打开对应资料，移除供应商关联并保存；回到此窗口会重新检查，也可手动刷新。
    </p>
    <div
      v-if="rows.length"
      class="dependency-table"
    >
      <table class="data-table">
        <thead><tr><th>类型</th><th>需要处理的记录</th><th>处理方式</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="(r,i) in visible"
            :key="`${r.type}-${r.id}-${i}`"
          >
            <td>{{ labels[r.type] }}</td><td><strong>{{ r.code || '受限记录' }}{{ r.archived?'（已删除）':'' }}</strong><small>{{ r.type==='PURCHASE'&&r.accessible?'采购历史记录':r.name }}</small></td><td>{{ r.archived?'产品或 SKU 已删除，可解除历史关联':r.type==='PURCHASE'?'未收货可删除；已收货需保留':'编辑资料，移除该供应商并保存' }}</td><td>
              <button
                v-if="canUnlink(r)"
                type="button"
                :disabled="loading||saving"
                @click="pendingUnlink=r"
              >
                解除历史关联
              </button><a
                v-else-if="link(r)"
                :href="link(r)"
                target="_blank"
                rel="noopener noreferrer"
              >{{ r.type==='PURCHASE'?'查看采购单':'前往处理' }} ↗</a><span
                v-else
                class="dependency-help"
              >请联系有权限的同事</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p
      v-if="checked&&!rows.length"
      class="dependency-warning"
    >
      删除后无法恢复。确认不再使用该供应商后再删除。
    </p>
    <p
      v-if="error"
      role="alert"
      class="data-state--error"
    >
      {{ error }}
    </p>
    <div
      v-if="pendingUnlink"
      class="dependency-warning"
      role="alert"
    >
      确认解除 {{ pendingUnlink.code }} 的历史供应商关联？产品及历史业务记录保留。<div>
        <button
          type="button"
          :disabled="saving"
          @click="pendingUnlink=undefined"
        >
          取消
        </button><button
          type="button"
          :disabled="saving||loading"
          @click="unlink(pendingUnlink)"
        >
          确认解除
        </button>
      </div>
    </div>
    <div class="dependency-pagination">
      <ListPagination
        v-if="rows.length"
        :page="page"
        :page-size="size"
        :total="rows.length"
        :disabled="loading||saving"
        @update:page="page=$event"
        @update:page-size="size=$event;page=1"
      />
    </div>
    <template #footer>
      <button
        type="button"
        :disabled="saving"
        @click="emit('close')"
      >
        关闭
      </button><button
        v-if="hasPurchase&&current.status==='ACTIVE'&&session.canAccess(['suppliers.write'])"
        type="button"
        :disabled="loading||saving||!checked"
        @click="disable"
      >
        {{ saving?'处理中…':'停用供应商' }}
      </button><button
        type="button"
        class="supplier-confirm-delete"
        :disabled="!checked||loading||saving||rows.length>0"
        @click="remove"
      >
        {{ saving?'处理中…':'确认删除' }}
      </button>
    </template>
  </AccessDialog>
</template>
<style scoped>
.dependency-summary{display:flex;align-items:center;justify-content:space-between;gap:12px}.dependency-summary p{margin:6px 0;color:var(--muted)}.dependency-summary button{white-space:nowrap;flex-shrink:0}.dependency-warning{padding:10px;background:#fff7e6;color:#865b12;border:1px solid #ffe0a3;border-radius:4px}.dependency-help{color:var(--muted);font-size:12px}.dependency-table{overflow:auto;margin-top:12px}.dependency-table table{min-width:660px}.dependency-table small{display:block;color:var(--muted);margin-top:4px}.dependency-table a{color:#2673c9;white-space:nowrap}.supplier-confirm-delete:not(:disabled){background:#fff1f0!important;color:#b42318!important;border-color:#efb6b0!important}
</style>
