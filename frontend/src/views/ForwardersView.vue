<script setup lang="ts">
import { beijingDateTime } from '@/utils/dateTime'


import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { forwardersApi, carrierNames, type ForwarderDocument, type ForwarderSummary } from '@/api/forwarders'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import { vSingleSelect } from '@/directives/singleSelect'
import ForwarderEditor from '@/components/ForwarderEditor.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const query=ref(''),carrier=ref(''),applied=ref({q:'',carrier:''}),page=ref(1),size=ref(20),rows=ref<ForwarderSummary[]>([]),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),success=ref(''),editor=ref(false),current=ref<ForwarderDocument>()
const writable=computed(()=>session.canAccess(['forwarders.write'])),exportable=computed(()=>session.canAccess(['forwarders.export'])),deletable=computed(()=>session.canAccess(['forwarders.delete']))
let sequence=0
const message=(e:unknown)=>e instanceof Error?e.message:'操作失败，请重试'
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await forwardersApi.list(applied.value.q,applied.value.carrier,page.value-1,size.value);if(seq===sequence){rows.value=r.items;total.value=r.totalCount;const last=Math.max(1,Math.ceil(r.totalCount/size.value));if(page.value>last){page.value=last;await load()}}}catch(e){if(seq===sequence)error.value=message(e)}finally{if(seq===sequence)loading.value=false}}
function search(){applied.value={q:query.value.trim(),carrier:carrier.value};page.value=1;void load()}
async function open(row?:ForwarderSummary){if(busy.value)return;busy.value=true;error.value='';try{current.value=row?await forwardersApi.get(row.id):undefined;editor.value=true}catch(e){error.value=message(e)}finally{busy.value=false}}
async function remove(row:ForwarderSummary){if(busy.value||!await confirmAction(`删除货代单据「${row.documentNo}」？删除后不再显示，产品资料和审计记录保留。`))return;busy.value=true;error.value='';try{await forwardersApi.remove(row.id,row.version);success.value='单据已删除';await load()}catch(e){error.value=message(e)}finally{busy.value=false}}
async function exportFile(row:ForwarderSummary){if(busy.value)return;busy.value=true;error.value='';try{await forwardersApi.export(row);success.value=`${carrierNames[row.carrier]}模板已导出`}catch(e){error.value=message(e)}finally{busy.value=false}}
onMounted(load);onBeforeUnmount(()=>++sequence)
</script>
<template>
  <section class="page-stack forwarders-page">
    <form
      class="panel filter-panel ui-filter-row"
      @submit.prevent="search"
    >
      <label>单据编号<input
        v-model="query"
        maxlength="128"
        placeholder="搜索单据编号"
      ></label>
      <label>合作货代<select
        v-model="carrier"
        v-single-select
      ><option value="">全部货代</option><option value="KAIQI">凯琦</option><option value="HUAMAO">华茂万通</option></select></label>
      <button
        type="submit"
        :disabled="loading"
      >
        搜索
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="query='';carrier='';search()"
      >
        重置
      </button>
    </form>
    <div class="panel material-toolbar">
      <button
        v-if="writable"
        class="ui-button-primary"
        :disabled="busy"
        @click="open()"
      >
        新建货代单据
      </button><span class="muted">按货代原模板导出 · 一票多 SKU · 支持混装箱 · 币种 USD</span>
    </div>
    <SuccessToast :message="success" /><p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p>
    <article class="panel table-panel">
      <TableViewport>
        <table class="data-table">
          <thead>
            <tr>
              <th>单据编号</th><th>合作货代</th><th>运单号 / 批次号</th><th>FBA 仓库</th><th>商品项数</th><th>箱数</th><th>总货值 USD</th><th>资料状态</th><th>更新时间</th><th class="forwarder-actions">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="r in rows"
              :key="r.id"
            >
              <td>
                <button
                  class="text-action"
                  :disabled="busy"
                  @click="open(r)"
                >
                  {{ r.documentNo }}
                </button>
              </td><td>{{ carrierNames[r.carrier] }}</td><td>{{ r.waybill||'—' }}</td><td>{{ r.warehouse||'—' }}</td><td>{{ r.itemCount }}</td><td>{{ r.cartons }}</td><td>{{ r.amount }}</td><td>
                <span
                  class="forwarder-status"
                  :class="{ready:r.status==='READY'}"
                >{{ r.status==='READY'?'已核对':'草稿' }}</span>
              </td><td>{{ beijingDateTime(r.updatedAt) }}</td><td class="forwarder-actions">
                <button
                  :disabled="busy"
                  @click="open(r)"
                >
                  详情
                </button><button
                  v-if="exportable"
                  :disabled="busy"
                  @click="exportFile(r)"
                >
                  导出
                </button><button
                  v-if="deletable"
                  class="danger"
                  :disabled="busy"
                  @click="remove(r)"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td
                colspan="10"
                class="data-state"
              >
                {{ loading?'正在加载…':'暂无货代单据，选择货代并填写装箱资料，即可导出对应模板。' }}
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport><ListPagination
        :page="page"
        :page-size="size"
        :total="total"
        :disabled="loading"
        @update:page="page=$event;load()"
        @update:page-size="size=$event;page=1;load()"
      />
    </article>
    <ForwarderEditor
      v-if="editor"
      :document="current"
      :writable="writable"
      :exportable="exportable"
      @close="editor=false"
      @saved="load()"
    />
  </section>
</template>
<style scoped>
.forwarders-page .data-table{min-width:1150px}.forwarder-actions{position:sticky;right:0;background:white;white-space:nowrap;min-width:145px}.data-table th.forwarder-actions{background:#f6f8fb;z-index:3}.forwarder-actions button,.text-action{background:none;border:0;color:#2864c5;padding:4px 6px;cursor:pointer;min-height:28px}.forwarder-actions .danger{color:#b42318}.forwarder-status{display:inline-block;padding:3px 8px;border-radius:4px;background:#f0f3f7;color:#64748b;font-size:12px}.forwarder-status.ready{background:#edf7f1;color:#21834a}
</style>
