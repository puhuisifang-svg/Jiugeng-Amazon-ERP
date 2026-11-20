<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { samplesApi, type SampleLocation } from '@/api/samples'
import type { ProductHistoryDto } from '@/api/domain'
import { sampleActions, sampleHistoryChanges, sampleLinkedStatus } from '@/utils/sampleHistory'
import { beijingDateTime } from '@/utils/dateTime'
import { session } from '@/state/session'
import AccessDialog from './AccessDialog.vue'
import SampleImage from './SampleImage.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import ParentProductDetailDialog from './ParentProductDetailDialog.vue'
import SkuDetailDialog from './SkuDetailDialog.vue'
import { domainApi, type SkuListItemDto } from '@/api/domain'
import '@/styles/sampleManagement.css'
const props=defineProps<{id:string;writable:boolean}>()
const emit=defineEmits<{close:[];edit:[row:SampleLocation];print:[row:SampleLocation]}>()
const row=ref<SampleLocation>(),loading=ref(false),error=ref(''),tab=ref('basic')
const entries=ref<ProductHistoryDto[]>([]),historyError=ref(''),historyLoading=ref(false),page=ref(1),size=ref(20),total=ref(0)
const parentOpen=ref(false),sku=ref<SkuListItemDto>(),linkBusy=ref(false),linkError=ref('')
let sequence=0,historySequence=0,linkSequence=0
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await samplesApi.detail(props.id);if(seq===sequence)row.value=r}catch(e){if(seq===sequence)error.value=String(e instanceof Error?e.message:e)}finally{if(seq===sequence)loading.value=false}}
async function loadHistory(){const seq=++historySequence;historyLoading.value=true;historyError.value='';try{const r=await samplesApi.history(props.id,page.value-1,size.value);if(seq===historySequence){entries.value=r.items;total.value=r.total}}catch(e){if(seq===historySequence)historyError.value=String(e instanceof Error?e.message:e)}finally{if(seq===historySequence)historyLoading.value=false}}
async function openSku(){if(!row.value?.productId||linkBusy.value)return;const seq=++linkSequence;linkBusy.value=true;linkError.value='';try{const p=await domainApi.product(row.value.productId);const v=p.variants.find(v=>v.id===row.value?.variantId);if(seq!==linkSequence)return;if(!v)throw new Error('关联子体已不可用');sku.value={productId:p.id,productName:p.productName,productCode:p.productCode,productStatus:p.status,variant:v,businessAvailable:p.status==='ACTIVE'&&v.status==='ACTIVE'}}catch(e){if(seq===linkSequence)linkError.value=String(e instanceof Error?e.message:e)}finally{if(seq===linkSequence)linkBusy.value=false}}
watch(()=>props.id,()=>{row.value=undefined;tab.value='basic';page.value=1;void load()},{immediate:true})
watch(tab,v=>{if(v==='history')void loadHistory()})
onBeforeUnmount(()=>{++sequence;++historySequence;++linkSequence})
</script>
<template>
  <AccessDialog
    title="样品详情"
    dialog-class="samples-ui sample-detail-dialog"
    @close="emit('close')"
  >
    <p
      v-if="loading"
      role="status"
    >
      正在加载样品…
    </p>
    <p
      v-else-if="error"
      role="alert"
      class="access-danger"
    >
      {{ error }} <button
        type="button"
        @click="load"
      >
        重试
      </button>
    </p>
    <template v-else-if="row">
      <div class="sample-detail-heading">
        <SampleImage
          :image-id="row.displayImageId ?? row.sampleImageId"
          :image-url="row.displayImageUrl"
          :alt="row.skuName"
        /><div>
          <h3>{{ row.skuName }}</h3><span class="sample-shelf">{{ row.locationCode }}</span><span class="sample-source-tag">{{ row.variantId?'关联 SKU':'手动录入' }}</span><span
            v-if="row.deletedAt"
            class="access-danger"
          >已移除</span>
        </div>
      </div>
      <nav
        class="access-tabs"
        aria-label="样品详情页签"
      >
        <button
          type="button"
          :class="{active:tab==='basic'}"
          @click="tab='basic'"
        >
          登记资料
        </button><button
          type="button"
          :class="{active:tab==='history'}"
          @click="tab='history'"
        >
          变更记录
        </button>
      </nav>
      <template v-if="tab==='basic'">
        <dl class="sample-facts">
          <dt>规格</dt><dd>{{ row.specification || '未填写' }}</dd><dt>登记父体</dt><dd>{{ row.productName || '—' }}</dd><dt>更新时间</dt><dd>{{ beijingDateTime(row.updatedAt) }}</dd><dt>备注</dt><dd class="sample-prewrap">
            {{ row.remark || '未填写' }}
          </dd>
        </dl>
        <div
          v-if="row.variantId"
          class="sample-associated"
        >
          <strong>当前关联产品</strong><span>{{ sampleLinkedStatus(row.linkedStatus) }}</span><p>{{ row.currentProductName || row.productName }} / {{ row.currentSkuName || row.skuName }}</p>
          <template v-if="session.canAccess(['products.read']) && !['DELETED','UNAVAILABLE'].includes(row.linkedStatus ?? '')">
            <button
              type="button"
              class="text-action"
              @click="parentOpen=true"
            >
              查看父体
            </button><button
              type="button"
              class="text-action"
              :disabled="linkBusy"
              @click="openSku"
            >
              {{ linkBusy?'加载中…':'查看子体' }}
            </button>
          </template>
          <p
            v-if="linkError"
            role="alert"
            class="access-danger"
          >
            {{ linkError }}
          </p>
        </div>
        <div class="sample-photo-detail">
          <strong>存放照片</strong><SampleImage
            :image-id="row.storageImageId"
            :alt="`${row.skuName} · ${row.locationCode} 存放照片`"
          />
        </div>
        <p class="sample-hint">
          {{ row.imageSource==='CURRENT_PRODUCT'?'此历史记录未留存样品图片，目前展示当前产品图片；下次保存登记时留存。':row.snapshotImageUrl?'图片地址为登记时留存，外部地址的内容和可用性由来源网站决定。':'样品图片为独立留存，不随产品换图变化。' }}名称与规格保留登记资料。
        </p>
      </template>
      <template v-else>
        <p
          v-if="historyLoading"
          role="status"
        >
          正在加载变更记录…
        </p><p
          v-else-if="historyError"
          role="alert"
          class="access-danger"
        >
          {{ historyError }} <button
            type="button"
            @click="loadHistory"
          >
            重试
          </button>
        </p><p
          v-else-if="!entries.length"
          class="data-state"
        >
          暂无变更记录
        </p>
        <TableViewport v-else>
          <table class="data-table sample-history">
            <thead><tr><th>时间 / 操作人</th><th>操作</th><th>变更内容</th></tr></thead><tbody>
              <tr
                v-for="event in entries"
                :key="event.id"
              >
                <td>{{ beijingDateTime(event.occurredAt) }}<small>{{ event.actorDisplayName || event.actorLoginName }}</small></td><td>{{ sampleActions[event.action] || '维护登记' }}</td><td>
                  <dl>
                    <template
                      v-for="change in sampleHistoryChanges(event)"
                      :key="change.key"
                    >
                      <dt>{{ change.label }}</dt><dd><span>{{ change.before }}</span> → <strong>{{ change.after }}</strong><small v-if="change.key.endsWith('ImageId') && change.before===change.after">已更换图片</small></dd>
                    </template>
                  </dl>
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
        <ListPagination
          :total="total"
          :page="page"
          :page-size="size"
          :disabled="historyLoading"
          @update:page="page=$event;loadHistory()"
          @update:page-size="size=$event;page=1;loadHistory()"
        />
      </template>
    </template>
    <template #footer>
      <button
        type="button"
        @click="emit('close')"
      >
        关闭
      </button><button
        v-if="row && !row.deletedAt"
        type="button"
        @click="emit('print',row)"
      >
        标签打印
      </button><button
        v-if="row && writable && !row.deletedAt"
        type="button"
        class="primary-action"
        @click="emit('edit',row)"
      >
        编辑登记
      </button>
    </template>
  </AccessDialog>
  <ParentProductDetailDialog
    v-if="parentOpen && row?.productId"
    :product-id="row.productId"
    :writable="false"
    @close="parentOpen=false"
    @sku="sku=$event"
  />
  <SkuDetailDialog
    v-if="sku"
    :detail-row="sku"
    :writable="false"
    @close="sku=undefined"
  />
</template>
