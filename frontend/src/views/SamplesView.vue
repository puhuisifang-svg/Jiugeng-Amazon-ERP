<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { samplesApi, emptySampleFilters, type SampleLocation, type SampleInput, type SampleFilters } from '@/api/samples'
import { ApiError } from '@/api/client'
import { catalogApi } from '@/api/catalog'
import type { SkuListItemDto } from '@/api/domain'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import { runtimeConfig } from '@/config/runtime'
import { beijingDateTime } from '@/utils/dateTime'
import { sampleSpecification } from '@/utils/sampleSpecification'
import { sampleLinkedStatus } from '@/utils/sampleHistory'
import { vSingleSelect } from '@/directives/singleSelect'
import SampleLabelDialog from '@/components/SampleLabelDialog.vue'
import SampleDetailDialog from '@/components/SampleDetailDialog.vue'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'
import SampleImage from '@/components/SampleImage.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import CustomerSkuPicker from '@/components/CustomerSkuPicker.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import '@/styles/sampleManagement.css'
const backendReady=ref(false),compatError=ref('')
async function checkBackend(){try{backendReady.value=(await samplesApi.capabilities()).closeoutVersion>=1;compatError.value=backendReady.value?'':'本地后端尚未同步样品新版本，暂仅提供原有查询和打印。'}catch{compatError.value='样品新接口尚不可用，新增、编辑、移除及批量功能暂不可用，请同步后端后重试。'}}
const writable=computed(()=>runtimeConfig.authMode==='password'&&session.canAccess(['samples.write']))
const removable=computed(()=>runtimeConfig.authMode==='password'&&session.canAccess(['samples.remove']))
const rows=ref<SampleLocation[]>([]),query=ref(''),applied=ref(''),page=ref(1),size=ref(20),total=ref(0),loading=ref(false),error=ref(''),actionError=ref(''),success=ref('')
const parents=ref<{id:string;name:string}[]>([]),parent=ref(''),appliedParent=ref(''),parentError=ref('')
const filters=reactive(emptySampleFilters()),appliedFilters=ref(emptySampleFilters()),advanced=ref(false),listHost=ref<HTMLElement>()
const selectedIds=ref<string[]>([]),detailId=ref(''),printSample=ref<SampleLocation>(),printRows=ref<SampleLocation[]>(),exportOpen=ref(false),exportSelection=ref(true),exporting=ref(false)
const dialog=ref<HTMLDialogElement>(),locationInput=ref<HTMLInputElement>(),open=ref(false),busy=ref(false),editing=ref<SampleLocation>(),copying=ref<SampleLocation>(),location=ref(''),selected=ref<SkuListItemDto>(),initial=ref(''),formError=ref(''),discard=ref(false)
const skuPickerOpen=ref(false),manual=ref(false),sampleName=ref(''),sampleCode=ref(''),specification=ref(''),remark=ref(''),sampleImageId=ref<string|null>(null),storageImageId=ref<string|null>(null),uploading=ref(false)
const editorRevision=ref(0)
const locationOptions=ref<string[]>([]),locationError=ref(''),conflictRow=ref<SampleLocation>(),duplicateRow=ref<SampleLocation>(),checkingConflict=ref(false)
interface Pending { key:string;input:SampleInput;editing?:SampleLocation|undefined;copying?:SampleLocation|undefined;selected?:SkuListItemDto|undefined;again:boolean }
const owner=session.user?.value?.id??'current',pendingKey=`jiugeng.samples.pending.${owner}`,stateKey=`jiugeng.samples.view.${owner}`
const pending=ref<Pending>()
try{const p=JSON.parse(sessionStorage.getItem(pendingKey)??'null') as Pending|null;if(p&&typeof p.key==='string'&&p.input&&typeof p.input.locationCode==='string')pending.value=p}catch{/* Optional recovery storage unavailable. */}
const working=computed(()=>busy.value||uploading.value),blocked=computed(()=>working.value||!!pending.value||!backendReady.value),hasFilters=computed(()=>!!(applied.value||appliedParent.value||appliedFilters.value.source||appliedFilters.value.photo||appliedFilters.value.location))
const allSelected=computed(()=>rows.value.length>0&&rows.value.every(r=>selectedIds.value.includes(r.id))),someSelected=computed(()=>rows.value.some(r=>selectedIds.value.includes(r.id))&&!allSelected.value)
const chips=computed(()=>[
 {key:'query',label:'关键词',value:applied.value},{key:'parent',label:'父体',value:parents.value.find(p=>p.id===appliedParent.value)?.name??(appliedParent.value?'已选父体':'')},
 {key:'source',label:'来源',value:({LINKED:'关联 SKU',MANUAL:'手动录入'} as Record<string,string>)[appliedFilters.value.source]??''},
 {key:'photo',label:'存放照片',value:({YES:'有照片',NO:'未上传'} as Record<string,string>)[appliedFilters.value.photo]??''},
 {key:'location',label:appliedFilters.value.locationMode==='exact'?'库位精确':'库位前缀',value:appliedFilters.value.location},
].filter(x=>x.value))
let listSequence=0,locationsSequence=0,scrollTop=0,locationsTimer:ReturnType<typeof setTimeout>|undefined
const snapshot=()=>JSON.stringify([selected.value?.variant.id??'',location.value,manual.value,sampleName.value,sampleCode.value,specification.value,remark.value,storageImageId.value,sampleImageId.value])
const dirty=computed(()=>open.value&&snapshot()!==initial.value)
function persistPending(){try{if(pending.value)sessionStorage.setItem(pendingKey,JSON.stringify(pending.value));else sessionStorage.removeItem(pendingKey)}catch{/* In-memory original request remains locked and retryable. */}}
function saveView(){scrollTop=listHost.value?.querySelector('.table-viewport')?.scrollTop??scrollTop;try{sessionStorage.setItem(stateKey,JSON.stringify({q:applied.value,parent:appliedParent.value,filters:appliedFilters.value,page:page.value,size:size.value,scrollTop}))}catch{/* Optional view preferences. */}}
async function loadParents(){try{parents.value=await samplesApi.parents();parentError.value=''}catch{parentError.value='父体选项加载失败'}}
async function load(){
 if(runtimeConfig.authMode!=='password'){error.value='样品库位管理仅在正式登录模式使用';return}
 const seq=++listSequence;loading.value=true;error.value=''
 try{const result=await samplesApi.list(applied.value,page.value-1,size.value,appliedParent.value,backendReady.value?appliedFilters.value:{});if(seq!==listSequence)return;rows.value=result.items;total.value=result.totalCount
  const last=Math.max(1,Math.ceil(total.value/size.value));if(page.value>last){page.value=last;await load();return}
  await nextTick();const host=listHost.value?.querySelector('.table-viewport');if(host)host.scrollTop=scrollTop
 }catch(e){if(seq===listSequence)error.value=message(e)}finally{if(seq===listSequence)loading.value=false}
}
function search(){applied.value=query.value.trim();appliedParent.value=parent.value;appliedFilters.value={...filters};page.value=1;scrollTop=0;selectedIds.value=[];const host=listHost.value?.querySelector('.table-viewport');if(host)host.scrollTop=0;saveView();void load()}
function reset(){query.value='';parent.value='';Object.assign(filters,emptySampleFilters(),{deleted:appliedFilters.value.deleted});search()}
function removeChip(key:string){query.value=applied.value;parent.value=appliedParent.value;Object.assign(filters,appliedFilters.value);if(key==='query')query.value='';else if(key==='parent')parent.value='';else if(key==='source'||key==='photo'||key==='location')filters[key]='';search()}
function toggleDeleted(){filters.deleted=!appliedFilters.value.deleted;search()}
function changePage(value:number){page.value=value;scrollTop=0;void load()}
function toggle(id:string,value:boolean){if(value&&!selectedIds.value.includes(id)){if(selectedIds.value.length>=500){actionError.value='一次最多选择 500 条';return}selectedIds.value.push(id)}else if(!value)selectedIds.value=selectedIds.value.filter(v=>v!==id)}
function togglePage(value:boolean){for(const r of rows.value)toggle(r.id,value)}
async function loadLocations(){const seq=++locationsSequence;try{const values=await samplesApi.locations(location.value);if(seq===locationsSequence){locationOptions.value=values;locationError.value=''}}catch{if(seq===locationsSequence)locationError.value='库位建议加载失败，可继续手动填写'}}
watch(location,()=>{if(!open.value)return;clearTimeout(locationsTimer);locationsTimer=setTimeout(()=>void loadLocations(),250)})
async function uploadStorage(file:File){uploading.value=true;formError.value='';try{storageImageId.value=(await catalogApi.uploadImage('SAMPLE_STORAGE',file)).id}catch(e){formError.value=message(e);throw e}finally{uploading.value=false}}
async function uploadSample(file:File){uploading.value=true;formError.value='';try{sampleImageId.value=(await catalogApi.uploadImage('SAMPLE_IMAGE',file)).id}catch(e){formError.value=message(e);throw e}finally{uploading.value=false}}
async function edit(row?:SampleLocation,copy=false){
 if(blocked.value||!writable.value)return
 editorRevision.value++;editing.value=copy?undefined:row;copying.value=copy?row:undefined;manual.value=!!row&&!row.variantId;sampleName.value=row?.skuName??'';sampleCode.value=row?.skuCode??'';specification.value=row?.specification??'';remark.value=row?.remark??'';storageImageId.value=copy?null:row?.storageImageId??null;sampleImageId.value=copy?null:row?.sampleImageId??null;location.value=copy?'':row?.locationCode??'';selected.value=undefined;formError.value='';conflictRow.value=undefined;duplicateRow.value=undefined;skuPickerOpen.value=false;open.value=true;initial.value=snapshot();await nextTick();dialog.value?.showModal();locationInput.value?.focus();void loadLocations()
}
function chooseSku(sku:SkuListItemDto){if(selected.value?.variant.id!==sku.variant.id)specification.value=sampleSpecification(sku.variant);selected.value=sku;skuPickerOpen.value=false}
function preventEnterSave(event:KeyboardEvent){if(event.key==='Enter'&&event.target instanceof HTMLInputElement&&!event.isComposing)event.preventDefault()}
function close(){skuPickerOpen.value=false;dialog.value?.close();open.value=false;discard.value=false}
function requestClose(){if(working.value)return;if(pending.value){close();return}if(dirty.value){discard.value=true;return}close()}
function input():SampleInput{
 const original=editing.value??copying.value
 return {productId:manual.value?null:original?.productId??selected.value?.productId??null,variantId:manual.value?null:original?.variantId??selected.value?.variant.id??null,locationCode:location.value,sampleName:sampleName.value,sampleCode:sampleCode.value,specification:specification.value,remark:remark.value,storageImageId:storageImageId.value,sampleImageId:manual.value?sampleImageId.value:null,...(editing.value?{version:editing.value.version}:{}),...(copying.value?{copyFromId:copying.value.id}:{})}
}
async function save(again=false){
 if(working.value||!writable.value||!backendReady.value)return
 if(!pending.value){const payload=input();if(!manual.value&&(!payload.productId||!payload.variantId)){formError.value='请先选择 SKU';return}if(manual.value&&!sampleName.value.trim()){formError.value='请输入样品名称';return}if(!location.value.trim()){formError.value='请输入库位编码';return}
 pending.value={key:crypto.randomUUID(),input:payload,editing:editing.value,copying:copying.value,selected:selected.value,again};persistPending()}
 const command=pending.value;busy.value=true;formError.value=''
 try{
  const result=command.editing?await samplesApi.update(command.editing.id,command.input,command.key):await samplesApi.create(command.input,command.key)
  if(!result || typeof result.id!=='string' || !Number.isFinite(result.version))throw new Error('保存响应不完整，需确认原请求')
  pending.value=undefined;persistPending();success.value=result.deletedAt?'原请求已确认，该登记随后已移除，请在已移除列表查看。':'样品库位已保存'
  if(command.again&&!result.deletedAt){editorRevision.value++;editing.value=undefined;copying.value=result;manual.value=!result.variantId;sampleName.value=result.skuName;sampleCode.value=result.skuCode;location.value='';storageImageId.value=null;sampleImageId.value=null;initial.value=snapshot();await nextTick();locationInput.value?.focus()}else close()
  await Promise.all([load(),loadParents()])
 }catch(e){
  if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;persistPending();formError.value=message(e)
   if(e.status===409&&command.input.variantId){try{duplicateRow.value=(await samplesApi.duplicates(command.input.variantId,command.input.locationCode)).find(r=>r.id!==command.editing?.id)}catch{/* Original error remains visible. */}}
  }else formError.value=`保存结果待确认：${message(e)}。输入已锁定，请确认原请求；不会重复新增。`
 }finally{busy.value=false}
}
async function resumePending(){const p=pending.value;if(!p)return;editing.value=p.editing;copying.value=p.copying;selected.value=p.selected;manual.value=!p.input.variantId;sampleName.value=p.input.sampleName??'';sampleCode.value=p.input.sampleCode??'';location.value=p.input.locationCode;specification.value=p.input.specification??'';remark.value=p.input.remark??'';storageImageId.value=p.input.storageImageId??null;sampleImageId.value=p.input.sampleImageId??null;open.value=true;initial.value=snapshot();formError.value='上次保存结果待确认，请使用原请求重试。';await nextTick();dialog.value?.showModal()}
async function checkConflict(){if(!editing.value||checkingConflict.value)return;checkingConflict.value=true;try{conflictRow.value=await samplesApi.detail(editing.value.id)}catch(e){formError.value=message(e)}finally{checkingConflict.value=false}}
function useLatest(){const r=conflictRow.value;if(!r||r.deletedAt)return;editing.value=r;sampleName.value=r.skuName;sampleCode.value=r.skuCode;location.value=r.locationCode;specification.value=r.specification??'';remark.value=r.remark??'';storageImageId.value=r.storageImageId??null;sampleImageId.value=r.sampleImageId??null;conflictRow.value=undefined;initial.value=snapshot();formError.value='已采用最新资料，请核对后保存。'}
function acceptVersion(){if(!conflictRow.value||conflictRow.value.deletedAt)return;editing.value=conflictRow.value;conflictRow.value=undefined;formError.value='已保留草稿并采用最新版本，请核对后保存。'}
const comparison=computed(()=>{if(!conflictRow.value)return [];const latest=conflictRow.value;return [{label:'样品名称',saved:latest.skuName,draft:manual.value?sampleName.value:latest.skuName},{label:'库位',saved:latest.locationCode,draft:location.value},{label:'规格',saved:latest.specification??'',draft:specification.value},{label:'备注',saved:latest.remark??'',draft:remark.value},{label:'存放照片',saved:latest.storageImageId,draft:storageImageId.value},{label:'样品图片',saved:latest.sampleImageId,draft:manual.value?sampleImageId.value:latest.sampleImageId}].filter(v=>v.saved!==v.draft)})
async function remove(row:SampleLocation){if(blocked.value||!removable.value||!await confirmAction(`移除“${row.skuName}”在 ${row.locationCode} 的登记？可在“已移除”中恢复，产品和 SKU 不受影响。`))return;busy.value=true;actionError.value='';try{await samplesApi.remove(row.id,row.version);selectedIds.value=selectedIds.value.filter(id=>id!==row.id);success.value='登记已移除，可恢复';await Promise.all([load(),loadParents()])}catch(e){actionError.value=message(e)}finally{busy.value=false}}
async function restore(row:SampleLocation){if(blocked.value||!writable.value||!removable.value||!await confirmAction(`恢复“${row.skuName}”到 ${row.locationCode}？同一 SKU 在该库位已有登记时无法恢复。`))return;busy.value=true;actionError.value='';try{await samplesApi.restore(row.id,row.version);success.value='样品登记已恢复';await Promise.all([load(),loadParents()])}catch(e){actionError.value=message(e)}finally{busy.value=false}}
async function batchPrint(){if(!selectedIds.value.length||blocked.value)return;if(selectedIds.value.length>100){actionError.value='一次最多打印 100 条样品，请减少勾选';return}busy.value=true;actionError.value='';try{printRows.value=await samplesApi.selection(selectedIds.value)}catch(e){actionError.value=message(e)}finally{busy.value=false}}
async function exportRows(){exporting.value=true;actionError.value='';try{const blob=await samplesApi.export(applied.value,appliedParent.value,appliedFilters.value,exportSelection.value?selectedIds.value:[]);const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='样品盘点清单.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);exportOpen.value=false;success.value='盘点清单已导出'}catch(e){actionError.value=message(e)}finally{exporting.value=false}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value||working.value||pending.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(async()=>{saveView();return working.value?false:(!dirty.value&&!pending.value)||await confirmAction(pending.value?'保存结果待确认，原请求将保留，可返回确认。确定离开吗？':'样品尚未保存，确定放弃并离开吗？')})
onMounted(async()=>{try{const saved=JSON.parse(sessionStorage.getItem(stateKey)??'null');if(saved){query.value=applied.value=typeof saved.q==='string'?saved.q:'';parent.value=appliedParent.value=typeof saved.parent==='string'?saved.parent:'';for(const key of Object.keys(filters) as (keyof SampleFilters)[])if(typeof saved.filters?.[key]===typeof filters[key])Object.assign(filters,{[key]:saved.filters[key]});appliedFilters.value={...filters};page.value=Math.max(1,Number(saved.page)||1);size.value=[10,20,50,100].includes(saved.size)?saved.size:20;scrollTop=Math.max(0,Number(saved.scrollTop)||0)}}catch{/* Use defaults. */}await checkBackend();if(!backendReady.value){appliedFilters.value=emptySampleFilters();Object.assign(filters,emptySampleFilters())}void load();void loadParents();window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{saveView();++listSequence;++locationsSequence;clearTimeout(locationsTimer);window.removeEventListener('beforeunload',beforeUnload);dialog.value?.close()})
</script>
<template>
  <section
    ref="listHost"
    class="page-stack samples-ui"
  >
    <SuccessToast :message="success" /><p
      v-if="compatError"
      role="alert"
      class="sample-notice"
    >
      {{ compatError }} <button
        type="button"
        @click="checkBackend().then(load)"
      >
        重试连接
      </button>
    </p>
    <form
      class="filter-panel ui-filter-row"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="query"
        maxlength="200"
        placeholder="品名、规格、备注或库位"
      ></label>
      <label>所属父体<select
        v-model="parent"
        v-single-select
      ><option value="">全部父体</option><option
        v-for="p in parents"
        :key="p.id"
        :value="p.id"
      >{{ p.name }}</option></select></label>
      <button
        type="button"
        class="text-action"
        :aria-expanded="advanced"
        :disabled="!backendReady"
        @click="advanced=!advanced"
      >
        更多筛选{{ chips.filter(c=>!['query','parent'].includes(c.key)).length?`（${chips.filter(c=>!['query','parent'].includes(c.key)).length}）`:'' }}
      </button>
      <template v-if="advanced">
        <label>来源<select
          v-model="filters.source"
          v-single-select
        ><option value="">全部来源</option><option value="LINKED">关联 SKU</option><option value="MANUAL">手动录入</option></select></label><label>存放照片<select
          v-model="filters.photo"
          v-single-select
        ><option value="">全部</option><option value="YES">有照片</option><option value="NO">未上传</option></select></label><label>库位<input
          v-model="filters.location"
          maxlength="100"
          placeholder="例如 A-01"
        ></label><label>匹配<select
          v-model="filters.locationMode"
          v-single-select
        ><option value="prefix">前缀</option><option value="exact">精确</option></select></label>
      </template>
      <button
        type="submit"
        :disabled="loading || busy"
      >
        搜索
      </button><button
        type="button"
        class="filter-reset-button"
        :disabled="loading || busy"
        @click="reset"
      >
        重置
      </button>
      <div
        v-if="chips.length"
        class="sample-filter-chips"
      >
        <button
          v-for="chip in chips"
          :key="chip.key"
          type="button"
          :aria-label="`移除${chip.label}筛选`"
          @click="removeChip(chip.key)"
        >
          {{ chip.label }}：{{ chip.value }} ×
        </button>
      </div>
    </form>
    <p
      v-if="parentError"
      role="alert"
      class="sample-notice access-danger"
    >
      {{ parentError }} <button
        type="button"
        @click="loadParents"
      >
        重试
      </button>
    </p>
    <div class="material-toolbar sample-toolbar">
      <button
        v-if="writable && !appliedFilters.deleted"
        type="button"
        class="primary-action"
        :disabled="blocked"
        @click="edit()"
      >
        新增样品
      </button>
      <template v-if="!appliedFilters.deleted">
        <button
          type="button"
          :disabled="blocked || !selectedIds.length"
          @click="batchPrint"
        >
          批量标签<span v-if="selectedIds.length">（{{ selectedIds.length }}）</span>
        </button><button
          type="button"
          :disabled="blocked || loading || !!error"
          @click="exportSelection=!!selectedIds.length;exportOpen=true"
        >
          导出盘点清单
        </button>
      </template>
      <button
        type="button"
        :disabled="blocked"
        :class="{active:appliedFilters.deleted}"
        @click="toggleDeleted"
      >
        {{ appliedFilters.deleted?'返回在库登记':'已移除' }}
      </button><button
        type="button"
        :disabled="loading || busy"
        @click="load"
      >
        刷新
      </button>
      <span
        v-if="selectedIds.length"
        class="sample-hint"
      >跨页已选 {{ selectedIds.length }} 条 <button
        type="button"
        class="text-action"
        @click="selectedIds=[]"
      >清空</button></span>
      <span
        v-else
        class="sample-toolbar-hint"
      >{{ appliedFilters.deleted?'已移除记录保留照片和历史；恢复需要维护及移除权限。':'登记样品与库位，照片与产品资料分别留存' }}</span>
    </div>
    <p
      v-if="pending && !open"
      role="alert"
      class="sample-notice"
    >
      有一笔保存结果待确认。<button
        type="button"
        @click="resumePending"
      >
        继续确认原请求
      </button>
    </p>
    <p
      v-if="actionError"
      role="alert"
      class="sample-notice access-danger"
    >
      {{ actionError }}
    </p>
    <p
      v-if="loading"
      role="status"
      class="sample-notice"
    >
      {{ rows.length?'正在刷新，暂时显示上次结果…':'正在加载样品…' }}
    </p>
    <p
      v-if="error"
      role="alert"
      class="sample-notice access-danger"
    >
      {{ rows.length?'刷新失败，下方为上次结果。':'加载失败。' }}{{ error }} <button
        type="button"
        @click="load"
      >
        重试
      </button>
    </p>
    <TableViewport v-if="rows.length || (!loading && !error)">
      <table
        class="data-table ui-action-table sample-table"
        :aria-busy="loading"
      >
        <thead>
          <tr>
            <th
              v-if="!appliedFilters.deleted"
              class="sample-check"
            >
              <input
                type="checkbox"
                aria-label="全选本页样品"
                :checked="allSelected"
                :indeterminate="someSelected"
                :disabled="loading || !!error"
                @change="togglePage(($event.target as HTMLInputElement).checked)"
              >
            </th><th>图片</th><th>品名 / 规格</th><th>来源 / 所属产品</th><th>库位</th><th>存放照片</th><th>备注</th><th>更新时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
            :class="{'sample-row-selected':selectedIds.includes(row.id)}"
          >
            <td v-if="!appliedFilters.deleted">
              <input
                type="checkbox"
                :aria-label="`选择样品 ${row.skuName} ${row.locationCode}`"
                :checked="selectedIds.includes(row.id)"
                :disabled="loading || !!error"
                @change="toggle(row.id,($event.target as HTMLInputElement).checked)"
              >
            </td>
            <td>
              <ShipmentProductImage
                v-if="!backendReady && row.productId && row.variantId"
                :product-id="row.productId"
                :variant-id="row.variantId"
                :alt="row.skuName"
              /><SampleImage
                v-else
                :image-id="row.displayImageId ?? row.sampleImageId"
                :image-url="row.displayImageUrl"
                :alt="row.skuName"
              />
            </td>
            <td class="sample-name">
              <button
                type="button"
                class="sample-name-link"
                :disabled="!backendReady"
                @click="saveView();detailId=row.id"
              >
                {{ row.skuName }}
              </button><small class="sample-spec">{{ row.specification || '规格未填写' }}</small><small
                v-if="row.imageSource==='CURRENT_PRODUCT'"
                class="sample-hint"
              >当前产品图 · 历史未留图</small>
            </td>
            <td>
              <span class="sample-source-tag">{{ row.variantId?'关联 SKU':'手动录入' }}</span><small>{{ row.productName || '独立样品' }}</small><small
                v-if="row.variantId && row.linkedStatus!=='ACTIVE'"
                class="sample-warning"
              >{{ sampleLinkedStatus(row.linkedStatus) }}</small>
            </td>
            <td>
              <button
                type="button"
                class="sample-shelf"
                :disabled="!backendReady"
                title="按此库位精确查找"
                @click="filters.location=row.locationCode;filters.locationMode='exact';search()"
              >
                {{ row.locationCode }}
              </button>
            </td>
            <td>
              <SampleImage
                v-if="row.storageImageId"
                :image-id="row.storageImageId"
                :alt="`${row.skuName} · ${row.locationCode} 存放照片`"
              /><span
                v-else
                class="muted"
              >未上传</span>
            </td>
            <td class="sample-remark">
              <details v-if="(row.remark?.length??0)>60">
                <summary>{{ row.remark?.slice(0,60) }}…</summary><p>{{ row.remark }}</p>
              </details><span v-else>{{ row.remark || '—' }}</span>
            </td>
            <td class="sample-time">
              {{ beijingDateTime(row.updatedAt) }}
            </td>
            <td class="sample-actions">
              <template v-if="!row.deletedAt">
                <button
                  type="button"
                  class="text-action"
                  :disabled="working || loading || !!error"
                  @click="printSample=row"
                >
                  标签打印
                </button><RowMoreActions
                  v-if="backendReady"
                  :label="`${row.skuName} 更多操作`"
                >
                  <button
                    type="button"
                    @click="detailId=row.id"
                  >
                    详情 / 历史
                  </button><button
                    v-if="writable"
                    type="button"
                    :disabled="blocked || loading || !!error"
                    @click="edit(row)"
                  >
                    编辑
                  </button><button
                    v-if="writable"
                    type="button"
                    :disabled="blocked || loading || !!error || ['DELETED','UNAVAILABLE'].includes(row.linkedStatus??'')"
                    @click="edit(row,true)"
                  >
                    复制到其他库位
                  </button><button
                    v-if="removable"
                    type="button"
                    class="sample-remove"
                    :disabled="blocked || loading || !!error"
                    @click="remove(row)"
                  >
                    移除
                  </button>
                </RowMoreActions>
              </template><template v-else>
                <button
                  type="button"
                  class="text-action"
                  @click="detailId=row.id"
                >
                  详情
                </button><button
                  v-if="writable && removable"
                  type="button"
                  class="text-action"
                  :disabled="blocked || loading || !!error"
                  @click="restore(row)"
                >
                  恢复
                </button>
              </template>
            </td>
          </tr><tr v-if="!rows.length">
            <td
              :colspan="appliedFilters.deleted?8:9"
              class="data-state"
            >
              {{ hasFilters?'没有匹配的样品记录':appliedFilters.deleted?'暂无已移除记录':'暂无样品库位登记' }} <button
                v-if="hasFilters"
                type="button"
                @click="reset"
              >
                清除筛选
              </button><button
                v-else-if="writable && !appliedFilters.deleted"
                type="button"
                @click="edit()"
              >
                新增样品
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      :total="total"
      :page="page"
      :page-size="size"
      :disabled="loading || busy"
      @update:page="changePage"
      @update:page-size="size=$event;changePage(1)"
    />
    <dialog
      ref="dialog"
      class="samples-ui sample-dialog"
      aria-labelledby="sample-editor-title"
      @cancel.prevent="requestClose"
    >
      <form
        v-if="open"
        @submit.prevent="save()"
        @keydown="preventEnterSave"
      >
        <header>
          <h2 id="sample-editor-title">
            {{ editing?'编辑样品登记':copying?'复制到其他库位':'新增样品' }}
          </h2><button
            type="button"
            :disabled="working"
            aria-label="关闭样品编辑"
            @click="requestClose"
          >
            ×
          </button>
        </header>
        <div class="sample-dialog-body">
          <p
            v-if="formError"
            role="alert"
            class="sample-notice access-danger"
          >
            {{ formError }}
          </p>
          <button
            v-if="editing && formError && !pending"
            type="button"
            :disabled="checkingConflict"
            @click="checkConflict"
          >
            {{ checkingConflict?'加载中…':'加载最新资料并对照草稿' }}
          </button>
          <button
            v-if="duplicateRow"
            type="button"
            class="text-action"
            @click="detailId=duplicateRow.id"
          >
            查看已登记的 {{ duplicateRow.skuName }} · {{ duplicateRow.locationCode }}
          </button>
          <section
            v-if="conflictRow"
            class="sample-conflict"
          >
            <strong>{{ conflictRow.deletedAt?'登记已被移除，不能继续保存':'最新资料与当前草稿对照' }}</strong><table>
              <thead><tr><th>字段</th><th>最新资料</th><th>当前草稿</th></tr></thead><tbody>
                <tr
                  v-for="c in comparison"
                  :key="c.label"
                >
                  <td>{{ c.label }}</td><td>{{ c.label.includes('照片')||c.label.includes('图片')?(c.saved?'已上传':'未上传'):c.saved||'未填写' }}</td><td>{{ c.label.includes('照片')||c.label.includes('图片')?(c.draft?'已上传':'未上传'):c.draft||'未填写' }}</td>
                </tr>
              </tbody>
            </table><p v-if="!comparison.length">
              资料内容一致，版本已更新。
            </p><button
              v-if="!conflictRow.deletedAt"
              type="button"
              @click="acceptVersion"
            >
              保留草稿，采用最新版本继续
            </button>
            <button
              v-if="!conflictRow.deletedAt"
              type="button"
              @click="useLatest"
            >
              用最新资料替换草稿
            </button>
          </section>
          <div
            v-if="!editing && !copying"
            class="sample-source ui-section-tabs"
            role="tablist"
            aria-label="样品来源"
          >
            <button
              type="button"
              role="tab"
              :aria-selected="!manual"
              :class="{active:!manual}"
              :disabled="blocked"
              @click="manual=false"
            >
              关联产品 SKU
            </button><button
              type="button"
              role="tab"
              :aria-selected="manual"
              :class="{active:manual}"
              :disabled="blocked"
              @click="manual=true"
            >
              手动录入
            </button>
          </div>
          <p
            v-if="copying"
            class="sample-hint"
          >
            复制 {{ copying.skuName }} 的资料与样品图片；请填写新库位，存放照片需重新上传。
          </p>
          <div
            v-if="manual"
            class="sample-manual-fields"
          >
            <label><span>样品名称 <span class="required">*</span></span><input
              v-model="sampleName"
              maxlength="200"
              required
              :disabled="blocked"
              placeholder="输入未建立 SKU 的产品名称"
            ></label>
          </div>
          <div
            v-if="(editing || copying) && !manual"
            class="selected-sku selected-sku-preview"
          >
            <SampleImage
              :image-id="(editing??copying)?.displayImageId ?? (editing??copying)?.sampleImageId"
              :image-url="(editing??copying)?.displayImageUrl"
              :alt="(editing??copying)?.skuName??'样品'"
            /><div><strong>{{ (editing??copying)?.skuName }}</strong><small>父体：{{ (editing??copying)?.productName }}</small><small>名称与规格保留登记资料</small></div>
          </div>
          <template v-else-if="!manual">
            <button
              type="button"
              class="sample-sku-picker-button"
              :disabled="blocked"
              @click="skuPickerOpen=true"
            >
              {{ selected?'更换 SKU':'选择产品 SKU' }}
            </button><div
              v-if="selected"
              class="selected-sku selected-sku-preview"
            >
              <PurchaseItemImage
                :image-asset-id="selected.variant.imageAssetId"
                :image-url="selected.variant.imageUrl"
                :alt="selected.variant.variantName"
              /><div><strong>{{ selected.variant.variantName }}</strong><small>父体：{{ selected.productName }}</small></div>
            </div><p
              v-else
              class="sample-hint"
            >
              尚未选择 SKU
            </p>
          </template>
          <div class="sample-field-grid">
            <label class="location-field">库位 <span class="required">*</span><input
              ref="locationInput"
              v-model="location"
              list="sample-location-options"
              maxlength="100"
              placeholder="例如 A-01-02"
              :disabled="blocked"
              required
            ><datalist id="sample-location-options"><option
              v-for="value in locationOptions"
              :key="value"
              :value="value"
            /></datalist><small>忽略大小写与全角差异，支持选择已有库位。</small></label><label>规格说明<input
              v-model="specification"
              maxlength="500"
              :disabled="blocked"
              placeholder="选填，如颜色、尺寸"
            ></label><label class="sample-field-wide">备注<textarea
              v-model="remark"
              maxlength="1000"
              :disabled="blocked"
              rows="2"
              placeholder="选填"
            /></label>
          </div>
          <p
            v-if="locationError"
            role="alert"
            class="sample-hint"
          >
            {{ locationError }} <button
              type="button"
              @click="loadLocations"
            >
              重试
            </button>
          </p>
          <div
            class="sample-photo-grid"
            :class="{'sample-photo-grid-single':!manual}"
          >
            <section
              v-if="manual"
              class="sample-storage-editor"
            >
              <h3 class="sample-upload-label">
                样品图片
              </h3>
              <div
                v-if="sampleImageId || copying?.sampleImageId"
                class="sample-storage-preview"
              >
                <SampleImage
                  :image-id="sampleImageId??copying?.sampleImageId"
                  alt="样品图片"
                /><button
                  v-if="sampleImageId"
                  type="button"
                  class="ui-image-remove"
                  :disabled="blocked"
                  @click="sampleImageId=null"
                >
                  移除图片
                </button><span
                  v-else
                  class="sample-hint"
                >保存时复制来源样品图</span>
              </div><SingleFileUpload
                :key="`${editorRevision}-sample`"
                compact
                title="样品图片"
                accept=".png,.jpg,.jpeg,.webp"
                :max-mb="5"
                :disabled="blocked"
                :upload="uploadSample"
                hint="样品本身的图片"
              />
            </section>
            <section class="sample-storage-editor">
              <h3 class="sample-upload-label">
                存放照片
              </h3>
              <div
                v-if="storageImageId"
                class="sample-storage-preview"
              >
                <SampleImage
                  :image-id="storageImageId"
                  alt="样品存放照片"
                /><button
                  type="button"
                  class="ui-image-remove"
                  :disabled="blocked"
                  @click="storageImageId=null"
                >
                  移除照片
                </button>
              </div><SingleFileUpload
                :key="`${editorRevision}-storage`"
                compact
                title="存放照片"
                accept=".png,.jpg,.jpeg,.webp"
                :max-mb="5"
                :disabled="blocked"
                :upload="uploadStorage"
                hint="拍摄样品及周边库位"
              />
            </section>
          </div>
          <p class="sample-hint">
            图片立即上传，保存登记后关联。{{ !manual?'同一 SKU 可登记多个库位，同一库位不能重复登记。':'' }}
          </p>
        </div>
        <footer class="ui-dialog-actions">
          <button
            type="button"
            class="secondary-action"
            :disabled="working"
            @click="requestClose"
          >
            {{ pending?'稍后确认':'取消' }}
          </button><button
            v-if="!editing && !pending"
            type="button"
            :disabled="blocked"
            @click="save(true)"
          >
            保存并继续
          </button><button
            type="submit"
            class="primary-action"
            :disabled="working"
          >
            {{ busy?'保存中…':pending?'确认原请求':'保存' }}
          </button>
        </footer>
      </form>
    </dialog>
    <CustomerSkuPicker
      v-if="skuPickerOpen"
      :selected-id="selected?.variant.id??''"
      :active-only="false"
      :show-codes="false"
      @close="skuPickerOpen=false"
      @select="chooseSku"
    />
    <UnsavedChangesDialog
      v-if="discard"
      @keep="discard=false"
      @discard="close"
    />
  </section>
  <SampleDetailDialog
    v-if="detailId"
    :id="detailId"
    :writable="writable && !open && !blocked"
    @close="detailId=''"
    @edit="detailId='';edit($event)"
    @print="printSample=$event"
  />
  <SampleLabelDialog
    v-if="printSample"
    :sample="printSample"
    @close="printSample=undefined"
  />
  <SampleLabelDialog
    v-if="printRows"
    :samples="printRows"
    @close="printRows=undefined"
  />
  <AccessDialog
    v-if="exportOpen"
    title="导出盘点清单"
    dialog-class="samples-ui"
    :busy="exporting"
    @close="exportOpen=false"
  >
    <p>CSV 清单包含品名、规格、来源、所属产品、库位、备注和北京时间。</p><label class="sample-export-choice"><input
      v-model="exportSelection"
      type="radio"
      :value="false"
      :disabled="exporting"
    >当前筛选全部结果（{{ total }} 条，最多 10000 条）</label><label class="sample-export-choice"><input
      v-model="exportSelection"
      type="radio"
      :value="true"
      :disabled="!selectedIds.length || exporting"
    >已勾选记录（{{ selectedIds.length }} 条，跨页保留）</label><p
      v-if="actionError"
      role="alert"
      class="access-danger"
    >
      {{ actionError }}
    </p><template #footer>
      <button
        type="button"
        :disabled="exporting"
        @click="exportOpen=false"
      >
        取消
      </button><button
        type="button"
        class="primary-action"
        :disabled="exporting || (exportSelection && !selectedIds.length)"
        @click="exportRows"
      >
        {{ exporting?'正在导出…':'导出 CSV' }}
      </button>
    </template>
  </AccessDialog>
</template>
