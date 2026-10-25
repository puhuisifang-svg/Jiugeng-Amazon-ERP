<script setup lang="ts">
import ProductNameEditor from './ProductNameEditor.vue'
import { computed, nextTick, onMounted, reactive, ref, toRaw } from 'vue'
import { RouterLink } from 'vue-router'
import { catalogApi } from '@/api/catalog'
import { domainApi, type InventoryBalanceViewDto, type MaterialDetailDto, type ProductInput, type ProductVariantInput, type SupplierDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import SupplierContactLinks from '@/components/SupplierContactLinks.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'
import { blankCustoms, customsInput } from '@/api/customs'
import CustomsFields from '@/components/CustomsFields.vue'

const props = defineProps<{ initial?: ProductInput; busy?: boolean; submitLabel: string; showCancel?: boolean; focusVariantId?: string | undefined }>()
const emit = defineEmits<{ submit: [value: ProductInput]; cancel: [] }>()
const suppliers = ref<SupplierDto[]>([]); const materials = ref<MaterialDetailDto[]>([])
const balances = ref<InventoryBalanceViewDto[]>([]); const inventoryFailed = ref(false)
const referenceError = ref(''); const loadingReferences = ref(false); const imageError = ref(''); const uploading = ref<number | null>(null)
const activeTabs = ref<Record<number, 'basic' | 'purchase' | 'specs' | 'materials' | 'image' | 'customs'>>({})
function activeTab(index: number) { return activeTabs.value[index] ?? 'basic' }
function setTab(index: number, value: 'basic' | 'purchase' | 'specs' | 'materials' | 'image' | 'customs') { if (value === 'customs' && !model.variants[index]!.customs) model.variants[index]!.customs = blankCustoms(); activeTabs.value[index] = value }
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
function blankVariant(): ProductVariantInput { return { skuCode: '', variantName: '', nameHighlighted: false, status: 'ACTIVE', suppliers: [], materials: [] } }
const model = reactive<ProductInput>(JSON.parse(JSON.stringify(props.initial ?? { productCode: '', productName: '', status: 'ACTIVE', variants: [blankVariant()] })))
function addVariant() { model.variants.push(blankVariant()) }
function removeVariant(index: number) { if (model.variants.length > 1) model.variants.splice(index, 1) }
function supplierName(id: string) { return suppliers.value.find(item => item.id === id)?.name ?? id }
function materialName(id: string) { const item = materials.value.find(value => value.id === id); return item ? `${item.materialCode} · ${item.name}` : id }
function stockLabel(material: MaterialDetailDto) { if (!session.canAccess(['inventory.read'])) return '无查看权限'; if (inventoryFailed.value) return '暂不可用'; const balance = balances.value.find(item => item.materialId === material.id); return balance ? `库存 ${balance.quantity} ${balance.unit}` : '库存未初始化' }
function addSupplier(variant: ProductVariantInput, event: Event) { const element = event.target as HTMLSelectElement; const id = element.value; element.value = ''; if (id && !variant.suppliers.some(item => item.supplierId === id)) variant.suppliers.push({ supplierId: id, primary: variant.suppliers.length === 0 }) }
function addMaterial(variant: ProductVariantInput, event: Event) { const element = event.target as HTMLSelectElement; const id = element.value; element.value = ''; const material = materials.value.find(item => item.id === id); if (material && !variant.materials.some(item => item.materialId === id)) variant.materials.push({ materialId: id, standardQuantity: 1, unit: material.unit, lossRate: 0, enabled: true }) }
function makePrimary(variant: ProductVariantInput, id: string) { variant.suppliers.forEach(item => { item.primary = item.supplierId === id }) }
function useImageUrl(variant: ProductVariantInput) { if (variant.imageUrl?.trim()) variant.imageAssetId = undefined }
async function uploadImage(variant: ProductVariantInput, index: number, event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; uploading.value = index; imageError.value = ''; try { variant.imageAssetId = (await catalogApi.uploadImage('SKU_MAIN', file)).id; variant.imageUrl = undefined } catch (error) { imageError.value = error instanceof Error ? error.message : '图片上传失败。' } finally { uploading.value = null } }
async function uploadParentImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || props.busy || uploading.value !== null) return
  uploading.value = -1; imageError.value = ''
  try { model.imageAssetId = (await catalogApi.uploadImage('PRODUCT_MAIN', file)).id }
  catch (error) { imageError.value = error instanceof Error ? error.message : '父体图片上传失败。' }
  finally { uploading.value = null; input.value = '' }
}
async function loadReferences() {
  if (runtimeConfig.authMode !== 'password') return
  loadingReferences.value = true; referenceError.value = ''
  const failed: string[] = []
  try {
    if (session.canAccess(['materials.read'])) {
      try { materials.value = (await domainApi.materials()).items } catch { failed.push('辅料') }
    }
    if (session.canAccess(['inventory.read'])) {
      try { balances.value = (await domainApi.balances()).items } catch { inventoryFailed.value = true }
    }
    if (supplierAccess.value) {
      try {
        const all: SupplierDto[] = []
        let page = 0
        while (true) {
          const response = await domainApi.suppliers({ page, size: 100 })
          all.push(...response.items)
          if (!response.items.length || all.length >= response.totalCount) break
          page++
        }
        suppliers.value = all
      } catch { failed.push('供应商') }
    }
    if (failed.length) referenceError.value = `${failed.join('、')}资料加载失败，请重试；已有关联资料保留。`
  } finally { loadingReferences.value = false }
}
async function submit(event: Event) {
  if (props.busy || uploading.value !== null) return
  const form = event.target as HTMLFormElement
  const invalid = form.querySelector<HTMLInputElement>(':invalid')
  if (invalid) {
    const editor = invalid.closest<HTMLElement>('[data-variant-index]')
    if (editor) {
      const index = Number(editor.dataset.variantIndex)
      const panel = invalid.closest<HTMLElement>('[data-editor-tab]')
      setTab(index, panel?.dataset.editorTab as 'basic' | 'purchase' | 'specs' | 'materials' | 'image' | 'customs' ?? 'basic')
    }
    await nextTick(); invalid.reportValidity(); invalid.focus(); return
  }
  const input = structuredClone(toRaw(model))
  for (const variant of input.variants) {
    if (variant.customs) variant.customs = customsInput(variant.customs)
    for (const key of ['itemLengthCm', 'itemWidthCm', 'itemHeightCm', 'packageLengthCm', 'packageWidthCm', 'packageHeightCm', 'packageWeightGrams'] as const) {
      if ((variant[key] as unknown) === '') variant[key] = undefined
    }
  }
  emit('submit', input)
}
onMounted(loadReferences)
</script>

<template>
  <form
    class="page-stack product-editor-form"
    novalidate
    @submit.prevent="submit"
  >
    <div class="product-form__body page-stack">
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
        class="panel operation-form product-form product-form__parent"
        aria-label="父体基础信息"
      >
        <h2>产品基础信息</h2>
        <label>产品编码<input
          v-model="model.productCode"
          required
          maxlength="64"
          :readonly="Boolean(model.version != null)"
        ></label>
        <label>产品名称<input
          v-model="model.productName"
          required
          maxlength="128"
        ></label>
        <label>产品类型<input
          v-model="model.productType"
          maxlength="100"
        ></label>
        <label>状态<select v-model="model.status"><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
        <fieldset
          class="form-wide catalog-image-editor parent-image-editor"
          :disabled="busy || uploading !== null"
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
          <label>{{ uploading === -1 ? '上传中…' : '上传父体图片' }}<input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            aria-label="上传父体图片"
            @change="uploadParentImage"
          ></label>
          <button
            v-if="model.imageAssetId"
            type="button"
            class="secondary-button"
            @click="model.imageAssetId = null"
          >
            移除父体图片
          </button>
          <p class="form-wide">
            支持 JPG、PNG、WebP，最大 5 MB。保存产品后生效；父体与 SKU 图片独立维护。
          </p>
        </fieldset>
        <label class="form-wide">描述<textarea
          v-model="model.description"
          maxlength="1000"
        /></label>
      </section>
      <div
        class="product-form__variants"
        aria-label="SKU 资料"
      >
        <header class="product-form__variants-heading">
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
          <div class="card-heading">
            <h2>SKU {{ variantIndex + 1 }}</h2><button
              v-if="!focusVariantId"
              type="button"
              class="danger-link"
              :disabled="model.variants.length === 1"
              @click="removeVariant(variantIndex)"
            >
              移除 SKU
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
            <button
              type="button"
              :class="{ active: activeTab(variantIndex) === 'image' }"
              @click="setTab(variantIndex, 'image')"
            >
              图片管理
            </button>
            <button
              type="button"
              :class="{ active: activeTab(variantIndex) === 'customs' }"
              @click="setTab(variantIndex, 'customs')"
            >
              报关资料
            </button>
          </nav>
          <div
            v-if="variant.customs"
            v-show="activeTab(variantIndex) === 'customs'"
            data-editor-tab="customs"
            class="product-tab-panel product-tab-panel--wide"
          >
            <CustomsFields
              v-model="variant.customs"
              :disabled="busy"
            />
          </div>
          <div class="sku-editor-grid">
            <div
              v-show="activeTab(variantIndex) === 'image'"
              data-editor-tab="image"
              class="catalog-image-editor sku-image-editor product-tab-panel product-tab-panel--wide"
            >
              <AuthenticatedCatalogImage
                v-if="variant.imageAssetId"
                :image-id="variant.imageAssetId"
                alt="SKU 主图"
              /><img
                v-else-if="variant.imageUrl"
                class="sku-image-preview"
                :src="variant.imageUrl"
                alt="SKU 主图"
                loading="lazy"
                referrerpolicy="no-referrer"
              ><span
                v-else
                class="catalog-image catalog-image--placeholder"
                role="img"
                aria-label="SKU 主图（无可用图片）"
              >图</span>
              <label class="sku-image-url">图片 URL<input
                v-model="variant.imageUrl"
                :aria-label="`SKU ${variantIndex + 1} 图片 URL`"
                type="url"
                maxlength="500"
                placeholder="https://..."
                @input="useImageUrl(variant)"
              ></label>
              <label class="secondary-action">{{ uploading === variantIndex ? '上传中…' : '上传主图' }}<input
                class="sr-only"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                :disabled="uploading !== null"
                @change="uploadImage(variant, variantIndex, $event)"
              ></label>
              <button
                v-if="variant.imageAssetId || variant.imageUrl"
                type="button"
                class="secondary-button"
                @click="variant.imageAssetId = undefined; variant.imageUrl = undefined"
              >
                移除图片
              </button>
            </div>
            <div
              v-show="activeTab(variantIndex) === 'basic'"
              data-editor-tab="basic"
              class="operation-form nested-form product-tab-panel--wide"
            >
              <label>SKU 编码<input
                v-model="variant.skuCode"
                required
                maxlength="64"
              ></label>
              <ProductNameEditor
                v-model="variant.variantName"
                v-model:styles="variant.nameStyles"
                :highlighted="variant.nameHighlighted"
              />
              <label>状态<select v-model="variant.status"><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
            </div>
            <section
              v-show="activeTab(variantIndex) === 'specs'"
              data-editor-tab="specs"
              class="operation-form nested-form structured-dimensions product-tab-panel--wide"
            >
              <h3>产品规格（cm）</h3><span />
              <label>长<input
                v-model.number="variant.itemLengthCm"
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
              ></label><span />
              <h3>包装规格（cm）</h3><span />
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
            <h3>关联供应商</h3>
            <p>
              <RouterLink to="/suppliers">
                维护或新建供应商档案
              </RouterLink>；档案独立保存，关联随产品整体保存。
            </p>
            <select
              aria-label="添加供应商"
              :disabled="loadingReferences"
              value=""
              @change="addSupplier(variant, $event)"
            >
              <option value="">
                选择供应商…
              </option><option
                v-for="supplier in suppliers.filter(item => item.status === 'ACTIVE')"
                :key="supplier.id"
                :value="supplier.id"
              >
                {{ supplier.name }}
              </option>
            </select>
            <div
              v-for="link in variant.suppliers"
              :key="link.supplierId"
              class="relation-row"
            >
              <strong>{{ supplierName(link.supplierId) }}</strong><label><input
                type="radio"
                :name="`primary-${variantIndex}`"
                :checked="link.primary"
                @change="makePrimary(variant, link.supplierId)"
              >主供应商</label>
              <SupplierContactLinks
                :supplier="suppliers.find(item => item.id === link.supplierId)"
                :purchase-url="link.purchaseUrl"
              /><input
                v-model="link.remark"
                aria-label="供应商关系备注"
                maxlength="500"
                placeholder="关系备注"
              ><button
                type="button"
                class="danger-link"
                @click="variant.suppliers.splice(variant.suppliers.indexOf(link), 1)"
              >
                解除
              </button>
            </div>
          </section>
          <section
            v-show="activeTab(variantIndex) === 'materials'"
            data-editor-tab="materials"
            class="relation-editor product-tab-panel"
          >
            <h3>关联辅料</h3>
            <select
              aria-label="添加辅料"
              :disabled="loadingReferences"
              value=""
              @change="addMaterial(variant, $event)"
            >
              <option value="">
                选择启用辅料…
              </option><option
                v-for="material in materials.filter(item => item.status === 'ACTIVE')"
                :key="material.id"
                :value="material.id"
              >
                {{ material.materialCode }} · {{ material.name }} · {{ material.specification || '无规格' }} · {{ stockLabel(material) }}
              </option>
            </select>
            <div
              v-for="link in variant.materials"
              :key="link.materialId"
              class="relation-row relation-row--material"
            >
              <strong>{{ materialName(link.materialId) }}</strong><label>标准用量<input
                v-model.number="link.standardQuantity"
                type="number"
                min="0.0001"
                step="0.0001"
                required
              ></label><label>单位<input
                v-model="link.unit"
                readonly
              ></label><label>损耗率<input
                v-model.number="link.lossRate"
                type="number"
                min="0"
                max="1"
                step="0.000001"
              ></label><input
                v-model="link.remark"
                aria-label="辅料关系备注"
                maxlength="1000"
                placeholder="备注"
              ><button
                type="button"
                class="danger-link"
                @click="variant.materials.splice(variant.materials.indexOf(link), 1)"
              >
                解除
              </button>
            </div>
          </section>
        </section>
      </div>
    </div>
    <div class="form-footer">
      <button
        v-if="!focusVariantId"
        type="button"
        class="secondary-action"
        @click="addVariant"
      >
        新增 SKU
      </button><div class="form-footer__actions">
        <button
          v-if="showCancel"
          type="button"
          class="secondary-action"
          :disabled="busy"
          @click="emit('cancel')"
        >
          取消
        </button><button
          type="submit"
          :disabled="busy || uploading !== null"
        >
          {{ busy ? '保存中…' : submitLabel }}
        </button>
      </div>
    </div>
  </form>
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
</style>
