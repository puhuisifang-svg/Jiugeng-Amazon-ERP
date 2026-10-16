<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { developmentApi, type DevelopmentDetail, type DevelopmentFields, type DevelopmentPage, type DevelopmentStatus } from '@/api/development'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import { runtimeConfig } from '@/config/runtime'
import { session } from '@/state/session'

const route = useRoute()
const id = computed(() => String(route.params.id || ''))
const result = ref<DevelopmentPage>({ items: [], total: 0, page: 0, size: 20 })
const detail = ref<DevelopmentDetail>(); const form = ref<DevelopmentFields>(); const products = ref<ProductDetailDto[]>([])
const q = ref(''); const status = ref<DevelopmentStatus | ''>(''); const page = ref(0); const size = ref(20)
const loading = ref(false); const saving = ref(false); const error = ref(''); const success = ref(''); const productError = ref('')
const showVoid = ref(false); const voidReason = ref('')
const writable = computed(() => session.canAccess(['products.write']))
const editable = computed(() => writable.value && !!detail.value && ['DRAFT', 'IN_PROGRESS'].includes(detail.value.item.status))
const dirty = computed(() => !!form.value && !!detail.value && JSON.stringify(form.value) !== JSON.stringify(detail.value.item.fields))
const labels: Record<DevelopmentStatus, string> = { DRAFT: '草稿', IN_PROGRESS: '进行中', COMPLETED: '已完成', VOID: '已作废' }
const actions: Record<string, string> = { DEVELOPMENT_CREATE: '候选通过，创建开发草稿', DEVELOPMENT_UPDATE: '保存开发资料', DEVELOPMENT_IN_PROGRESS: '开始开发', DEVELOPMENT_COMPLETED: '完成开发', DEVELOPMENT_VOID: '作废需求' }
const numericFields = [
  { key: 'purchaseCost', label: '采购成本（CNY）' }, { key: 'weight', label: '商品重量（g）' },
  { key: 'length', label: '长（cm）' }, { key: 'width', label: '宽（cm）' }, { key: 'height', label: '高（cm）' },
] as const
const noteFields = [{ key: 'supplyNotes', label: '供货方案 / 报价说明' }, { key: 'sampleNotes', label: '打样 / 验收结论' }, { key: 'researchNotes', label: '竞品研究结论' }, { key: 'remark', label: '开发备注' }] as const
let sequence = 0
function message(exception: unknown) { return exception instanceof ApiError ? exception.message : '请求失败，请检查网络并刷新核对结果。' }
async function loadProducts() {
  productError.value = ''
  try { products.value = (await domainApi.products()).items.filter(product => product.status === 'ACTIVE') }
  catch { productError.value = '产品列表加载失败，可点击重试；不影响查看开发需求。' }
}
async function load(force = false) {
  if (!force && dirty.value && !window.confirm('刷新会丢弃尚未保存的开发资料，确定刷新？')) return
  const request = ++sequence; loading.value = true; error.value = ''
  if (runtimeConfig.authMode === 'mock') { loading.value = false; error.value = '新品开发需要正式持久化模式，模拟候选不会创建开发需求。'; return }
  try {
    if (id.value) {
      const data = await developmentApi.detail(id.value)
      if (request !== sequence) return
      detail.value = data; form.value = JSON.parse(JSON.stringify(data.item.fields))
    } else {
      const data = await developmentApi.list(q.value.trim(), status.value, page.value, size.value)
      if (request === sequence) result.value = data
    }
  } catch (exception) { if (request === sequence) error.value = message(exception) }
  finally { if (request === sequence) loading.value = false }
}
async function save() {
  if (!form.value || !detail.value || saving.value || !editable.value) return
  saving.value = true; error.value = ''; success.value = ''
  const payload = JSON.parse(JSON.stringify(form.value)) as DevelopmentFields
  for (const { key } of numericFields) { const value = payload[key] as number | ''; payload[key] = value === '' || value === null ? null : Number(value) }
  for (const key of ['startDate', 'endDate', 'launchDate', 'linkedProductId'] as const) payload[key] ||= null
  try {
    const item = await developmentApi.update(id.value, detail.value.item.version, payload)
    detail.value.item = item; form.value = JSON.parse(JSON.stringify(item.fields)); success.value = '开发资料已保存。'
    await load(true)
  } catch (exception) { error.value = message(exception) }
  finally { saving.value = false }
}
async function transition(target: DevelopmentStatus) {
  if (!detail.value || dirty.value || saving.value || !editable.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const item = await developmentApi.transition(id.value, detail.value.item.version, target, target === 'VOID' ? voidReason.value.trim() : undefined)
    detail.value.item = item; showVoid.value = false; success.value = target === 'IN_PROGRESS' ? '已开始开发。' : `需求${labels[target]}。`; await load(true)
  } catch (exception) { error.value = message(exception) }
  finally { saving.value = false }
}
function search() { page.value = 0; void load() }
function reset() { q.value = ''; status.value = ''; search() }
function changePage(value: number) { page.value = value; void load() }
function changeStatus(value: DevelopmentStatus | '') { status.value = value; search() }
function mayLeave() { return !saving.value && (!dirty.value || window.confirm('开发资料尚未保存，确定离开？')) }
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onBeforeRouteLeave(mayLeave); onBeforeRouteUpdate(mayLeave)
watch(id, () => {
  detail.value = undefined; form.value = undefined; success.value = ''; showVoid.value = false; page.value = 0
  void load(true)
  if (id.value && writable.value && runtimeConfig.authMode !== 'mock') void loadProducts()
}, { immediate: true })
</script>

<template>
  <section class="page-stack development-page">
    <header class="panel development-header">
      <div>
        <p class="eyebrow">
          NEW PRODUCT DEVELOPMENT
        </p><h1>{{ id ? detail?.item.number || '新品开发详情' : '新品开发' }}</h1><p>承接审核通过的候选，跟进商品方案、打样与开发交付。</p>
      </div>
      <div class="development-actions">
        <button
          type="button"
          class="secondary-action"
          :disabled="loading || saving"
          @click="load()"
        >
          刷新
        </button><RouterLink
          class="secondary-action"
          :to="id ? '/product-development' : '/selection/candidates'"
        >
          {{ id ? '返回开发列表' : '前往候选池' }}
        </RouterLink>
      </div>
    </header>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p>
    <template v-if="!id">
      <div
        class="development-tabs"
        role="group"
        aria-label="开发状态"
      >
        <button
          type="button"
          :aria-pressed="status === ''"
          @click="changeStatus('')"
        >
          全部
        </button><button
          v-for="(label,key) in labels"
          :key="key"
          type="button"
          :aria-pressed="status === key"
          @click="changeStatus(key)"
        >
          {{ label }}
        </button>
      </div>
      <form
        class="panel filter-panel development-filter"
        @submit.prevent="search"
      >
        <label>搜索<input
          v-model="q"
          maxlength="300"
          placeholder="需求编号 / 名称 / ASIN / 开发员"
        ></label><button
          type="submit"
          :disabled="loading"
        >
          搜索
        </button><button
          type="button"
          @click="reset"
        >
          重置
        </button>
      </form>
      <section
        class="panel table-panel"
        :aria-busy="loading"
      >
        <p
          v-if="loading"
          class="data-state"
        >
          正在加载…
        </p>
        <div
          v-else-if="!result.items.length"
          class="empty-state"
        >
          <span class="empty-state__icon">新</span><h2>暂无符合条件的新品需求</h2><p>候选审核通过后将自动生成草稿，历史已通过候选也会进入此列表。</p><RouterLink
            class="text-link"
            to="/selection/candidates"
          >
            查看候选池
          </RouterLink>
        </div>
        <div
          v-else
          class="table-scroll"
        >
          <table class="data-table">
            <thead><tr><th>需求编号 / 名称</th><th>来源 ASIN</th><th>开发员</th><th>推荐星级</th><th>开发周期</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="item in result.items"
                :key="item.id"
              >
                <td>
                  <RouterLink
                    class="text-link"
                    :to="`/product-development/${item.id}`"
                  >
                    <strong>{{ item.number }}</strong>
                  </RouterLink><small>{{ item.fields.name }}</small>
                </td><td>{{ item.source.asin }}</td><td>{{ item.fields.developer || '未分配' }}</td><td>{{ item.fields.stars ? '★'.repeat(item.fields.stars) : '未评定' }}</td><td>{{ item.fields.startDate || '待定' }}<small>至 {{ item.fields.endDate || '待定' }}</small></td><td>
                  <span
                    class="development-status"
                    :data-status="item.status"
                  >{{ labels[item.status] }}</span>
                </td><td>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</td><td>
                  <RouterLink
                    class="text-link"
                    :to="`/product-development/${item.id}`"
                  >
                    查看需求
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="development-pagination">
          <span>共 {{ result.total }} 条</span><select
            v-model.number="size"
            aria-label="每页条数"
            @change="search"
          >
            <option :value="20">
              20 条/页
            </option><option :value="50">
              50 条/页
            </option><option :value="100">
              100 条/页
            </option>
          </select><button
            type="button"
            :disabled="loading || page <= 0"
            @click="changePage(page - 1)"
          >
            上一页
          </button><span>第 {{ page + 1 }} 页</span><button
            type="button"
            :disabled="loading || (page + 1) * size >= result.total"
            @click="changePage(page + 1)"
          >
            下一页
          </button>
        </footer>
      </section>
    </template>
    <p
      v-else-if="loading"
      class="data-state"
    >
      正在加载开发资料…
    </p>
    <template v-else-if="detail && form">
      <section class="panel development-source">
        <div class="development-section-heading">
          <h2>来源候选 · {{ detail.item.source.asin }}</h2><span
            class="development-status"
            :data-status="detail.item.status"
          >{{ labels[detail.item.status] }}</span>
        </div><p>{{ detail.item.source.title }}</p><p>美国站 · 通过时评分 {{ detail.item.source.score }} · 参考售价 {{ detail.item.source.referencePrice === null ? '未知' : `USD ${detail.item.source.referencePrice}` }}（不是采购成本）</p><p>{{ detail.item.source.selectionReason || '未填写选品理由' }}</p><div class="development-actions">
          <RouterLink
            class="text-link"
            :to="`/selection/candidates/${detail.item.source.candidateId}`"
          >
            查看来源候选与证据
          </RouterLink><a
            :href="detail.item.source.sourceUrl"
            target="_blank"
            rel="noreferrer"
          >Amazon 来源</a>
        </div><p
          v-if="detail.item.voidReason"
          class="data-state"
        >
          作废原因：{{ detail.item.voidReason }}
        </p>
      </section>
      <p
        v-if="!writable"
        class="data-state"
      >
        当前账号可查看开发资料；维护由管理员或产品辅料人员完成。
      </p>
      <form
        class="development-editor"
        @submit.prevent="save"
      >
        <fieldset :disabled="!editable || saving">
          <section class="panel">
            <h2>基本信息</h2><div class="development-fields">
              <label class="development-wide">需求名称<input
                v-model="form.name"
                required
                maxlength="300"
              ></label>
              <label>开发员<input
                v-model="form.developer"
                maxlength="100"
                placeholder="开始开发前必填"
              ></label><label>采购员<input
                v-model="form.buyer"
                maxlength="100"
              ></label>
              <label>推荐星级<select v-model.number="form.stars"><option :value="0">未评定</option><option
                v-for="n in 5"
                :key="n"
                :value="n"
              >{{ n }} 星</option></select></label>
              <label>开发开始日期<input
                v-model="form.startDate"
                type="date"
                min="2000-01-01"
                max="9999-12-31"
              ></label><label>开发结束日期<input
                v-model="form.endDate"
                type="date"
                min="2000-01-01"
                max="9999-12-31"
              ></label><label>预计开售日期<input
                v-model="form.launchDate"
                type="date"
                min="2000-01-01"
                max="9999-12-31"
              ></label>
            </div>
          </section>
          <section class="panel">
            <h2>商品方案</h2><p class="development-hint">
              计划 SKU 仅用于开发方案，不会自动建立自有商品；数值留空表示未知。
            </p><div class="development-fields">
              <label class="development-wide">计划品名<input
                v-model="form.productName"
                maxlength="300"
              ></label><label>品牌<input
                v-model="form.brand"
                maxlength="100"
              ></label><label>类目<input
                v-model="form.category"
                maxlength="100"
              ></label><label>计划 SKU<input
                v-model="form.plannedSku"
                maxlength="100"
              ></label><label>材质<input
                v-model="form.material"
                maxlength="200"
              ></label>
              <label
                v-for="field in numericFields"
                :key="field.key"
              >{{ field.label }}<input
                v-model.number="form[field.key]"
                type="number"
                min="0"
                max="9999999999.9999"
                step="0.0001"
              ></label>
            </div>
          </section>
          <section class="panel">
            <h2>供货、打样与研究</h2><div class="development-fields">
              <label
                v-for="field in noteFields"
                :key="field.key"
                class="development-wide"
              >{{ field.label }}<textarea
                v-model="form[field.key]"
                maxlength="3000"
                rows="3"
              /></label>
            </div>
          </section>
          <section class="panel">
            <h2>开发成果</h2><p class="development-hint">
              完成前需填写打样 / 验收结论，并关联已启用产品。不会自动复制竞品或修改产品档案。
            </p>
            <label v-if="editable">关联产品<select
              v-model="form.linkedProductId"
              aria-label="关联产品"
            ><option :value="null">尚未关联</option><option
              v-for="product in products"
              :key="product.id"
              :value="product.id"
            >{{ product.productCode }} · {{ product.productName }}</option></select></label>
            <p v-if="detail.product">
              当前关联：<RouterLink
                class="text-link"
                :to="`/products/${detail.product.id}/variants`"
              >
                {{ detail.product.productCode }} · {{ detail.product.name }}
              </RouterLink>{{ detail.product.active ? '' : '（已停用）' }}
            </p>
            <p
              v-if="productError"
              role="alert"
            >
              {{ productError }} <button
                type="button"
                @click="loadProducts"
              >
                重试加载产品
              </button>
            </p><RouterLink
              v-if="editable"
              class="text-link"
              to="/products"
            >
              前往产品管理建档
            </RouterLink>
          </section>
        </fieldset>
        <footer
          v-if="editable"
          class="panel development-actions"
        >
          <button
            class="primary-link"
            type="submit"
            :disabled="saving || !dirty"
          >
            {{ saving ? '保存中…' : '保存资料' }}
          </button><span
            v-if="dirty"
            class="development-hint"
          >资料已修改，请先保存再流转状态。</span><button
            v-if="detail.item.status === 'DRAFT'"
            type="button"
            :disabled="saving || dirty"
            @click="transition('IN_PROGRESS')"
          >
            开始开发
          </button><button
            v-if="detail.item.status === 'IN_PROGRESS'"
            type="button"
            :disabled="saving || dirty"
            @click="transition('COMPLETED')"
          >
            完成开发
          </button><button
            type="button"
            :disabled="saving || dirty"
            @click="showVoid = true; voidReason = ''"
          >
            作废需求
          </button>
        </footer>
      </form>
      <section class="panel">
        <h2>操作历史</h2><p
          v-if="!detail.history.length"
          class="development-hint"
        >
          历史已通过候选由迁移补建草稿；原评审记录在来源候选中查看。
        </p><ol class="development-history">
          <li
            v-for="event in detail.history"
            :key="event.id"
          >
            <strong>{{ actions[event.action] || event.action }}</strong><span>{{ event.actorDisplayName || event.actorLoginName }} · {{ new Date(event.occurredAt).toLocaleString() }}</span>
          </li>
        </ol>
      </section>
    </template>
    <div
      v-if="showVoid"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="void-title"
    >
      <form
        class="panel operation-form operation-dialog__card operation-dialog__card--compact"
        @submit.prevent="transition('VOID')"
      >
        <h2 id="void-title">
          作废新品需求
        </h2><p class="form-wide">
          作废后只读，不会删除来源候选或修改已关联产品。
        </p><p
          v-if="error"
          class="form-wide error-message"
          role="alert"
        >
          {{ error }}
        </p><label class="form-wide">作废原因<textarea
          v-model="voidReason"
          required
          maxlength="500"
          :disabled="saving"
        /></label><div class="operation-form__actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="saving"
            @click="showVoid = false"
          >
            返回
          </button><button
            type="submit"
            :disabled="saving || !voidReason.trim()"
          >
            确认作废
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.development-page .panel { min-height: 0; }
.development-header,.development-section-heading,.development-actions,.development-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.development-header h1 { margin: 4px 0 8px; }.development-header p { margin: 0; color: var(--muted); }
.development-actions { justify-content: flex-start; }.development-tabs { display: flex; gap: 20px; border-bottom: 1px solid var(--line); padding: 0 16px; }
.development-tabs button { padding: 12px 6px; border: 0; border-bottom: 3px solid transparent; background: transparent; color: var(--muted); cursor: pointer; font-weight: 700; }
.development-tabs button[aria-pressed="true"] { color: var(--accent); border-bottom-color: var(--accent); }
.development-filter { grid-template-columns: 1fr auto auto; }.development-page td small { display: block; margin-top: 5px; color: var(--muted); }
.development-status { padding: 5px 10px; border-radius: 6px; background: #eef0f4; font-size: 13px; }.development-status[data-status="IN_PROGRESS"] { background: #fff3df; color: #9a5700; }.development-status[data-status="COMPLETED"] { background: #eaf7ef; color: #166534; }
.development-pagination { justify-content: flex-end; padding: 16px; border-top: 1px solid var(--line); }
.development-page button:not(.primary-link),.development-pagination select { cursor: pointer; border-radius: 6px; }.development-actions button:not(.primary-link),.development-pagination button { padding: 9px 14px; border: 1px solid var(--line); background: #f4f5f7; color: var(--ink); }
.development-page button:disabled { opacity: .5; cursor: not-allowed; }.development-editor,.development-editor fieldset { display: grid; gap: 20px; }.development-editor fieldset { margin: 0; padding: 0; border: 0; min-width: 0; }
.development-fields { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 18px; }.development-wide { grid-column: 1/-1; }
.development-editor label { display: grid; gap: 7px; font-size: 13px; font-weight: 600; }.development-editor input,.development-editor select,.development-editor textarea { width: 100%; min-width: 0; min-height: 40px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 7px; color: var(--ink); background: white; font: inherit; }.development-editor textarea { resize: vertical; line-height: 1.6; }
.development-editor :disabled { background: #f8f9fb; }.development-hint,.development-source p { color: var(--muted); line-height: 1.7; }.development-source h2 { margin: 0; }
.development-history { display: grid; gap: 16px; padding-left: 22px; }.development-history span { display: block; color: var(--muted); margin-top: 4px; }
@media(max-width:760px) { .development-fields { grid-template-columns: 1fr; }.development-tabs { gap: 10px; overflow-x: auto; } }
</style>
