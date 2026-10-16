<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { domainApi } from '@/api/domain'
import type { WatchEventDto } from '@/api/resources'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { session } from '@/state/session'

type Event = WatchEventDto
const items = ref<Event[]>([]); const loading = ref(true); const busyId = ref(''); const error = ref(''); const success = ref('')
const writable = computed(() => session.canAccess(['watch.write']))
async function load() { loading.value = true; try { items.value = (await domainApi.events()).items } catch { error.value = '上新监控数据加载失败。' } finally { loading.value = false } }
async function confirm(item: Event) { busyId.value = item.id; error.value = ''; success.value = ''; try { const response = await domainApi.confirmEvent(item.id); const index = items.value.findIndex(event => event.id === item.id); items.value[index] = response.item; success.value = response.replayed ? `事件已确认，复用候选 ${response.candidate.id}。` : `已确认并生成候选 ${response.candidate.id}。` } catch { error.value = '确认失败，请刷新后重试。' } finally { busyId.value = '' } }
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <SyntheticNotice /><p
      v-if="!writable"
      class="data-state"
    >
      当前角色仅可查看监控事件。
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
    <article
      v-else
      class="panel table-panel"
    >
      <table class="data-table">
        <thead><tr><th>店铺</th><th>ASIN</th><th>商品</th><th>来源运行</th><th>发现时间</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td>{{ item.storeName }}</td><td>{{ item.asin }}</td><td>{{ item.title }}</td><td>{{ item.sourceRunId ?? '—' }}</td><td>{{ item.detectedAt }}</td><td>{{ item.status }}</td><td>
              <button
                v-if="writable"
                :disabled="busyId === item.id"
                type="button"
                @click="confirm(item)"
              >
                {{ item.status === 'CONFIRMED' ? '再次确认（幂等）' : '确认进入候选池' }}
              </button><span v-else>只读</span>
            </td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
