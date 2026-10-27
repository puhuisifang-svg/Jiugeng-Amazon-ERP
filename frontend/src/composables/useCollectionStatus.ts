import { onBeforeUnmount, onMounted, ref } from 'vue'
import { apiRequest } from '@/api/client'
import { runtimeConfig } from '@/config/runtime'
export interface CollectionState { targetId: string; status: string | null; reason: string | null; createdAt: string | null }
export function useCollectionStatus(kind: 'PRODUCT' | 'STORE', updated: () => void, completed?: (id: string, result: CollectionState) => void) {
  const states = ref<CollectionState[]>([]), enabled = ref(false), error = ref(''), busy = ref('')
  const requests = ref<Record<string, { previous: string | null; started: number; submitting: boolean; observed: boolean }>>({})
  let timer: ReturnType<typeof setInterval> | undefined, fetching = false, stopped = false
  const active = ['QUEUED', 'RUNNING', 'RETRY']
  const terminal = ['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED']
  const labels: Record<string, string> = { SUBMITTING: '提交中…', QUEUED: '排队中…', RUNNING: '采集中…', RETRY: '等待重试…', UNKNOWN: '尚未确认启动', FAILED: '采集失败', PARTIAL: '部分更新', SUCCEEDED: '已更新', CANCELLED: '已取消' }
  function rawState(id: string) { return states.value.find(s => s.targetId === id) }
  function state(id: string): CollectionState | undefined {
    const request = requests.value[id], raw = rawState(id)
    if (request && !request.observed) return { targetId: id, createdAt: null, status: request.submitting ? 'SUBMITTING' : Date.now() - request.started < 120000 ? 'QUEUED' : 'UNKNOWN', reason: '更新请求已提交，正在等待采集任务启动；超过两分钟未启动时请刷新状态或重试。' }
    return raw
  }
  function label(id: string) { return labels[state(id)?.status ?? ''] ?? '等待首次采集' }
  function pending(id: string) { return [...active, 'SUBMITTING'].includes(state(id)?.status ?? '') }
  async function refresh() {
    if (fetching || stopped || runtimeConfig.authMode === 'mock') return
    fetching = true
    try {
      const [source, next] = await Promise.all([apiRequest<{ sources: { provider: string; approved: boolean }[] }>('/v1/collection/status'), apiRequest<CollectionState[]>(`/v1/collection/targets?kind=${kind}`)])
      if (stopped) return
      enabled.value = source.sources.some(s => s.provider === 'amazon-public-us' && s.approved)
      const changed = next.some(n => ['SUCCEEDED', 'PARTIAL'].includes(n.status ?? '') && (!rawState(n.targetId) || rawState(n.targetId)?.createdAt !== n.createdAt || rawState(n.targetId)?.status !== n.status))
      states.value = next; error.value = ''
      for (const n of next) {
        const request = requests.value[n.targetId]
        if (!request || request.submitting) continue
        // The enqueue endpoint schedules work; its immediate status can still be the PREVIOUS job.
        if (active.includes(n.status ?? '') || (n.createdAt && n.createdAt !== request.previous)) request.observed = true
        if (request.observed && terminal.includes(n.status ?? '')) {
          delete requests.value[n.targetId]
          completed?.(n.targetId, n)
        }
      }
      if (changed) updated()
    } catch (e) { if (!stopped) error.value = e instanceof Error ? e.message : '更新状态读取失败' } finally { fetching = false }
  }
  async function collect(id: string) {
    if (pending(id)) return false
    requests.value[id] = { previous: rawState(id)?.createdAt ?? null, started: Date.now(), submitting: true, observed: false }
    busy.value = id; error.value = ''
    try {
      await apiRequest(kind === 'PRODUCT' ? `/v1/product-monitoring/${id}/collect` : `/v1/watch/stores/${id}/collect`, { method: 'POST' })
      requests.value[id]!.submitting = false
      await refresh(); return true
    } catch (e) { delete requests.value[id]; error.value = e instanceof Error ? e.message : '采集排队失败'; return false }
    finally { if (busy.value === id) busy.value = '' }
  }
  onMounted(() => { void refresh(); timer = setInterval(() => void refresh(), 5000) })
  onBeforeUnmount(() => { stopped = true; clearInterval(timer) })
  return { states, enabled, error, busy, state, label, pending, collect, refresh }
}
