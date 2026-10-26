<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave } from 'vue-router'
import { monitoringApi } from '@/api/monitoring'
import SuccessToast from './SuccessToast.vue'
import '@/styles/monitoringWorkbench.css'
import { apiRequest } from '@/api/client'
import type { WatchStoreDto } from '@/api/resources'
import ListPagination from './ListPagination.vue'
import TableViewport from './TableViewport.vue'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props = defineProps<{ store: WatchStoreDto | undefined; writable: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
interface Detail { store: WatchStoreDto; runs: { id: string; completedAt: string; decision: string; reasonCode: string }[]; products: { asin: string; firstDetectedAt: string; online: boolean; source: string }[]; automaticProducts: { provider: string; asin: string; title: string; firstAt: string; lastAt: string; missingCount: number; missingAt: string | null; imageUrl?: string; price?: number; rating?: string }[] }
const detail = ref<Detail>(), dialog = ref<HTMLDialogElement>(), error = ref(''), busy = ref(false), tab = ref('online'), q = ref(''), page = ref(1), size = ref(20), unsaved = ref(false)
const chosen=ref<string[]>([]), from=ref(''), to=ref(''), notice=ref('')
function day(value:string){return new Intl.DateTimeFormat('en-CA',{timeZone:'America/Los_Angeles'}).format(new Date(value))}
function date(value:string){return new Date(value).toLocaleString('zh-CN',{hour12:false})}
async function monitor(asins:string[]){if(!props.writable)return;busy.value=true;error.value='';try{const result=await monitoringApi.create({requestId:crypto.randomUUID(),asins,tags:[],remark:''});notice.value=`已添加 ${result.filter(r=>r.status==='CREATED').length} 个，已存在 ${result.filter(r=>r.status==='EXISTS').length} 个`;const failed=result.filter(r=>!['CREATED','EXISTS'].includes(r.status));if(failed.length)error.value=failed.map(r=>r.message).join('；');chosen.value=[]}catch(e){error.value=e instanceof Error?e.message:'添加监控失败'}finally{busy.value=false}}
const draft = ref({ name: '', owner: '', remark: '', enabled: true }), baseline = ref('')
const products = computed(() => detail.value?.products.filter(p => !q.value || p.asin.includes(q.value.toUpperCase())) ?? [])
const auto = computed(() => detail.value?.automaticProducts.filter(p => (tab.value === 'missing' ? p.missingCount >= 2 : p.missingCount < 2) && (!q.value || p.asin.includes(q.value.toUpperCase()) || p.title.toLowerCase().includes(q.value.toLowerCase())) && (!from.value || day(p.firstAt)>=from.value) && (!to.value || day(p.firstAt)<=to.value)) ?? [])
const total = computed(() => tab.value === 'manual' ? products.value.length : auto.value.length)
watch([q, tab, size, from, to], () => { page.value = 1 })
watch(() => props.store, async value => {
  if (!value) return
  error.value = ''; detail.value = undefined; tab.value = 'online'; page.value = 1; chosen.value=[];q.value='';from.value='';to.value='';notice.value=''
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
    class="store-detail monitor-workbench"
    aria-label="店铺监控详情"
    @cancel.prevent="close"
  >
    <header>
      <div>
        <h2>店铺监控详情</h2><p class="store-subtitle">
          {{ store?.storeName }} · 美国站
        </p>
      </div><button
        :disabled="busy"
        @click="close"
      >
        关闭
      </button>
    </header>
    <SuccessToast :message="notice" />
    <div class="store-body">
      <p
        v-if="error"
        role="alert"
      >
        {{ error }}
      </p>
      <div class="ui-section-tabs">
        <button
          v-for="t in [{id:'online',label:'在线产品'},{id:'missing',label:'疑似下架产品'},{id:'runs',label:'更新记录'},{id:'settings',label:'店铺设置'},{id:'manual',label:'人工记录'}]"
          :key="t.id"
          :aria-pressed="tab === t.id"
          @click="tab = t.id"
        >
          {{ t.label }}
        </button>
      </div>
      <template v-if="['manual','online','missing'].includes(tab)">
        <div class="mw-toolbar">
          <button
            v-if="tab!=='manual'"
            class="mw-primary"
            :disabled="!writable||!chosen.length||busy"
            @click="monitor(chosen)"
          >
            批量添加产品监控
          </button>
          <label>首次发现 <input
            v-model="from"
            type="date"
            aria-label="首次发现开始日期"
          ></label>
          <label>至 <input
            v-model="to"
            type="date"
            aria-label="首次发现结束日期"
          ></label>
          <input
            v-model="q"
            placeholder="搜索标题 / ASIN"
            aria-label="搜索标题或ASIN"
          >
        </div>
        <p class="store-hint">
          {{ tab==='manual' ? '人工快照记录，保留历史来源。' : '首次发现为本系统观测时间。公开搜索未出现的商品不据此判定下架。' }}
        </p>
        <TableViewport>
          <table class="mw-table ui-action-table">
            <thead>
              <tr>
                <th v-if="tab!=='manual'">
                  <input
                    type="checkbox"
                    aria-label="选择本页商品"
                    :checked="!!auto.length&&auto.slice((page-1)*size,page*size).every(p=>chosen.includes(p.asin))"
                    @change="chosen=($event.target as HTMLInputElement).checked?auto.slice((page-1)*size,page*size).map(p=>p.asin):[]"
                  >
                </th><th>产品信息</th><th v-if="tab!=='manual'">
                  价格
                </th><th v-if="tab!=='manual'">
                  星级评分
                </th><th>首次发现</th><th>最近观测</th><th>操作</th>
              </tr>
            </thead><tbody>
              <template v-if="tab === 'manual'">
                <tr
                  v-for="p in products.slice((page-1)*size,page*size)"
                  :key="p.asin"
                >
                  <td>{{ p.asin }}</td><td>{{ date(p.firstDetectedAt) }}</td><td>{{ p.online?'最近快照中出现':'最近快照中未出现' }}</td><td>人工记录</td>
                </tr>
              </template>
              <template v-else>
                <tr
                  v-for="p in auto.slice((page-1)*size,page*size)"
                  :key="p.provider+p.asin"
                >
                  <td>
                    <input
                      v-model="chosen"
                      type="checkbox"
                      :value="p.asin"
                      :aria-label="`选择 ${p.asin}`"
                    >
                  </td>
                  <td>
                    <div class="mw-cell-product">
                      <img
                        v-if="p.imageUrl"
                        :src="p.imageUrl"
                        alt="商品主图"
                      ><div
                        v-else
                        class="store-image-empty"
                      >
                        暂无图片
                      </div><div>
                        <a
                          :href="`https://www.amazon.com/dp/${p.asin}`"
                          target="_blank"
                          rel="noopener noreferrer"
                        >{{ p.title }}</a><small>{{ p.asin }}</small>
                      </div>
                    </div>
                  </td>
                  <td>{{ p.price==null?'—':`US$ ${Number(p.price).toFixed(2)}` }}</td><td>{{ p.rating||'—' }}</td><td>{{ day(p.firstAt) }}</td><td>{{ date(p.lastAt) }}</td>
                  <td>
                    <button
                      class="mw-link"
                      :disabled="!writable||busy"
                      @click="monitor([p.asin])"
                    >
                      添加产品监控
                    </button>
                  </td>
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
          {{ date(run.completedAt) }} · {{ run.decision==='PROMOTED'?'已更新商品清单':'保留原有数据' }}
        </p><p v-if="!detail?.runs.length">
          暂无更新记录。
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

.store-detail{width:min(1280px,calc(100vw - 24px));border:0;box-shadow:0 24px 80px #14284440}.store-detail header{padding:16px 20px;border-bottom:1px solid #e7ebf0}.store-detail h2{margin:0;font-size:18px}.store-subtitle{font-size:13px;color:#718096;margin:6px 0 0}.store-body{padding:0 16px 12px;flex:1}.store-body .ui-section-tabs{padding:12px 0 0;gap:20px;border-bottom:1px solid #e7ebf0}.store-body .ui-section-tabs button{border:0;border-radius:0;background:transparent;padding:10px 4px;color:#617188}.store-body .ui-section-tabs button[aria-pressed=true]{border-bottom:2px solid #3069df;color:#3069df;background:transparent}.store-body .mw-toolbar{padding:12px 0;gap:12px}.store-body .mw-toolbar label{font-size:12px;color:#718096;display:flex;align-items:center;gap:6px}.store-body .mw-toolbar input{width:150px}.store-hint{font-size:12px;color:#8a94a3;margin:0 0 10px}.store-image-empty{width:52px;height:60px;flex-shrink:0;background:#f6f8fb;border:1px solid #e7ebf0;border-radius:4px;display:grid;place-items:center;font-size:10px;color:#9ba5b5}.store-detail footer{border-top:1px solid #e7ebf0}.store-body> a{display:inline-block;font-size:13px;margin-top:12px}.store-detail .mw-table td:last-child{min-width:125px}
</style>
