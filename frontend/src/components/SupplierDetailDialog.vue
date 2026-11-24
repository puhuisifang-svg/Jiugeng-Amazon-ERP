<script setup lang="ts">
import '@/styles/supplierManagement.css'
import {onMounted,onBeforeUnmount,ref,watch} from 'vue'
import {domainApi,type SupplierDto} from '@/api/domain'
import AccessDialog from './AccessDialog.vue'
import SupplierImage from './SupplierImage.vue'
import SupplierContactValue from './SupplierContactValue.vue'
import SupplierRelations from './SupplierRelations.vue'
import {beijingDateTime} from '@/utils/dateTime'
const props=defineProps<{id:string}>();defineEmits<{close:[]}>()
const item=ref<SupplierDto>(),tab=ref('basic'),loading=ref(false),error=ref('');let generation=0
async function load(){const seq=++generation;loading.value=true;error.value='';try{const r=await domainApi.supplier(props.id);if(seq===generation)item.value=r}catch(e){if(seq===generation)error.value=e instanceof Error?e.message:'供应商读取失败'}finally{if(seq===generation)loading.value=false}}
watch(tab,()=>{if(tab.value!=='relations')void load();else{generation++;loading.value=false;error.value=''}});onMounted(load);onBeforeUnmount(()=>generation++)
</script>
<template>
  <AccessDialog
    title="供应商详情"
    wide
    dialog-class="supplier-ui supplier-detail"
    @close="$emit('close')"
  >
    <div
      v-if="item"
      class="supplier-summary"
    >
      <SupplierImage
        :name="item.name"
        :image-asset-id="item.qrImageId"
        :image-url="item.qrImageUrl"
      /><div><strong>{{ item.name }}</strong><p>{{ item.categories?.map(c=>c==='PRODUCT'?'产品':'辅料').join('、') }} · {{ item.status==='ACTIVE'?'启用':'停用' }}</p><small>更新于 {{ item.updatedAt?beijingDateTime(item.updatedAt):'—' }}</small></div>
    </div><nav class="supplier-tabs">
      <button
        v-for="t in [{id:'basic',label:'基础资料'},{id:'relations',label:'关联业务'}]"
        :key="t.id"
        :aria-pressed="tab===t.id"
        :disabled="!item"
        @click="tab=t.id"
      >
        {{ t.label }}
      </button>
    </nav><p v-if="loading">
      正在读取…
    </p><p
      v-else-if="error"
      role="alert"
    >
      {{ error }} <button @click="load">
        重试
      </button>
    </p><template v-else-if="item">
      <dl
        v-if="tab==='basic'"
        class="supplier-facts"
      >
        <dt>联系人</dt><dd>{{ item.contactPerson||'—' }}</dd><dt>电话</dt><dd>
          <SupplierContactValue
            :value="item.phone"
            phone
          />
        </dd><dt>微信</dt><dd><SupplierContactValue :value="item.wechatId" /></dd><dt>采购来源</dt><dd>
          <a
            v-if="item.sourceUrl"
            :href="item.sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
          >打开 1688 采购来源 ↗</a><span v-else>未填写</span>
        </dd><dt>备注</dt><dd>{{ item.remark||'—' }}</dd>
      </dl><SupplierRelations
        v-else-if="tab==='relations'"
        :id="id"
      />
    </template>
  </AccessDialog>
</template>
