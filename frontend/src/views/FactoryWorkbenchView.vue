<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {factoryApi,type FactoryKind,type FactoryRecord,type FactoryEvent} from '@/api/factory'
import {factoryTitles,factoryStates,factoryMoney,factoryActions,localDay,paymentState} from '@/utils/factoryPresentation'
import type {Capability} from '@/router/meta'
import {session} from '@/state/session'
import {confirmAction,promptReason} from '@/state/uiConfirmation'
import {vSingleSelect} from '@/directives/singleSelect'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import DatePicker from '@/components/DatePicker.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import FactoryPicker from '@/components/FactoryPicker.vue'
import FactoryAttachments from '@/components/FactoryAttachments.vue'
import FactoryEditor from '@/components/FactoryEditor.vue'
import FactoryRecordContent from '@/components/FactoryRecordContent.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import '@/styles/factoryWorkbench.css'
const props=defineProps<{kind:FactoryKind}>()
const route=useRoute(),router=useRouter()
const q=ref(''),status=ref(''),from=ref(''),to=ref(''),month=ref(localDay().slice(0,7)),employeeId=ref(''),employeeName=ref(''),page=ref(1),size=ref(20),total=ref(0),amount=ref('0'),rows=ref<FactoryRecord[]>([]),selected=ref<string[]>([]),loading=ref(false),busy=ref(false),error=ref(''),notice=ref('')
const applied=ref<Record<string,string|number>>({}),detail=ref<FactoryRecord>(),tab=ref('overview'),editor=ref<{mode:string;record?:FactoryRecord}>(),history=ref<FactoryEvent[]>([]),historyPage=ref(1),historySize=ref(10),historyTotal=ref(0),historyBusy=ref(false),snapshot=ref<FactoryRecord>(),detailError=ref('')
const can=(action:string)=>session.canAccess([`factory.${props.kind}.${action}` as Capability])
const title=computed(()=>factoryTitles[props.kind])
const states=computed(()=>({employees:['ACTIVE','LEFT'],rates:['ACTIVE','STOPPED'],reports:['DRAFT','PENDING','REVIEWED','VOID'],payroll:['DRAFT','CORRECTING','LOCKED']}[props.kind]))
const tabs=computed(()=>[{key:'overview',label:'基本资料'},...(props.kind==='payroll'?[{key:'lines',label:'计件明细'},{key:'adjustments',label:'工资加减项'},{key:'payments',label:'发放记录'}]:[]),{key:'history',label:'操作记录'}])
const chosen=computed(()=>rows.value.filter(r=>selected.value.includes(r.id)))
const selectable=(r:FactoryRecord)=>props.kind==='reports'?['DRAFT','PENDING'].includes(r.status):props.kind==='payroll'?['DRAFT','CORRECTING'].includes(r.status):false
let sequence=0,detailSequence=0
async function load(){const seq=++sequence;loading.value=true;error.value='';selected.value=[];try{const r=await factoryApi.list(props.kind,{...applied.value,page:page.value-1,size:size.value});if(seq===sequence){rows.value=r.items;total.value=r.totalCount;amount.value=String(r.amount)}}catch(e){if(seq===sequence)error.value=message(e)}finally{if(seq===sequence)loading.value=false}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
function search(){applied.value={q:q.value,status:status.value,employeeId:employeeId.value,from:props.kind==='payroll'?month.value:from.value,to:props.kind==='payroll'?month.value:to.value};page.value=1;void load()}
function reset(){q.value='';status.value='';employeeId.value='';employeeName.value='';from.value=props.kind==='reports'?localDay():'';to.value=from.value;month.value=localDay().slice(0,7);search()}
watch(()=>[props.kind,route.query.employeeId],()=>{detail.value=undefined;editor.value=undefined;tab.value='overview';q.value='';status.value='';from.value=props.kind==='reports'?localDay():'';to.value=from.value;employeeId.value=String(route.query.employeeId??'');employeeName.value=String(route.query.employeeName??'');search()},{immediate:true})
async function open(r:FactoryRecord){detailError.value='';tab.value='overview';historyPage.value=1;const seq=++detailSequence;try{const d=await factoryApi.get(props.kind,r.id);if(seq===detailSequence)detail.value=d}catch(e){error.value=message(e)}}
async function loadHistory(){if(!detail.value)return;historyBusy.value=true;detailError.value='';try{const r=await factoryApi.history(props.kind,detail.value.id,historyPage.value-1,historySize.value);history.value=r.items;historyTotal.value=r.totalCount}catch(e){detailError.value=message(e)}finally{historyBusy.value=false}}
watch(tab,v=>{if(v==='history')void loadHistory()})
function tabKey(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();const list=tabs.value,i=list.findIndex(t=>t.key===tab.value);tab.value=list[e.key==='Home'?0:e.key==='End'?list.length-1:(i+(e.key==='ArrowRight'?1:-1)+list.length)%list.length]!.key;requestAnimationFrame(()=>document.getElementById(`factory-tab-${tab.value}`)?.focus())}
async function command(action:string,record?:FactoryRecord,extra:Record<string,unknown>={}){if(busy.value)return;busy.value=true;error.value='';detailError.value='';try{await factoryApi.command(props.kind,action,{...(record?{id:record.id,version:record.version}:{}),...extra,requestId:crypto.randomUUID()});notice.value='操作已完成';if(action==='remove')detail.value=undefined;await load();if(detail.value){detail.value=await factoryApi.get(props.kind,detail.value.id);if(tab.value==='history')await loadHistory()}}catch(e){if(detail.value)detailError.value=message(e);else error.value=message(e)}finally{busy.value=false}}
async function reasonAction(action:string,r:FactoryRecord,label:string,extra:Record<string,unknown>={}){const reason=await promptReason(label);if(reason?.trim())await command(action,r,{...extra,reason})}
async function lock(r:FactoryRecord){if(await confirmAction(`锁定 ${r.employeeName} ${r.date} 工资，应发 ¥${r.pricingIncomplete?'待定':factoryMoney(r.amount)}？锁定后更改须走更正流程。`))await command('lock',r)}
async function batchAction(){if(!chosen.value.length)return;const action=props.kind==='reports'?'reviewBatch':'lockBatch';if(await confirmAction(`确认${props.kind==='reports'?'核对报工':'锁定工资'} ${chosen.value.length} 条？所有所选记录验证通过才会生效。`))await command(action,undefined,{items:chosen.value.map(r=>({id:r.id,version:r.version}))})}
function toggleAll(e:Event){selected.value=(e.target as HTMLInputElement).checked?rows.value.filter(selectable).map(r=>r.id):[]}
async function saved(){editor.value=undefined;notice.value='保存成功';await load();try{if(detail.value)detail.value=await factoryApi.get(props.kind,detail.value.id)}catch(e){detailError.value=message(e)}}
async function exportMonth(){busy.value=true;try{await factoryApi.export(String(applied.value.from||month.value),String(applied.value.employeeId||''))}catch(e){error.value=message(e)}finally{busy.value=false}}
function goEmployee(target:FactoryKind){if(!detail.value)return;void router.push({path:`/factory/${target}`,query:{employeeId:detail.value.id,employeeName:detail.value.name}})}
async function exportLines(){if(!detail.value)return;busy.value=true;try{await factoryApi.export(detail.value.date,detail.value.employeeId,'lines')}catch(e){detailError.value=message(e)}finally{busy.value=false}}
function print(){window.print()}
</script>
<template>
  <section class="page-stack factory-ui">
    <SuccessToast :message="notice" />
    <form
      class="filter-panel factory-filters"
      @submit.prevent="search"
    >
      <label class="factory-search">搜索<input
        v-model="q"
        placeholder="姓名、工号、加工项目或备注"
        maxlength="200"
      ></label>
      <label v-if="kind==='payroll'">工资月份<input
        v-model="month"
        type="month"
        required
      ></label>
      <label v-else-if="kind==='reports'">工作日期<DatePicker
        v-model="from"
        v-model:end="to"
        range
      /></label>
      <label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部状态</option><option
        v-for="state in states"
        :key="state"
        :value="state"
      >{{ kind==='employees'&&state==='ACTIVE'?'在职':factoryStates[state] }}</option></select></label>
      <div
        v-if="['reports','payroll'].includes(kind)&&session.canAccess(['factory.employees.read'])"
        class="factory-filter-employee"
      >
        <FactoryPicker
          kind="employees"
          label="筛选员工"
          :value="employeeName||'全部员工'"
          @select="employeeId=$event.id;employeeName=$event.name!"
        /><button
          v-if="employeeId"
          type="button"
          class="factory-link"
          @click="employeeId='';employeeName=''"
        >
          清除
        </button>
      </div>
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
    <div class="factory-toolbar">
      <div>
        <button
          v-if="can('write')"
          class="factory-primary"
          :disabled="busy"
          @click="editor={mode:kind==='payroll'?'generate':'save'}"
        >
          ＋ {{ ({employees:'新增员工',rates:'新增工价',reports:'新增报工',payroll:'生成员工工资'})[kind] }}
        </button><button
          v-if="kind==='payroll'&&can('write')"
          :disabled="busy"
          @click="command('generateMonth',undefined,{date:month})"
        >
          生成本月工资
        </button><button
          v-if="(kind==='reports'&&can('review'))||(kind==='payroll'&&can('lock'))"
          :class="{'factory-selected':selected.length>0}"
          :disabled="!selected.length||busy"
          @click="batchAction"
        >
          {{ kind==='reports'?'核对所选':'锁定所选' }}
        </button><button
          v-if="kind==='payroll'&&can('export')"
          :disabled="busy||!rows.length"
          @click="exportMonth"
        >
          导出工资汇总
        </button>
      </div><span>{{ ({employees:'员工档案与登录账号独立，离职保留历史工资。',rates:'价格按工作日期生效，历史报工保留原工价。',reports:'先记录数量，再核对计薪；不自动生成库存。',payroll:'核算状态与发放进度分开管理，不执行真实转账。'})[kind] }}</span>
    </div>
    <p
      v-if="error"
      role="alert"
      class="factory-error"
    >
      {{ error }} <button
        class="factory-link"
        @click="load"
      >
        重试
      </button>
    </p>
    <div
      v-if="['reports','payroll'].includes(kind)"
      class="factory-summary"
    >
      {{ kind==='reports'?'筛选记录计件合计（含草稿，不含作废）':'筛选应发合计（含未锁定工资）' }} <strong>¥ {{ factoryMoney(amount) }}</strong><span v-if="loading">正在加载…</span>
    </div>
    <TableViewport>
      <table class="data-table factory-table">
        <thead>
          <tr>
            <th
              v-if="['reports','payroll'].includes(kind)"
              class="factory-select"
            >
              <input
                type="checkbox"
                aria-label="全选本页可操作记录"
                :checked="rows.filter(selectable).length>0&&selected.length===rows.filter(selectable).length"
                :disabled="busy"
                @change="toggleAll"
              >
            </th>
            <template v-if="kind==='employees'">
              <th>员工 / 工号</th><th>班组</th><th>手机号</th><th>常做工序</th><th>入职日期</th><th>状态</th>
            </template>
            <template v-else-if="kind==='rates'">
              <th>加工项目 / SKU</th><th>工序</th><th>单位</th><th>单价（元）</th><th>适用员工</th><th>生效 / 停止日期</th><th>状态</th>
            </template>
            <template v-else-if="kind==='reports'">
              <th>工作日期</th><th>员工 / 工号</th><th>加工项目摘要</th><th>明细项数</th><th>金额（元）</th><th>状态</th><th>最近维护</th>
            </template>
            <template v-else>
              <th>月份 / 员工</th><th>班组</th><th>计件工资</th><th>加项 / 减项</th><th>应发</th><th>已发</th><th>待发 / 超发</th><th>核算状态</th><th>发放进度</th>
            </template><th class="factory-actions">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="r in rows"
            :key="r.id"
          >
            <td
              v-if="['reports','payroll'].includes(kind)"
              class="factory-select"
            >
              <input
                v-model="selected"
                type="checkbox"
                :value="r.id"
                :disabled="!selectable(r)||busy"
                :aria-label="`选择${r.employeeName} ${r.date}`"
              >
            </td>
            <template v-if="kind==='employees'">
              <td><strong>{{ r.name }}</strong><small>{{ r.number }}</small></td><td>{{ r.team||'—' }}</td><td>{{ r.phone||'—' }}</td><td>{{ r.operations||'—' }}</td><td>{{ r.date }}</td><td>
                <span
                  class="factory-badge"
                  :data-status="r.status"
                >{{ r.endDate?'已离职':'在职' }}</span>
              </td>
            </template>
            <template v-else-if="kind==='rates'">
              <td><strong>{{ r.project }}</strong><small>{{ r.skuCode||'自定义加工项目' }}</small></td><td>{{ r.operation }}</td><td>{{ r.unit }}</td><td class="factory-number">
                {{ r.price }}
              </td><td>{{ r.employeeName }}</td><td>{{ r.date }}<small v-if="r.endDate">{{ r.endDate }} 起停止</small></td><td>
                <span
                  class="factory-badge"
                  :data-status="r.status"
                >{{ factoryStates[r.status] }}</span>
              </td>
            </template>
            <template v-else-if="kind==='reports'">
              <td>{{ r.date }}</td><td><strong>{{ r.employeeName }}</strong><small>{{ r.number }}</small></td><td class="factory-long">
                {{ r.project }}
              </td><td>{{ r.lines?.length||0 }}</td><td class="factory-number">
                {{ r.pricingIncomplete?'待定':factoryMoney(r.amount) }}
              </td><td>
                <span
                  class="factory-badge"
                  :data-status="r.status"
                >{{ factoryStates[r.status] }}</span>
              </td><td>{{ r.updatedBy }}<small>{{ r.updatedAt?new Date(r.updatedAt).toLocaleString():'' }}</small></td>
            </template>
            <template v-else>
              <td><strong>{{ r.employeeName }}</strong><small>{{ r.date }} · {{ r.number }}</small></td><td>{{ r.team||'—' }}</td><td class="factory-number">
                {{ factoryMoney(r.base) }}
              </td><td class="factory-number">
                +{{ factoryMoney(r.plus) }}<small>−{{ factoryMoney(r.minus) }}</small>
              </td><td class="factory-number">
                <strong>{{ r.pricingIncomplete?'待定':factoryMoney(r.amount) }}</strong>
              </td><td class="factory-number">
                {{ factoryMoney(r.paid) }}
              </td><td
                class="factory-number"
                :class="{danger:Number(r.remaining)<0}"
              >
                {{ factoryMoney(r.remaining) }}
              </td><td>
                <span
                  class="factory-badge"
                  :data-status="r.status"
                >{{ factoryStates[r.status] }}</span><small
                  v-if="r.stale"
                  class="factory-warning"
                >来源已变化</small><small v-if="r.historical">历史期初</small>
              </td><td>{{ paymentState(r.amount,r.paid) }}</td>
            </template>
            <td class="factory-actions">
              <button
                class="factory-link"
                :disabled="busy"
                @click="open(r)"
              >
                详情
              </button><button
                v-if="kind==='employees'&&can('write')"
                class="factory-link"
                @click="editor={mode:'save',record:r}"
              >
                编辑
              </button><button
                v-if="kind==='rates'&&can('write')"
                class="factory-link"
                @click="editor={mode:'save',record:r}"
              >
                调价
              </button><button
                v-if="kind==='reports'&&can('review')&&selectable(r)"
                class="factory-link"
                :disabled="busy"
                @click="command('review',r)"
              >
                核对
              </button>
            </td>
          </tr><tr v-if="!rows.length">
            <td
              :colspan="kind==='payroll'?11:kind==='employees'?7:9"
              class="data-state"
            >
              {{ loading?'正在加载…':`暂无${title}记录，可从上方按钮开始建立` }}
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
    >
      <template #summary>
        共 {{ total }} 条<span v-if="selected.length"> · 已选 {{ selected.length }} 条</span>
      </template>
    </ListPagination>
    <AccessDialog
      v-if="detail"
      :title="`${title} · ${detail.name||detail.employeeName||detail.project}`"
      wide
      :busy="busy"
      @close="detail=undefined;detailSequence++"
    >
      <div class="factory-ui factory-print-area">
        <h2 class="factory-print-title">
          {{ detail.employeeName }} · {{ detail.date }} 工资单
        </h2>
        <nav
          class="factory-tabs"
          role="tablist"
          aria-label="工厂记录详情"
        >
          <button
            v-for="t in tabs"
            :id="`factory-tab-${t.key}`"
            :key="t.key"
            role="tab"
            :aria-selected="tab===t.key"
            :aria-controls="`factory-panel-${t.key}`"
            :tabindex="tab===t.key?0:-1"
            @click="tab=t.key"
            @keydown="tabKey"
          >
            {{ t.label }}
          </button>
        </nav>
        <p
          v-if="detailError"
          role="alert"
          class="factory-error"
        >
          {{ detailError }}
        </p>
        <div
          :id="`factory-panel-${tab}`"
          role="tabpanel"
          :aria-labelledby="`factory-tab-${tab}`"
        >
          <FactoryRecordContent
            v-if="tab!=='history'"
            :kind="kind"
            :record="detail"
            :section="tab"
            :can-void="kind==='payroll'&&can('pay')&&!busy"
            @void-payment="reasonAction('voidPayment',detail!,'请说明作废发放登记的原因；此操作不会撤回真实付款。',{paymentId:$event})"
          />
          <template v-else>
            <p v-if="historyBusy">
              正在加载…
            </p><div class="factory-edit-table">
              <table>
                <thead><tr><th>时间 / 操作人</th><th>动作</th><th>原因</th><th>版本</th></tr></thead><tbody>
                  <tr
                    v-for="h in history"
                    :key="h.id"
                  >
                    <td>{{ new Date(h.time).toLocaleString() }}<small>{{ h.actor }}</small></td><td>{{ factoryActions[h.action]||h.action }}</td><td>{{ h.reason||'—' }}</td><td>
                      <button
                        class="factory-link"
                        @click="snapshot=h.after"
                      >
                        查看当时记录
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div><ListPagination
              :total="historyTotal"
              :page="historyPage"
              :page-size="historySize"
              :disabled="historyBusy"
              @update:page="historyPage=$event;loadHistory()"
              @update:page-size="historySize=$event;historyPage=1;loadHistory()"
            />
          </template>
        </div>
        <FactoryAttachments
          v-if="['reports','payroll'].includes(kind)&&tab==='overview'"
          :id="detail.id"
          :key="detail.id"
          :kind="kind"
          :writable="kind==='payroll'?can('pay'):can('write')"
        />
        <div
          v-if="kind==='payroll'"
          class="factory-print-only"
        >
          <FactoryRecordContent
            kind="payroll"
            :record="detail"
            section="overview"
            printing
          /><FactoryRecordContent
            kind="payroll"
            :record="detail"
            section="lines"
            printing
          /><FactoryRecordContent
            kind="payroll"
            :record="detail"
            section="adjustments"
          /><FactoryRecordContent
            kind="payroll"
            :record="detail"
            section="payments"
          />
        </div>
      </div>
      <template #footer>
        <template v-if="kind==='employees'">
          <button
            v-if="can('write')"
            @click="editor={mode:'save',record:detail}"
          >
            编辑员工
          </button><button
            v-if="can('write')"
            class="danger"
            :disabled="busy"
            @click="reasonAction('remove',detail,'只允许移除没有工价、报工或工资引用的员工。请说明原因。')"
          >
            移除误建员工
          </button><button
            v-if="session.canAccess(['factory.reports.read'])"
            @click="goEmployee('reports')"
          >
            个人报工
          </button><button
            v-if="session.canAccess(['factory.payroll.read'])"
            @click="goEmployee('payroll')"
          >
            个人工资
          </button>
        </template>
        <template v-if="kind==='rates'&&can('write')">
          <button @click="editor={mode:'stop',record:detail}">
            设置停止日期
          </button><button
            class="factory-primary"
            @click="editor={mode:'save',record:detail}"
          >
            新增价格版本
          </button>
        </template>
        <template v-if="kind==='reports'">
          <button
            v-if="can('write')&&['DRAFT','PENDING'].includes(detail.status)"
            @click="editor={mode:'save',record:detail}"
          >
            编辑报工
          </button><button
            v-if="can('review')&&['DRAFT','PENDING'].includes(detail.status)"
            :disabled="busy"
            class="factory-primary"
            @click="command('review',detail)"
          >
            核对通过
          </button><button
            v-if="can('review')&&detail.status==='REVIEWED'"
            :disabled="busy"
            @click="reasonAction('withdraw',detail,'请说明撤回核对的原因')"
          >
            撤回核对
          </button><button
            v-if="can('review')&&detail.status!=='VOID'"
            class="danger"
            :disabled="busy"
            @click="reasonAction('void',detail,'请说明报工作废原因')"
          >
            作废
          </button>
        </template>
        <template v-if="kind==='payroll'">
          <button
            v-if="can('export')"
            @click="print"
          >
            打印工资单
          </button><button
            v-if="can('export')"
            :disabled="busy"
            @click="exportLines"
          >
            导出计件明细
          </button><template v-if="['DRAFT','CORRECTING'].includes(detail.status)">
            <button
              v-if="can('write')"
              :disabled="busy"
              @click="editor={mode:'adjust',record:detail}"
            >
              调整工资
            </button><button
              v-if="can('write')"
              :disabled="busy"
              @click="command('calculate',detail)"
            >
              重新计算
            </button><button
              v-if="can('lock')"
              :disabled="busy"
              class="factory-primary"
              @click="lock(detail)"
            >
              核对并锁定
            </button>
          </template><button
            v-if="can('correct')&&detail.status==='LOCKED'"
            :disabled="busy"
            @click="reasonAction('correct',detail,'请说明更正原因。旧版本与发放记录保留，更正期间暂停发放。')"
          >
            更正工资
          </button><button
            v-if="can('pay')&&['DRAFT','LOCKED'].includes(detail.status)"
            :disabled="busy"
            @click="editor={mode:'pay',record:detail}"
          >
            {{ detail.status==='DRAFT'?'登记预支':'登记发放' }}
          </button>
        </template>
      </template>
    </AccessDialog>
    <AccessDialog
      v-if="snapshot"
      title="历史版本（只读）"
      wide
      @close="snapshot=undefined"
    >
      <FactoryRecordContent
        :kind="kind"
        :record="snapshot"
        section="overview"
      /><template v-if="kind==='payroll'">
        <FactoryRecordContent
          :kind="kind"
          :record="snapshot"
          section="lines"
        /><FactoryRecordContent
          :kind="kind"
          :record="snapshot"
          section="adjustments"
        /><FactoryRecordContent
          :kind="kind"
          :record="snapshot"
          section="payments"
        />
      </template>
    </AccessDialog>
    <FactoryEditor
      v-if="editor"
      :kind="kind"
      :mode="editor.mode"
      :record="editor.record"
      :month="month"
      @close="editor=undefined"
      @saved="saved"
    />
  </section>
</template>
