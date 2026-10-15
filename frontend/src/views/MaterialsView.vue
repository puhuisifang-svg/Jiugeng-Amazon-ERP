<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { domainApi, type InventoryBalanceViewDto, type InventoryCommandType, type MaterialDetailDto, type MaterialInput, type SupplierAssignmentDto, type SupplierDto } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const items = ref<MaterialDetailDto[]>([])
const balances = ref<InventoryBalanceViewDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const editingId = ref<string | null>(null); const showMaterialForm = ref(false); const showInventoryForm = ref(false)
const supplierOptions = ref<SupplierDto[]>([]); const supplierLinks = ref<SupplierAssignmentDto[]>([])
const form = reactive<MaterialInput>({ materialCode: '', name: '', materialType: '', specification: '', unit: 'PCS', imageUrl: '', remark: '' })
const filters = reactive({ keyword: '', materialType: '', status: '', stock: '' })
const inventoryForm = reactive<{ materialId: string; materialCode: string; type: InventoryCommandType | 'OPENING' | 'ADJUSTMENT'; quantity: number; reason: string }>({
  materialId: '', materialCode: '', type: 'INBOUND', quantity: 0, reason: '日常库存操作',
})
const safetyValues = ref<Record<string, number>>({})
const writable = computed(() => session.canAccess(['materials.write']))
const formal = computed(() => runtimeConfig.authMode === 'password')
const mockMode = computed(() => runtimeConfig.authMode === 'mock')
const inventoryReadable = computed(() => session.canAccess(['inventory.read']))
const inventoryWritable = computed(() => inventoryReadable.value && (mockMode.value || session.canAccess(['inventory.write'])))
const importReadable = computed(() => session.canAccess(['imports.read']))
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
function resetForm() {
  Object.assign(form, { materialCode: '', name: '', materialType: '', specification: '', unit: 'PCS', imageUrl: '', remark: '' })
  editingId.value = null; supplierLinks.value = []; showMaterialForm.value = false
}
function resetFilters() { Object.assign(filters, { keyword: '', materialType: '', status: '', stock: '' }) }
async function edit(item: MaterialDetailDto) {
  Object.assign(form, { materialCode: item.materialCode, name: item.name, materialType: item.materialType,
    specification: item.specification ?? '', unit: item.unit, imageUrl: item.imageUrl ?? '', remark: item.remark ?? '' })
  editingId.value = item.id; showMaterialForm.value = true; success.value = ''; error.value = ''
  if (session.canAccess(['suppliers.read'])) supplierLinks.value = (await domainApi.materialSupplierLinks(item.id)).map(link => ({ supplierId: link.supplierId, purchaseUrl: link.purchaseUrl, remark: link.remark, primary: link.primary }))
}
function openInventory(item?: MaterialDetailDto) {
  inventoryForm.materialId = item?.id ?? ''; inventoryForm.materialCode = item?.materialCode ?? ''
  inventoryForm.type = 'INBOUND'; inventoryForm.quantity = 0; inventoryForm.reason = '日常库存操作'
  showInventoryForm.value = true; success.value = ''; error.value = ''
}
function addSupplier(event: Event) { const select = event.target as HTMLSelectElement; const id = select.value; select.value = ''; if (id && !supplierLinks.value.some(item => item.supplierId === id)) supplierLinks.value.push({ supplierId: id, primary: supplierLinks.value.length === 0 }) }
function supplierName(id: string) { return supplierOptions.value.find(item => item.id === id)?.name ?? id }
function makePrimary(id: string) { supplierLinks.value.forEach(item => { item.primary = item.supplierId === id }) }
function newRequestId() { return `web-${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() {
  loading.value = true; error.value = ''
  try {
    const materialResponse = await domainApi.materials()
    items.value = materialResponse.items
    if (session.canAccess(['suppliers.read'])) supplierOptions.value = (await domainApi.suppliers({ status: 'ACTIVE', size: 100 })).items
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
      ...(form.remark?.trim() ? { remark: form.remark.trim() } : {}) }
    if (editingId.value) {
      const updated = await domainApi.updateMaterial(editingId.value, input)
      await domainApi.replaceMaterialSupplierLinks(updated.id, supplierLinks.value)
      items.value = items.value.map(item => item.id === updated.id ? updated : item); success.value = '辅料及供应商关系已更新。'
    } else {
      const response = await domainApi.createMaterial(input)
      items.value.unshift(response.item); success.value = response.mockData ? '模拟辅料已创建。' : '辅料已创建。'
    }
    resetForm()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败：请检查字段与编码唯一性。' }
  finally { saving.value = false }
}
async function submitInventory() {
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
  } catch { error.value = '提交失败：请检查数量、启用状态和负库存约束。' }
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
async function disable(item: MaterialDetailDto) {
  if (!window.confirm(`确认停用辅料 ${item.materialCode}？`)) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const disabled = await domainApi.disableMaterial(item.id)
    items.value = items.value.map(value => value.id === disabled.id ? disabled : value); success.value = '辅料已停用。'
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '辅料停用失败。' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-stack material-management">
    <SyntheticNotice v-if="!formal" />
    <form
      class="panel material-filters"
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
          @click="showMaterialForm = true"
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
        <RouterLink
          v-if="importReadable"
          class="secondary-action"
          to="/imports"
        >
          导入
        </RouterLink>
      </div>
      <RouterLink
        v-if="inventoryReadable"
        class="text-link"
        to="/inventory/ledger"
      >
        查看库存流水
      </RouterLink>
    </section>
    <form
      v-if="writable && showMaterialForm"
      class="panel operation-form"
      @submit.prevent="save"
    >
      <h2>{{ editingId ? '编辑辅料' : formal ? '新增辅料' : '新增模拟辅料' }}</h2>
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
      <label>单位<input
        v-model="form.unit"
        required
        maxlength="32"
      ></label>
      <label>图片 HTTPS 地址<input
        v-model="form.imageUrl"
        maxlength="500"
        type="url"
      ></label>
      <label class="form-wide">备注<textarea
        v-model="form.remark"
        maxlength="1000"
      /></label>
      <section
        v-if="editingId && session.canAccess(['suppliers.read'])"
        class="form-wide relation-editor"
      >
        <h3>关联供应商</h3>
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
      <button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '保存中…' : editingId ? '保存修改' : '新增辅料' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        @click="resetForm"
      >
        取消
      </button>
    </form>
    <form
      v-if="inventoryWritable && showInventoryForm"
      class="panel operation-form"
      @submit.prevent="submitInventory"
    >
      <h2>提交{{ mockMode ? '模拟' : '正式' }}库存命令</h2>
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
      <label>类型<select v-model="inventoryForm.type"><template v-if="mockMode"><option>OPENING</option><option>INBOUND</option><option>OUTBOUND</option><option>ADJUSTMENT</option></template><template v-else><option>INBOUND</option><option>OUTBOUND</option><option>ADJUST_IN</option><option>ADJUST_OUT</option></template></select></label>
      <label>数量<input
        v-model.number="inventoryForm.quantity"
        required
        min="0.0001"
        type="number"
        step="0.0001"
      ></label>
      <label class="form-wide">原因<input
        v-model="inventoryForm.reason"
        required
        maxlength="500"
      ></label>
      <button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '提交中…' : '提交流水' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        @click="showInventoryForm = false"
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
              <th>图片</th><th>辅料编码</th><th>品名 / 类型</th><th>规格</th><th v-if="inventoryReadable">
                现存量
              </th><th v-if="inventoryReadable">
                安全库存
              </th><th>单位</th><th v-if="inventoryReadable">
                库存状态
              </th><th>主档状态</th><th v-if="writable || inventoryWritable">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filteredItems"
              :key="item.id"
            >
              <td>
                <img
                  v-if="item.imageUrl"
                  class="material-image"
                  :src="item.imageUrl"
                  :alt="item.name"
                ><span
                  v-else
                  class="material-image material-image--placeholder"
                  aria-hidden="true"
                >辅</span>
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
              <td
                v-if="writable || inventoryWritable"
                class="table-actions"
              >
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
                  v-if="formal && writable"
                  type="button"
                  class="danger-button"
                  :disabled="item.status === 'DISABLED' || saving"
                  @click="disable(item)"
                >
                  停用
                </button>
              </td>
            </tr>
            <tr v-if="!filteredItems.length">
              <td :colspan="6 + (inventoryReadable ? 3 : 0) + (writable || inventoryWritable ? 1 : 0)">
                没有符合条件的辅料。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="material-table-footer">
        共 {{ filteredItems.length }} 条
      </footer>
    </article>
  </section>
</template>
