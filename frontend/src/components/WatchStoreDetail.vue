<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave } from 'vue-router'
import { apiRequest } from '@/api/client'
import type { WatchStoreDto } from '@/api/resources'
import ListPagination from './ListPagination.vue'
import TableViewport from './TableViewport.vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props = defineProps<{ store: WatchStoreDto | undefined; writable: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
interface Detail { store: WatchStoreDto; runs: { id: string; completedAt: string; decision: string; reasonCode: string }[]; products: { asin: string; firstDetectedAt: string; online: boolean; source: string }[]; automaticProducts: { provider: string; asin: string; title: string; firstAt: string; lastAt: string; missingCount: number; missingAt: string | null }[] }
const detail = ref<Detail>(), dialog = ref<HTMLDialogElement>(), error = ref(''), busy = ref(false), tab = ref('manual'), q = ref(''), page = ref(1), size = ref(20), unsaved = ref(false)
const draft = ref({ name: '', owner: '', remark: '', enabled: true }), baseline = ref('')
const products = computed(() => detail.value?.products.filter(p => !q.value || p.asin.includes(q.value.toUpperCase())) ?? [])
const auto = computed(() => detail.value?.automaticProducts.filter(p => (tab.value === 'missing' ? p.missingCount >= 2 : p.missingCount < 2) && (!q.value || p.asin.includes(q.value.toUpperCase()))) ?? [])
const total = computed(() => tab.value === 'manual' ? products.value.length : auto.value.length)
watch([q, tab, size], () => { page.value = 1 })
watch(() => props.store, async value => {
  if (!value) return
  error.value = ''; detail.value = undefined; tab.value = 'manual'; page.value = 1
  draft.value = { name: value.storeName, owner: value.owner ?? '', remark: value.remark ?? '', enabled: value.status === 'ACTIVE' }; baseline.value = JSON.stringify(draft.value)
  await nextTick(); dialog.value?.showModal()
  try { detail.value = await apiRequest<Detail>(`/v1/watch/stores/${value.id}`) } catch (e) { error.value = String(e) }
})
onBeforeRouteLeave(() => { if (props.store && (busy.value || JSON.stringify(draft.value) !== baseline.value)) { unsaved.value = true; return false } return true })
function close() { if (busy.value) return; if (JSON.stringify(draft.value) !== baseline.value) unsaved.value = true; else discard() }
function discard() { unsaved.value = false; dialog.value?.close(); emit('close') }
async function save() {
  if (!props.store) return
  busy.value = true
  try { await apiRequest(`/v1/watch/stores/${props.store.id}`, { method: 'PUT', body: JSON.stringify({ ...draft.value, version: props.store.version }) }); baseline.value = JSON.stringify(draft.value); emit('changed'); discard() } catch (e) { error.value = e instanceof Error ? e.message : '保存失败' } finally { busy.value = false }
}
</script>
<template>
  <dialog
    ref="dialog"
    class="store-detail"
    aria-label="店铺监控详情"
    @cancel.prevent="close"
  >
    <header>
      <h2>{{ store?.storeName }} · 店铺监控详情</h2><button
        :disabled="busy"
        @click="close"
      >
        关闭
      </button>
    </header>
    <div class="store-body">
      <p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p>
      <div class="ui-section-tabs">
        <button
          v-for="t in [{id:'manual',label:'人工商品快照'},{id:'online',label:'在线产品'},{id:'missing',label:'疑似下架产品'},{id:'runs',label:'同步记录'},{id:'settings',label:'设置'}]"
          :key="t.id"
          :aria-pressed="tab === t.id"
          @click="tab = t.id"
        >
          {{ t.label }}
        </button>
      </div>
      <template v-if="['manual','online','missing'].includes(tab)">
        <label>ASIN<input
          v-model="q"
          placeholder="搜索ASIN"
        ></label>
        <p v-if="tab === 'manual'">
          人工观测不能证明亚马逊实际上架或下架；仅展示本系统首次发现时间。
        </p><p v-else>
          疑似下架须两个不同站点自然日完整快照缺失。暂无来源时不会生成自动数据。
        </p>
        <TableViewport>
          <table class="data-table">
            <thead><tr><th>商品 / ASIN</th><th>首次发现</th><th>来源 / 状态</th></tr></thead><tbody>
              <template v-if="tab === 'manual'">
                <tr
                  v-for="p in products.slice((page-1)*size,page*size)"
                  :key="p.asin"
                >
                  <td>{{ p.asin }}</td><td>{{ p.firstDetectedAt }}</td><td>人工 · {{ p.online ? '最近快照中出现' : '最近快照中未出现' }}</td>
                </tr>
              </template>
              <template v-else>
                <tr
                  v-for="p in auto.slice((page-1)*size,page*size)"
                  :key="p.provider+p.asin"
                >
                  <td>{{ p.title }}<br>{{ p.asin }}</td><td>{{ p.firstAt }}</td><td>{{ p.provider }} · {{ p.missingCount >= 2 ? '疑似下架' : '在线' }}<br>最近出现 {{ p.lastAt }}<br v-if="p.missingAt">{{ p.missingAt ? `首次缺失 ${p.missingAt}` : '' }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </TableViewport><p v-if="!total">
          尚无符合条件的数据。
        </p>
      </template>
      <div v-else-if="tab === 'runs'">
        <p
          v-for="run in detail?.runs"
          :key="run.id"
        >
          {{ run.completedAt }} · {{ run.decision }} · {{ run.reasonCode }}
        </p><p v-if="!detail?.runs.length">
          暂无同步记录。
        </p>
      </div>
      <form
        v-else
        class="operation-form"
        @submit.prevent="save"
      >
        <label>店铺名称<input
          v-model="draft.name"
          :disabled="!writable"
          required
          maxlength="200"
        ></label><label>监控人<input
          v-model="draft.owner"
          :disabled="!writable"
          maxlength="100"
        ></label><label>备注<textarea
          v-model="draft.remark"
          :disabled="!writable"
          maxlength="1000"
        /></label><label>监控状态<select
          v-model="draft.enabled"
          :disabled="!writable"
        ><option :value="true">启用</option><option :value="false">暂停（保留历史）</option></select></label>
      </form>
      <RouterLink
        v-if="store"
        :to="`/watch/events?storeId=${store.id}`"
        @click="close"
      >
        查看该店铺上新事件
      </RouterLink>
    </div>
    <footer>
      <ListPagination
        v-if="['manual','online','missing'].includes(tab)"
        v-model:page="page"
        v-model:page-size="size"
        :total="total"
      /><button
        v-if="tab === 'settings' && writable"
        :disabled="busy"
        @click="save"
      >
        保存设置
      </button>
    </footer>
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved=false"
      @discard="discard"
    />
  </dialog>
</template>
<style scoped>
.store-detail{width:min(1100px,calc(100vw - 24px));height:min(780px,calc(100dvh - 24px));padding:0;border:1px solid #cbd5e1;border-radius:10px}.store-detail[open]{display:flex;flex-direction:column}.store-detail::backdrop{background:#0f172a80}.store-detail header,.store-detail footer{display:flex;justify-content:space-between;gap:8px;align-items:center;flex-wrap:wrap;padding:12px;flex-shrink:0}.store-body{padding:12px;overflow:auto;min-height:0}.store-detail .pagination{width:100%}.store-body .operation-form{grid-template-columns:repeat(auto-fit,minmax(180px,1fr))}.store-detail td{overflow-wrap:anywhere}
</style>
