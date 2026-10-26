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
import { downloadLabelBlob } from '@/api/cartonLabels'
const rows = ref<CostRow[]>([]), total = ref(0), page = ref(1), size = ref(20), q = ref(''), state = ref('')
const history = ref<Awaited<ReturnType<typeof costsApi.history>>>([])
const actions: Record<string, string> = { COST_DRAFT_CREATE: '创建成本草稿', COST_DRAFT_UPDATE: '更新成本草稿', COST_PUBLISH: '成本生效', COST_DRAFT_DELETE: '删除草稿', COST_EXPORT: '导出成本明细' }
const busy = ref(false), error = ref(''), success = ref(''), target = ref<CostRow>(), versions = ref<CostVersion[]>([]), viewed = ref<CostVersion>(), compare = ref('')
const form = ref<CostInput>(), preview = ref<CostSnapshot>(), draft = ref<CostVersion>(), reason = ref(''), previewInput = ref(''), original = ref('')
const materials = ref<MaterialDetailDto[]>([]), operations = ref<CostOperation[]>([]), materialId = ref(''), operationId = ref(''), costMaterial = ref<MaterialDetailDto>()
const operationOpen = ref(false), operationName = ref(''), operationEdit = ref<CostOperation>()
const confirm = ref<'close' | 'publish' | 'delete'>(), copySku = ref(''), copyOptions = ref<CostRow[]>([])
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
async function open(row: CostRow) { await run(async () => { [versions.value, history.value] = await Promise.all([costsApi.versions(row.sku.id), costsApi.history(row.sku.id)]); target.value = row; viewed.value = versions.value.find(v => v.status === 'EFFECTIVE') ?? versions.value[0]; form.value = undefined; compare.value = '' }) }
async function edit() { if (!target.value) return; await run(async () => {
  const id = target.value!.sku.id
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
  <section class="costs-page">
    <SuccessToast :message="success" /><SuccessToast
      :message="error"
      tone="error"
    />
    <form
      class="cost-filters"
      @submit.prevent="search"
    >
      <input
        v-model="q"
        placeholder="搜索产品品名 / SKU编码"
        aria-label="搜索产品成本"
      ><select
        v-model="state"
        aria-label="成本状态"
      >
        <option value="">
          全部状态
        </option><option value="MISSING">
          未生效
        </option><option value="DRAFT">
          有草稿
        </option><option value="EFFECTIVE">
          有生效成本
        </option><option value="STALE">
          来源变化待核算
        </option><option value="INCOMPLETE">
          成本待完善
        </option>
      </select><button :disabled="busy">
        搜索
      </button><button
        type="button"
        :disabled="busy"
        @click="q = ''; state = ''; search()"
      >
        重置
      </button><button
        v-if="writable"
        type="button"
        :disabled="busy"
        @click="run(async () => { operations = await costsApi.operations(); operationOpen = true })"
      >
        工序管理
      </button>
    </form>
    <p class="cost-note">
      人民币 · 不含税 · 每件完整成品标准成本；不作为实际库存成本，不自动改变客户报价。
    </p>
    <TableViewport>
      <table>
        <thead><tr><th>产品图片</th><th>品名 / SKU编码</th><th>物料成本</th><th>人工成本</th><th>合计 / 件</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.sku.id"
          >
            <td>
              <AuthenticatedCatalogImage
                :image-id="row.sku.imageId ?? undefined"
                :alt="row.sku.name"
              />
            </td><td class="cost-name">
              {{ row.sku.name }}<small>{{ row.sku.code }}</small>
            </td><td>{{ row.current?.snapshot.materialTotal ?? '—' }}</td><td>{{ row.current?.snapshot.laborTotal ?? '—' }}</td><td><strong>{{ row.current?.snapshot.total ?? '—' }}</strong></td><td>
              {{ row.current ? `生效 V${row.current.versionNumber}` : '未生效' }}<small v-if="row.draft">有草稿 V{{ row.draft.versionNumber }}</small><small
                v-if="row.stale"
                class="cost-warning"
              >来源已变化，待重新核算</small>
            </td><td>
              <button
                :disabled="busy"
                @click="open(row)"
              >
                详情 / 维护
              </button>
            </td>
          </tr><tr v-if="!rows.length">
            <td colspan="7">
              {{ busy ? '加载中…' : '暂无产品 SKU' }}
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      v-model:page="page"
      v-model:page-size="size"
      :total="total"
      :disabled="busy"
    />
    <div
      v-if="target"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="产品成本详情"
      @keydown.esc.stop.prevent="close"
    >
      <section class="operation-dialog__card cost-card">
        <header>
          <h2>{{ target.sku.name }} · 产品成本</h2><button
            :disabled="busy"
            @click="close"
          >
            关闭
          </button>
        </header>
        <template v-if="form">
          <div class="cost-toolbar">
            <label>成品单位<input
              v-model="form.unit"
              maxlength="16"
            ></label><label>核算基数（成品数）<input
              v-model.number="form.baseQuantity"
              type="number"
              min="0.0001"
              step="0.0001"
            ></label><button
              :disabled="busy"
              @click="reloadBom"
            >
              重新读取当前辅料用量（基数重置为1）
            </button><select
              v-model="copySku"
              aria-label="复制同父体成本"
            >
              <option value="">
                同父体 SKU（前100条）
              </option><option
                v-for="r in copyOptions"
                :key="r.sku.id"
                :value="r.sku.id"
              >
                {{ r.sku.name }} / {{ r.sku.code }}
              </option>
            </select><button
              :disabled="!copySku || busy"
              @click="copy"
            >
              复制为草稿
            </button>
          </div>
          <h3>物料成本</h3><p class="cost-note">
            每件用量 = 用量 × 换算系数 ÷ 分摊成品数 ÷ 核算基数。通常分摊数填1；包装箱按每箱成品数分摊。价格来自辅料成本价。
          </p>
          <div class="cost-toolbar">
            <select
              v-model="materialId"
              aria-label="选择辅料"
            >
              <option value="">
                选择已有辅料
              </option><option
                v-for="m in materials.filter(m => m.status === 'ACTIVE')"
                :key="m.id"
                :value="m.id"
              >
                {{ m.name }} / {{ m.materialCode }}
              </option>
            </select><button
              :disabled="!materialId || busy"
              @click="addMaterial"
            >
              添加辅料
            </button>
          </div>
          <div class="cost-table">
            <table>
              <thead><tr><th>辅料</th><th>用量</th><th>用量单位</th><th>换算至辅料单位</th><th>分摊成品数</th><th>损耗率%</th><th>备注</th><th>操作</th></tr></thead><tbody>
                <tr
                  v-for="(m, index) in form.materials"
                  :key="m.materialId"
                >
                  <td>{{ materials.find(x => x.id === m.materialId)?.name ?? m.materialId }}</td><td>
                    <input
                      v-model.number="m.quantity"
                      type="number"
                      step="any"
                      min="0"
                      aria-label="物料用量"
                    >
                  </td><td>
                    <input
                      v-model="m.usageUnit"
                      maxlength="32"
                      aria-label="用量单位"
                    >
                  </td><td>
                    <input
                      v-model.number="m.conversion"
                      type="number"
                      step="any"
                      min="0"
                      aria-label="换算系数"
                    >
                  </td><td>
                    <input
                      v-model.number="m.perProducts"
                      type="number"
                      step="any"
                      min="0"
                      aria-label="分摊成品数"
                    >
                  </td><td>
                    <input
                      v-model.number="m.lossPercent"
                      type="number"
                      min="0"
                      max="100"
                      step="0.0001"
                      aria-label="损耗率"
                    >
                  </td><td>
                    <input
                      v-model="m.remark"
                      maxlength="500"
                      aria-label="物料备注"
                    >
                  </td><td>
                    <button @click="costMaterial = materials.find(x => x.id === m.materialId)">
                      成本价
                    </button><button @click="form.materials.splice(index, 1)">
                      移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <label v-if="!form.materials.length">无物料原因<input
            v-model="form.noMaterialReason"
            maxlength="500"
          ></label>
          <h3>人工成本</h3><div class="cost-toolbar">
            <select
              v-model="operationId"
              aria-label="选择工序"
            >
              <option value="">
                选择工序
              </option><option
                v-for="o in operations.filter(o => o.enabled)"
                :key="o.id"
                :value="o.id"
              >
                {{ o.name }}
              </option>
            </select><button
              :disabled="!operationId || busy"
              @click="addLabor"
            >
              添加工序
            </button><button @click="operationOpen = true">
              工序管理
            </button>
          </div>
          <div class="cost-table">
            <table>
              <thead><tr><th>工序</th><th>工价（元）</th><th>计价方式</th><th>次数</th><th>每箱成品数</th><th>备注 / 零工价原因</th><th>操作</th></tr></thead><tbody>
                <tr
                  v-for="(l,index) in form.labor"
                  :key="index"
                >
                  <td>{{ operations.find(o => o.id === l.operationId)?.name }}</td><td>
                    <input
                      v-model.number="l.price"
                      type="number"
                      min="0"
                      step="0.000001"
                      aria-label="工价"
                    >
                  </td><td>
                    <select
                      v-model="l.basis"
                      aria-label="工价方式"
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
                    >
                  </td><td>
                    <input
                      v-model.number="l.perProducts"
                      :disabled="l.basis !== 'BOX'"
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
                    >
                  </td><td>
                    <button @click="form.labor.splice(index,1)">
                      移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <label v-if="!form.labor.length">无人工原因<input
            v-model="form.noLaborReason"
            maxlength="500"
          ></label>
          <label>变更原因<input
            v-model="reason"
            maxlength="500"
            placeholder="必填，说明本次成本维护原因"
          ></label>
          <section
            v-if="preview"
            class="cost-summary"
          >
            <strong>物料 {{ preview.materialTotal }} + 人工 {{ preview.laborTotal }} = ¥{{ preview.total }} / {{ form.unit }}</strong><p
              v-if="!recalculated"
              class="cost-warning"
            >
              输入已变化，请重新核算。
            </p><p
              v-for="issue in preview.errors"
              :key="issue"
              class="cost-warning"
            >
              {{ issue }}
            </p>
          </section>
          <footer>
            <button
              :disabled="busy"
              @click="recalculate"
            >
              重新核算
            </button><button
              :disabled="busy || !recalculated || !reason.trim()"
              @click="save"
            >
              保存草稿
            </button>
          </footer>
        </template>
        <template v-else>
          <div class="cost-toolbar">
            <button
              v-if="writable"
              :disabled="busy"
              @click="edit"
            >
              {{ versions.some(v => v.status === 'DRAFT') ? '编辑草稿' : '新建成本草稿' }}
            </button><label>版本<select
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
            >V{{ v.versionNumber }}</option></select></label>
          </div>
          <p v-if="!viewed">
            该 SKU 尚无成本版本。请新建草稿，核算后再生效。
          </p>
          <div class="cost-comparison">
            <article
              v-for="v in [viewed, compareVersion].filter((v): v is CostVersion => !!v)"
              :key="v.id"
            >
              <h3>V{{ v.versionNumber }} · {{ labels[v.status] }}</h3><p>{{ v.reason }} · {{ v.publishedAt ?? v.createdAt }}</p><p
                v-if="v.stale"
                class="cost-warning"
              >
                来源已变化。当前版本金额保持不变，请编辑草稿并重新核算。
              </p><p>人民币 / {{ v.input.unit }} · 物料不含税 · 核算基数 {{ v.input.baseQuantity }}</p><table>
                <thead><tr><th>类型 / 名称</th><th>单位成品金额</th></tr></thead><tbody>
                  <tr
                    v-for="m in v.snapshot.materials"
                    :key="m.input.materialId"
                  >
                    <td>物料 · {{ m.name }}<small>用量 {{ m.input.quantity }}{{ m.input.usageUnit }} · 换算 {{ m.input.conversion }} · 分摊 {{ m.input.perProducts }} · 损耗 {{ m.input.lossPercent }}% · 单价 {{ m.price ?? '缺价格' }}</small><small>{{ m.input.remark }}</small></td><td>{{ m.price === null ? '待完善' : m.amount }}</td>
                  </tr><tr
                    v-for="(l,i) in v.snapshot.labor"
                    :key="i"
                  >
                    <td>人工 · {{ l.name }}<small>工价 {{ l.input.price }} · {{ { PIECE: '每件', BOX: '每箱', BATCH: '每批' }[l.input.basis] }} × {{ l.input.times }} · 每箱成品数 {{ l.input.perProducts }}</small><small>{{ l.input.remark }}</small></td><td>{{ l.amount }}</td>
                  </tr>
                </tbody>
              </table><p>物料 {{ v.snapshot.materialTotal }} · 人工 {{ v.snapshot.laborTotal }}</p><strong>合计 ¥{{ v.snapshot.total }} / {{ v.input.unit }}</strong><p
                v-for="issue in v.snapshot.errors"
                :key="issue"
                class="cost-warning"
              >
                {{ issue }}
              </p>
            </article>
          </div>
          <details>
            <summary>变更记录（{{ history.length }}）</summary><ul>
              <li
                v-for="event in history"
                :key="event.id"
              >
                {{ actions[event.action] ?? event.action }} · {{ event.actorDisplayName }} · {{ event.occurredAt }}<small>{{ event.detail }}</small>
              </li>
            </ul><p v-if="!history.length">
              暂无变更记录
            </p>
          </details>
          <footer v-if="viewed">
            <button
              v-if="session.canAccess(['costs.export'])"
              :disabled="busy"
              @click="exportCost(viewed)"
            >
              导出成本明细
            </button><button
              v-if="viewed.status === 'DRAFT' && writable"
              :disabled="busy"
              @click="confirm = 'delete'"
            >
              删除草稿
            </button><button
              v-if="viewed.status === 'DRAFT' && publishable"
              :disabled="busy || viewed.stale || !!viewed.snapshot.errors.length"
              @click="reason = ''; confirm = 'publish'"
            >
              确认生效
            </button>
          </footer>
        </template>
      </section>
    </div>
    <div
      v-if="operationOpen"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="工序管理"
    >
      <section class="operation-dialog__card">
        <h2>工序管理</h2><form @submit.prevent="saveOperation">
          <label>工序名称<input
            v-model="operationName"
            required
            maxlength="100"
          ></label><button :disabled="busy">
            {{ operationEdit ? '更新名称' : '新增工序' }}
          </button>
        </form><ul>
          <li
            v-for="o in operations"
            :key="o.id"
          >
            {{ o.name }} · {{ o.enabled ? '启用' : '停用' }} <button
              :disabled="busy"
              @click="operationEdit = o; operationName = o.name"
            >
              改名
            </button><button
              :disabled="busy"
              @click="toggleOperation(o)"
            >
              {{ o.enabled ? '停用' : '启用' }}
            </button>
          </li>
        </ul><footer>
          <button
            :disabled="busy"
            @click="operationOpen = false; operationEdit = undefined; operationName = ''"
          >
            关闭
          </button>
        </footer>
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
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="确认成本操作"
    >
      <section class="operation-dialog__card">
        <h2>{{ confirm === 'close' ? '放弃未保存修改？' : confirm === 'delete' ? '删除成本草稿？' : '确认成本生效？' }}</h2><p>{{ confirm === 'publish' ? '新版本将替换当前生效版本，并同步每件成品的辅料用量到现有 SKU 辅料清单。历史快照保持不变。' : '此操作不会改变历史生效成本。' }}</p><label v-if="confirm === 'publish'">生效原因<input
          v-model="reason"
          maxlength="500"
        ></label><footer>
          <button
            :disabled="busy"
            @click="confirm = undefined"
          >
            取消
          </button><button
            :disabled="busy || (confirm === 'publish' && !reason.trim())"
            @click="confirmed"
          >
            确认
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>
<style scoped>
.costs-page { min-width:0; }
.costs-page .operation-dialog__card { background:var(--surface); padding:20px; border:1px solid var(--line); border-radius:10px; }
.costs-page :is(input, select) { min-height:32px; padding:5px 8px; border:1px solid var(--line); border-radius:4px; color:var(--ink); background:white; max-width:100%; }
.costs-page label { display:grid; gap:5px; margin:8px 0; font-size:13px; color:var(--muted); }
.costs-page button { min-height:32px; border:1px solid var(--line); border-radius:4px; background:white; color:var(--ink); padding:5px 12px; cursor:pointer; }
.costs-page button:hover:not(:disabled) { color:var(--accent); border-color:var(--accent); }.costs-page button:disabled { opacity:.5; cursor:not-allowed; }
.costs-page table { border-collapse:collapse; width:100%; font-size:13px; background:white; }
.costs-page :is(th,td) { padding:8px 12px; text-align:left; border-bottom:1px solid var(--line); vertical-align:middle; overflow-wrap:anywhere; }
.costs-page th { position:sticky; top:0; background:#f8f9fb; color:var(--muted); z-index:1; white-space:nowrap; }
.costs-page :deep(.table-viewport) > table { min-width:850px; }.costs-page :deep(.table-viewport) > table td:last-child { position:sticky; right:0; background:white; }.costs-page :deep(.table-viewport) > table th:last-child { right:0; z-index:2; }
.cost-card h2 { font-size:18px; }.cost-card h3 { font-size:15px; margin:16px 0 8px; }.cost-comparison td:first-child { width:75%; }
.cost-filters,.cost-toolbar { display:flex; gap:8px; flex-wrap:wrap; align-items:end; margin-bottom:10px; }
.cost-filters input { max-width:300px; }.cost-toolbar label { flex:1; min-width:140px; }.cost-toolbar select { max-width:280px; }
.cost-note,small { color:var(--muted); font-size:12px; }small { display:block; }.cost-warning { color:#b54708; }.cost-name { white-space:pre-line; }
.costs-page :deep(.catalog-image) { width:72px; height:72px; object-fit:contain; }.cost-card { width:min(1200px,100%); max-width:100%; min-width:0; }
.cost-card header { display:flex; justify-content:space-between; gap:12px; }.cost-card footer { position:sticky; bottom:0; background:white; padding:12px 0; display:flex; justify-content:flex-end; gap:8px; }
.cost-table { overflow:auto; max-height:40vh; }.cost-table table { min-width:1050px; }.cost-table input { min-width:80px; width:100%; }.cost-table td { padding:6px; }.cost-table td:first-child { min-width:110px; }.cost-table td:last-child { min-width:90px; }
.cost-comparison { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr)); gap:20px; }.cost-comparison article { min-width:0; }.cost-summary { padding:12px; background:#fff7ed; margin:12px 0; }
</style>
