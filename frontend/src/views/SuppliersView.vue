<script setup lang="ts">
import '@/styles/supplierManagement.css'
import {computed,nextTick,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue'
import {onBeforeRouteLeave} from 'vue-router'
import {domainApi,type SupplierDto} from '@/api/domain'
import {supplierManagementApi as api,type SupplierFilters,type SupplierImpact} from '@/api/supplierManagement'
import {session} from '@/state/session'
import {runtimeConfig} from '@/config/runtime'
import {confirmAction} from '@/state/uiConfirmation'
import {vSingleSelect} from '@/directives/singleSelect'
import SupplierImage from '@/components/SupplierImage.vue'
import SupplierContactValue from '@/components/SupplierContactValue.vue'
import SupplierEditor from '@/components/SupplierEditor.vue'
import SupplierDetailDialog from '@/components/SupplierDetailDialog.vue'
import SupplierDeleteDialog from '@/components/SupplierDeleteDialog.vue'
import SupplierImpactSummary from '@/components/SupplierImpactSummary.vue'
import AccessDialog from '@/components/AccessDialog.vue'
import RowMoreActions from '@/components/RowMoreActions.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const defaults=():SupplierFilters=>({q:'',status:'',category:''}),filters=reactive(defaults()),applied=ref(defaults())
const items=ref<SupplierDto[]>([]),page=ref(1),size=ref(20),total=ref(0),loading=ref(false),busy=ref(false),ready=ref(false),error=ref(''),capabilityError=ref(''),success=ref(''),host=ref<HTMLElement>()
const writable=computed(()=>session.canAccess(['suppliers.write'])),deletable=computed(()=>session.canAccess(['suppliers.delete']))
const editorState=ref<{id?:string;copy?:boolean;resume?:boolean}>(),editor=ref<InstanceType<typeof SupplierEditor>>(),detailId=ref(''),deleteTarget=ref<SupplierDto>(),statusTarget=ref<SupplierDto>(),impact=ref<SupplierImpact>(),impactError=ref(''),impactLoading=ref(false)
const selected=ref<Record<string,SupplierDto>>({}),hasPending=ref(false),expanded=ref<string[]>([]),owner=session.user.value?.id??'current',stateKey=`jiugeng.supplier.view.${owner}`,pendingKey=`jiugeng.supplier.pending.${owner}`
const selection=computed(()=>Object.values(selected.value)),allSelected=computed(()=>items.value.length>0&&items.value.every(s=>!!selected.value[s.id]))
const tags=computed(()=>Object.entries(applied.value).filter(([,v])=>v).map(([key,value])=>({key:key as keyof SupplierFilters,label:({q:'关键词',status:'状态',category:'分类'})[key],value:({ACTIVE:'启用',DISABLED:'停用',PRODUCT:'产品',MATERIAL:'辅料'} as Record<string,string>)[value]??value})))
let generation=0,scroll=0,impactGeneration=0
function remember(){scroll=host.value?.querySelector('.table-viewport')?.scrollTop??scroll;try{sessionStorage.setItem(stateKey,JSON.stringify({filters:applied.value,page:page.value,size:size.value,scroll}))}catch{/* Optional preferences. */}}
function pending(){try{hasPending.value=!!sessionStorage.getItem(pendingKey)}catch{hasPending.value=false}}
async function load(){const seq=++generation;loading.value=true;error.value='';try{const f=applied.value;const r=await domainApi.suppliers({q:f.q,...(f.status?{status:f.status}:{}),...(f.category?{category:f.category}:{}),page:page.value-1,size:size.value});if(seq!==generation)return;if(page.value>1&&!r.items.length){page.value=Math.max(1,Math.ceil(r.totalCount/size.value));return}items.value=r.items;total.value=r.totalCount;await nextTick();const viewport=host.value?.querySelector('.table-viewport');if(viewport)viewport.scrollTop=scroll}catch(e){if(seq===generation)error.value=(items.value.length?'刷新失败，当前显示上次结果。':'')+(e instanceof Error?e.message:'供应商读取失败')}finally{if(seq===generation)loading.value=false}}
async function initialize(){ready.value=false;capabilityError.value='';if(runtimeConfig.authMode==='mock'){capabilityError.value='演示模式不提供供应商维护，请使用本地正式登录';await load();return}try{ready.value=(await api.capabilities()).closeoutVersion>=1;if(!ready.value)throw new Error('本地后端尚未同步供应商收尾接口');await load()}catch(e){capabilityError.value=e instanceof Error?e.message:'后端能力读取失败，暂不可操作'}}
function search(){applied.value={...filters};selected.value={};scroll=0;if(page.value!==1)page.value=1;else void load();remember()}
function reset(){Object.assign(filters,defaults());search()}
function removeTag(key:keyof SupplierFilters){filters[key]='';applied.value={...applied.value,[key]:''};selected.value={};scroll=0;if(page.value!==1)page.value=1;else void load()}
function toggle(s:SupplierDto){if(selected.value[s.id]){const next={...selected.value};delete next[s.id];selected.value=next}else if(selection.value.length<100)selected.value={...selected.value,[s.id]:s};else error.value='一次最多勾选 100 家供应商'}
function toggleAll(){if(allSelected.value){const next={...selected.value};for(const s of items.value)delete next[s.id];selected.value=next}else for(const s of items.value)if(!selected.value[s.id])toggle(s)}
function openEditor(s?:SupplierDto,copy=false){pending();editorState.value=hasPending.value?{resume:true}:s?{id:s.id,copy}:{}}
function closeEditor(){editorState.value=undefined;pending()}
async function saved(s:SupplierDto){success.value='供应商已保存';selected.value={};await load();if(!items.value.some(i=>i.id===s.id))success.value='供应商已保存；当前筛选或页码未包含该记录，可按名称搜索'}
async function status(s:SupplierDto){statusTarget.value=s;impact.value=undefined;await loadImpact()}
async function loadImpact(){const s=statusTarget.value;if(!s)return;const seq=++impactGeneration;impactLoading.value=true;impactError.value='';try{const result=await api.impact(s.id);if(seq===impactGeneration)impact.value=result}catch(e){if(seq===impactGeneration)impactError.value=e instanceof Error?e.message:'影响读取失败'}finally{if(seq===impactGeneration)impactLoading.value=false}}
async function confirmStatus(){if(!statusTarget.value||!impact.value||impactLoading.value||busy.value)return;busy.value=true;try{await api.status(statusTarget.value,statusTarget.value.status==='ACTIVE'?'DISABLED':'ACTIVE',true);statusTarget.value=undefined;success.value='供应商状态已更新';selected.value={};await load()}catch(e){impactError.value=(e instanceof Error?e.message:'状态修改失败')+'；请关闭并刷新列表后核对'}finally{busy.value=false}}
async function changed(){deleteTarget.value=undefined;selected.value={};success.value='供应商资料已更新';await load()}
async function exportRows(chosen=false){if(busy.value)return;busy.value=true;error.value='';try{const blob=await api.export(chosen?defaults():applied.value,chosen?selection.value.map(s=>s.id):[]);const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='供应商清单.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}catch(e){error.value=e instanceof Error?e.message:'导出失败'}finally{busy.value=false}}
watch([page,size],()=>{remember();if(ready.value)void load()})
onBeforeRouteLeave(async()=>{remember();if(busy.value||editor.value?.busy)return false;if(editor.value?.dirty&&!editor.value.pending)return confirmAction('当前填写内容未保存，确定放弃并离开吗？', { unsaved: true, discardLabel: '放弃并离开' });return true})
onMounted(async()=>{try{const v=JSON.parse(sessionStorage.getItem(stateKey)??'null');if(v){filters.q=typeof v.filters?.q==='string'?v.filters.q:'';filters.status=['ACTIVE','DISABLED'].includes(v.filters?.status)?v.filters.status:'';filters.category=['PRODUCT','MATERIAL'].includes(v.filters?.category)?v.filters.category:'';applied.value={...filters};page.value=Math.max(1,Number(v.page)||1);size.value=[10,20,50,100].includes(v.size)?v.size:20;scroll=Math.max(0,Number(v.scroll)||0)}}catch{/* Defaults. */}pending();await initialize()})
onBeforeUnmount(()=>{generation++;impactGeneration++;remember()})
</script>
<template>
  <section
    ref="host"
    class="page-stack supplier-ui"
  >
    <form
      class="panel filter-panel ui-filter-row supplier-filters"
      aria-label="供应商筛选"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="filters.q"
        type="search"
        maxlength="128"
        placeholder="名称 / 联系人 / 电话 / 微信"
      ></label><label>分类<select
        v-model="filters.category"
        v-single-select
      ><option value="">全部分类</option><option value="PRODUCT">产品</option><option value="MATERIAL">辅料</option></select></label><label>状态<select
        v-model="filters.status"
        v-single-select
      ><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><button
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
      </button>
    </form><div
      v-if="tags.length"
      class="supplier-filter-tags"
    >
      <button
        v-for="t in tags"
        :key="t.key"
        @click="removeTag(t.key)"
      >
        {{ t.label }}：{{ t.value }} ×
      </button>
    </div><section class="panel material-toolbar">
      <div>
        <button
          v-if="writable"
          :disabled="!ready||busy"
          @click="openEditor()"
        >
          新增供应商
        </button><button
          class="secondary-button"
          :disabled="!ready||busy"
          @click="exportRows()"
        >
          导出筛选结果
        </button><button
          v-if="selection.length"
          class="secondary-button"
          :disabled="!ready||busy"
          @click="exportRows(true)"
        >
          导出勾选（{{ selection.length }}）
        </button><button
          v-if="selection.length"
          class="supplier-link"
          @click="selected={}"
        >
          清空勾选
        </button>
      </div><button
        class="supplier-link"
        :disabled="loading||busy"
        @click="initialize"
      >
        刷新
      </button>
    </section><p
      v-if="hasPending&&writable"
      class="supplier-warning"
    >
      有一笔供应商保存结果待确认。<button
        :disabled="!ready"
        @click="editorState={resume:true}"
      >
        恢复原请求
      </button>
    </p><p
      v-if="capabilityError"
      role="alert"
    >
      {{ capabilityError }} <button @click="initialize">
        重试
      </button>
    </p><p
      v-if="error"
      role="alert"
    >
      {{ error }} <button @click="load">
        重新读取
      </button>
    </p><p v-if="loading">
      正在加载…
    </p><TableViewport v-else>
      <table class="data-table supplier-table ui-action-table">
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                aria-label="选择本页供应商"
                :checked="allSelected"
                :indeterminate="!allSelected&&items.some(s=>selected[s.id])"
                @change="toggleAll"
              >
            </th><th>联系图片</th><th>名称 / 分类</th><th>联系人 / 电话</th><th>微信 / 采购来源</th><th>状态</th><th>操作</th>
          </tr>
        </thead><tbody>
          <template
            v-for="s in items"
            :key="s.id"
          >
            <tr :class="{'supplier-selected':selected[s.id]}">
              <td>
                <input
                  type="checkbox"
                  :aria-label="`选择${s.name}`"
                  :checked="!!selected[s.id]"
                  @change="toggle(s)"
                >
              </td><td>
                <SupplierImage
                  :name="s.name"
                  :image-asset-id="s.qrImageId"
                  :image-url="s.qrImageUrl"
                />
              </td><td>
                <button
                  class="supplier-link supplier-name"
                  @click="detailId=s.id"
                >
                  {{ s.name }}
                </button><small>{{ s.categories?.map(c=>c==='PRODUCT'?'产品':'辅料').join('、')||'待分类' }}</small><button
                  v-if="s.remark"
                  class="supplier-link"
                  :aria-expanded="expanded.includes(s.id)"
                  @click="expanded=expanded.includes(s.id)?expanded.filter(id=>id!==s.id):[...expanded,s.id]"
                >
                  {{ expanded.includes(s.id)?'收起备注':'查看备注' }}
                </button>
              </td><td>
                {{ s.contactPerson||'未填联系人' }}<small><SupplierContactValue
                  :value="s.phone"
                  phone
                /></small>
              </td><td>
                <SupplierContactValue :value="s.wechatId" /><small><a
                  v-if="s.sourceUrl"
                  :href="s.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >1688 采购来源 ↗</a><span v-else>未填采购来源</span></small>
              </td><td>
                <span
                  class="supplier-status"
                  :class="{'is-disabled':s.status==='DISABLED'}"
                >{{ s.status==='ACTIVE'?'启用':'停用' }}</span>
              </td><td>
                <div class="table-actions">
                  <button
                    class="supplier-link"
                    @click="detailId=s.id"
                  >
                    详情
                  </button><button
                    v-if="writable"
                    class="supplier-link"
                    :disabled="!ready||busy"
                    @click="openEditor(s)"
                  >
                    编辑
                  </button><RowMoreActions
                    v-if="writable||deletable"
                    :label="`${s.name}更多操作`"
                  >
                    <button
                      v-if="writable"
                      :disabled="!ready||busy"
                      @click="openEditor(s,true)"
                    >
                      复制
                    </button><button
                      v-if="writable"
                      :disabled="!ready||busy"
                      @click="status(s)"
                    >
                      {{ s.status==='ACTIVE'?'停用':'启用' }}
                    </button><button
                      v-if="deletable"
                      class="supplier-danger"
                      :disabled="!ready||busy"
                      @click="deleteTarget=s"
                    >
                      删除
                    </button>
                  </RowMoreActions>
                </div>
              </td>
            </tr><tr v-if="expanded.includes(s.id)">
              <td
                colspan="7"
                class="supplier-remark"
              >
                {{ s.remark }}
              </td>
            </tr>
          </template>
        </tbody>
      </table><p v-if="!items.length&&!error">
        {{ tags.length?'没有符合条件的供应商。':'暂无供应商。' }}
      </p>
    </TableViewport><ListPagination
      :page="page"
      :page-size="size"
      :total="total"
      :disabled="loading||!ready"
      @update:page="page=$event"
      @update:page-size="size=$event;page=1"
    >
      <template #summary>
        共 {{ total }} 家 · 已选 {{ selection.length }} 家
      </template>
    </ListPagination><SuccessToast :message="success" />
  </section><SupplierEditor
    v-if="editorState"
    ref="editor"
    v-bind="editorState"
    @close="closeEditor"
    @saved="saved"
  /><SupplierDetailDialog
    v-if="detailId"
    :id="detailId"
    @close="detailId=''"
  /><SupplierDeleteDialog
    v-if="deleteTarget"
    :supplier="deleteTarget"
    @close="deleteTarget=undefined"
    @deleted="changed"
  /><AccessDialog
    v-if="statusTarget"
    :title="statusTarget.status==='ACTIVE'?'停用供应商':'启用供应商'"
    dialog-class="supplier-ui"
    :busy="busy"
    @close="statusTarget=undefined;impactGeneration++"
  >
    <strong>{{ statusTarget.name }}</strong><p v-if="impactLoading">
      正在读取影响…
    </p><SupplierImpactSummary
      v-if="impact"
      :impact="impact"
    /><p
      v-if="impactError"
      role="alert"
    >
      {{ impactError }} <button @click="loadImpact">
        重新读取影响
      </button>
    </p><template #footer>
      <button
        :disabled="busy"
        @click="statusTarget=undefined"
      >
        取消
      </button><button
        :disabled="busy||impactLoading||!impact||!!impactError"
        @click="confirmStatus"
      >
        确认{{ statusTarget.status==='ACTIVE'?'停用':'启用' }}
      </button>
    </template>
  </AccessDialog>
</template>
