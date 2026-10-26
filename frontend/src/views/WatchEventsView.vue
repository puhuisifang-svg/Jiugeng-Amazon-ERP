<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SuccessToast from '@/components/SuccessToast.vue'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import SyntheticNotice from '@/components/SyntheticNotice.vue'
import { domainApi } from '@/api/domain'
import { apiRequest } from '@/api/client'
import type { WatchEventDto } from '@/api/resources'
import { session } from '@/state/session'
import { runtimeConfig } from '@/config/runtime'
import { promptReason } from '@/state/uiConfirmation'
const route = useRoute()
const items = ref<WatchEventDto[]>([]), loading = ref(true), busy = ref(false), error = ref(''), success = ref('')
const query = ref(''), state = ref(''), store = ref(String(route.query.storeId ?? '')), page = ref(1), size = ref(20), selected = ref<string[]>([])
const writable = computed(() => session.canAccess(['watch.write']))
const formal = runtimeConfig.authMode === 'password'
const labels: Record<string,string> = { PENDING_REVIEW:'待处理', CONFIRMED:'已入候选', IGNORED:'已忽略' }
const stores = computed(() => [...new Map(items.value.map(i => [i.storeId,i.storeName])).entries()])
const filtered = computed(() => items.value.filter(i => (!query.value || `${i.asin} ${i.title}`.toLowerCase().includes(query.value.toLowerCase())) && (!state.value || state.value === i.status) && (!store.value || i.storeId === store.value)))
const rows = computed(() => filtered.value.slice((page.value-1)*size.value,page.value*size.value))
watch([query,state,store,size], () => { page.value=1; selected.value=[] })
watch(page, () => { selected.value=[] })
async function load() { loading.value=true;try { items.value=(await domainApi.events()).items } catch(e) { error.value=e instanceof Error?e.message:'加载失败' } finally { loading.value=false } }
async function act(action:string, targets:WatchEventDto[]) {
  if(!targets.length || busy.value)return
  const reason=action==='ignore'?await promptReason('填写忽略原因（适用于本次选中的未处理事件）'):''
  if(action==='ignore'&&!reason?.trim())return
  busy.value=true;error.value='';const messages:string[]=[]
  for(const item of targets)try {
    const result=action==='confirm'?(await domainApi.confirmEvent(item.id)).item:await apiRequest<WatchEventDto>(`/v1/watch/events/${item.id}/handling`,{method:'PUT',body:JSON.stringify({version:item.version,ignored:action==='ignore',reason})})
    items.value[items.value.findIndex(i=>i.id===item.id)]=result;messages.push(`${item.asin}：成功`)
  }catch(e){messages.push(`${item.asin}：${e instanceof Error?e.message:'操作失败'}`)}
  success.value=messages.join('；');selected.value=[];busy.value=false
}
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <RouterLink to="/watch/stores">
      ← 返回上新监控（对标店铺）
    </RouterLink>
    <SyntheticNotice v-if="!formal" />
    <p>美国站 · 首次发现为监控观测时间，不等于亚马逊上架日期。</p>
    <div class="panel filter-panel operation-form ui-filter-row">
      <label>ASIN / 标题<input v-model="query"></label><label>店铺<select v-model="store"><option value="">全部店铺</option><option
        v-for="[id,name] in stores"
        :key="id"
        :value="id"
      >{{ name }}</option></select></label><label>处理状态<select v-model="state"><option value="">全部</option><option
        v-for="(name,key) in labels"
        :key="key"
        :value="key"
      >{{ name }}</option></select></label><button
        class="filter-reset-button"
        @click="query='';store='';state=''"
      >
        重置
      </button>
    </div>
    <div
      v-if="writable"
      class="inline-actions"
    >
      <button
        :disabled="busy || !selected.length"
        @click="act('confirm',rows.filter(r=>selected.includes(r.id)))"
      >
        选中加入候选
      </button><button
        v-if="formal"
        :disabled="busy || !selected.length"
        @click="act('ignore',rows.filter(r=>selected.includes(r.id)))"
      >
        忽略选中事件
      </button>
    </div>
    <SuccessToast :message="success" /><p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p><p v-if="loading">
      正在加载…
    </p>
    <TableViewport v-else>
      <table class="data-table ui-action-table">
        <thead><tr><th>选择</th><th>店铺</th><th>商品 / ASIN</th><th>首次发现</th><th>来源运行</th><th>处理状态</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="item in rows"
            :key="item.id"
          >
            <td>
              <input
                v-if="writable"
                v-model="selected"
                type="checkbox"
                :value="item.id"
                :aria-label="`选择 ${item.asin}`"
                :disabled="busy || item.status!=='PENDING_REVIEW'"
              >
            </td><td>{{ item.storeName }}</td><td>{{ item.title }}<br>{{ item.asin }}</td><td>{{ new Date(item.detectedAt).toLocaleString('zh-CN',{timeZone:'America/Los_Angeles'}) }}</td><td>{{ item.sourceRunId ?? '—' }}</td><td>{{ labels[item.status] ?? item.status }}<br>{{ item.ignoreReason }}</td><td>
              <RouterLink
                v-if="item.candidateId"
                :to="`/selection/candidates/${item.candidateId}`"
              >
                查看候选
              </RouterLink><template v-else-if="writable">
                <button
                  v-if="item.status==='PENDING_REVIEW'"
                  :disabled="busy"
                  @click="act('confirm',[item])"
                >
                  加入候选
                </button><button
                  v-if="formal"
                  :disabled="busy"
                  @click="act(item.status==='IGNORED'?'restore':'ignore',[item])"
                >
                  {{ item.status==='IGNORED'?'恢复待处理':'忽略' }}
                </button>
              </template><span v-else>只读</span>
            </td>
          </tr>
        </tbody>
      </table><p v-if="!rows.length">
        没有符合条件的上新事件。
      </p>
    </TableViewport>
    <ListPagination
      v-model:page="page"
      v-model:page-size="size"
      :total="filtered.length"
      :disabled="loading || busy"
    />
  </section>
</template>
