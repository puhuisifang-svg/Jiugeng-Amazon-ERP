<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiRequest } from '@/api/client'
import { session } from '@/state/session'
const props = defineProps<{ kind: string; targets: { id: string; label: string }[] }>()
interface Schedule { id: string; kind: string; targetId: string; provider: string; enabled: boolean; version: number; sourceStatus: string; nextAt: string }
const state = ref(''), sources = ref<{ provider: string; kinds: string[]; approved: boolean }[]>([]), schedules = ref<Schedule[]>([])
const jobs = ref<{ id: string; status: string; attempts: number; reason: string }[]>([])
const notices = ref<{ id: string; message: string; isRead: boolean; createdAt: string }[]>([])
const subscribed = ref(false), selected = ref(''), provider = ref(''), error = ref(''), busy = ref(false)
const admin = computed(() => session.canAccess(['system.users.manage']))
const available = computed(() => sources.value.filter(s => s.approved && s.kinds.includes(props.kind)))
async function load() {
  try {
    const result = await apiRequest<{ state: string; sources: typeof sources.value }>('/v1/collection/status'); state.value = result.state; sources.value = result.sources
    subscribed.value = (await apiRequest<{ enabled: boolean }>('/v1/collection/subscription')).enabled
    notices.value = await apiRequest<typeof notices.value>('/v1/collection/notifications')
    if (admin.value) { schedules.value = await apiRequest<Schedule[]>('/v1/collection/schedules'); jobs.value = await apiRequest<typeof jobs.value>('/v1/collection/jobs') }
  } catch (e) { error.value = e instanceof Error ? e.message : '调度状态加载失败' }
}
async function subscribe() { busy.value = true; try { await apiRequest('/v1/collection/subscription', { method: 'PUT', body: JSON.stringify({ enabled: !subscribed.value }) }); await load() } catch (e) { error.value = String(e) } finally { busy.value = false } }
async function configure(enabled: boolean, existing?: Schedule) {
  busy.value = true; error.value = ''
  try {
    const old = existing ?? schedules.value.find(s => s.kind === props.kind && s.targetId === selected.value)
    await apiRequest('/v1/collection/schedules', { method: 'PUT', body: JSON.stringify({ kind: props.kind, targetId: existing?.targetId ?? selected.value, provider: existing?.provider ?? provider.value, enabled, version: old?.version }) }); await load()
  } catch (e) { error.value = e instanceof Error ? e.message : '配置失败' } finally { busy.value = false }
}
async function read(id: string) { try { await apiRequest(`/v1/collection/notifications/${id}/read`, { method: 'PUT' }); await load() } catch (e) { error.value = String(e) } }
onMounted(load)
</script>
<template>
  <details class="panel compact-panel">
    <summary>自动监控：{{ state === 'CONFIGURED' ? '来源已配置' : '待接入真实数据源' }} · 任务与站内通知</summary>
    <p>美国站 · America/Los_Angeles · 每日调度。来源未接入时不会发起采集，人工快照保持独立。</p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <button
      :disabled="busy"
      @click="subscribe"
    >
      {{ subscribed ? '取消我的站内通知订阅' : '订阅我的站内通知' }}
    </button>
    <form
      v-if="admin && available.length"
      class="operation-form"
      @submit.prevent="configure(true)"
    >
      <label>监控对象<select
        v-model="selected"
        required
      ><option value="">请选择</option><option
        v-for="target in targets"
        :key="target.id"
        :value="target.id"
      >{{ target.label }}</option></select></label>
      <label>获批来源<select
        v-model="provider"
        required
      ><option value="">请选择</option><option
        v-for="s in available"
        :key="s.provider"
      >{{ s.provider }}</option></select></label><button :disabled="busy">
        启用每日采集
      </button>
    </form>
    <div v-if="admin">
      <p
        v-for="s in schedules.filter(s => s.kind === kind)"
        :key="s.id"
      >
        {{ targets.find(t => t.id === s.targetId)?.label || '监控对象' }} · {{ s.provider }} · {{ s.sourceStatus === 'READY' ? '已接入' : '待接入' }} · {{ s.enabled ? '已启用' : '暂停' }} · 下次 {{ s.nextAt }} <button
          v-if="s.enabled"
          :disabled="busy"
          @click="configure(false, s)"
        >
          暂停调度
        </button>
      </p><details>
        <summary>最近100次任务</summary><p v-if="!jobs.length">
          暂无任务
        </p><p
          v-for="job in jobs"
          :key="job.id"
        >
          {{ job.status }} · 已尝试 {{ job.attempts }} 次 · {{ job.reason }}
        </p>
      </details>
    </div>
    <p v-if="!notices.length">
      暂无站内通知。
    </p><p
      v-for="n in notices"
      :key="n.id"
    >
      {{ n.createdAt }} · {{ n.message }} <button
        v-if="!n.isRead"
        @click="read(n.id)"
      >
        标记已读
      </button>
    </p>
  </details>
</template>
