<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { catalogApi } from '@/api/catalog'
import { domainApi, type InventoryBalanceViewDto, type InventoryCommandType, type MaterialDetailDto, type MaterialInput, type SupplierAssignmentDto, type SupplierDto, type SupplierLinkDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import ListPagination from '@/components/ListPagination.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const items = ref<MaterialDetailDto[]>([])
const balances = ref<InventoryBalanceViewDto[]>([])
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
const purchaseMaterial = ref<MaterialDetailDto | null>(null)
const purchaseLoading = ref(false)
const purchaseSuppliers = ref<Array<{ link: SupplierLinkDto; supplier: SupplierDto }>>([])
const unitOptions = ['件', '个', '套', '盒', '包', '卷', '米', '千克', '克', 'PCS', 'SET', 'BOX', 'PACK', 'ROLL', 'M', 'KG']
const writable = computed(() => session.canAccess(['materials.write']))
const formal = computed(() => runtimeConfig.authMode === 'password')
const mockMode = computed(() => runtimeConfig.authMode === 'mock')
const inventoryReadable = computed(() => session.canAccess(['inventory.read']))
const inventoryWritable = computed(() => inventoryReadable.value && (mockMode.value || session.canAccess(['inventory.write'])))
const selectedIds = ref<string[]>([]); const deleteOpen = ref(false); const deleteError = ref('')
const page = ref(1); const pageSize = ref(20); const inventoryError = ref('')
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
const supplierWritable = computed(() => writable.value && session.canAccess(['suppliers.read', 'suppliers.write']))
const rowActionsAvailable = computed(() => writable.value || inventoryWritable.value || (formal.value && supplierAccess.value))
const activeMaterials = computed(() => items.value.filter(item => item.status === 'ACTIVE'))
const selectedMaterial = computed(() => items.value.find(item => item.id === inventoryForm.materialId))
const balanceByMaterial = computed(() => new Map(balances.value.map(item => [item.materialId ?? item.materialCode, item])))
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
  if (saving.value || !selectedIds.value.length) return
  saving.value = true; deleteError.value = ''
  const ids = [...selectedIds.value]
  try {
    await domainApi.deleteMaterials(ids)
    items.value = items.value.filter(item => !ids.includes(item.id)); selectedIds.value = []
    deleteOpen.value = false; success.value = `已删除 ${ids.length} 条辅料。`
  } catch (exception) { deleteError.value = exception instanceof ApiError ? exception.message : '删除失败，请稍后重试。所选记录已保留。' }
  finally { saving.value = false }
}
function resetForm() {
  Object.assign(form, { materialCode: '', name: '', materialType: '', specification: '', unit: '件', imageUrl: '', imageAssetId: '', remark: '' })
  editingId.value = null; supplierLinks.value = []; showMaterialForm.value = false
}
function resetFilters() { Object.assign(filters, { keyword: '', materialType: '', status: '', stock: '' }) }
function openMaterialForm() { resetForm(); showInventoryForm.value = false; showMaterialForm.value = true; success.value = ''; error.value = '' }
async function edit(item: MaterialDetailDto) {
  Object.assign(form, { materialCode: item.materialCode, name: item.name, materialType: item.materialType,
    specification: item.specification ?? '', unit: item.unit, imageUrl: item.imageUrl ?? '', imageAssetId: item.imageAssetId ?? '', remark: item.remark ?? '' })
  editingId.value = item.id; showInventoryForm.value = false; showMaterialForm.value = true; success.value = ''; error.value = ''
  if (supplierAccess.value) supplierLinks.value = (await domainApi.materialSupplierLinks(item.id)).map(link => ({ supplierId: link.supplierId, purchaseUrl: link.purchaseUrl, remark: link.remark, primary: link.primary }))
}
function openInventory(item?: MaterialDetailDto) {
  inventoryError.value = ''
  resetForm()
  inventoryForm.materialId = item?.id ?? ''; inventoryForm.materialCode = item?.materialCode ?? ''
  inventoryForm.type = 'INBOUND'; inventoryForm.quantity = 0; inventoryForm.reason = '日常库存操作'
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
function previewSupplierQr(supplier: SupplierDto) {
  const alt = `${supplier.name}微信二维码`
  if (supplier.qrImageId) imagePreview.value = { alt, imageAssetId: supplier.qrImageId, kind: 'supplier-qr' }
  else if (supplier.qrImageUrl) imagePreview.value = { alt, imageUrl: supplier.qrImageUrl, kind: 'supplier-qr' }
}
async function openPurchase(item: MaterialDetailDto) {
  purchaseMaterial.value = item; purchaseSuppliers.value = []; purchaseLoading.value = true; error.value = ''
  try {
    const links = await domainApi.materialSupplierLinks(item.id)
    purchaseSuppliers.value = await Promise.all(links.map(async link => ({ link, supplier: await domainApi.supplier(link.supplierId) })))
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商采购信息加载失败。' }
  finally { purchaseLoading.value = false }
}
async function uploadImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true; imageError.value = ''
  try { form.imageAssetId = (await catalogApi.uploadImage('MATERIAL_IMAGE', file)).id; form.imageUrl = '' }
  catch (exception) { imageError.value = exception instanceof Error ? exception.message : '图片上传失败。' }
  finally { uploading.value = false }
}
function newRequestId() { return `web-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() {
  loading.value = true; error.value = ''
  try {
    const materialResponse = await domainApi.materials()
    items.value = materialResponse.items
    if (supplierAccess.value) supplierOptions.value = (await domainApi.suppliers({ status: 'ACTIVE', size: 100 })).items
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
  saving.value = true; error.value = ''; success.value = ''
  try {
    const input: MaterialInput = { materialCode: form.materialCode.trim(), name: form.name.trim(),
      materialType: form.materialType.trim() || '模拟辅料', unit: form.unit.trim(),
      ...(form.specification?.trim() ? { specification: form.specification.trim() } : {}),
      ...(form.imageUrl?.trim() ? { imageUrl: form.imageUrl.trim() } : {}),
      ...(form.imageAssetId ? { imageAssetId: form.imageAssetId } : {}),
      ...(form.remark?.trim() ? { remark: form.remark.trim() } : {}) }
    if (editingId.value) {
      const updated = await domainApi.updateMaterial(editingId.value, input)
      if (supplierWritable.value) await domainApi.replaceMaterialSupplierLinks(updated.id, supplierLinks.value)
      items.value = items.value.map(item => item.id === updated.id ? updated : item); success.value = '辅料及供应商关系已更新。'
    } else {
      const response = await domainApi.createMaterial(input)
      if (supplierWritable.value) await domainApi.replaceMaterialSupplierLinks(response.item.id, supplierLinks.value)
      items.value.unshift(response.item); success.value = response.mockData ? '模拟辅料已创建。' : '辅料已创建。'
    }
    resetForm()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败：请检查字段与编码唯一性。' }
  finally { saving.value = false }
}
async function submitInventory() {
  if (saving.value) return
  inventoryError.value = ''
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
  saving.value = true; error.value = ''; success.value = ''
  try {
    const updated = await domainApi.updateSafetyStock(balance.materialId, safetyValues.value[balance.materialId] ?? 0)
    const index = balances.value.findIndex(value => value.materialId === balance.materialId)
    if (index >= 0) balances.value[index] = updated
    success.value = '安全库存已更新。'
  } catch { error.value = '安全库存更新失败。' }
  finally { saving.value = false }
}
async function toggleStatus(item: MaterialDetailDto) {
  const enabling = item.status === 'DISABLED'
  if (!window.confirm(`确认${enabling ? '启用' : '停用'}辅料 ${item.materialCode}？`)) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const updated = enabling ? await domainApi.enableMaterial(item.id) : await domainApi.disableMaterial(item.id)
    items.value = items.value.map(value => value.id === updated.id ? updated : value)
    success.value = `辅料已${enabling ? '启用' : '停用'}。`
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : `辅料${enabling ? '启用' : '停用'}失败。` }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack material-management">
    <SyntheticNotice v-if="!formal" />
    <form
      class="panel filter-panel material-filters"
      @submit.prevent
    >
      <label>关键词<input
        v-model="filters.keyword"
        placeholder="辅料编码、名称或规格"
      ></label>
      <label>辅料类型<select v-model="filters.materialType"><option value="">全部类型</option><option
        v-for="item in materialTypes"
        :key="item"
        :value="item"
      >{{ item }}</option></select></label>
      <label>主档状态<select v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
      <label v-if="inventoryReadable">库存状态<select v-model="filters.stock"><option value="">全部库存</option><option value="NORMAL">正常</option><option value="LOW">低库存</option><option value="UNINITIALIZED">未初始化</option></select></label>
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
          v-if="formal && writable && selectedIds.length"
          type="button"
          class="danger-button"
          :disabled="saving"
          @click="deleteError = ''; deleteOpen = true"
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
      @click.self="resetForm"
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
        <label>辅料编码<input
          v-model="form.materialCode"
          required
          maxlength="64"
          :readonly="Boolean(editingId)"
        ></label>
        <label>辅料名称<input
          v-model="form.name"
          required
          maxlength="200"
        ></label>
        <label>辅料类型<input
          v-model="form.materialType"
          :required="formal"
          maxlength="100"
        ></label>
        <label>规格<input
          v-model="form.specification"
          maxlength="500"
        ></label>
        <label>单位<select
          v-model="form.unit"
          required
        ><option
          v-for="unit in unitOptions"
          :key="unit"
          :value="unit"
        >{{ unit }}</option></select></label>
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
          <label class="secondary-action">{{ uploading ? '上传中…' : '上传辅料图片' }}<input
            class="sr-only"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            :disabled="uploading"
            @change="uploadImage"
          ></label>
          <button
            v-if="form.imageAssetId"
            type="button"
            class="secondary-button"
            @click="form.imageAssetId = ''"
          >
            移除上传图片
          </button>
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
            aria-label="添加辅料供应商"
            value=""
            @change="addSupplier($event)"
          >
            <option value="">
              选择供应商…
            </option><option
              v-for="supplier in supplierOptions"
              :key="supplier.id"
              :value="supplier.id"
            >
              {{ supplier.name }}
            </option>
          </select>
          <div
            v-for="link in supplierLinks"
            :key="link.supplierId"
            class="relation-row"
          >
            <strong>{{ supplierName(link.supplierId) }}</strong>
            <label><input
              type="radio"
              name="material-primary-supplier"
              :checked="link.primary"
              @change="makePrimary(link.supplierId)"
            >主供应商</label>
            <input
              v-model="link.purchaseUrl"
              aria-label="辅料采购链接"
              type="url"
              placeholder="1688 HTTPS 采购链接"
            ><input
              v-model="link.remark"
              aria-label="辅料供应商关系备注"
              maxlength="500"
              placeholder="关系备注"
            >
            <button
              type="button"
              class="danger-link"
              @click="supplierLinks.splice(supplierLinks.indexOf(link), 1)"
            >
              解除
            </button>
          </div>
        </section>
        <div class="operation-form__actions">
          <button
            class="secondary-button"
            type="button"
            @click="resetForm"
          >
            取消
          </button>
          <button
            :disabled="saving || uploading"
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
      @click.self="showInventoryForm = false"
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
          required
        ><option
          value=""
          disabled
        >请选择</option><option
          v-for="item in activeMaterials"
          :key="item.id"
          :value="item.id"
        >{{ item.materialCode }} · {{ item.name }}</option></select></label>
        <label>类型<select v-model="inventoryForm.type"><template v-if="mockMode"><option value="OPENING">期初入库</option><option value="INBOUND">入库</option><option value="OUTBOUND">出库</option><option value="ADJUSTMENT">库存调整</option></template><template v-else><option value="INBOUND">入库</option><option value="OUTBOUND">出库</option><option value="ADJUST_IN">盘盈调整</option><option value="ADJUST_OUT">盘亏调整</option></template></select></label>
        <label>数量<input
          v-model.number="inventoryForm.quantity"
          required
          min="0.0001"
          type="number"
          step="0.0001"
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
        <div class="operation-form__actions">
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
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
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
              <th>图片</th><th>辅料编码</th><th>品名 / 类型</th><th>规格</th><th v-if="inventoryReadable">
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
              <td><strong>{{ item.materialCode }}</strong></td><td>{{ item.name }}<small>{{ item.materialType }}</small></td><td>{{ item.specification || '—' }}</td>
              <td v-if="inventoryReadable">
                {{ balanceFor(item)?.quantity ?? '—' }}
              </td>
              <td v-if="inventoryReadable">
                <div
                  v-if="formal && inventoryWritable && balanceFor(item)?.materialId"
                  class="safety-editor"
                >
                  <input
                    v-model.number="safetyValues[item.id]"
                    :aria-label="`${item.materialCode}安全库存`"
                    min="0"
                    type="number"
                    step="0.0001"
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
                  <button
                    v-if="formal && supplierAccess"
                    class="secondary-button"
                    type="button"
                    @click="openPurchase(item)"
                  >
                    采购
                  </button>
                  <button
                    v-if="formal && writable"
                    type="button"
                    :class="item.status === 'ACTIVE' ? 'danger-button' : 'secondary-button'"
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
          确认删除已勾选的 {{ selectedIds.length }} 条辅料？删除后不可恢复。已有库存流水、库存或产品/采购关联的辅料不能删除，可使用停用功能。
        </p>
        <p
          v-if="deleteError"
          class="form-wide data-state data-state--error"
          role="alert"
        >
          {{ deleteError }}
        </p>
        <div class="operation-form__actions">
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
      v-if="purchaseMaterial"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="purchase-dialog-title"
      @click.self="purchaseMaterial = null"
      @keydown.esc="purchaseMaterial = null"
    >
      <section class="panel operation-dialog__card purchase-dialog">
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭采购信息"
          @click="purchaseMaterial = null"
        >
          ×
        </button>
        <h2 id="purchase-dialog-title">
          {{ purchaseMaterial.materialCode }} · {{ purchaseMaterial.name }}采购供应商
        </h2>
        <p
          v-if="purchaseLoading"
          class="data-state"
        >
          正在加载供应商信息…
        </p>
        <p
          v-else-if="!purchaseSuppliers.length"
          class="data-state"
        >
          暂未关联供应商，请先编辑辅料并维护供应商关系。
        </p>
        <div
          v-else
          class="table-scroll"
        >
          <table class="data-table purchase-table">
            <thead><tr><th>供应商</th><th>联系人</th><th>电话</th><th>微信号</th><th>微信二维码</th><th>采购链接</th><th>关系</th><th>备注</th></tr></thead>
            <tbody>
              <tr
                v-for="item in purchaseSuppliers"
                :key="item.link.supplierId"
              >
                <td><strong>{{ item.supplier.name }}</strong><small>{{ item.supplier.status === 'ACTIVE' ? '启用' : '停用' }}</small></td>
                <td>{{ item.supplier.contactPerson || '—' }}</td>
                <td>{{ item.supplier.phone || '—' }}</td>
                <td>{{ item.supplier.wechatId || '—' }}</td>
                <td>
                  <button
                    v-if="item.supplier.qrImageId || item.supplier.qrImageUrl"
                    class="image-button"
                    type="button"
                    :aria-label="`查看${item.supplier.name}微信二维码`"
                    @click="previewSupplierQr(item.supplier)"
                  >
                    <AuthenticatedCatalogImage
                      v-if="item.supplier.qrImageId"
                      class="supplier-qr-thumbnail"
                      :image-id="item.supplier.qrImageId"
                      :alt="`${item.supplier.name}微信二维码`"
                    /><img
                      v-else
                      class="supplier-qr-thumbnail"
                      :src="item.supplier.qrImageUrl"
                      :alt="`${item.supplier.name}微信二维码`"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                    >
                  </button><span v-else>—</span>
                </td>
                <td>
                  <a
                    v-if="item.link.purchaseUrl || item.supplier.sourceUrl"
                    :href="item.link.purchaseUrl || item.supplier.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >打开采购链接</a><span v-else>—</span>
                </td>
                <td>{{ item.link.primary ? '主供应商' : '备选供应商' }}</td>
                <td>{{ item.link.remark || item.supplier.remark || '—' }}</td>
              </tr>
            </tbody>
          </table>
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
