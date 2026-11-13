<script setup lang="ts">
import EditableSelect from '@/components/EditableSelect.vue'
import { materialTypesApi } from '@/api/materialTypes'
import { costsApi, type MaterialPrice } from '@/api/costs'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { confirmAction } from '@/state/uiConfirmation'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import { catalogApi } from '@/api/catalog'
import { domainApi, type InventoryBalanceViewDto, type InventoryCommandType, type MaterialDetailDto, type MaterialInput, type SupplierAssignmentDto, type SupplierDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SupplierContactLinks from '@/components/SupplierContactLinks.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import ListPagination from '@/components/ListPagination.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'
import { isNonNegativeInventoryInteger, isPositiveInventoryInteger } from '@/ui/inventory'

const items = ref<MaterialDetailDto[]>([])
const prices=ref<Record<string,MaterialPrice>>({}),priceError=ref(''),priceLoading=ref(false)
const netPrice=ref(''),originalPrice=ref<MaterialPrice|null>(null),formPriceReady=ref(true)
const canReadPrice=computed(()=>formal.value && session.canAccess(['costs.read']))
const canWritePrice=computed(()=>canReadPrice.value && session.canAccess(['costs.write','materials.write']))
const priceChanged=computed(()=>(originalPrice.value ? netPrice.value==='' || Number(netPrice.value)!==Number(Number(originalPrice.value.netPrice).toFixed(2)) : netPrice.value!=='') || (!!originalPrice.value && form.unit!==originalPrice.value.unit))
async function loadPrices(){if(!canReadPrice.value)return;priceLoading.value=true;priceError.value='';try{prices.value=Object.fromEntries((await costsApi.prices()).map(p=>[p.materialId,p]))}catch{priceError.value='成本价加载失败，请重试';}finally{priceLoading.value=false}}
function priceText(item:MaterialDetailDto){if(!canReadPrice.value)return '无查看权限';if(priceError.value)return '加载失败';if(priceLoading.value)return '加载中…';const price=prices.value[item.id];return !price?'未维护':price.unit!==item.unit?'单位已变，请核定':`¥ ${Number(price.netPrice).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} / ${price.unit}`}
function unlinkSupplier(id:string){const primary=supplierLinks.value.find(l=>l.supplierId===id)?.primary;supplierLinks.value=supplierLinks.value.filter(l=>l.supplierId!==id);if(primary&&supplierLinks.value[0])supplierLinks.value[0].primary=true}

const balances = ref<InventoryBalanceViewDto[]>([])
const formError = ref('')
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const editingId = ref<string | null>(null); const showMaterialForm = ref(false); const showInventoryForm = ref(false)
const supplierOptions = ref<SupplierDto[]>([]); const supplierLinks = ref<SupplierAssignmentDto[]>([])
const form = reactive({ materialCode: '', name: '', materialType: '', specification: '', unit: '件', imageUrl: '', imageAssetId: '', remark: '' })
const filters = reactive({ keyword: '', materialType: '', status: '', stock: '' })
const inventoryForm = reactive<{ materialId: string; materialCode: string; type: InventoryCommandType | 'OPENING' | 'ADJUSTMENT'; quantity: number; reason: string }>({
  materialId: '', materialCode: '', type: 'INBOUND', quantity: 0, reason: '日常库存操作',
})
const safetyValues = ref<Record<string, number>>({})
const uploading = ref(false); const imageError = ref('')
const failedRemoteImages = ref<Set<string>>(new Set())
type MaterialImagePreview = ({ alt: string; imageUrl: string } | { alt: string; imageAssetId: string }) & { kind?: 'material' | 'supplier-qr' }
const imagePreview = ref<MaterialImagePreview | null>(null)
const unitOptions = ref<string[]>(['件','个','套'])
const writable = computed(() => session.canAccess(['materials.write']))
const formal = computed(() => runtimeConfig.authMode === 'password')
const mockMode = computed(() => runtimeConfig.authMode === 'mock')
const inventoryReadable = computed(() => session.canAccess(['inventory.read']))
const inventoryWritable = computed(() => inventoryReadable.value && (mockMode.value || session.canAccess(['inventory.inbound']) || session.canAccess(['inventory.outbound'])))
const deleteIds = ref<string[]>([])
function requestDelete(ids:string[]) { deleteIds.value=[...ids]; deleteError.value=''; deleteOpen.value=true }
const selectedIds = ref<string[]>([]); const deleteOpen = ref(false); const deleteError = ref('')
const page = ref(1); const pageSize = ref(20); const inventoryError = ref('')
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
const supplierWritable = computed(() => writable.value && session.canAccess(['suppliers.read', 'suppliers.write']))
const rowActionsAvailable = computed(() => writable.value || inventoryWritable.value || (formal.value && (session.canAccess(['purchases.read', 'purchases.write', 'suppliers.read']))))
const activeMaterials = computed(() => items.value.filter(item => item.status === 'ACTIVE'))
const selectedMaterial = computed(() => items.value.find(item => item.id === inventoryForm.materialId))
const balanceByMaterial = computed(() => new Map(balances.value.map(item => [item.materialId ?? item.materialCode, item])))
const selectableTypes=ref<string[]>([])
async function removeUnit(name:string){
  if(!await confirmAction(`从候选项中删除单位“${name}”？已有辅料资料不受影响。`))return
  try{await materialTypesApi.removeUnit(name);unitOptions.value=unitOptions.value.filter(v=>v!==name)}catch{formError.value='单位删除失败，请重试。'}
}
async function removeType(name:string){
  if(!await confirmAction(`从候选项中删除“${name}”？已有辅料的类型不受影响。`))return
  try{await materialTypesApi.remove(name);selectableTypes.value=selectableTypes.value.filter(v=>v!==name)}catch{formError.value='类型删除失败，请重试。'}
}
const materialTypes = computed(() => [...new Set(items.value.map(item => item.materialType).filter(Boolean))].sort())
const filteredItems = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return items.value.filter(item => {
    const balance = balanceByMaterial.value.get(item.id) ?? balanceByMaterial.value.get(item.materialCode)
    const keywordMatches = !keyword || [item.materialCode, item.name, item.specification ?? ''].some(value => value.toLowerCase().includes(keyword))
    const stockMatches = !filters.stock
      || (filters.stock === 'LOW' && balance?.lowStock)
      || (filters.stock === 'NORMAL' && balance && !balance.lowStock)
      || (filters.stock === 'UNINITIALIZED' && !balance)
    return keywordMatches && (!filters.materialType || item.materialType === filters.materialType)
      && (!filters.status || item.status === filters.status) && stockMatches
  })
})

function balanceFor(item: MaterialDetailDto) {
  return balanceByMaterial.value.get(item.id) ?? balanceByMaterial.value.get(item.materialCode)
}
const pageItems = computed(() => filteredItems.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
watch(filters, () => { page.value = 1; selectedIds.value = [] })
watch([page, pageSize], () => { selectedIds.value = [] })
watch(() => filteredItems.value.length, total => { page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize.value))) })
const allPageSelected = computed(() => pageItems.value.length > 0 && pageItems.value.every(item => selectedIds.value.includes(item.id)))
function selectPage(event: Event) { selectedIds.value = (event.target as HTMLInputElement).checked ? pageItems.value.map(item => item.id) : [] }
async function deleteSelected() {
  if (saving.value || !deleteIds.value.length) return
  saving.value = true; deleteError.value = ''
  const ids = [...deleteIds.value]
  try {
    await domainApi.deleteMaterials(ids)
    items.value = items.value.filter(item => !ids.includes(item.id)); selectedIds.value = []
    deleteOpen.value = false; success.value = `已删除 ${ids.length} 条辅料。`
  } catch (exception) { deleteError.value = exception instanceof ApiError ? exception.message : '删除失败，请稍后重试。所选记录已保留。' }
  finally { saving.value = false }
}
function resetForm() {
  formError.value=''
  Object.assign(form, { materialCode: '', name: '', materialType: '', specification: '', unit: '件', imageUrl: '', imageAssetId: '', remark: '' })
  netPrice.value='';originalPrice.value=null;formPriceReady.value=true;
  editingId.value = null; supplierLinks.value = []; showMaterialForm.value = false
}
function resetFilters() { Object.assign(filters, { keyword: '', materialType: '', status: '', stock: '' }) }
function openMaterialForm() { resetForm(); showInventoryForm.value = false; showMaterialForm.value = true; success.value = ''; error.value = '' }
async function edit(item: MaterialDetailDto) {

  formError.value=''
  netPrice.value='';originalPrice.value=null;formPriceReady.value=!canReadPrice.value;supplierLinks.value=[]

  Object.assign(form, { materialCode: item.materialCode, name: item.name, materialType: item.materialType,
    specification: item.specification ?? '', unit: item.unit, imageUrl: item.imageUrl ?? '', imageAssetId: item.imageAssetId ?? '', remark: item.remark ?? '' })
  editingId.value = item.id; showInventoryForm.value = false; showMaterialForm.value = true; success.value = ''; error.value = ''
  if(canReadPrice.value){try{const price=await costsApi.price(item.id);originalPrice.value=price;netPrice.value=price?Number(price.netPrice).toFixed(2):'';formPriceReady.value=true}catch{formError.value='成本价读取失败，请关闭并重新编辑；本次不能保存。'}}
  if (supplierAccess.value) supplierLinks.value = (await domainApi.materialSupplierLinks(item.id)).map(link => ({ supplierId: link.supplierId, purchaseUrl: link.purchaseUrl, remark: link.remark, primary: link.primary }))
}
function openInventory(item?: MaterialDetailDto) {
  inventoryError.value = ''
  resetForm()
  inventoryForm.materialId = item?.id ?? ''; inventoryForm.materialCode = item?.materialCode ?? ''
  inventoryForm.type = session.canAccess(['inventory.inbound']) ? 'INBOUND' : 'OUTBOUND'; inventoryForm.quantity = 0; inventoryForm.reason = '日常库存操作'
  showInventoryForm.value = true; success.value = ''; error.value = ''
}
function addSupplier(event: Event) { const select = event.target as HTMLSelectElement; const id = select.value; select.value = ''; if (id && !supplierLinks.value.some(item => item.supplierId === id)) supplierLinks.value.push({ supplierId: id, primary: supplierLinks.value.length === 0 }) }
function supplierName(id: string) { return supplierOptions.value.find(item => item.id === id)?.name ?? id }
function makePrimary(id: string) { supplierLinks.value.forEach(item => { item.primary = item.supplierId === id }) }
function useImageUrl() { if (form.imageUrl?.trim()) form.imageAssetId = '' }
function markRemoteImageFailed(key: string) { failedRemoteImages.value = new Set(failedRemoteImages.value).add(key) }
function previewImage(item: MaterialDetailDto) {
  if (item.imageAssetId) imagePreview.value = { alt: item.name, imageAssetId: item.imageAssetId, kind: 'material' }
  else if (item.imageUrl) imagePreview.value = { alt: item.name, imageUrl: item.imageUrl, kind: 'material' }
}
async function uploadImage(file: File) {
  uploading.value = true; imageError.value = ''
  try { form.imageAssetId = (await catalogApi.uploadImage('MATERIAL_IMAGE', file)).id; form.imageUrl = '' }
  catch (exception) { imageError.value = exception instanceof Error ? exception.message : '图片上传失败。' ; return false }
  finally { uploading.value = false }
}
function newRequestId() { return `web-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() {
  loading.value = true; error.value = ''
  try {
    const materialResponse = await domainApi.materials()
    items.value = materialResponse.items
    if(formal.value){selectableTypes.value=await materialTypesApi.list();unitOptions.value=await materialTypesApi.units()}
    else selectableTypes.value=materialTypes.value
    await loadPrices()
    if (supplierAccess.value) {const all:SupplierDto[]=[];let p=0;while(true){const result=await domainApi.suppliers({status:'ACTIVE',category:'MATERIAL',size:100,page:p++});all.push(...result.items);if(!result.items.length||all.length>=result.totalCount)break}supplierOptions.value=all}
    if (inventoryReadable.value) {
      try {
        balances.value = (await domainApi.balances()).items
        safetyValues.value = Object.fromEntries(balances.value.filter(item => item.materialId).map(item => [item.materialId as string, item.safetyStock ?? 0]))
      } catch { error.value = '辅料已加载，但库存数据暂时不可用。' }
    }
  } catch { error.value = formal.value ? '辅料数据加载失败。' : '辅料模拟数据加载失败。' }
  finally { loading.value = false }
}
async function save() {
  if(saving.value || uploading.value || !formPriceReady.value)return
  if(canWritePrice.value && priceChanged.value && (!String(netPrice.value).trim() || !/^\d+(\.\d{1,2})?$/.test(String(netPrice.value).trim()))){formError.value='请填写大于或等于 0 的成本价，最多两位小数（例如 0.5）。';return}
  saving.value = true; formError.value = ''; success.value = ''
  try {
    const input: MaterialInput = { materialCode: form.materialCode.trim(), name: form.name.trim(),
      materialType: form.materialType.trim() || '模拟辅料', unit: form.unit.trim(),
      ...(canWritePrice.value && priceChanged.value ? {costPrice:{netPrice:String(netPrice.value).trim(),unit:form.unit,source:'MANUAL',referenceId:null,reason:editingId.value?'编辑辅料时核定成本价':'新增辅料时核定成本价',version:originalPrice.value?.version??-1,confirmedNet:true}} : {}),
      ...(form.specification?.trim() ? { specification: form.specification.trim() } : {}),
      ...(form.imageUrl?.trim() ? { imageUrl: form.imageUrl.trim() } : {}),
      ...(form.imageAssetId ? { imageAssetId: form.imageAssetId } : {}),
      ...(form.remark?.trim() ? { remark: form.remark.trim() } : {}) }
    if (editingId.value) {
      const updated = await domainApi.updateMaterial(editingId.value, input)
      if(input.costPrice){originalPrice.value={...input.costPrice,version:input.costPrice.version+1};prices.value[updated.id]=originalPrice.value}
      if (supplierWritable.value) await domainApi.replaceMaterialSupplierLinks(updated.id, supplierLinks.value)
      items.value = items.value.map(item => item.id === updated.id ? updated : item); success.value = '辅料及供应商关系已更新。'
    } else {
      const response = await domainApi.createMaterial(input)
      editingId.value=response.item.id;form.materialCode=response.item.materialCode;items.value.unshift(response.item)
      if(input.costPrice){originalPrice.value={...input.costPrice,version:0};prices.value[response.item.id]=originalPrice.value}
      if (supplierWritable.value) await domainApi.replaceMaterialSupplierLinks(response.item.id, supplierLinks.value)
      success.value = response.mockData ? '模拟辅料已创建。' : '辅料已创建。'
    }
    resetForm()
  } catch (exception) { formError.value = exception instanceof ApiError ? exception.message : '保存失败，请检查填写内容后重试。' }
  finally { saving.value = false }
}
async function submitInventory() {
  if (saving.value) return
  inventoryError.value = ''
  if (!isPositiveInventoryInteger(inventoryForm.quantity)) {
    inventoryError.value = '数量必须是大于 0 的整数。'
    return
  }
  const balance = selectedMaterial.value ? balanceFor(selectedMaterial.value) : undefined
  if (!mockMode.value && ['OUTBOUND', 'ADJUST_OUT'].includes(inventoryForm.type) && balance && inventoryForm.quantity > balance.quantity) {
    inventoryError.value = `库存不足：当前可用 ${balance.quantity} ${balance.unit}，本次需要 ${inventoryForm.quantity} ${balance.unit}。请减少数量或先办理入库。`
    return
  }
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.transact(mockMode.value
      ? { requestId: newRequestId(), materialCode: inventoryForm.materialCode.trim(), type: inventoryForm.type, quantity: inventoryForm.quantity, reason: inventoryForm.reason.trim() }
      : { requestId: newRequestId(), materialId: inventoryForm.materialId, type: inventoryForm.type, quantity: inventoryForm.quantity,
          unit: selectedMaterial.value?.unit ?? 'PCS', reason: inventoryForm.reason.trim() })
    const balance: InventoryBalanceViewDto = response.balance
    const key = balance.materialId ?? balance.materialCode
    const index = balances.value.findIndex(item => (item.materialId ?? item.materialCode) === key)
    if (index >= 0) balances.value[index] = balance; else balances.value.unshift(balance)
    if (balance.materialId) safetyValues.value[balance.materialId] = balance.safetyStock ?? 0
    success.value = response.replayed ? '请求已幂等复用。' : `${mockMode.value ? '模拟' : '正式'}库存流水已提交。`
    showInventoryForm.value = false
  } catch (exception) {
    inventoryError.value = exception instanceof ApiError
      ? exception.code === 'NEGATIVE_INVENTORY'
        ? '库存不足，无法完成本次出库。库存可能已被其他操作更新，请减少数量或关闭弹窗刷新库存后重试。'
        : exception.message
      : '库存操作未成功，请检查网络后重试。填写的内容已保留。'
  }
  finally { saving.value = false }
}
async function saveSafety(item: MaterialDetailDto) {
  const balance = balanceFor(item)
  if (!balance?.materialId) return
  const safetyStock = safetyValues.value[balance.materialId]
  if (!isNonNegativeInventoryInteger(safetyStock)) {
    error.value = '安全库存必须是大于或等于 0 的整数。'
    return
  }
  saving.value = true; error.value = ''; success.value = ''
  try {
    const updated = await domainApi.updateSafetyStock(balance.materialId, safetyStock)
    const index = balances.value.findIndex(value => value.materialId === balance.materialId)
    if (index >= 0) balances.value[index] = updated
    success.value = '安全库存已更新。'
  } catch { error.value = '安全库存更新失败。' }
  finally { saving.value = false }
}
async function toggleStatus(item: MaterialDetailDto) {
  const enabling = item.status === 'DISABLED'
  if (!await confirmAction(`确认${enabling ? '启用' : '停用'}辅料 ${item.materialCode}？`)) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const updated = enabling ? await domainApi.enableMaterial(item.id) : await domainApi.disableMaterial(item.id)
    items.value = items.value.map(value => value.id === updated.id ? updated : value)
    success.value = `辅料已${enabling ? '启用' : '停用'}。`
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : `辅料${enabling ? '启用' : '停用'}失败。` }
  finally { saving.value = false }
}
const route=useRoute()
const router=useRouter()
const canBatchPurchase=computed(()=>selectedIds.value.length>0 && selectedIds.value.length<=200 && selectedIds.value.every(id=>items.value.some(item=>item.id===id&&item.status==='ACTIVE')))
function batchPurchase(){if(canBatchPurchase.value)void router.push({path:'/purchases/orders',query:{type:'MATERIAL',materialIds:selectedIds.value.join(',')}})}
async function openLinkedSupplier(){const id=route?.query.editMaterial;if(typeof id!=='string'||!session.canAccess(['materials.write','suppliers.write']))return;const item=items.value.find(m=>m.id===id);if(!item){error.value='关联辅料不存在或无法查看，请刷新清单。';return}try{await edit(item)}catch(e){error.value=e instanceof Error?e.message:'辅料加载失败。'}}
watch(()=>route?.query.editMaterial,openLinkedSupplier)
onMounted(async()=>{await load();await openLinkedSupplier()})
</script>

<template>
  <section class="page-stack material-management">
    <p
      v-if="priceError"
      class="error-message"
      role="alert"
    >
      {{ priceError }} <button
        type="button"
        @click="loadPrices"
      >
        重试
      </button>
    </p>
    <SyntheticNotice v-if="!formal" />
    <form
      class="panel filter-panel material-filters"
      @submit.prevent
    >
      <label>关键词<input
        v-model="filters.keyword"
        placeholder="辅料名称或规格"
      ></label>
      <label>辅料类型<select
        v-model="filters.materialType"
        v-single-select
      ><option value="">全部类型</option><option
        v-for="item in materialTypes"
        :key="item"
        :value="item"
      >{{ item }}</option></select></label>
      <label>主档状态<select
        v-model="filters.status"
        v-single-select
      ><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
      <label v-if="inventoryReadable">库存状态<select
        v-model="filters.stock"
        v-single-select
      ><option value="">全部库存</option><option value="NORMAL">正常</option><option value="LOW">低库存</option><option value="UNINITIALIZED">未初始化</option></select></label>
      <button
        class="secondary-button"
        type="button"
        @click="resetFilters"
      >
        重置
      </button>
    </form>
    <section
      class="panel material-toolbar"
      aria-label="辅料管理操作"
    >
      <div>
        <button
          v-if="writable"
          type="button"
          @click="openMaterialForm"
        >
          添加辅料
        </button>
        <button
          v-if="inventoryWritable"
          class="secondary-button"
          type="button"
          @click="openInventory()"
        >
          库存操作
        </button>
        <button
          v-if="formal && session.canAccess(['purchases.read','purchases.write','suppliers.read'])"
          class="batch-purchase-button"
          type="button"
          :disabled="!canBatchPurchase || saving"
          title="选择启用的辅料后批量采购，每单最多 200 项"
          @click="batchPurchase"
        >
          批量采购{{ selectedIds.length ? '（' + selectedIds.length + '）' : '' }}
        </button>
        <button
          v-if="formal && writable && selectedIds.length"
          type="button"
          class="danger-button"
          :disabled="saving"
          @click="requestDelete(selectedIds)"
        >
          删除（{{ selectedIds.length }}）
        </button>
      </div>
      <RouterLink
        v-if="inventoryReadable"
        class="text-link"
        to="/inventory/ledger"
      >
        查看库存流水
      </RouterLink>
    </section>
    <div
      v-if="writable && showMaterialForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="material-dialog-title"
      @keydown.esc="resetForm"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="save"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭辅料操作"
          @click="resetForm"
        >
          ×
        </button>
        <h2 id="material-dialog-title">
          {{ editingId ? '编辑辅料' : formal ? '新增辅料' : '新增模拟辅料' }}
        </h2>
        <label>辅料名称<input
          v-model="form.name"
          required
          maxlength="200"
        ></label>
        <label>辅料类型<EditableSelect
          v-model="form.materialType"
          :options="selectableTypes"
          label="辅料类型"
          :required="formal"
          :disabled="saving"
          @remove="removeType"
        /></label>
        <label v-if="canReadPrice">成本价（人民币 / {{ form.unit }}，不含税）<input
          v-model="netPrice"
          aria-label="辅料成本价"
          inputmode="decimal"
          maxlength="19"
          :disabled="!canWritePrice || !formPriceReady || saving"
          placeholder="选填，未填写表示尚未维护"
          @blur="netPrice && Number.isFinite(Number(netPrice)) && (netPrice=Number(netPrice).toFixed(2))"
        ></label>
        <label>规格<input
          v-model="form.specification"
          maxlength="500"
        ></label>
        <label>单位<EditableSelect
          v-model="form.unit"
          :options="unitOptions"
          label="单位"
          required
          :max-length="32"
          :disabled="saving"
          @remove="removeUnit"
        /></label>
        <label>图片 HTTPS 地址<input
          v-model="form.imageUrl"
          maxlength="500"
          type="url"
          placeholder="也可以直接上传图片"
          @input="useImageUrl"
        ></label>
        <section
          v-if="formal"
          class="form-wide catalog-image-editor material-image-editor"
        >
          <img
            v-if="form.imageUrl"
            class="material-image"
            :src="form.imageUrl"
            alt="辅料图片预览"
            referrerpolicy="no-referrer"
          ><AuthenticatedCatalogImage
            v-else
            :image-id="form.imageAssetId"
            alt="辅料图片"
          />
          <div class="material-image-controls">
            <SingleFileUpload
              hint="图片立即上传，保存表单后关联"
              title="辅料图片"
              accept=".png,.jpg,.jpeg,.webp"
              :max-mb="5"
              :disabled="saving || uploading"
              :upload="uploadImage"
            />
            <button
              v-if="form.imageAssetId || form.imageUrl"
              type="button"
              class="ui-image-remove"
              :disabled="saving || uploading"
              @click="form.imageAssetId = ''; form.imageUrl = ''; imageError = ''"
            >
              移除图片
            </button>
          </div>
        </section>
        <p
          v-if="imageError"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ imageError }}
        </p>
        <label class="form-wide">备注<textarea
          v-model="form.remark"
          maxlength="1000"
        /></label>
        <section
          v-if="supplierWritable"
          class="form-wide relation-editor"
        >
          <h3>关联供应商</h3>
          <p>
            <RouterLink to="/suppliers">
              维护或新建供应商档案
            </RouterLink>；供应商档案独立保存，关联随辅料保存。
          </p>
          <select
            v-single-select
            class="ui-supplier-select"
            aria-label="添加辅料供应商"
            :disabled="saving"
            value=""
            @change="addSupplier($event)"
          >
            <option value="">
              选择供应商…
            </option><option
              v-for="supplier in supplierOptions.filter(item => !supplierLinks.some(link => link.supplierId === item.id))"
              :key="supplier.id"
              :value="supplier.id"
            >
              {{ supplier.name }}
            </option>
          </select>
          <div class="material-supplier-cards">
            <article
              v-for="link in supplierLinks"
              :key="link.supplierId"
              class="material-supplier-card"
            >
              <header>
                <strong>{{ supplierName(link.supplierId) }}</strong><span
                  v-if="link.primary"
                  class="material-primary-tag"
                >主供应商</span><div class="material-supplier-actions">
                  <button
                    v-if="!link.primary"
                    type="button"
                    :disabled="saving"
                    @click="makePrimary(link.supplierId)"
                  >
                    设为主供应商
                  </button><button
                    type="button"
                    class="material-unlink"
                    :disabled="saving"
                    @click="unlinkSupplier(link.supplierId)"
                  >
                    移除
                  </button>
                </div>
              </header>
              <div class="material-supplier-contact">
                <SupplierContactLinks
                  :supplier="supplierOptions.find(item=>item.id===link.supplierId)"
                  :purchase-url="link.purchaseUrl"
                />
              </div>
              <label>备注<input
                v-model="link.remark"
                aria-label="辅料供应商关系备注"
                maxlength="500"
                :disabled="saving"
                placeholder="选填，如供货约定、联系人说明"
              ></label>
            </article>
            <p
              v-if="!supplierLinks.length"
              class="muted"
            >
              暂无关联供应商，请从上方选择。
            </p>
          </div>
        </section>
        <div class="operation-form__actions ui-dialog-actions">
          <p
            v-if="formError"
            class="material-form-error data-state data-state--error"
            role="alert"
          >
            {{ formError }}
          </p>
          <button
            class="secondary-button"
            type="button"
            @click="resetForm"
          >
            取消
          </button>
          <button
            :disabled="saving || uploading || !formPriceReady"
            type="submit"
          >
            {{ saving ? '保存中…' : editingId ? '保存修改' : '新增辅料' }}
          </button>
        </div>
      </form>
    </div>
    <div
      v-if="inventoryWritable && showInventoryForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="inventory-dialog-title"
      @keydown.esc="showInventoryForm = false"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="submitInventory"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭库存操作"
          @click="showInventoryForm = false"
        >
          ×
        </button>
        <h2 id="inventory-dialog-title">
          提交{{ mockMode ? '模拟' : '正式' }}库存命令
        </h2>
        <label v-if="mockMode">辅料编码<input
          v-model="inventoryForm.materialCode"
          required
        ></label>
        <label v-else>辅料<select
          v-model="inventoryForm.materialId"
          v-single-select
          required
        ><option
          value=""
          disabled
        >请选择</option><option
          v-for="item in activeMaterials"
          :key="item.id"
          :value="item.id"
        >{{ item.name }}</option></select></label>
        <label>类型<select
          v-model="inventoryForm.type"
          v-single-select
        ><template v-if="mockMode"><option value="OPENING">期初入库</option><option
          v-if="mockMode || session.canAccess(['inventory.inbound'])"
          value="INBOUND"
        >入库</option><option
          v-if="mockMode || session.canAccess(['inventory.outbound'])"
          value="OUTBOUND"
        >出库</option><option value="ADJUSTMENT">库存调整</option></template><template v-else><option
          v-if="mockMode || session.canAccess(['inventory.inbound'])"
          value="INBOUND"
        >入库</option><option
          v-if="mockMode || session.canAccess(['inventory.outbound'])"
          value="OUTBOUND"
        >出库</option><option
          v-if="session.canAccess(['inventory.inbound'])"
          value="ADJUST_IN"
        >盘盈调整</option><option
          v-if="session.canAccess(['inventory.outbound'])"
          value="ADJUST_OUT"
        >盘亏调整</option></template></select></label>
        <label>数量<input
          v-model.number="inventoryForm.quantity"
          required
          min="1"
          type="number"
          step="1"
        ></label>
        <p
          v-if="selectedMaterial && balanceFor(selectedMaterial)"
          class="form-wide inventory-stock-hint"
        >
          当前可用库存：{{ balanceFor(selectedMaterial)?.quantity }} {{ selectedMaterial.unit }}
        </p>
        <label class="form-wide">原因<input
          v-model="inventoryForm.reason"
          required
          maxlength="500"
        ></label>
        <p
          v-if="inventoryError"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ inventoryError }}
        </p>
        <div class="operation-form__actions ui-dialog-actions">
          <button
            class="secondary-button"
            type="button"
            @click="showInventoryForm = false"
          >
            取消
          </button>
          <button
            :disabled="saving"
            type="submit"
          >
            {{ saving ? '提交中…' : '提交流水' }}
          </button>
        </div>
      </form>
    </div>
    <SuccessToast :message="success" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-else
      class="panel table-panel"
    >
      <div class="table-scroll">
        <TableViewport>
          <table class="data-table material-table">
            <thead>
              <tr>
                <th
                  v-if="formal && writable"
                  class="selection-cell"
                >
                  <input
                    type="checkbox"
                    aria-label="选择本页全部辅料"
                    :checked="allPageSelected"
                    :disabled="saving"
                    @change="selectPage"
                  >
                </th>
                <th>图片</th><th>品名 / 类型</th><th>规格</th><th>成本价（不含税）</th><th v-if="inventoryReadable">
                  现存量
                </th><th v-if="inventoryReadable">
                  安全库存
                </th><th>单位</th><th v-if="inventoryReadable">
                  库存状态
                </th><th>主档状态</th><th v-if="rowActionsAvailable">
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pageItems"
                :key="item.id"
              >
                <td
                  v-if="formal && writable"
                  class="selection-cell"
                >
                  <input
                    v-model="selectedIds"
                    type="checkbox"
                    :value="item.id"
                    :aria-label="`选择辅料 ${item.materialCode}`"
                    :disabled="saving"
                  >
                </td>
                <td>
                  <button
                    v-if="item.imageAssetId || (item.imageUrl && !failedRemoteImages.has(item.id))"
                    class="material-image-button"
                    type="button"
                    :aria-label="`查看${item.name}大图`"
                    @click="previewImage(item)"
                  >
                    <AuthenticatedCatalogImage
                      v-if="item.imageAssetId"
                      :image-id="item.imageAssetId"
                      :alt="item.name"
                    /><img
                      v-else
                      class="material-image"
                      :src="item.imageUrl"
                      :alt="item.name"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                      @error="markRemoteImageFailed(item.id)"
                    >
                  </button><span
                    v-else
                    class="material-image material-image--placeholder"
                    role="img"
                    :aria-label="`${item.name}暂无可用图片`"
                  >图</span>
                </td>
                <td>{{ item.name }}<small>{{ item.materialType }}</small></td><td>{{ item.specification || '—' }}</td><td class="material-cost-cell">
                  {{ priceText(item) }}
                </td>
                <td v-if="inventoryReadable">
                  {{ balanceFor(item)?.quantity ?? '—' }}
                </td>
                <td v-if="inventoryReadable">
                  <div
                    v-if="formal && session.canAccess(['inventory.write']) && balanceFor(item)?.materialId"
                    class="safety-editor"
                  >
                    <input
                      v-model.number="safetyValues[item.id]"
                      :aria-label="`${item.materialCode}安全库存`"
                      min="0"
                      type="number"
                      step="1"
                    >
                    <button
                      :disabled="saving"
                      type="button"
                      @click="saveSafety(item)"
                    >
                      保存
                    </button>
                  </div><span v-else>{{ balanceFor(item)?.safetyStock ?? '—' }}</span>
                </td>
                <td>{{ item.unit }}</td>
                <td
                  v-if="inventoryReadable"
                  :class="balanceFor(item)?.lowStock ? 'status-failure' : balanceFor(item) ? 'status-success' : ''"
                >
                  {{ balanceFor(item) ? (balanceFor(item)?.lowStock ? '低库存' : '正常') : '未初始化' }}
                </td>
                <td>{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</td>
                <td v-if="rowActionsAvailable">
                  <div class="table-actions">
                    <button
                      v-if="writable"
                      type="button"
                      :disabled="item.status === 'DISABLED' || saving"
                      @click="edit(item)"
                    >
                      编辑
                    </button>
                    <button
                      v-if="inventoryWritable"
                      class="secondary-button"
                      type="button"
                      :disabled="item.status === 'DISABLED' || saving"
                      @click="openInventory(item)"
                    >
                      库存
                    </button>
                    <RouterLink
                      v-if="formal && item.status === 'ACTIVE' && session.canAccess(['purchases.read', 'purchases.write', 'suppliers.read'])"
                      class="secondary-button"
                      :to="`/purchases/orders?type=MATERIAL&materialId=${encodeURIComponent(item.id)}`"
                    >
                      采购
                    </RouterLink>
                    <button
                      v-if="formal && writable"
                      type="button"
                      class="material-unlink"
                      :disabled="saving"
                      @click="requestDelete([item.id])"
                    >
                      删除
                    </button>
                    <button
                      v-if="formal && writable"
                      type="button"
                      class="material-status-action"
                      :class="item.status === 'ACTIVE' ? 'material-status-action--disable' : 'material-status-action--enable'"
                      :disabled="saving"
                      @click="toggleStatus(item)"
                    >
                      {{ item.status === 'ACTIVE' ? '停用' : '启用' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!filteredItems.length">
                <td :colspan="6 + (inventoryReadable ? 3 : 0) + (rowActionsAvailable ? 1 : 0) + (formal && writable ? 1 : 0)">
                  没有符合条件的辅料。
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
      </div>
      <ListPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filteredItems.length"
      />
    </article>
    <div
      v-if="deleteOpen"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-material-title"
      @keydown.esc="!saving && (deleteOpen = false)"
    >
      <section class="panel operation-dialog__card operation-dialog__card--compact operation-form">
        <h2 id="delete-material-title">
          删除所选辅料
        </h2>
        <p class="form-wide">
          确认删除 {{ deleteIds.length }} 条辅料（{{ items.filter(item => deleteIds.includes(item.id)).map(item => item.name).join('、') }}）？删除后不可恢复。已有库存流水、库存或产品/采购关联的辅料不能删除，可使用停用功能。
        </p>
        <p
          v-if="deleteError"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ deleteError }}
        </p>
        <div class="operation-form__actions ui-dialog-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="deleteOpen = false"
          >
            取消
          </button><button
            type="button"
            class="danger-button"
            :disabled="saving"
            @click="deleteSelected"
          >
            {{ saving ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </section>
    </div>
    <div
      v-if="imagePreview"
      class="image-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="`${imagePreview.alt}图片预览`"
      @click.self="imagePreview = null"
      @keydown.esc="imagePreview = null"
    >
      <div class="material-preview">
        <button
          type="button"
          aria-label="关闭图片预览"
          @click="imagePreview = null"
        >
          ×
        </button>
        <AuthenticatedCatalogImage
          v-if="'imageAssetId' in imagePreview"
          :class="{ 'supplier-qr-preview': imagePreview.kind === 'supplier-qr' }"
          :image-id="imagePreview.imageAssetId"
          variant="original"
          :alt="imagePreview.alt"
        /><img
          v-else
          :class="imagePreview.kind === 'supplier-qr' ? 'supplier-qr-preview' : 'material-preview__image'"
          :src="imagePreview.imageUrl"
          :alt="imagePreview.alt"
          referrerpolicy="no-referrer"
        >
        <p>{{ imagePreview.alt }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.batch-purchase-button{background:#168347;color:#fff;border:1px solid #168347}.batch-purchase-button:disabled{background:#e5e7eb;color:#94a3b8;border-color:#e5e7eb;cursor:not-allowed}
.operation-form__actions { flex-wrap: wrap; }
.material-form-error { flex: 1 1 100%; margin: 0; text-align: left; }
.material-supplier-contact { display:flex;flex-wrap:wrap;gap:16px;color:var(--text-secondary,#64748b);font-size:12px; }
.material-supplier-cards{display:grid;gap:8px;margin-top:10px}.material-supplier-card{border:1px solid var(--line);border-radius:6px;background:#f8fafc;padding:10px 12px;display:grid;gap:8px;min-width:0}.material-supplier-card header{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.material-supplier-card strong{font-size:13px;overflow-wrap:anywhere}.material-primary-tag{color:#2563a6;background:#eaf2ff;padding:2px 6px;border-radius:4px;font-size:12px}.material-supplier-actions{margin-left:auto;display:flex;gap:12px}.material-supplier-actions button{background:transparent;border:0;padding:4px;color:var(--accent);font-size:12px;cursor:pointer}.material-supplier-actions button.material-unlink{color:#b42318}.material-supplier-card label{display:flex;align-items:center;gap:8px;font-size:13px}.material-supplier-card input{flex:1;min-width:0}.material-price-confirm{display:flex;align-items:center;gap:8px;font-size:13px}.material-price-confirm input{width:auto}.material-cost-cell{white-space:nowrap;font-variant-numeric:tabular-nums}

#app .material-management .table-actions .material-status-action{display:inline-flex;align-items:center;justify-content:center;min-height:30px;padding:4px 10px;border:1px solid;border-radius:4px;font-size:13px;font-weight:400;cursor:pointer}
#app .material-management .table-actions .material-status-action--disable{color:#b42318;background:#fff7f6;border-color:#f1c5c1}
#app .material-management .table-actions .material-status-action--disable:hover:not(:disabled){background:#feecea}
#app .material-management .table-actions .material-status-action--enable{color:#168252;background:#eff9f3;border-color:#b7dec7}
#app .material-management .table-actions .material-status-action:disabled{opacity:.5;cursor:not-allowed}
</style>

<style scoped>
.batch-purchase-button{background:#168347;color:#fff;border:1px solid #168347}.batch-purchase-button:disabled{background:#e5e7eb;color:#94a3b8;border-color:#e5e7eb;cursor:not-allowed}
.operation-form__actions { flex-wrap: wrap; }
.material-form-error { flex: 1 1 100%; margin: 0; text-align: left; }
.material-supplier-contact { display:flex;flex-wrap:wrap;gap:16px;color:var(--text-secondary,#64748b);font-size:12px; }
#app .material-image-editor{display:grid;grid-template-columns:72px minmax(0,1fr);align-items:start;gap:12px}
#app .material-image-controls{display:grid;gap:8px;min-width:0}#app .material-image-controls .ui-image-remove{justify-self:start}
@media(max-width:560px){#app .material-image-editor{grid-template-columns:minmax(0,1fr)}}
</style>
