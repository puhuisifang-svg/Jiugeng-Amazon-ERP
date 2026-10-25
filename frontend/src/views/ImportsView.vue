<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { activeImportTypes, importTemplateVersions, importsApi, type ImportBatch, type ImportType } from '@/api/imports'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const props = withDefaults(defineProps<{ importBasePath?: string }>(), { importBasePath: '/imports' })
const batches = ref<ImportBatch[]>([])
const type = ref<ImportType>('PRODUCTS')
const file = ref<File | null>(null)
const loading = ref(false); const saving = ref(false); const error = ref(''); const success = ref('')
const formal = runtimeConfig.authMode === 'password'
const canUpload = computed(() => session.canAccess(['imports.validate']))

const labels: Record<ImportType, string> = {
  PRODUCTS: '产品', VARIANTS: 'SKU', AUXILIARY_MATERIALS: '辅料主档',
  PRODUCT_AUXILIARY_MATERIALS: 'SKU—辅料关系', AUXILIARY_MATERIAL_OPENING_INVENTORY: '辅料期初库存',
}

async function load() {
  if (!formal) return
  loading.value = true; error.value = ''
  try { batches.value = await importsApi.list() }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '导入批次加载失败。' }
  finally { loading.value = false }
}
function choose(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0] ?? null }
async function upload() {
  if (!file.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const receipt = await importsApi.upload(type.value, file.value)
    success.value = receipt.replayed ? '已复用相同上传请求。' : '文件已进入校验队列。'
    await load()
  } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '上传失败。' }
  finally { saving.value = false }
}
async function downloadTemplate() {
  error.value = ''
  try { await importsApi.downloadTemplate(type.value) }
  catch (exception) { error.value = exception instanceof ApiError ? exception.message : '模板下载失败。' }
}
onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice v-if="!formal" />
    <div class="page-heading">
      <div>
        <p class="eyebrow">
          数据导入 · 五类模板
        </p><h1>导入中心</h1><p>上传、校验、预览与批次状态均来自正式导入运行时。</p>
      </div>
    </div>
    <article
      v-if="!formal"
      class="panel empty-state"
    >
      <h2>模拟模式不执行导入</h2><p>请切换到密码登录与 MySQL 模式后使用五类正式导入；历史 STORE_LISTINGS 不在活跃范围。</p>
    </article>
    <form
      v-else-if="canUpload"
      class="panel operation-form"
      @submit.prevent="upload"
    >
      <h2>上传 XLSX</h2>
      <label>导入类型<select v-model="type"><option
        v-for="item in activeImportTypes"
        :key="item"
        :value="item"
      >{{ labels[item] }}</option></select></label>
      <label>模板版本<input
        :value="importTemplateVersions[type]"
        readonly
      ></label>
      <label>文件（最大 10 MiB）<input
        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        required
        type="file"
        @change="choose"
      ></label>
      <button
        :disabled="saving || !file"
        type="submit"
      >
        {{ saving ? '上传中…' : '上传并校验' }}
      </button>
      <button
        class="secondary-button"
        type="button"
        @click="downloadTemplate"
      >
        下载当前模板
      </button>
    </form>
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
      v-else-if="formal"
      class="panel table-panel"
    >
      <TableViewport>
        <table class="data-table">
          <thead><tr><th>创建时间</th><th>类型</th><th>状态</th><th>进度</th><th>错误/警告</th><th>详情</th></tr></thead>
          <tbody>
            <tr
              v-for="item in batches"
              :key="item.id"
            >
              <td>{{ new Date(item.createdAt).toLocaleString() }}</td><td>{{ labels[item.importType] }}</td><td>{{ item.status }}</td><td>{{ item.percent }}%</td><td>{{ item.errorCount }} / {{ item.warningCount }}</td><td>
                <RouterLink :to="`${props.importBasePath}/${item.id}`">
                  查看
                </RouterLink>
              </td>
            </tr>
            <tr v-if="!batches.length">
              <td colspan="6">
                暂无导入批次。
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </article>
  </section>
</template>
