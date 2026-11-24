<script setup lang="ts">
import SupplierDetailDialog from './SupplierDetailDialog.vue'
import type { UploadProgress } from '@/utils/imageUpload'
import ProductMaterialPicker from './ProductMaterialPicker.vue'
import TableViewport from './TableViewport.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import '@/styles/productManagement.css'
import { vSingleSelect } from '@/directives/singleSelect'
import ProductNameEditor from './ProductNameEditor.vue'
import { computed, inject, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, matchedRouteKey, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
import { catalogApi } from '@/api/catalog'
import { domainApi, type InventoryBalanceViewDto, type MaterialDetailDto, type ProductInput, type ProductVariantInput, type SupplierDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SupplierContactLinks from '@/components/SupplierContactLinks.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ initial?: ProductInput; busy?: boolean; submitLabel: string; showCancel?: boolean; focusVariantId?: string | undefined; focusField?: 'image' | 'dimensions' | 'weight' | 'supplier' | undefined }>()
const emit = defineEmits<{ submit: [value: ProductInput]; cancel: [] }>()
const suppliers = ref<SupplierDto[]>([]); const materials = ref<MaterialDetailDto[]>([])
const balances = ref<InventoryBalanceViewDto[]>([]); const inventoryFailed = ref(false)
const referenceError = ref(''); const loadingReferences = ref(false); const imageError = ref(''); const uploading = ref(new Set<number>())
const activeTabs = ref<Record<number, 'basic' | 'purchase' | 'specs' | 'materials'>>({})
function activeTab(index: number) { return activeTabs.value[index] ?? 'basic' }
function setTab(index: number, value: 'basic' | 'purchase' | 'specs' | 'materials') { activeTabs.value[index] = value }
const parentReadonly = computed(()=>!!props.focusVariantId && props.focusVariantId !== '__NEW__')
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
function blankVariant(): ProductVariantInput { return { skuCode: '', variantName: '', nameHighlighted: false, status: 'ACTIVE', suppliers: [], materials: [] } }
const model = reactive<ProductInput>(JSON.parse(JSON.stringify(props.initial ?? { productCode: '', productName: '', status: 'ACTIVE', variants: [blankVariant()] })))
const initialSnapshot = JSON.stringify(model)
const dirty = computed(() => JSON.stringify(model) !== initialSnapshot)
const discardPrompt = ref(false)
let pendingDiscard: (() => void) | undefined
let pendingKeep: (() => void) | undefined
function requestClose() {
  if (props.busy || uploading.value.size || discardPrompt.value) return
  if (!dirty.value) { emit('cancel'); return }
  pendingDiscard = () => emit('cancel'); pendingKeep = undefined; discardPrompt.value = true
}
function resolveDiscard(discard: boolean) {
  discardPrompt.value = false
  if (discard) pendingDiscard?.(); else pendingKeep?.()
  pendingDiscard = undefined; pendingKeep = undefined
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || props.busy || uploading.value.size) { event.preventDefault(); event.returnValue = '' }
}
if (inject(matchedRouteKey, undefined)) {
  const leave = () => {
    if (props.busy || uploading.value.size || discardPrompt.value) return false
    if (!dirty.value) return true
    return new Promise<boolean>(resolve => {
      pendingDiscard = () => resolve(true); pendingKeep = () => resolve(false); discardPrompt.value = true
    })
  }
  onBeforeRouteLeave(leave); onBeforeRouteUpdate(leave)
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); pendingKeep?.() })
defineExpose({ requestClose })
function addVariant() { model.variants.push(blankVariant()) }
function removeVariant(index: number) {
  if (props.busy || uploading.value.size) return
  const variant = model.variants[index]
  if (variant?.id) variant.status = 'DISABLED'
  else if (model.variants.length > 1) model.variants.splice(index, 1)
}
function supplierName(id: string) { return suppliers.value.find(item => item.id === id)?.name ?? (loadingReferences.value ? '正在加载供应商…' : '供应商资料暂不可用') }
function materialName(id: string) { const item = materials.value.find(value => value.id === id); return item ? item.name : loadingReferences.value ? '正在加载辅料…' : '辅料资料暂不可用' }
function stockLabel(material: MaterialDetailDto) { if (!session.canAccess(['inventory.read'])) return '无查看权限'; if (inventoryFailed.value) return '暂不可用'; const balance = balances.value.find(item => item.materialId === material.id); return balance ? `库存 ${balance.quantity} ${balance.unit}` : '库存未初始化' }
const supplierDetailId=ref('')
function addSupplier(variant: ProductVariantInput, event: Event) { const element = event.target as HTMLSelectElement; const id = element.value; element.value = ''; if (id && !variant.suppliers.some(item => item.supplierId === id)) variant.suppliers.push({ supplierId: id, primary: variant.suppliers.length === 0 }) }
const openRelationNotes = ref(new Set<object>())
function toggleRelationNote(link: object) {
  if (openRelationNotes.value.has(link)) openRelationNotes.value.delete(link)
  else openRelationNotes.value.add(link)
}
const materialTarget = ref<ProductVariantInput>()
function addMaterials(ids: string[]) {
  const variant = materialTarget.value
  if (!variant || props.busy) return
  ids.forEach(id => {
    const material = materials.value.find(item => item.id === id && item.status === 'ACTIVE')
    if (material && !variant.materials.some(item => item.materialId === id)) variant.materials.push({ materialId: id, standardQuantity: 1, unit: material.unit, lossRate: 0, enabled: true })
  })
  materialTarget.value = undefined
}
function makePrimary(variant: ProductVariantInput, id: string) { variant.suppliers.forEach(item => { item.primary = item.supplierId === id }) }
function useImageUrl(variant: ProductVariantInput) { if (variant.imageUrl?.trim()) variant.imageAssetId = undefined }
async function uploadImage(variant: ProductVariantInput, index: number, file: File, progress?: UploadProgress) {
  if (props.busy || uploading.value.has(index)) return false
  uploading.value.add(index); imageError.value = ''
  try { variant.imageAssetId = (await catalogApi.uploadImage('SKU_MAIN', file, progress)).id; variant.imageUrl = undefined }
  catch (error) { imageError.value = error instanceof Error ? error.message : '图片上传失败。'; return false }
  finally { uploading.value.delete(index) }
}
async function uploadParentImage(file: File, progress?: UploadProgress) {
  if (!file || props.busy || uploading.value.has(-1)) return false
  uploading.value.add(-1); imageError.value = ''
  try { model.imageAssetId = (await catalogApi.uploadImage('PRODUCT_MAIN', file, progress)).id }
  catch (error) { imageError.value = error instanceof Error ? error.message : '父体图片上传失败。'; return false }
  finally { uploading.value.delete(-1) }
}
const referenceLoaded = new Set<string>()
const referenceTasks = new Map<string, Promise<void>>()
let referenceRequests = 0
async function ensureReferences(kind: 'purchase' | 'materials' | 'inventory') {
  if (referenceLoaded.has(kind)) return
  if (referenceTasks.has(kind)) return referenceTasks.get(kind)
  const task = Promise.resolve().then(async () => {
    referenceRequests++; loadingReferences.value = true
    try {
      if (kind === 'purchase' && supplierAccess.value) {
        const all: SupplierDto[] = []; let page = 0
        while (true) { const response = await domainApi.suppliers({ page, size: 100, status: 'ACTIVE', category: 'PRODUCT' }); all.push(...response.items); if (!response.items.length || all.length >= response.totalCount) break; page++ }
        const missing = [...new Set(model.variants.flatMap(v=>v.suppliers.map(s=>s.supplierId)))].filter(id=>!all.some(s=>s.id===id))
        await Promise.all(Array.from({length:Math.min(4,missing.length)},async()=>{while(missing.length){const id=missing.shift();if(id)all.push(await domainApi.supplier(id))}}))
        suppliers.value = all
      } else if (kind === 'materials' && session.canAccess(['materials.read'])) materials.value = (await domainApi.materials()).items
      else if (kind === 'inventory' && session.canAccess(['inventory.read'])) { balances.value = (await domainApi.balances()).items; inventoryFailed.value = false }
      referenceLoaded.add(kind)
    } catch {
      if (kind === 'inventory') inventoryFailed.value = true
      referenceError.value = `${kind === 'purchase' ? '供应商' : kind === 'materials' ? '辅料' : '库存'}资料加载失败，请重试；已有关联资料保留。`
    } finally { referenceTasks.delete(kind); referenceRequests--; loadingReferences.value = referenceRequests > 0 }
  })
  referenceTasks.set(kind,task); return task
}
async function loadReferences() {
  if (runtimeConfig.authMode !== 'password') return
  referenceError.value = ''
  const tabs = new Set(Object.values(activeTabs.value))
  await Promise.all([tabs.has('purchase') ? ensureReferences('purchase') : undefined, tabs.has('materials') ? ensureReferences('materials') : undefined, materialTarget.value ? ensureReferences('inventory') : undefined])
}
watch([() => JSON.stringify(activeTabs.value), materialTarget], () => { void loadReferences() })
function refreshReferences() { referenceLoaded.clear(); void loadReferences() }
const duplicateNames = computed(() => model.variants.filter((v,i,all)=>v.variantName.trim() && all.some((other,j)=>i!==j&&other.variantName.trim().toLocaleLowerCase()===v.variantName.trim().toLocaleLowerCase())).map(v=>v.variantName))
// Child v-model updates can nest reactive arrays inside otherwise plain form objects.
// Copy the JSON-shaped API data recursively, preserving optional undefined properties.
function copyFormValue<T>(value: T): T {
  if (Array.isArray(value)) return value.map(item => copyFormValue(item)) as T
  if (value !== null && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, copyFormValue(item)])) as T
  return value
}
async function submit(event: Event) {
  if (props.busy || uploading.value.size > 0) return
  const form = event.target as HTMLFormElement
  const invalid = form.querySelector<HTMLInputElement>(':invalid')
  if (invalid) {
    const editor = invalid.closest<HTMLElement>('[data-variant-index]')
    if (editor) {
      const index = Number(editor.dataset.variantIndex)
      const panel = invalid.closest<HTMLElement>('[data-editor-tab]')
      setTab(index, panel?.dataset.editorTab as 'basic' | 'purchase' | 'specs' | 'materials' ?? 'basic')
    }
    await nextTick(); invalid.reportValidity(); invalid.focus(); return
  }
  const input = copyFormValue(model)
  for (const variant of input.variants) {
    delete variant.customs
    for (const key of ['itemLengthCm', 'itemWidthCm', 'itemHeightCm', 'packageLengthCm', 'packageWidthCm', 'packageHeightCm', 'packageWeightGrams'] as const) {
      if ((variant[key] as unknown) === '') variant[key] = undefined
    }
  }
  emit('submit', input)
}
onMounted(loadReferences)
onMounted(async () => {
  if (!props.focusField || !props.focusVariantId) return
  const index = model.variants.findIndex(variant => variant.id === props.focusVariantId)
  if (index < 0) return
  const tab = props.focusField === 'supplier' ? 'purchase' : props.focusField === 'image' ? 'basic' : 'specs'
  setTab(index, tab)
  await nextTick()
  const editor = formElement.value?.querySelector<HTMLElement>(`[data-variant-index="${index}"]`)
  const target = editor?.querySelector<HTMLElement>(`[data-product-field="${props.focusField}"]`)
  target?.scrollIntoView?.({ block: 'nearest' })
  const input = target?.matches('input,button') ? target : target?.querySelector<HTMLElement>('.single-select-trigger:not(:disabled),button:not(:disabled)') ?? target
  input?.focus()
})
const formElement = ref<HTMLFormElement>()
</script>

<template>
  <form
    ref="formElement"
    v-bind="$attrs"
    class="page-stack product-editor-form product-ui"
    :class="{ 'product-editor-form--single': parentReadonly }"
    novalidate
    @submit.prevent="submit"
  >
    <div
      v-if="parentReadonly"
      class="product-parent-context"
    >
      <span>所属父体</span><strong>{{ model.productName }}</strong><small>仅编辑当前子体</small>
    </div>
    <div class="product-form__body page-stack">
      <p
        v-if="duplicateNames.length"
        class="data-state"
        role="status"
      >
        存在同名子体：{{ [...new Set(duplicateNames)].join('、') }}。如为不同规格，请补充名称便于区分；仍可保存。
      </p>
      <p
        v-if="referenceError"
        class="data-state data-state--error"
        role="alert"
      >
        {{ referenceError }} <button
          type="button"
          @click="loadReferences"
        >
          重试
        </button>
      </p>
      <section
        v-if="!parentReadonly"
        class="panel operation-form product-form product-form__parent"
        aria-label="父体基础信息"
      >
        <h2>父体资料</h2>
        <fieldset
          class="form-wide catalog-image-editor parent-image-editor"
          :disabled="busy || parentReadonly || uploading.has(-1)"
        >
          <legend>父体图片</legend>
          <AuthenticatedCatalogImage
            v-if="model.imageAssetId"
            :image-id="model.imageAssetId"
            alt="父体图片"
          />
          <span
            v-else
            class="catalog-image catalog-image--placeholder"
            role="img"
            aria-label="父体暂无图片"
          />
          <SingleFileUpload
            compact
            hint="PNG / JPG / WEBP · ≤5MB · 保存后关联"
            title="父体图片"
            accept=".png,.jpg,.jpeg,.webp"
            :max-mb="5"
            :disabled="busy || parentReadonly || uploading.has(-1)"
            :upload="uploadParentImage"
          />
          <small class="product-upload-note">PNG / JPG / WEBP · ≤5MB · 保存后关联</small>
          <button
            v-if="model.imageAssetId"
            type="button"
            class="ui-image-remove"
            :disabled="busy || parentReadonly"
            @click="model.imageAssetId = null"
          >
            移除父体图片
          </button>
        </fieldset>
        <label>产品名称<input
          v-model="model.productName"
          :disabled="busy || parentReadonly"
          required
          maxlength="128"
        ></label>
        <label>状态<select
          v-model="model.status"
          v-single-select
          :disabled="busy || parentReadonly"
        ><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
        <label class="form-wide">描述<textarea
          v-model="model.description"
          :disabled="busy || parentReadonly"
          maxlength="1000"
        /></label>
      </section>
      <div
        class="product-form__variants"
        aria-label="SKU 资料"
      >
        <header
          v-if="!parentReadonly"
          class="product-form__variants-heading"
        >
          <h2>SKU 资料</h2>
          <span>共 {{ model.variants.length }} 个 SKU</span>
        </header>
        <p
          v-if="imageError"
          class="data-state data-state--error"
          role="alert"
        >
          {{ imageError }}
        </p>
        <section
          v-for="(variant, variantIndex) in model.variants"
          v-show="!focusVariantId || (focusVariantId === '__NEW__' ? !variant.id : variant.id === focusVariantId)"
          :key="variant.id ?? variantIndex"
          :data-variant-index="variantIndex"
          class="panel compact-panel variant-editor"
        >
          <div
            v-if="!parentReadonly"
            class="card-heading"
          >
            <h2>{{ variant.variantName || `SKU ${variantIndex + 1}` }}</h2><button
              v-if="!focusVariantId"
              type="button"
              class="danger-link"
              :disabled="busy || uploading.size > 0 || (variant.id ? variant.status === 'DISABLED' : model.variants.length === 1)"
              @click="removeVariant(variantIndex)"
            >
              {{ variant.id ? '停用子体' : '移除 SKU' }}
            </button>
          </div>
          <nav
            class="product-editor-tabs"
            :aria-label="`SKU ${variantIndex + 1} 资料页签`"
          >
            <button
              type="button"
              :class="{ active: activeTab(variantIndex) === 'basic' }"
              @click="setTab(variantIndex, 'basic')"
            >
              基础信息
            </button>
            <button
              v-if="supplierAccess"
              type="button"
              :class="{ active: activeTab(variantIndex) === 'purchase' }"
              @click="setTab(variantIndex, 'purchase')"
            >
              采购信息
            </button>
            <button
              type="button"
              :class="{ active: activeTab(variantIndex) === 'specs' }"
              @click="setTab(variantIndex, 'specs')"
            >
              规格信息
            </button>
            <button
              type="button"
              :class="{ active: activeTab(variantIndex) === 'materials' }"
              @click="setTab(variantIndex, 'materials')"
            >
              关联辅料
            </button>
          </nav>
          <div
            v-show="['basic', 'specs'].includes(activeTab(variantIndex))"
            class="sku-editor-grid"
          >
            <div
              v-show="activeTab(variantIndex) === 'basic'"
              data-editor-tab="basic"
              class="catalog-image-editor sku-image-editor product-tab-panel product-tab-panel--wide"
              data-product-field="image"
              tabindex="-1"
            >
              <AuthenticatedCatalogImage
                v-if="variant.imageAssetId"
                :image-id="variant.imageAssetId"
                alt="SKU 主图"
              /><AuthenticatedCatalogImage
                v-else-if="variant.imageUrl"
                draft-permission="products.read"
                class="sku-image-preview"
                :image-url="variant.imageUrl"
                alt="SKU 主图"
              /><span
                v-else
                class="catalog-image catalog-image--placeholder"
                role="img"
                aria-label="SKU 主图（无可用图片）"
              >图</span>
              <div class="sku-image-controls">
                <details class="sku-image-link-option">
                  <summary>使用图片链接</summary><label class="sku-image-url">图片 URL<input
                    v-model="variant.imageUrl"
                    :aria-label="`SKU ${variantIndex + 1} 图片 URL`"
                    type="url"
                    maxlength="500"
                    placeholder="https://..."
                    :disabled="busy || uploading.size > 0"
                    @input="useImageUrl(variant)"
                  ></label>
                </details>
                <SingleFileUpload
                  compact
                  hint="PNG / JPG / WEBP · ≤5MB · 保存后关联"
                  title="SKU 主图"
                  accept=".png,.jpg,.jpeg,.webp"
                  :max-mb="5"
                  :disabled="busy || uploading.has(variantIndex)"
                  :upload="(file, progress) => uploadImage(variant, variantIndex, file, progress)"
                />
                <button
                  v-if="variant.imageAssetId || variant.imageUrl"
                  type="button"
                  class="ui-image-remove"
                  :disabled="busy || uploading.size > 0"
                  @click="variant.imageAssetId = undefined; variant.imageUrl = undefined"
                >
                  移除图片
                </button>
                <small class="product-upload-note">PNG / JPG / WEBP · ≤5MB · 保存后关联</small>
              </div>
            </div>
            <div
              v-show="activeTab(variantIndex) === 'basic'"
              data-editor-tab="basic"
              class="operation-form nested-form product-basic-fields product-tab-panel--wide"
            >
              <label>状态<select
                v-model="variant.status"
                v-single-select
                :disabled="busy"
              ><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
              <ProductNameEditor
                v-model="variant.variantName"
                v-model:styles="variant.nameStyles"
                :highlighted="variant.nameHighlighted"
              />
            </div>
            <section
              v-show="activeTab(variantIndex) === 'specs'"
              data-editor-tab="specs"
              class="operation-form nested-form structured-dimensions product-tab-panel--wide"
            >
              <h3>产品规格（cm）</h3>
              <label>长<input
                v-model.number="variant.itemLengthCm"
                data-product-field="dimensions"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <label>宽<input
                v-model.number="variant.itemWidthCm"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <label>高<input
                v-model.number="variant.itemHeightCm"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <h3>包装规格（cm）</h3>
              <label>包装长<input
                v-model.number="variant.packageLengthCm"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <label>包装宽<input
                v-model.number="variant.packageWidthCm"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <label>包装高<input
                v-model.number="variant.packageHeightCm"
                type="number"
                min="0.001"
                max="999999.999"
                step="0.001"
              ></label>
              <label>包装重量（克）<input
                v-model.number="variant.packageWeightGrams"
                data-product-field="weight"
                type="number"
                min="0.001"
                max="999999999.999"
                step="0.001"
              ></label>
              <p
                v-if="variant.itemDimensions || variant.packageDimensions"
                class="legacy-dimensions"
              >
                旧规格（只读）：{{ variant.itemDimensions || '—' }} / {{ variant.packageDimensions || '—' }}
              </p>
            </section>
          </div>
          <section
            v-if="supplierAccess"
            v-show="activeTab(variantIndex) === 'purchase'"
            data-editor-tab="purchase"
            class="relation-editor product-tab-panel"
          >
            <div class="relation-editor-toolbar">
              <div
                class="product-supplier-picker"
                data-product-field="supplier"
                tabindex="-1"
              >
                <select
                  v-single-select
                  class="ui-supplier-select"
                  aria-label="添加供应商"
                  :disabled="loadingReferences || busy"
                  value=""
                  @change="addSupplier(variant, $event)"
                >
                  <option value="">
                    ＋ 选择供应商…
                  </option>
                  <option
                    v-for="supplier in suppliers.filter(item => item.status === 'ACTIVE' && !variant.suppliers.some(link => link.supplierId === item.id))"
                    :key="supplier.id"
                    :value="supplier.id"
                  >
                    {{ supplier.name }}
                  </option>
                </select>
              </div>
              <span class="relation-count">已关联 {{ variant.suppliers.length }} 家</span><button
                type="button"
                class="relation-note-toggle"
                :disabled="loadingReferences"
                @click="refreshReferences"
              >
                刷新档案
              </button>
              <RouterLink
                class="relation-master-link"
                to="/suppliers"
              >
                维护供应商档案
              </RouterLink>
            </div>
            <p class="relation-editor-hint">
              档案独立保存，关联随本次资料保存。
            </p>
            <p
              v-if="!variant.suppliers.length"
              class="relation-editor-empty"
            >
              暂未关联供应商，请从上方选择。
            </p>
            <TableViewport
              v-else
              class="relation-edit-viewport"
            >
              <table
                class="relation-edit-table"
                aria-label="编辑关联供应商"
              >
                <thead>
                  <tr>
                    <th scope="col">
                      供应商
                    </th><th scope="col">
                      主供应商
                    </th><th scope="col">
                      采购 / 电话
                    </th><th scope="col">
                      备注
                    </th><th scope="col">
                      操作
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <template
                    v-for="link in variant.suppliers"
                    :key="link.supplierId"
                  >
                    <tr>
                      <th
                        scope="row"
                        class="relation-edit-name"
                      >
                        <button
                          type="button"
                          class="product-text-button"
                          @click="supplierDetailId=link.supplierId"
                        >
                          {{ supplierName(link.supplierId) }}
                        </button>
                        <small
                          v-if="link.primary"
                          class="product-primary-supplier"
                        >主供应商</small>
                        <small
                          v-if="suppliers.find(item => item.id === link.supplierId)?.status === 'DISABLED'"
                          class="product-inline-note"
                        >已停用 · 历史关联</small>
                      </th>
                      <td class="relation-primary-cell">
                        <label class="relation-radio"><input
                          type="radio"
                          :name="`primary-${variantIndex}`"
                          :checked="link.primary"
                          :disabled="busy"
                          :aria-label="`将${supplierName(link.supplierId)}设为主供应商`"
                          @change="makePrimary(variant, link.supplierId)"
                        ></label>
                      </td>
                      <td class="relation-edit-contact">
                        <SupplierContactLinks
                          :supplier="suppliers.find(item => item.id === link.supplierId)"
                          :purchase-url="link.purchaseUrl"
                        />
                      </td>
                      <td class="relation-edit-action">
                        <button
                          type="button"
                          class="relation-note-toggle"
                          :aria-expanded="openRelationNotes.has(link)"
                          :aria-controls="`supplier-note-${variantIndex}-${link.supplierId}`"
                          :aria-label="`${openRelationNotes.has(link) ? '收起' : '编辑'}${supplierName(link.supplierId)}的关系备注`"
                          :title="link.remark"
                          @click="toggleRelationNote(link)"
                        >
                          {{ openRelationNotes.has(link) ? '收起' : link.remark ? '已填备注' : '添加备注' }}
                        </button>
                      </td>
                      <td class="relation-edit-action">
                        <button
                          type="button"
                          class="danger-link"
                          :disabled="busy"
                          :aria-label="`解除${supplierName(link.supplierId)}的关联`"
                          @click="openRelationNotes.delete(link); variant.suppliers.splice(variant.suppliers.indexOf(link), 1)"
                        >
                          解除
                        </button>
                      </td>
                    </tr>
                    <tr
                      v-show="openRelationNotes.has(link)"
                      :id="`supplier-note-${variantIndex}-${link.supplierId}`"
                      class="relation-edit-note"
                    >
                      <td colspan="5">
                        <label>关系备注<input
                          v-model="link.remark"
                          aria-label="供应商关系备注"
                          maxlength="500"
                          placeholder="填写该供应商与当前 SKU 的采购备注"
                          :disabled="busy"
                        ></label>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </TableViewport>
          </section>
          <section
            v-show="activeTab(variantIndex) === 'materials'"
            data-editor-tab="materials"
            class="relation-editor product-tab-panel"
          >
            <div class="relation-editor-toolbar">
              <button
                type="button"
                class="product-select-button"
                aria-label="添加辅料"
                :disabled="loadingReferences || busy"
                @click="materialTarget = variant"
              >
                ＋ 选择辅料
              </button>
              <span class="relation-count">已关联 {{ variant.materials.length }} 种</span>
              <span class="relation-editor-hint">损耗率按百分比填写</span>
            </div>
            <p
              v-if="!variant.materials.length"
              class="relation-editor-empty"
            >
              暂未关联辅料，可批量选择后填写用量。
            </p>
            <TableViewport
              v-else
              class="relation-edit-viewport"
            >
              <table
                class="relation-edit-table"
                aria-label="编辑关联辅料"
              >
                <thead>
                  <tr>
                    <th scope="col">
                      辅料
                    </th><th scope="col">
                      标准用量 / 单位
                    </th><th scope="col">
                      损耗率（%）
                    </th><th scope="col">
                      备注
                    </th><th scope="col">
                      操作
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <template
                    v-for="link in variant.materials"
                    :key="link.materialId"
                  >
                    <tr>
                      <th
                        scope="row"
                        class="relation-edit-name"
                      >
                        {{ materialName(link.materialId) }}
                      </th>
                      <td class="relation-edit-quantity">
                        <div class="relation-quantity-input">
                          <input
                            v-model.number="link.standardQuantity"
                            :aria-label="`${materialName(link.materialId)}标准用量`"
                            type="number"
                            min="0.0000000001"
                            step="any"
                            required
                            :disabled="busy"
                          ><span :aria-label="`单位：${link.unit}`">{{ link.unit || '—' }}</span>
                        </div>
                      </td>
                      <td class="relation-edit-loss">
                        <input
                          :value="Number((link.lossRate * 100).toFixed(4))"
                          :aria-label="`${materialName(link.materialId)}损耗率（百分比）`"
                          type="number"
                          min="0"
                          max="100"
                          step="0.0001"
                          :disabled="busy"
                          @input="link.lossRate = Number(($event.target as HTMLInputElement).value) / 100"
                        >
                      </td>
                      <td class="relation-edit-action">
                        <button
                          type="button"
                          class="relation-note-toggle relation-note-toggle--material"
                          :aria-expanded="openRelationNotes.has(link)"
                          :aria-controls="`material-note-${variantIndex}-${link.materialId}`"
                          :aria-label="`${openRelationNotes.has(link) ? '收起' : '编辑'}${materialName(link.materialId)}的关系备注`"
                          :title="link.remark"
                          @click="toggleRelationNote(link)"
                        >
                          {{ openRelationNotes.has(link) ? '收起' : link.remark ? '已填备注' : '添加备注' }}
                        </button>
                      </td>
                      <td class="relation-edit-action">
                        <button
                          type="button"
                          class="danger-link"
                          :disabled="busy"
                          :aria-label="`解除${materialName(link.materialId)}的关联`"
                          @click="openRelationNotes.delete(link); variant.materials.splice(variant.materials.indexOf(link), 1)"
                        >
                          解除
                        </button>
                      </td>
                    </tr>
                    <tr
                      v-show="openRelationNotes.has(link)"
                      :id="`material-note-${variantIndex}-${link.materialId}`"
                      class="relation-edit-note"
                    >
                      <td colspan="5">
                        <label>关系备注<input
                          v-model="link.remark"
                          aria-label="辅料关系备注"
                          maxlength="1000"
                          placeholder="填写该辅料与当前 SKU 的关联备注"
                          :disabled="busy"
                        ></label>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </TableViewport>
          </section>
        </section>
      </div>
    </div>
    <div class="form-footer ui-dialog-actions">
      <button
        v-if="!focusVariantId"
        type="button"
        class="product-select-button primary-button"
        :disabled="busy"
        @click="addVariant"
      >
        新增 SKU
      </button><div class="form-footer__actions ui-dialog-actions">
        <button
          v-if="showCancel"
          type="button"
          class="secondary-action"
          :disabled="busy"
          @click="requestClose"
        >
          取消
        </button><button
          type="submit"
          :disabled="busy || uploading.size > 0"
        >
          {{ busy ? '保存中…' : submitLabel }}
        </button>
      </div>
    </div>
  </form>
  <UnsavedChangesDialog
    v-if="discardPrompt"
    @keep="resolveDiscard(false)"
    @discard="resolveDiscard(true)"
  />
  <ProductMaterialPicker
    v-if="materialTarget"
    :materials="materials"
    :selected-ids="materialTarget.materials.map(item => item.materialId)"
    :stock-label="stockLabel"
    @close="materialTarget = undefined"
    @select="addMaterials"
  />
  <SupplierDetailDialog
    v-if="supplierDetailId"
    :id="supplierDetailId"
    @close="supplierDetailId=''"
  />
</template>

<style scoped>
.product-form__body { grid-auto-rows: max-content; align-content: start; }
.product-editor-form .product-form__body { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; min-width: 0; align-items: start; }
.product-form__body > .data-state { grid-column: 1 / -1; }
.product-editor-form .product-form__parent { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; min-height: 0; padding: 20px; gap: 16px; position: sticky; top: 0; }
.product-form__parent > h2 { margin: 0; padding-left: 10px; border-left: 3px solid var(--accent); }
.product-form__parent input, .product-form__parent textarea, .product-form__parent select { width: 100%; min-width: 0; }
.product-form__parent .parent-image-editor { margin: 0; padding: 12px; }
.product-form__parent .parent-image-editor label { flex-basis: 100%; }
.product-form__parent .catalog-image { width: 112px; height: 112px; object-fit: contain; }
.product-form__variants { display: grid; gap: 16px; min-width: 0; }
.product-form__variants-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding: 16px 20px; background: white; border: 1px solid var(--line); border-radius: 10px; }
.product-form__variants-heading h2 { margin: 0; padding-left: 10px; border-left: 3px solid var(--accent); font-size: 17px; }
.product-form__variants-heading span { color: var(--muted); font-size: 13px; }
.product-form__variants > .variant-editor { min-width: 0; min-height: 0; box-shadow: none; }
@media (max-width: 900px) {
  .product-editor-form .product-form__body { grid-template-columns: minmax(0, 1fr); }
  .product-editor-form .product-form__parent { position: static; }
}
.parent-image-editor { min-width: 0; border: 1px solid var(--line); border-radius: 8px; padding: 14px; }
.parent-image-editor legend { color: var(--muted); font-size: 13px; font-weight: 700; }
.parent-image-editor label { min-width: 0; flex: 1 1 220px; }
.parent-image-editor p { flex-basis: 100%; margin: 0; color: var(--muted); font-size: 12px; }
/* Editable rows stay at the shared 40px form-control size; density comes from layout. */
#app .product-editor-form .relation-editor { margin-top: 0; padding-top: var(--ui-page-padding); border-top: 0; }
.relation-editor-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: var(--ui-section-gap) 10px; margin-bottom: var(--ui-section-gap); }
#app .product-editor-form .relation-editor-toolbar .product-supplier-picker { width: min(100%, 260px); }
.relation-editor-toolbar .product-select-button { min-height: var(--ui-control-height); padding: 4px 10px; }
.relation-count, .relation-editor-hint { color: var(--muted); font-size: 12px; }
.relation-count { white-space: nowrap; }
.relation-master-link { margin-left: auto; color: #2864c5; font-size: 12px; padding: 6px 0; }
.relation-editor-hint { margin: 0 0 var(--ui-section-gap); }
.relation-editor-toolbar .relation-editor-hint { margin: 0; }
.relation-editor-empty { margin: 0; padding: 12px 10px; color: var(--muted); background: #f8fafc; border: 1px dashed var(--line); border-radius: 4px; font-size: 13px; }
.relation-edit-viewport { border: 1px solid var(--line); border-radius: 4px; }
.relation-edit-table { min-width: 570px; font-size: 13px; }
.relation-edit-table th, .relation-edit-table td { padding: var(--ui-section-gap) 8px; text-align: left; vertical-align: middle; border-bottom: 1px solid var(--line); }
.relation-edit-table thead th { height: var(--ui-control-height); box-sizing: border-box; font-weight: 500; font-size: 12px; white-space: nowrap; color: var(--muted); }
.relation-edit-name { width: 32%; min-width: 140px; font-weight: 500; overflow-wrap: anywhere; }
.relation-edit-table tbody tr:not(.relation-edit-note):hover { background: #f8fafc; }
.relation-edit-table tbody tr:last-child td { border-bottom: 0; }
.relation-edit-contact :deep(> span) { display: block; font-size: 12px; overflow-wrap: anywhere; }
.relation-edit-contact :deep(> span + span) { margin-top: 3px; }
.relation-edit-contact :deep(a) { color: #2864c5; }
#app .product-editor-form .relation-radio { display: inline-flex; align-items: center; justify-content: center; min-width: 32px; min-height: 40px; cursor: pointer; }
.relation-radio input { width: 16px; height: 16px; margin: 0; accent-color: var(--accent); }
.relation-edit-action { width: 64px; white-space: nowrap; }
#app .product-editor-form .relation-edit-action button { min-height: var(--ui-control-height); padding: 4px 6px; font-size: 12px; }
.relation-note-toggle { border: 0; border-radius: 4px; background: transparent; color: #2864c5; cursor: pointer; }
.relation-note-toggle:hover { background: #edf3fb; }
.relation-quantity-input { display: flex; align-items: center; gap: var(--ui-section-gap); }
.relation-quantity-input span { max-width: 70px; overflow-wrap: anywhere; color: var(--muted); font-size: 12px; }
.relation-edit-quantity { width: 160px; }
.relation-edit-loss { width: 110px; }
#app .product-editor-form .relation-edit-table input:not([type=radio]) { box-sizing: border-box; width: 100%; min-width: 0; min-height: 40px; padding: 0 8px; border: 1px solid var(--line); border-radius: 4px; background: white; color: var(--ink); }
#app .product-editor-form .relation-quantity-input input { width: 100px; flex: none; }
.relation-edit-note { background: #f8fafc; }
#app .product-editor-form .relation-edit-note label { display: flex; align-items: center; gap: 10px; white-space: nowrap; }
#app .product-editor-form .relation-edit-note input { flex: 1; }
</style>
