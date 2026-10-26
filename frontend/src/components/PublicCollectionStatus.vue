<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ApiError, apiRequest } from '@/api/client'
const props = defineProps<{ kind: 'PRODUCT' | 'STORE'; targets: { id: string; label: string; enabled: boolean }[]; writable?: boolean }>()
const emit = defineEmits<{ updated: [] }>()
interface State { targetId: string; status: string | null; reason: string | null; nextAt: string; createdAt: string; enabled: boolean }
const states = ref<State[]>([]), error = ref(''), busy = ref(''), enabled = ref(false)
const labels: Record<string,string> = { QUEUED:'等待采集', RUNNING:'正在读取亚马逊前台', RETRY:'等待重试', FAILED:'采集失败', PARTIAL:'采集不完整', SUCCEEDED:'采集成功', CANCELLED:'任务已取消' }
let timer: ReturnType<typeof setInterval> | undefined
let fetching = false
const incompatible = ref(false)
async function load() {
 if (fetching) return
 fetching = true
 try {
  const status = await apiRequest<{ sources: { provider: string; approved: boolean }[] }>('/v1/collection/status')
  enabled.value = status.sources.some(s => s.provider === 'amazon-public-us' && s.approved)
  const next = await apiRequest<State[]>(`/v1/collection/targets?kind=${props.kind}`)
  const changed = next.some(n => ['SUCCEEDED','PARTIAL'].includes(n.status ?? '') && states.value.some(old => old.targetId === n.targetId && (old.createdAt !== n.createdAt || old.status !== n.status)))
  error.value = ''; incompatible.value = false
  states.value = next
  if (changed) emit('updated')
 } catch (e) { incompatible.value = e instanceof ApiError && e.status === 404; error.value = incompatible.value ? '当前页面连接的后端尚未部署自动采集接口。' : e instanceof Error ? e.message : '采集状态读取失败' } finally { fetching = false }
}
async function collect(id: string) {
 busy.value = id; error.value = ''
 try { await apiRequest(props.kind === 'PRODUCT' ? `/v1/product-monitoring/${id}/collect` : `/v1/watch/stores/${id}/collect`, { method:'POST' }); await load() } catch (e) { error.value = e instanceof Error ? e.message : '采集排队失败' } finally { busy.value = '' }
}
watch(() => props.targets.map(t=>t.id).join(','), () => void load())
onMounted(() => { void load(); timer = setInterval(() => void load(), 5000) })
onBeforeUnmount(() => clearInterval(timer))
</script>
<template>
  <article class="panel compact-panel">
    <strong>亚马逊前台自动监控 · {{ enabled ? '已启用' : '未启用' }}</strong>
    <p>添加后自动采集，后续每日更新。未观测字段保留历史值；公开搜索未出现的商品不会据此判为下架。</p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <div
      v-for="target in targets"
      :key="target.id"
      class="inline-actions public-status"
    >
      <span>{{ target.label }} · {{ !target.enabled ? '已暂停' : labels[states.find(s=>s.targetId===target.id)?.status ?? ''] ?? (states.some(s=>s.targetId===target.id) ? '等待调度' : '尚未排队') }}</span>
      <span>{{ states.find(s=>s.targetId===target.id)?.reason }}</span>
      <button
        v-if="writable !== false"
        :disabled="!enabled || !target.enabled || !!busy || ['RUNNING','QUEUED','RETRY'].includes(states.find(s=>s.targetId===target.id)?.status ?? '')"
        @click="collect(target.id)"
      >
        立即采集
      </button>
    </div>
  </article>
</template>
<style scoped>.public-status{border-top:1px solid #e2e8f0;padding:var(--ui-gap,8px) 0;overflow-wrap:anywhere}.public-status span{min-width:0}</style>
