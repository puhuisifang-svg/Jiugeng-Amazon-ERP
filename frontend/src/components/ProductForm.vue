<script setup lang="ts">
import { reactive, toRaw } from 'vue'

import type { ProductInput, ProductVariantDto } from '@/api/domain'

const props = defineProps<{ initial?: ProductInput; busy?: boolean; submitLabel: string }>()
const emit = defineEmits<{ submit: [value: ProductInput] }>()

function blankVariant(): ProductVariantDto {
  return {
    skuCode: '', variantName: '', status: 'ACTIVE',
    attributes: [{ attributeCode: 'color', attributeValue: '', displayOrder: 0 }],
  }
}

const model = reactive<ProductInput>(structuredClone(props.initial ?? {
  productCode: '', nameZh: '', status: 'ACTIVE', variants: [blankVariant()],
}))

function addVariant() { model.variants.push(blankVariant()) }
function removeVariant(index: number) { if (model.variants.length > 1) model.variants.splice(index, 1) }
function addAttribute(variant: ProductVariantDto) {
  variant.attributes.push({ attributeCode: '', attributeValue: '', displayOrder: variant.attributes.length })
}
function removeAttribute(variant: ProductVariantDto, index: number) {
  if (variant.attributes.length > 1) variant.attributes.splice(index, 1)
}
function submit() { emit('submit', structuredClone(toRaw(model))) }
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
      ></label>
      <label>中文名称<input
        v-model="model.nameZh"
        required
        maxlength="200"
      ></label>
      <label>英文名称<input
        v-model="model.nameEn"
        maxlength="200"
      ></label>
      <label>品牌<input
        v-model="model.brand"
        maxlength="100"
      ></label>
      <label>内部类目<input
        v-model="model.internalCategory"
        maxlength="100"
      ></label>
      <label>产品类型<input
        v-model="model.productType"
        maxlength="100"
      ></label>
      <label>状态<select v-model="model.status"><option value="DRAFT">草稿</option><option value="ACTIVE">启用</option></select></label>
      <label class="form-wide">描述<textarea
        v-model="model.description"
        maxlength="1000"
      /></label>
    </section>

    <section
      v-for="(variant, variantIndex) in model.variants"
      :key="variant.id ?? variantIndex"
      class="panel compact-panel variant-editor"
    >
      <div class="card-heading">
        <h2>SKU {{ variantIndex + 1 }}</h2>
        <button
          type="button"
          class="danger-link"
          :disabled="model.variants.length === 1"
          @click="removeVariant(variantIndex)"
        >
          移除 SKU
        </button>
      </div>
      <div class="operation-form nested-form">
        <label>SKU 编码<input
          v-model="variant.skuCode"
          required
          maxlength="64"
        ></label>
        <label>规格名称<input
          v-model="variant.variantName"
          required
          maxlength="200"
        ></label>
        <label>商品条码<input
          v-model="variant.barcode"
          maxlength="64"
        ></label>
        <label>材质<input
          v-model="variant.material"
          maxlength="200"
        ></label>
        <label>内部规格<input
          v-model="variant.specification"
          maxlength="300"
        ></label>
        <label>产品尺寸<input
          v-model="variant.itemDimensions"
          maxlength="200"
        ></label>
        <label>包装尺寸<input
          v-model="variant.packageDimensions"
          maxlength="200"
        ></label>
        <label>包装重量<input
          v-model.number="variant.packageWeight"
          type="number"
          min="0"
          step="0.0001"
        ></label>
        <label>重量单位<select
          v-model="variant.packageWeightUnit"
          :required="variant.packageWeight != null"
        ><option value="">未填写</option><option value="G">G</option><option value="KG">KG</option><option value="OZ">OZ</option><option value="LB">LB</option></select></label>
        <label>状态<select v-model="variant.status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label>
      </div>
      <h3>动态属性</h3>
      <div
        v-for="(attribute, attributeIndex) in variant.attributes"
        :key="attributeIndex"
        class="attribute-row"
      >
        <input
          v-model="attribute.attributeCode"
          aria-label="属性编码"
          required
          maxlength="64"
          placeholder="属性编码，例如 color"
        >
        <input
          v-model="attribute.attributeValue"
          aria-label="属性值"
          required
          maxlength="300"
          placeholder="属性值，例如 Black"
        >
        <input
          v-model.number="attribute.displayOrder"
          aria-label="展示顺序"
          type="number"
          min="0"
        >
        <button
          type="button"
          class="secondary-button"
          :disabled="variant.attributes.length === 1"
          @click="removeAttribute(variant, attributeIndex)"
        >
          移除
        </button>
      </div>
      <button
        type="button"
        class="secondary-action"
        @click="addAttribute(variant)"
      >
        新增属性
      </button>
    </section>

    <div class="form-footer">
      <button
        type="button"
        class="secondary-action"
        @click="addVariant"
      >
        新增 SKU
      </button>
      <button
        type="submit"
        :disabled="busy"
      >
        {{ busy ? '保存中…' : submitLabel }}
      </button>
    </div>
  </form>
</template>
