<script setup lang="ts">
import { beijingDateTime } from '@/utils/dateTime'


import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import { customsDocumentsApi, type CustomsDocument, type CustomsSummary } from '@/api/customsDocuments'
import CustomsDocumentEditor from '@/components/CustomsDocumentEditor.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import CustomsFilesDialog from '@/components/CustomsFilesDialog.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const fileRow=ref<CustomsSummary>(),fba=ref('')
const query=ref(''),status=ref(''),applied=ref({q:'',status:'',fba:''}),page=ref(1),size=ref(20),rows=ref<CustomsSummary[]>([]),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),success=ref(''),editor=ref(false),current=ref<CustomsDocument>(),copy=ref(false),deleting=ref('')
const writable=computed(()=>session.canAccess(['products.write'])),exportable=computed(()=>session.canAccess(['products.export']))
const labels={DRAFT:'草稿',READY:'资料已核对',VOID:'已作废'}
let sequence=0
const message=(e:unknown)=>e instanceof Error?e.message:'操作失败，请重试'
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await customsDocumentsApi.list(applied.value.q,applied.value.status,page.value-1,size.value,applied.value.fba);if(seq===sequence){rows.value=r.items;total.value=r.totalCount;const last=Math.max(1,Math.ceil(r.totalCount/size.value));if(page.value>last){page.value=last;await load()}}}catch(e){if(seq===sequence)error.value=message(e)}finally{if(seq===sequence)loading.value=false}}
function search(){applied.value={q:query.value.trim(),status:status.value,fba:fba.value.trim()};page.value=1;void load()}
async function open(row?:CustomsSummary,duplicate=false){if(busy.value)return;error.value='';busy.value=true;try{current.value=row?await customsDocumentsApi.get(row.id):undefined;copy.value=duplicate;editor.value=true}catch(e){error.value=message(e)}finally{busy.value=false}}
async function cancel(row:CustomsSummary){if(!await confirmAction('作废这票报关资料？历史资料保留，作废后不可修改或导出。'))return;busy.value=true;try{await customsDocumentsApi.void(row.id,row.version);success.value='单据已作废';await load()}catch(e){error.value=message(e)}finally{busy.value=false}}
async function remove(row:CustomsSummary){if(busy.value)return;if(!await confirmAction(`确定删除报关单据「${row.documentNo}」？删除后将从列表移除，不能再查看、编辑或导出；产品资料及审计记录保留。`))return;busy.value=true;deleting.value=row.id;error.value='';try{await customsDocumentsApi.remove(row.id,row.version);success.value='单据已删除';await load()}catch(e){error.value=message(e)}finally{busy.value=false;deleting.value=''}}
async function exportFile(row:CustomsSummary){busy.value=true;error.value='';try{await customsDocumentsApi.export(row.id,row.documentNo);success.value='五张报关单证已导出'}catch(e){error.value=message(e)}finally{busy.value=false}}
onMounted(load);onBeforeUnmount(()=>++sequence)
</script>
<template>
  <section class="page-stack customs-documents-page">
    <form
      class="panel filter-panel ui-filter-row"
      @submit.prevent="search"
    >
      <label>单据编号<input
        v-model="query"
        maxlength="128"
        placeholder="搜索单据编号"
      ></label><label class="customs-fba-filter">FBA编号<input
        v-model="fba"
        maxlength="128"
        placeholder="搜索FBA编号"
      ></label><label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部状态</option><option value="PENDING_FILES">文档待上传</option><option value="COMPLETED">已完成</option><option value="DRAFT">草稿</option><option value="READY">资料已核对</option><option value="VOID">已作废</option></select></label><button
        type="submit"
        :disabled="loading"
      >
        搜索
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="query='';fba='';status='';search()"
      >
        重置
      </button>
    </form>
    <div class="panel material-toolbar">
      <button
        v-if="writable"
        type="button"
        :disabled="busy"
        @click="open()"
      >
        新建报关单据
      </button><span class="muted">按票整理资料，一次导出报关单、申报要素、合同、发票和装箱单。</span>
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
              <th>单据编号</th><th>FBA编号</th><th>FBA 仓库代码</th><th>件数</th><th>运费</th><th>境内发货人</th><th>境外收货人</th><th>运抵国</th><th>商品项数</th><th>状态</th><th>更新时间</th><th class="customs-doc-actions">
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
                  type="button"
                  class="text-action"
                  :disabled="busy"
                  @click="open(r)"
                >
                  {{ r.documentNo }}
                </button>
              </td><td class="customs-fba">
                {{ r.fbaNo || '—' }}
              </td><td>{{ r.warehouse || '—' }}</td><td class="customs-count">
                {{ r.cartonCount ?? '—' }}
              </td><td>{{ r.freight || '—' }}</td><td>{{ r.seller || '—' }}</td><td>{{ r.buyer || '—' }}</td><td>{{ r.destination || '—' }}</td><td>{{ r.itemCount }}</td><td>
                <span
                  class="customs-status"
                  :class="r.status==='VOID'?'void':r.fileStatus==='COMPLETED'?'ready':'draft'"
                >{{ r.status==='VOID'?labels.VOID:r.fileStatus==='COMPLETED'?'已完成':'文档待上传' }}</span>
              </td><td>{{ beijingDateTime(r.updatedAt) }}</td><td class="customs-doc-actions">
                <button
                  type="button"
                  :disabled="busy"
                  @click="open(r)"
                >
                  详情
                </button><button
                  v-if="exportable && r.status!=='VOID'"
                  type="button"
                  :disabled="busy"
                  @click="exportFile(r)"
                >
                  导出
                </button><button
                  type="button"
                  :disabled="busy"
                  @click="fileRow=r"
                >
                  文件上传
                </button><RowMoreActions
                  v-if="writable"
                  :label="`${r.documentNo} 更多操作`"
                >
                  <button
                    v-if="writable"
                    type="button"
                    :disabled="busy"
                    @click="open(r,true)"
                  >
                    复制
                  </button><button
                    v-if="writable && r.status!=='VOID'"
                    type="button"
                    :disabled="busy"
                    @click="cancel(r)"
                  >
                    作废
                  </button><button
                    v-if="writable"
                    type="button"
                    class="customs-delete"
                    :disabled="busy"
                    @click="remove(r)"
                  >
                    {{ deleting===r.id?'删除中…':'删除' }}
                  </button>
                </RowMoreActions>
              </td>
            </tr><tr v-if="!rows.length">
              <td
                colspan="12"
                class="data-state"
              >
                {{ loading?'正在加载…':'暂无报关单据，点击“新建报关单据”整理本票资料。' }}
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport><ListPagination
        :page="page"
        :page-size="size"
        :total="total"
        :disabled="loading"
        @update:page="if(page!==$event){page=$event;load()}"
        @update:page-size="size=$event;page=1;load()"
      />
    </article>
    <CustomsFilesDialog
      v-if="fileRow"
      :id="fileRow.id"
      :document-no="fileRow.documentNo"
      :writable="writable&&fileRow.status!=='VOID'"
      @close="fileRow=undefined"
      @changed="load()"
    />
    <CustomsDocumentEditor
      v-if="editor"
      :document="current"
      :copy="copy"
      :writable="writable"
      :exportable="exportable"
      @close="editor=false"
      @saved="load()"
    />
  </section>
</template>
<style>
.customs-documents-page .customs-fba{white-space:nowrap}.customs-documents-page .customs-count{text-align:right;font-variant-numeric:tabular-nums}
.customs-documents-page .material-toolbar{display:flex;gap:16px;align-items:center;flex-wrap:wrap}.customs-documents-page .material-toolbar span{font-size:12px}.customs-documents-page > .table-panel .data-table{min-width:1500px}.customs-documents-page .data-table td{padding:10px;font-size:13px}.customs-documents-page .customs-doc-actions{position:sticky;right:0;background:var(--panel,#fff);min-width:240px;white-space:nowrap}.customs-documents-page .customs-doc-actions button,.customs-documents-page .text-action{background:none;border:0;color:#2864c5;padding:4px 6px;cursor:pointer}.customs-status{padding:3px 8px;border-radius:4px;background:#f3f4f6;color:#5e6572}.customs-status.ready{background:#edf7f0;color:#21834a}.customs-status.draft{background:#fff7e6;color:#986b16}
</style>

<style scoped>
.customs-documents-page .customs-doc-actions .customs-delete{color:#c62828}.customs-documents-page .customs-doc-actions button:disabled{opacity:.5;cursor:not-allowed}
</style>

<style scoped>
.customs-documents-page .filter-panel .customs-fba-filter{flex:0 1 370px;max-width:100%}.customs-documents-page .filter-panel .customs-fba-filter input{width:290px;flex:1;min-width:0}@media(max-width:560px){.customs-documents-page .filter-panel .customs-fba-filter{flex-basis:100%}.customs-documents-page .filter-panel .customs-fba-filter input{width:100%}}
</style>
