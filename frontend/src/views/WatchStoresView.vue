<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { domainApi, type WatchRunMode } from '@/api/domain'
import type { WatchStoreDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'

const items = ref<WatchStoreDto[]>([])
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const success = ref('')
const writable = computed(() => session.canAccess(['watch.write']))
const formal = runtimeConfig.authMode === 'password'
const storeKey = ref(''); const storeName = ref(''); const sourceUrl = ref('')
const snapshots = ref<Record<string, string>>({})

async function load() {
  loading.value = true
  try { items.value = (await domainApi.watchStores()).items }
  catch { error.value = '监控店铺数据加载失败。' }
  finally { loading.value = false }
}

async function run(item: WatchStoreDto, mode: WatchRunMode) {
  busyId.value = item.id; error.value = ''; success.value = ''
  try {
    const asins = (snapshots.value[item.id] ?? '').split(/[\s,]+/).map(value => value.trim()).filter(Boolean)
    const response = await domainApi.runWatchStore(item.id, mode, asins)
    const index = items.value.findIndex(store => store.id === item.id)
    items.value[index] = response.item
    if (response.baselineChanged) {
      success.value = response.events.length
        ? `完整快照已提升为基线，生成 ${response.events.length} 条待复核事件。`
        : '完整快照已提升为基线，本次没有新增事件。'
    } else {
      success.value = `快照被安全拒绝，继续保留基线 ${response.item.baselineRunId ?? '（尚未建立）'}。`
    }
  } catch { error.value = '快照运行失败，请检查完整性字段和 ASIN。' }
  finally { busyId.value = '' }
}

async function createStore() {
  error.value = ''; success.value = ''
  try {
    await domainApi.createWatchStore({ storeKey: storeKey.value.trim(), storeName: storeName.value.trim(), sourceUrl: sourceUrl.value.trim() })
    storeKey.value = ''; storeName.value = ''; sourceUrl.value = ''; success.value = '监控店铺已登记。'; await load()
  } catch { error.value = '店铺登记失败，请检查唯一标识和 HTTPS 链接。' }
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
    <form
      v-if="formal && writable"
      class="panel operation-form"
      @submit.prevent="createStore"
    >
      <h2>登记监控店铺</h2><label>店铺标识<input
        v-model="storeKey"
        required
        maxlength="100"
      ></label>
      <label>店铺名称<input
        v-model="storeName"
        required
        maxlength="200"
      ></label>
      <label class="form-wide">获批公开来源<input
        v-model="sourceUrl"
        required
        type="url"
        placeholder="https://..."
      ></label>
      <button type="submit">
        登记店铺
      </button>
    </form>
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
      <label
        v-if="formal && writable"
        class="form-wide"
      >人工 ASIN 快照（空格、逗号或换行分隔，最多 1000 行）
        <textarea
          v-model="snapshots[item.id]"
          placeholder="B0..."
        />
      </label>
      <div
        v-if="writable"
        class="inline-actions"
      >
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'COMPLETE')"
        >
          {{ formal ? '提交完整快照' : '模拟完整快照' }}
        </button>
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'INCOMPLETE')"
        >
          {{ formal ? '提交不完整快照' : '模拟不完整快照' }}
        </button>
        <button
          :disabled="busyId === item.id"
          type="button"
          @click="run(item, 'FAILED')"
        >
          {{ formal ? '登记失败快照' : '模拟失败快照' }}
        </button>
      </div>
    </article>
  </section>
</template>
