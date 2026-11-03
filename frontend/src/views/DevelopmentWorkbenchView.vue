<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {developmentWorkbenchApi,type DevelopmentItem} from '@/api/developmentWorkbench'
import {launchApi,stateNames,stages,rows} from '@/api/launch'
import {developmentStates,overdue} from '@/utils/developmentPresentation'
import {safeLink,formatDate} from '@/utils/candidatePresentation'
import {session} from '@/state/session'
import {confirmAction} from '@/state/uiConfirmation'
import {vSingleSelect} from '@/directives/singleSelect'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import DevelopmentProjectDialog from '@/components/launch/DevelopmentProjectDialog.vue'
const route=useRoute(),router=useRouter(),api=launchApi(true)
const id=computed(()=>String(route.params.id??'')),writable=computed(()=>session.canAccess(['development.write']))
const items=ref<DevelopmentItem[]>([]),total=ref(0),counts=ref<Record<string,number>>({}),owners=ref<string[]>([]),loading=ref(false),error=ref(''),success=ref('')
const query=ref(''),owner=ref(''),stage=ref(-1),state=ref(''),page=ref(1),size=ref(20),applied=ref({q:'',owner:'',stage:-1})
const createOpen=ref(false),creating=ref(false),createError=ref(''),initial=ref(''),preview=ref('')
const createDraft=ref({title:'',owner:'',reason:'',requestId:''}),createDirty=computed(()=>createOpen.value&&JSON.stringify(createDraft.value)!==initial.value)
const deleting=ref('')
async function remove(item:DevelopmentItem){if(deleting.value)return;if(!await confirmAction(`确定删除开发项目「${item.title}」？删除后将从新品开发和样品待开发列表移除，来源候选、产品档案及历史记录保留。`))return;deleting.value=item.id;error.value='';try{await developmentWorkbenchApi.remove(item.id,item.version);success.value='开发项目已删除';await load()}catch(e){error.value=e instanceof Error?e.message:'删除失败，请重试'}finally{deleting.value=''}}
let request=0
const totalStates=computed(()=>Object.values(counts.value).reduce((a,b)=>a+b,0))
async function load(){const n=++request;loading.value=true;error.value='';try{const result=await developmentWorkbenchApi.list({...applied.value,state:state.value,page:page.value,size:size.value});if(n!==request)return;items.value=result.items;total.value=result.total;counts.value=result.counts??{};owners.value=result.owners??[];if(page.value>1&&!items.value.length){page.value=Math.max(1,Math.ceil(total.value/size.value));void load()}}catch(e){if(n===request)error.value=e instanceof Error?e.message:'项目加载失败'}finally{if(n===request)loading.value=false}}
function search(){applied.value={q:query.value.trim(),owner:owner.value,stage:stage.value};page.value=1;void load()}
function reset(){query.value='';owner.value='';stage.value=-1;state.value='';search()}
function chooseState(value:string){state.value=value;page.value=1;void load()}
function resize(value:number){size.value=value;page.value=1;void load()}
function openCreate(){createDraft.value={title:'',owner:session.user.value?.displayName??'',reason:'',requestId:crypto.randomUUID()};initial.value=JSON.stringify(createDraft.value);createError.value='';createOpen.value=true}
async function create(){creating.value=true;createError.value='';try{const result=await api.create(createDraft.value);createOpen.value=false;creating.value=false;success.value='开发草稿已创建，完善立项资料后提交审核。';await router.push(`/product-development/${result.id}`);void load()}catch(e){createError.value=e instanceof Error?e.message:'创建失败'}finally{creating.value=false}}
async function guard(){return !creating.value&&(!createDirty.value||await confirmAction('放弃未保存的项目资料？'))}
async function closeCreate(){if(await guard()){createOpen.value=false;if(route.path.endsWith('/new'))void router.push('/product-development')}}
onBeforeRouteLeave(guard);onBeforeRouteUpdate(guard)
function beforeUnload(e:BeforeUnloadEvent){if(createDirty.value){e.preventDefault();e.returnValue=''}}
window.addEventListener('beforeunload',beforeUnload);onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload))
watch(()=>route.path,p=>{if(p.endsWith('/new')&&writable.value)openCreate()},{immediate:true})
watch(id,(value,old)=>{if(!value&&old)void load()})
void load()
</script>
<template>
  <!-- eslint-disable vue/max-attributes-per-line, vue/singleline-html-element-content-newline -->
  <section class="candidate-ui development-ui page-stack">
    <SuccessToast :message="success" />
    <form class="candidate-filters filter-panel ui-filter-row" @submit.prevent="search"><label class="candidate-search">搜索<input v-model="query" placeholder="项目名称 / 开发编号 / 负责人"></label><label>开发阶段<select v-model="stage" v-single-select><option :value="-1">全部阶段</option><option v-for="(name,index) in stages" :key="index" :value="index">{{ name }}</option></select></label><label>负责人<select v-model="owner" v-single-select><option value="">全部负责人</option><option v-for="name in owners" :key="name">{{ name }}</option></select></label><button class="candidate-primary" :disabled="loading">搜索</button><button type="button" class="filter-reset-button" @click="reset">重置</button></form>
    <div class="candidate-toolbar"><button v-if="writable" class="candidate-primary" @click="openCreate">＋ 新建开发项目</button><span>候选审核通过自动进入；独立创建的项目需先完成立项审核。</span></div>
    <nav class="candidate-state-tabs" aria-label="项目状态"><button :aria-pressed="state===''" @click="chooseState('')">全部 <b>{{ totalStates }}</b></button><button v-for="s in developmentStates" :key="s" :aria-pressed="state===s" @click="chooseState(s)">{{ s==='DRAFT'?'待立项 / 接手':stateNames[s] }} <b>{{ counts[s]??0 }}</b></button></nav>
    <p v-if="error" role="alert" class="data-state--error">{{ error }} <button @click="load">重试</button></p>
    <div class="candidate-table-wrap"><TableViewport><table class="candidate-table development-table"><thead><tr><th class="candidate-product-fixed">开发项目</th><th>项目状态</th><th>当前阶段</th><th>负责人 / 采购协作</th><th>计划完成日</th><th>打样进度</th><th>目标售价</th><th>方案版本</th><th>更新时间</th><th class="candidate-action-fixed">操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td class="candidate-product-fixed"><div class="candidate-product"><button v-if="safeLink(item.document.imageUrl)" class="candidate-thumbnail" aria-label="放大产品图片" @click="preview=safeLink(item.document.imageUrl)"><img :src="safeLink(item.document.imageUrl)" alt="产品图片"></button><span v-else class="candidate-image-empty">暂无图片</span><div><RouterLink :to="`/product-development/${item.id}`">{{ item.title }}</RouterLink><small>{{ item.number }}</small><small>{{ item.source==='MANUAL'?'手动立项':'候选评估转入' }}</small></div></div></td><td><span class="candidate-badge" :data-state="item.state">{{ item.state==='DRAFT'?'待立项 / 接手':stateNames[item.state] }}</span><small v-if="item.hasProposal" class="development-warning">方案变更待审核</small></td><td><span>{{ stages[item.stage??0] }}</span><div class="development-mini-progress"><i v-for="(_,index) in stages" :key="index" :class="{done:item.state==='COMPLETED'||index<(item.stage??0),current:item.state!=='COMPLETED'&&index===(item.stage??0)}" /></div></td><td>{{ item.document.owner||item.owner||'待分配' }}<small>{{ item.document.buyer||'未指定采购协作' }}</small></td><td>{{ item.document.endDate||'待安排' }}<small v-if="overdue(item.document,item.state)" class="development-warning">已逾期</small></td><td>{{ rows(item.document,'samples').length }} 轮<small>{{ item.document.selectedSample?'已选定最终样品':'待确认最终样品' }}</small></td><td>{{ item.document.price!=null?'$'+item.document.price:'—' }}</td><td>{{ item.document.specVersion||'—' }}</td><td class="candidate-time">{{ formatDate(item.updatedAt) }}</td><td class="candidate-action-fixed"><RouterLink :to="`/product-development/${item.id}`">详情</RouterLink><button v-if="writable" class="development-delete" :disabled="!!deleting" @click="remove(item)">{{ deleting===item.id?'删除中…':'删除' }}</button></td></tr><tr v-if="!items.length"><td colspan="10" class="data-state">{{ loading?'正在加载项目…':error?'项目加载失败':'暂无符合条件的开发项目' }}<p v-if="!loading&&!error">候选审核通过后自动进入，也可手动创建明确的开发需求。</p></td></tr></tbody></table></TableViewport></div>
    <ListPagination :page="page" :page-size="size" :total="total" :disabled="loading" @update:page="page=$event;load()" @update:page-size="resize" />
    <DevelopmentProjectDialog v-if="id" :key="id" :project-id="id" @close="router.push('/product-development')" @changed="load" />
    <AccessDialog v-if="createOpen" title="新建开发项目" :busy="creating" :dirty="createDirty" @close="createOpen=false;route.path.endsWith('/new')&&router.push('/product-development')"><form id="development-create" class="access-form" @submit.prevent="create"><p class="candidate-hint">独立项目先建立草稿，再补充需求、方案、成本与风险资料；立项审核通过后开始开发。</p><p v-if="createError" role="alert" class="data-state--error">{{ createError }}</p><label>项目名称<input v-model.trim="createDraft.title" required maxlength="300" placeholder="填写易于识别的开发项目名称"></label><label>开发负责人<input v-model.trim="createDraft.owner" required maxlength="100"></label><label>立项理由<textarea v-model.trim="createDraft.reason" required maxlength="1000" rows="3" placeholder="要解决什么问题？为什么启动这个项目？" /></label></form><template #footer><button :disabled="creating" @click="closeCreate">取消</button><button class="candidate-primary" form="development-create" :disabled="creating">{{ creating?'创建中…':'创建项目' }}</button></template></AccessDialog>
    <AccessDialog v-if="preview" title="产品图片" wide @close="preview=''"><img class="candidate-image-preview" :src="preview" alt="产品图片"></AccessDialog>
  </section>
</template>
<style src="@/styles/candidateWorkbench.css"></style>
<style src="@/styles/developmentWorkbench.css"></style>

<style scoped>
.development-delete{border:0;background:transparent;color:#dc2626;padding:4px 0;font-size:13px;cursor:pointer}.development-delete:disabled{opacity:.5;cursor:wait}
</style>
