<script setup lang="ts">
import { vSingleSelect } from '@/directives/singleSelect'
import MonitoringPencil from '@/components/MonitoringPencil.vue'
import { useAnchoredPopover } from '@/composables/useAnchoredPopover'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { apiRequest } from '@/api/client'
import { domainApi, type WatchRunMode } from '@/api/domain'
import type { WatchStoreDto, WatchEventDto } from '@/api/resources'
import { confirmAction } from '@/state/uiConfirmation'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'
import { useCollectionStatus } from '@/composables/useCollectionStatus'
import { isNovel } from '@/composables/watchNovelty'
import WatchStoreDetail from '@/components/WatchStoreDetail.vue'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
import '@/styles/monitoringWorkbench.css'
const formal = runtimeConfig.authMode === 'password'
const writable = computed(() => session.canAccess(['watch.write']))
const items = ref<WatchStoreDto[]>([]), events = ref<WatchEventDto[]>([]), selectedStore = ref<WatchStoreDto>()
const loading = ref(false), busyId = ref(''), error = ref(''), success = ref(''), selected = ref<string[]>([])
const assignableStores = computed(() => { const name=session.user.value?.displayName; return name ? items.value.filter(s=>selected.value.includes(s.id) && s.owner!==name) : [] })
const page = ref(1), pageSize = ref(20), query = ref(''), status = ref(''), owner = ref('')
const owners = computed(() => [...new Set([session.user.value?.displayName, ...items.value.map(s=>s.owner)].filter(Boolean))] as string[])
const filtered = computed(() => items.value.filter(s=>(!status.value||s.status===status.value)&&(!owner.value||s.owner===owner.value)&&[s.storeName,s.storeKey,s.owner,s.remark].join(' ').toLowerCase().includes(query.value.toLowerCase())))
type StoreSort = 'today' | 'createdAt'
const sortKey = ref<StoreSort>('createdAt'), sortDirection = ref<'ascending' | 'descending'>('descending')
const sorted = computed(() => {
 if (!sortKey.value) return filtered.value
 return [...filtered.value].sort((a,b) => {
  const left=sortKey.value==='today'?newCount(a.id):Date.parse(a.createdAt??'')
  const right=sortKey.value==='today'?newCount(b.id):Date.parse(b.createdAt??'')
  if(!Number.isFinite(left)||!Number.isFinite(right))return Number.isFinite(left)?-1:Number.isFinite(right)?1:a.id.localeCompare(b.id)
  return (left-right)*(sortDirection.value==='ascending'?1:-1)||a.id.localeCompare(b.id)
 })
})
function sortBy(key:StoreSort){sortDirection.value=sortKey.value===key&&sortDirection.value==='descending'?'ascending':'descending';sortKey.value=key;page.value=1;selected.value=[]}
function sortIcon(key:StoreSort){return sortKey.value===key?(sortDirection.value==='ascending'?'↑':'↓'):'↕'}
const visible = computed(() => sorted.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value))
const collection = useCollectionStatus('STORE', () => { if (!selectedStore.value) void load() }, (id, result) => {
 const name=items.value.find(item=>item.id===id)?.storeName??'店铺'
 if(result.status==='SUCCEEDED')success.value=`${name}：更新完成`
 else error.value=`${name}：${result.status==='PARTIAL'?'部分更新':result.status==='CANCELLED'?'更新已取消':'更新失败'}${result.reason?'，'+result.reason:''}`
})
const addDialog = ref<HTMLDialogElement>(), addOpen = ref(false), urls = ref(''), storeName = ref(''), addOwner = ref(''), saving = ref(false), addError = ref(''), unsaved = ref(false)
const addNotice=ref('')
const todayProducts=ref<WatchEventDto[]>()
const initialTab=ref('online')
const noveltyCollection=useCollectionStatus('WATCH_NOVELTY',()=>{void load()})
void noveltyCollection
const remarkDialog=ref<HTMLDialogElement>(), remarkStore=ref<WatchStoreDto>(), remarkDraft=ref(''), remarkError=ref(''), remarkBusy=ref(false), remarkUnsaved=ref(false)
const snapshots = ref<Record<string,string>>({})
watch([query,status,owner,pageSize],()=>{page.value=1;selected.value=[]})
function time(value?: string) { return value ? new Date(value).toLocaleString('zh-CN',{hour12:false}) : '—' }
const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}).format(new Date())
const todayEvents = computed(() => {
 const groups = new Map<string,WatchEventDto[]>(), formatter=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}), date=today()
 for(const event of events.value){if(event.storeId&&formatter.format(new Date(event.detectedAt))===date){const group=groups.get(event.storeId)??[];group.push(event);groups.set(event.storeId,group)}}
 return groups
})
function newProducts(id: string) { return todayEvents.value.get(id)??[] }
function newCount(id:string){return newProducts(id).length}
function showDetail(item:WatchStoreDto,todayOnly=false,novelty=false){initialTab.value=novelty?'novelty':todayOnly?'new':'online';todayProducts.value=todayOnly?newProducts(item.id):undefined;selectedStore.value=item}
function novelCount(id:string){return newProducts(id).filter(isNovel).length}
let remarkAnchor:HTMLElement|undefined
const {position:remarkPosition,reposition:positionRemark}=useAnchoredPopover(remarkDialog,()=>remarkAnchor)
async function showRemark(item:WatchStoreDto,event:Event){remarkAnchor=event.currentTarget as HTMLElement;remarkStore.value=item;remarkDraft.value=item.remark??'';remarkError.value='';await nextTick();remarkDialog.value?.showModal();positionRemark();remarkDialog.value?.querySelector('textarea')?.focus()}
function discardRemark(){remarkUnsaved.value=false;remarkDialog.value?.close();remarkStore.value=undefined;remarkAnchor?.focus()}
function closeRemark(){if(remarkBusy.value)return;if(remarkDraft.value!==(remarkStore.value?.remark??'')){remarkUnsaved.value=true;return}discardRemark()}
async function saveRemark(){if(!remarkStore.value)return;remarkBusy.value=true;remarkError.value='';try{await apiRequest(`/v1/watch/stores/${remarkStore.value.id}/remark`,{method:'PATCH',body:JSON.stringify({version:remarkStore.value.version,remark:remarkDraft.value})});discardRemark();success.value='备注已保存';await load()}catch(e){remarkError.value=e instanceof Error?e.message:'备注保存失败'}finally{remarkBusy.value=false}}
async function removeStore(item:WatchStoreDto){if(!await confirmAction(`移除店铺“${item.storeName}”？将停止监控并从列表移出，历史记录保留，重新添加同一地址可恢复。`))return;busyId.value=item.id;error.value='';try{await apiRequest(`/v1/watch/stores/${item.id}?version=${item.version}`,{method:'DELETE'});selected.value=selected.value.filter(id=>id!==item.id);success.value='店铺已移除';await load();page.value=Math.min(page.value,Math.max(1,Math.ceil(filtered.value.length/pageSize.value)))}catch(e){error.value=e instanceof Error?e.message:'移除失败'}finally{busyId.value=''}}
async function load() {
 loading.value=true
 try { const [stores,records]=await Promise.all([domainApi.watchStores(),formal?apiRequest<WatchEventDto[]>('/v1/watch/events'):Promise.resolve([])]);items.value=stores.items;events.value=records }
 catch(e){error.value=e instanceof Error?e.message:'店铺监控数据加载失败。'}finally{loading.value=false}
}
async function toggle(item: WatchStoreDto) {
 busyId.value=item.id;error.value=''
 try { await apiRequest(`/v1/watch/stores/${item.id}`,{method:'PUT',body:JSON.stringify({name:item.storeName,owner:item.owner??'',remark:item.remark??'',enabled:item.status!=='ACTIVE',version:item.version})}); if(item.status!=='ACTIVE')await collection.collect(item.id);await load() }
 catch(e){error.value=e instanceof Error?e.message:'更新失败，请刷新后重试'}finally{busyId.value=''}
}
async function showAdd() {success.value='';addNotice.value='';addError.value='';urls.value='';storeName.value='';addOwner.value=session.user.value?.displayName??'';addOpen.value=true;await nextTick();addDialog.value?.showModal()}
function closeAdd(){if(saving.value)return;if(urls.value.trim()||storeName.value.trim()){unsaved.value=true;return}discardAdd()}
function discardAdd(){unsaved.value=false;addOpen.value=false;addDialog.value?.close()}
onBeforeRouteLeave(()=>{if(remarkStore.value&&(remarkBusy.value||remarkDraft.value!==(remarkStore.value.remark??''))){remarkUnsaved.value=true;return false}if(addOpen.value&&(saving.value||urls.value.trim()||storeName.value.trim())){unsaved.value=true;return false}return true})
async function createStore(){
 if(saving.value)return
 const lines=[...new Set(urls.value.split(/\n/).map(v=>v.trim()).filter(Boolean))]
 if(!lines.length||lines.length>20){addError.value='每次请填写 1–20 个店铺地址，一行一个。';return}
 saving.value=true;addError.value='';addNotice.value='';success.value='';const failures:string[]=[],warnings:string[]=[],duplicates:string[]=[];let count=0
 for(const url of lines){try{
  // Compare seller identity, not the complete URL: /sp?seller= and /s?me= identify the same store.
  let existing:WatchStoreDto|undefined
  try{const address=new URL(url);if(address.protocol==='https:'&&['amazon.com','www.amazon.com'].includes(address.hostname)&&!address.username&&!address.password&&(!address.port||address.port==='443')){
   const seller=address.searchParams.get('seller')||address.searchParams.get('me')
   if(seller&&/^[A-Z0-9]{8,30}$/.test(seller))existing=items.value.find(item=>item.storeKey?.toUpperCase()===seller)
  }}catch{/* Invalid addresses retain the server validation message. */}
  if(existing){duplicates.push(`${existing.storeName}（${existing.storeKey}）`);continue}
  let store=await apiRequest<WatchStoreDto>('/v1/watch/stores/from-url',{method:'POST',body:JSON.stringify({url,name:lines.length===1?storeName.value.trim():''})})
  // Creation already schedules collection. Only an owner change needs a new version scheduled.
  if(!store.owner&&addOwner.value){
   try{
    store=await apiRequest<WatchStoreDto>(`/v1/watch/stores/${store.id}`,{method:'PUT',body:JSON.stringify({name:store.storeName,owner:addOwner.value,remark:store.remark??'',enabled:store.status==='ACTIVE',version:store.version})})
    if(store.status==='ACTIVE')await apiRequest(`/v1/watch/stores/${store.id}/collect`,{method:'POST'})
   }catch(e){warnings.push(`${store.storeName}已保存，${e instanceof Error?e.message:'监控人设置或采集排队失败'}，请在列表检查监控人并点击“更新所选”。`)}
  }
  items.value=[store,...items.value.filter(item=>item.id!==store.id)]
  count++
 }catch(e){failures.push(url);addError.value=e instanceof Error?e.message:'添加失败'}}
 saving.value=false
 const duplicateMessage=duplicates.length?`${duplicates.join('、')}已添加，无需重复添加。需要更新数据时，请在列表勾选后点击“更新所选”。`:''
 if(failures.length){urls.value=failures.join('\n');addError.value=`成功 ${count} 个，剩余 ${failures.length} 个未完成：${addError.value}`;addNotice.value=duplicateMessage}
 else if(!count&&duplicates.length){addNotice.value=duplicateMessage}
 else{discardAdd();success.value=`已保存 ${count} 个店铺，采集进度请查看列表状态。${duplicates.length?`已跳过 ${duplicates.length} 个已添加的店铺。`:''}`}
 if(warnings.length)error.value=warnings.join('；')
 // List/event/status reads must not hold the saved form open.
 if(count){void load();void collection.refresh()}
}
async function refreshSelected(){
 if(!writable.value||!selected.value.length||busyId.value)return
 busyId.value='refresh-selected';error.value='';success.value='';let count=0;let skipped=0;const failed:string[]=[]
 try{for(const item of items.value.filter(s=>selected.value.includes(s.id))){
  if(item.status!=='ACTIVE'||collection.pending(item.id)){skipped++;continue}
  if(await collection.collect(item.id))count++;else failed.push(item.storeName)
 }
 if(count)success.value=`已提交 ${count} 家店铺采集${skipped?`，跳过 ${skipped} 家暂停或采集中的店铺`:''}`
 if(failed.length)error.value=`以下店铺提交失败：${failed.join('、')}。请重试。`
 else if(!count)success.value='所选店铺已在采集中或已暂停，无需重复提交。'
 }finally{busyId.value=''}
}
async function assignOwner(){
 if(busyId.value || !writable.value || !assignableStores.value.length)return
 const targets=[...assignableStores.value]
 busyId.value='batch';error.value=''
 try{for(const item of targets)await apiRequest(`/v1/watch/stores/${item.id}`,{method:'PUT',body:JSON.stringify({name:item.storeName,owner:session.user.value?.displayName??'',remark:item.remark??'',enabled:item.status==='ACTIVE',version:item.version})});success.value='已设置为我负责';selected.value=[];await load()}catch(e){error.value=e instanceof Error?e.message:'设置失败'}finally{busyId.value=''}
}
async function run(item:WatchStoreDto,mode:WatchRunMode){busyId.value=item.id;try{const result=await domainApi.runWatchStore(item.id,mode,(snapshots.value[item.id]??'').split(/[\s,]+/).filter(Boolean));success.value=result.baselineChanged?'完整快照已提升为基线，本次没有新增事件。':'快照被安全拒绝，继续保留基线。';items.value=items.value.map(s=>s.id===item.id?result.item:s)}catch{error.value='快照运行失败'}finally{busyId.value=''}}
onMounted(load)
</script>
<template>
  <section class="page-stack monitor-workbench">
    <SyntheticNotice v-if="!formal" />
    <form
      class="panel mw-filters"
      @submit.prevent="page=1"
    >
      <label>站点<select
        v-single-select
        disabled
      ><option>美国</option></select></label>
      <label>监控人<select
        v-model="owner"
        v-single-select
      ><option value="">全部监控人</option><option
        v-for="name in owners"
        :key="name"
      >{{ name }}</option></select></label>
      <label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部状态</option><option value="ACTIVE">监控中</option><option value="DISABLED">已暂停</option></select></label>
      <label>店铺名称<input
        v-model="query"
        placeholder="店铺名称、标识或备注"
      ></label>
      <button
        type="button"
        class="filter-reset-button"
        @click="query='';owner='';status=''"
      >
        重置
      </button>
    </form>
    <SuccessToast :message="success" />
    <p
      v-if="error||collection.error.value"
      class="mw-error"
      role="alert"
    >
      {{ error||collection.error.value }}
    </p>
    <div class="panel mw-table-panel">
      <div class="mw-toolbar">
        <button
          v-if="writable&&formal"
          class="mw-primary"
          @click="showAdd"
        >
          ＋ 添加店铺
        </button>
        <button
          v-if="writable&&formal"
          :disabled="!assignableStores.length||!!busyId||loading"
          @click="assignOwner"
        >
          设为我负责
        </button>
        <button
          v-if="(formal) && session.canAccess(['watch.collect'])"
          :disabled="!writable||!selected.length||!!busyId"
          :class="{'mw-selected-update':selected.length>0}"
          aria-label="更新所选店铺"
          @click="refreshSelected"
        >
          更新所选<span v-if="selected.length">（{{ selected.length }}）</span>
        </button>
        <span class="mw-note">监控店铺 {{ items.filter(s=>s.status==='ACTIVE').length }} · 按计划更新</span>
      </div>
      <TableViewport>
        <table class="mw-table ui-action-table">
          <thead>
            <tr>
              <th>
                <input
                  type="checkbox"
                  aria-label="选择本页店铺"
                  :checked="!!visible.length&&visible.every(s=>selected.includes(s.id))"
                  @change="selected=($event.target as HTMLInputElement).checked?visible.map(s=>s.id):[]"
                >
              </th><th>监控</th><th>店铺名称</th><th>站点</th><th
                :aria-sort="sortKey==='today'?sortDirection:'none'"
                title="今日首次在该店铺监测到的商品，不代表实际首次上架日期"
              >
                <button
                  class="sort-button"
                  aria-label="按今日上架排序"
                  @click="sortBy('today')"
                >
                  今日上架 <span aria-hidden="true">{{ sortIcon('today') }}</span>
                </button> ⓘ
              </th><th title="今日首次发现且符合新品条件的商品；统一日期窗口60天，包含疑似新品和人工确认">
                今日新品 ⓘ
              </th><th>状态</th><th :aria-sort="sortKey==='createdAt'?sortDirection:'none'">
                <button
                  class="sort-button"
                  aria-label="按开始监控时间排序"
                  @click="sortBy('createdAt')"
                >
                  开始监控时间 <span aria-hidden="true">{{ sortIcon('createdAt') }}</span>
                </button>
              </th><th>更新时间</th><th>备注</th><th>监控人</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in visible"
              :key="item.id"
            >
              <td>
                <input
                  v-model="selected"
                  type="checkbox"
                  :value="item.id"
                  :aria-label="`选择 ${item.storeName}`"
                >
              </td>
              <td>
                <button
                  class="mw-switch"
                  role="switch"
                  :aria-checked="item.status==='ACTIVE'"
                  :aria-label="`监控 ${item.storeName}`"
                  :disabled="!writable||!!busyId||!formal"
                  @click="toggle(item)"
                />
              </td>
              <td>
                <a
                  v-if="item.storeKey"
                  class="mw-link"
                  :href="`https://www.amazon.com/s?me=${encodeURIComponent(item.storeKey)}&marketplaceID=ATVPDKIKX0DER`"
                  target="_blank"
                  rel="noopener noreferrer"
                  :aria-label="`${item.storeName}（打开亚马逊店铺）`"
                >
                  {{ item.storeName }}
                </a><span v-else>{{ item.storeName }}</span><small>{{ item.storeKey }}</small>
              </td>
              <td>美国</td><td>
                <button
                  class="mw-link mw-count-link"
                  :aria-label="`${item.storeName} 今日上架 ${newCount(item.id)}`"
                  @click="showDetail(item,true)"
                >
                  {{ newCount(item.id) }}
                </button>
              </td><td>
                <button
                  class="mw-link mw-count-link"
                  :aria-label="`${item.storeName} 今日新品 ${novelCount(item.id)}`"
                  @click="showDetail(item,true,true)"
                >
                  {{ novelCount(item.id) }}
                </button>
              </td>
              <td>
                <span
                  class="mw-status"
                  :class="{good:item.status==='ACTIVE',warn:['FAILED','PARTIAL'].includes(collection.state(item.id)?.status??'')}"
                  :title="collection.state(item.id)?.reason??''"
                >{{ item.status!=='ACTIVE'?'已暂停':collection.label(item.id) }}</span>
              </td>
              <td>{{ time(item.createdAt) }}</td><td>{{ time(item.lastRunAt) }}</td><td :title="item.remark">
                {{ item.remark ? item.remark.slice(0,30) : '—' }}<button
                  v-if="writable&&formal"
                  class="mw-link mw-remark-edit"
                  :aria-label="`编辑 ${item.storeName} 备注`"
                  title="编辑备注"
                  @click="showRemark(item,$event)"
                >
                  <MonitoringPencil />
                </button>
              </td><td>{{ item.owner||'未分配' }}</td>
              <td>
                <button
                  class="mw-link"
                  @click="showDetail(item)"
                >
                  详情
                </button><button
                  v-if="formal && session.canAccess(['watch.remove'])"
                  class="mw-link"
                  :disabled="!!busyId"
                  @click="removeStore(item)"
                >
                  移除
                </button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td
                colspan="12"
                class="mw-empty"
              >
                <strong>{{ loading?'正在加载店铺…':'暂无监控店铺' }}</strong>添加对标店铺，自动发现新商品。
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </div>
    <ListPagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="filtered.length"
      :disabled="loading"
    />
    <WatchStoreDetail
      v-if="formal"
      :store="selectedStore"
      :new-products="todayProducts"
      :initial-tab="initialTab"
      :writable="writable"
      @close="selectedStore=undefined"
      @changed="load"
    />
    <details
      v-if="!formal"
      class="panel"
    >
      <summary>合成快照演示</summary><div
        v-for="item in items"
        :key="item.id"
      >
        <strong>{{ item.storeName }}</strong><span>有效基线：{{ item.baselineRunId }}</span><button @click="run(item,'COMPLETE')">
          模拟完整快照
        </button><button @click="run(item,'INCOMPLETE')">
          模拟不完整快照
        </button><button @click="run(item,'FAILED')">
          模拟失败快照
        </button>
      </div>
    </details>
    <dialog
      ref="addDialog"
      class="mw-dialog"
      aria-label="添加店铺监控"
      @cancel.prevent="closeAdd"
    >
      <header>
        <h2>添加店铺监控</h2><button
          :disabled="saving"
          class="mw-link"
          aria-label="关闭添加店铺"
          @click="closeAdd"
        >
          ✕
        </button>
      </header>
      <form
        id="add-watch-store"
        class="mw-dialog-body"
        @submit.prevent="createStore"
      >
        <div class="mw-form-row">
          <label>站点<select
            v-single-select
            disabled
          ><option>美国</option></select></label><label>监控人<select
            v-model="addOwner"
            v-single-select
          ><option value="">未分配</option><option
            v-for="name in owners"
            :key="name"
          >{{ name }}</option></select></label>
        </div>
        <label>店铺地址 <textarea
          v-model="urls"
          required
          placeholder="添加多个店铺请换行，一行一个"
          :disabled="saving"
        /></label>
        <label>店铺名称（可选，留空自动获取）<input
          v-model="storeName"
          maxlength="200"
          :disabled="saving"
        ></label>
        <p>支持含 seller= 或 me= 的亚马逊美国站店铺地址。首次采集建立商品清单，后续新发现的商品进入上架记录。</p>
        <p
          v-if="addNotice"
          class="watch-add-notice"
          role="status"
        >
          {{ addNotice }}
        </p>
        <p
          v-if="addError"
          class="mw-error"
          role="alert"
        >
          {{ addError }}
        </p>
      </form>
      <footer class="ui-dialog-actions">
        <button
          :disabled="saving"
          @click="closeAdd"
        >
          取消
        </button><button
          class="mw-primary"
          type="submit"
          form="add-watch-store"
          :disabled="saving"
        >
          {{ saving?'正在添加…':'添加并开始监控' }}
        </button>
      </footer>
    </dialog>
    <dialog
      ref="remarkDialog"
      class="mw-dialog mw-remark-popover"
      :style="remarkPosition"
      aria-label="编辑店铺备注"
      @click.self="closeRemark"
      @cancel.prevent="closeRemark"
    >
      <header>
        <h2>修改备注</h2><button
          :disabled="remarkBusy"
          aria-label="关闭备注编辑"
          @click="closeRemark"
        >
          ✕
        </button>
      </header>
      <form
        id="watch-remark-form"
        class="mw-dialog-body"
        @submit.prevent="saveRemark"
      >
        <label class="mw-remark-label">备注<textarea
          v-model="remarkDraft"
          maxlength="1000"
          :disabled="remarkBusy"
          placeholder="填写店铺备注"
        /></label><p>{{ remarkDraft.length }} / 1000</p><p
          v-if="remarkError"
          class="mw-error"
          role="alert"
        >
          {{ remarkError }}
        </p>
      </form>
      <footer class="ui-dialog-actions">
        <button
          :disabled="remarkBusy"
          @click="closeRemark"
        >
          取消
        </button><button
          class="mw-primary"
          type="submit"
          form="watch-remark-form"
          :disabled="remarkBusy"
        >
          {{ remarkBusy?'保存中…':'保存' }}
        </button>
      </footer>
    </dialog>
    <UnsavedChangesDialog
      v-if="remarkUnsaved"
      @keep="remarkUnsaved=false"
      @discard="discardRemark"
    />
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false"
      @discard="discardAdd"
    />
  </section>
</template>

<style scoped>
.watch-add-notice { padding: 10px 12px; border: 1px solid #bfd4f3; border-radius: 6px; background: #f0f6ff; color: #24549a; line-height: 1.6; overflow-wrap: anywhere; }
</style>
