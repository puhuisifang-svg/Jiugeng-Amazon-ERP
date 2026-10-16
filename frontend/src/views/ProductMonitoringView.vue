<script setup lang="ts">
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { monitoringApi, type FieldName, type MonitorDetail, type MonitorFields, type MonitorResult, type MonitorSnapshot, type MonitorTarget, type SnapshotInput } from '@/api/monitoring'
import { session } from '@/state/session'

const route = useRoute()
const rows = ref<MonitorTarget[]>([]), total = ref(0), active = ref(0)
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const filters = reactive({ q: '', searchBy: 'ASIN', status: 'ALL', owner: '', tag: '', followed: false, page: 0, size: 20 })
const selected = ref<string[]>([]), batchAction = ref('PAUSE'), batchTags = ref('')
const detail = ref<MonitorDetail>(), history = ref<MonitorSnapshot[]>([]), historyPage = ref(0), historyTotal = ref(0)
const modal = ref<HTMLDialogElement>(), modalKind = ref<'create' | 'columns' | 'snapshot'>('create')
const createAsins = ref(''), createTags = ref(''), createRemark = ref(''), results = ref<MonitorResult[]>([])
const columnLabels: Record<string, string> = { brand: '品牌', price: '价格', availability: '可售状态', owner: '监控人', tags: '标签', updated: '最近观测', source: '来源' }
const columns = ref(Object.keys(columnLabels)), draftColumns = ref<string[]>([]), columnSearch = ref('')
const fieldLabels: Record<FieldName, string> = { title: '标题', brand: '品牌', imageUrl: '主图 URL', bullets: '五点描述', price: '价格', currency: '币种', availability: '可售状态' }
const fieldNames = Object.keys(fieldLabels) as FieldName[]
const modes = reactive<Record<FieldName, string>>({ title: 'VALUE', brand: 'UNKNOWN', imageUrl: 'UNKNOWN', bullets: 'UNKNOWN', price: 'UNKNOWN', currency: 'VALUE', availability: 'UNKNOWN' })
const values = reactive<Record<FieldName, string>>({ title: '', brand: '', imageUrl: '', bullets: '', price: '', currency: 'USD', availability: 'AVAILABLE' })
const observation = reactive({ source: 'MANUAL', quality: 'COMPLETE', at: '', reason: '' })
let createRequest: { signature: string; id: string } | undefined
let snapshotRequest: { signature: string; id: string } | undefined
let loadSerial = 0
const isDetail = computed(() => typeof route.params.id === 'string')
const selectedRows = computed(() => rows.value.filter(row => selected.value.includes(row.id)))
const allSelected = computed(() => rows.value.length > 0 && selected.value.length === rows.value.length)
const statusLabels: Record<string, string> = { PENDING: '待首次观测', BASELINE: '已建立基线', SOURCE_BASELINE: '来源切换，重建基线', CHANGED: '发现变化', UNCHANGED: '无变化', PARTIAL: '部分观测', FAILED: '更新失败', OUT_OF_ORDER: '旧观测未采纳' }
const sourceLabel = (source: string | null) => source === 'MANUAL' ? '人工观测' : source === 'SYNTHETIC' ? '合成演示' : '暂无数据'
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const display = (value: unknown) => value === undefined ? '未观测' : value === null ? '未知' : Array.isArray(value) ? (value.join('\n') || '明确为空') : value === 'AVAILABLE' ? '可售' : value === 'UNAVAILABLE' ? '不可售' : String(value)
const stale = (row: MonitorTarget) => row.observedAt && Date.now() - Date.parse(row.observedAt) > 48 * 3600000
const split = (value: string) => [...new Set(value.split(/[\s,，]+/).map(v => v.trim()).filter(Boolean))]
const fail = (e: unknown) => { error.value = e instanceof Error ? e.message : '请求失败，请重试。' }
function requestKey(signature: string, previous?: { signature: string; id: string }) { return previous?.signature === signature ? previous : { signature, id: crypto.randomUUID() } }

async function load() {
  const serial = ++loadSerial
  loading.value = true; error.value = ''; selected.value = []
  try {
    if (isDetail.value) {
      const value = await monitoringApi.detail(String(route.params.id))
      if (serial !== loadSerial) return
      detail.value = value; historyPage.value = 0
      const page = await monitoringApi.history(value.target.id, 0)
      if (serial !== loadSerial) return
      history.value = page.items; historyTotal.value = page.total
    } else {
      const value = await monitoringApi.list({ ...filters })
      if (serial !== loadSerial) return
      rows.value = value.items; total.value = value.total; active.value = value.activeCount
    }
  } catch (e) { if (serial === loadSerial) fail(e) }
  finally { if (serial === loadSerial) loading.value = false }
}
async function search() { filters.page = 0; await load() }
async function reset() { Object.assign(filters, { q: '', searchBy: 'ASIN', status: 'ALL', owner: '', tag: '', followed: false, page: 0 }); await load() }
async function turnPage(delta: number) { filters.page += delta; await load() }
function toggleAll() { selected.value = allSelected.value ? [] : rows.value.map(r => r.id) }
async function show(kind: typeof modalKind.value) {
  error.value = ''; results.value = []; modalKind.value = kind
  if (kind === 'columns') { draftColumns.value = [...columns.value]; columnSearch.value = '' }
  if (kind === 'snapshot') {
    const date = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
    observation.at = date.toISOString().slice(0, 19); observation.reason = ''; observation.quality = 'COMPLETE'
    observation.source = detail.value?.target.source ?? 'MANUAL'
    for (const field of fieldNames) {
      const value = detail.value?.target.fields[field]
      modes[field] = value == null ? 'UNKNOWN' : 'VALUE'
      values[field] = Array.isArray(value) ? value.join('\n') : value == null ? '' : String(value)
    }
    values.currency = 'USD'; values.availability ||= 'AVAILABLE'
    if (!detail.value?.target.observedAt) { modes.title = 'VALUE'; modes.currency = 'VALUE' }
  }
  await nextTick(); modal.value?.showModal()
}
function close() { if (!busy.value) modal.value?.close() }
async function create() {
  busy.value = true; notice.value = String(); error.value = ''
  try {
    const input = { asins: split(createAsins.value).map(s => s.toUpperCase()), tags: split(createTags.value), remark: createRemark.value }
    if (!input.asins.length || input.asins.length > 100) throw new Error('每批请输入 1～100 个 ASIN。')
    createRequest = requestKey(JSON.stringify(input), createRequest)
    results.value = await monitoringApi.create({ ...input, requestId: createRequest.id })
    notice.value = `新增 ${results.value.filter(r => r.status === 'CREATED').length} 条，已存在 ${results.value.filter(r => r.status === 'EXISTS').length} 条。`
    await load()
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function batch(action = batchAction.value, targets = selectedRows.value) {
  if (!targets.length) return
  busy.value = true; notice.value = String(); error.value = ''
  try {
    const result = await monitoringApi.batch(targets.map(({ id, version }) => ({ id, version })), action, split(batchTags.value))
    const failed = result.filter(r => r.status !== 'UPDATED')
    notice.value = `已更新 ${result.length - failed.length} 条。${failed.map(r => `${r.asin}：${r.message}`).join('；')}`
    await load()
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function follow(row: MonitorTarget) {
  busy.value = true; notice.value = String(); error.value = ''
  try { await monitoringApi.follow(row.id, !row.followed); await load() } catch (e) { fail(e) } finally { busy.value = false }
}
async function followSelected(followed: boolean) {
  busy.value = true; notice.value = String(); error.value = ''; let done = 0
  try { for (const row of selectedRows.value) { await monitoringApi.follow(row.id, followed); done++ } notice.value = `已${followed ? '关注' : '取消关注'} ${done} 条。`; await load() }
  catch (e) { notice.value = `已完成 ${done} 条，其余可重试。`; fail(e) } finally { busy.value = false }
}
async function saveColumns() {
  busy.value = true; notice.value = String(); error.value = ''
  try { const p = await monitoringApi.columns(draftColumns.value); columns.value = p.columns; modal.value?.close() } catch (e) { fail(e) } finally { busy.value = false }
}
function moveColumn(index: number, offset: number) {
  const to = index + offset
  if (to < 0 || to >= draftColumns.value.length) return
  const copy = [...draftColumns.value], item = copy.splice(index, 1)[0]
  if (item) copy.splice(to, 0, item)
  draftColumns.value = copy
}
async function saveSnapshot() {
  if (!detail.value) return
  busy.value = true; notice.value = String(); error.value = ''
  try {
    const fields: Record<string, unknown> = {}
    for (const field of fieldNames) {
      if (modes[field] === 'UNOBSERVED') continue
      if (modes[field] === 'UNKNOWN') { fields[field] = null; continue }
      const value = values[field].trim()
      if (!value && field !== 'bullets') throw new Error(`${fieldLabels[field]}为空，请明确选择“未知”。`)
      fields[field] = field === 'bullets' ? value.split('\n').filter(Boolean) : field === 'price' ? Number(value) : value
    }
    const input = { version: detail.value.target.version, observedAt: new Date(observation.at).toISOString(), source: observation.source, quality: observation.quality, fields: fields as MonitorFields, reason: observation.reason }
    snapshotRequest = requestKey(JSON.stringify({ id: detail.value.target.id, ...input }), snapshotRequest)
    const result = await monitoringApi.snapshot(detail.value.target.id, { ...input, requestId: snapshotRequest.id } as SnapshotInput)
    notice.value = `观测已保存：${statusLabels[result.decision] ?? result.decision}`
    modal.value?.close(); snapshotRequest = undefined; await load()
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function historyTurn(delta: number) {
  if (!detail.value) return
  loading.value = true
  try { const page = await monitoringApi.history(detail.value.target.id, historyPage.value + delta); history.value = page.items; historyTotal.value = page.total; historyPage.value += delta } catch (e) { fail(e) } finally { loading.value = false }
}
watch(() => route.params.id, load)
watch(() => JSON.stringify(filters), () => { selected.value = [] })
onMounted(async () => { await load(); try { columns.value = (await monitoringApi.preferences()).columns } catch (e) { fail(e) } })
</script>

<template>
  <section class="monitor-page page-stack">
    <div class="monitor-intro">
      <div><span class="monitor-eyebrow">AMAZON · US</span><h2>{{ isDetail ? '商品观测详情' : '产品监控' }}</h2><p>持续记录商品变化，让每一次判断都有依据。</p></div><span class="monitor-source-note">人工观测 / 合成演示 · 尚未接入自动采集</span>
    </div>
    <SuccessToast :message="notice" />
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <template v-if="!isDetail">
      <form
        class="panel monitor-filters"
        @submit.prevent="search"
      >
        <label>站点<select disabled><option>美国 · USD</option></select></label>
        <label>监控状态<select v-model="filters.status"><option value="ALL">全部状态</option><option value="ACTIVE">监控中</option><option value="PAUSED">已暂停</option></select></label>
        <label>监控人<select v-model="filters.owner"><option value="">全部人员</option><option :value="session.user.value?.id">我负责的</option></select></label>
        <label>标签<input
          v-model="filters.tag"
          maxlength="50"
          placeholder="精确匹配标签"
        ></label>
        <label class="monitor-check"><input
          v-model="filters.followed"
          type="checkbox"
        >仅我关注</label>
        <div class="monitor-search">
          <select
            v-model="filters.searchBy"
            aria-label="搜索字段"
          >
            <option value="ASIN">
              ASIN
            </option><option value="TITLE">
              标题
            </option>
          </select><input
            v-model="filters.q"
            aria-label="搜索内容"
            :placeholder="filters.searchBy === 'ASIN' ? '输入 ASIN，多个用空格或逗号分隔' : '搜索商品标题'"
            maxlength="2000"
          ><button :disabled="loading">
            搜索
          </button><button
            type="button"
            :disabled="loading"
            @click="reset"
          >
            重置
          </button>
        </div>
      </form>
      <article class="panel monitor-list">
        <div class="monitor-toolbar">
          <button
            class="primary"
            :disabled="busy"
            @click="show('create')"
          >
            ＋ 添加监控
          </button><span class="monitor-quota">启用目标 <strong>{{ active }}</strong> / 1,000</span><span class="monitor-spacer" /><button
            :disabled="busy"
            @click="show('columns')"
          >
            自定义列
          </button><button
            :disabled="loading || busy"
            @click="load"
          >
            刷新列表
          </button>
        </div>
        <div class="monitor-batch">
          <span>已选 {{ selected.length }} 条</span><select
            v-model="batchAction"
            aria-label="批量操作"
          >
            <option value="PAUSE">
              暂停监控
            </option><option value="ENABLE">
              恢复监控
            </option><option value="ASSIGN_SELF">
              设为我负责
            </option><option value="CLEAR_OWNER">
              清除监控人
            </option><option value="ADD_TAGS">
              追加标签
            </option><option value="REMOVE_TAGS">
              移除标签
            </option>
          </select><input
            v-if="batchAction.includes('TAGS')"
            v-model="batchTags"
            aria-label="批量标签"
            placeholder="标签以逗号分隔"
          ><button
            :disabled="!selected.length || busy || loading"
            @click="batch()"
          >
            应用
          </button><button
            :disabled="!selected.length || busy || loading"
            @click="followSelected(true)"
          >
            关注
          </button><button
            :disabled="!selected.length || busy || loading"
            @click="followSelected(false)"
          >
            取消关注
          </button>
        </div>
        <p
          v-if="loading"
          class="data-state"
        >
          正在加载监控清单…
        </p>
        <div
          v-else
          class="monitor-table-wrap"
        >
          <TableViewport>
            <table class="monitor-table">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      aria-label="选择本页全部"
                      :checked="allSelected"
                      :disabled="busy"
                      @change="toggleAll"
                    >
                  </th><th>监控 / 关注</th><th class="monitor-product-col">
                    商品 / ASIN
                  </th><th>最新状态</th><th
                    v-for="col in columns"
                    :key="col"
                  >
                    {{ columnLabels[col] }}
                  </th><th>操作</th>
                </tr>
              </thead><tbody>
                <tr
                  v-for="row in rows"
                  :key="row.id"
                >
                  <td>
                    <input
                      v-model="selected"
                      type="checkbox"
                      :value="row.id"
                      :aria-label="`选择 ${row.asin}`"
                      :disabled="busy"
                    >
                  </td><td>
                    <span :class="['monitor-pill', { paused: !row.enabled }]">{{ row.enabled ? '监控中' : '已暂停' }}</span><button
                      class="monitor-star"
                      :aria-label="`${row.followed ? '取消关注' : '关注'} ${row.asin}`"
                      :disabled="busy"
                      @click="follow(row)"
                    >
                      {{ row.followed ? '★' : '☆' }}
                    </button>
                  </td><td>
                    <div class="monitor-product">
                      <img
                        v-if="row.fields.imageUrl"
                        :src="row.fields.imageUrl"
                        alt="商品主图"
                        referrerpolicy="no-referrer"
                      ><span
                        v-else
                        class="monitor-image-empty"
                      >暂无图片</span><div>
                        <RouterLink :to="`/product-monitoring/${row.id}`">
                          {{ row.fields.title || '等待首次观测' }}
                        </RouterLink><p class="monitor-asin">
                          {{ row.asin }}
                        </p>
                      </div>
                    </div>
                  </td><td>
                    <span>{{ statusLabels[row.lastStatus] ?? row.lastStatus }}</span><small
                      v-if="stale(row)"
                      class="monitor-stale"
                    >数据已超过 48 小时</small>
                  </td><td
                    v-for="col in columns"
                    :key="col"
                  >
                    <template v-if="col === 'brand'">
                      {{ display(row.fields.brand) }}
                    </template><template v-else-if="col === 'price'">
                      {{ row.fields.price == null ? '—' : `USD ${row.fields.price.toFixed(2)}` }}
                    </template><template v-else-if="col === 'availability'">
                      {{ display(row.fields.availability) }}
                    </template><template v-else-if="col === 'owner'">
                      {{ row.ownerName || '未分配' }}
                    </template><template v-else-if="col === 'tags'">
                      <span
                        v-for="tag in row.tags"
                        :key="tag"
                        class="monitor-tag"
                      >{{ tag }}</span><span v-if="!row.tags.length">—</span>
                    </template><template v-else-if="col === 'updated'">
                      {{ formatTime(row.observedAt) }}
                    </template><template v-else-if="col === 'source'">
                      {{ sourceLabel(row.source) }}
                    </template>
                  </td><td>
                    <RouterLink :to="`/product-monitoring/${row.id}`">
                      详情
                    </RouterLink><button
                      class="monitor-text-button"
                      :disabled="busy"
                      @click="batch(row.enabled ? 'PAUSE' : 'ENABLE', [row])"
                    >
                      {{ row.enabled ? '暂停' : '恢复' }}
                    </button>
                  </td>
                </tr><tr v-if="!rows.length">
                  <td
                    :colspan="columns.length + 5"
                    class="monitor-empty"
                  >
                    <h3>暂无监控商品</h3><p>添加美国站 ASIN，录入首次观测后即可建立基线。</p><button @click="show('create')">
                      添加监控
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </div>
        <div class="monitor-pagination">
          <span>共 {{ total }} 条</span><span class="monitor-spacer" /><button
            :disabled="filters.page === 0 || loading || busy"
            @click="turnPage(-1)"
          >
            上一页
          </button><span>第 {{ filters.page + 1 }} 页</span><button
            :disabled="(filters.page + 1) * filters.size >= total || loading || busy"
            @click="turnPage(1)"
          >
            下一页
          </button><select
            v-model.number="filters.size"
            aria-label="每页条数"
            :disabled="busy"
            @change="search"
          >
            <option :value="20">
              20 条/页
            </option><option :value="50">
              50 条/页
            </option><option :value="100">
              100 条/页
            </option>
          </select>
        </div>
      </article>
    </template>
    <template v-else-if="detail">
      <div class="monitor-toolbar">
        <RouterLink to="/product-monitoring">
          ← 返回监控清单
        </RouterLink><span class="monitor-spacer" /><button
          :disabled="busy || loading"
          @click="load"
        >
          刷新
        </button><button
          :disabled="busy || loading"
          @click="batch(detail.target.enabled ? 'PAUSE' : 'ENABLE', [detail.target])"
        >
          {{ detail.target.enabled ? '暂停监控' : '恢复监控' }}
        </button><button
          class="primary"
          :disabled="!detail.target.enabled || busy || loading"
          @click="show('snapshot')"
        >
          录入观测
        </button>
      </div>
      <article class="panel monitor-detail">
        <div class="monitor-product">
          <img
            v-if="detail.target.fields.imageUrl"
            :src="detail.target.fields.imageUrl"
            alt="商品主图"
            referrerpolicy="no-referrer"
          ><div><h2>{{ detail.target.fields.title || '等待首次观测' }}</h2><p>{{ detail.target.asin }} · 美国站 · {{ detail.target.enabled ? '监控中' : '已暂停' }}</p><p>{{ sourceLabel(detail.target.source) }} · 最近观测 {{ formatTime(detail.target.observedAt) }}</p></div>
        </div><p v-if="detail.target.remark">
          备注：{{ detail.target.remark }}
        </p><p>最近结果：{{ statusLabels[detail.target.lastStatus] }} {{ detail.target.lastReason }}</p><p
          v-if="stale(detail.target)"
          class="monitor-stale"
        >
          数据已超过 48 小时，请补充最新观测。
        </p><dl class="monitor-field-grid">
          <div
            v-for="field in fieldNames.filter(f => f !== 'imageUrl')"
            :key="field"
          >
            <dt>{{ fieldLabels[field] }}</dt><dd>{{ display(detail.target.fields[field]) }}</dd>
          </div>
        </dl>
      </article>
      <article class="panel monitor-detail">
        <h3>商品变化与历史快照</h3><p class="muted">
          记录商品观测，不记录人员操作历史。发现时间不代表 Amazon 实际修改时刻。
        </p><div
          v-if="!history.length"
          class="monitor-empty"
        >
          尚无快照。录入完整观测以建立基线。
        </div><details
          v-for="snapshot in history"
          :key="snapshot.id"
          class="monitor-history"
        >
          <summary><strong>{{ statusLabels[snapshot.decision] }}</strong><span>{{ formatTime(snapshot.observedAt) }} · {{ sourceLabel(snapshot.source) }}</span><span>{{ snapshot.changes.length }} 项变化</span></summary><p>记录时间：{{ formatTime(snapshot.recordedAt) }} <span v-if="snapshot.reason"> · {{ snapshot.reason }}</span></p><div
            v-for="change in snapshot.changes"
            :key="change.field"
            class="monitor-change"
          >
            <strong>{{ fieldLabels[change.field] }}</strong><div><span>之前</span><pre>{{ display(change.before) }}</pre></div><div><span>之后</span><pre>{{ display(change.after) }}</pre></div>
          </div><dl class="monitor-field-grid">
            <div
              v-for="field in fieldNames"
              :key="field"
            >
              <dt>{{ fieldLabels[field] }}</dt><dd>{{ display(snapshot.fields[field]) }}</dd>
            </div>
          </dl>
        </details><div class="monitor-pagination">
          <span>共 {{ historyTotal }} 次观测 · 第 {{ historyPage + 1 }} 页</span><span class="monitor-spacer" /><button
            :disabled="historyPage === 0 || loading"
            @click="historyTurn(-1)"
          >
            上一页
          </button><button
            :disabled="(historyPage + 1) * 20 >= historyTotal || loading"
            @click="historyTurn(1)"
          >
            下一页
          </button>
        </div>
      </article>
    </template>
    <p
      v-else-if="loading"
      class="data-state"
    >
      正在加载详情…
    </p>
    <dialog
      ref="modal"
      class="monitor-dialog"
      @cancel="busy && $event.preventDefault()"
    >
      <div class="monitor-dialog-heading">
        <h2>{{ modalKind === 'create' ? '添加产品监控' : modalKind === 'columns' ? '自定义列' : '录入商品观测' }}</h2><button
          aria-label="关闭弹窗"
          :disabled="busy"
          @click="close"
        >
          ×
        </button>
      </div>
      <p
        v-if="error"
        class="data-state data-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <form
        v-if="modalKind === 'create'"
        class="monitor-form"
        @submit.prevent="create"
      >
        <p>美国站 · 每批最多 100 条。新增后等待人工观测，不自动采集。</p><label>ASIN<textarea
          v-model="createAsins"
          required
          rows="5"
          maxlength="2000"
          placeholder="每行一个 ASIN，或用空格、逗号分隔"
        /></label><label>标签<input
          v-model="createTags"
          placeholder="逗号分隔，最多 20 个"
        ></label><label>备注<textarea
          v-model="createRemark"
          maxlength="1000"
          rows="2"
        /></label><ul
          v-if="results.length"
          class="monitor-results"
        >
          <li
            v-for="(result, index) in results"
            :key="index"
          >
            {{ result.asin }} — {{ result.message }}
          </li>
        </ul><footer>
          <button
            type="button"
            :disabled="busy"
            @click="close"
          >
            关闭
          </button><button
            class="primary"
            :disabled="busy"
          >
            {{ busy ? '保存中…' : '校验并添加' }}
          </button>
        </footer>
      </form>
      <form
        v-else-if="modalKind === 'columns'"
        class="monitor-form"
        @submit.prevent="saveColumns"
      >
        <p>仅影响你的列表。商品标识、监控状态和操作列始终保留。</p><input
          v-model="columnSearch"
          aria-label="搜索列"
          placeholder="搜索字段"
        ><div class="monitor-column-options">
          <label
            v-for="(label, key) in columnLabels"
            v-show="label.includes(columnSearch)"
            :key="key"
            class="monitor-check"
          ><input
            v-model="draftColumns"
            type="checkbox"
            :value="key"
          >{{ label }}</label>
        </div><ol>
          <li
            v-for="(col, index) in draftColumns"
            :key="col"
          >
            {{ columnLabels[col] }} <button
              type="button"
              :disabled="index === 0"
              :aria-label="`上移${columnLabels[col]}`"
              @click="moveColumn(index, -1)"
            >
              ↑
            </button><button
              type="button"
              :disabled="index === draftColumns.length - 1"
              :aria-label="`下移${columnLabels[col]}`"
              @click="moveColumn(index, 1)"
            >
              ↓
            </button>
          </li>
        </ol><footer>
          <button
            type="button"
            :disabled="busy"
            @click="draftColumns = Object.keys(columnLabels)"
          >
            恢复默认
          </button><button
            type="button"
            :disabled="busy"
            @click="close"
          >
            取消
          </button><button
            class="primary"
            :disabled="busy"
          >
            保存并应用
          </button>
        </footer>
      </form>
      <form
        v-else
        class="monitor-form"
        @submit.prevent="saveSnapshot"
      >
        <div class="monitor-field-grid">
          <label>来源<select v-model="observation.source"><option value="MANUAL">人工观测</option><option value="SYNTHETIC">合成演示</option></select></label><label>观测时间（本地时区）<input
            v-model="observation.at"
            type="datetime-local"
            step="1"
            required
          ></label><label>完整性<select v-model="observation.quality"><option value="COMPLETE">完整观测</option><option value="PARTIAL">部分观测</option><option value="FAILED">失败记录</option></select></label>
        </div><p>完整观测需逐项确认；“未知”是明确观测结果，“未观测”不会覆盖旧值。部分和失败记录不会更新基线。</p><div
          v-for="field in fieldNames"
          :key="field"
          class="monitor-input-row"
        >
          <label :for="`field-${field}`">{{ fieldLabels[field] }}</label><select
            v-model="modes[field]"
            :aria-label="`${fieldLabels[field]}观测状态`"
          >
            <option value="VALUE">
              有值
            </option><option value="UNKNOWN">
              明确未知
            </option><option value="UNOBSERVED">
              未观测
            </option>
          </select><template v-if="modes[field] === 'VALUE'">
            <textarea
              v-if="field === 'bullets'"
              :id="`field-${field}`"
              v-model="values[field]"
              placeholder="每行一条，最多五条"
            /><select
              v-else-if="field === 'availability'"
              :id="`field-${field}`"
              v-model="values[field]"
            >
              <option value="AVAILABLE">
                可售
              </option><option value="UNAVAILABLE">
                不可售
              </option>
            </select><input
              v-else
              :id="`field-${field}`"
              v-model="values[field]"
              :type="field === 'price' ? 'number' : field === 'imageUrl' ? 'url' : 'text'"
              :readonly="field === 'currency'"
              :min="field === 'price' ? 0 : undefined"
              :step="field === 'price' ? '0.01' : undefined"
              :maxlength="2000"
              required
            >
          </template><span
            v-else
            class="muted"
          >{{ modes[field] === 'UNKNOWN' ? '保存为未知值' : '本次没有观测此项' }}</span>
        </div><label>原因 / 说明<textarea
          v-model="observation.reason"
          maxlength="500"
          :required="observation.quality !== 'COMPLETE'"
          rows="2"
        /></label><footer>
          <button
            type="button"
            :disabled="busy"
            @click="close"
          >
            取消
          </button><button
            class="primary"
            :disabled="busy"
          >
            {{ busy ? '保存中…' : '保存观测' }}
          </button>
        </footer>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.monitor-page{color:#24334b}.monitor-intro{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:8px 0}.monitor-intro h2{margin:6px 0;font-size:25px}.monitor-intro p{color:#728097;margin:0}.monitor-eyebrow{font-size:11px;font-weight:700;letter-spacing:2px;color:#4166ad}.monitor-source-note{font-size:12px;background:#fff8e7;color:#8a6723;padding:9px 14px;border-radius:6px}.monitor-filters{display:flex;flex-wrap:wrap;gap:16px;padding:20px}.monitor-filters label,.monitor-form label{display:flex;flex-direction:column;gap:7px;font-size:13px}.monitor-check{flex-direction:row!important;align-items:center}.monitor-check input{width:auto}.monitor-search{display:flex;gap:10px;width:100%}.monitor-search input{flex:1}.monitor-search select{width:100px}.monitor-list{padding:0;overflow:hidden}.monitor-toolbar,.monitor-batch,.monitor-pagination{display:flex;align-items:center;gap:12px;padding:16px 20px;flex-wrap:wrap}.monitor-batch{padding-top:0;font-size:13px;color:#6e7d93}.monitor-spacer{flex:1}.monitor-quota{font-size:13px;color:#768398}.monitor-quota strong{color:#253d66}.monitor-page button.primary{background:#3069df;border-color:#3069df;color:white}.monitor-page button:disabled{opacity:.45;cursor:not-allowed}.monitor-table-wrap{overflow:auto}.monitor-table{width:100%;border-collapse:collapse;font-size:13px}.monitor-table th{background:#f5f7fb;text-align:left;color:#697992;font-weight:500;white-space:nowrap;padding:14px 16px}.monitor-table td{padding:16px;border-top:1px solid #edf0f5;min-width:110px}.monitor-table td:first-child{min-width:30px}.monitor-table td:last-child{position:sticky;right:0;background:white;box-shadow:-2px 0 4px #23395808;min-width:120px}.monitor-product{display:flex;align-items:center;gap:14px;min-width:300px;max-width:430px}.monitor-product img,.monitor-image-empty{width:56px;height:64px;object-fit:contain;flex-shrink:0;border:1px solid #edf0f5;border-radius:6px}.monitor-image-empty{font-size:10px;display:grid;place-items:center;color:#9ba5b5;background:#f6f8fb}.monitor-product a{display:block;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:500;color:#2e4e7f}.monitor-asin{color:#6481ab;font-size:12px;margin:6px 0 0;letter-spacing:.5px}.monitor-pill{background:#edf8f4;color:#268262;border-radius:4px;padding:5px 7px;font-size:12px;white-space:nowrap}.monitor-pill.paused{background:#f0f2f5;color:#778197}.monitor-star{border:0;background:none;font-size:21px;color:#ca982d;padding:4px}.monitor-stale{display:block;color:#b47a1f;font-size:12px;margin-top:5px}.monitor-tag{display:inline-block;border:1px solid #e0e7f3;padding:2px 7px;border-radius:4px;color:#6681a6;margin:2px}.monitor-text-button{border:0;background:none;color:#456db8;margin-left:10px}.monitor-empty{text-align:center;padding:56px!important;color:#7b889c}.monitor-empty h3{color:#415573;font-weight:500}.monitor-pagination{border-top:1px solid #edf0f5;font-size:13px;color:#6e7d93}.monitor-detail{padding:24px}.monitor-detail .monitor-product{max-width:none}.monitor-detail .monitor-product img{width:90px;height:100px}.monitor-field-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin:20px 0}.monitor-field-grid dt{color:#7b879a;font-size:12px;margin-bottom:6px}.monitor-field-grid dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px}.monitor-history{border-top:1px solid #e8edf5;padding:16px 0}.monitor-history summary{display:flex;gap:20px;cursor:pointer;align-items:center;font-size:13px}.monitor-history summary strong{color:#345fa5}.monitor-history summary span{color:#78869b}.monitor-change{display:grid;grid-template-columns:100px 1fr 1fr;gap:16px;margin:12px 0;background:#f5f8fc;padding:12px}.monitor-change span{font-size:12px;color:#7e8da1}.monitor-change pre{white-space:pre-wrap;overflow-wrap:anywhere;font-family:inherit}.monitor-dialog{border:0;border-radius:12px;padding:0;width:min(780px,90vw);max-height:88vh;box-shadow:0 24px 80px #172e4b30;color:#273d5e}.monitor-dialog::backdrop{background:#14284466}.monitor-dialog-heading{display:flex;align-items:center;justify-content:space-between;padding:18px 24px;border-bottom:1px solid #e7edf6}.monitor-dialog-heading h2{font-size:18px;margin:0}.monitor-dialog-heading button{border:0;background:none;font-size:24px}.monitor-form{display:flex;flex-direction:column;gap:16px;padding:24px}.monitor-form p{font-size:13px;color:#79879c;line-height:1.6;margin:0}.monitor-form textarea{resize:vertical;width:100%;box-sizing:border-box}.monitor-form footer{display:flex;justify-content:flex-end;gap:10px;border-top:1px solid #edf0f5;padding-top:18px;margin-top:6px}.monitor-input-row{display:grid;grid-template-columns:90px 130px 1fr;gap:12px;align-items:center}.monitor-input-row input,.monitor-input-row select{min-width:0;width:100%}.monitor-column-options{display:flex;gap:16px;flex-wrap:wrap}.monitor-form ol li{padding:5px}.monitor-results{max-height:170px;overflow:auto;padding:12px 25px;background:#f6f8fc;font-size:13px}.muted{color:#8792a5;font-size:12px}@media(max-width:900px){.monitor-intro{align-items:flex-start;flex-direction:column}.monitor-input-row{grid-template-columns:80px 110px 1fr}.monitor-field-grid{grid-template-columns:1fr}}
.monitor-page .panel { min-height: 0; }
.monitor-filters { align-items: end; align-content: start; }
.monitor-page button { min-height: 34px; padding: 6px 12px; border: 1px solid #d9e1ed; border-radius: 5px; background: #fff; color: #425572; cursor: pointer; font-size: 13px; }
.monitor-page input:not([type=checkbox]), .monitor-page select, .monitor-page textarea { min-height: 36px; padding: 7px 10px; border: 1px solid #d9e1ed; border-radius: 5px; color: #30435e; background-color: #fff; font-size: 13px; }
.monitor-page select { min-width: 120px; }
.monitor-page input[type=checkbox] { width: 15px; height: 15px; accent-color: #3069df; }
.monitor-search { align-items: center; }
.monitor-search input { min-width: 140px; }
.monitor-page .monitor-star { min-height: 28px; padding: 3px 6px; border: 0; background: none; color: #ca982d; font-size: 21px; }
.monitor-page .monitor-text-button { border: 0; color: #456db8; background: none; }
.monitor-page .monitor-dialog-heading button { border: 0; font-size: 24px; }
.monitor-page .monitor-input-row select { min-width: 0; }
</style>
