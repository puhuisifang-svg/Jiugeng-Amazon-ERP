import { onBeforeUnmount, onMounted, ref } from 'vue'
import { apiRequest } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
export interface CollectionState { targetId: string; status: string | null; reason: string | null; createdAt: string }
export function useCollectionStatus(kind: 'PRODUCT' | 'STORE', updated: () => void) {
  const states = ref<CollectionState[]>([]), enabled = ref(false), error = ref(''), busy = ref('')
  let timer: ReturnType<typeof setInterval> | undefined, fetching = false, stopped = false
  const labels: Record<string, string> = { QUEUED: '待采集', RUNNING: '采集中', RETRY: '等待重试', FAILED: '采集失败', PARTIAL: '部分更新', SUCCEEDED: '已更新', CANCELLED: '已取消' }
  function state(id: string) { return states.value.find(s => s.targetId === id) }
  function label(id: string) { return labels[state(id)?.status ?? ''] ?? '等待首次采集' }
  function pending(id: string) { return ['QUEUED','RUNNING','RETRY'].includes(state(id)?.status ?? '') || busy.value === id }
  async function refresh() {
    if (fetching || stopped || runtimeConfig.authMode === 'mock') return
    fetching = true
    try {
      const [source, next] = await Promise.all([apiRequest<{ sources: { provider: string; approved: boolean }[] }>('/v1/collection/status'), apiRequest<CollectionState[]>(`/v1/collection/targets?kind=${kind}`)])
      if (stopped) return
      enabled.value = source.sources.some(s => s.provider === 'amazon-public-us' && s.approved)
      const changed = next.some(n => ['SUCCEEDED','PARTIAL'].includes(n.status ?? '') && (!state(n.targetId) || state(n.targetId)?.createdAt !== n.createdAt || state(n.targetId)?.status !== n.status))
      states.value = next; error.value = ''; if (changed) updated()
    } catch (e) { if (!stopped) error.value = e instanceof Error ? e.message : '更新状态读取失败' } finally { fetching = false }
  }
  async function collect(id: string) {
    busy.value = id; error.value = ''
    try { await apiRequest(kind === 'PRODUCT' ? `/v1/product-monitoring/${id}/collect` : `/v1/watch/stores/${id}/collect`, { method: 'POST' }); await refresh(); return true }
    catch (e) { error.value = e instanceof Error ? e.message : '采集排队失败'; return false } finally { busy.value = '' }
  }
  onMounted(() => { void refresh(); timer = setInterval(() => void refresh(), 5000) })
  onBeforeUnmount(() => { stopped = true; clearInterval(timer) })
  return { states, enabled, error, busy, state, label, pending, collect, refresh }
}
