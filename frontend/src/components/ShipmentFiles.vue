<script setup lang="ts">
import { ref, watch } from 'vue'
import { dashboardApi, type ShipmentDocument } from '@/api/dashboard'
import { ApiError } from '@/api/client'
import SuccessToast from './SuccessToast.vue'

const props = defineProps<{ shipmentId: string; scope: 'today-shipments' | 'pending-shipments'; kind: ShipmentDocument['kind']; editable?: boolean }>()
const files = ref<ShipmentDocument[]>([]); const loading = ref(false); const busy = ref(false); const error = ref(''); const success = ref('')
let sequence = 0
async function load() {
  const request = ++sequence; loading.value = true; error.value = ''
  try { const result = await dashboardApi.documents(props.scope, props.shipmentId, props.kind); if (request === sequence) files.value = result }
  catch { if (request === sequence) error.value = '文件加载失败，请重试。' }
  finally { if (request === sequence) loading.value = false }
}
watch(() => [props.scope, props.shipmentId, props.kind], () => { files.value = []; void load() }, { immediate: true })
async function upload(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]
  if (!file || busy.value) return
  error.value = ''; success.value = ''
  if (!file.size || file.size > 10 * 1024 * 1024) { error.value = '文件不能为空，且不能超过 10 MB。'; input.value = ''; return }
  busy.value = true
  try { const document = await dashboardApi.uploadDocument(props.shipmentId, props.kind, file); files.value.unshift(document); success.value = '标签上传成功。' }
  catch (e) { error.value = e instanceof ApiError ? e.message : '上传失败，请重试。' }
  finally { busy.value = false; input.value = '' }
}
async function download(file: ShipmentDocument) {
  busy.value = true; error.value = ''
  try { await dashboardApi.downloadDocument(props.scope, props.shipmentId, file) }
  catch (e) { error.value = e instanceof ApiError ? e.message : '下载失败，请重试。' }
  finally { busy.value = false }
}
</script>

<template>
  <div class="shipment-files">
    <small v-if="loading">加载中…</small>
    <span
      v-else-if="!files.length && !error"
      class="muted"
    >未上传</span>
    <button
      v-for="file in files"
      :key="file.id"
      type="button"
      class="shipment-file-link"
      :disabled="busy"
      :title="file.fileName"
      @click="download(file)"
    >
      ↓ {{ file.fileName }}
    </button>
    <label
      v-if="editable"
      class="shipment-upload"
    >{{ busy ? '处理中…' : '+ 上传文件' }}<input
      type="file"
      accept=".pdf,.png,.jpg,.jpeg,.txt,.zpl"
      :aria-label="kind === 'LABEL' ? '上传商品标签' : '上传外箱标签'"
      :disabled="busy || loading"
      @change="upload"
    ></label>
    <small v-if="editable">PDF / 图片 / TXT / ZPL，≤10 MB</small>
    <p
      v-if="error"
      class="shipment-file-error"
      role="alert"
    >
      {{ error }} <button
        type="button"
        :disabled="busy || loading"
        @click="load"
      >
        重新加载
      </button>
    </p>
    <SuccessToast :message="success" />
  </div>
</template>
