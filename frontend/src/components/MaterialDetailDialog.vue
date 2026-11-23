<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import AccessDialog from './AccessDialog.vue'
import MaterialImage from './MaterialImage.vue'
import MaterialSupplierDetail from './MaterialSupplierDetail.vue'
import SupplierContactLinks from './SupplierContactLinks.vue'
import ProductHistoryTable from './ProductHistoryTable.vue'
import ListPagination from './ListPagination.vue'
import { materialManagementApi, type MaterialDetail, type MaterialProduct } from '@/api/materialManagement'
import type { ProductHistoryDto } from '@/api/domain'
import { materialNumber } from '@/ui/materialQuantity'
import { beijingDateTime } from '@/utils/dateTime'
import { session } from '@/state/session'
const props=defineProps<{id:string;readOnly?:boolean}>(),emit=defineEmits<{close:[];edit:[];inventory:[]}>()
const data=ref<MaterialDetail>(),tab=ref('basic'),loading=ref(false),error=ref(''),page=ref(1),size=ref(20),total=ref(0),products=ref<MaterialProduct[]>([]),events=ref<ProductHistoryDto[]>([]),supplierId=ref('')
const tabs=computed(()=>[{id:'basic',name:'基础资料'},{id:'suppliers',name:'供应商'},...(session.canAccess(['products.read'])?[{id:'products',name:'关联产品'}]:[]),{id:'history',name:'变更记录'}])
let sequence=0
async function load(){const seq=++sequence;loading.value=true;error.value='';try{if(tab.value==='history'){const r=await materialManagementApi.history(props.id,page.value-1,size.value);if(seq===sequence){events.value=r.items;total.value=r.total}}else if(tab.value==='products'){const r=await materialManagementApi.products(props.id,page.value-1,size.value);if(seq===sequence){products.value=r.items;total.value=r.totalCount}}else {const r=await materialManagementApi.detail(props.id);if(seq===sequence)data.value=r}}catch(e){if(seq===sequence)error.value=e instanceof Error?e.message:'资料读取失败'}finally{if(seq===sequence)loading.value=false}}
watch([tab,page,size],load)
function changeTab(value:string){tab.value=value;page.value=1}
onMounted(load);onBeforeUnmount(()=>{sequence++})
</script>
<template>
  <AccessDialog
    title="辅料详情"
    wide
    dialog-class="materials-ui material-detail"
    @close="emit('close')"
  >
    <div
      v-if="data"
      class="material-summary"
    >
      <MaterialImage
        :name="data.material.name"
        :image-asset-id="data.material.imageAssetId"
        :image-url="data.material.imageUrl"
      /><div><strong>{{ data.material.name }}</strong><p>{{ data.material.materialType }} · {{ data.material.status==='ACTIVE'?'启用':'停用' }}</p><small>更新于 {{ beijingDateTime(data.material.updatedAt) }}</small></div>
    </div>
    <nav
      class="material-tabs"
      aria-label="辅料资料分类"
    >
      <button
        v-for="t in tabs"
        :key="t.id"
        :disabled="!data"
        :aria-pressed="tab===t.id"
        @click="changeTab(t.id)"
      >
        {{ t.name }}
      </button>
    </nav>
    <p v-if="loading">
      正在加载…
    </p><p
      v-else-if="error"
      role="alert"
    >
      {{ error }} <button @click="load">
        重试
      </button>
    </p>
    <template v-else-if="data">
      <dl
        v-if="tab==='basic'"
        class="material-facts"
      >
        <dt>规格</dt><dd>{{ data.material.specification||'—' }}</dd><dt>基础单位</dt><dd>{{ data.material.unit }} <small>{{ data.quantityScale?'测量单位，最多四位小数':'计数单位，整数' }}</small></dd><template v-if="session.canAccess(['costs.read'])">
          <dt>成本价</dt><dd>{{ data.price&&data.price.unit===data.material.unit?`¥ ${materialNumber(data.price.netPrice)} / ${data.material.unit}`:'未维护或需重新核定' }} <small>人民币，不含税</small></dd>
        </template><dt>备注</dt><dd>{{ data.material.remark||'—' }}</dd>
      </dl>
      <div
        v-if="tab==='suppliers'"
        class="material-table-wrap"
      >
        <table
          v-if="data.suppliers.length"
          class="data-table"
        >
          <thead><tr><th>供应商</th><th>联系人 / 微信</th><th>采购 / 电话</th><th>关系备注</th></tr></thead><tbody>
            <tr
              v-for="s in data.suppliers"
              :key="s.supplierId"
            >
              <td>
                <button
                  class="material-link"
                  @click="supplierId=s.supplierId"
                >
                  {{ s.supplierName }}
                </button><small>{{ s.primary?'主供应商 · ':'' }}{{ s.status==='ACTIVE'?'启用':'已停用' }}</small>
              </td><td>{{ s.contactPerson||'—' }}<small>{{ s.wechatId||'—' }}</small></td><td>
                <SupplierContactLinks
                  :supplier="s"
                  :purchase-url="s.purchaseUrl"
                />
              </td><td>{{ s.remark||'—' }}</td>
            </tr>
          </tbody>
        </table><p v-else>
          暂无关联供应商
        </p>
      </div>
      <div
        v-if="tab==='products'"
        class="material-table-wrap"
      >
        <table
          v-if="products.length"
          class="data-table"
        >
          <thead><tr><th>产品 / 子体</th><th>标准用量</th><th>损耗率</th><th>状态</th></tr></thead><tbody>
            <tr
              v-for="p in products"
              :key="p.variantId"
            >
              <td>
                <RouterLink
                  v-if="!p.archived"
                  :to="`/products/${p.productId}`"
                >
                  {{ p.name }}
                </RouterLink><span v-else>{{ p.name }}</span><small>{{ p.productName }}</small>
              </td><td class="material-number">
                {{ materialNumber(p.quantity) }} {{ p.unit }}
              </td><td class="material-number">
                {{ materialNumber(p.lossRate*100) }}%
              </td><td>{{ p.archived?'产品已归档':p.enabled?'关联有效':'关联停用' }}</td>
            </tr>
          </tbody>
        </table><p v-else>
          暂无关联产品
        </p>
      </div>
      <ProductHistoryTable
        v-if="tab==='history'"
        :entries="events"
      />
    </template>
    <ListPagination
      v-if="['products','history'].includes(tab)"
      v-model:page="page"
      v-model:page-size="size"
      :total="total"
      :disabled="loading"
    />
    <template #footer>
      <button @click="emit('close')">
        关闭
      </button><button
        v-if="!readOnly&&session.canAccess(['inventory.read'])"
        @click="emit('inventory')"
      >
        查看库存
      </button><button
        v-if="!readOnly&&data?.material.status==='ACTIVE'&&session.canAccess(['materials.write'])"
        class="ui-button-primary"
        @click="emit('edit')"
      >
        编辑资料
      </button>
    </template>
  </AccessDialog>
  <MaterialSupplierDetail
    v-if="supplierId"
    :id="supplierId"
    @close="supplierId=''"
  />
</template>
