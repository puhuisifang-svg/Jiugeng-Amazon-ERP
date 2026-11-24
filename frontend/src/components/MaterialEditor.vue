<script setup lang="ts">
import '@/styles/materialManagement.css'
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import AccessDialog from './AccessDialog.vue'
import EditableSelect from './EditableSelect.vue'
import SingleFileUpload from './SingleFileUpload.vue'
import MaterialImage from './MaterialImage.vue'
import MaterialSupplierDetail from './MaterialSupplierDetail.vue'
import SupplierContactLinks from './SupplierContactLinks.vue'
import ListPagination from './ListPagination.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { materialManagementApi, emptyMaterialFilters, type MaterialRecord, type MaterialDetail, type MaterialSave, type MaterialSupplier } from '@/api/materialManagement'
import { domainApi, type MaterialInput, type SupplierAssignmentDto, type SupplierDto } from '@/api/domain'
import { materialTypesApi } from '@/api/materialTypes'
import { catalogApi } from '@/api/catalog'
import { ApiError } from '@/api/client'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
const props=defineProps<{id?:string;copy?:boolean;resume?:boolean}>(),emit=defineEmits<{close:[];saved:[MaterialRecord]}>()
const dialog=ref<InstanceType<typeof AccessDialog>>()
const data=ref<MaterialDetail>(),loading=ref(false),loadError=ref(''),busy=ref(false),uploading=ref(false),error=ref(''),conflict=ref<MaterialDetail>(),initial=ref(''),duplicate=ref('')
const types=ref<string[]>([]),units=ref<string[]>(['件','个','套','米','千克']),optionsError=ref('')
const form=reactive({materialCode:'',name:'',materialType:'',specification:'',unit:'件',imageUrl:'',imageAssetId:'',remark:''})
const links=ref<SupplierAssignmentDto[]>([]),names=ref<Record<string,MaterialSupplier|SupplierDto>>({}),notes=ref<string[]>([])
const netPrice=ref(''),keepCost=ref(false),confirmedNet=ref(false),priceReason=ref('')
const supplierQuery=ref(''),supplierPage=ref(1),supplierTotal=ref(0),supplierOptions=ref<SupplierDto[]>([]),supplierLoading=ref(false),supplierError=ref(''),supplierOpen=ref(false),viewSupplier=ref('')
const canReadPrice=computed(()=>session.canAccess(['costs.read'])),canWritePrice=computed(()=>session.canAccess(['costs.read','costs.write','materials.write']))
const canEditSuppliers=computed(()=>session.canAccess(['suppliers.read','suppliers.write','materials.write']))
const pendingKey=`jiugeng.material.pending.${session.user.value?.id??'current'}`
interface Pending {input:MaterialSave}
const pending=ref<Pending>()
const locked=computed(()=>busy.value||uploading.value||!!pending.value||loading.value||!!loadError.value||!!conflict.value)
const skippedSuppliers=computed(()=>props.copy?data.value?.suppliers.filter(s=>s.status!=='ACTIVE'||!s.materialEligible)??[]:[])
const editing=computed(()=>!!props.id&&!props.copy)
const priceChanged=computed(()=>canWritePrice.value&&(!props.copy||keepCost.value)&&netPrice.value!==''&&(!data.value?.price||Number(netPrice.value)!==Number(data.value.price.netPrice)||props.copy||form.unit!==data.value.price.unit))
function snapshot(){return JSON.stringify({form,links:links.value,netPrice:netPrice.value,keepCost:keepCost.value,confirmedNet:confirmedNet.value,priceReason:priceReason.value})}
const dirty=computed(()=>initial.value!==''&&snapshot()!==initial.value)
function persist(){try{if(pending.value)sessionStorage.setItem(pendingKey,JSON.stringify(pending.value));else sessionStorage.removeItem(pendingKey)}catch{/* Original payload remains locked in memory. */}}
function restore(){try{const p=JSON.parse(sessionStorage.getItem(pendingKey)??'null') as Pending|null;if(p?.input?.requestId&&p.input.material){pending.value=p;Object.assign(form,p.input.material);links.value=p.input.suppliers??[];netPrice.value=p.input.material.costPrice?.netPrice??'';initial.value=snapshot()}}catch{/* Invalid storage ignored. */}}
let alive=true,supplierSequence=0
async function options(){optionsError.value='';try{const [t,u]=await Promise.all([materialTypesApi.list(),materialTypesApi.units()]);if(alive){types.value=t;units.value=u}}catch{if(alive)optionsError.value='类型/单位候选项读取失败，可重试或手动填写'}}
async function load(){loading.value=true;loadError.value='';try{if(props.id){const d=await materialManagementApi.detail(props.id);if(!alive)return;data.value=d;Object.assign(form,{...d.material,specification:d.material.specification??'',remark:d.material.remark??'',imageUrl:d.material.imageUrl??'',imageAssetId:d.material.imageAssetId??'',materialCode:props.copy?'':d.material.materialCode});links.value=d.suppliers.filter(s=>!props.copy||(s.status==='ACTIVE'&&s.materialEligible)).map(s=>({supplierId:s.supplierId,purchaseUrl:s.purchaseUrl,remark:s.remark,primary:s.primary}));names.value=Object.fromEntries(d.suppliers.map(s=>[s.supplierId,s]));netPrice.value=d.price?.netPrice??'';if(props.copy)form.name=d.material.name+'（副本）';}initial.value=snapshot()}catch(e){if(alive)loadError.value=e instanceof Error?e.message:'编辑资料加载失败'}finally{if(alive)loading.value=false}}
async function removeOption(kind:'type'|'unit',name:string){if(!await confirmAction(`从候选项移除“${name}”？已有资料不受影响。`))return;try{if(kind==='type')await materialTypesApi.remove(name);else await materialTypesApi.removeUnit(name);await options()}catch(e){error.value=e instanceof Error?e.message:'候选项移除失败'}}
async function searchSuppliers(reset=false){if(reset)supplierPage.value=1;const seq=++supplierSequence;supplierLoading.value=true;supplierError.value='';try{const r=await domainApi.suppliers({q:supplierQuery.value,status:'ACTIVE',category:'MATERIAL',page:supplierPage.value-1,size:20});if(!alive||seq!==supplierSequence)return;supplierOptions.value=r.items;supplierTotal.value=r.totalCount}catch(e){if(seq===supplierSequence)supplierError.value=e instanceof Error?e.message:'供应商搜索失败'}finally{if(seq===supplierSequence)supplierLoading.value=false}}
function addSupplier(event:Event){const id=(event.target as HTMLSelectElement).value,item=supplierOptions.value.find(s=>s.id===id);(event.target as HTMLSelectElement).value='';if(!item||links.value.some(l=>l.supplierId===id))return;names.value[id]=item;links.value.push({supplierId:id,primary:links.value.length===0})}
function supplierName(id:string){const item=names.value[id];return item?('supplierName' in item?item.supplierName:item.name):'供应商资料待确认'}
function primary(id:string){links.value.forEach(l=>l.primary=l.supplierId===id)}
function unlink(id:string){const was=links.value.find(l=>l.supplierId===id)?.primary;links.value=links.value.filter(l=>l.supplierId!==id);if(was&&links.value[0])links.value[0].primary=true}
function toggleNote(id:string){notes.value=notes.value.includes(id)?notes.value.filter(v=>v!==id):[...notes.value,id]}
async function upload(file:File){uploading.value=true;error.value='';try{const r=await catalogApi.uploadImage('MATERIAL_IMAGE',file);if(alive){form.imageAssetId=r.id;form.imageUrl=''}}catch(e){error.value=e instanceof Error?e.message:'上传失败';return false}finally{uploading.value=false}}
async function checkDuplicates(){duplicate.value='';if(editing.value||!form.name.trim())return;try{const r=await materialManagementApi.page({...emptyMaterialFilters(),q:form.name.trim()},0,20);if(alive&&r.items.some(r=>r.material.name===form.name.trim()&&(r.material.specification??'')===form.specification.trim()))duplicate.value='已有同名同规格辅料，请确认是否确需新增。'}catch{/* Optional hint does not block saving. */}}
async function submit(){
 if(busy.value||uploading.value||loading.value||loadError.value||conflict.value)return
 error.value=''
 if(!pending.value){
  if(!form.name.trim()||!form.materialType.trim()||!form.unit.trim()){error.value='请填写名称、类型和基础单位';return}
  const priceWasCleared=editing.value&&data.value?.price&&netPrice.value===''
  if(canWritePrice.value&&priceWasCleared){error.value='已有成本价不能通过清空删除，请填写有效价格';return}
  if(priceChanged.value&&(!/^\d{1,12}(\.\d{1,6})?$/.test(netPrice.value)||!confirmedNet.value||!priceReason.value.trim())){error.value='成本价最多六位小数，请确认人民币不含税口径并填写核定原因';return}
  const input:MaterialInput={materialCode:form.materialCode,name:form.name.trim(),materialType:form.materialType.trim(),unit:form.unit.trim(),specification:form.specification.trim(),remark:form.remark.trim(),...(form.imageAssetId?{imageAssetId:form.imageAssetId}:form.imageUrl.trim()?{imageUrl:form.imageUrl.trim()}:{})}
  if(priceChanged.value)input.costPrice={netPrice:netPrice.value,unit:form.unit,source:'MANUAL',referenceId:null,reason:priceReason.value.trim(),confirmedNet:true,version:editing.value?data.value?.price?.version??-1:-1}
  pending.value={input:{requestId:crypto.randomUUID(),id:editing.value?props.id??null:null,version:editing.value?data.value?.material.version??null:null,material:input,...(canEditSuppliers.value?{suppliers:links.value.map(l=>({...l}))}:{}),...(props.copy&&props.id?{copyFromId:props.id}:{})}};persist()
 }
 const command=pending.value;busy.value=true
 try{const saved=await materialManagementApi.save(command.input);pending.value=undefined;persist();emit('saved',saved);emit('close')}
 catch(e){error.value=e instanceof Error?e.message:'保存失败';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;persist();if(e.code==='MATERIAL_VERSION_CONFLICT'&&command.input.id){try{conflict.value=await materialManagementApi.detail(command.input.id)}catch{error.value+='；最新资料读取失败，草稿已保留，请再次保存以重新核对版本' }}}else error.value='保存结果待确认，原请求已保留，请重试确认。'+error.value}
 finally{busy.value=false}
}
function acceptLatest(){const latest=conflict.value;if(!latest)return;data.value=latest;Object.assign(form,{...latest.material,specification:latest.material.specification??'',remark:latest.material.remark??'',imageUrl:latest.material.imageUrl??'',imageAssetId:latest.material.imageAssetId??''});links.value=latest.suppliers.map(s=>({supplierId:s.supplierId,purchaseUrl:s.purchaseUrl,remark:s.remark,primary:s.primary}));names.value=Object.fromEntries(latest.suppliers.map(s=>[s.supplierId,s]));netPrice.value=latest.price?.netPrice??'';confirmedNet.value=false;conflict.value=undefined;error.value='';initial.value=snapshot()}
function keepDraft(){if(!conflict.value)return;data.value=conflict.value;conflict.value=undefined;error.value='已保留草稿并采用最新版本；请核对后再次保存。'}
function unload(e:BeforeUnloadEvent){if(dirty.value||busy.value||uploading.value||pending.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{void options();if(props.resume){restore();if(!pending.value)void load()}else void load();window.addEventListener('beforeunload',unload)})
onBeforeUnmount(()=>{alive=false;supplierSequence++;window.removeEventListener('beforeunload',unload)})
defineExpose({dirty,busy:computed(()=>busy.value||uploading.value),pending})
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="resume?'确认辅料保存':editing?'编辑辅料':copy?'复制辅料':'新增辅料'"
    wide
    dialog-class="materials-ui material-editor"
    :busy="busy||uploading"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <p v-if="loading">
      正在读取完整资料…
    </p><p
      v-if="loadError"
      role="alert"
    >
      {{ loadError }} <button @click="load">
        重新加载
      </button>
    </p>
    <p
      v-if="pending"
      class="material-warning"
      role="status"
    >
      保存结果待确认。内容已锁定，点击“确认原请求”不会重复创建。
    </p>
    <section
      v-if="conflict"
      class="material-conflict"
      role="alert"
    >
      <h3>资料已被其他人更新</h3><table class="data-table">
        <thead><tr><th>字段</th><th>我的草稿</th><th>最新资料</th></tr></thead><tbody>
          <tr
            v-for="field in (['name','materialType','specification','unit','remark'] as const)"
            :key="field"
          >
            <td>{{ ({name:'名称',materialType:'类型',specification:'规格',unit:'单位',remark:'备注'})[field] }}</td><td>{{ form[field] || '—' }}</td><td>{{ conflict.material[field] || '—' }}</td>
          </tr><tr>
            <td>图片</td><td>
              <MaterialImage
                :name="form.name"
                :image-asset-id="form.imageAssetId"
                :image-url="form.imageUrl"
              />
            </td><td>
              <MaterialImage
                :name="conflict.material.name"
                :image-asset-id="conflict.material.imageAssetId"
                :image-url="conflict.material.imageUrl"
              />
            </td>
          </tr><tr v-if="canReadPrice">
            <td>不含税成本价</td><td>{{ netPrice || '未维护' }} / {{ form.unit }}</td><td>{{ conflict.price ? `${conflict.price.netPrice} / ${conflict.price.unit}` : '未维护' }}</td>
          </tr><tr><td>供应商</td><td>{{ links.map(l=>supplierName(l.supplierId)).join('、')||'未关联' }}</td><td>{{ conflict.suppliers.map(s=>s.supplierName).join('、')||'未关联' }}</td></tr>
        </tbody>
      </table><button @click="acceptLatest">
        采用最新资料
      </button><button @click="keepDraft">
        保留草稿，重新核对
      </button>
    </section>
    <form
      id="material-editor-form"
      @submit.prevent="submit()"
    >
      <fieldset
        :disabled="locked"
        class="material-fields"
      >
        <div class="material-full material-image-edit">
          <MaterialImage
            :name="form.name||'辅料图片'"
            :image-asset-id="form.imageAssetId"
            :image-url="form.imageUrl"
          /><div>
            <SingleFileUpload
              title="辅料图片"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              compact
              :disabled="locked"
              :upload="upload"
              hint="图片立即上传，保存后关联"
            /><button
              v-if="form.imageAssetId||form.imageUrl"
              type="button"
              class="ui-image-remove"
              @click="form.imageAssetId='';form.imageUrl=''"
            >
              移除图片
            </button><details>
              <summary>使用图片链接</summary><label>HTTPS 图片地址<input
                v-model="form.imageUrl"
                maxlength="500"
                @input="form.imageAssetId=''"
              ></label>
            </details>
          </div>
        </div>
        <label>辅料名称<input
          v-model="form.name"
          required
          maxlength="200"
          @blur="checkDuplicates"
        ></label>
        <label>类型<EditableSelect
          v-model="form.materialType"
          :options="types"
          label="辅料类型"
          required
          :disabled="locked"
          @remove="removeOption('type',$event)"
        /></label>
        <label>规格<input
          v-model="form.specification"
          maxlength="300"
          @blur="checkDuplicates"
        ></label>
        <label>基础单位<EditableSelect
          v-model="form.unit"
          :options="units"
          label="辅料单位"
          required
          :disabled="locked||!!(editing&&data?.unitLocked)"
          @remove="removeOption('unit',$event)"
        /><small v-if="editing&&data?.unitLocked">已有业务记录，基础单位锁定；包装可在库存操作中换算。</small></label>
        <p
          v-if="optionsError"
          class="material-full"
          role="alert"
        >
          {{ optionsError }} <button
            type="button"
            @click="options"
          >
            重试
          </button>
        </p>
        <p
          v-if="duplicate"
          class="material-full material-warning"
        >
          {{ duplicate }}
        </p>
        <label v-if="canReadPrice">成本价（人民币 / {{ form.unit }}，不含税）<input
          v-model="netPrice"
          aria-label="辅料成本价"
          inputmode="decimal"
          maxlength="19"
          :disabled="!canWritePrice||(copy&&!keepCost)"
          placeholder="未填写表示未维护，最多六位小数"
        ></label>
        <label
          v-if="copy&&canWritePrice"
          class="material-check"
        ><input
          v-model="keepCost"
          type="checkbox"
        >沿用并重新核定来源成本价</label>
        <template v-if="priceChanged">
          <label>核定原因<input
            v-model="priceReason"
            required
            maxlength="500"
          ></label><label class="material-check material-full"><input
            v-model="confirmedNet"
            type="checkbox"
            required
          >确认以上为人民币不含税单价</label>
        </template>
        <label class="material-full">备注<textarea
          v-model="form.remark"
          maxlength="1000"
          rows="2"
        /></label>
      </fieldset>
      <section
        v-if="session.canAccess(['suppliers.read'])"
        class="material-relations"
      >
        <div class="material-section-heading">
          <h3>关联供应商 <small>{{ links.length }} 家</small></h3><button
            v-if="canEditSuppliers"
            type="button"
            :disabled="locked"
            :aria-expanded="supplierOpen"
            @click="supplierOpen=!supplierOpen;supplierOpen&&searchSuppliers(true)"
          >
            {{ supplierOpen?'收起选择':'添加供应商' }}
          </button>
        </div>
        <div
          v-if="supplierOpen"
          class="material-supplier-picker"
        >
          <div class="material-supplier-search">
            <input
              v-model="supplierQuery"
              type="search"
              aria-label="搜索供应商"
              placeholder="搜索供应商名称"
              :disabled="locked"
              @keydown.enter.prevent="searchSuppliers(true)"
            ><button
              type="button"
              :disabled="locked||supplierLoading"
              @click="searchSuppliers(true)"
            >
              {{ supplierLoading?'搜索中…':'搜索' }}
            </button>
          </div>
          <div class="material-supplier-choice">
            <select
              v-single-select
              class="ui-supplier-select"
              aria-label="添加辅料供应商"
              value=""
              :disabled="locked||supplierLoading||!!supplierError"
              @change="addSupplier"
            >
              <option value="">
                {{ supplierLoading?'正在加载…':'选择供应商并关联' }}
              </option><option
                v-for="s in supplierOptions.filter(s=>!links.some(l=>l.supplierId===s.id))"
                :key="s.id"
                :value="s.id"
              >
                {{ s.name }}
              </option>
            </select>
          </div>
          <p
            v-if="supplierError"
            role="alert"
            class="material-supplier-feedback"
          >
            {{ supplierError }}
          </p><p
            v-else-if="!supplierLoading&&!supplierTotal"
            class="material-supplier-feedback"
          >
            暂无匹配的启用供应商，请尝试其他关键词。
          </p>
          <ListPagination
            v-if="supplierTotal>20"
            :page="supplierPage"
            :page-size="20"
            :sizes="[20]"
            :total="supplierTotal"
            :disabled="supplierLoading||locked"
            @update:page="supplierPage=$event;searchSuppliers()"
          />
        </div>
        <p
          v-if="skippedSuppliers.length"
          class="material-warning"
        >
          复制时未带入已停用或不再供应辅料的供应商：{{ skippedSuppliers.map(s=>s.supplierName).join('、') }}。原辅料的历史关联仍保留。
        </p>
        <div class="material-table-wrap">
          <table
            v-if="links.length"
            class="data-table material-supplier-table"
          >
            <thead><tr><th>供应商</th><th>主供</th><th>采购 / 联系方式</th><th>操作</th></tr></thead><tbody>
              <template
                v-for="link in links"
                :key="link.supplierId"
              >
                <tr>
                  <td>
                    <button
                      type="button"
                      class="material-link"
                      @click="viewSupplier=link.supplierId"
                    >
                      {{ supplierName(link.supplierId) }}
                    </button><small v-if="names[link.supplierId]?.status==='DISABLED'">已停用 · 保留历史关联</small><small v-else-if="names[link.supplierId] && 'materialEligible' in names[link.supplierId]! && !(names[link.supplierId] as MaterialSupplier).materialEligible">已不再供应辅料 · 保留历史关联</small>
                  </td><td>
                    <input
                      type="radio"
                      name="material-primary"
                      :aria-label="`${supplierName(link.supplierId)}主供应商`"
                      :checked="link.primary"
                      :disabled="locked||!canEditSuppliers"
                      @change="primary(link.supplierId)"
                    >
                  </td><td>
                    <SupplierContactLinks
                      :supplier="names[link.supplierId]"
                      :purchase-url="link.purchaseUrl"
                    />
                  </td><td>
                    <button
                      type="button"
                      @click="toggleNote(link.supplierId)"
                    >
                      {{ link.remark?'已填备注':'备注/采购链接' }}
                    </button><button
                      v-if="canEditSuppliers"
                      type="button"
                      class="material-danger"
                      :disabled="locked"
                      @click="unlink(link.supplierId)"
                    >
                      解除
                    </button>
                  </td>
                </tr><tr v-if="notes.includes(link.supplierId)">
                  <td colspan="4">
                    <div class="material-fields">
                      <label>关系备注<input
                        v-model="link.remark"
                        maxlength="500"
                        :disabled="locked||!canEditSuppliers"
                      ></label><label>1688 采购链接<input
                        v-model="link.purchaseUrl"
                        maxlength="500"
                        :disabled="locked||!canEditSuppliers"
                      ></label>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table><p v-else>
            暂无关联供应商
          </p>
        </div>
      </section>
      <p
        v-if="error"
        role="alert"
        class="error-message"
      >
        {{ error }}
      </p>
    </form>
    <template #footer>
      <button
        type="button"
        :disabled="busy||uploading"
        @click="dialog?.requestClose()"
      >
        {{ pending?'稍后确认':'取消' }}
      </button><button
        class="ui-button-primary"
        type="submit"
        form="material-editor-form"
        :disabled="busy||uploading||loading||!!loadError||!!conflict"
      >
        {{ busy?'保存中…':pending?'确认原请求':'保存' }}
      </button>
    </template>
  </AccessDialog>
  <MaterialSupplierDetail
    v-if="viewSupplier"
    :id="viewSupplier"
    @close="viewSupplier=''"
  />
</template>
