<script setup lang="ts">
import '@/styles/materialManagement.css'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { vSingleSelect } from '@/directives/singleSelect'
import AccessDialog from '@/components/AccessDialog.vue'
import MaterialEditor from '@/components/MaterialEditor.vue'
import MaterialDetailDialog from '@/components/MaterialDetailDialog.vue'
import MaterialInventoryDialog from '@/components/MaterialInventoryDialog.vue'
import MaterialImage from '@/components/MaterialImage.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import { materialManagementApi as api, emptyMaterialFilters, type MaterialFilters, type MaterialRow, type MaterialRecord, type MaterialImpact } from '@/api/materialManagement'
import { materialTypesApi } from '@/api/materialTypes'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import { materialNumber } from '@/ui/materialQuantity'
const route=useRoute(),router=useRouter()
const filters=reactive(emptyMaterialFilters()),applied=ref(emptyMaterialFilters()),advanced=ref(false)
const rows=ref<MaterialRow[]>([]),page=ref(1),size=ref(20),total=ref(0),types=ref<string[]>([])
const loading=ref(false),error=ref(''),ready=ref(false),capabilityError=ref(''),optionError=ref(''),busy=ref(false),success=ref('')
const selected=ref<Record<string,MaterialRecord>>({}),listHost=ref<HTMLElement>(),detailId=ref(''),inventory=ref<MaterialRecord>(),readStock=ref(false)
const editorState=ref<{id?:string;copy?:boolean;resume?:boolean}>(),editor=ref<InstanceType<typeof MaterialEditor>>(),stockDialog=ref<InstanceType<typeof MaterialInventoryDialog>>()
const impactTargets=ref<MaterialRecord[]>([]),impacts=ref<MaterialImpact[]>([]),impactOpen=ref(false),impactLoading=ref(false),impactError=ref(''),impactMode=ref<'delete'|'status'>('delete')
const pendingStocks=ref<string[]>([])
const owner=session.user.value?.id??'current',stateKey=`jiugeng.material.view.${owner}`,pendingKey=`jiugeng.material.pending.${owner}`,hasPending=ref(false)
const writable=computed(()=>session.canAccess(['materials.write'])),stockReadable=computed(()=>session.canAccess(['inventory.read'])),priceReadable=computed(()=>session.canAccess(['costs.read'])),canPurchase=computed(()=>session.canAccess(['purchases.read','purchases.write','suppliers.read']))
const canDelete=computed(()=>session.canAccess(['materials.delete']))
const canSelect=computed(()=>writable.value||canPurchase.value||canDelete.value),selection=computed(()=>Object.values(selected.value)),allSelected=computed(()=>rows.value.length>0&&rows.value.every(r=>selected.value[r.material.id]))
const filterLabels:Record<keyof MaterialFilters,string>={q:'关键词',type:'类型',status:'状态',stock:'库存',supplier:'供应商',photo:'图片',price:'成本'}
const appliedTags=computed(()=>Object.entries(applied.value).filter(([,v])=>v).map(([k,v])=>({key:k as keyof MaterialFilters,label:filterLabels[k as keyof MaterialFilters],value:({ACTIVE:'启用',DISABLED:'停用',LOW:'低库存',NORMAL:'正常',ZERO:'零库存',UNINITIALIZED:'未建账',YES:'有图',NO:'缺图',MISSING:'缺成本',SET:'已维护'} as Record<string,string>)[v]??v})))
const canConfirmImpact=computed(()=>!impactLoading.value&&!impactError.value&&!busy.value&&impacts.value.length===impactTargets.value.length&&(impactMode.value==='status'||impacts.value.every(i=>i.canDelete)))
let sequence=0,scrollTop=0
function saveView(){scrollTop=listHost.value?.querySelector('.table-viewport')?.scrollTop??scrollTop;try{sessionStorage.setItem(stateKey,JSON.stringify({filters:applied.value,page:page.value,size:size.value,scrollTop}))}catch{/* Optional preferences. */}}
function checkPending(){try{hasPending.value=!!sessionStorage.getItem(pendingKey);const prefix=`jiugeng.material-stock.pending.${owner}.`;pendingStocks.value=Object.keys(sessionStorage).filter(k=>k.startsWith(prefix)).map(k=>k.slice(prefix.length))}catch{hasPending.value=false}}
async function resumeStock(id:string){try{const d=await api.detail(id);stock(d.material)}catch(e){error.value=e instanceof Error?e.message:'待确认辅料读取失败'}}
function closeStock(){inventory.value=undefined;checkPending()}
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await api.page(applied.value,page.value-1,size.value);if(seq!==sequence)return;if(page.value>1&&!r.items.length&&r.totalCount<(page.value-1)*size.value+1){page.value=Math.max(1,Math.ceil(r.totalCount/size.value));return}rows.value=r.items;total.value=r.totalCount;await nextTick();const viewport=listHost.value?.querySelector('.table-viewport');if(viewport)viewport.scrollTop=scrollTop}catch(e){if(seq===sequence)error.value=(rows.value.length?'刷新失败，当前显示上次结果。':'')+(e instanceof Error?e.message:'辅料加载失败')}finally{if(seq===sequence)loading.value=false}}
async function initialize(){ready.value=false;capabilityError.value='';try{const cap=await api.capabilities();ready.value=cap.closeoutVersion>=1;if(!ready.value)throw new Error('本地后端尚未支持辅料收尾功能');await load()}catch(e){capabilityError.value=e instanceof Error?e.message:'后端能力读取失败，暂不可操作'}}
async function loadOptions(){optionError.value='';try{types.value=await materialTypesApi.list()}catch{optionError.value='类型候选项读取失败'}}
function search(){applied.value={...filters};selected.value={};scrollTop=0;if(page.value!==1)page.value=1;else void load();saveView()}
function reset(){Object.assign(filters,emptyMaterialFilters());search()}
function removeTag(key:keyof MaterialFilters){filters[key]='';applied.value={...applied.value,[key]:''};selected.value={};scrollTop=0;if(page.value!==1)page.value=1;else void load()}
function toggle(item:MaterialRecord){if(selected.value[item.id]){const next={...selected.value};delete next[item.id];selected.value=next}else if(selection.value.length<100)selected.value={...selected.value,[item.id]:item};else error.value='一次最多勾选 100 条辅料'}
function togglePage(){if(allSelected.value){for(const row of rows.value){const next={...selected.value};delete next[row.material.id];selected.value=next}}else for(const row of rows.value)if(!selected.value[row.material.id])toggle(row.material)}
function edit(item?:MaterialRecord,copy=false){checkPending();if(hasPending.value){editorState.value={resume:true};return}editorState.value=item?{id:item.id,copy}:{};detailId.value=''}
function closeEditor(){editorState.value=undefined;checkPending()}
async function saved(item:MaterialRecord){success.value='辅料已保存';selected.value={};await load();if(!rows.value.some(r=>r.material.id===item.id))success.value='辅料已保存；当前筛选或页码未包含该记录，可按名称搜索。'}
function stock(item:MaterialRecord,readOnly=false){detailId.value='';inventory.value=item;readStock.value=readOnly}
async function fromDetailStock(){try{const d=await api.detail(detailId.value);stock(d.material,true)}catch(e){error.value=e instanceof Error?e.message:'辅料读取失败'}}
async function fromDetailEdit(){const id=detailId.value;detailId.value='';editorState.value={id}}
async function requestImpact(targets:MaterialRecord[],mode:'delete'|'status'){impactTargets.value=targets;impactMode.value=mode;impactOpen.value=true;await loadImpacts()}
async function loadImpacts(){impactLoading.value=true;impactError.value='';impacts.value=[];try{const result:MaterialImpact[]=[];for(let i=0;i<impactTargets.value.length;i+=4)result.push(...await Promise.all(impactTargets.value.slice(i,i+4).map(m=>api.impact(m.id))));impacts.value=result}catch(e){impactError.value=e instanceof Error?e.message:'影响范围读取失败'}finally{impactLoading.value=false}}
async function confirmImpact(){if(!canConfirmImpact.value)return;busy.value=true;impactError.value='';try{if(impactMode.value==='delete')await api.remove(impactTargets.value);else await api.status(impactTargets.value[0]!);impactOpen.value=false;selected.value={};success.value=impactMode.value==='delete'?'辅料已删除':'辅料状态已更新';await load()}catch(e){impactError.value=e instanceof Error?e.message:'操作失败，请刷新后核对'}finally{busy.value=false}}
async function purchase(items:MaterialRecord[]){
 if(busy.value)return;busy.value=true;error.value='';saveView()
 try{const fresh=await api.selection(items.map(m=>m.id));if(fresh.length!==items.length||fresh.some(r=>r.material.status!=='ACTIVE'))throw new Error('所选辅料已删除或停用，请刷新后重新选择');const amounts:Record<string,number>={};for(const row of fresh)if(row.quantity!=null&&row.safetyStock!=null)amounts[row.material.id]=Number(Math.max(0,row.safetyStock-row.quantity).toFixed(4));busy.value=false;await router.push({path:'/purchases/orders',query:{type:'MATERIAL',materialIds:items.map(m=>m.id).join(','),materialQuantities:JSON.stringify(amounts)}})}catch(e){error.value=e instanceof Error?e.message:'采购资料加载失败'}finally{busy.value=false}
}
async function exportRows(chosen=false){if(busy.value)return;busy.value=true;error.value='';try{const blob=await api.export(chosen?emptyMaterialFilters():applied.value,chosen?selection.value.map(m=>m.id):[]);const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='辅料清单.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(e){error.value=e instanceof Error?e.message:'导出失败'}finally{busy.value=false}}
async function openLinked(){const view=route.query.viewMaterial;if(typeof view==='string'&&ready.value){detailId.value=view;const query={...route.query};delete query.viewMaterial;await router.replace({query});return}const id=route.query.editMaterial;if(typeof id==='string'&&writable.value&&ready.value){editorState.value={id};const query={...route.query};delete query.editMaterial;await router.replace({query})}}
watch([page,size],()=>{saveView();if(ready.value)void load()})
watch(()=>[route.query.editMaterial,route.query.viewMaterial],openLinked)
onBeforeRouteLeave(async()=>{saveView();if(editor.value?.busy||stockDialog.value?.busy||busy.value)return false;if(editor.value?.dirty||stockDialog.value?.dirty)return await confirmAction('当前填写内容未保存，确定放弃并离开吗？', { unsaved: true, discardLabel: '放弃并离开' });return true})
onMounted(async()=>{try{const saved=JSON.parse(sessionStorage.getItem(stateKey)??'null');if(saved){for(const key of Object.keys(filters) as (keyof MaterialFilters)[])if(typeof saved.filters?.[key]==='string')filters[key]=saved.filters[key];applied.value={...filters};page.value=Math.max(1,Number(saved.page)||1);size.value=[10,20,50,100].includes(saved.size)?saved.size:20;scrollTop=Math.max(0,Number(saved.scrollTop)||0)}}catch{/* Defaults. */}checkPending();void loadOptions();await initialize();await openLinked()})
onBeforeUnmount(()=>{sequence++;saveView()})
</script>
<template>
  <section
    ref="listHost"
    class="page-stack material-management materials-ui"
  >
    <form
      class="panel filter-panel ui-filter-row material-filters"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="filters.q"
        maxlength="200"
        placeholder="名称或规格"
      ></label><label>类型<select
        v-model="filters.type"
        v-single-select
      ><option value="">全部类型</option><option
        v-for="t in types"
        :key="t"
      >{{ t }}</option></select></label><label>状态<select
        v-model="filters.status"
        v-single-select
      ><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><label v-if="stockReadable">库存<select
        v-model="filters.stock"
        v-single-select
      ><option value="">全部库存</option><option value="LOW">低库存</option><option value="NORMAL">正常</option><option value="ZERO">零库存</option><option value="UNINITIALIZED">未建账</option></select></label><button
        type="submit"
        :disabled="loading||!ready"
      >
        查询
      </button><button
        class="filter-reset-button"
        type="button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button><button
        type="button"
        class="material-link"
        :aria-expanded="advanced"
        @click="advanced=!advanced"
      >
        更多筛选
      </button><template v-if="advanced">
        <label>供应商<input
          v-model="filters.supplier"
          maxlength="128"
          placeholder="供应商名称"
        ></label><label>图片<select
          v-model="filters.photo"
          v-single-select
        ><option value="">全部</option><option value="YES">有图</option><option value="NO">缺图</option></select></label><label v-if="priceReadable">成本<select
          v-model="filters.price"
          v-single-select
        ><option value="">全部</option><option value="MISSING">缺成本</option><option value="SET">已维护</option></select></label>
      </template>
    </form>
    <div
      v-if="appliedTags.length"
      class="material-filter-tags"
    >
      <button
        v-for="t in appliedTags"
        :key="t.key"
        @click="removeTag(t.key)"
      >
        {{ t.label }}：{{ t.value }} ×
      </button>
    </div>
    <p
      v-if="optionError"
      role="alert"
    >
      {{ optionError }} <button @click="loadOptions">
        重试
      </button>
    </p>
    <section class="panel material-toolbar">
      <div>
        <button
          v-if="writable"
          :disabled="!ready||busy"
          @click="edit()"
        >
          添加辅料
        </button><button
          v-if="canPurchase"
          class="secondary-button"
          :disabled="!ready||busy||!selection.length||selection.some(m=>m.status!=='ACTIVE')"
          @click="purchase(selection)"
        >
          批量采购{{ selection.length?`（${selection.length}）`:'' }}
        </button><button
          class="secondary-button"
          :disabled="!ready||busy||loading"
          @click="exportRows(false)"
        >
          导出筛选结果
        </button><button
          v-if="selection.length"
          class="secondary-button"
          :disabled="busy"
          @click="exportRows(true)"
        >
          导出勾选
        </button><button
          v-if="canDelete&&selection.length"
          class="material-danger"
          :disabled="busy||!ready"
          @click="requestImpact(selection,'delete')"
        >
          删除勾选
        </button><button
          v-if="selection.length"
          class="material-link"
          @click="selected={}"
        >
          清空勾选
        </button>
      </div><RouterLink
        v-if="stockReadable"
        to="/inventory/ledger"
      >
        库存流水
      </RouterLink><button
        class="material-link"
        :disabled="loading"
        @click="initialize"
      >
        刷新
      </button>
    </section>
    <p
      v-if="capabilityError"
      role="alert"
      class="material-warning"
    >
      {{ capabilityError }}。为避免新旧接口混用，写入已禁用。<button @click="initialize">
        重试
      </button>
    </p>
    <p
      v-if="hasPending&&!editorState"
      class="material-warning"
    >
      上次辅料保存结果待确认。<button @click="editorState={resume:true}">
        确认原请求
      </button>
    </p>
    <p
      v-if="pendingStocks.length&&!inventory"
      class="material-warning"
    >
      有 {{ pendingStocks.length }} 条库存操作待确认。<button
        v-for="(id,index) in pendingStocks"
        :key="id"
        @click="resumeStock(id)"
      >
        确认库存请求 {{ index+1 }}
      </button>
    </p>
    <SuccessToast :message="success" /><p
      v-if="error"
      role="alert"
    >
      {{ error }} <button @click="load">
        重试
      </button>
    </p>
    <p v-if="loading">
      正在加载辅料…
    </p><p v-else-if="ready&&!error&&!rows.length">
      {{ appliedTags.length?'没有符合筛选条件的辅料':'暂无辅料，可先添加资料' }}
    </p>
    <TableViewport v-if="rows.length">
      <table class="data-table material-table ui-action-table">
        <thead>
          <tr>
            <th
              v-if="canSelect"
              class="selection-cell"
            >
              <input
                type="checkbox"
                aria-label="选择本页全部辅料"
                :checked="allSelected"
                :indeterminate="!allSelected&&rows.some(r=>selected[r.material.id])"
                :disabled="loading||busy"
                @change="togglePage"
              >
            </th><th>图片</th><th>品名 / 类型</th><th>规格</th><th v-if="priceReadable">
              成本价（不含税）
            </th><th v-if="stockReadable">
              现存量 / 安全库存
            </th><th>状态</th><th>操作</th>
          </tr>
        </thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.material.id"
            :class="{'material-selected':selected[row.material.id]}"
          >
            <td v-if="canSelect">
              <input
                type="checkbox"
                :aria-label="`选择辅料 ${row.material.name}`"
                :checked="!!selected[row.material.id]"
                :disabled="loading||busy"
                @change="toggle(row.material)"
              >
            </td><td>
              <MaterialImage
                :name="row.material.name"
                :image-asset-id="row.material.imageAssetId"
                :image-url="row.material.imageUrl"
              />
            </td><td>
              <button
                class="material-link material-name"
                @click="detailId=row.material.id"
              >
                {{ row.material.name }}
              </button><small>{{ row.material.materialType }}</small>
            </td><td>{{ row.material.specification||'—' }}</td><td
              v-if="priceReadable"
              class="material-number"
            >
              {{ row.netPrice!=null&&row.priceUnit===row.material.unit?`¥ ${materialNumber(row.netPrice)}`:'未维护' }}<small>/ {{ row.material.unit }}</small>
            </td><td
              v-if="stockReadable"
              class="material-number"
            >
              <button
                class="material-link"
                :disabled="!!error"
                @click="stock(row.material,true)"
              >
                {{ row.initialized?materialNumber(row.quantity):'未建账' }}
              </button> {{ row.material.unit }}<small>安全库存 {{ row.initialized?materialNumber(row.safetyStock):'未设置' }}</small><small
                v-if="row.quantity!=null&&row.safetyStock!=null&&row.quantity<row.safetyStock"
                class="material-warning-text"
              >低于安全库存 {{ materialNumber(row.safetyStock-row.quantity) }} {{ row.material.unit }}</small>
            </td><td>
              <span
                class="material-status"
                :class="{'is-disabled':row.material.status==='DISABLED'}"
              >{{ row.material.status==='ACTIVE'?'启用':'停用' }}</span>
            </td><td>
              <div class="table-actions">
                <button
                  class="material-link"
                  @click="detailId=row.material.id"
                >
                  详情
                </button><button
                  v-if="stockReadable"
                  class="material-link"
                  :disabled="!ready||busy"
                  @click="stock(row.material)"
                >
                  库存
                </button><RowMoreActions :label="`${row.material.name}更多操作`">
                  <button
                    v-if="writable"
                    :disabled="row.material.status!=='ACTIVE'||!ready||busy"
                    @click="edit(row.material)"
                  >
                    编辑
                  </button><button
                    v-if="writable"
                    :disabled="!ready||busy"
                    @click="edit(row.material,true)"
                  >
                    复制
                  </button><button
                    v-if="canPurchase"
                    :disabled="row.material.status!=='ACTIVE'||!ready||busy"
                    @click="purchase([row.material])"
                  >
                    采购
                  </button><button
                    v-if="writable"
                    :disabled="!ready||busy"
                    @click="requestImpact([row.material],'status')"
                  >
                    {{ row.material.status==='ACTIVE'?'停用':'启用' }}
                  </button><button
                    v-if="canDelete"
                    class="material-danger"
                    :disabled="!ready||busy"
                    @click="requestImpact([row.material],'delete')"
                  >
                    删除
                  </button>
                </RowMoreActions>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      v-model:page="page"
      v-model:page-size="size"
      :total="total"
      :disabled="loading||!ready"
    >
      <template #summary>
        共 {{ total }} 条 · 已选 {{ selection.length }} 条
      </template>
    </ListPagination>
  </section>
  <MaterialEditor
    v-if="editorState"
    ref="editor"
    v-bind="editorState"
    @close="closeEditor"
    @saved="saved"
  />
  <MaterialDetailDialog
    v-if="detailId"
    :id="detailId"
    @close="detailId=''"
    @edit="fromDetailEdit"
    @inventory="fromDetailStock"
  />
  <MaterialInventoryDialog
    v-if="inventory"
    ref="stockDialog"
    :material="inventory"
    :read-only="readStock"
    @close="closeStock"
    @changed="load"
  />
  <AccessDialog
    v-if="impactOpen"
    :title="impactMode==='delete'?'删除辅料前检查':'辅料状态变更'"
    wide
    dialog-class="materials-ui"
    :busy="busy||impactLoading"
    @close="impactOpen=false"
  >
    <p v-if="impactLoading">
      正在核对关联和库存…
    </p><p
      v-if="impactError"
      role="alert"
    >
      {{ impactError }} <button @click="loadImpacts">
        重新检查
      </button>
    </p><div class="material-table-wrap">
      <table
        v-if="impacts.length"
        class="data-table"
      >
        <thead><tr><th>辅料</th><th>产品关联</th><th>采购明细</th><th>库存流水</th><th>现存量</th><th>处理说明</th></tr></thead><tbody>
          <tr
            v-for="i in impacts"
            :key="i.id"
          >
            <td>{{ i.name }}</td><td>{{ i.products }}</td><td>{{ i.purchases }}</td><td>{{ i.transactions }}</td><td>{{ materialNumber(i.quantity) }}</td><td>{{ impactMode==='status'?'保留历史资料；停用后不能新增采购或库存记账':i.canDelete?'可删除，删除后无法恢复':'已有业务或成本版本引用，不能删除，可使用停用' }}</td>
          </tr>
        </tbody>
      </table>
    </div><p v-if="impactMode==='delete'">
      批量删除任一项不满足条件时，全部不删除。
    </p><template #footer>
      <button
        :disabled="busy"
        @click="impactOpen=false"
      >
        取消
      </button><button
        :disabled="!canConfirmImpact"
        :class="impactMode==='delete'?'material-danger':'ui-button-primary'"
        @click="confirmImpact"
      >
        {{ busy?'处理中…':impactMode==='delete'?'确认删除':impactTargets[0]?.status==='ACTIVE'?'确认停用':'确认启用' }}
      </button>
    </template>
  </AccessDialog>
</template>
