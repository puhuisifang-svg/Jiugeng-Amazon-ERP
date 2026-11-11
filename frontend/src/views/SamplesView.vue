<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { samplesApi, type SampleLocation } from '@/api/samples'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import PurchaseItemImage from '@/components/PurchaseItemImage.vue'
import { catalogApi } from '@/api/catalog'
import ShipmentProductImage from '@/components/ShipmentProductImage.vue'
import CustomerSkuPicker from '@/components/CustomerSkuPicker.vue'
import { type SkuListItemDto } from '@/api/domain'
import { session } from '@/state/session'
import { confirmAction } from '@/state/uiConfirmation'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import UnsavedChangesDialog from '@/components/UnsavedChangesDialog.vue'
import { runtimeConfig } from '@/config/runtime'
const writable=computed(()=>runtimeConfig.authMode==='password'&&session.canAccess(['samples.write']))
const rows=ref<SampleLocation[]>([]),query=ref(''),applied=ref(''),page=ref(1),size=ref(20),total=ref(0),loading=ref(false),error=ref(''),success=ref('')
const dialog=ref<HTMLDialogElement>(),locationInput=ref<HTMLInputElement>(),open=ref(false),busy=ref(false),editing=ref<SampleLocation>(),location=ref(''),selected=ref<SkuListItemDto>(),initial=ref(''),formError=ref(''),discard=ref(false)
const skuPickerOpen=ref(false)
const parents=ref<{id:string;name:string}[]>([]),parent=ref(''),appliedParent=ref(''),parentError=ref('')
const manual=ref(false),sampleName=ref(''),sampleCode=ref(''),specification=ref(''),remark=ref(''),sampleImageId=ref<string|null>(null),storageImageId=ref<string|null>(null),uploading=ref(false)
const blocked=computed(()=>busy.value||uploading.value)
async function loadParents(){try{parents.value=await samplesApi.parents();parentError.value=''}catch{parentError.value='父体选项加载失败，请重试'}}
async function uploadStorage(file:File){uploading.value=true;formError.value='';try{storageImageId.value=(await catalogApi.uploadImage('SAMPLE_STORAGE',file)).id}catch(e){formError.value=message(e);throw e}finally{uploading.value=false}}
async function uploadSample(file:File){uploading.value=true;formError.value='';try{sampleImageId.value=(await catalogApi.uploadImage('SAMPLE_IMAGE',file)).id}catch(e){formError.value=message(e);throw e}finally{uploading.value=false}}
let listSequence=0
const snapshot=()=>JSON.stringify([selected.value?.variant.id??'',location.value,manual.value,sampleName.value,sampleCode.value,specification.value,remark.value,storageImageId.value,sampleImageId.value])
const dirty=computed(()=>open.value&&snapshot()!==initial.value)
async function load(){if(runtimeConfig.authMode!=='password'){error.value='样品库位管理仅在正式登录模式使用';return}const sequence=++listSequence;loading.value=true;error.value='';try{const result=await samplesApi.list(applied.value,page.value-1,size.value,appliedParent.value);if(sequence===listSequence){rows.value=result.items;total.value=result.totalCount;if(page.value>1&&!rows.value.length){page.value--;await load()}}}catch(e){if(sequence===listSequence)error.value=message(e)}finally{if(sequence===listSequence)loading.value=false}}
function search(){applied.value=query.value;appliedParent.value=parent.value;page.value=1;void load()}
function reset(){query.value='';parent.value='';search()}
async function edit(row?:SampleLocation){editing.value=row;manual.value=!!row&&!row.variantId;sampleName.value=row?.skuName??'';sampleCode.value=row?.skuCode??'';specification.value=row?.specification??'';remark.value=row?.remark??'';storageImageId.value=row?.storageImageId??null;sampleImageId.value=row?.sampleImageId??null;location.value=row?.locationCode??'';selected.value=undefined;formError.value='';skuPickerOpen.value=false;open.value=true;initial.value=snapshot();await nextTick();dialog.value?.showModal();if(row)locationInput.value?.focus();else{dialog.value?.querySelector<HTMLInputElement>('input')?.focus()}}
function close(){skuPickerOpen.value=false;dialog.value?.close();open.value=false;discard.value=false}
function requestClose(){if(blocked.value)return;if(dirty.value){discard.value=true;return}close()}
async function save(){
 if(blocked.value)return
 const productId=manual.value?null:(editing.value?.productId??selected.value?.productId),variantId=manual.value?null:(editing.value?.variantId??selected.value?.variant.id)
 if(!manual.value&&(!productId||!variantId)){formError.value='请先选择 SKU';return}
 if(manual.value&&!sampleName.value.trim()){formError.value='请输入样品名称';return}
 if(!location.value.trim()){formError.value='请输入库位编码';return}
 busy.value=true;formError.value=''
 try{const input={productId:productId??null,variantId:variantId??null,locationCode:location.value,sampleName:sampleName.value,sampleCode:sampleCode.value,specification:specification.value,remark:remark.value,storageImageId:storageImageId.value,sampleImageId:manual.value?sampleImageId.value:null,...(editing.value?{version:editing.value.version}:{})};if(editing.value)await samplesApi.update(editing.value.id,input);else await samplesApi.create(input);close();success.value='样品库位已保存';await Promise.all([load(),loadParents()])}catch(e){formError.value=message(e)}finally{busy.value=false}
}
async function remove(row:SampleLocation){if(busy.value||!await confirmAction(`移除 ${row.skuCode || row.skuName} 在 ${row.locationCode} 的样品库位登记？不会删除产品或 SKU。`))return;busy.value=true;error.value='';try{await samplesApi.remove(row.id,row.version);success.value='样品库位登记已移除';await Promise.all([load(),loadParents()])}catch(e){error.value=message(e)}finally{busy.value=false}}
function message(e:unknown){return e instanceof Error?e.message:'操作失败，请重试'}
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value||blocked.value){e.preventDefault();e.returnValue=''}}
onBeforeRouteLeave(async()=>blocked.value?false:!dirty.value||await confirmAction('样品库位尚未保存，确定放弃并离开吗？'))
onMounted(()=>{void load();void loadParents();window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{++listSequence;skuPickerOpen.value=false;window.removeEventListener('beforeunload',beforeUnload);dialog.value?.close()})
</script>
<template>
  <section class="page-stack samples-ui">
    <SuccessToast :message="success" />
    <form
      class="filter-panel ui-filter-row"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="query"
        maxlength="200"
        placeholder="品名、SKU 或库位编码"
      ></label>
      <label>所属父体<select v-model="parent"><option value="">全部父体</option><option
        v-for="p in parents"
        :key="p.id"
        :value="p.id"
      >{{ p.name }}</option></select></label>
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
    </form>
    <p
      v-if="parentError"
      role="alert"
      class="access-danger"
    >
      {{ parentError }} <button
        type="button"
        @click="loadParents"
      >
        重试
      </button>
    </p>
    <div class="material-toolbar">
      <button
        v-if="writable"
        type="button"
        class="primary-action"
        :disabled="blocked"
        @click="edit()"
      >
        新增样品
      </button><span class="muted">关联 SKU 或手动录入，登记库位与存放照片</span>
    </div>
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }}
    </p>
    <TableViewport>
      <table class="data-table ui-action-table sample-table">
        <thead><tr><th>图片</th><th>品名 / SKU 编码</th><th>所属产品</th><th>库位编码</th><th>存放照片</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
          >
            <td>
              <ShipmentProductImage
                v-if="row.productId && row.variantId"
                :product-id="row.productId!"
                :variant-id="row.variantId"
                :alt="row.skuName"
              />
              <PurchaseItemImage
                v-else
                :image-asset-id="row.sampleImageId"
                :alt="row.skuName"
              />
            </td><td>
              <strong>{{ row.skuName }}</strong><small class="muted">{{ row.skuCode || '无编码' }}</small><small
                v-if="row.specification"
                class="muted"
              >{{ row.specification }}</small>
            </td><td>{{ row.productName || '手动录入' }}</td><td><strong class="sample-location-code">{{ row.locationCode }}</strong></td><td>
              <PurchaseItemImage
                v-if="row.storageImageId"
                :image-asset-id="row.storageImageId"
                :alt="`${row.skuName} · ${row.locationCode} 存放照片`"
              /><span
                v-else
                class="muted"
              >未上传</span>
            </td><td>{{ new Date(row.updatedAt).toLocaleString('zh-CN',{hour12:false}) }}</td><td>
              <template v-if="writable || session.canAccess(['samples.remove'])">
                <button
                  v-if="session.canAccess(['samples.write'])"
                  type="button"
                  class="text-action"
                  :disabled="blocked"
                  @click="edit(row)"
                >
                  编辑
                </button><button
                  v-if="session.canAccess(['samples.remove'])"
                  type="button"
                  class="text-action danger"
                  :disabled="blocked"
                  @click="remove(row)"
                >
                  移除
                </button>
              </template><span
                v-else
                class="muted"
              >只读</span>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td
              colspan="7"
              class="data-state"
            >
              {{ loading?'正在加载…':'暂无样品库位记录' }}
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
      @update:page="page=$event;load()"
      @update:page-size="size=$event;page=1;load()"
    />
    <dialog
      ref="dialog"
      class="sample-dialog"
      aria-labelledby="sample-editor-title"
      @cancel.prevent="requestClose"
    >
      <form
        v-if="open"
        @submit.prevent="save"
      >
        <header>
          <h2 id="sample-editor-title">
            {{ editing?'编辑样品库位':'新增样品' }}
          </h2><button
            type="button"
            class="secondary-action"
            :disabled="blocked"
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
            class="data-state data-state--error"
          >
            {{ formError }}
          </p>
          <div
            v-if="!editing"
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
            </button>
            <button
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
            <label>样品编码<input
              v-model="sampleCode"
              maxlength="64"
              :disabled="blocked"
              placeholder="选填，如供应商货号"
            ></label>
          </div>
          <div
            v-if="editing && !manual"
            class="selected-sku"
          >
            <strong>{{ editing.skuName }}</strong><span>{{ editing.skuCode }}</span>
          </div>
          <template v-else-if="!editing && !manual">
            <button
              type="button"
              class="sample-sku-picker-button"
              :disabled="blocked"
              @click="skuPickerOpen = true"
            >
              {{ selected ? '更换 SKU' : '选择产品 SKU' }}
            </button>
            <p class="selected-sku">
              已选：{{ selected ? `${selected.variant.variantName} / ${selected.variant.skuCode}` : '尚未选择 SKU' }}
            </p>
          </template>
          <label class="location-field">库位编码 <span class="muted">必填</span><input
            ref="locationInput"
            v-model="location"
            maxlength="100"
            placeholder="例如 A-01-02"
            :disabled="blocked"
            required
          ></label>
          <label class="location-field">规格说明<input
            v-model="specification"
            maxlength="500"
            :disabled="blocked"
            placeholder="选填，如颜色、尺寸"
          ></label>
          <label class="location-field">备注<input
            v-model="remark"
            maxlength="1000"
            :disabled="blocked"
            placeholder="选填"
          ></label>
          <section
            v-if="manual"
            class="sample-storage-editor"
          >
            <div
              v-if="sampleImageId"
              class="sample-storage-preview"
            >
              <PurchaseItemImage
                :image-asset-id="sampleImageId"
                alt="样品图片"
              /><button
                type="button"
                class="ui-image-remove"
                :disabled="blocked"
                @click="sampleImageId=null"
              >
                移除图片
              </button>
            </div>
            <SingleFileUpload
              title="样品图片"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="blocked"
              :upload="uploadSample"
              hint="上传样品本身的图片，支持选择或粘贴；与库位存放照片分开保存。"
            />
          </section>
          <section class="sample-storage-editor">
            <div
              v-if="storageImageId"
              class="sample-storage-preview"
            >
              <PurchaseItemImage
                :image-asset-id="storageImageId"
                alt="样品存放照片"
              /><button
                type="button"
                class="ui-image-remove"
                :disabled="blocked"
                @click="storageImageId=null"
              >
                移除照片
              </button>
            </div>
            <SingleFileUpload
              title="存放照片"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="blocked"
              :upload="uploadStorage"
              hint="拍摄样品及周边库位，支持选择或粘贴图片；保存后关联到本条样品。"
            />
          </section>
          <p
            v-if="!manual"
            class="muted"
          >
            同一 SKU 可登记多个库位，同一库位不能重复登记该 SKU。
          </p>
        </div>
        <footer class="ui-dialog-actions">
          <button
            type="button"
            class="secondary-action"
            :disabled="blocked"
            @click="requestClose"
          >
            取消
          </button><button
            type="submit"
            class="primary-action"
            :disabled="blocked"
          >
            {{ busy?'保存中…':'保存' }}
          </button>
        </footer>
      </form>
    </dialog>
    <CustomerSkuPicker
      v-if="skuPickerOpen"
      :selected-id="selected?.variant.id ?? ''"
      :active-only="false"
      @close="skuPickerOpen = false"
      @select="selected = $event; skuPickerOpen = false"
    />
    <UnsavedChangesDialog
      v-if="discard"
      @keep="discard=false"
      @discard="close"
    />
  </section>
</template>
<style scoped>
.sample-location-code { color: #dc2626; }
.sample-manual-fields{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:12px}.sample-manual-fields label{display:grid;gap:6px}.sample-source{margin-bottom:12px}.sample-storage-editor{margin-top:16px;display:grid;gap:10px}.sample-storage-preview{display:flex;align-items:center;gap:12px}.required{color:#b42318}@media(max-width:600px){.sample-manual-fields{grid-template-columns:1fr}}
#app .samples-ui .sample-sku-picker-button{display:inline-flex;align-items:center;justify-content:center;min-height:36px;padding:6px 14px;background:#2864c5;color:white;border:1px solid #2864c5;border-radius:5px;font-size:13px;cursor:pointer}.sample-sku-picker-button:disabled{opacity:.5}

.samples-ui .filter-panel label{flex:0 1 360px;min-width:220px}.sample-table{min-width:800px;width:100%}.sample-table small,.sku-options small{display:block}.sample-table .text-action{background:transparent;border:0;color:var(--accent);padding:0 8px;min-height:32px;height:32px;cursor:pointer}.sample-table .text-action.danger{color:#b42318}.sample-table th:last-child,.sample-table td:last-child{width:128px;min-width:128px}
.sample-dialog{border:0;border-radius:12px;padding:0;width:min(820px,calc(100vw - 32px));max-width:calc(100vw - 32px);max-height:calc(100dvh - 32px);overflow:hidden}.sample-dialog::backdrop{background:#17203366}
.sample-dialog form{display:flex;flex-direction:column;max-height:calc(100dvh - 32px);min-width:0}.sample-dialog header,.sample-dialog footer{display:flex;align-items:center;gap:8px;padding:16px;flex-shrink:0}.sample-dialog header{justify-content:space-between;border-bottom:1px solid var(--line)}.sample-dialog footer button{min-height:40px;padding:0 18px;border:1px solid var(--line);border-radius:6px;cursor:pointer}.sample-dialog footer .primary-action{background:var(--accent);color:white;border-color:var(--accent)}.sample-dialog footer button:disabled{opacity:.6;cursor:default}.sample-dialog h2{margin:0;font-size:18px}.sample-dialog footer{justify-content:flex-end;border-top:1px solid var(--line)}.sample-dialog-body{overflow:auto;padding:16px;min-height:0}
.sku-search{display:flex;align-items:end;gap:8px}.sku-search label{flex:1;min-width:0;display:grid;gap:4px}.sample-dialog input:not([type=radio]){width:100%;min-width:0;box-sizing:border-box;height:40px;border:1px solid var(--line);border-radius:6px;padding:0 10px}.sku-options{overflow:auto;max-height:260px;margin-top:8px}.sku-options table{width:100%;min-width:480px}.sku-options input[type=radio]{width:24px;height:24px}.sku-pages{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:8px 0}.sku-pages button{min-height:32px}.selected-sku{display:flex;flex-wrap:wrap;gap:8px;background:#f5f7fa;padding:10px;border-radius:6px}.location-field{display:block;margin-top:12px}.location-field input{display:block;margin-top:6px}
@media(max-width:600px){.samples-ui .filter-panel label{flex:1 1 100%;min-width:0}.sample-dialog{width:calc(100vw - 16px);max-width:calc(100vw - 16px)}}
</style>
