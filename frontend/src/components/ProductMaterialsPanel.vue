<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { ApiError } from '@/api/client'
import { domainApi, type MaterialDetailDto, type ProductDetailDto, type ProductMaterialRelationDto } from '@/api/domain'
import { session } from '@/state/session'

const props = defineProps<{ product: ProductDetailDto }>()
const relations = ref<ProductMaterialRelationDto[]>([])
const materials = ref<MaterialDetailDto[]>([])
const loading = ref(true); const busy = ref(false); const error = ref(''); const success = ref('')
const editingId = ref<string | null>(null); const applyAll = ref(false)
const form = reactive({ variantId: '', materialId: '', standardQuantity: 1, unit: 'PCS', lossRate: 0, remark: '' })
const writable = computed(() => props.product.status !== 'DISABLED'
  && session.canAccess(['products.write', 'materials.write']))
const activeVariants = computed(() => props.product.variants.filter(item => item.id && item.status === 'ACTIVE'))
const activeMaterials = computed(() => materials.value.filter(item => item.status === 'ACTIVE'))

function reset() {
  Object.assign(form, { variantId: activeVariants.value[0]?.id ?? '', materialId: '', standardQuantity: 1,
    unit: 'PCS', lossRate: 0, remark: '' })
  editingId.value = null; applyAll.value = false
}
function edit(item: ProductMaterialRelationDto) {
  Object.assign(form, { variantId: item.variantId, materialId: item.materialId,
    standardQuantity: item.standardQuantity, unit: item.unit, lossRate: item.lossRate, remark: item.remark ?? '' })
  editingId.value = item.id; applyAll.value = false; error.value = ''; success.value = ''
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const [relationItems, materialResponse] = await Promise.all([
      domainApi.productMaterialRelations({ productId: props.product.id }), domainApi.materials(),
    ])
    relations.value = relationItems; materials.value = materialResponse.items; reset()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '对应辅料加载失败。' }
  finally { loading.value = false }
}
async function save() {
  busy.value = true; error.value = ''; success.value = ''
  const base = { productId: props.product.id, materialId: form.materialId,
    standardQuantity: Number(form.standardQuantity), unit: form.unit.trim(), lossRate: Number(form.lossRate),
    ...(form.remark.trim() ? { remark: form.remark.trim() } : {}) }
  try {
    if (editingId.value) {
      const updated = await domainApi.updateProductMaterialRelation(editingId.value,
        { ...base, variantId: form.variantId, enabled: true })
      relations.value = relations.value.map(item => item.id === updated.id ? updated : item)
      success.value = 'SKU 辅料关系已调整。'
    } else if (applyAll.value) {
      relations.value = await domainApi.applyMaterialToAllVariants(base)
      success.value = '辅料已应用到全部启用 SKU；已有关系未被覆盖。'
    } else {
      const created = await domainApi.createProductMaterialRelation({ ...base, variantId: form.variantId })
      relations.value.push(created); success.value = 'SKU 辅料关系已新增。'
    }
    reset()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '对应辅料保存失败。' }
  finally { busy.value = false }
}
async function disable(item: ProductMaterialRelationDto) {
  if (!window.confirm(`确认停用 ${item.skuCode} 与 ${item.materialCode} 的关系？`)) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    const disabled = await domainApi.disableProductMaterialRelation(item.id)
    relations.value = relations.value.map(value => value.id === disabled.id ? disabled : value)
    success.value = 'SKU 辅料关系已停用。'
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '停用失败。' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <h2>对应辅料</h2>
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="save"
    >
      <h2>{{ editingId ? '调整 SKU 辅料' : '新增 SKU 辅料' }}</h2>
      <label>SKU<select
        v-model="form.variantId"
        required
        :disabled="Boolean(editingId) || applyAll"
      >
        <option
          value=""
          disabled
        >请选择 SKU</option><option
          v-for="variant in activeVariants"
          :key="variant.id"
          :value="variant.id"
        >{{ variant.skuCode }} · {{ variant.variantName }}</option>
      </select></label>
      <label>辅料<select
        v-model="form.materialId"
        required
        :disabled="Boolean(editingId)"
      >
        <option
          value=""
          disabled
        >请选择辅料</option><option
          v-for="material in activeMaterials"
          :key="material.id"
          :value="material.id"
        >{{ material.materialCode }} · {{ material.name }}</option>
      </select></label>
      <label>标准用量<input
        v-model.number="form.standardQuantity"
        required
        min="0.0001"
        step="0.0001"
        type="number"
      ></label>
      <label>单位<input
        v-model="form.unit"
        required
        maxlength="32"
      ></label>
      <label>损耗率（0～1）<input
        v-model.number="form.lossRate"
        required
        min="0"
        max="1"
        step="0.000001"
        type="number"
      ></label>
      <label class="form-wide">备注<textarea
        v-model="form.remark"
        maxlength="1000"
      /></label>
      <label v-if="!editingId"><input
        v-model="applyAll"
        type="checkbox"
      > 应用到全部启用 SKU</label>
      <button
        :disabled="busy || !activeMaterials.length || !activeVariants.length"
        type="submit"
      >
        {{ busy ? '保存中…' : '保存关系' }}
      </button>
      <button
        v-if="editingId"
        class="secondary-button"
        type="button"
        @click="reset"
      >
        取消
      </button>
    </form>
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
      正在加载对应辅料…
    </p>
    <article
      v-else-if="!relations.length"
      class="panel empty-state"
    >
      <p>尚未维护 SKU 对应辅料。</p>
    </article>
    <article
      v-else
      class="panel table-panel"
    >
      <table class="data-table">
        <thead>
          <tr>
            <th>SKU</th><th>辅料</th><th>标准用量</th><th>损耗率</th><th>状态</th><th v-if="writable">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in relations"
            :key="item.id"
          >
            <td>{{ item.skuCode }}</td><td>{{ item.materialCode }} · {{ item.materialName }}</td>
            <td>{{ item.standardQuantity }} {{ item.unit }}</td><td>{{ item.lossRate }}</td><td>{{ item.enabled ? '启用' : '停用' }}</td>
            <td
              v-if="writable"
              class="table-actions"
            >
              <button
                type="button"
                :disabled="!item.enabled || busy"
                @click="edit(item)"
              >
                调整
              </button>
              <button
                type="button"
                class="danger-button"
                :disabled="!item.enabled || busy"
                @click="disable(item)"
              >
                停用
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
