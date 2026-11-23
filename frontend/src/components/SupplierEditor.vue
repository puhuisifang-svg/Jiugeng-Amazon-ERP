<script setup lang="ts">
import '@/styles/supplierManagement.css'
import {computed,onBeforeUnmount,onMounted,reactive,ref} from 'vue'
import {domainApi,type SupplierDto,type SupplierInput} from '@/api/domain'
import {supplierManagementApi as api,type SupplierSave,type SupplierImpact} from '@/api/supplierManagement'
import {catalogApi} from '@/api/catalog'
import {ApiError} from '@/api/client'
import {session} from '@/state/session'
import AccessDialog from './AccessDialog.vue'
import SingleFileUpload from './SingleFileUpload.vue'
import SupplierImage from './SupplierImage.vue'
import SupplierImpactSummary from './SupplierImpactSummary.vue'
import SupplierDetailDialog from './SupplierDetailDialog.vue'
import {vPhone} from '@/directives/phone'
import {vSingleSelect} from '@/directives/singleSelect'
const props=defineProps<{id?:string;copy?:boolean;resume?:boolean}>();const emit=defineEmits<{close:[];saved:[SupplierDto]}>()
const dialog=ref<InstanceType<typeof AccessDialog>>(),item=ref<SupplierDto>(),conflict=ref<SupplierDto>(),loading=ref(false),loadError=ref(''),busy=ref(false),uploading=ref(false),error=ref(''),initial=ref(''),impact=ref<SupplierImpact>(),impactConfirmed=ref(false),confirmCopy=ref(false),duplicates=ref<SupplierDto[]>([]),duplicateError=ref(''),viewId=ref('')
const blank=():SupplierInput=>({name:'',contactPerson:'',phone:'',wechatId:'',sourceUrl:'',qrImageId:'',qrImageUrl:'',remark:'',status:'ACTIVE',categories:[]})
const form=reactive(blank()),continuing=ref(false)
const activeId=ref(props.id),copySourceId=ref(props.copy?props.id:undefined)
const editing=computed(()=>!!activeId.value&&!copySourceId.value&&!continuing.value),copying=computed(()=>!!copySourceId.value&&!continuing.value)
const key=`jiugeng.supplier.pending.${session.user.value?.id??'current'}`
interface Pending {input:SupplierSave;again:boolean}
const pending=ref<Pending>(),dirty=computed(()=>!!initial.value&&JSON.stringify(form)!==initial.value),locked=computed(()=>loading.value||!!loadError.value||busy.value||uploading.value||!!pending.value||!!conflict.value)
const impactful=computed(()=>editing.value&&item.value&&((item.value.status==='ACTIVE'&&form.status==='DISABLED')||item.value.categories?.some(c=>!form.categories?.includes(c))))
let alive=true,duplicateSeq=0
function persist(){try{if(pending.value)sessionStorage.setItem(key,JSON.stringify(pending.value));else sessionStorage.removeItem(key)}catch{/* In-memory original remains available. */}}
function fill(value:SupplierDto){item.value=value;Object.assign(form,blank(),value,{categories:[...(value.categories??[])]});if(copying.value){form.name=value.name+'（副本）';form.status='ACTIVE';form.version=undefined}initial.value=JSON.stringify(form)}
async function load(){loading.value=true;loadError.value='';try{if(props.id){const value=await domainApi.supplier(props.id);if(alive)fill(value)}else initial.value=JSON.stringify(form)}catch(e){if(alive)loadError.value=e instanceof Error?e.message:'完整资料读取失败'}finally{if(alive)loading.value=false}}
async function upload(file:File){if(locked.value)return false;uploading.value=true;error.value='';try{const result=await catalogApi.uploadImage('SUPPLIER_QR',file);if(alive){form.qrImageId=result.id;form.qrImageUrl=''}}catch(e){if(alive)error.value=e instanceof Error?e.message:'图片上传失败';return false}finally{uploading.value=false}}
async function checkDuplicates(){if(editing.value||locked.value)return;const seq=++duplicateSeq;duplicateError.value='';try{const result=await api.duplicates(form.name.trim(),form.phone?.trim()??'',form.wechatId?.trim()??'');if(alive&&seq===duplicateSeq)duplicates.value=result}catch{if(alive&&seq===duplicateSeq)duplicateError.value='重复资料检查失败，可重试；不影响保存'}}
async function loadImpact(){loading.value=true;error.value='';impactConfirmed.value=false;try{impact.value=await api.impact(activeId.value!)}catch(e){error.value=e instanceof Error?e.message:'影响范围读取失败'}finally{loading.value=false}}
function input():SupplierInput{return {name:form.name.trim(),contactPerson:form.contactPerson?.trim(),phone:form.phone?.trim(),wechatId:form.wechatId?.trim(),sourceUrl:form.sourceUrl?.trim(),qrImageId:form.qrImageId||undefined,qrImageUrl:form.qrImageUrl||undefined,remark:form.remark?.trim(),status:editing.value?form.status:'ACTIVE',categories:[...(form.categories??[])],...(editing.value?{version:item.value?.version}:{})}}
async function save(again=false){
 if(busy.value||uploading.value||loading.value||loadError.value||conflict.value)return;error.value=''
 if(!pending.value){
  if(!form.name.trim()||!form.categories?.length){error.value='请填写供应商名称并选择至少一个分类';return}
  if(![form.phone,form.wechatId,form.sourceUrl,form.qrImageId,form.qrImageUrl].some(v=>v?.trim())){error.value='电话、微信号、1688 链接、联系图片至少填写一项';return}
  if(form.sourceUrl){try{const url=new URL(form.sourceUrl);if(url.protocol!=='https:'||url.username||url.password||!(url.hostname==='1688.com'||url.hostname.endsWith('.1688.com')))throw new Error()}catch{error.value='采购来源须为 1688.com 或其子域的 HTTPS 链接';return}}
  if(copying.value&&!confirmCopy.value){error.value='请核对副本名称和联系方式，并勾选确认';return}
  if(impactful.value&&!impactConfirmed.value){await loadImpact();return}
  pending.value={input:{requestId:crypto.randomUUID(),id:editing.value?activeId.value??null:null,supplier:input(),impactConfirmed:!!impactful.value&&impactConfirmed.value,...(copying.value?{copyFromId:copySourceId.value!}:{})},again};persist()
 }
 const command=pending.value;if(!command)return;busy.value=true
 try{const saved=await api.save(command.input);pending.value=undefined;persist();emit('saved',saved);if(command.again){const categories=[...(form.categories??[])];continuing.value=true;Object.assign(form,blank(),{categories});item.value=undefined;impact.value=undefined;impactConfirmed.value=false;confirmCopy.value=false;duplicates.value=[];initial.value=JSON.stringify(form)}else emit('close')}
 catch(e){error.value=e instanceof Error?e.message:'保存失败';if(e instanceof ApiError&&e.status<500&&![401,403,408,429].includes(e.status)){pending.value=undefined;persist();if(e.code==='SUPPLIER_VERSION_CONFLICT'&&command.input.id){try{conflict.value=await domainApi.supplier(command.input.id)}catch{error.value+='；最新资料读取失败，草稿保留，请重新保存以核对版本'}}if(e.code==='SUPPLIER_IMPACT_CONFIRM_REQUIRED')impactConfirmed.value=false}else error.value='保存结果待确认，请使用原请求重试。'+error.value}
 finally{busy.value=false}
}
function latest(){if(conflict.value)fill(conflict.value);conflict.value=undefined;impact.value=undefined;impactConfirmed.value=false;error.value=''}
function keep(){if(conflict.value)item.value=conflict.value;conflict.value=undefined;impact.value=undefined;impactConfirmed.value=false;error.value='已保留草稿，请核对后再次保存'}
function unload(e:BeforeUnloadEvent){if(dirty.value||busy.value||uploading.value||pending.value){e.preventDefault();e.returnValue=''}}
onMounted(()=>{if(props.resume){try{const value=JSON.parse(sessionStorage.getItem(key)??'null') as Pending|null;if(value?.input?.requestId&&value.input.supplier){pending.value=value;activeId.value=value.input.id??undefined;copySourceId.value=value.input.copyFromId;if(value.input.id)item.value={id:value.input.id,name:value.input.supplier.name,status:value.input.supplier.status,categories:value.input.supplier.categories??[],version:value.input.supplier.version??0};Object.assign(form,value.input.supplier);initial.value=JSON.stringify(form)}}catch{/* Ignore malformed cache. */}}if(!pending.value)void load();window.addEventListener('beforeunload',unload)})
onBeforeUnmount(()=>{alive=false;duplicateSeq++;window.removeEventListener('beforeunload',unload)})
defineExpose({dirty,busy:computed(()=>busy.value||uploading.value),pending})
</script>
<template>
  <AccessDialog
    ref="dialog"
    :title="resume?'确认供应商保存':editing?'编辑供应商':copying?'复制供应商':'新增供应商'"
    wide
    dialog-class="supplier-ui supplier-editor"
    :busy="busy||uploading"
    :dirty="dirty&&!pending"
    @close="emit('close')"
  >
    <p v-if="loading">
      正在读取…
    </p><p
      v-if="loadError"
      role="alert"
    >
      {{ loadError }} <button @click="load">
        重新加载
      </button>
    </p><p
      v-if="pending"
      class="supplier-warning"
    >
      保存结果待确认，内容已锁定。点击“确认原请求”不会重复创建。
    </p><section
      v-if="conflict"
      class="supplier-warning"
    >
      <h3>资料已被其他人更新</h3><div class="supplier-table-wrap">
        <table class="data-table">
          <thead><tr><th>字段</th><th>我的草稿</th><th>最新资料</th></tr></thead><tbody>
            <tr
              v-for="field in (['name','contactPerson','phone','wechatId','sourceUrl','remark'] as const)"
              :key="field"
            >
              <td>{{ ({name:'名称',contactPerson:'联系人',phone:'电话',wechatId:'微信',sourceUrl:'采购来源',remark:'备注'})[field] }}</td><td>{{ form[field]||'—' }}</td><td>{{ conflict[field]||'—' }}</td>
            </tr><tr><td>状态 / 分类</td><td>{{ form.status==='ACTIVE'?'启用':'停用' }} / {{ form.categories?.map(c=>c==='PRODUCT'?'产品':'辅料').join('、') }}</td><td>{{ conflict.status==='ACTIVE'?'启用':'停用' }} / {{ conflict.categories?.map(c=>c==='PRODUCT'?'产品':'辅料').join('、') }}</td></tr><tr>
              <td>联系图片</td><td>
                <SupplierImage
                  :name="form.name"
                  :image-asset-id="form.qrImageId"
                  :image-url="form.qrImageUrl"
                />
              </td><td>
                <SupplierImage
                  :name="conflict.name"
                  :image-asset-id="conflict.qrImageId"
                  :image-url="conflict.qrImageUrl"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div><button @click="latest">
        采用最新资料
      </button><button @click="keep">
        保留草稿，重新核对
      </button>
    </section><form
      id="supplier-editor-form"
      @submit.prevent="save()"
    >
      <fieldset
        :disabled="locked"
        class="supplier-fields"
      >
        <label>供应商名称<input
          v-model="form.name"
          required
          maxlength="128"
          @blur="checkDuplicates"
        ></label><label>联系人<input
          v-model="form.contactPerson"
          maxlength="64"
        ></label><label>电话<input
          v-model="form.phone"
          v-phone
          maxlength="32"
          @blur="checkDuplicates"
        ></label><label>微信号<input
          v-model="form.wechatId"
          maxlength="100"
          autocomplete="off"
          @blur="checkDuplicates"
        ></label><label class="supplier-full">1688 采购来源<input
          v-model="form.sourceUrl"
          type="url"
          maxlength="500"
          placeholder="https://detail.1688.com/..."
        ></label><div class="supplier-full supplier-inline">
          <span>供应商分类 *</span><label><input
            v-model="form.categories"
            type="checkbox"
            value="MATERIAL"
            @change="impactConfirmed=false;impact=undefined"
          >辅料</label><label><input
            v-model="form.categories"
            type="checkbox"
            value="PRODUCT"
            @change="impactConfirmed=false;impact=undefined"
          >产品</label><label v-if="editing">状态<select
            v-model="form.status"
            v-single-select
            @change="impactConfirmed=false;impact=undefined"
          ><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
        </div><div class="supplier-full supplier-image-editor">
          <SupplierImage
            :name="form.name||'联系图片'"
            :image-asset-id="form.qrImageId"
            :image-url="form.qrImageUrl"
          /><div>
            <SingleFileUpload
              compact
              title="联系图片 / 二维码"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="locked"
              :upload="upload"
              hint="图片立即上传，保存后关联"
            /><button
              v-if="form.qrImageId||form.qrImageUrl"
              type="button"
              class="supplier-link"
              @click="form.qrImageId='';form.qrImageUrl=''"
            >
              移除图片
            </button>
          </div>
        </div><small class="supplier-full">电话、微信号、采购来源、联系图片至少填写一项。</small><label class="supplier-full">备注<textarea
          v-model="form.remark"
          rows="2"
          maxlength="500"
        /></label><label
          v-if="copying"
          class="supplier-full supplier-check"
        ><input
          v-model="confirmCopy"
          type="checkbox"
        >已核对副本名称和联系方式。复制不会带入产品、辅料或采购关联。</label>
      </fieldset><div
        v-if="duplicates.length&&!editing"
        class="supplier-warning"
      >
        发现可能重复的供应商（最多显示 10 条），请确认是否仍需新增：<button
          v-for="d in duplicates"
          :key="d.id"
          type="button"
          class="supplier-link"
          @click="viewId=d.id"
        >
          {{ d.name }}
        </button>
      </div><p v-if="duplicateError">
        {{ duplicateError }} <button
          type="button"
          @click="checkDuplicates"
        >
          重试检查
        </button>
      </p><section
        v-if="impact&&impactful"
        class="supplier-warning"
      >
        <SupplierImpactSummary :impact="impact" /><label class="supplier-check"><input
          v-model="impactConfirmed"
          type="checkbox"
          :disabled="locked"
        >已了解影响，确认本次停用或分类变更</label>
      </section><p
        v-if="error"
        role="alert"
        class="error-message"
      >
        {{ error }}
      </p>
    </form><template #footer>
      <button
        :disabled="busy||uploading"
        @click="dialog?.requestClose()"
      >
        {{ pending?'稍后确认':'取消' }}
      </button><button
        v-if="!editing&&!pending&&!resume"
        :disabled="locked"
        @click="save(true)"
      >
        保存并继续
      </button><button
        type="submit"
        form="supplier-editor-form"
        class="ui-button-primary"
        :disabled="busy||uploading||loading||!!loadError||!!conflict"
      >
        {{ busy?'保存中…':pending?'确认原请求':'保存供应商' }}
      </button>
    </template>
  </AccessDialog><SupplierDetailDialog
    v-if="viewId"
    :id="viewId"
    @close="viewId=''"
  />
</template>
