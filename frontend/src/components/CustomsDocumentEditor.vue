<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import AccessDialog from './AccessDialog.vue'
import DatePicker from './DatePicker.vue'
import TableViewport from './TableViewport.vue'
import ListPagination from './ListPagination.vue'
import { customsDocumentsApi, type CustomsDocument, type CustomsInput } from '@/api/customsDocuments'
import { customsApi, type CustomsRow } from '@/api/customs'
import { headerGroups, transportGroups, itemFields, blankItem, missingFields } from '@/ui/customsDocumentFields'
import { confirmAction } from '@/state/uiConfirmation'
const props=defineProps<{document?:CustomsDocument|undefined;copy?:boolean;writable:boolean;exportable:boolean}>()
const emit=defineEmits<{close:[];saved:[CustomsDocument]}>()
const source=props.document,localDate=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`}
const form=ref<CustomsInput>({documentNo:props.copy?'':source?.documentNo??'',status:props.copy?'DRAFT':source?.status==='READY'?'READY':'DRAFT',fields:source?{...source.fields}:{date:localDate()},items:source?source.items.map(r=>({...r})):[],version:props.copy?undefined:source?.version})
const savedId=ref(props.copy?undefined:source?.id),initial=ref(JSON.stringify(form.value)),tab=ref('basic'),busy=ref(false),error=ref(''),notice=ref(''),dialog=ref<InstanceType<typeof AccessDialog>>()
const readOnly=computed(()=>!props.writable||(!props.copy&&source?.status==='VOID')),dirty=computed(()=>JSON.stringify(form.value)!==initial.value),missing=computed(()=>missingFields(form.value.fields,form.value.items))
const tabs=[['basic','单证与收发货人'],['transport','运输与申报'],['items','商品与装箱'],['review','导出检查']]
const activeGroups=computed(()=>tab.value==='basic'?headerGroups:transportGroups)
const money=(n:number)=>Number.isFinite(n)?n.toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}):'—'
const total=computed(()=>form.value.items.reduce((sum,r)=>sum+Math.round(Number(r.quantity||0)*Number(r.price||0)*100)/100,0))
const item=ref<Record<string,string>>(),itemIndex=ref(-1),itemInitial=ref(''),itemError=ref(''),itemDialog=ref<InstanceType<typeof AccessDialog>>()
const itemDirty=computed(()=>!!item.value && !readOnly.value && JSON.stringify(item.value)!==itemInitial.value)
function editItem(index:number){itemIndex.value=index;item.value=index<0?blankItem():{...form.value.items[index]};itemInitial.value=JSON.stringify(item.value);itemError.value=''}
function keepItem(){if(!item.value)return;if(!item.value.sku?.trim()||!item.value.name?.trim()){itemError.value='请至少填写 SKU 和中文申报品名，其余资料可先存草稿。';return}if(itemIndex.value<0)form.value.items.push({...item.value});else form.value.items[itemIndex.value]={...item.value};item.value=undefined}
async function removeItem(index:number){if(await confirmAction('从本票单据移除这项商品？产品资料不会删除。'))form.value.items.splice(index,1)}
const picker=ref(false),query=ref(''),pickerPage=ref(1),pickerSize=ref(10),pickerRows=ref<CustomsRow[]>([]),pickerTotal=ref(0),pickerLoading=ref(false),pickerError=ref(''),selected=ref<CustomsRow[]>([])
let request=0
async function loadPicker(){const seq=++request;pickerLoading.value=true;pickerError.value='';try{const r=await customsApi.list(query.value,'',pickerPage.value-1,pickerSize.value);if(seq===request){pickerRows.value=r.items;pickerTotal.value=r.totalCount}}catch(e){if(seq===request)pickerError.value=message(e)}finally{if(seq===request)pickerLoading.value=false}}
function openPicker(){picker.value=true;selected.value=[];query.value='';pickerPage.value=1;void loadPicker()}
function select(row:CustomsRow){selected.value=selected.value.some(r=>r.variantId===row.variantId)?selected.value.filter(r=>r.variantId!==row.variantId):[...selected.value,row]}
function addSelected(){for(const row of selected.value){const c=row.customs;form.value.items.push({...blankItem(),variantId:row.variantId,sku:row.skuCode,name:c?.nameZh??'',nameEn:c?.nameEn??'',hsCode:c?.chinaCode??'',unit:c?.unit??'',origin:c?.originCountry??'',declarationElements:c?.declarationElements??''})}picker.value=false;tab.value='items'}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
async function save(){if(busy.value||readOnly.value)return;busy.value=true;error.value='';try{const d=await customsDocumentsApi.save(form.value,savedId.value);savedId.value=d.id;form.value.version=d.version;initial.value=JSON.stringify(form.value);notice.value='单据已保存';emit('saved',d)}catch(e){error.value=message(e)}finally{busy.value=false}}
async function exportFile(){if(!savedId.value||busy.value||dirty.value)return;busy.value=true;error.value='';try{await customsDocumentsApi.export(savedId.value,form.value.documentNo);notice.value='五张报关单证已导出'}catch(e){error.value=message(e);tab.value='review'}finally{busy.value=false}}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value||itemDirty.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>window.addEventListener('beforeunload',beforeUnload));onBeforeUnmount(()=>{++request;window.removeEventListener('beforeunload',beforeUnload)})
onBeforeRouteLeave(()=>{if(busy.value)return false;if(itemDirty.value){itemDialog.value?.requestClose();return false}if(dirty.value){dialog.value?.requestClose();return false}return true})
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="readOnly?'报关单据详情':savedId?'编辑报关单据':'新建报关单据'"
    wide
    dialog-class="customs-document-editor"
    :dirty="dirty"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="customs-document-summary">
      <strong>{{ form.documentNo || '新的一票报关资料' }}</strong><span>{{ form.items.length }} 项商品</span><span>{{ form.fields.currency || '币制待填写' }} {{ money(total) }}</span>
    </div>
    <div class="ui-section-tabs">
      <button
        v-for="[key,label] in tabs"
        :key="key"
        type="button"
        :class="{active:tab===key}"
        :aria-pressed="tab===key"
        @click="tab=key!"
      >
        {{ label }}<span v-if="key==='review' && missing.length">（{{ missing.length }}）</span>
      </button>
    </div>
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p><p
      v-if="notice && !dirty"
      role="status"
      class="customs-save-notice"
    >
      {{ notice }}
    </p>
    <fieldset
      v-if="tab==='basic' || tab==='transport'"
      :disabled="busy || readOnly"
      class="customs-fields"
    >
      <label
        v-if="tab==='basic'"
        class="document-number"
      >单据编号 <span class="required">*</span><input
        v-model="form.documentNo"
        maxlength="128"
        placeholder="用于查找本票资料，不替代合同号或发票号"
      ></label>
      <section
        v-for="group in activeGroups"
        :key="group.name"
        class="customs-form-section"
      >
        <h3>{{ group.name }}</h3><div class="customs-form-grid">
          <label
            v-for="f in group.fields"
            :key="f.key"
            :class="{wide:f.type==='textarea'}"
          >{{ f.label }}<span
            v-if="f.required"
            class="required"
          > *</span><DatePicker
            v-if="f.type==='date'"
            v-model="form.fields[f.key]"
            type="date"
            :disabled="busy || readOnly"
          /><textarea
            v-else-if="f.type==='textarea'"
            v-model="form.fields[f.key]"
            maxlength="4000"
            rows="2"
          /><input
            v-else
            v-model="form.fields[f.key]"
            maxlength="4000"
          ></label>
        </div>
      </section>
    </fieldset>
    <template v-else-if="tab==='items'">
      <div class="customs-item-toolbar">
        <button
          v-if="!readOnly"
          type="button"
          class="primary-button"
          :disabled="busy || form.items.length>=100"
          @click="openPicker"
        >
          选择产品 SKU
        </button><button
          v-if="!readOnly"
          type="button"
          :disabled="busy || form.items.length>=100"
          @click="editItem(-1)"
        >
          手动添加商品
        </button><span>箱数与重量填写本行合计；混装箱请拆分归属，避免重复累计。</span>
      </div>
      <TableViewport>
        <table class="data-table customs-items-table">
          <thead><tr><th>SKU / 品名</th><th>商品编号</th><th>数量 / 单位</th><th>单价 / 金额</th><th>箱数</th><th>毛重 / 净重 kg</th><th>资料</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="(r,i) in form.items"
              :key="i"
            >
              <td><strong>{{ r.sku || '待填写 SKU' }}</strong><small>{{ r.name || '待填写品名' }}</small></td><td>{{ r.hsCode || '—' }}</td><td>{{ r.quantity || '—' }} {{ r.unit }}</td><td>{{ r.price || '—' }}<small>{{ r.quantity && r.price?money(Number(r.quantity)*Number(r.price)):'—' }}</small></td><td>{{ r.cartons || '—' }}</td><td>{{ r.grossWeight || '—' }} / {{ r.netWeight || '—' }}</td><td>{{ missingFields(form.fields,[r]).some(x=>x.startsWith('商品'))?'待补充':'已填写' }}</td><td>
                <button
                  type="button"
                  class="text-action"
                  :disabled="busy"
                  @click="editItem(i)"
                >
                  {{ readOnly?'查看':'编辑' }}
                </button><button
                  v-if="!readOnly"
                  type="button"
                  class="text-action danger"
                  :disabled="busy"
                  @click="removeItem(i)"
                >
                  移除
                </button>
              </td>
            </tr><tr v-if="!form.items.length">
              <td
                colspan="8"
                class="data-state"
              >
                请选择产品 SKU 或手动添加商品，再填写本票数量、申报价格和装箱信息。
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </template>
    <template v-else>
      <section class="customs-export-summary">
        <h3>导出完整报关资料</h3><p>报关单 · 申报要素 · 合同 · 发票 · 装箱单</p><p>五张表使用同一票数据，明细和合计自动生成。标记已核对表示内部核对，不代表已向海关申报。</p>
      </section>
      <h3>{{ missing.length?`尚有 ${missing.length} 项必填资料`:'基本资料已齐全' }}</h3><ul
        v-if="missing.length"
        class="customs-missing"
      >
        <li
          v-for="m in missing"
          :key="m"
        >
          {{ m }}
        </li>
      </ul><p
        v-else
        class="customs-save-notice"
      >
        可保存后导出，导出时会再次检查数量、价格和重量。
      </p>
      <label class="customs-reviewed"><input
        v-model="form.status"
        type="checkbox"
        true-value="READY"
        false-value="DRAFT"
        :disabled="busy || readOnly"
      >已人工核对本票资料</label>
      <p
        v-if="dirty || !savedId"
        class="muted"
      >
        请先保存单据，再导出已保存的版本。
      </p>
    </template>
    <template #footer>
      <span class="customs-footer-note">{{ dirty?'有未保存修改':savedId?'已保存':'可先存草稿，随后补齐资料' }}</span><button
        type="button"
        :disabled="busy"
        @click="dialog?.requestClose()"
      >
        关闭
      </button><button
        v-if="exportable && (copy || source?.status!=='VOID')"
        type="button"
        :disabled="busy || dirty || !savedId || !!missing.length"
        @click="exportFile"
      >
        导出 Excel
      </button><button
        v-if="!readOnly"
        type="button"
        class="ui-button-primary"
        :disabled="busy || (!dirty && !!savedId)"
        @click="save"
      >
        {{ busy?'处理中…':form.status==='READY'?'保存已核对资料':'保存草稿' }}
      </button>
    </template>
  </AccessDialog>
  <AccessDialog
    v-if="item"
    ref="itemDialog"
    :title="`商品 ${itemIndex<0?form.items.length+1:itemIndex+1} · 申报与装箱资料`"
    wide
    dialog-class="customs-line-editor"
    :dirty="!readOnly && JSON.stringify(item)!==itemInitial"
    @close="item=undefined"
  >
    <p
      v-if="itemError"
      role="alert"
      class="access-danger"
    >
      {{ itemError }}
    </p><fieldset
      class="customs-fields"
      :disabled="readOnly"
    >
      <div class="customs-form-grid">
        <label
          v-for="f in itemFields"
          :key="f.key"
          :class="{wide:f.type==='textarea'}"
        >{{ f.label }}<span
          v-if="f.required"
          class="required"
        > *</span><textarea
          v-if="f.type==='textarea'"
          :value="item[f.key]"
          maxlength="4000"
          rows="5"
          @input="item[f.key]=($event.target as HTMLInputElement).value"
        /><input
          v-else
          :value="item[f.key]"
          :type="f.type==='number'?'number':'text'"
          :min="f.type==='number'?0:undefined"
          :step="f.key==='cartons'?1:'0.0001'"
          maxlength="4000"
          @input="item[f.key]=($event.target as HTMLInputElement).value"
        ><small
          v-if="f.hint"
          class="muted"
        >{{ f.hint }}</small></label>
      </div>
    </fieldset>
    <template #footer>
      <button
        v-if="readOnly"
        type="button"
        @click="item=undefined"
      >
        关闭
      </button><button
        v-else
        type="button"
        class="ui-button-primary"
        @click="keepItem"
      >
        保存到本票
      </button>
    </template>
  </AccessDialog>
  <AccessDialog
    v-if="picker"
    title="选择产品 SKU"
    wide
    dialog-class="customs-sku-picker"
    @close="picker=false"
  >
    <form
      class="customs-picker-filter"
      @submit.prevent="pickerPage=1;loadPicker()"
    >
      <input
        v-model="query"
        placeholder="搜索 SKU、产品名称或商品编号"
        maxlength="128"
      ><button
        type="submit"
        :disabled="pickerLoading"
      >
        搜索
      </button><span>已选 {{ selected.length }} 项</span>
    </form>
    <p
      v-if="pickerError"
      role="alert"
      class="access-danger"
    >
      {{ pickerError }}
    </p><TableViewport>
      <table class="data-table">
        <thead><tr><th>选择</th><th>SKU / 产品</th><th>申报品名</th><th>商品编号</th><th>资料状态</th></tr></thead><tbody>
          <tr
            v-for="r in pickerRows"
            :key="r.variantId"
          >
            <td>
              <input
                type="checkbox"
                :aria-label="`选择 ${r.skuCode}`"
                :checked="selected.some(x=>x.variantId===r.variantId)"
                @change="select(r)"
              >
            </td><td>{{ r.skuCode }}<small>{{ r.skuName }}</small></td><td>{{ r.customs?.nameZh || '待维护' }}</td><td>{{ r.customs?.chinaCode || '—' }}</td><td>{{ r.customs?.state==='REVIEWED'?'已核对':'待核对' }}</td>
          </tr><tr v-if="!pickerRows.length">
            <td colspan="5">
              {{ pickerLoading?'加载中…':'无匹配产品' }}
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport><ListPagination
      :page="pickerPage"
      :page-size="pickerSize"
      :total="pickerTotal"
      :disabled="pickerLoading"
      @update:page="if(pickerPage!==$event){pickerPage=$event;loadPicker()}"
      @update:page-size="pickerSize=$event;pickerPage=1;loadPicker()"
    />
    <template #footer>
      <button
        type="button"
        @click="picker=false"
      >
        取消
      </button><button
        type="button"
        class="ui-button-primary"
        :disabled="!selected.length || form.items.length+selected.length>100 || pickerLoading"
        @click="addSelected"
      >
        添加所选（{{ selected.length }}）
      </button>
    </template>
  </AccessDialog>
</template>
<style>
#app .customs-document-editor{width:min(1180px,calc(100vw - 24px));height:min(880px,calc(100dvh - 24px))}.customs-document-editor .access-dialog-body{flex:1;min-height:0}.customs-document-summary{display:flex;gap:18px;align-items:center;margin-bottom:14px;font-size:13px}.customs-document-summary strong{font-size:16px}.customs-document-summary span{color:var(--muted)}.customs-fields{border:0;padding:0;margin:0;min-width:0}.customs-form-section{padding:14px 0;border-bottom:1px solid var(--line)}.customs-form-section h3{font-size:14px;margin:0 0 12px}.customs-form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.customs-form-grid label,.document-number{display:block;font-size:13px}.customs-form-grid .wide{grid-column:1/-1}#app .customs-form-grid input,#app .customs-form-grid textarea,#app .document-number input{display:block;margin-top:6px;width:100%;height:36px;padding:7px 10px;border:1px solid var(--line);border-radius:5px;font-size:13px;box-sizing:border-box}#app .customs-form-grid textarea{height:auto;resize:vertical}.customs-form-grid .date-picker{margin-top:6px}.customs-fields .required{color:#b42318}.document-number{max-width:420px;padding-top:12px}.customs-footer-note{margin-right:auto;font-size:12px;color:var(--muted)}.customs-item-toolbar{display:flex;gap:8px;align-items:center;margin:12px 0;flex-wrap:wrap}.customs-item-toolbar span{font-size:12px;color:var(--muted)}.customs-items-table{min-width:960px}.customs-items-table small,.customs-sku-picker small{display:block;color:var(--muted);margin-top:4px}.customs-items-table td{font-size:13px;padding:10px}.customs-export-summary{padding:14px;border:1px solid var(--line);background:#f7f9fc;border-radius:6px;margin:14px 0;font-size:13px}.customs-export-summary h3{margin-top:0}.customs-missing{columns:2;font-size:13px;color:#b54708;line-height:1.9}.customs-reviewed{display:flex;gap:8px;align-items:center;font-size:13px}.customs-save-notice{font-size:13px;color:#21834a}.customs-picker-filter{display:flex;gap:10px;align-items:center;margin-bottom:12px}.customs-picker-filter input{flex:1;min-width:0;height:34px;padding:6px 10px}.customs-sku-picker .table-viewport{max-height:50vh}.customs-line-editor .access-dialog-body{max-height:70vh}.customs-sku-picker td{padding:10px}.customs-document-editor .text-action{border:0;background:none;color:#2864c5}.customs-document-editor .danger{color:#b42318}@media(max-width:760px){.customs-form-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.customs-document-summary{flex-wrap:wrap;gap:8px}.customs-footer-note{flex-basis:100%}}@media(max-width:480px){.customs-form-grid{grid-template-columns:1fr}.customs-missing{columns:1}}
</style>
