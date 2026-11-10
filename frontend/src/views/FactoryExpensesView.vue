<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { factoryApi, type FactoryRecord, type FactoryEvent } from '@/api/factory'
import { getSessionToken } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import { createRequestId } from '@/utils/requestId'
import { localDay, factoryMoney } from '@/utils/factoryPresentation'
import AccessDialog from '@/components/AccessDialog.vue'
import DatePicker from '@/components/DatePicker.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import FactoryAttachments from '@/components/FactoryAttachments.vue'
const writable=computed(()=>session.canAccess(['factory.expenses.write']))
const removable=computed(()=>session.canAccess(['factory.expenses.remove']))
const rows=ref<FactoryRecord[]>([]),q=ref(''),from=ref(localDay().slice(0,7)+'-01'),to=ref(localDay()),page=ref(1),size=ref(20),total=ref(0),amount=ref<string|number>(0),loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const applied=ref({q:'',from:from.value,to:to.value})
let sequence=0
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const result=await factoryApi.list('expenses',{...applied.value,page:page.value-1,size:size.value});if(seq!==sequence)return;rows.value=result.items;total.value=result.totalCount;amount.value=result.amount;if(page.value>1&&!rows.value.length){page.value--;await load()}}catch(e){if(seq===sequence)error.value=message(e)}finally{if(seq===sequence)loading.value=false}}
function search(){if(from.value&&to.value&&from.value>to.value){error.value='开始日期不能晚于结束日期';return}applied.value={q:q.value,from:from.value,to:to.value};page.value=1;void load()}
function reset(){q.value='';from.value='';to.value='';search()}
const editor=ref(false),editing=ref<FactoryRecord>(),editorDialog=ref<InstanceType<typeof AccessDialog>>(),formError=ref(''),initial=ref(''),voucher=ref<File>(),voucherKey=ref(0),request=ref({content:'',key:''})
const form=reactive({name:'',date:localDay(),amount:'',payee:'',remark:''})
const serialize=()=>JSON.stringify([form,voucher.value?.name,voucher.value?.size,voucher.value?.lastModified])
const dirty=computed(()=>serialize()!==initial.value)
function edit(row?:FactoryRecord){editing.value=row;Object.assign(form,{name:row?.name??'',date:row?.date??localDay(),amount:row?.amount===undefined?'':String(row.amount),payee:row?.payee??'',remark:row?.remark??''});voucher.value=undefined;voucherKey.value++;request.value={content:'',key:''};formError.value='';initial.value=serialize();editor.value=true}
async function save(){if(busy.value)return;busy.value=true;formError.value='';try{
 const data={...form,...(editing.value?{id:editing.value.id,version:editing.value.version}:{})};const content=JSON.stringify(data);if(content!==request.value.content)request.value={content,key:createRequestId()};
 const saved=await factoryApi.command('expenses','save',{...data,requestId:request.value.key});editing.value=saved;
 if(voucher.value){const body=new FormData();body.append('file',voucher.value);const response=await fetch(`${runtimeConfig.apiBaseUrl}/v1/factory/expenses/${saved.id}/attachments`,{method:'POST',body,headers:{Authorization:`Bearer ${getSessionToken()}`}});if(!response.ok){const problem=await response.json().catch(()=>null) as {detail?:string}|null;throw new Error(`开销已保存，凭证上传失败：${problem?.detail||'请重试保存以补传凭证'}`)}}
 editor.value=false;notice.value='开销已保存';await load();if(detail.value?.id===saved.id)detail.value=saved;
 }catch(e){formError.value=message(e)}finally{busy.value=false}}
async function remove(row:FactoryRecord){if(busy.value||!await confirmAction(`删除“${row.name}” ¥${factoryMoney(row.amount)}？此笔将不再计入开销汇总，操作记录保留。`))return;busy.value=true;error.value='';try{await factoryApi.command('expenses','remove',{id:row.id,version:row.version,requestId:createRequestId()});if(detail.value?.id===row.id)detail.value=undefined;notice.value='开销已删除';await load()}catch(e){error.value=message(e)}finally{busy.value=false}}
const detail=ref<FactoryRecord>(),tab=ref('overview'),events=ref<FactoryEvent[]>([]),historyPage=ref(1),historyTotal=ref(0),historyError=ref(''),historyBusy=ref(false)
async function show(row:FactoryRecord){error.value='';try{detail.value=await factoryApi.get('expenses',row.id);tab.value='overview';historyPage.value=1;events.value=[]}catch(e){error.value=message(e)}}
async function history(){if(!detail.value)return;historyBusy.value=true;historyError.value='';try{const result=await factoryApi.history('expenses',detail.value.id,historyPage.value-1,10);events.value=result.items;historyTotal.value=result.totalCount}catch(e){historyError.value=message(e)}finally{historyBusy.value=false}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
function beforeUnload(e:BeforeUnloadEvent){if(busy.value||(editor.value&&dirty.value)){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(async()=>busy.value?false:!editor.value||!dirty.value||await confirmAction('开销尚未保存，确定放弃并离开吗？'))
onMounted(()=>{void load();window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{sequence++;window.removeEventListener('beforeunload',beforeUnload)})
</script>
<template>
  <section class="page-stack factory-expenses">
    <SuccessToast :message="notice" />
    <form
      class="filter-panel ui-filter-row"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="q"
        maxlength="200"
        placeholder="开销项目、收款人或备注"
      ></label>
      <label>开始日期<DatePicker v-model="from" /></label><label>结束日期<DatePicker v-model="to" /></label>
      <button
        type="submit"
        :disabled="loading"
      >
        搜索
      </button><button
        type="button"
        class="filter-reset-button"
        @click="reset"
      >
        重置
      </button>
    </form>
    <div class="expense-toolbar">
      <button
        v-if="writable"
        type="button"
        class="primary-action"
        :disabled="busy"
        @click="edit()"
      >
        新增开销
      </button><span>筛选合计 <strong>¥ {{ factoryMoney(amount) }}</strong> · {{ total }} 笔</span>
    </div>
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p>
    <TableViewport>
      <table class="data-table ui-action-table">
        <thead><tr><th>日期</th><th>开销项目</th><th>金额（元）</th><th>收款人</th><th>备注</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
          >
            <td>{{ row.date }}</td><td><strong>{{ row.name }}</strong></td><td class="expense-amount">
              {{ factoryMoney(row.amount) }}
            </td><td>{{ row.payee||'—' }}</td><td>{{ row.remark||'—' }}</td><td class="expense-actions">
              <button
                type="button"
                @click="show(row)"
              >
                详情 / 凭证
              </button><button
                v-if="writable"
                type="button"
                :disabled="busy"
                @click="edit(row)"
              >
                编辑
              </button><button
                v-if="removable"
                type="button"
                class="danger"
                :disabled="busy"
                @click="remove(row)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td
              colspan="6"
              class="data-state"
            >
              {{ loading?'正在加载…':'暂无开销记录' }}
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      :total="total"
      :page="page"
      :page-size="size"
      :disabled="loading||busy"
      @update:page="page=$event;load()"
      @update:page-size="size=$event;page=1;load()"
    />
    <AccessDialog
      v-if="editor"
      ref="editorDialog"
      :title="editing?'编辑开销':'新增开销'"
      wide
      :busy="busy"
      :dirty="dirty"
      @close="editor=false"
    >
      <p
        v-if="formError"
        role="alert"
        class="access-danger"
      >
        {{ formError }}
      </p>
      <form
        id="expense-form"
        class="expense-form access-form"
        @submit.prevent="save"
      >
        <label>开销项目 *<input
          v-model="form.name"
          required
          maxlength="100"
          placeholder="如：电费、设备维修"
          :disabled="busy"
        ></label>
        <label>日期 *<DatePicker
          v-model="form.date"
          required
          :disabled="busy"
        /></label>
        <label>金额（元） *<input
          v-model="form.amount"
          type="number"
          min="0.01"
          max="999999999999"
          step="0.01"
          required
          :disabled="busy"
        ></label>
        
        <label>收款人<input
          v-model="form.payee"
          maxlength="100"
          placeholder="选填"
          :disabled="busy"
        ></label>
        <label class="wide">备注<textarea
          v-model="form.remark"
          maxlength="500"
          rows="2"
          :disabled="busy"
        /></label>
        <SingleFileUpload
          :key="voucherKey"
          title="开销凭证（可选）"
          accept=".png,.jpg,.jpeg,.pdf"
          :max-mb="5"
          deferred
          :disabled="busy"
          :upload="(file:File)=>{voucher=file}"
          hint="支持图片粘贴；保存时上传。后续可在详情中补充凭证。"
        />
        <button
          v-if="voucher"
          type="button"
          class="ui-image-remove"
          :disabled="busy"
          @click="voucher=undefined;voucherKey++"
        >
          移除待上传凭证
        </button>
      </form>
      <template #footer>
        <button
          type="button"
          :disabled="busy"
          @click="editorDialog?.requestClose()"
        >
          取消
        </button><button
          type="submit"
          form="expense-form"
          class="primary-button"
          :disabled="busy"
        >
          {{ busy?'保存中…':'保存' }}
        </button>
      </template>
    </AccessDialog>
    <AccessDialog
      v-if="detail"
      title="开销详情"
      wide
      @close="detail=undefined"
    >
      <nav class="ui-section-tabs">
        <button
          type="button"
          :class="{active:tab==='overview'}"
          @click="tab='overview'"
        >
          基本信息
        </button><button
          type="button"
          :class="{active:tab==='files'}"
          @click="tab='files'"
        >
          凭证附件
        </button><button
          type="button"
          :class="{active:tab==='history'}"
          @click="tab='history';history()"
        >
          操作记录
        </button>
      </nav>
      <dl
        v-if="tab==='overview'"
        class="expense-facts"
      >
        <dt>开销项目</dt><dd>{{ detail.name }}</dd><dt>日期</dt><dd>{{ detail.date }}</dd><dt>金额</dt><dd>¥ {{ factoryMoney(detail.amount) }}</dd><dt>收款人</dt><dd>{{ detail.payee||'—' }}</dd><dt>备注</dt><dd>{{ detail.remark||'—' }}</dd>
      </dl>
      <FactoryAttachments
        v-if="tab==='files'"
        :id="detail.id"
        kind="expenses"
        :writable="writable"
      />
      <template v-if="tab==='history'">
        <p
          v-if="historyError"
          role="alert"
          class="access-danger"
        >
          {{ historyError }}
        </p><TableViewport>
          <table class="data-table">
            <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>金额（元）</th></tr></thead><tbody>
              <tr
                v-for="event in events"
                :key="event.id"
              >
                <td>{{ new Date(event.time).toLocaleString() }}</td><td>{{ event.actor }}</td><td>{{ event.action==='EXPENSE_SAVE'?'保存开销':event.action==='EXPENSE_REMOVE'?'删除开销':event.action }}</td><td>{{ factoryMoney(event.after.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </TableViewport><ListPagination
          :total="historyTotal"
          :page="historyPage"
          :page-size="10"
          :disabled="historyBusy"
          @update:page="historyPage=$event;history()"
        />
      </template>
    </AccessDialog>
  </section>
</template>
<style scoped>
.expense-toolbar .primary-action{min-height:32px;padding:5px 12px;background:#2864c5;border:1px solid #2864c5;border-radius:4px;color:white;font-size:13px;cursor:pointer}.expense-toolbar{display:flex;align-items:center;gap:14px;min-height:36px}.expense-toolbar span{color:var(--muted);font-size:13px}.expense-toolbar strong{color:var(--ink)}.expense-amount{font-variant-numeric:tabular-nums;text-align:right}.expense-actions{white-space:nowrap}.expense-actions button{border:0;background:transparent;color:var(--accent);padding:4px 8px;cursor:pointer}.expense-actions .danger{color:#b42318}.expense-form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.expense-form label{display:grid;gap:6px;font-size:13px}.expense-form .wide{grid-column:1/-1}.expense-facts{display:grid;grid-template-columns:100px 1fr 100px 1fr;gap:12px;font-size:13px}.expense-facts dt{color:var(--muted)}.expense-facts dd{margin:0;overflow-wrap:anywhere}.factory-expenses .data-table{min-width:800px}@media(max-width:650px){.expense-form{grid-template-columns:1fr}.expense-facts{grid-template-columns:90px 1fr}}
</style>
