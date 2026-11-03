<script setup lang="ts">
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { launchApi, defaults, rows, stateNames, stages, actionNames, type Workspace, type LaunchItem, type Document, type Value } from '@/api/launch'
import { sections, type Section, type Field } from '@/components/launch/schema'
import LaunchFields from '@/components/launch/LaunchFields.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import { apiRequest } from '@/api/client'
import { attachmentList, uploadAttachment, downloadAttachment, type LaunchAttachment } from '@/api/launch'
import { session } from '@/state/session'
const route=useRoute(),router=useRouter()
const development=computed(()=>route.path.startsWith('/product-development'))
const api=computed(()=>launchApi(development.value))
const id=computed(()=>String(route.params.candidateId??route.params.id??''))
const base=computed(()=>development.value?'/product-development':'/selection/candidates')
const writable=computed(()=>session.canAccess([development.value?'development.write':'selection.write']))
const reviewer=computed(()=>session.canAccess([development.value?'development.review':'selection.review']))
const items=ref<LaunchItem[]>([]),total=ref(0),page=ref(1),size=ref(20),query=ref(''),queryDraft=ref(''),state=ref('')
const workspace=ref<Workspace>(),draft=ref<Document>({}),snapshot=ref(''),tab=ref('overview'),error=ref(''),success=ref(''),busy=ref(false),loading=ref(false),unsaved=ref(false),createOpen=ref(false)
const files=ref<LaunchAttachment[]>([]), products=ref<ProductDetailDto[]>([]), productQuery=ref(''), observation=ref<Record<string,unknown>>({}),collectionStatus=ref('')
const legacy=ref<Record<string,unknown>>({}),createDraft=ref({type:'AMAZON',title:'',asinOrUrl:'',owner:'',reason:'',sourceUrl:'',requestId:''})
const confirm=ref(''),reason=ref(''),selfConfirmed=ref(false),selectedRevision=ref<number|null>(null)
const editable=computed(()=>writable.value&&!!workspace.value&&['NEW','EVALUATING','RETURNED','DRAFT','ACTIVE'].includes(workspace.value.state)&&!Object.keys(workspace.value.proposal).length)
const dirty=computed(()=>!!workspace.value&&JSON.stringify(draft.value)!==snapshot.value)
const visibleSections=computed(()=>sections.filter(s=>development.value?!['market','criteria'].includes(s.key):!['quotes','samples','handover','skus'].includes(s.key)))
const groups=computed(()=>development.value?[
 {title:'项目概览',keys:['overview']},{title:'产品方案',keys:['specs','needs','requirements']},{title:'供应与打样',keys:['quotes','samples']},{title:'成本与利润',keys:['cost']},{title:'风险评估',keys:['risk']},{title:'验收与交接',keys:['handover','skus','evidence']},{title:'变更与记录',keys:['history']}
]:[{title:'评估概览',keys:['overview']},{title:'市场与销售',keys:['market']},{title:'产品方案',keys:['specs']},{title:'需求与改进',keys:['needs','requirements','evidence']},{title:'成本与利润',keys:['cost']},{title:'风险评估',keys:['risk']},{title:'评审与记录',keys:['criteria','history']}])
const activeGroup=computed(()=>groups.value.find(g=>g.keys.includes(tab.value)))
function effectiveFields(fields:Field[]):Field[]{return fields.filter(f=>development.value||!['buyer','startDate','endDate','legacyNotes'].includes(f.key)).map(field=>{const key=({needId:'needs',selectedQuote:'quotes',quoteId:'quotes',selectedSample:'samples',previousId:'samples'} as Record<string,string>)[field.key];if(!key)return field;return {...field,type:'select',options:rows(draft.value,key).map(row=>[String(row.id),String(row.name??row.supplier??row.pain??row.id)])}})}
const current=computed(()=>visibleSections.value.find(s=>s.key===tab.value))
const stateOptions=computed(()=>development.value?['DRAFT','PROJECT_REVIEW','ACTIVE','FINAL_REVIEW','PAUSED','COMPLETED','VOID']:['NEW','EVALUATING','REVIEW','RETURNED','APPROVED','REJECTED'])
const actions=computed(()=>{
 const w=workspace.value;if(!w||!writable.value)return []
 const values:string[]=[];const s=w.state
 if(development.value){if(s==='DRAFT')values.push(Object.keys(w.baseline).length?'START':'SUBMIT','PAUSE');if(s==='ACTIVE')values.push(w.stage<4?'NEXT':'SUBMIT',...(w.stage>0?['BACK']:[]),'PAUSE');if(s==='PAUSED')values.push('RESUME');if(['PROJECT_REVIEW','FINAL_REVIEW'].includes(s)){values.push('WITHDRAW');if(reviewer.value)values.push(s==='FINAL_REVIEW'?'COMPLETE':'APPROVE','RETURN')}if(reviewer.value&&!['COMPLETED','VOID'].includes(s))values.push('TERMINATE')}
 else{if(s==='NEW')values.push('START');if(['EVALUATING','RETURNED'].includes(s))values.push('SUBMIT');if(s==='REVIEW'){values.push('WITHDRAW');if(reviewer.value)values.push('APPROVE','RETURN')}if(s==='REJECTED')values.push('REOPEN');if(reviewer.value&&!['APPROVED','REJECTED'].includes(s))values.push('REJECT')}
 if(reviewer.value&&editable.value)values.push('REVIEW_RISK')
 if(reviewer.value&&Object.keys(w.proposal).length)values.push('CHANGE_APPROVE','CHANGE_REJECT')
 return values
})
const sourceCandidate=computed(()=>{const item=legacy.value.item as {source?:{candidateId:string}}|undefined;return item?.source?.candidateId})
async function readFiles(){files.value=await attachmentList(development.value?'DEVELOPMENT':'CANDIDATE',id.value)}
async function upload(file:File){if(busy.value)return false;busy.value=true;try{const receipt=await uploadAttachment(development.value?'DEVELOPMENT':'CANDIDATE',id.value,file);await readFiles();success.value=`附件已保存：${receipt.name}；可在证据中引用附件编号。`}catch(e){error.value=message(e); return false }finally{busy.value=false}}
async function download(file:LaunchAttachment){try{await downloadAttachment(development.value?'DEVELOPMENT':'CANDIDATE',id.value,file)}catch(e){error.value=message(e)}}
async function findProducts(){try{products.value=(await domainApi.products()).items.filter(p=>`${p.productName} ${p.productCode}`.toLowerCase().includes(productQuery.value.toLowerCase()))}catch(e){error.value=message(e)}}
function chooseProduct(product:ProductDetailDto){draft.value.productId=product.id;draft.value.skus=product.variants.map(v=>({id:crypto.randomUUID(),skuId:v.id??'',name:v.variantName,sampleId:''}));success.value='已带入商品及SKU，请移除本次未涉及的SKU后保存。'}
async function readObservation(){try{const result=await apiRequest<{observation:Record<string,unknown>;job:Record<string,unknown>[]}>(`/v1/selection/workbench/${id.value}/observation`);observation.value=result.observation;const job=result.job[0];collectionStatus.value=job?`${({QUEUED:'排队中',RUNNING:'采集中',RETRY:'等待重试',SUCCEEDED:'采集完成',FAILED:'采集失败'} as Record<string,string>)[String(job.status??job.STATUS)]??String(job.status??job.STATUS)} ${job.reason??job.REASON??''}`:'尚无采集任务'}catch(e){error.value=message(e)}}
async function collect(){try{await apiRequest(`/v1/selection/workbench/${id.value}/collect`,{method:'POST'});collectionStatus.value='已请求更新，点击查看采集进度获取结果';success.value='采集请求已提交，不覆盖人工评估。'}catch(e){error.value=message(e)}}
function adoptObservation(){const o=observation.value;for(const [src,dst] of [['title','referenceTitle'],['imageUrl','imageUrl'],['price','referencePrice'],['rating','rating'],['ratingCount','ratingCount'],['listedAt','availableDate'],['brand','referenceBrand']]){if(src&&dst&&o[src]!=null)draft.value[dst]=String(o[src])}success.value='可得参考数据已带入草稿，请核对并保存。'}
function accept(w:Workspace){workspace.value=w;draft.value=defaults(w.document);snapshot.value=JSON.stringify(draft.value)}
function money(n:unknown){return typeof n==='number'?n.toFixed(2):'待完善'}
function label(action:string){if(action==='START')return development.value?'接手并开始开发':'开始评估';if(action==='SUBMIT')return workspace.value?.state==='ACTIVE'?'提交完成审核':'提交审核';if(action==='APPROVE'&&development.value)return '通过立项';return actionNames[action]??action}
async function load(){loading.value=true;error.value='';try{if(id.value){const result=await api.value.get(id.value);legacy.value=result.legacy;if('version'in result.workspace)accept(result.workspace as Workspace);else workspace.value=undefined}else{const result=await api.value.list(query.value,state.value,page.value,size.value);items.value=result.items;total.value=result.total}}catch(e){error.value=message(e)}finally{loading.value=false}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
async function initialize(){busy.value=true;try{accept(await api.value.initialize(id.value));success.value='工作台已启用，历史资料保留。'}catch(e){error.value=message(e)}finally{busy.value=false}}
async function save(){busy.value=true;error.value='';try{accept(await api.value.save(id.value,workspace.value!.version,draft.value));success.value=Object.keys(workspace.value!.proposal).length?'变更已提交，执行方案待批准后更新。':'资料已保存。'}catch(e){error.value=message(e)}finally{busy.value=false}}
async function action(){busy.value=true;error.value='';try{accept(await api.value.action(id.value,workspace.value!.version,confirm.value,reason.value,selfConfirmed.value));confirm.value='';success.value='操作已完成。'}catch(e){error.value=message(e)}finally{busy.value=false}}
function ask(action:string){if(dirty.value){error.value='请先保存当前修改，再执行流程操作。';return}confirm.value=action;reason.value='';selfConfirmed.value=false}
function add(section:Section){if(!section.rows)return;const list=rows(draft.value,section.rows);list.push({id:crypto.randomUUID()});draft.value[section.rows]=list}
function setField(key:string,value:Value){draft.value[key]=value}
function showCreate(){createDraft.value={type:'AMAZON',title:'',asinOrUrl:'',owner:'',reason:'',sourceUrl:'',requestId:crypto.randomUUID()};createOpen.value=true}
async function create(){busy.value=true;error.value='';try{const result=await api.value.create(createDraft.value);createOpen.value=false;busy.value=false;await router.push(`${base.value}/${result.id}`)}catch(e){error.value=message(e)}finally{busy.value=false}}
function search(){query.value=queryDraft.value;page.value=1;void load()}
function reset(){queryDraft.value='';query.value='';state.value='';page.value=1;void load()}
function resize(n:number){size.value=n;page.value=1;void load()}
function guard(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',guard);onBeforeUnmount(()=>window.removeEventListener('beforeunload',guard))
onBeforeRouteLeave(()=>{if(busy.value)return false;if(dirty.value){unsaved.value=true;return false}return true})
onBeforeRouteUpdate(()=>{if(busy.value)return false;if(dirty.value){unsaved.value=true;return false}return true})
watch(()=>route.fullPath,()=>{workspace.value=undefined;tab.value='overview';selectedRevision.value=null;void load();if(route.path.endsWith('/new'))showCreate()},{immediate:true})
</script>
<template>
  <section class="launch-ui page-stack">
    <SuccessToast :message="success" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <template v-if="!id">
      <form
        class="filter-panel launch-filter"
        @submit.prevent="search"
      >
        <label>关键词<input
          v-model="queryDraft"
          placeholder="名称 / 编号 / ASIN / 负责人"
        ></label>
        <label>状态<select
          v-model="state"
          v-single-select
        ><option value="">全部状态</option><option
          v-for="s in stateOptions"
          :key="s"
          :value="s"
        >{{ stateNames[s] }}</option></select></label>
        <button :disabled="loading">
          搜索
        </button><button
          type="button"
          class="filter-reset-button"
          @click="reset"
        >
          重置
        </button>
      </form>
      <div class="monitor-toolbar">
        <button
          v-if="writable"
          @click="showCreate"
        >
          {{ development?'添加新品需求':'添加候选' }}
        </button><span class="muted">{{ development?'从批准方案到样品验证与商品交接':'以客户需求、经济性与风险证据作出开发决策' }}</span>
      </div>
      <TableViewport>
        <table class="launch-table">
          <thead>
            <tr>
              <th class="sticky-name">
                {{ development?'开发项目':'候选产品' }}
              </th><th>状态</th><th>{{ development?'当前阶段':'来源' }}</th><th>负责人</th><th>目标售价 USD</th><th>{{ development?'计划完成日':'销售表现' }}</th><th>风险调查</th><th class="sticky-action">
                操作
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="item in items"
              :key="item.id"
            >
              <td class="sticky-name">
                <RouterLink :to="`${base}/${item.id}`">
                  {{ item.title }}
                </RouterLink><small>{{ item.number??item.asin??`CP-${item.id.slice(0,8)}` }}</small>
              </td><td>
                <span
                  class="launch-badge"
                  :data-state="item.state"
                >{{ stateNames[item.state??item.legacy_state]??item.legacy_state }}</span>
              </td><td>{{ development?(item.state?stages[item.stage??0]:'阶段待确认'):item.source==='WATCH'?'上新监控':item.asin?'亚马逊商品':'其他产品' }}</td><td>{{ item.document.owner??item.owner??'待分配' }}</td><td>{{ item.document.price??'—' }}</td><td>{{ development?(item.document.endDate??'待安排'):(item.document.salesValue??'无可得数据') }}</td><td>{{ rows(item.document,'risks').length?'查看调查':'待调查' }}</td><td class="sticky-action">
                <RouterLink :to="`${base}/${item.id}`">
                  详情
                </RouterLink>
              </td>
            </tr>
            <tr v-if="!items.length">
              <td
                colspan="8"
                class="data-state"
              >
                暂无记录，可添加{{ development?'新品需求':'候选' }}。
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
      <ListPagination
        :total="total"
        :page="page"
        :page-size="size"
        :disabled="loading"
        @update:page="page=$event;load()"
        @update:page-size="resize"
      />
    </template>
    <template v-else>
      <div
        v-if="!workspace&&!loading"
        class="panel"
      >
        <h2>启用评估与开发工作台</h2><p>保留历史记录及关联，启用版本化方案、风险复核和阶段验证。历史通过或完成记录不会重新审批。</p><button
          v-if="writable"
          :disabled="busy"
          @click="initialize"
        >
          启用工作台
        </button><p v-else>
          该记录尚未启用新版工作台，请负责人先完成启用。
        </p>
      </div>
      <template v-if="workspace">
        <header class="launch-hero panel">
          <img
            v-if="String(draft.imageUrl??'').startsWith('https://')"
            :src="String(draft.imageUrl)"
            alt="产品参考图片"
          >
          <div class="launch-title">
            <span class="muted">{{ development?'新品开发':'候选评估' }} · {{ id.slice(0,8) }}</span><h1>{{ draft.name }}</h1><div class="inline-actions">
              <span
                class="launch-badge"
                :data-state="workspace.state"
              >{{ stateNames[workspace.state] }}</span><span>负责人：{{ draft.owner??'待分配' }}</span><span>版本 {{ workspace.version }}</span>
            </div><RouterLink
              v-if="sourceCandidate"
              :to="`/selection/candidates/${sourceCandidate}`"
            >
              查看来源候选与立项依据
            </RouterLink><RouterLink
              v-if="!development&&workspace.state==='APPROVED'"
              :to="`/product-development/${id}`"
            >
              查看关联新品开发
            </RouterLink>
          </div>
          <div class="launch-actions">
            <button
              v-if="editable"
              :disabled="busy||!dirty"
              @click="save"
            >
              {{ busy?'保存中…':'保存资料' }}
            </button><button
              v-for="a in actions"
              :key="a"
              class="secondary-button"
              :disabled="busy"
              @click="ask(a)"
            >
              {{ label(a) }}
            </button>
          </div>
        </header>
        <div
          v-if="development"
          class="launch-stages"
        >
          <span
            v-for="(stage,index) in stages"
            :key="stage"
            :class="{active:workspace.stage===index,done:workspace.stage>index}"
          ><b>{{ index+1 }}</b>{{ stage }}</span>
        </div>
        <div class="launch-metrics">
          <article><small>目标售价</small><strong>{{ workspace.calculation?'$'+money(Number(draft.price)):'待完善' }}</strong></article><article><small>单件贡献 / 利润率</small><strong>{{ money(workspace.calculation?.profit) }} / {{ workspace.calculation?money(Number(workspace.calculation.margin)*100)+'%':'待完善' }}</strong></article><article><small>风险复核</small><strong>{{ workspace.riskReview.valid?'已复核':'待复核' }}</strong></article><article><small>送审检查</small><strong>{{ workspace.issues.length?`${workspace.issues.length} 项待完善`:'已齐备' }}</strong></article>
        </div>
        <p
          v-if="dirty"
          class="launch-notice"
        >
          存在未保存修改，保存后才能执行审批或推进阶段。
        </p>
        <div
          v-if="Object.keys(workspace.proposal).length"
          class="launch-notice"
        >
          存在待审核方案变更。执行版本保持不变；请在“版本与记录”对照后审核。
        </div>
        <nav
          class="ui-section-tabs launch-tabs"
          aria-label="工作台页签"
        >
          <button
            v-for="group in groups"
            :key="group.title"
            :class="{active:group.keys.includes(tab)}"
            @click="tab=group.keys[0]??'overview'"
          >
            {{ group.title }}
          </button>
        </nav>
        <div
          v-if="activeGroup&&activeGroup.keys.length>1"
          class="launch-subtabs"
        >
          <button
            v-for="key in activeGroup.keys"
            :key="key"
            class="secondary-button"
            :class="{active:tab===key}"
            @click="tab=key"
          >
            {{ sections.find(s=>s.key===key)?.title??'版本与记录' }}
          </button>
        </div>
        <article
          v-if="current"
          class="panel launch-editor"
        >
          <header class="card-heading">
            <div>
              <h2>{{ development && current.key === 'overview' ? '项目概览' : current.title }}</h2><p class="muted">
                {{ current.description }}
              </p>
            </div><button
              v-if="current.rows&&editable"
              class="secondary-button"
              @click="add(current)"
            >
              添加条目
            </button>
          </header>
          <template v-if="tab==='overview'">
            <details
              v-if="workspace.issues.length"
              class="launch-check"
            >
              <summary>送审待补充项（{{ workspace.issues.length }}）</summary><ul>
                <li
                  v-for="(issue,index) in workspace.issues"
                  :key="index"
                >
                  {{ issue }}
                </li>
              </ul>
            </details>
          </template>
          <div
            v-if="tab==='risk'&&workspace.riskReview.valid"
            class="launch-check"
          >
            复核人：{{ workspace.riskReview.actor }} · {{ workspace.riskReview.date }}<p>{{ workspace.riskReview.reason }}</p>
          </div>
          <div
            v-if="tab==='market'"
            class="launch-check"
          >
            <div class="inline-actions">
              <button
                v-if="writable&&draft.asin"
                class="secondary-button"
                :disabled="busy"
                @click="collect"
              >
                更新参考数据
              </button><button
                class="secondary-button"
                @click="readObservation"
              >
                查看采集进度
              </button><button
                v-if="editable&&Object.keys(observation).length"
                class="secondary-button"
                @click="adoptObservation"
              >
                采纳可得参考字段
              </button>
            </div><p>{{ collectionStatus }}</p><dl v-if="Object.keys(observation).length">
              <template
                v-for="(value,key) in observation"
                :key="key"
              >
                <dt>{{ key }}</dt><dd>{{ value }}</dd>
              </template>
            </dl>
          </div>
          <div
            v-if="tab==='handover'&&editable"
            class="launch-check"
          >
            <label>查找自有商品<input
              v-model="productQuery"
              placeholder="商品名 / 编码"
            ></label><button
              class="secondary-button"
              @click="findProducts"
            >
              搜索商品
            </button><p
              v-for="product in products"
              :key="product.id"
            >
              {{ product.productName }} / {{ product.productCode }} <button
                class="secondary-button"
                @click="chooseProduct(product)"
              >
                关联并选择SKU
              </button>
            </p>
          </div>
          <div
            v-if="tab==='evidence'"
            class="launch-check"
          >
            <button
              class="secondary-button"
              @click="readFiles"
            >
              查看已上传附件
            </button><SingleFileUpload
              v-if="writable"
              title="证据附件"
              accept=".pdf,.png,.jpg,.jpeg"
              :max-mb="5"
              :disabled="busy"
              :upload="upload"
            /><p
              v-for="file in files"
              :key="file.id"
            >
              <button
                class="secondary-button"
                @click="download(file)"
              >
                {{ file.name }}
              </button> · 附件编号 {{ file.id }}
            </p>
          </div>
          <LaunchFields
            v-if="current.fields"
            :fields="effectiveFields(current.fields)"
            :model="draft"
            :disabled="!editable||busy"
            @change="setField"
          />
          <div
            v-if="tab==='cost'&&workspace.calculation"
            class="launch-cost-result"
          >
            <strong>已保存方案试算</strong><p>单位成本 ${{ money(workspace.calculation.unitCost) }} · 保本售价 ${{ money(workspace.calculation.breakEven) }} · 可承受广告率 {{ money(Number(workspace.calculation.maxAdvertising)*100) }}%</p><p>售价下降10%：${{ money(workspace.calculation.lowPriceProfit) }}；成本上涨10%：${{ money(workspace.calculation.highCostProfit) }}</p><p>目标月贡献：${{ money(workspace.calculation.monthlyProfit) }}（销量假设） · 一次性投入回收销量：{{ workspace.calculation.recoveryUnits??'当前不可求解' }}</p><p
              v-if="Number(workspace.calculation.profit)<0"
              class="data-state--error"
            >
              当前方案为负贡献，审批须明确说明投入理由。
            </p>
          </div>
          <template v-if="current.rows">
            <section
              v-for="(row,index) in rows(draft,current.rows)"
              :key="String(row.id)"
              class="launch-row"
            >
              <header>
                <strong>{{ current.title }} {{ index+1 }}</strong><small class="muted">条目 {{ String(row.id).slice(0,8) }}</small><button
                  v-if="editable"
                  class="secondary-button"
                  @click="rows(draft,current.rows).splice(index,1)"
                >
                  移除草稿条目
                </button>
              </header><LaunchFields
                :fields="effectiveFields(current.columns??[])"
                :model="row"
                :disabled="!editable||busy"
                @change="(key,value)=>row[key]=value"
              />
            </section><p
              v-if="!rows(draft,current.rows).length"
              class="data-state"
            >
              尚无条目。
            </p>
          </template>
          <div
            v-if="tab==='handover'"
            class="inline-actions"
          >
            <RouterLink to="/products">
              前往商品管理建立或查看商品档案
            </RouterLink><span>返回后关联目标商品和SKU，不自动生成档案。</span>
          </div>
        </article>
        <article
          v-else-if="tab==='history'"
          class="panel launch-history"
        >
          <h2>版本与记录</h2>
          <details v-if="Object.keys(workspace.baseline).length">
            <summary>查看批准立项基线</summary><dl>
              <template
                v-for="s in visibleSections"
                :key="s.key"
              >
                <dt>{{ s.title }}</dt><dd>{{ s.fields?.map(f=>`${f.label}：${workspace!.baseline[f.key]??'—'}`).join('；') }}</dd><dd v-if="s.rows">
                  <div
                    v-for="r in rows(workspace.baseline,s.rows)"
                    :key="String(r.id)"
                  >
                    {{ s.columns?.map(f=>`${f.label}：${r[f.key]??'—'}`).join('；') }}
                  </div>
                </dd>
              </template>
            </dl>
          </details>
          <details
            v-if="Object.keys(workspace.proposal).length"
            open
          >
            <summary>待审核方案变更 · 原值与新值</summary><table class="launch-table">
              <thead><tr><th>字段</th><th>执行方案</th><th>拟变更方案</th></tr></thead><tbody>
                <template
                  v-for="s in visibleSections"
                  :key="s.key"
                >
                  <tr
                    v-for="f in s.fields?.filter(f=>JSON.stringify(workspace!.document[f.key])!==JSON.stringify(workspace!.proposal[f.key]))"
                    :key="f.key"
                  >
                    <th>{{ f.label }}</th><td>{{ workspace.document[f.key]??'—' }}</td><td>{{ workspace.proposal[f.key]??'—' }}</td>
                  </tr><tr v-if="s.rows&&JSON.stringify(workspace.document[s.rows])!==JSON.stringify(workspace.proposal[s.rows])">
                    <th>{{ s.title }}</th><td>
                      <p
                        v-for="r in rows(workspace.document,s.rows)"
                        :key="String(r.id)"
                      >
                        {{ s.columns?.map(f=>`${f.label}：${r[f.key]??'—'}`).join('；') }}
                      </p>
                    </td><td>
                      <p
                        v-for="r in rows(workspace.proposal,s.rows)"
                        :key="String(r.id)"
                      >
                        {{ s.columns?.map(f=>`${f.label}：${r[f.key]??'—'}`).join('；') }}
                      </p>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </details>
          <section
            v-for="revision in workspace.history"
            :key="revision.version"
            class="launch-row"
          >
            <button
              class="secondary-button"
              @click="selectedRevision=selectedRevision===revision.version?null:revision.version"
            >
              v{{ revision.version }} · {{ actionNames[revision.action]??revision.action }} · {{ revision.actor }} · {{ revision.date }}
            </button><p>{{ revision.reason }}</p><dl v-if="selectedRevision===revision.version">
              <template
                v-for="s in visibleSections"
                :key="s.key"
              >
                <dt>{{ s.title }}</dt><dd>{{ s.fields?.map(f=>`${f.label}：${revision.snapshot.document[f.key]??'—'}`).join('；') }}</dd><dd v-if="s.rows">
                  <p
                    v-for="r in rows(revision.snapshot.document,s.rows)"
                    :key="String(r.id)"
                  >
                    {{ s.columns?.map(f=>`${f.label}：${r[f.key]??'—'}`).join('；') }}
                  </p>
                </dd>
              </template>
            </dl>
          </section>
        </article>
      </template>
    </template>
    <div
      v-if="createOpen"
      class="launch-overlay"
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-label="添加候选或新品需求"
        class="launch-dialog"
        @submit.prevent="create"
      >
        <header><h2>{{ development?'添加新品需求':'添加候选' }}</h2></header><div class="launch-dialog-body">
          <label v-if="!development">来源<select
            v-model="createDraft.type"
            v-single-select
          ><option value="AMAZON">亚马逊商品 / ASIN</option><option value="OTHER">其他平台 / 样品 / 原创概念</option></select></label><label>名称<input
            v-model="createDraft.title"
            required
            maxlength="300"
          ></label><label v-if="!development&&createDraft.type==='AMAZON'">ASIN或亚马逊链接<input
            v-model="createDraft.asinOrUrl"
            required
          ></label><label>负责人<input
            v-model="createDraft.owner"
            required
            maxlength="100"
          ></label><label>理由<textarea
            v-model="createDraft.reason"
            required
            maxlength="1000"
          /></label><label v-if="!development&&createDraft.type==='OTHER'">来源链接（可空）<input
            v-model="createDraft.sourceUrl"
            type="url"
          ></label><p
            v-if="error"
            role="alert"
          >
            {{ error }}
          </p>
        </div><footer class="ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="busy"
            @click="createOpen=false"
          >
            取消
          </button><button
            class="ui-button-primary"
            :disabled="busy"
          >
            {{ busy?'创建中…':'创建并评估' }}
          </button>
        </footer>
      </form>
    </div>
    <div
      v-if="confirm"
      class="launch-overlay"
    >
      <form
        role="dialog"
        aria-modal="true"
        :aria-label="label(confirm)"
        class="launch-dialog"
        @submit.prevent="action"
      >
        <header><h2>{{ label(confirm) }}</h2></header><div class="launch-dialog-body">
          <p>本次针对已保存版本 v{{ workspace?.version }}。审核及状态变化将记录操作人和版本。</p><label>原因 / 审核结论<textarea
            v-model="reason"
            maxlength="2000"
            :required="!['START','SUBMIT','NEXT','WITHDRAW'].includes(confirm)"
          /></label><label
            v-if="['APPROVE','COMPLETE','CHANGE_APPROVE'].includes(confirm)"
            class="launch-checkbox"
          ><input
            v-model="selfConfirmed"
            type="checkbox"
          >若本次由我提交，我确认进行自审并记录。</label><p
            v-if="error"
            role="alert"
            class="data-state--error"
          >
            {{ error }}
          </p>
        </div><footer class="ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="busy"
            @click="confirm=''"
          >
            取消
          </button><button
            class="ui-button-primary"
            :disabled="busy"
          >
            {{ busy?'处理中…':'确认' }}
          </button>
        </footer>
      </form>
    </div>
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false"
      @discard="draft=defaults(workspace!.document);snapshot=JSON.stringify(draft);unsaved=false"
    />
  </section>
</template>
<style scoped>
.launch-subtabs{display:flex;gap:8px;flex-wrap:wrap}.launch-subtabs .active{border-color:#e78a2d;color:#a35b12;background:#fff8ee}.launch-ui button{min-height:32px;padding:6px 12px;border:1px solid #d6dee9;border-radius:6px;background:#fff;font-size:13px;cursor:pointer}.launch-ui button:disabled{cursor:default;opacity:.55}.launch-ui{min-width:0;--launch-border:#e2e8f0}.launch-ui .panel{min-height:0;padding:16px}.launch-filter{display:flex;flex-wrap:wrap;gap:10px;align-items:end}.launch-filter label{display:flex;gap:8px;align-items:center;flex:0 1 auto}.launch-filter label:first-child{width:350px}.launch-filter label:nth-child(2){width:205px}.launch-filter input,.launch-filter select{min-width:0;width:100%;flex:1;min-height:32px}.launch-filter label span{flex-shrink:0}.launch-ui .monitor-toolbar>button,.launch-dialog footer>button:last-child{background:#e58926;color:white;border-color:#e58926}.launch-title a{color:#b76a17}.launch-actions button:first-child:not(:disabled){background:#e58926;color:white;border-color:#e58926}.launch-table{min-width:1060px;width:100%;border-collapse:separate;border-spacing:0}.launch-table th,.launch-table td{padding:12px;border-bottom:1px solid var(--launch-border);text-align:left;vertical-align:middle}.launch-table th{background:#f6f8fb;position:sticky;top:0;z-index:2;white-space:nowrap}.launch-table td{background:white}.sticky-name{position:sticky;left:0;z-index:1;min-width:250px;max-width:320px;box-shadow:1px 0 var(--launch-border)}.launch-table th.sticky-name{z-index:3}.sticky-action{position:sticky;right:0;z-index:1;box-shadow:-1px 0 var(--launch-border)}.launch-table small{display:block;color:#64748b;margin-top:5px}.muted{color:#64748b;font-size:13px}.launch-hero{display:flex;align-items:center;gap:16px;flex-wrap:wrap}.launch-hero img{width:96px;height:96px;object-fit:contain}.launch-title{flex:1;min-width:200px}.launch-title h1{font-size:20px;margin:6px 0 10px;overflow-wrap:anywhere}.launch-actions{display:flex;gap:8px;flex-wrap:wrap;max-width:550px}.launch-badge{display:inline-flex;border-radius:5px;background:#eef2f7;color:#475569;padding:4px 8px;font-size:12px}.launch-badge[data-state=APPROVED],.launch-badge[data-state=COMPLETED]{background:#e6f5ed;color:#167047}.launch-badge[data-state=REVIEW],.launch-badge[data-state=FINAL_REVIEW]{background:#fff1dc;color:#9a5d00}.launch-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.launch-metrics article{padding:14px;background:white;border:1px solid var(--launch-border);border-radius:8px}.launch-metrics small{display:block;color:#64748b;margin-bottom:8px}.launch-metrics strong{font-size:19px;font-variant-numeric:tabular-nums}.launch-tabs{display:flex;overflow-x:auto;flex-wrap:nowrap;background:white;border-bottom:1px solid var(--launch-border)}.launch-tabs button{flex-shrink:0;border:0;border-radius:0;background:transparent;color:#475569;min-height:40px;white-space:nowrap}.launch-tabs button.active{color:#c46c17;border-bottom:2px solid #e78a2d;background:#fffaf3}.launch-notice,.launch-check{padding:12px;border:1px solid #f2dcae;background:#fffaf0;border-radius:6px}.launch-editor h2,.launch-history h2{font-size:17px;margin:0 0 8px}.launch-row{padding:16px 0;border-top:1px solid var(--launch-border);margin-top:16px}.launch-row header{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:14px}.launch-row code{color:#64748b;font-size:11px;overflow-wrap:anywhere}.launch-row header button{margin-left:auto}.launch-cost-result{background:#f2f8f5;padding:16px;border-radius:8px;margin-top:14px}.launch-stages{display:flex;overflow-x:auto;background:white;padding:12px;gap:18px}.launch-stages span{display:flex;gap:6px;align-items:center;color:#64748b;white-space:nowrap}.launch-stages b{width:24px;height:24px;border-radius:50%;background:#eef2f7;text-align:center;line-height:24px}.launch-stages .active{color:#b76a17}.launch-stages .active b{background:#fff0da}.launch-stages .done{color:#167047}.launch-history details{padding:14px 0;border-bottom:1px solid var(--launch-border);overflow:auto}.launch-history dt{font-weight:600;margin-top:14px}.launch-history dd{margin:6px 0;overflow-wrap:anywhere}.launch-overlay{position:fixed;inset:0;background:#0f172a80;z-index:1000;display:grid;place-items:center;padding:12px}.launch-dialog{width:min(640px,100%);max-height:calc(100dvh - 24px);background:white;border-radius:10px;display:flex;flex-direction:column;min-height:0}.launch-dialog header,.launch-dialog footer{padding:16px;flex-shrink:0;border-bottom:1px solid var(--launch-border)}.launch-dialog footer{display:flex;justify-content:flex-end;gap:8px;border-top:1px solid var(--launch-border)}.launch-dialog-body{padding:16px;overflow:auto;display:grid;gap:12px}.launch-dialog label{display:grid;gap:6px}.launch-dialog input,.launch-dialog textarea,.launch-dialog select{min-height:40px;width:100%;box-sizing:border-box}.launch-dialog .launch-checkbox{display:flex;align-items:center}.launch-checkbox input{width:20px;min-height:20px}.launch-dialog h2{font-size:18px;margin:0}.launch-ui :deep(.launch-fields){display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.launch-ui :deep(.launch-fields label){display:flex;flex-direction:column;gap:6px;min-width:0;font-size:13px}.launch-ui :deep(.launch-fields input),.launch-ui :deep(.launch-fields select),.launch-ui :deep(.launch-fields textarea){width:100%;min-height:40px;box-sizing:border-box;border:1px solid #d6dee9;border-radius:6px;padding:8px 10px;font:inherit;color:#24334b}.launch-ui :deep(.launch-fields textarea){resize:vertical}.launch-ui :deep(.launch-fields :disabled){color:#334155;background:#f8fafc;opacity:1}.launch-ui :deep(.launch-wide){grid-column:1/-1}@media(max-width:800px){.launch-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.launch-actions{max-width:none}.launch-filter label{flex-wrap:wrap}}@media(max-width:500px){.launch-ui :deep(.launch-fields){grid-template-columns:minmax(0,1fr)}.sticky-name{position:static;min-width:180px;max-width:220px}.launch-metrics strong{font-size:16px}.launch-ui .panel{padding:12px}.launch-hero img{width:72px;height:72px}}
</style>
