<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { catalogApi, type SkuDocument } from '@/api/catalog'
import { session } from '@/state/session'
const props = defineProps<{ productId: string; variantId: string; kind: SkuDocument['kind'] }>()
const files = ref<SkuDocument[]>([])
const loading = ref(false); const uploading = ref(false); const downloading = ref('')
const error = ref(''); const success = ref('')
async function load() {
  loading.value = true; error.value = ''
  try { files.value = await catalogApi.documents(props.productId, props.variantId, props.kind) }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '文件列表加载失败。' }
  finally { loading.value = false }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]
  if (!file || uploading.value) return
  error.value = ''; success.value = ''
  if (!file.size || file.size > 10 * 1024 * 1024) { error.value = '文件不能为空，且不能超过 10 MB。'; input.value = ''; return }
  uploading.value = true
  try {
    const uploaded = await catalogApi.uploadDocument(props.productId, props.variantId, props.kind, file)
    files.value.unshift(uploaded); success.value = '上传成功。'
  } catch (exception) { error.value = exception instanceof Error ? exception.message : '上传失败，请重试。' }
  finally { uploading.value = false; input.value = '' }
}
async function download(file: SkuDocument) {
  downloading.value = file.id; error.value = ''
  try { await catalogApi.downloadDocument(props.productId, props.variantId, file) }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '下载失败，请重试。' }
  finally { downloading.value = '' }
}
onMounted(load)
</script>

<template>
  <section class="sku-documents">
    <div class="material-toolbar">
      <div><h3>{{ kind === 'MANUAL' ? '说明书管理' : '商品标签管理' }}</h3></div>
      <label
        v-if="session.canAccess(['products.write'])"
        class="secondary-action"
      >
        {{ uploading ? '上传中…' : '上传文件' }}
        <input
          type="file"
          class="sr-only"
          accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.txt,.zpl"
          :disabled="uploading || loading"
          aria-label="上传文件"
          @change="upload"
        >
      </label>
    </div>
    <p class="document-hint">
      支持 PDF、PNG、JPG、DOCX、XLSX、TXT、ZPL，每个文件不超过 10 MB。重复上传保留为独立文件。
    </p>
    <p
      v-if="error"
      role="alert"
      class="data-state data-state--error"
    >
      {{ error }} <button
        type="button"
        class="secondary-action"
        @click="load"
      >
        刷新列表
      </button>
    </p>
    <p
      v-if="success"
      role="status"
      class="data-state data-state--success"
    >
      {{ success }}
    </p>
    <p v-if="loading">
      正在加载文件…
    </p>
    <p v-else-if="!files.length && !error">
      暂无文件。
    </p>
    <div
      v-else
      class="table-scroll"
    >
      <table class="data-table">
        <thead><tr><th>文件名</th><th>大小</th><th>上传时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr
            v-for="file in files"
            :key="file.id"
          >
            <td class="document-name">
              {{ file.fileName }}
            </td><td>{{ Math.max(1, Math.ceil(file.byteSize / 1024)) }} KB</td>
            <td>{{ new Date(file.uploadedAt).toLocaleString('zh-CN') }}</td>
            <td>
              <button
                type="button"
                class="secondary-button"
                :disabled="!!downloading"
                @click="download(file)"
              >
                {{ downloading === file.id ? '下载中…' : '下载' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
