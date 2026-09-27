<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { domainApi, type CandidateStatus } from '@/api/domain'
import type { SelectionCandidateDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

type Candidate = SelectionCandidateDto & { rejectionReason?: string }
const items = ref<Candidate[]>([]); const loading = ref(true); const busyId = ref(''); const error = ref(''); const success = ref(''); const reasons = reactive<Record<string, string>>({})
const saving = ref(false); const asinOrUrl = ref(''); const title = ref(''); const score = ref(50)
const writable = computed(() => ['ADMIN', 'SELECTION'].includes(session.user.value?.role ?? ''))
function actions(status: string): CandidateStatus[] { if (status === 'NEW') return ['REVIEWING', 'REJECTED']; if (status === 'REVIEWING') return ['APPROVED', 'REJECTED']; return [] }
async function load() { loading.value = true; try { items.value = (await domainApi.candidates()).items } catch { error.value = '候选池模拟数据加载失败。' } finally { loading.value = false } }
async function create() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.createCandidate({ asinOrUrl: asinOrUrl.value.trim(), title: title.value.trim(), score: score.value })
    items.value.unshift(response.item); asinOrUrl.value = ''; title.value = ''; score.value = 50
    success.value = `候选 ${response.item.asin} 已创建并归一化。`
  }
  catch { error.value = '创建失败：请检查 ASIN/标准 Amazon URL 及重复候选。' } finally { saving.value = false }
}
async function transition(item: Candidate, status: CandidateStatus) {
  const rejectionReason = reasons[item.id]?.trim()
  if (status === 'REJECTED' && !rejectionReason) { error.value = '拒绝候选必须填写原因。'; return }
  busyId.value = item.id; error.value = ''; success.value = ''
  try {
    const input = status === 'REJECTED' ? { status, rejectionReason: rejectionReason as string } : { status }
    const response = await domainApi.transitionCandidate(item.id, input)
    const index = items.value.findIndex(candidate => candidate.id === item.id)
    items.value[index] = response.item
    success.value = `候选 ${item.asin} 已流转为 ${status}。`
  }
  catch { error.value = '状态流转失败，请刷新后重试。' } finally { busyId.value = '' }
}
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <SyntheticNotice />
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="create"
    >
      <h2>手工新增模拟候选</h2><label class="form-wide">ASIN 或 Amazon URL<input
        v-model="asinOrUrl"
        required
        placeholder="B0… 或 https://www.amazon.com/dp/B0…"
      ></label><label>候选标题<input
        v-model="title"
        placeholder="候选标题"
        required
      ></label><label>初始评分<input
        v-model.number="score"
        min="0"
        max="100"
        required
        type="number"
      ></label><button
        :disabled="saving"
        type="submit"
      >
        {{ saving ? '保存中…' : '新增候选' }}
      </button>
    </form><p
      v-if="!writable"
      class="data-state"
    >
      当前角色仅可查看候选池。
    </p><p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p><p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <div
      v-else
      class="card-list"
    >
      <article
        v-for="item in items"
        :key="item.id"
        class="panel compact-panel"
      >
        <div class="card-heading">
          <div><strong>{{ item.asin }}</strong><h2>{{ item.title }}</h2></div><span class="placeholder-badge">{{ item.status }}</span>
        </div><p>
          评分：{{ item.score }}<span v-if="item.sourceUrl"> · <a
            :href="item.sourceUrl"
            target="_blank"
            rel="noreferrer"
          >标准链接</a></span><span v-if="item.rejectionReason"> · 拒绝原因：{{ item.rejectionReason }}</span>
        </p>
        <div
          v-if="writable && actions(item.status).length"
          class="inline-actions"
        >
          <input
            v-model="reasons[item.id]"
            placeholder="拒绝时必填原因"
          ><button
            v-for="target in actions(item.status)"
            :key="target"
            :disabled="busyId === item.id"
            type="button"
            @click="transition(item, target)"
          >
            {{ target }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
