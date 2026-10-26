<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { costsApi, type CostRow, type CostVersion, type CostInput, type CostSnapshot, type CostOperation } from '@/api/costs'
import { domainApi, type MaterialDetailDto } from '@/api/domain'
import { session } from '@/state/session'
import ListPagination from '@/components/ListPagination.vue'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import MaterialCostPriceDialog from '@/components/MaterialCostPriceDialog.vue'
import CostVersionPanel from '@/components/CostVersionPanel.vue'
import '@/styles/productCosts.css'
import { downloadLabelBlob } from '@/api/cartonLabels'
const rows = ref<CostRow[]>([]), total = ref(0), page = ref(1), size = ref(20), q = ref(''), state = ref('')
const history = ref<Awaited<ReturnType<typeof costsApi.history>>>([])
const actions: Record<string, string> = { COST_DRAFT_CREATE: '创建成本草稿', COST_DRAFT_UPDATE: '更新成本草稿', COST_PUBLISH: '成本生效', COST_DRAFT_DELETE: '删除草稿', COST_EXPORT: '导出成本明细' }
const busy = ref(false), error = ref(''), success = ref(''), target = ref<CostRow>(), versions = ref<CostVersion[]>([]), viewed = ref<CostVersion>(), compare = ref('')
const form = ref<CostInput>(), preview = ref<CostSnapshot>(), draft = ref<CostVersion>(), reason = ref(''), previewInput = ref(''), original = ref('')
const materials = ref<MaterialDetailDto[]>([]), operations = ref<CostOperation[]>([]), materialId = ref(''), operationId = ref(''), costMaterial = ref<MaterialDetailDto>()
const operationOpen = ref(false), operationName = ref(''), operationEdit = ref<CostOperation>()
const confirm = ref<'close' | 'publish' | 'delete'>(), copySku = ref(''), copyOptions = ref<CostRow[]>([])
const editorTab = ref<'materials' | 'labor'>('materials'), detailTab = ref<'cost' | 'history'>('cost')
const imagePreview = ref<{ id: string; name: string }>()
const writable = computed(() => session.canAccess(['costs.write']))
const publishable = computed(() => session.canAccess(['costs.publish', 'products.write', 'materials.write']))
const recalculated = computed(() => form.value && preview.value && JSON.stringify(form.value) === previewInput.value)
const compareVersion = computed(() => versions.value.find(v => v.id === compare.value))
const labels: Record<string, string> = { DRAFT: '草稿', EFFECTIVE: '生效', HISTORICAL: '历史' }
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const message = (e: unknown) => e instanceof Error ? e.message : '请求失败，请重试'
async function run(action: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; try { await action() } catch (e) { error.value = message(e) } finally { busy.value = false } }
async function load() { const result = await costsApi.list(q.value, state.value, page.value - 1, size.value); rows.value = result.items; total.value = result.totalCount }
function search() { page.value = 1; void run(load) }
async function open(row: CostRow) { await run(async () => { [versions.value, history.value] = await Promise.all([costsApi.versions(row.sku.id), costsApi.history(row.sku.id)]); target.value = row; viewed.value = versions.value.find(v => v.status === 'EFFECTIVE') ?? versions.value[0]; form.value = undefined; compare.value = ''; detailTab.value = 'cost' }) }
async function edit() { if (!target.value) return; await run(async () => {
  const id = target.value!.sku.id
  editorTab.value = 'materials'
  versions.value = await costsApi.versions(id); draft.value = versions.value.find(v => v.status === 'DRAFT')
  const current = versions.value.find(v => v.status === 'EFFECTIVE')
  form.value = clone(draft.value?.input ?? current?.input ?? await costsApi.initial(id)); original.value = JSON.stringify(form.value); reason.value = ''; preview.value = undefined
  const [m, o, siblings] = await Promise.all([domainApi.materials(), costsApi.operations(), costsApi.list('', '', 0, 100, target.value!.sku.productId)])
  materials.value = m.items; operations.value = o; copyOptions.value = siblings.items.filter(r => r.sku.id !== id && (r.current || r.draft)); copySku.value = ''
}) }
async function recalculate() { if (!target.value || !form.value) return; await run(async () => { preview.value = await costsApi.preview(target.value!.sku.id, form.value!); previewInput.value = JSON.stringify(form.value) }) }
async function save() { if (!recalculated.value || !target.value || !form.value || !preview.value || !reason.value.trim()) return; await run(async () => {
  draft.value = await costsApi.save(target.value!.sku.id, form.value!, preview.value!.sourceHash, draft.value?.revision ?? null, reason.value)
  original.value = JSON.stringify(form.value); viewed.value = draft.value; form.value = undefined; versions.value = await costsApi.versions(target.value!.sku.id); history.value = await costsApi.history(target.value!.sku.id); await load(); success.value = '成本草稿已保存。'
}) }
function close() { if (busy.value) return; if (form.value && JSON.stringify(form.value) !== original.value) { confirm.value = 'close'; return } target.value = undefined; form.value = undefined }
async function confirmed() { if (confirm.value === 'close') { target.value = undefined; form.value = undefined; confirm.value = undefined; return }
  const action = confirm.value, v = viewed.value; if (!v) return
  await run(async () => { if (action === 'publish') viewed.value = await costsApi.publish(v, reason.value); else { await costsApi.remove(v); viewed.value = undefined }
    confirm.value = undefined; versions.value = await costsApi.versions(target.value!.sku.id); history.value = await costsApi.history(target.value!.sku.id); await load(); success.value = action === 'publish' ? '成本已生效，关联辅料用量已同步。' : '草稿已删除。' })
}
function addMaterial() { const m = materials.value.find(m => m.id === materialId.value); if (!m || !form.value) return; if (form.value.materials.some(l => l.materialId === m.id)) return; form.value.materials.push({ materialId: m.id, quantity: 1, usageUnit: m.unit, conversion: 1, perProducts: 1, lossPercent: 0, remark: '' }); materialId.value = '' }
function addLabor() { if (!operationId.value || !form.value) return; form.value.labor.push({ operationId: operationId.value, price: 0, basis: 'PIECE', times: 1, perProducts: 1, remark: '' }); operationId.value = '' }
async function reloadBom() { if (!target.value || !form.value) return; await run(async () => { const latest = await costsApi.initial(target.value!.sku.id); form.value!.materials = latest.materials; form.value!.baseQuantity = 1; preview.value = undefined }) }
function copy() { const source = copyOptions.value.find(r => r.sku.id === copySku.value); if (source) { form.value = clone((source.draft ?? source.current)!.input); preview.value = undefined } }
async function saveOperation() { await run(async () => { await costsApi.operation(operationName.value, operationEdit.value?.enabled ?? true, operationEdit.value?.version ?? -1, operationEdit.value?.id); operations.value = await costsApi.operations(); operationName.value = ''; operationEdit.value = undefined; success.value = '工序已保存。' }) }
async function toggleOperation(o: CostOperation) { await run(async () => { await costsApi.operation(o.name, !o.enabled, o.version, o.id); operations.value = await costsApi.operations() }) }
async function exportCost(v: CostVersion) { await run(async () => { downloadLabelBlob(await costsApi.export(v.id), `产品成本-V${v.versionNumber}.csv`); success.value = '成本明细已导出。' }) }
watch([page, size], () => { void run(load) })
function beforeUnload(event: BeforeUnloadEvent) { if (form.value && JSON.stringify(form.value) !== original.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(() => { if (busy.value) return false; if (form.value && JSON.stringify(form.value) !== original.value) { confirm.value = 'close'; return false } return true })
onMounted(() => { void run(load); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>
<template>
  <section class="costs-page cost-ui">
    <SuccessToast :message="success" /><SuccessToast
      :message="error"
      tone="error"
    />
    <form
      class="cost-filters filter-panel"
      @submit.prevent="search"
    >
      <label class="cost-search"><span>关键词</span><input
        v-model="q"
        placeholder="产品品名 / SKU编码"
        aria-label="搜索产品成本"
      ></label>
      <label><span>成本状态</span><select
        v-model="state"
        aria-label="成本状态"
      ><option value="">全部状态</option><option value="MISSING">未生效</option><option value="DRAFT">有草稿</option><option value="EFFECTIVE">有生效成本</option><option value="STALE">来源变化待核算</option><option value="INCOMPLETE">成本待完善</option></select></label>
      <button
        class="cost-button cost-button--primary"
        :disabled="busy"
      >
        搜索
      </button>
      <button
        class="filter-reset-button"
        type="button"
        :disabled="busy"
        @click="q = ''; state = ''; search()"
      >
        重置
      </button>
    </form>
    <div class="cost-list-panel">
      <div class="cost-list-toolbar">
        <div><strong>SKU 标准成本</strong><span class="cost-meta">人民币 · 物料不含税 · 每件完整成品</span></div><button
          v-if="writable"
          class="cost-button"
          type="button"
          :disabled="busy"
          @click="run(async () => { operations = await costsApi.operations(); operationOpen = true })"
        >
          工序管理
        </button>
      </div>
      <TableViewport>
        <table
          class="cost-list-table"
          :aria-busy="busy"
        >
          <colgroup><col style="width:96px"><col><col style="width:142px"><col style="width:142px"><col style="width:160px"><col style="width:182px"><col style="width:116px"></colgroup>
          <thead>
            <tr>
              <th>产品图片</th><th>品名 / SKU编码</th><th class="cost-number">
                物料成本
              </th><th class="cost-number">
                人工成本
              </th><th class="cost-number">
                单位标准成本
              </th><th>版本状态</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.sku.id"
            >
              <td>
                <button
                  class="cost-image-button"
                  :disabled="!row.sku.imageId"
                  :aria-label="'查看'+row.sku.name+'图片'"
                  @click="imagePreview = { id: row.sku.imageId!, name: row.sku.name }"
                >
                  <AuthenticatedCatalogImage
                    :image-id="row.sku.imageId ?? undefined"
                    :alt="row.sku.name"
                  />
                </button>
              </td>
              <td class="cost-name">
                <strong>{{ row.sku.name }}</strong><small>{{ row.sku.code }}</small>
              </td>
              <td class="cost-number">
                {{ row.current?.snapshot.materialTotal ?? '—' }}
              </td><td class="cost-number">
                {{ row.current?.snapshot.laborTotal ?? '—' }}
              </td>
              <td class="cost-number cost-total-cell">
                <strong>{{ row.current ? '¥ ' + row.current.snapshot.total : '—' }}</strong><small v-if="row.current">/ {{ row.current.input.unit }}</small>
              </td>
              <td>
                <div class="cost-status-stack">
                  <span
                    class="cost-badge"
                    :class="row.current ? 'cost-badge--effective' : 'cost-badge--neutral'"
                  >{{ row.current ? '生效 V'+row.current.versionNumber : '未建立生效成本' }}</span><span
                    v-if="row.draft"
                    class="cost-badge cost-badge--draft"
                  >草稿 V{{ row.draft.versionNumber }}</span><small
                    v-if="row.stale"
                    class="cost-warning"
                  >● 来源变化 · 待核算</small>
                </div>
              </td>
              <td>
                <button
                  class="cost-button cost-button--link"
                  :disabled="busy"
                  @click="open(row)"
                >
                  详情 / 维护
                </button>
              </td>
            </tr><tr v-if="!rows.length">
              <td colspan="7">
                <div class="cost-empty">
                  <span
                    class="cost-empty__icon"
                    aria-hidden="true"
                  >≡</span><strong>{{ busy ? '正在加载成本数据…' : '暂无匹配的产品 SKU' }}</strong><p v-if="!busy">
                    可调整筛选条件，或先在产品管理中维护 SKU。
                  </p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport>
    </div>
    <ListPagination
      v-model:page="page"
      v-model:page-size="size"
      :total="total"
      :disabled="busy"
    />
    <div
      v-if="target"
      class="operation-dialog cost-modal"
      role="dialog"
      aria-modal="true"
      aria-label="产品成本详情"
      @keydown.esc.stop.prevent="close"
    >
      <section class="operation-dialog__card cost-sheet">
        <header class="cost-sheet__header">
          <div class="cost-identity">
            <AuthenticatedCatalogImage
              :image-id="target.sku.imageId ?? undefined"
              :alt="target.sku.name"
            /><div>
              <div class="cost-eyebrow">
                {{ form ? '成本草稿 · 编辑' : '产品成本 · 详情' }}
              </div><h2>{{ target.sku.name }}</h2><small>SKU：{{ target.sku.code }}</small>
            </div>
          </div><button
            class="cost-button"
            :disabled="busy"
            @click="close"
          >
            关闭
          </button>
        </header>
        <div class="cost-sheet__body">
          <div
            v-if="form"
            class="cost-editor"
          >
            <aside class="cost-editor__aside">
              <section class="cost-setting-card">
                <h3>核算设置</h3><p class="cost-meta">
                  一件代表一套完整成品
                </p><label>成品单位<input
                  v-model="form.unit"
                  maxlength="16"
                  :disabled="busy"
                ></label><label>核算基数（成品数）<input
                  v-model.number="form.baseQuantity"
                  type="number"
                  min="0.0001"
                  step="0.0001"
                  :disabled="busy"
                ></label><div class="cost-setting-caption">
                  人民币 CNY<span>物料不含税</span>
                </div>
              </section>
              <section class="cost-setting-card">
                <h3>辅料来源</h3><p class="cost-meta">
                  使用现有 SKU 辅料清单，成本生效后同步每件用量。
                </p><button
                  class="cost-button cost-button--full"
                  :disabled="busy"
                  @click="reloadBom"
                >
                  重新读取当前辅料用量
                </button><small>读取后，核算基数重置为 1。</small>
              </section>
              <section class="cost-setting-card">
                <h3>复用成本</h3><label>同父体 SKU<select
                  v-model="copySku"
                  aria-label="复制同父体成本"
                  :disabled="busy"
                ><option value="">选择已有成本的 SKU</option><option
                  v-for="r in copyOptions"
                  :key="r.sku.id"
                  :value="r.sku.id"
                >{{ r.sku.name }} / {{ r.sku.code }}</option></select></label><button
                  class="cost-button cost-button--full"
                  :disabled="!copySku || busy"
                  @click="copy"
                >
                  复制为草稿
                </button><small>最多列出前 100 个 SKU；复制后需重新核算。</small>
              </section>
            </aside>
            <main class="cost-editor__main">
              <div
                class="cost-tabs"
                role="tablist"
                aria-label="成本明细编辑"
              >
                <button
                  id="cost-material-tab"
                  role="tab"
                  :aria-selected="editorTab === 'materials'"
                  aria-controls="cost-material-panel"
                  @click="editorTab = 'materials'"
                >
                  物料成本 <span>{{ form.materials.length }}</span>
                </button>
                <button
                  id="cost-labor-tab"
                  role="tab"
                  :aria-selected="editorTab === 'labor'"
                  aria-controls="cost-labor-panel"
                  @click="editorTab = 'labor'"
                >
                  人工成本 <span>{{ form.labor.length }}</span>
                </button>
              </div>
              <section
                v-show="editorTab === 'materials'"
                id="cost-material-panel"
                role="tabpanel"
                aria-labelledby="cost-material-tab"
                class="cost-edit-section"
              >
                <div class="cost-section-heading">
                  <div><h3>物料明细</h3><p>选择辅料并维护用量，单价沿用辅料成本价。</p></div>
                </div>
                <div class="cost-add-bar">
                  <label><span class="sr-only">选择辅料</span><select
                    v-model="materialId"
                    aria-label="选择辅料"
                    :disabled="busy"
                  ><option value="">选择已有辅料</option><option
                    v-for="m in materials.filter(m => m.status === 'ACTIVE')"
                    :key="m.id"
                    :value="m.id"
                  >{{ m.name }} / {{ m.materialCode }}</option></select></label><button
                    class="cost-button cost-button--soft"
                    :disabled="!materialId || busy"
                    @click="addMaterial"
                  >
                    ＋ 添加辅料
                  </button>
                </div>
                <details class="cost-formula">
                  <summary>用量换算与分摊说明</summary><p>每件用量 = 用量 × 换算系数 ÷ 分摊成品数 ÷ 核算基数。通常分摊数填 1；包装箱按每箱成品数分摊。金额包含设置的损耗。</p>
                </details>
                <div class="cost-edit-table">
                  <table>
                    <colgroup><col style="width:150px"><col style="width:100px"><col style="width:95px"><col style="width:110px"><col style="width:110px"><col style="width:100px"><col style="width:180px"><col style="width:110px"></colgroup><thead><tr><th>辅料</th><th>用量</th><th>用量单位</th><th>换算系数</th><th>分摊成品数</th><th>损耗率 %</th><th>备注</th><th>操作</th></tr></thead><tbody>
                      <tr
                        v-for="(m,index) in form.materials"
                        :key="m.materialId"
                      >
                        <td><strong>{{ materials.find(x => x.id === m.materialId)?.name ?? m.materialId }}</strong><small>{{ materials.find(x => x.id === m.materialId)?.materialCode }}</small></td><td>
                          <input
                            v-model.number="m.quantity"
                            type="number"
                            step="any"
                            min="0"
                            aria-label="物料用量"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model="m.usageUnit"
                            maxlength="32"
                            aria-label="用量单位"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model.number="m.conversion"
                            type="number"
                            step="any"
                            min="0"
                            aria-label="换算系数"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model.number="m.perProducts"
                            type="number"
                            step="any"
                            min="0"
                            aria-label="分摊成品数"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model.number="m.lossPercent"
                            type="number"
                            min="0"
                            max="100"
                            step="0.0001"
                            aria-label="损耗率"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model="m.remark"
                            maxlength="500"
                            aria-label="物料备注"
                            placeholder="补充说明"
                            :disabled="busy"
                          >
                        </td><td>
                          <div class="cost-row-actions">
                            <button
                              class="cost-button cost-button--link"
                              :disabled="busy"
                              @click="costMaterial = materials.find(x => x.id === m.materialId)"
                            >
                              成本价
                            </button><button
                              class="cost-button cost-button--danger-link"
                              :disabled="busy"
                              @click="form.materials.splice(index,1)"
                            >
                              移除
                            </button>
                          </div>
                        </td>
                      </tr><tr v-if="!form.materials.length">
                        <td
                          colspan="8"
                          class="cost-empty-inline"
                        >
                          尚未添加辅料，请从上方选择已有辅料。
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <label
                  v-if="!form.materials.length"
                  class="cost-inline-reason"
                >无物料原因<input
                  v-model="form.noMaterialReason"
                  maxlength="500"
                  placeholder="如果确实无需物料，请说明原因"
                  :disabled="busy"
                ></label>
              </section>
              <section
                v-show="editorTab === 'labor'"
                id="cost-labor-panel"
                role="tabpanel"
                aria-labelledby="cost-labor-tab"
                class="cost-edit-section"
              >
                <div class="cost-section-heading">
                  <div><h3>人工明细</h3><p>按件、按箱或按批计价，工价统一使用人民币。</p></div><button
                    class="cost-button cost-button--link"
                    :disabled="busy"
                    @click="operationOpen = true"
                  >
                    工序管理
                  </button>
                </div>
                <div class="cost-add-bar">
                  <label><span class="sr-only">选择工序</span><select
                    v-model="operationId"
                    aria-label="选择工序"
                    :disabled="busy"
                  ><option value="">选择工序</option><option
                    v-for="o in operations.filter(o => o.enabled)"
                    :key="o.id"
                    :value="o.id"
                  >{{ o.name }}</option></select></label><button
                    class="cost-button cost-button--soft"
                    :disabled="!operationId || busy"
                    @click="addLabor"
                  >
                    ＋ 添加工序
                  </button>
                </div>
                <div class="cost-edit-table">
                  <table>
                    <colgroup><col style="width:150px"><col style="width:125px"><col style="width:150px"><col style="width:100px"><col style="width:125px"><col style="width:190px"><col style="width:88px"></colgroup><thead><tr><th>工序</th><th>工价 / 元</th><th>计价方式</th><th>次数</th><th>每箱成品数</th><th>备注 / 零工价原因</th><th>操作</th></tr></thead><tbody>
                      <tr
                        v-for="(l,index) in form.labor"
                        :key="index"
                      >
                        <td><strong>{{ operations.find(o => o.id === l.operationId)?.name }}</strong></td><td>
                          <input
                            v-model.number="l.price"
                            type="number"
                            min="0"
                            step="0.000001"
                            aria-label="工价"
                            :disabled="busy"
                          >
                        </td><td>
                          <select
                            v-model="l.basis"
                            aria-label="工价方式"
                            :disabled="busy"
                            @change="l.perProducts = 1"
                          >
                            <option value="PIECE">
                              每件
                            </option><option value="BOX">
                              每箱
                            </option><option value="BATCH">
                              每批（核算基数）
                            </option>
                          </select>
                        </td><td>
                          <input
                            v-model.number="l.times"
                            type="number"
                            min="0"
                            step="0.0001"
                            aria-label="工序次数"
                            :disabled="busy"
                          >
                        </td><td>
                          <input
                            v-model.number="l.perProducts"
                            :disabled="l.basis !== 'BOX' || busy"
                            type="number"
                            min="0"
                            step="0.0001"
                            aria-label="每箱成品数"
                          >
                        </td><td>
                          <input
                            v-model="l.remark"
                            maxlength="500"
                            aria-label="工序备注"
                            placeholder="零工价须填写原因"
                            :disabled="busy"
                          >
                        </td><td>
                          <button
                            class="cost-button cost-button--danger-link"
                            :disabled="busy"
                            @click="form.labor.splice(index,1)"
                          >
                            移除
                          </button>
                        </td>
                      </tr><tr v-if="!form.labor.length">
                        <td
                          colspan="7"
                          class="cost-empty-inline"
                        >
                          尚未添加工序，请选择已有工序或进入工序管理。
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <label
                  v-if="!form.labor.length"
                  class="cost-inline-reason"
                >无人工原因<input
                  v-model="form.noLaborReason"
                  maxlength="500"
                  placeholder="如果确实无需人工，请说明原因"
                  :disabled="busy"
                ></label>
              </section>
              <section class="cost-draft-summary">
                <div class="cost-section-heading">
                  <h3>核算结果</h3><span
                    class="cost-badge"
                    :class="recalculated ? 'cost-badge--effective' : 'cost-badge--warning'"
                  >{{ !preview ? '尚未核算' : recalculated ? '已核算' : '输入已变化' }}</span>
                </div>
                <div
                  v-if="preview"
                  class="cost-metrics"
                >
                  <div><span>物料成本</span><strong>¥{{ preview.materialTotal }}</strong></div><div><span>人工成本</span><strong>¥{{ preview.laborTotal }}</strong></div><div class="cost-metrics__total">
                    <span>{{ preview.errors.length ? '当前小计 · 待完善' : '单位标准成本' }}</span><strong>¥{{ preview.total }}</strong><small>/ {{ form.unit }}</small>
                  </div>
                </div>
                <p
                  v-else
                  class="cost-meta"
                >
                  填写明细后点击“重新核算”，金额由系统计算。保存草稿不会直接生效。
                </p>
                <div
                  v-if="preview && (!recalculated || preview.errors.length)"
                  class="cost-notice cost-notice--warning"
                >
                  <strong>{{ !recalculated ? '输入已变化，请重新核算。' : '以下信息需在生效前完善' }}</strong><ul v-if="preview.errors.length">
                    <li
                      v-for="issue in preview.errors"
                      :key="issue"
                    >
                      {{ issue }}
                    </li>
                  </ul>
                </div>
                <label class="cost-inline-reason">变更原因 <span class="cost-required">必填</span><input
                  v-model="reason"
                  maxlength="500"
                  placeholder="说明本次成本维护原因，便于后续追溯"
                  :disabled="busy"
                ></label>
              </section>
            </main>
          </div>
          <div
            v-else
            class="cost-details"
          >
            <div class="cost-detail-controls">
              <div
                class="cost-tabs"
                role="tablist"
                aria-label="成本详情页签"
              >
                <button
                  role="tab"
                  :aria-selected="detailTab === 'cost'"
                  @click="detailTab = 'cost'"
                >
                  成本明细
                </button><button
                  role="tab"
                  :aria-selected="detailTab === 'history'"
                  @click="detailTab = 'history'"
                >
                  变更记录 <span>{{ history.length }}</span>
                </button>
              </div><button
                v-if="writable"
                class="cost-button cost-button--primary"
                :disabled="busy"
                @click="edit"
              >
                {{ versions.some(v => v.status === 'DRAFT') ? '编辑草稿' : '新建成本草稿' }}
              </button>
            </div>
            <template v-if="detailTab === 'cost'">
              <div
                v-if="versions.length"
                class="cost-version-picker"
              >
                <label>查看版本<select
                  :value="viewed?.id"
                  @change="viewed = versions.find(v => v.id === ($event.target as HTMLSelectElement).value)"
                ><option
                  v-for="v in versions"
                  :key="v.id"
                  :value="v.id"
                >V{{ v.versionNumber }} · {{ labels[v.status] }}</option></select></label><label>对比版本<select v-model="compare"><option value="">不对比</option><option
                  v-for="v in versions.filter(v => v.id !== viewed?.id)"
                  :key="v.id"
                  :value="v.id"
                >V{{ v.versionNumber }} · {{ labels[v.status] }}</option></select></label><span class="cost-meta">展示所选版本快照，历史金额不随价格变化。</span>
              </div>
              <div
                v-if="!viewed"
                class="cost-empty cost-empty--detail"
              >
                <span
                  class="cost-empty__icon"
                  aria-hidden="true"
                >¥</span><h3>尚未建立成本档案</h3><p>从现有辅料清单开始，维护工价后核算并保存草稿。</p><div class="cost-empty__steps">
                  <span>01 维护明细</span><span>02 核算草稿</span><span>03 确认生效</span>
                </div>
              </div>
              <div
                v-else
                class="cost-comparison"
                :class="{ 'cost-comparison--two': compareVersion && compareVersion.id !== viewed.id }"
              >
                <CostVersionPanel :version="viewed" /><CostVersionPanel
                  v-if="compareVersion && compareVersion.id !== viewed.id"
                  :version="compareVersion"
                />
              </div>
            </template>
            <section
              v-else
              class="cost-history"
            >
              <h3>成本变更记录</h3><ol v-if="history.length">
                <li
                  v-for="event in history"
                  :key="event.id"
                >
                  <div><strong>{{ actions[event.action] ?? '其他操作' }}</strong><time>{{ event.occurredAt }}</time></div><p>{{ event.detail || '—' }}</p><small>操作人：{{ event.actorDisplayName }}</small>
                </li>
              </ol><div
                v-else
                class="cost-empty"
              >
                <strong>暂无变更记录</strong><p>保存、发布和导出等操作会在此记录。</p>
              </div>
            </section>
          </div>
        </div>
        <footer class="cost-sheet__footer">
          <template v-if="form">
            <p class="cost-footer-hint">
              {{ !preview ? '请先核算，再保存草稿。' : !recalculated ? '输入已变化，请重新核算。' : !reason.trim() ? '请填写变更原因。' : '保存草稿后，可由有权限的人员确认生效。' }}
            </p><div>
              <button
                class="cost-button"
                :disabled="busy"
                @click="recalculate"
              >
                重新核算
              </button><button
                class="cost-button cost-button--primary"
                :disabled="busy || !recalculated || !reason.trim()"
                @click="save"
              >
                {{ busy ? '处理中…' : '保存草稿' }}
              </button>
            </div>
          </template>
          <template v-else>
            <p class="cost-footer-hint">
              标准成本不改变客户供货价，也不计入实际库存成本。
            </p><div v-if="viewed">
              <button
                v-if="session.canAccess(['costs.export'])"
                class="cost-button"
                :disabled="busy"
                @click="exportCost(viewed)"
              >
                导出成本明细
              </button><button
                v-if="viewed.status === 'DRAFT' && writable"
                class="cost-button cost-button--danger"
                :disabled="busy"
                @click="confirm = 'delete'"
              >
                删除草稿
              </button><button
                v-if="viewed.status === 'DRAFT' && publishable"
                class="cost-button cost-button--primary"
                :disabled="busy || viewed.stale || !!viewed.snapshot.errors.length"
                @click="reason = ''; confirm = 'publish'"
              >
                确认生效
              </button>
            </div>
          </template>
        </footer>
      </section>
    </div>
    <div
      v-if="operationOpen"
      class="operation-dialog cost-modal"
      role="dialog"
      aria-modal="true"
      aria-label="工序管理"
    >
      <section class="operation-dialog__card cost-sheet cost-sheet--compact">
        <header class="cost-sheet__header">
          <div>
            <h2>工序管理</h2><p class="cost-meta">
              维护通用工序名称，具体工价在每个 SKU 中设置。
            </p>
          </div><button
            class="cost-button"
            :disabled="busy"
            @click="operationOpen = false; operationEdit = undefined; operationName = ''"
          >
            关闭
          </button>
        </header><div class="cost-sheet__body cost-compact-body">
          <form
            class="cost-operation-form"
            @submit.prevent="saveOperation"
          >
            <label>工序名称<input
              v-model="operationName"
              required
              maxlength="100"
              placeholder="例如：裁剪、包装"
              :disabled="busy"
            ></label><button
              class="cost-button cost-button--primary"
              :disabled="busy"
            >
              {{ operationEdit ? '更新名称' : '新增工序' }}
            </button>
          </form><div class="cost-detail-table">
            <table>
              <thead><tr><th>工序名称</th><th>状态</th><th>操作</th></tr></thead><tbody>
                <tr
                  v-for="o in operations"
                  :key="o.id"
                >
                  <td><strong>{{ o.name }}</strong></td><td>
                    <span
                      class="cost-badge"
                      :class="o.enabled ? 'cost-badge--effective' : 'cost-badge--neutral'"
                    >{{ o.enabled ? '启用' : '停用' }}</span>
                  </td><td>
                    <div class="cost-row-actions">
                      <button
                        class="cost-button cost-button--link"
                        :disabled="busy"
                        @click="operationEdit = o; operationName = o.name"
                      >
                        改名
                      </button><button
                        class="cost-button cost-button--link"
                        :disabled="busy"
                        @click="toggleOperation(o)"
                      >
                        {{ o.enabled ? '停用' : '启用' }}
                      </button>
                    </div>
                  </td>
                </tr><tr v-if="!operations.length">
                  <td
                    colspan="3"
                    class="cost-empty-inline"
                  >
                    暂无工序，请先新增。
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
    <MaterialCostPriceDialog
      v-if="costMaterial"
      :material="costMaterial"
      @close="costMaterial = undefined"
      @saved="costMaterial = undefined; preview = undefined; success = '成本价已保存，请重新核算。'"
    />
    <div
      v-if="confirm"
      class="operation-dialog cost-modal"
      role="dialog"
      aria-modal="true"
      aria-label="确认成本操作"
    >
      <section class="operation-dialog__card cost-sheet cost-sheet--confirm">
        <header class="cost-sheet__header">
          <h2>{{ confirm === 'close' ? '放弃未保存修改？' : confirm === 'delete' ? '删除成本草稿？' : '确认成本生效？' }}</h2>
        </header><div class="cost-sheet__body cost-compact-body">
          <p class="cost-confirm-product">
            <span>产品名</span><strong>{{ target?.sku.name }}</strong>
          </p><div
            class="cost-notice"
            :class="confirm === 'publish' ? 'cost-notice--info' : 'cost-notice--warning'"
          >
            {{ confirm === 'publish' ? '新版本将替换当前生效版本，并同步每件成品的辅料用量到现有 SKU 辅料清单。历史快照保持不变。' : confirm === 'close' ? '尚未保存的输入将被放弃，已保存的成本版本不会受影响。' : '仅删除当前草稿，历史生效成本保持不变。' }}
          </div><label
            v-if="confirm === 'publish'"
            class="cost-inline-reason"
          >生效原因<input
            v-model="reason"
            maxlength="500"
            placeholder="说明本次确认生效的原因"
            :disabled="busy"
          ></label>
        </div><footer class="cost-sheet__footer">
          <button
            class="cost-button"
            :disabled="busy"
            @click="confirm = undefined"
          >
            取消
          </button><button
            class="cost-button"
            :class="confirm === 'publish' ? 'cost-button--primary' : 'cost-button--danger'"
            :disabled="busy || (confirm === 'publish' && !reason.trim())"
            @click="confirmed"
          >
            确认
          </button>
        </footer>
      </section>
    </div>
    <div
      v-if="imagePreview"
      class="operation-dialog cost-modal cost-image-modal"
      role="dialog"
      aria-modal="true"
      aria-label="产品图片预览"
      @click.self="imagePreview = undefined"
      @keydown.esc.stop="imagePreview = undefined"
    >
      <section class="cost-image-panel">
        <header>
          <strong>{{ imagePreview.name }}</strong><button
            class="cost-button"
            @click="imagePreview = undefined"
          >
            关闭
          </button>
        </header><AuthenticatedCatalogImage
          :image-id="imagePreview.id"
          :alt="imagePreview.name"
          variant="original"
        />
      </section>
    </div>
  </section>
</template>
