<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave } from 'vue-router'
import { apiRequest } from '@/api/client'
import { domainApi, type WatchRunMode } from '@/api/domain'
import type { WatchStoreDto, WatchEventDto } from '@/api/resources'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'
import { useCollectionStatus } from '@/composables/useCollectionStatus'
import WatchStoreDetail from '@/components/WatchStoreDetail.vue'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
import CollectionPanel from '@/components/CollectionPanel.vue'
import '@/styles/monitoringWorkbench.css'
const formal = runtimeConfig.authMode === 'password'
const writable = computed(() => session.canAccess(['watch.write']))
const items = ref<WatchStoreDto[]>([]), events = ref<WatchEventDto[]>([]), selectedStore = ref<WatchStoreDto>()
const loading = ref(false), busyId = ref(''), error = ref(''), success = ref(''), selected = ref<string[]>([])
const page = ref(1), pageSize = ref(20), query = ref(''), status = ref(''), owner = ref('')
const owners = computed(() => [...new Set([session.user.value?.displayName, ...items.value.map(s=>s.owner)].filter(Boolean))] as string[])
const filtered = computed(() => items.value.filter(s=>(!status.value||s.status===status.value)&&(!owner.value||s.owner===owner.value)&&[s.storeName,s.storeKey,s.owner,s.remark].join(' ').toLowerCase().includes(query.value.toLowerCase())))
const visible = computed(() => filtered.value.slice((page.value-1)*pageSize.value,page.value*pageSize.value))
const collection = useCollectionStatus('STORE', () => { if (!selectedStore.value) void load() })
const addDialog = ref<HTMLDialogElement>(), addOpen = ref(false), urls = ref(''), storeName = ref(''), addOwner = ref(''), saving = ref(false), addError = ref(''), unsaved = ref(false)
const snapshots = ref<Record<string,string>>({})
watch([query,status,owner,pageSize],()=>{page.value=1;selected.value=[]})
function time(value?: string) { return value ? new Date(value).toLocaleString('zh-CN',{hour12:false}) : '—' }
const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}).format(new Date())
function newCount(id: string) { return events.value.filter(e=>e.storeId===id&&new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}).format(new Date(e.detectedAt))===today()).length }
async function load() {
 loading.value=true
 try { items.value=(await domainApi.watchStores()).items; if(formal) events.value=await apiRequest<WatchEventDto[]>('/v1/watch/events') }
 catch(e){error.value=e instanceof Error?e.message:'店铺监控数据加载失败。'}finally{loading.value=false}
}
async function toggle(item: WatchStoreDto) {
 busyId.value=item.id;error.value=''
 try { await apiRequest(`/v1/watch/stores/${item.id}`,{method:'PUT',body:JSON.stringify({name:item.storeName,owner:item.owner??'',remark:item.remark??'',enabled:item.status!=='ACTIVE',version:item.version})}); if(item.status!=='ACTIVE')await collection.collect(item.id);await load() }
 catch(e){error.value=e instanceof Error?e.message:'更新失败，请刷新后重试'}finally{busyId.value=''}
}
async function showAdd() {addError.value='';urls.value='';storeName.value='';addOwner.value=session.user.value?.displayName??'';addOpen.value=true;await nextTick();addDialog.value?.showModal()}
function closeAdd(){if(saving.value)return;if(urls.value.trim()||storeName.value.trim()){unsaved.value=true;return}discardAdd()}
function discardAdd(){unsaved.value=false;addOpen.value=false;addDialog.value?.close()}
onBeforeRouteLeave(()=>{if(addOpen.value&&(saving.value||urls.value.trim()||storeName.value.trim())){unsaved.value=true;return false}return true})
async function createStore(){
 const lines=[...new Set(urls.value.split(/\n/).map(v=>v.trim()).filter(Boolean))]
 if(!lines.length||lines.length>20){addError.value='每次请填写 1–20 个店铺地址，一行一个。';return}
 saving.value=true;addError.value='';const failures:string[]=[];let count=0
 for(const url of lines){try{
  const store=await apiRequest<WatchStoreDto>('/v1/watch/stores/from-url',{method:'POST',body:JSON.stringify({url,name:lines.length===1?storeName.value.trim():''})})
  if(!store.owner&&addOwner.value)await apiRequest(`/v1/watch/stores/${store.id}`,{method:'PUT',body:JSON.stringify({name:store.storeName,owner:addOwner.value,remark:store.remark??'',enabled:store.status==='ACTIVE',version:store.version})})
  // Owner edit invalidates an in-flight version; explicitly schedule the current version.
  if(store.status==='ACTIVE' && !await collection.collect(store.id))throw new Error('店铺已保存，但自动采集排队失败，请重试。')
  count++
 }catch(e){failures.push(url);addError.value=e instanceof Error?e.message:'添加失败'}}
 saving.value=false;await load();void collection.refresh()
 if(failures.length){urls.value=failures.join('\n');addError.value=`成功 ${count} 个，剩余 ${failures.length} 个未完成：${addError.value}`}else{discardAdd();success.value=`已添加 ${count} 个店铺，正在自动采集。`}
}
async function assignOwner(){
 busyId.value='batch';error.value=''
 try{for(const item of items.value.filter(s=>selected.value.includes(s.id)))await apiRequest(`/v1/watch/stores/${item.id}`,{method:'PUT',body:JSON.stringify({name:item.storeName,owner:session.user.value?.displayName??'',remark:item.remark??'',enabled:item.status==='ACTIVE',version:item.version})});success.value='已设置为我负责';selected.value=[];await load()}catch(e){error.value=e instanceof Error?e.message:'设置失败'}finally{busyId.value=''}
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
      <label>站点<select disabled><option>美国</option></select></label>
      <label>监控人<select v-model="owner"><option value="">全部监控人</option><option
        v-for="name in owners"
        :key="name"
      >{{ name }}</option></select></label>
      <label>状态<select v-model="status"><option value="">全部状态</option><option value="ACTIVE">监控中</option><option value="DISABLED">已暂停</option></select></label>
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
          :disabled="!selected.length||!!busyId"
          @click="assignOwner"
        >
          设为我负责
        </button>
        <RouterLink
          to="/watch/events"
          class="mw-link"
        >
          上新记录
        </RouterLink>
        <span class="mw-note">监控店铺 {{ items.filter(s=>s.status==='ACTIVE').length }} · 每日更新</span>
        <button
          :disabled="loading"
          aria-label="刷新店铺列表"
          @click="load();collection.refresh()"
        >
          刷新
        </button>
        <details>
          <summary>更多</summary><CollectionPanel
            v-if="formal"
            kind="STORE"
            :targets="items.map(s=>({id:s.id,label:s.storeName}))"
          />
        </details>
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
              </th><th>监控</th><th>店铺名称</th><th>站点</th><th>今日上新</th><th>状态</th><th>开始监控时间</th><th>更新时间</th><th>备注</th><th>监控人</th><th>操作</th>
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
                <button
                  class="mw-link"
                  @click="selectedStore=item"
                >
                  {{ item.storeName }}
                </button><small>{{ item.storeKey }}</small>
              </td>
              <td>美国</td><td>
                <RouterLink
                  class="mw-link"
                  :to="`/watch/events?storeId=${item.id}`"
                >
                  {{ newCount(item.id) }}
                </RouterLink>
              </td>
              <td>
                <span
                  class="mw-status"
                  :class="{good:item.status==='ACTIVE',warn:['FAILED','PARTIAL'].includes(collection.state(item.id)?.status??'')}"
                  :title="collection.state(item.id)?.reason??''"
                >{{ item.status!=='ACTIVE'?'已暂停':collection.label(item.id) }}</span>
              </td>
              <td>{{ time(item.createdAt) }}</td><td>{{ time(item.lastRunAt) }}</td><td :title="item.remark">
                {{ item.remark ? item.remark.slice(0,30) : '—' }}
              </td><td>{{ item.owner||'未分配' }}</td>
              <td>
                <button
                  class="mw-link"
                  @click="selectedStore=item"
                >
                  详情
                </button><button
                  v-if="writable&&formal"
                  class="mw-link"
                  :disabled="item.status!=='ACTIVE'||collection.pending(item.id)||!collection.enabled.value"
                  @click="collection.collect(item.id)"
                >
                  更新
                </button>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td
                colspan="11"
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
          <label>站点<select disabled><option>美国</option></select></label><label>监控人<select v-model="addOwner"><option value="">未分配</option><option
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
        <label>店铺名称（单个店铺可填）<input
          v-model="storeName"
          maxlength="200"
          :disabled="saving"
        ></label>
        <p>支持含 seller= 或 me= 的亚马逊美国站店铺地址。首次采集建立商品清单，后续新发现的商品进入上新记录。</p>
        <p
          v-if="addError"
          class="mw-error"
          role="alert"
        >
          {{ addError }}
        </p>
      </form>
      <footer>
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
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false"
      @discard="discardAdd"
    />
  </section>
</template>
