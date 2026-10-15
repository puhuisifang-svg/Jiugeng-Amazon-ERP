<script setup lang="ts">
import { computed, onMounted, reactive, ref, toRaw } from 'vue'
import { RouterLink } from 'vue-router'
import { catalogApi } from '@/api/catalog'
import { domainApi, type InventoryBalanceViewDto, type MaterialDetailDto, type ProductInput, type ProductVariantInput, type SupplierDto } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const props = defineProps<{ initial?: ProductInput; busy?: boolean; submitLabel: string; showCancel?: boolean }>()
const emit = defineEmits<{ submit: [value: ProductInput]; cancel: [] }>()
const suppliers = ref<SupplierDto[]>([]); const materials = ref<MaterialDetailDto[]>([])
const balances = ref<InventoryBalanceViewDto[]>([]); const inventoryFailed = ref(false)
const loadingReferences = ref(false); const imageError = ref(''); const uploading = ref<number | null>(null)
const supplierAccess = computed(() => session.canAccess(['suppliers.read']))
function blankVariant(): ProductVariantInput { return { skuCode: '', variantName: '', status: 'DRAFT', suppliers: [], materials: [] } }
const model = reactive<ProductInput>(structuredClone(props.initial ?? { productCode: '', productName: '', status: 'DRAFT', variants: [blankVariant()] }))
function addVariant() { model.variants.push(blankVariant()) }
function removeVariant(index: number) { if (model.variants.length > 1) model.variants.splice(index, 1) }
function supplierName(id: string) { return suppliers.value.find(item => item.id === id)?.name ?? id }
function materialName(id: string) { const item = materials.value.find(value => value.id === id); return item ? `${item.materialCode} · ${item.name}` : id }
function stockLabel(material: MaterialDetailDto) { if (!session.canAccess(['inventory.read'])) return '无查看权限'; if (inventoryFailed.value) return '暂不可用'; const balance = balances.value.find(item => item.materialId === material.id); return balance ? `库存 ${balance.quantity} ${balance.unit}` : '库存未初始化' }
function addSupplier(variant: ProductVariantInput, event: Event) { const element = event.target as HTMLSelectElement; const id = element.value; element.value = ''; if (id && !variant.suppliers.some(item => item.supplierId === id)) variant.suppliers.push({ supplierId: id, primary: variant.suppliers.length === 0 }) }
function addMaterial(variant: ProductVariantInput, event: Event) { const element = event.target as HTMLSelectElement; const id = element.value; element.value = ''; const material = materials.value.find(item => item.id === id); if (material && !variant.materials.some(item => item.materialId === id)) variant.materials.push({ materialId: id, standardQuantity: 1, unit: material.unit, lossRate: 0, enabled: true }) }
function makePrimary(variant: ProductVariantInput, id: string) { variant.suppliers.forEach(item => { item.primary = item.supplierId === id }) }
async function uploadImage(variant: ProductVariantInput, index: number, event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; uploading.value = index; imageError.value = ''; try { variant.imageAssetId = (await catalogApi.uploadImage('SKU_MAIN', file)).id } catch (error) { imageError.value = error instanceof Error ? error.message : '图片上传失败。' } finally { uploading.value = null } }
async function loadReferences() { if (runtimeConfig.authMode !== 'password') return; loadingReferences.value = true; try { const materialResponse = await domainApi.materials(); materials.value = materialResponse.items.filter(item => item.status === 'ACTIVE'); if (session.canAccess(['inventory.read'])) { try { balances.value = (await domainApi.balances()).items } catch { inventoryFailed.value = true } } if (supplierAccess.value) suppliers.value = (await domainApi.suppliers({ status: 'ACTIVE', size: 100 })).items } finally { loadingReferences.value = false } }
function submit() { emit('submit', structuredClone(toRaw(model))) }
onMounted(loadReferences)
</script>

<template>
  <form
    class="page-stack"
    @submit.prevent="submit"
  >
    <section class="panel operation-form product-form">
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
      <label class="form-wide">描述<textarea
        v-model="model.description"
        maxlength="1000"
      /></label>
    </section>
    <p
      v-if="imageError"
      class="data-state data-state--error"
      role="alert"
    >
      {{ imageError }}
    </p>
    <section
      v-for="(variant, variantIndex) in model.variants"
      :key="variant.id ?? variantIndex"
      class="panel compact-panel variant-editor"
    >
      <div class="card-heading">
        <h2>SKU {{ variantIndex + 1 }}</h2><button
          type="button"
          class="danger-link"
          :disabled="model.variants.length === 1"
          @click="removeVariant(variantIndex)"
        >
          移除 SKU
        </button>
      </div>
      <div class="sku-editor-grid">
        <div class="catalog-image-editor">
          <AuthenticatedCatalogImage
            :image-id="variant.imageAssetId"
            alt="SKU 主图"
          />
          <label class="secondary-action">{{ uploading === variantIndex ? '上传中…' : '上传主图' }}<input
            class="sr-only"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            :disabled="uploading !== null"
            @change="uploadImage(variant, variantIndex, $event)"
          ></label>
          <button
            v-if="variant.imageAssetId"
            type="button"
            class="secondary-button"
            @click="variant.imageAssetId = undefined"
          >
            移除图片
          </button>
        </div>
        <div class="operation-form nested-form">
          <label>SKU 编码<input
            v-model="variant.skuCode"
            required
            maxlength="64"
          ></label>
          <label>SKU 名称<input
            v-model="variant.variantName"
            required
            maxlength="128"
          ></label>
          <label>产品尺寸<input
            v-model="variant.itemDimensions"
            maxlength="200"
          ></label>
          <label>包装尺寸<input
            v-model="variant.packageDimensions"
            maxlength="200"
          ></label>
          <label>包装重量（克）<input
            v-model.number="variant.packageWeightGrams"
            type="number"
            min="0.001"
            max="999999999.999"
            step="0.001"
          ></label>
          <label>状态<select v-model="variant.status"><option value="ACTIVE">启用</option><option value="DRAFT">待定</option><option value="DISABLED">停用</option></select></label>
        </div>
      </div>
      <section
        v-if="supplierAccess"
        class="relation-editor"
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
            v-for="supplier in suppliers"
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
          <input
            v-model="link.purchaseUrl"
            aria-label="采购链接"
            type="url"
            maxlength="500"
            placeholder="1688 HTTPS 采购链接"
          ><input
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
      <section class="relation-editor">
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
            v-for="material in materials"
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
    <div class="form-footer">
      <button
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
