<script setup lang="ts">
import { beijingDateTime, beijingDate } from '@/utils/dateTime'


import { vSingleSelect } from '@/directives/singleSelect'
import EditableSelect from '@/components/EditableSelect.vue'
import DatePicker from '@/components/DatePicker.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { financeApi, type FinanceSubject, type FinanceAccount, type FinanceBill, type BillFilter, type BillInput, type BillPage, type BillAttachment, type BillHistory } from '@/api/finance'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import VoucherUpload from '@/components/VoucherUpload.vue'
import FinanceVoucher from '@/components/FinanceVoucher.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
const dateString=(d:Date)=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
const today=beijingDate(),monthStart=`${today.slice(0,7)}-01`
const filter=reactive<BillFilter>({customerId:'',from:monthStart,to:today,direction:'',q:'',voided:'false'}),applied=ref<BillFilter>({...filter})
const subjects=ref<FinanceSubject[]>([]),legacySubjects=ref<FinanceSubject[]>([]),result=ref<BillPage>({items:[],totalCount:0,totals:[],accounts:[],months:[]}),page=ref(1),size=ref(20),tab=ref('bills'),loading=ref(false),busy=ref(false),error=ref(''),success=ref('')
const writable=computed(()=>session.canAccess(['finance.write']))
const activeSubject=computed(()=>[...subjects.value,...legacySubjects.value].find(s=>s.id===applied.value.customerId))
const total=computed(()=>result.value.totals[0]??{income:0,expense:0,balance:0,count:0})
const money=(v:number)=>new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}).format(v)
const message=(e:unknown)=>e instanceof Error?e.message:'操作失败，请重试'
let sequence=0
async function load(){if(!applied.value.customerId){++sequence;loading.value=false;result.value={items:[],totalCount:0,totals:[],accounts:[],months:[]};return}const seq=++sequence;loading.value=true;error.value='';try{const r=await financeApi.list(applied.value,page.value-1,size.value);if(seq===sequence){result.value=r;if(page.value>1&&!r.items.length){page.value--;await load()}}}catch(e){if(seq===sequence)error.value=message(e)}finally{if(seq===sequence)loading.value=false}}
async function loadSubjects(){const [customers,legacy]=await Promise.all([financeApi.subjects(),financeApi.legacySubjects()]);subjects.value=customers;legacySubjects.value=legacy;if(!filter.customerId){filter.customerId=customers[0]?.id??legacy[0]?.id??'';applied.value={...filter}}}
function search(){if(!filter.from||!filter.to||filter.from>filter.to){error.value='请选择有效日期范围';return}applied.value={...filter};result.value={items:[],totalCount:0,totals:[],accounts:[],months:[]};page.value=1;void load()}
function previousMonth(){const current=new Date(beijingDate()+'T12:00:00');filter.from=dateString(new Date(current.getFullYear(),current.getMonth()-1,1));filter.to=dateString(new Date(current.getFullYear(),current.getMonth(),0));search()}
function period(year=false){filter.from=year?`${today.slice(0,4)}-01-01`:monthStart;filter.to=today;search()}
function changePage(v:number){if(v===page.value)return;page.value=v;void load()}
function changeSize(v:number){size.value=v;page.value=1;void load()}
async function exportBills(){busy.value=true;error.value='';try{await financeApi.export(applied.value);success.value='账单已导出'}catch(e){error.value=message(e)}finally{busy.value=false}}
async function voidBill(b:FinanceBill){if(busy.value||!await confirmAction(`确认作废 ${b.date} 的${b.direction==='INCOME'?'收入':'支出'} ${money(b.amount)} 元？作废后不再计入有效账单汇总，原记录和凭证仍保留。`))return;busy.value=true;error.value='';try{await financeApi.void(b);success.value='账单已作废';await load()}catch(e){error.value=message(e)}finally{busy.value=false}}
const modal=ref<HTMLDialogElement>(),mode=ref<'bill'|'detail'|''>(''),formError=ref(''),discard=ref(false),initial=ref(''),editing=ref<FinanceBill>()
const bill=reactive<BillInput>({customerId:'',date:today,direction:'EXPENSE',category:'',detail:'',account:'',amount:'',remark:''})
const files=ref<File[]>([]),attachments=ref<BillAttachment[]>([]),history=ref<BillHistory[]>([]),detailLoading=ref(false)
const accounts=ref<FinanceAccount[]>([]),accountsLoading=ref(false)
const availableAccounts=computed(()=>accounts.value.filter(a=>a.active))
let accountSequence=0
async function loadAccounts(){const seq=++accountSequence,customerId=bill.customerId;accountsLoading.value=true;try{const rows=await financeApi.accounts(customerId);if(seq===accountSequence&&customerId===bill.customerId)accounts.value=rows}catch(e){if(seq===accountSequence)formError.value=message(e)}finally{if(seq===accountSequence)accountsLoading.value=false}}
async function removeAccount(name:string){const account=accounts.value.find(a=>a.name===name);if(!account||busy.value||!await confirmAction(`从此客户的历史选项中删除“${name}”？已保存账单不受影响。`))return;busy.value=true;formError.value='';try{await financeApi.removeAccount(account);accounts.value=accounts.value.filter(a=>a.id!==account.id);success.value='历史账户已移除'}catch(e){formError.value=message(e)}finally{busy.value=false}}
function snapshot(){return JSON.stringify([bill,files.value.map(f=>[f.name,f.size,f.lastModified])])}
const dirty=computed(()=>mode.value==='bill'&&initial.value!==snapshot())
async function show(kind:typeof mode.value){mode.value=kind;formError.value='';discard.value=false;initial.value=snapshot();await nextTick();modal.value?.showModal()}
function close(){modal.value?.close();mode.value='';discard.value=false;files.value=[];++accountSequence;accountsLoading.value=false}
function requestClose(){if(busy.value)return;if(dirty.value){discard.value=true;return}close()}
function discardChanges(){close()}
async function editBill(b?:FinanceBill){editing.value=b;files.value=[];attachments.value=[];history.value=[];Object.assign(bill,{customerId:b?.customerId??applied.value.customerId,date:b?.date??today,direction:b?.direction??'EXPENSE',category:b?.category??'',detail:b?.detail??'',account:b?.account??'',amount:b?String(b.amount):'',remark:b?.remark??'',version:b?.version});accounts.value=[];await show('bill');await loadAccounts();if(b)await loadDetails(b)}
async function detail(b:FinanceBill){editing.value=b;attachments.value=[];history.value=[];await show('detail');await loadDetails(b)}
async function loadDetails(b:FinanceBill){detailLoading.value=true;try{const [a,h]=await Promise.all([financeApi.attachments(b.id),financeApi.history(b.id)]);if(editing.value?.id===b.id){attachments.value=a;history.value=h}}catch(e){formError.value=message(e)}finally{detailLoading.value=false}}
const voucherUpload=ref<InstanceType<typeof VoucherUpload>>()
function pasteVoucher(e:ClipboardEvent){if(mode.value==='bill'&&!discard.value)voucherUpload.value?.paste(e)}
async function saveBill(){if(busy.value||accountsLoading.value)return;if(!bill.account){formError.value='请输入或选择收支账户';return;}busy.value=true;formError.value='';try{const saved=await financeApi.save({...bill,...(editing.value?{version:editing.value.version}:{})},editing.value?.id);editing.value=saved;bill.version=saved.version;while(files.value.length){attachments.value.push(await financeApi.upload(saved.id,files.value[0]!));files.value.shift()}initial.value=snapshot();close();success.value='账单及凭证已保存';await load()}catch(e){formError.value=`${message(e)}${editing.value?'（已保存的账单不会重复新增，可重试）':''}`}finally{busy.value=false}}
async function download(a:BillAttachment){if(!editing.value)return;formError.value='';try{await financeApi.download(editing.value.id,a)}catch(e){formError.value=message(e)}}
async function removeAttachment(a:BillAttachment){if(!editing.value||busy.value||!await confirmAction(`移除凭证 ${a.name}？`))return;busy.value=true;try{await financeApi.removeAttachment(editing.value.id,a.id);attachments.value=attachments.value.filter(x=>x.id!==a.id);success.value='凭证已移除';await load()}catch(e){formError.value=message(e)}finally{busy.value=false}}
const actions:Record<string,string>={FINANCE_BILL_CREATE:'新增账单',FINANCE_BILL_UPDATE:'修改账单',FINANCE_BILL_VOID:'作废账单',FINANCE_ATTACHMENT_ADD:'添加凭证',FINANCE_ATTACHMENT_REMOVE:'移除凭证'}
const labels:Record<string,string>={date:'日期',direction:'收支',category:'分类',detail:'明细',account:'账户',amount:'金额',remark:'备注',voided:'作废',name:'文件名'}
function changes(h:BillHistory){try{const before=JSON.parse(h.before||'{}') as Record<string,unknown>,after=JSON.parse(h.after||'{}') as Record<string,unknown>;return Object.entries(labels).filter(([key])=>JSON.stringify(before[key])!==JSON.stringify(after[key])).map(([key,label])=>`${label}：${display(before[key])} → ${display(after[key])}`).join('；')||'凭证或账单信息已更新'}catch{return '记录已保存'}}
function display(v:unknown){if(Array.isArray(v)&&v.length===3&&v.every(x=>typeof x==='number'))return v.map((x,i)=>i?String(x).padStart(2,'0'):String(x)).join('-');if(v===null||v===undefined||v==='')return '—';if(typeof v==='boolean')return v?'是':'否';if(v==='INCOME')return '收入';if(v==='EXPENSE')return '支出';return String(v)}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(()=>{if(busy.value)return false;if(dirty.value){discard.value=true;return false}return true})
onMounted(async()=>{window.addEventListener('beforeunload',beforeUnload);try{await loadSubjects();await load()}catch(e){error.value=message(e)}})
onBeforeUnmount(()=>{++sequence;window.removeEventListener('beforeunload',beforeUnload);modal.value?.close()})
</script>

<template>
  <section class="page-stack finance-ui">
    <form
      class="panel filter-panel ui-filter-row finance-filters"
      @submit.prevent="search"
    >
      <label>账单主体<select
        v-model="filter.customerId"
        v-single-select
        required
      ><option
        value=""
        disabled
      >请选择客户</option><option
        v-for="s in subjects"
        :key="s.id"
        :value="s.id"
      >{{ s.name }}{{ s.active?'':'（停用）' }}</option><optgroup
        v-if="legacySubjects.length"
        label="历史店铺（待确认客户归属，只读）"
      ><option
        v-for="s in legacySubjects"
        :key="s.id"
        :value="s.id"
      >{{ s.name }}（历史）</option></optgroup></select></label>
      <label class="date-range-filter">账单日期<DatePicker
        v-model="filter.from"
        v-model:end="filter.to"
        range
        type="date"
        required
      /></label>
      <label>收支<select
        v-model="filter.direction"
        v-single-select
      ><option value="">全部收支</option><option value="INCOME">收入</option><option value="EXPENSE">支出</option></select></label>
      <label>状态<select
        v-model="filter.voided"
        v-single-select
      ><option value="false">有效账单</option><option value="true">已作废</option></select></label>
      <label class="keyword">搜索<input
        v-model="filter.q"
        maxlength="200"
        type="search"
        placeholder="明细 / 账户 / 备注"
      ></label>
      <button
        type="submit"
        :disabled="loading || (!subjects.length && !legacySubjects.length)"
      >
        搜索
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="period()"
      >
        本月
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="previousMonth"
      >
        上月
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="period(true)"
      >
        本年
      </button>
    </form>
    <section class="panel material-toolbar">
      <div class="finance-actions">
        <button
          v-if="writable"
          :disabled="!activeSubject?.active || busy"
          @click="editBill()"
        >
          新增账单
        </button><button
          v-if="session.canAccess(['finance.export'])"
          class="secondary-button"
          :disabled="busy || loading || !result.totalCount"
          @click="exportBills"
        >
          导出账单
        </button>
      </div>
      <span class="muted">{{ activeSubject?.name ?? '请先在客户信息中添加客户' }} · 人民币 CNY</span>
    </section>
    <p
      v-if="activeSubject?.legacy"
      class="muted"
    >
      历史店铺账单暂不归属客户，仅供查询和导出。
    </p>
    <SuccessToast :message="success" />
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p>
    <div
      class="finance-totals"
      :aria-busy="loading"
    >
      <article class="panel">
        <span>{{ applied.voided==='true'?'作废':'筛选' }}收入</span><strong class="income">¥ {{ money(total.income) }}</strong>
      </article>
      <article class="panel">
        <span>{{ applied.voided==='true'?'作废':'筛选' }}支出</span><strong class="expense">¥ {{ money(total.expense) }}</strong>
      </article>
      <article class="panel">
        <span>收支结余</span><strong>¥ {{ money(total.balance) }}</strong><small>收入 − 支出，不代表会计利润</small>
      </article>
    </div>
    <div class="ui-section-tabs">
      <button
        v-for="t in [{id:'bills',name:'账单明细'},{id:'accounts',name:'账户汇总'},{id:'months',name:'月度汇总'}]"
        :key="t.id"
        :class="{'is-active':tab===t.id}"
        :aria-pressed="tab===t.id"
        @click="tab=t.id"
      >
        {{ t.name }}
      </button>
    </div>
    <article class="panel table-panel">
      <TableViewport>
        <table
          v-if="tab==='bills'"
          class="data-table ui-action-table finance-table"
          :aria-busy="loading"
        >
          <thead>
            <tr>
              <th>日期</th><th>收支</th><th>收支明细</th><th>账户</th><th class="amount">
                金额（CNY）
              </th><th>凭证</th><th>备注</th><th>操作</th>
            </tr>
          </thead><tbody>
            <tr
              v-for="b in result.items"
              :key="b.id"
            >
              <td>{{ b.date }}</td><td>
                <span :class="b.direction==='INCOME'?'income':'expense'">{{ b.direction==='INCOME'?'收入':'支出' }}</span><small
                  v-if="b.voided"
                  class="muted"
                >已作废</small>
              </td><td class="bill-detail">
                {{ b.detail }}
              </td><td>{{ b.account }}</td><td class="amount">
                {{ money(b.amount) }}
              </td><td>
                <button
                  v-if="b.attachments"
                  class="text-action"
                  @click="detail(b)"
                >
                  {{ b.attachments }} 个
                </button><span v-else>—</span>
              </td><td class="bill-detail">
                {{ b.remark||'—' }}
              </td><td>
                <div class="table-actions">
                  <button
                    class="text-action"
                    @click="detail(b)"
                  >
                    详情
                  </button><button
                    v-if="writable&&!b.voided&&!activeSubject?.legacy"
                    class="text-action"
                    :disabled="busy||!activeSubject?.active"
                    @click="editBill(b)"
                  >
                    编辑
                  </button><button
                    v-if="!b.voided && !activeSubject?.legacy && session.canAccess(['finance.void'])"
                    class="text-action danger"
                    :disabled="busy"
                    @click="voidBill(b)"
                  >
                    作废
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!result.items.length">
              <td
                colspan="8"
                class="data-state"
              >
                {{ loading?'正在加载…':applied.customerId?'暂无账单记录':'暂无客户，请先在客户信息中添加' }}
              </td>
            </tr>
          </tbody>
        </table>
        <table
          v-else
          class="data-table finance-summary"
        >
          <thead>
            <tr>
              <th>{{ tab==='accounts'?'收支账户':'月份' }}</th><th class="amount">
                收入（CNY）
              </th><th class="amount">
                支出（CNY）
              </th><th class="amount">
                结余（CNY）
              </th><th>笔数</th>
            </tr>
          </thead><tbody>
            <tr
              v-for="r in (tab==='accounts'?result.accounts:result.months)"
              :key="r.label"
            >
              <td>{{ r.label }}</td><td class="amount income">
                {{ money(r.income) }}
              </td><td class="amount expense">
                {{ money(r.expense) }}
              </td><td class="amount">
                {{ money(r.balance) }}
              </td><td>{{ r.count }}</td>
            </tr><tr v-if="!(tab==='accounts'?result.accounts:result.months).length">
              <td
                colspan="5"
                class="data-state"
              >
                暂无汇总数据
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </article>
    <ListPagination
      v-if="tab==='bills'"
      :page="page"
      :page-size="size"
      :total="result.totalCount"
      :disabled="loading"
      @update:page="changePage"
      @update:page-size="changeSize"
    />
    <dialog
      ref="modal"
      class="finance-dialog"
      :aria-label="mode==='detail'?'账单详情':editing?'编辑账单':'新增账单'"
      @paste="pasteVoucher"
      @cancel.prevent="requestClose"
    >
      <form @submit.prevent="saveBill()">
        <header>
          <h2>{{ mode==='detail'?'账单详情':editing?'编辑账单':'新增账单' }}</h2><button
            type="button"
            class="secondary-button"
            :disabled="busy"
            aria-label="关闭弹窗"
            @click="requestClose"
          >
            ×
          </button>
        </header>
        <div class="finance-dialog-body">
          <p
            v-if="formError"
            role="alert"
            class="data-state data-state--error"
          >
            {{ formError }}
          </p>
          <template v-if="mode==='bill'">
            <p class="bill-subject">
              账单主体：{{ subjects.find(s=>s.id===bill.customerId)?.name }} <span class="muted">· 人民币记账</span>
            </p>
            <div class="bill-form-grid">
              <label>日期<DatePicker
                v-model="bill.date"
                type="date"
                min="2000-01-01"
                max="2100-12-31"
                required
                :disabled="busy"
              /></label>
              <label>收支类型<select
                v-model="bill.direction"
                v-single-select
                :disabled="busy"
              ><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select></label>
              <label>收支账户<EditableSelect
                v-model="bill.account"
                :options="availableAccounts.map(a=>a.name)"
                label="收支账户"
                required
                :disabled="busy || accountsLoading"
                @remove="removeAccount"
              /></label>
              <label>金额（CNY）<input
                v-model="bill.amount"
                type="number"
                min="0.01"
                max="999999999999.99"
                step="0.01"
                required
                :disabled="busy"
              ></label>
              <label class="wide">收支明细<textarea
                v-model="bill.detail"
                required
                maxlength="500"
                :disabled="busy"
                placeholder="说明本笔收入或支出的用途"
              /></label>
              <label class="wide">备注<textarea
                v-model="bill.remark"
                maxlength="1000"
                :disabled="busy"
                placeholder="可记录汇率、订单号等补充信息"
              /></label>
            </div>
            <section class="bill-attachments">
              <VoucherUpload
                ref="voucherUpload"
                v-model="files"
                :existing-count="attachments.length"
                :disabled="busy||detailLoading"
              />
              <ul>
                <li
                  v-for="a in attachments"
                  :key="a.id"
                >
                  <button
                    type="button"
                    class="text-action"
                    @click="download(a)"
                  >
                    {{ a.name }}
                  </button><button
                    v-if="session.canAccess(['finance.attachment.remove'])"
                    type="button"
                    class="text-action danger"
                    :disabled="busy"
                    @click="removeAttachment(a)"
                  >
                    移除
                  </button>
                </li>
              </ul>
            </section>
          </template>
          <template v-else-if="mode==='detail'&&editing">
            <div class="bill-facts">
              <div><span>账单主体</span><strong>{{ editing.customerName }}</strong></div><div><span>日期</span><strong>{{ editing.date }}</strong></div><div><span>{{ editing.direction==='INCOME'?'收入':'支出' }}</span><strong :class="editing.direction==='INCOME'?'income':'expense'">¥ {{ money(editing.amount) }}</strong></div><div><span>账户</span><strong>{{ editing.account }}</strong></div>
            </div>
            <h3>收支明细</h3><p class="preserve">
              {{ editing.detail }}
            </p><h3>备注</h3><p class="preserve">
              {{ editing.remark||'—' }}
            </p>
            <h3>凭证</h3><p v-if="detailLoading">
              正在加载…
            </p><p
              v-else-if="!attachments.length"
              class="muted"
            >
              暂无凭证
            </p><ul>
              <li
                v-for="a in attachments"
                :key="a.id"
              >
                <FinanceVoucher
                  :bill-id="editing.id"
                  :file="a"
                />
                <button
                  type="button"
                  class="text-action"
                  @click="download(a)"
                >
                  {{ a.name }} · 下载
                </button>
              </li>
            </ul>
            <h3>变更记录</h3><ol class="bill-history">
              <li
                v-for="(h,i) in history"
                :key="i"
              >
                <strong>{{ actions[h.action]??'账单变更' }}</strong><span class="muted">{{ h.actor }} · {{ beijingDateTime(h.time) }}</span><p>{{ changes(h) }}</p>
              </li>
            </ol>
          </template>
        </div>
        <footer class="ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="busy"
            @click="requestClose()"
          >
            {{ mode==='detail'?'关闭':'取消' }}
          </button><button
            v-if="mode==='bill'"
            type="submit"
            :disabled="busy||detailLoading||accountsLoading"
          >
            {{ busy?'保存中…':'保存账单' }}
          </button>
        </footer>
      </form>
    </dialog>
    <UnsavedChangesDialog
      v-if="discard"
      discard-label="放弃并关闭"
      message="本次填写的内容尚未保存。关闭后将丢失这些修改，已保存的数据不受影响。"
      @keep="discard=false"
      @discard="discardChanges"
    />
  </section>
</template>
<style scoped>
.finance-ui .panel{min-height:0}.finance-filters{display:flex;flex-wrap:wrap}.finance-filters label{flex:0 1 160px;min-width:120px}.finance-filters label:first-child{flex:0 1 240px}.finance-filters .date-filter{flex:0 0 215px}.finance-filters .keyword{flex:1 1 240px}.finance-actions{display:flex;gap:8px;flex-wrap:wrap}.finance-totals{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.finance-totals article{padding:12px 16px;display:grid;gap:6px}.finance-totals article>span,.finance-totals small{color:var(--muted);font-size:12px}.finance-totals strong{font-size:22px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.income{color:#15803d}.expense{color:#b54708}.amount{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}.finance-table{min-width:1120px;width:100%}.finance-summary{width:100%;min-width:640px}.bill-detail{min-width:160px;max-width:320px;white-space:pre-wrap;overflow-wrap:anywhere}.finance-table td:last-child,.finance-table th:last-child{min-width:174px;width:174px}.finance-table small{display:block}.finance-ui .text-action{background:transparent;border:0;color:var(--accent);font-size:13px;min-height:32px;padding:4px 6px;cursor:pointer}.finance-ui .danger{color:#b42318}.finance-ui button:disabled{opacity:.5;cursor:default}
.bill-form-grid :deep(.editable-select > input){width:100%;box-sizing:border-box;height:40px;min-height:40px;border:1px solid var(--line);border-radius:6px;padding:8px 34px 8px 12px;font:inherit}
.finance-dialog{width:min(840px,calc(100vw - 32px));max-width:calc(100vw - 32px);max-height:calc(100dvh - 32px);padding:0;border:0;border-radius:12px;overflow:hidden}.finance-dialog::backdrop{background:#17203366}.finance-dialog form{display:flex;flex-direction:column;max-height:calc(100dvh - 32px);min-width:0}.finance-dialog header,.finance-dialog footer{display:flex;align-items:center;gap:8px;padding:12px 16px;flex-shrink:0}.finance-dialog header{justify-content:space-between;border-bottom:1px solid var(--line)}.finance-dialog footer{justify-content:flex-end;border-top:1px solid var(--line)}.finance-dialog h2{font-size:18px;margin:0}.finance-dialog h3{font-size:14px;margin:16px 0 8px}.finance-dialog-body{overflow:auto;padding:16px;min-height:0}.finance-dialog header button,.finance-dialog footer button,.shop-list button{min-height:36px;padding:6px 14px;border:1px solid var(--line);border-radius:6px;background:white;color:var(--text);cursor:pointer}.finance-dialog footer button[type=submit]{background:var(--accent);color:white}.bill-form-grid,.bill-facts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.bill-form-grid label{display:grid;align-content:start;gap:6px;font-size:13px}.bill-form-grid .wide{grid-column:1/-1}.bill-form-grid input,.bill-form-grid select,.bill-form-grid textarea{width:100%;min-width:0;box-sizing:border-box;min-height:40px;border:1px solid var(--line);border-radius:6px;padding:8px;font:inherit}.bill-form-grid textarea{min-height:70px;resize:vertical}.bill-subject{margin:0 0 16px;font-weight:600}.bill-attachments{border-top:1px solid var(--line);margin-top:16px}.bill-attachments input{max-width:100%}.bill-attachments li{display:flex;align-items:center;justify-content:space-between;gap:8px;overflow-wrap:anywhere}.bill-facts>div{display:grid;gap:6px;padding:10px;background:#f5f7fa;border-radius:6px;overflow-wrap:anywhere}.bill-facts span{color:var(--muted);font-size:12px}.preserve{white-space:pre-wrap;overflow-wrap:anywhere}.bill-history{padding-left:20px}.bill-history li{margin:12px 0}.bill-history span{display:block;font-size:12px;margin-top:4px}.bill-history p{font-size:13px;overflow-wrap:anywhere}.shop-list{display:flex;gap:6px;flex-wrap:wrap;padding-bottom:16px}.shop-list .selected-shop{color:var(--accent);border-color:var(--accent)}
@media(max-width:600px){.finance-filters .date-filter{flex:1 1 130px}.finance-filters label:first-child{flex:1 1 100%}.finance-ui .finance-filters{max-height:180px}.finance-totals{grid-template-columns:1fr}.finance-totals article{display:flex;align-items:center;flex-wrap:wrap;justify-content:space-between;padding:8px 12px}.finance-totals strong{font-size:18px}.finance-totals small{flex-basis:100%}.finance-filters label{flex:1 1 130px;min-width:0}.bill-form-grid,.bill-facts{grid-template-columns:1fr}.finance-dialog{width:calc(100vw - 16px);max-width:calc(100vw - 16px)}}
</style>

<style>
#app .finance-dialog .bill-form-grid .single-select-trigger{width:100%;height:40px;min-height:40px;padding:8px 12px;font-size:13px;border-radius:6px}
#app .finance-dialog .bill-form-grid .single-select{width:100%;max-width:none}
</style>
