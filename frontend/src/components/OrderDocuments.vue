<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { apiRequest, getSessionToken, ApiError, SESSION_EXPIRED_EVENT } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
const props = defineProps<{ orderId: string; scope: 'ORDER' | 'INVOICE'; writable: boolean }>()
const rows = ref<Array<{ id: string; fileName: string; byteSize: number }>>([]), busy = ref(false), error = ref(''), success = ref('')
import SuccessToast from './SuccessToast.vue'
const path = () => `/v1/customer-orders/${encodeURIComponent(props.orderId)}/documents/${props.scope}`
async function load() { try { rows.value = await apiRequest(path()) } catch (e) { error.value = String(e) } }
async function request(suffix: string, init: { method?: string; body?: FormData }) {
  const headers = new Headers(); const token = getSessionToken(); if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${runtimeConfig.apiBaseUrl}${path()}${suffix}`, { ...init, headers })
  if (!response.ok) { if (response.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT)); const p = await response.json().catch(() => null); throw new ApiError(response.status, p?.code ?? 'DOCUMENT_FAILED', p?.message ?? '附件操作失败') }
  return response
}
async function upload(event: Event) {
  const target = event.target as HTMLInputElement, file = target.files?.[0]; if (!file || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { if (file.size > 10 * 1024 * 1024) throw new Error('附件不得超过10MB'); const body = new FormData(); body.set('file', file); await request('', { method: 'POST', body }); success.value = '附件已上传。'; await load() } catch (e) { error.value = String(e) } finally { busy.value = false; target.value = '' }
}
async function download(item: typeof rows.value[number]) { busy.value = true; error.value = ''; try { const response = await request(`/${item.id}`, {}); const url = URL.createObjectURL(await response.blob()); const a = document.createElement('a'); a.href = url; a.download = item.fileName; a.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) } catch (e) { error.value = String(e) } finally { busy.value = false } }
watch(() => [props.orderId,props.scope], load); onMounted(load)
</script>
<template>
  <section>
    <h3>{{ scope === 'ORDER' ? '合同 / 订单附件' : '发票附件' }}</h3><SuccessToast :message="success" /><p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p><label v-if="writable">上传附件（PDF、PNG、JPG，≤10MB）<input
      type="file"
      accept=".pdf,.png,.jpg,.jpeg"
      :disabled="busy"
      @change="upload"
    ></label><p v-if="!rows.length">
      暂无附件
    </p><p
      v-for="item in rows"
      :key="item.id"
    >
      <button
        type="button"
        :disabled="busy"
        @click="download(item)"
      >
        {{ item.fileName }}
      </button>
    </p>
  </section>
</template>
