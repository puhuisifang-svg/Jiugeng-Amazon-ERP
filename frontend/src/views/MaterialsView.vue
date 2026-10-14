<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { ApiError } from '@/api/client'
import { domainApi, type MaterialDetailDto, type MaterialInput } from '@/api/domain'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const items = ref<MaterialDetailDto[]>([])
const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref('')
const editingId = ref<string | null>(null)
const form = reactive<MaterialInput>({ materialCode: '', name: '', materialType: '', specification: '', unit: 'PCS', imageUrl: '', remark: '' })
const writable = computed(() => session.canAccess(['materials.write']))
const formal = computed(() => runtimeConfig.authMode === 'password')

function resetForm() {
  Object.assign(form, { materialCode: '', name: '', materialType: '', specification: '', unit: 'PCS', imageUrl: '', remark: '' })
  editingId.value = null
}
function edit(item: MaterialDetailDto) {
  Object.assign(form, { materialCode: item.materialCode, name: item.name, materialType: item.materialType,
    specification: item.specification ?? '', unit: item.unit, imageUrl: item.imageUrl ?? '', remark: item.remark ?? '' })
  editingId.value = item.id; success.value = ''; error.value = ''
}
async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await domainApi.materials()).items }
  catch { error.value = formal.value ? '辅料数据加载失败。' : '辅料模拟数据加载失败。' }
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
      items.value = items.value.map(item => item.id === updated.id ? updated : item); success.value = '辅料已更新。'
    } else {
      const response = await domainApi.createMaterial(input)
      items.value.unshift(response.item); success.value = response.mockData ? '模拟辅料已创建。' : '辅料已创建。'
    }
    resetForm()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '保存失败：请检查字段与编码唯一性。' }
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
  <section class="page-stack">
    <SyntheticNotice v-if="!formal" />
    <form
      v-if="writable"
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
      <button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '保存中…' : editingId ? '保存修改' : '新增辅料' }}
      </button>
      <button
        v-if="editingId"
        class="secondary-button"
        type="button"
        @click="resetForm"
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
      v-else-if="!items.length"
      class="panel empty-state"
    >
      <h2>暂无辅料</h2><p>创建第一条辅料主数据。</p>
    </article>
    <article
      v-else
      class="panel table-panel"
    >
      <table class="data-table">
        <thead>
          <tr>
            <th>编码</th><th>名称</th><th>类型</th><th>规格</th><th>单位</th><th>状态</th><th v-if="formal && writable">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td>{{ item.materialCode }}</td><td>{{ item.name }}</td><td>{{ item.materialType }}</td>
            <td>{{ item.specification || '—' }}</td><td>{{ item.unit }}</td><td>{{ item.status }}</td>
            <td
              v-if="formal && writable"
              class="table-actions"
            >
              <button
                type="button"
                :disabled="item.status === 'DISABLED' || saving"
                @click="edit(item)"
              >
                编辑
              </button>
              <button
                type="button"
                class="danger-button"
                :disabled="item.status === 'DISABLED' || saving"
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
