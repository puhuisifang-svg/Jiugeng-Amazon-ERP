<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { domainApi, type WatchRunMode } from '@/api/domain'
import type { WatchStoreDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

const items = ref<WatchStoreDto[]>([])
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const success = ref('')
const writable = computed(() => session.canAccess(['watch.write']))

async function load() {
  loading.value = true
  try { items.value = (await domainApi.watchStores()).items }
  catch { error.value = '监控店铺模拟数据加载失败。' }
  finally { loading.value = false }
}

async function run(item: WatchStoreDto, mode: WatchRunMode) {
  busyId.value = item.id; error.value = ''; success.value = ''
  try {
    const response = await domainApi.runWatchStore(item.id, mode)
    const index = items.value.findIndex(store => store.id === item.id)
    items.value[index] = response.item
    if (response.baselineChanged) {
      success.value = response.events.length
        ? `完整快照已提升为基线，生成 ${response.events.length} 条待复核事件。`
        : '完整快照已提升为基线，本次没有新增事件。'
    } else {
      success.value = `快照被安全拒绝，继续保留基线 ${response.item.baselineRunId ?? '（尚未建立）'}。`
    }
  } catch { error.value = '模拟运行失败，请刷新后重试。' }
  finally { busyId.value = '' }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SyntheticNotice />
    <p
      v-if="!writable"
      class="data-state"
    >
      当前角色仅可查看监控店铺。
    </p>
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-for="item in items"
      v-else
      :key="item.id"
      class="panel compact-panel"
    >
      <div class="card-heading">
        <div><strong>{{ item.storeName }}</strong><p>{{ item.sourceUrl }}</p></div>
        <span class="placeholder-badge">{{ item.status }}</span>
      </div>
      <p>有效基线：{{ item.baselineRunId ?? '尚未建立' }} · ASIN 数：{{ item.baselineAsinCount }}</p>
      <p v-if="item.lastRunId">
        最近运行：{{ item.lastRunId }} · {{ item.lastRunStatus }} · 完整性 {{ item.lastIntegrityStatus }} · 决策 {{ item.lastDecision }}
      </p>
      <div
        v-if="writable"
        class="inline-actions"
      >
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'COMPLETE')"
        >
          模拟完整快照
        </button>
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'INCOMPLETE')"
        >
          模拟不完整快照
        </button>
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'FAILED')"
        >
          模拟失败快照
        </button>
      </div>
    </article>
  </section>
</template>
