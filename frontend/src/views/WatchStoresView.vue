<script setup lang="ts">
import { RouterLink } from 'vue-router'
import PublicCollectionStatus from '@/components/PublicCollectionStatus.vue'
import { apiRequest } from '@/api/client'
import CollectionPanel from '@/components/CollectionPanel.vue'
import WatchStoreDetail from '@/components/WatchStoreDetail.vue'
import ListPagination from '@/components/ListPagination.vue'
import { watch } from 'vue'
import SuccessToast from "@/components/SuccessToast.vue"
import { computed, onMounted, ref } from 'vue'
import { domainApi, type WatchRunMode } from '@/api/domain'
import type { WatchStoreDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'

const selectedStore = ref<WatchStoreDto>()
const page = ref(1), pageSize = ref(20), query = ref('')
watch([query, pageSize], () => { page.value = 1 })
const filtered = computed(() => items.value.filter(s => [s.storeName,s.storeKey,s.owner].join(' ').toLowerCase().includes(query.value.toLowerCase())))
const items = ref<WatchStoreDto[]>([])
const loading = ref(true)
const busyId = ref('')
const error = ref('')
const success = ref('')
const writable = computed(() => session.canAccess(['watch.write']))
const formal = runtimeConfig.authMode === 'password'
const storeName = ref(''); const sourceUrl = ref('')
const snapshots = ref<Record<string, string>>({})

async function load() {
  loading.value = true
  try { items.value = (await domainApi.watchStores()).items }
  catch { error.value = '店铺监控数据加载失败。' }
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
    await apiRequest('/v1/watch/stores/from-url', { method:'POST', body:JSON.stringify({url:sourceUrl.value.trim(),name:storeName.value.trim()}) })
    storeName.value = ''; sourceUrl.value = ''; success.value = '监控店铺已登记。'; await load()
  } catch { error.value = '店铺登记失败，请检查唯一标识和 HTTPS 链接。' }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <div class="panel inline-actions">
      <span>监控亚马逊对标店铺的上新商品；商品持续变化请使用产品监控。</span><RouterLink to="/watch/events">
        查看上新记录
      </RouterLink>
    </div>
    <SyntheticNotice v-if="!formal" />
    <PublicCollectionStatus
      v-if="formal"
      kind="STORE"
      :writable="writable"
      :targets="items.map(s=>({id:s.id,label:s.storeName,enabled:s.status==='ACTIVE'}))"
      @updated="!selectedStore && load()"
    />
    <CollectionPanel
      v-if="formal"
      kind="STORE"
      :targets="items.map(s => ({ id:s.id,label:s.storeName }))"
    />
    <WatchStoreDetail
      v-if="formal"
      :store="selectedStore"
      :writable="writable"
      @close="selectedStore=undefined"
      @changed="load"
    />
    <label
      v-if="formal"
      class="panel filter-panel"
    >店铺 / 监控人搜索<input
      v-model="query"
      placeholder="店铺名称、标识或监控人"
    ></label>
    <p
      v-if="!writable"
      class="data-state"
    >
      当前角色仅可查看监控店铺。
    </p>
    <SuccessToast :message="success" />
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
      <h2>添加对标店铺</h2><label>店铺名称（可选）<input
        v-model="storeName"
        maxlength="200"
      ></label>
      <label class="form-wide">亚马逊店铺地址<input
        v-model="sourceUrl"
        required
        type="url"
        placeholder="https://www.amazon.com/sp?seller=..."
      ></label>
      <button type="submit">
        添加并开始采集
      </button>
    </form>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-for="item in filtered.slice((page-1)*pageSize,page*pageSize)"
      v-else
      :key="item.id"
      class="panel compact-panel"
    >
      <div class="card-heading">
        <div><strong>{{ item.storeName }}</strong><p>{{ item.sourceUrl }}</p></div>
        <span class="placeholder-badge">{{ item.status === 'ACTIVE' ? '启用' : '暂停' }}</span><button
          v-if="formal"
          @click="selectedStore=item"
        >
          详情 / 设置
        </button>
      </div>
      <details>
        <summary>人工补录（可选）</summary>
        <p>人工有效基线：{{ item.baselineRunId ?? '尚未建立' }} · ASIN 数：{{ item.baselineAsinCount }}</p>
        <label
          v-if="formal && writable"
          class="form-wide ui-snapshot-label"
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
            :disabled="busyId === item.id || item.status !== 'ACTIVE'"
            type="button"
            @click="run(item, 'COMPLETE')"
          >
            {{ formal ? '提交完整快照' : '模拟完整快照' }}
          </button>
          <button
            :disabled="busyId === item.id || item.status !== 'ACTIVE'"
            type="button"
            @click="run(item, 'INCOMPLETE')"
          >
            {{ formal ? '提交不完整快照' : '模拟不完整快照' }}
          </button>
          <button
            :disabled="busyId === item.id || item.status !== 'ACTIVE'"
            type="button"
            @click="run(item, 'FAILED')"
          >
            {{ formal ? '登记失败快照' : '模拟失败快照' }}
          </button>
        </div>
      </details>
      <p v-if="item.lastRunId">
        最近采集：{{ item.lastRunAt }} · {{ item.lastRunStatus }}（详情中查看在线产品）
      </p>
    </article>
    <ListPagination
      v-if="formal"
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="filtered.length"
      :disabled="loading"
    />
  </section>
</template>
