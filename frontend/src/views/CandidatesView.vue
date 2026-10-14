<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { domainApi, type CandidateStatus } from '@/api/domain'
import type { SelectionCandidateDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

type Candidate = SelectionCandidateDto & { rejectionReason?: string }
interface EvaluationDraft { category: string; price: string; rating: string; reviewCount: string; owner: string; tags: string; selectionReason: string }
const items = ref<Candidate[]>([]); const loading = ref(true); const busyId = ref(''); const error = ref(''); const success = ref(''); const reasons = reactive<Record<string, string>>({})
const saving = ref(false); const asinOrUrl = ref(''); const title = ref(''); const score = ref(50)
const query = ref(''); const statusFilter = ref(''); const ownerFilter = ref(''); const drafts = reactive<Record<string, EvaluationDraft>>({})
const writable = computed(() => ['ADMIN', 'SELECTION'].includes(session.user.value?.role ?? ''))
const owners = computed(() => [...new Set(items.value.map(item => item.owner).filter((owner): owner is string => Boolean(owner)))].sort())
const filteredItems = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const searchable = [item.asin, item.title, item.category, item.owner, ...(item.tags ?? [])].filter(Boolean).join(' ').toLowerCase()
    return (!keyword || searchable.includes(keyword))
      && (!statusFilter.value || item.status === statusFilter.value)
      && (!ownerFilter.value || item.owner === ownerFilter.value)
  })
})
function actions(status: string): CandidateStatus[] { if (status === 'NEW') return ['REVIEWING', 'REJECTED']; if (status === 'REVIEWING') return ['APPROVED', 'REJECTED']; return [] }
function syncDraft(item: Candidate) {
  drafts[item.id] = { category: item.category ?? '', price: item.price?.toString() ?? '', rating: item.rating?.toString() ?? '', reviewCount: item.reviewCount?.toString() ?? '', owner: item.owner ?? '', tags: (item.tags ?? []).join(', '), selectionReason: item.selectionReason ?? '' }
}
function draftFor(candidateId: string): EvaluationDraft {
  return drafts[candidateId] ?? (drafts[candidateId] = { category: '', price: '', rating: '', reviewCount: '', owner: '', tags: '', selectionReason: '' })
}
async function load() { loading.value = true; try { items.value = (await domainApi.candidates()).items; items.value.forEach(syncDraft) } catch { error.value = '候选池模拟数据加载失败。' } finally { loading.value = false } }
async function create() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const response = await domainApi.createCandidate({ asinOrUrl: asinOrUrl.value.trim(), title: title.value.trim(), score: score.value })
    items.value.unshift(response.item); syncDraft(response.item); asinOrUrl.value = ''; title.value = ''; score.value = 50
    success.value = `候选 ${response.item.asin} 已创建并归一化。`
  }
  catch { error.value = '创建失败：请检查 ASIN/标准 Amazon URL 及重复候选。' } finally { saving.value = false }
}
async function updateEvaluation(item: Candidate) {
  const draft = drafts[item.id]; if (!draft) return
  busyId.value = item.id; error.value = ''; success.value = ''
  const optionalNumber = (value: string) => value.trim() === '' ? undefined : Number(value)
  try {
    const response = await domainApi.updateCandidateEvaluation(item.id, { category: draft.category.trim() || undefined, price: optionalNumber(draft.price), rating: optionalNumber(draft.rating), reviewCount: optionalNumber(draft.reviewCount), owner: draft.owner.trim() || undefined, tags: draft.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean), selectionReason: draft.selectionReason.trim() || undefined })
    const index = items.value.findIndex(candidate => candidate.id === item.id); items.value[index] = response.item; syncDraft(response.item)
    success.value = `候选 ${item.asin} 的评估资料已保存。`
  }
  catch { error.value = '评估资料保存失败，请检查评分、价格和评论数。' } finally { busyId.value = '' }
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
      v-if="!loading"
      class="panel operation-form"
    >
      <h2>筛选候选</h2><label class="form-wide">搜索<input
        v-model="query"
        placeholder="ASIN、标题、类目、负责人或标签"
      ></label><label>状态<select v-model="statusFilter"><option value="">全部状态</option><option value="NEW">NEW</option><option value="REVIEWING">REVIEWING</option><option value="APPROVED">APPROVED</option><option value="REJECTED">REJECTED</option></select></label><label>负责人<select v-model="ownerFilter"><option value="">全部负责人</option><option
        v-for="owner in owners"
        :key="owner"
        :value="owner"
      >{{ owner }}</option></select></label>
    </div>
    <p
      v-if="!loading && !filteredItems.length"
      class="data-state"
    >
      没有符合当前条件的候选。
    </p>
    <div
      v-if="!loading && filteredItems.length"
      class="card-list"
    >
      <article
        v-for="item in filteredItems"
        :key="item.id"
        class="panel compact-panel"
      >
        <div class="card-heading">
          <div><strong>{{ item.asin }}</strong><h2>{{ item.title }}</h2></div><span class="placeholder-badge">{{ item.status }}</span>
        </div><p>
          综合分：{{ item.score }}<span v-if="item.category"> · 类目：{{ item.category }}</span><span v-if="item.price !== undefined"> · 价格：{{ item.price }}</span><span v-if="item.rating !== undefined"> · 星级：{{ item.rating }}</span><span v-if="item.reviewCount !== undefined"> · 评论：{{ item.reviewCount }}</span><span v-if="item.owner"> · 负责人：{{ item.owner }}</span><span v-if="item.source"> · 来源：{{ item.source }}<template v-if="item.sourceStore"> / {{ item.sourceStore }}</template></span><span v-if="item.sourceUrl"> · <a
            :href="item.sourceUrl"
            target="_blank"
            rel="noreferrer"
          >标准链接</a></span><span v-if="item.rejectionReason"> · 拒绝原因：{{ item.rejectionReason }}</span>
        </p>
        <p v-if="item.tags?.length">
          标签：{{ item.tags.join('、') }}
        </p><p v-if="item.selectionReason">
          选品理由：{{ item.selectionReason }}
        </p><p v-if="item.sourceEventId">
          来源链路：{{ item.sourceStore }} → {{ item.sourceRunId }} → {{ item.sourceEventId }}
        </p><p>
          <RouterLink :to="`/selection/candidates/${item.id}`">
            查看详情与评审记录
          </RouterLink>
        </p>
        <form
          v-if="writable && drafts[item.id]"
          class="operation-form evaluation-form"
          @submit.prevent="updateEvaluation(item)"
        >
          <h2>评估资料</h2><label>类目<input
            v-model="draftFor(item.id).category"
            name="category"
          ></label><label>价格<input
            v-model="draftFor(item.id).price"
            min="0"
            name="price"
            step="0.01"
            type="number"
          ></label><label>星级<input
            v-model="draftFor(item.id).rating"
            max="5"
            min="0"
            name="rating"
            step="0.1"
            type="number"
          ></label><label>评论数<input
            v-model="draftFor(item.id).reviewCount"
            min="0"
            name="reviewCount"
            step="1"
            type="number"
          ></label><label>负责人<input
            v-model="draftFor(item.id).owner"
            name="owner"
          ></label><label>标签（逗号分隔）<input
            v-model="draftFor(item.id).tags"
            name="tags"
          ></label><label class="form-wide">选品理由<input
            v-model="draftFor(item.id).selectionReason"
            name="selectionReason"
          ></label><button
            :disabled="busyId === item.id"
            type="submit"
          >
            保存评估
          </button>
        </form>
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
