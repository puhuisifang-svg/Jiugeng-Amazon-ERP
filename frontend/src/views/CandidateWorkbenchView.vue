<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {candidatesApi,type CandidateItem} from '@/api/candidates'
import {launchApi,stateNames,rows} from '@/api/launch'
import {candidateStates,candidateScore,riskProgress,safeLink,formatDate} from '@/utils/candidatePresentation'
import {session} from '@/state/session'
import {confirmAction} from '@/state/uiConfirmation'
import {vSingleSelect} from '@/directives/singleSelect'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import CandidateAssessmentDialog from '@/components/launch/CandidateAssessmentDialog.vue'
const route=useRoute(),router=useRouter(),api=launchApi(false)
const id=computed(()=>String(route.params.candidateId??'')),writable=computed(()=>session.canAccess(['selection.write']))
const items=ref<CandidateItem[]>([]),total=ref(0),counts=ref<Record<string,number>>({}),owners=ref<string[]>([]),loading=ref(false),error=ref(''),success=ref('')
const query=ref(''),owner=ref(''),source=ref(''),state=ref(''),page=ref(1),size=ref(20),applied=ref({q:'',owner:'',source:''})
const createOpen=ref(false),creating=ref(false),createError=ref(''),initial=ref(''),preview=ref('')
const createDraft=ref({type:'AMAZON',title:'',asinOrUrl:'',owner:'',reason:'',sourceUrl:'',requestId:''})
const createDirty=computed(()=>createOpen.value&&JSON.stringify(createDraft.value)!==initial.value)
let request=0
const totalStates=computed(()=>Object.values(counts.value).reduce((a,b)=>a+b,0))
const image=(item:CandidateItem)=>safeLink(item.document.imageUrl)||safeLink(item.observation?.imageUrl)
const sourceLabel=(item:CandidateItem)=>item.source==='WATCH'?'上新监控':item.asin?'亚马逊商品':'非亚马逊产品'
const nextStep=(s:string)=>({NEW:'开始评估',EVALUATING:'继续评估',RETURNED:'补充资料',REVIEW:'查看审核',APPROVED:'查看通过方案',REJECTED:'查看淘汰原因'}[s]??'详情')
async function load(){const n=++request;loading.value=true;error.value='';try{const result=await candidatesApi.list({...applied.value,state:state.value,page:page.value,size:size.value});if(n!==request)return;items.value=result.items;total.value=result.total;counts.value=result.counts??{};owners.value=result.owners??[];if(page.value>1&&!items.value.length&&result.total){page.value=Math.ceil(result.total/size.value);void load()}}catch(e){if(n===request)error.value=e instanceof Error?e.message:'候选加载失败'}finally{if(n===request)loading.value=false}}
function search(){applied.value={q:query.value.trim(),owner:owner.value,source:source.value};page.value=1;void load()}
function reset(){query.value='';owner.value='';source.value='';state.value='';search()}
function chooseState(value:string){state.value=value;page.value=1;void load()}
function resize(value:number){size.value=value;page.value=1;void load()}
function openCreate(){createDraft.value={type:'AMAZON',title:'',asinOrUrl:'',owner:session.user.value?.displayName??'',reason:'',sourceUrl:'',requestId:crypto.randomUUID()};initial.value=JSON.stringify(createDraft.value);createError.value='';createOpen.value=true}
async function create(){creating.value=true;createError.value='';try{const result=await api.create(createDraft.value);createOpen.value=false;creating.value=false;success.value='已打开候选评估；相同 ASIN 自动复用已有候选。';await router.push(`/selection/candidates/${result.id}`);void load()}catch(e){createError.value=e instanceof Error?e.message:'创建失败，请重试'}finally{creating.value=false}}
function closeDetail(){void router.push('/selection/candidates')}
async function guard(){return !creating.value&&(!createDirty.value||await confirmAction('放弃未保存的候选信息？'))}
onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
function beforeUnload(e:BeforeUnloadEvent){if(createDirty.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload);onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload))
watch(id,(value,old)=>{if(!value&&old)void load()})
void load()
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="candidate-ui page-stack monitor-workbench">
    <SuccessToast :message="success" />
    <form class="candidate-filters filter-panel ui-filter-row" @submit.prevent="search">
      <label class="candidate-search">搜索<input v-model="query" placeholder="产品名称 / ASIN / 负责人"></label>
      <label>来源<select v-model="source" v-single-select><option value="">全部来源</option><option value="AMAZON">亚马逊商品</option><option value="WATCH">上新监控</option><option value="OTHER">非亚马逊产品</option></select></label>
      <label>负责人<select v-model="owner" v-single-select><option value="">全部负责人</option><option v-for="name in owners" :key="name">{{ name }}</option></select></label>
      <button class="candidate-primary" :disabled="loading">搜索</button><button type="button" class="filter-reset-button" @click="reset">重置</button>
    </form>
    <div class="candidate-toolbar"><button v-if="writable" class="candidate-primary" @click="openCreate">＋ 添加候选</button><span>收集机会 → 完成评估 → 审核通过 → 新品开发</span></div>
    <nav class="candidate-state-tabs" aria-label="候选状态"><button :aria-pressed="state===''" @click="chooseState('')">全部 <b>{{ totalStates }}</b></button><button v-for="s in candidateStates" :key="s" :aria-pressed="state===s" @click="chooseState(s)">{{ stateNames[s] }} <b>{{ counts[s]??0 }}</b></button></nav>
    <p v-if="error" role="alert" class="data-state--error">{{ error }} <button @click="load">重试</button></p>
    <div class="candidate-table-wrap">
      <TableViewport>
        <table class="candidate-table">
          <thead><tr><th class="candidate-product-fixed">候选产品</th><th>评估状态</th><th>来源 / 负责人</th><th>销售表现</th><th>参考售价</th><th>目标售价</th><th>评估得分</th><th>风险调查</th><th>更新时间</th><th class="candidate-action-fixed">操作</th></tr></thead><tbody>
            <tr v-for="item in items" :key="item.id">
              <td class="candidate-product-fixed"><div class="candidate-product"><button v-if="image(item)" class="candidate-thumbnail" aria-label="放大产品图片" @click="preview=image(item)"><img :src="image(item)" alt="产品图片"></button><span v-else class="candidate-image-empty">暂无图片</span><div><RouterLink :to="`/selection/candidates/${item.id}`">{{ item.title }}</RouterLink><small>{{ item.asin||'非亚马逊产品' }}</small><small v-if="item.document.reason" class="candidate-reason" :title="String(item.document.reason)">{{ item.document.reason }}</small></div></div></td>
              <td><span class="candidate-badge" :data-state="item.state">{{ stateNames[item.state]??'待启用' }}</span></td><td>{{ sourceLabel(item) }}<small>{{ item.document.owner||item.owner||'待分配' }}</small></td>
              <td>{{ item.document.salesValue||'待补充' }}<small>{{ ({OBSERVED:'前台展示',MANUAL:'人工录入',ESTIMATE:'估算'} as Record<string,string>)[String(item.document.salesType)]|| (item.document.salesMissingReason?'已记录无数据原因':'') }}</small></td><td>{{ item.document.referencePrice??item.observation?.price??'—' }}</td><td>{{ item.document.price!=null&&item.document.price!==''?'$'+item.document.price:'—' }}</td><td>{{ candidateScore(item.document)??'未完成' }}<small v-if="candidateScore(item.document)!=null">满分 100</small></td><td>{{ riskProgress(item.document) }} / 5 类<small>{{ rows(item.document,'risks').some(r=>['BLOCKED','DOUBT','REMEDIATE'].includes(String(r.conclusion)))?'存在待处理风险':riskProgress(item.document)===5?'待审核复核':'待完善调查' }}</small></td><td class="candidate-time">{{ formatDate(item.updatedAt) }}</td><td class="candidate-action-fixed"><RouterLink :to="`/selection/candidates/${item.id}`">{{ nextStep(item.state) }}</RouterLink><RouterLink v-if="item.state==='APPROVED'&&session.canAccess(['development.read'])" :to="`/product-development/${item.id}`">新品开发 ↗</RouterLink></td>
            </tr>
            <tr v-if="!items.length"><td colspan="10" class="data-state"><strong>{{ loading?'正在加载候选…':error?'候选加载失败':'暂无符合条件的候选' }}</strong><p v-if="!loading&&!error">可从产品监控、上新监控加入，或手动添加 ASIN、样品与原创产品。</p><button v-if="!loading&&writable&&!error" @click="openCreate">添加第一个候选</button></td></tr>
          </tbody>
        </table>
      </TableViewport>
    </div>
    <ListPagination :page="page" :page-size="size" :total="total" :disabled="loading" @update:page="page=$event;load()" @update:page-size="resize" />
    <CandidateAssessmentDialog v-if="id" :key="id" :candidate-id="id" @close="closeDetail" @changed="load" />
    <AccessDialog v-if="createOpen" title="添加候选" :busy="creating" :dirty="createDirty" @close="createOpen=false"><form id="candidate-create" class="access-form" @submit.prevent="create"><p v-if="createError" role="alert" class="data-state--error">{{ createError }}</p><label>产品来源<select v-model="createDraft.type" v-single-select><option value="AMAZON">亚马逊商品 / ASIN</option><option value="OTHER">非亚马逊产品 / 样品 / 原创概念</option></select></label><label v-if="createDraft.type==='AMAZON'">ASIN 或美国站商品链接<input v-model.trim="createDraft.asinOrUrl" required maxlength="1000" placeholder="B0… 或 https://www.amazon.com/dp/…"></label><label>候选名称<input v-model.trim="createDraft.title" required maxlength="300" placeholder="填写易于识别的产品名称"></label><label>负责人<input v-model.trim="createDraft.owner" required maxlength="100"></label><label>入池理由<textarea v-model.trim="createDraft.reason" required maxlength="1000" rows="3" placeholder="看到了什么机会？准备解决什么问题？" /></label><label v-if="createDraft.type==='OTHER'">来源链接（选填）<input v-model.trim="createDraft.sourceUrl" type="url" pattern="https://.*" placeholder="供应商或平台产品链接"></label><p class="candidate-hint">先建立候选，再逐步补充评估。亚马逊产品创建后会请求采集参考数据，采集结果需核对后采纳。</p></form><template #footer><button :disabled="creating" @click="guard().then(ok=>{if(ok)createOpen=false})">取消</button><button class="candidate-primary" form="candidate-create" :disabled="creating">{{ creating?'创建中…':'创建并评估' }}</button></template></AccessDialog>
    <AccessDialog v-if="preview" title="产品图片" wide @close="preview=''"><img class="candidate-image-preview" :src="preview" alt="放大的产品图片"></AccessDialog>
  </section>
</template>
<style src="@/styles/candidateWorkbench.css"></style>
