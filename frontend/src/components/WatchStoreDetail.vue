<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useCollectionStatus } from '@/composables/useCollectionStatus'
import { isNovel, noveltyStatus, noveltyLabel } from '@/composables/watchNovelty'
import { confirmAction } from '@/state/uiConfirmation'
import { monitoringApi } from '@/api/monitoring'
import { apiRequest } from '@/api/client'
import type { WatchStoreDto, WatchEventDto } from '@/api/resources'
import SuccessToast from './SuccessToast.vue'
import ListPagination from './ListPagination.vue'
import HorizontalTableViewport from './HorizontalTableViewport.vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
import StoreTimingForm from './StoreTimingForm.vue'
import '@/styles/monitoringWorkbench.css'
const props=defineProps<{store:WatchStoreDto|undefined;writable:boolean;newProducts?:WatchEventDto[]|undefined;initialTab?:string}>()
const emit=defineEmits<{close:[];changed:[]}>()
interface Product {provider:string;asin:string;title:string;firstAt:string;lastAt:string;missingCount:number;missingAt:string|null;imageUrl?:string;price?:number;priceType?:string;rating?:string;event?:WatchEventDto}
interface Detail {automaticProducts:Product[]}
const detail=ref<Detail>(), events=ref<WatchEventDto[]>([]), dialog=ref<HTMLDialogElement>(), timing=ref<InstanceType<typeof StoreTimingForm>>()
const error=ref(''), eventError=ref(''), busy=ref(false), loading=ref(false), tab=ref('online'), q=ref(''), page=ref(1), size=ref(20), unsaved=ref(false), notice=ref(''), settingsVisited=ref(false)
const judgement=ref('NOVEL')
const applied=ref({q:'',from:'',to:'',judgement:'NOVEL'})
const isRecord=computed(()=>['new','novelty'].includes(tab.value))
const novelty=useCollectionStatus('WATCH_NOVELTY',()=>{if(props.store)void loadEvents(props.store.id,request)})
const filters=computed(()=>isRecord.value?applied.value:{q:q.value,from:from.value,to:to.value,judgement:judgement.value})
function search(){if(invalidRange.value)return;applied.value={q:q.value.trim(),from:from.value,to:to.value,judgement:judgement.value};page.value=1;chosen.value=[]}
const chosen=ref<string[]>([]), from=ref(''), to=ref(''), version=ref(0)
const draft=ref({name:'',owner:'',remark:'',enabled:true}), baseline=ref('')
function day(value:string){return new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}).format(new Date(value))}
function date(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false})}
const today=()=>day(new Date().toISOString())
function todayRange(){from.value=today();to.value=today()}
const online=computed(()=>detail.value?.automaticProducts.filter(p=>p.missingCount<2)??[])
const todayCount=computed(()=>events.value.filter(e=>day(e.detectedAt)===today()).length)
const novelTodayCount=computed(()=>events.value.filter(e=>day(e.detectedAt)===today()&&isNovel(e)).length)
const sourceProducts=computed<Product[]>(()=>isRecord.value?events.value.map(e=>({...detail.value?.automaticProducts.find(p=>p.asin===e.asin),event:e,provider:'event',asin:e.asin,title:e.title??e.asin,firstAt:e.detectedAt,lastAt:detail.value?.automaticProducts.find(p=>p.asin===e.asin)?.lastAt??e.detectedAt,missingCount:0,missingAt:null})):detail.value?.automaticProducts??[])
const invalidRange=computed(()=>!!from.value&&!!to.value&&from.value>to.value)
const auto=computed(()=>(!!filters.value.from&&!!filters.value.to&&filters.value.from>filters.value.to)?[]:sourceProducts.value.filter(p=>(isRecord.value||(tab.value==='missing'?p.missingCount>=2:p.missingCount<2))&&(tab.value!=='novelty'||!!p.event&&(filters.value.judgement==='ALL'||(filters.value.judgement==='NOVEL'?isNovel(p.event):noveltyStatus(p.event)===filters.value.judgement)))&&(!filters.value.q||p.asin.includes(filters.value.q.toUpperCase())||p.title.toLowerCase().includes(filters.value.q.toLowerCase()))&&(!filters.value.from||day(p.firstAt)>=filters.value.from)&&(!filters.value.to||day(p.firstAt)<=filters.value.to)))
const total=computed(()=>auto.value.length)
const dirty=computed(()=>JSON.stringify(draft.value)!==baseline.value||!!timing.value?.dirty)
const locked=computed(()=>busy.value||!!timing.value?.busy)
const visible=computed(()=>auto.value.slice((page.value-1)*size.value,page.value*size.value))
function switchTab(id:string){tab.value=id;if(id==='settings')settingsVisited.value=true;if(['new','novelty'].includes(id))todayRange();else{from.value='';to.value=''}q.value='';judgement.value='NOVEL';applied.value={q:'',from:from.value,to:to.value,judgement:'NOVEL'}}
watch([q,tab,size,from,to],()=>{page.value=1;chosen.value=[]})
watch(page,()=>{chosen.value=[]})
watch(()=>props.newProducts,value=>{if(value!==undefined){events.value=value;switchTab(props.initialTab==='novelty'?'novelty':'new')}})
let request=0
async function loadEvents(id:string,current:number){try{const result=await apiRequest<WatchEventDto[]>(`/v1/watch/stores/${id}/events`);if(current===request&&Array.isArray(result))events.value=result}catch(e){if(current===request)eventError.value=e instanceof Error?e.message:'上架记录加载失败'}}
watch(()=>props.store,async value=>{
 if(!value)return
 const current=++request;detail.value=undefined;events.value=props.newProducts??[];error.value='';eventError.value='';notice.value='';settingsVisited.value=false;chosen.value=[];page.value=1;switchTab(props.initialTab??(props.newProducts===undefined?'online':'new'))
 version.value=value.version??0;draft.value={name:value.storeName,owner:value.owner??'',remark:value.remark??'',enabled:value.status==='ACTIVE'};baseline.value=JSON.stringify(draft.value)
 await nextTick();dialog.value?.showModal();loading.value=true
 try{const result=await apiRequest<Detail>(`/v1/watch/stores/${value.id}`);if(current===request)detail.value=result;await loadEvents(value.id,current)}catch(e){if(current===request)error.value=e instanceof Error?e.message:'店铺详情加载失败'}finally{if(current===request)loading.value=false}
})
onBeforeRouteLeave(()=>{if(props.store&&locked.value)return false;if(props.store&&dirty.value){unsaved.value=true;return false}return true})
function close(){if(locked.value)return;if(dirty.value)unsaved.value=true;else discard()}
function discard(){request++;unsaved.value=false;dialog.value?.close();emit('close')}
async function save(){if(!props.store||!props.writable||locked.value)return;busy.value=true;error.value='';try{const result=await apiRequest<WatchStoreDto>(`/v1/watch/stores/${props.store.id}`,{method:'PUT',body:JSON.stringify({...draft.value,version:version.value})});version.value=result.version??version.value;baseline.value=JSON.stringify(draft.value);notice.value='店铺信息已保存';emit('changed')}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}}
async function monitor(asins:string[]){if(!props.writable||locked.value)return;busy.value=true;error.value='';try{const result=await monitoringApi.create({requestId:crypto.randomUUID(),asins,tags:[],remark:''});notice.value=`已添加 ${result.filter(r=>r.status==='CREATED').length} 个，已存在 ${result.filter(r=>r.status==='EXISTS').length} 个`;const failed=result.filter(r=>!['CREATED','EXISTS'].includes(r.status));if(failed.length)error.value=failed.map(r=>r.message).join('；');chosen.value=[]}catch(e){error.value=e instanceof Error?e.message:'添加监控失败'}finally{busy.value=false}}
async function retryNovelty(event:WatchEventDto){if(!props.writable||locked.value)return;if(await novelty.collect(event.id))notice.value='日期补采已排队，上架记录不受影响';else error.value=novelty.error.value}
async function markNovelty(event:WatchEventDto,action:string){
 if(!props.writable||locked.value||!await confirmAction(action==='RESET'?'撤销人工标记，恢复自动判定？':action==='EXCLUDED'?'将此商品排除出新品统计？上架记录会保留。':'确认此商品为新品？该人工判断会计入首次发现当天的新品统计。'))return
 busy.value=true;error.value=''
 try{const result=await apiRequest<WatchEventDto>(`/v1/watch/events/${event.id}/novelty`,{method:'PUT',body:JSON.stringify({version:event.noveltyVersion??0,action})});events.value=events.value.map(e=>e.id===result.id?result:e);notice.value='新品标记已保存';emit('changed')}catch(e){error.value=e instanceof Error?e.message:'保存失败'}finally{busy.value=false}
}
</script>
<template>
  <dialog
    ref="dialog"
    class="store-detail monitor-workbench"
    aria-label="店铺监控详情"
    @cancel.prevent="close"
  >
    <header class="store-detail-header">
      <div class="store-identity">
        <span
          class="store-avatar"
          aria-hidden="true"
        >店</span><div>
          <h2>{{ store?.storeName }}</h2><p class="store-subtitle">
            店铺监控详情 · 美国站 · {{ store?.storeKey }}
          </p>
        </div>
      </div><button
        :disabled="locked"
        @click="close"
      >
        关闭
      </button>
    </header>
    <div class="store-overview">
      <div><span>在线产品</span><strong>{{ online.length.toLocaleString() }}</strong></div><div><span>今日上架</span><strong>{{ todayCount.toLocaleString() }}</strong></div><div><span>今日新品</span><strong>{{ novelTodayCount.toLocaleString() }}</strong></div><div class="store-last-observed">
        <span>最近观测</span><strong>{{ detail?.automaticProducts[0]?.lastAt?date(detail.automaticProducts[0].lastAt):'等待采集' }}</strong>
      </div>
    </div>
    <nav
      class="store-detail-tabs"
      aria-label="店铺详情页签"
    >
      <button
        v-for="t in [{id:'online',label:'在线产品'},{id:'missing',label:'疑似下架产品'},{id:'new',label:'上架记录'},{id:'novelty',label:'新品记录'},{id:'settings',label:'店铺设置'}]"
        :key="t.id"
        :aria-pressed="tab===t.id"
        @click="switchTab(t.id)"
      >
        {{ t.label }}
      </button>
    </nav>
    <SuccessToast :message="notice" />
    <div
      class="store-body"
      :class="{'store-body-products':tab!=='settings'}"
    >
      <p
        v-if="error"
        class="mw-error"
        role="alert"
      >
        {{ error }}
      </p><p
        v-if="loading"
        role="status"
      >
        正在加载店铺数据…
      </p>
      <template v-if="tab!=='settings'">
        <div
          class="store-product-filters"
          :class="{'store-record-filters':isRecord}"
        >
          <label>{{ isRecord?'发现日期':'首次发现' }}<input
            v-model="from"
            type="date"
            :aria-label="isRecord?'上架开始日期':'首次发现开始日期'"
          ></label><label>至<input
            v-model="to"
            type="date"
            :aria-label="isRecord?'上架结束日期':'首次发现结束日期'"
          ></label><label class="store-search">搜索<input
            v-model="q"
            placeholder="商品标题 / ASIN"
            aria-label="搜索标题或ASIN"
          ></label><label v-if="tab==='novelty'">判断结果<select
            v-model="judgement"
            aria-label="新品判断结果"
          ><option value="NOVEL">新品候选 / 疑似 / 已确认</option><option value="PENDING">待核实</option><option value="OLD">老链接</option><option value="EXCLUDED">已排除</option><option value="ALL">全部记录</option></select></label><button
            v-if="isRecord"
            class="mw-primary"
            :disabled="loading||invalidRange"
            @click="search"
          >
            搜索
          </button>
        </div>
        <div class="store-product-actions">
          <button
            class="mw-primary"
            :disabled="!writable||!chosen.length||locked||loading"
            @click="monitor(chosen)"
          >
            批量添加产品监控
          </button><span>{{ chosen.length?`已选 ${chosen.length} 个商品`:'勾选商品可添加至产品监控' }}</span>
        </div>
        <p
          v-if="invalidRange"
          class="mw-error"
          role="alert"
        >
          开始日期不能晚于结束日期。
        </p>
        <p
          v-if="isRecord&&eventError"
          class="mw-error"
          role="alert"
        >
          {{ eventError }} <button @click="store&&loadEvents(store.id,request)">
            重试
          </button>
        </p>
        <p class="store-hint">
          {{ tab==='novelty'?'统一新品窗口60天，按首次发现日判断；无日期不自动认定新品，补采不会改变记录归属日期。':isRecord?'按美国太平洋时间统计首次发现，不代表实际首次上架；初次建档商品不计入今日上架。':tab==='missing'?'搜索未出现不等于下架；当前公开网页采集不会仅凭缺失判定下架。':'价格为美国站公开报价，“起”为其他卖家最低报价；缺失价格或评分不会填零。' }}
        </p>
        <HorizontalTableViewport>
          <table
            class="mw-table ui-action-table"
            :class="{'store-novelty-table':tab==='novelty'}"
          >
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="选择本页商品"
                    :checked="!!visible.length&&visible.every(p=>chosen.includes(p.asin))"
                    @change="chosen=($event.target as HTMLInputElement).checked?visible.map(p=>p.asin):[]"
                  >
                </th><th>产品信息</th><th>价格</th><th>星级评分</th><th>首次发现时间</th><th>最近观测</th><template v-if="tab==='novelty'">
                  <th>亚马逊首次可用日期</th><th>判断结果 / 依据</th>
                </template><th>操作</th>
              </tr>
            </thead><tbody>
              <tr
                v-for="p in visible"
                :key="p.provider+p.asin"
              >
                <td>
                  <input
                    v-model="chosen"
                    type="checkbox"
                    :value="p.asin"
                    :aria-label="`选择 ${p.asin}`"
                  >
                </td><td>
                  <div class="mw-cell-product">
                    <img
                      v-if="p.imageUrl"
                      :src="p.imageUrl"
                      alt="商品主图"
                      loading="lazy"
                    ><span
                      v-else
                      class="store-image-empty"
                    >暂无图片</span><div>
                      <a
                        :href="`https://www.amazon.com/dp/${p.asin}`"
                        target="_blank"
                        rel="noopener noreferrer"
                      >{{ p.title }}</a><small>{{ p.asin }}</small>
                    </div>
                  </div>
                </td><td class="store-price">
                  {{ p.price!=null?`US$ ${Number(p.price).toFixed(2)}${p.priceType==='OFFER_FROM'?' 起':''}`:'暂无公开报价' }}
                </td><td><span :class="{'store-rating':!!p.rating}">{{ p.rating||'暂无公开评分' }}</span></td><td>{{ isRecord?date(p.firstAt):day(p.firstAt) }}</td><td>{{ date(p.lastAt) }}</td><template v-if="tab==='novelty'&&p.event">
                  <td>
                    {{ p.event.availableDate||'未观测' }}<small><a
                      :href="`https://www.amazon.com/dp/${p.asin}`"
                      target="_blank"
                      rel="noopener noreferrer"
                    >查看来源</a></small>
                  </td><td class="store-novelty-evidence">
                    <span
                      class="mw-status"
                      :class="{good:isNovel(p.event),warn:noveltyStatus(p.event)==='PENDING'}"
                    >{{ noveltyLabel(p.event) }}</span><small>{{ p.event.noveltyReason||'等待补采亚马逊首次可用日期' }}</small><small v-if="p.event.noveltyCheckedAt">核实于 {{ date(p.event.noveltyCheckedAt) }}</small><small v-if="novelty.state(p.event.id)">{{ novelty.label(p.event.id) }} {{ novelty.state(p.event.id)?.reason }}</small>
                  </td>
                </template><td>
                  <button
                    class="mw-link"
                    :disabled="!writable||locked"
                    @click="monitor([p.asin])"
                  >
                    添加产品监控
                  </button><template v-if="tab==='novelty'&&p.event">
                    <button
                      class="mw-link"
                      :disabled="!writable||locked||novelty.pending(p.event.id)"
                      @click="retryNovelty(p.event)"
                    >
                      补采日期
                    </button><button
                      v-if="p.event.noveltyOverride"
                      class="mw-link"
                      :disabled="!writable||locked"
                      @click="markNovelty(p.event,'RESET')"
                    >
                      撤销标记
                    </button><template v-else>
                      <button
                        class="mw-link"
                        :disabled="!writable||locked"
                        @click="markNovelty(p.event,'CONFIRMED')"
                      >
                        确认新品
                      </button><button
                        class="mw-link"
                        :disabled="!writable||locked"
                        @click="markNovelty(p.event,'EXCLUDED')"
                      >
                        排除
                      </button>
                    </template>
                  </template>
                </td>
              </tr>
              <tr v-if="!loading&&!total">
                <td
                  :colspan="tab==='novelty'?9:7"
                  class="mw-empty"
                >
                  <strong>{{ tab==='novelty'?'暂无符合条件的新品记录':isRecord?'所选时段暂无上架记录':'暂无符合条件的产品' }}</strong>{{ tab==='novelty'?'可切换“待核实”或“全部记录”，查看日期补采与判断结果。':isRecord?'可调整日期范围查看历史上架。':'可调整搜索条件，或等待店铺采集完成。' }}
                </td>
              </tr>
            </tbody>
          </table>
        </HorizontalTableViewport>
      </template>
      <div
        v-if="settingsVisited"
        v-show="tab==='settings'"
        class="store-settings-layout"
      >
        <section class="store-settings-card">
          <div class="store-card-heading">
            <div><h3>店铺基本信息</h3><p>维护名称、负责人和备注，暂停监控会保留历史记录。</p></div>
          </div><form
            id="store-basic-form"
            class="store-basic-form"
            @submit.prevent="save"
          >
            <fieldset :disabled="!writable||locked">
              <div class="store-settings-grid">
                <label>店铺名称<input
                  v-model="draft.name"
                  required
                  maxlength="200"
                ></label><label>监控人<input
                  v-model="draft.owner"
                  maxlength="100"
                ></label><label>监控状态<select v-model="draft.enabled"><option :value="true">监控中</option><option :value="false">暂停监控</option></select></label><label class="store-form-wide">备注<textarea
                  v-model="draft.remark"
                  maxlength="1000"
                  rows="3"
                  placeholder="记录该店铺的关注重点"
                /></label>
              </div>
            </fieldset>
          </form>
        </section>
        <StoreTimingForm
          v-if="store"
          ref="timing"
          :store-id="store.id"
          :writable="writable&&!busy"
          hide-actions
          @saved="emit('changed')"
        />
      </div>
    </div>
    <footer>
      <ListPagination
        v-if="tab!=='settings'"
        v-model:page="page"
        v-model:page-size="size"
        :total="total"
        :disabled="loading"
      /><div
        v-else
        class="store-settings-footer"
      >
        <span class="store-footer-hint">店铺信息与采集计划分别保存</span><div>
          <button
            :disabled="!writable||locked||JSON.stringify(draft)===baseline"
            form="store-basic-form"
            type="submit"
          >
            {{ busy?'保存中…':'保存店铺信息' }}
          </button><button
            class="mw-primary"
            :disabled="!writable||locked||!timing?.dirty"
            @click="timing?.save()"
          >
            {{ timing?.busy?'保存中…':'保存采集计划' }}
          </button>
        </div>
      </div>
    </footer>
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false"
      @discard="discard"
    />
  </dialog>
</template>
