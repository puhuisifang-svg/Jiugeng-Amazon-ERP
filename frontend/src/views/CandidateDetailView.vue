<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { domainApi } from '@/api/domain'
import type { CandidateDetailDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

const route = useRoute()
const candidateId = computed(() => String(route.params.candidateId))
const detail = ref<CandidateDetailDto | null>(null)
const content = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const writable = computed(() => ['ADMIN', 'SELECTION'].includes(session.user.value?.role ?? ''))

async function load() {
  loading.value = true; error.value = ''
  try { detail.value = (await domainApi.candidateDetail(candidateId.value)).item }
  catch { error.value = '候选详情加载失败。' }
  finally { loading.value = false }
}

async function addNote() {
  const note = content.value.trim()
  if (!note) { error.value = '评审记录不能为空。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try {
    detail.value = (await domainApi.addCandidateNote(candidateId.value, note)).item
    content.value = ''
    success.value = '评审记录已追加。'
  } catch { error.value = '评审记录保存失败。' }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice />
    <p>
      <RouterLink to="/selection/candidates">
        ← 返回候选池
      </RouterLink>
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <template v-if="detail">
      <article class="panel compact-panel">
        <div class="card-heading">
          <div><strong>{{ detail.candidate.asin }}</strong><h2>{{ detail.candidate.title }}</h2></div>
          <span class="placeholder-badge">{{ detail.candidate.status }}</span>
        </div>
        <p>综合分：{{ detail.candidate.score }} · 类目：{{ detail.candidate.category ?? '—' }} · 负责人：{{ detail.candidate.owner ?? '—' }}</p>
        <p>
          来源：{{ detail.candidate.source ?? '—' }}<template v-if="detail.candidate.sourceStore">
            / {{ detail.candidate.sourceStore }}
          </template>
        </p>
        <p v-if="detail.candidate.sourceEventId">
          来源链路：{{ detail.candidate.sourceRunId }} → {{ detail.candidate.sourceEventId }}
        </p>
        <p v-if="detail.candidate.selectionReason">
          选品理由：{{ detail.candidate.selectionReason }}
        </p>
      </article>
      <form
        v-if="writable"
        class="panel operation-form"
        @submit.prevent="addNote"
      >
        <h2>追加评审记录</h2>
        <label class="form-wide">记录内容<textarea
          v-model="content"
          maxlength="1000"
          placeholder="记录判断、风险或下一步验证动作"
        /></label>
        <button
          :disabled="saving"
          type="submit"
        >
          {{ saving ? '保存中…' : '追加记录' }}
        </button>
      </form>
      <p
        v-else
        class="data-state"
      >
        当前角色只能查看评审记录。
      </p>
      <section
        class="card-list"
        aria-label="评审记录"
      >
        <article
          v-for="note in detail.notes"
          :key="note.id"
          class="panel compact-panel"
        >
          <p>{{ note.content }}</p>
          <small>{{ note.authorName }} · {{ note.authorRole }} · {{ note.createdAt }}</small>
        </article>
        <p
          v-if="!detail.notes.length"
          class="data-state"
        >
          暂无评审记录。
        </p>
      </section>
    </template>
  </section>
</template>
