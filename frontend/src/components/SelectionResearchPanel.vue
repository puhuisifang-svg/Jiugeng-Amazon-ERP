<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { researchApi, type Research, type ResearchView, type EstimateInput } from '@/api/selectionResearch'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
const props = defineProps<{ candidateId: string; writable: boolean; status: string }>()
const emit = defineEmits<{ changed: [] }>()
const data = ref<ResearchView>(), error = ref(''), busy = ref(false), mode = ref(''), unsaved = ref(false)
const dialog = ref<HTMLDialogElement>(), savedDraft = ref('')
const names = ['需求证据（25%）', '差异化（25%）', '利润空间（20%）', '供应可行性（20%）', '风险可控性（10%）']
const initial = (): Research => ({ customer: '', differentiation: '', supply: '', riskStatus: 'PENDING', riskReason: '', criteria: names.map(() => ({ score: null, reason: '' })), estimateId: '', conclusion: '' })
const research = ref<Research>(initial())
const estimate = ref<EstimateInput>({ requestId: '', name: '', price: null, currency: 'USD', costs: [], commissionRate: null, advertisingRate: null, lossRate: null, rateDate: '', basis: '' })
const editable = computed(() => props.writable && !['APPROVED', 'REJECTED'].includes(props.status))
const dirty = computed(() => !!mode.value && JSON.stringify(mode.value === 'research' ? research.value : estimate.value) !== savedDraft.value)
const money = (v: number | null | undefined) => v == null ? '不可求解' : Number(v).toFixed(2)
const message = (e: unknown) => e instanceof Error ? e.message : '保存失败，请保留输入后重试'
async function load() { try { data.value = await researchApi.get(props.candidateId) } catch (e) { error.value = message(e) } }
async function open(kind: string) {
  mode.value = kind; error.value = ''
  if (kind === 'research') research.value = { ...initial(), ...JSON.parse(JSON.stringify(data.value?.research ?? {})), version: data.value?.version ?? 0 }
  else estimate.value = { version: data.value?.version ?? 0, requestId: crypto.randomUUID(), name: '', price: null, currency: 'USD', costs: ['采购', '包装', '头程', '履约', '其他'].map(name => ({ name, amount: null, currency: 'USD', exchangeRate: 1, basis: '' })), commissionRate: null, advertisingRate: null, lossRate: null, rateDate: new Date().toISOString().slice(0, 10), basis: '' }
  savedDraft.value = JSON.stringify(kind === 'research' ? research.value : estimate.value)
  await nextTick(); dialog.value?.showModal()
}
function close() { if (busy.value) return; if (dirty.value) unsaved.value = true; else discard() }
function discard() { unsaved.value = false; dialog.value?.close(); mode.value = ''; error.value = '' }
async function save() {
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'research') await researchApi.save(props.candidateId, research.value)
    else await researchApi.estimate(props.candidateId, estimate.value)
    busy.value = false; discard(); await load(); emit('changed')
  } catch (e) { error.value = message(e) } finally { busy.value = false }
}
function leave(e: BeforeUnloadEvent) { if (dirty.value) { e.preventDefault(); e.returnValue = '' } }
onBeforeRouteLeave(() => { if (busy.value || dirty.value) { unsaved.value = dirty.value; return false } return true })
onMounted(() => { void load(); window.addEventListener('beforeunload', leave) })
onBeforeUnmount(() => window.removeEventListener('beforeunload', leave))
</script>
<template>
  <article class="panel research-panel">
    <div class="card-heading">
      <h2>研究与利润试算</h2><span>评分规则 v1 · {{ data?.weightedScore ?? '未完成' }} / 100</span>
    </div>
    <p>美国站 · 不含税 · 每件贡献利润（非财务净利）。费用需有依据，未知费用不能按零处理。</p>
    <p v-if="!editable">
      当前评估只读；已通过或淘汰的核心评估被冻结。
    </p>
    <div
      v-if="editable && data"
      class="inline-actions"
    >
      <button @click="open('research')">
        编辑研究与评分
      </button><button @click="open('estimate')">
        新增试算方案
      </button>
    </div>
    <p
      v-if="error && !mode"
      role="alert"
    >
      {{ error }}
    </p>
    <dl v-if="data">
      <dt>目标客户 / 场景</dt><dd>{{ data.research.customer || '待完善' }}</dd><dt>差异化与供应</dt><dd>{{ data.research.differentiation || '待完善' }} / {{ data.research.supply || '待完善' }}</dd><dt>风险结论</dt><dd>{{ ({ PENDING: '待核查', ACCEPTABLE: '可接受', BLOCKED: '阻断' } as Record<string, string>)[data.research.riskStatus ?? 'PENDING'] }} · {{ data.research.riskReason || '待完善' }}</dd><dt>审核结论</dt><dd>{{ data.research.conclusion || '待完善' }}</dd>
    </dl>
    <p
      v-for="(criterion, index) in data?.research.criteria ?? []"
      :key="index"
    >
      {{ names[index] }}：{{ criterion.score ?? '未评分' }} / 5 · {{ criterion.reason || '待补充依据' }}
    </p>
    <div
      v-for="item in data?.estimates ?? []"
      :key="item.id"
      class="estimate-card"
    >
      <strong>{{ item.input.name }} · 版本 {{ item.revision }} {{ data?.research.estimateId === item.id ? '（本次评审）' : '' }}</strong>
      <p>售价 ${{ money(item.input.price) }} · 单位费用 ${{ money(item.result.unitCost) }} · 利润 ${{ money(item.result.profit) }} · 利润率 {{ money(item.result.margin * 100) }}% · 保本售价 {{ money(item.result.breakEven) }}</p>
      <p>售价降低10%：${{ money(item.result.lowPriceProfit) }}；成本增加10%：${{ money(item.result.highCostProfit) }}。{{ item.result.profit < 0 ? '当前方案为负利润，请审慎评估。' : '' }}</p>
      <details>
        <summary>费用与来源依据 · {{ item.createdAt }}</summary><p>{{ item.input.basis }} · 汇率日期 {{ item.input.rateDate }}</p><p
          v-for="cost in item.input.costs"
          :key="cost.name"
        >
          {{ cost.name }}：{{ cost.amount }} {{ cost.currency }} × {{ cost.exchangeRate }} USD/{{ cost.currency }}；{{ cost.basis }}
        </p>
      </details>
    </div>
    <p v-if="data && !data.estimates.length">
      尚无试算方案。保存后可在研究与评分中指定评审版本。
    </p>
    <dialog
      v-if="mode"
      ref="dialog"
      class="research-dialog"
      aria-label="选品评估编辑"
      @cancel.prevent="close"
    >
      <form @submit.prevent="save">
        <header><h2>{{ mode === 'research' ? '研究与评分' : '利润试算新版本' }}</h2><span>保存失败保留输入</span></header>
        <div class="research-body">
          <p
            v-if="error"
            role="alert"
            class="data-state--error"
          >
            {{ error }}
          </p>
          <div
            v-if="mode === 'research'"
            class="research-grid"
          >
            <label>目标客户 / 场景<textarea
              v-model="research.customer"
              maxlength="2000"
            /></label>
            <label>差异化方案<textarea
              v-model="research.differentiation"
              maxlength="2000"
            /></label>
            <label>供货可行性<textarea
              v-model="research.supply"
              maxlength="2000"
            /></label>
            <label>风险状态<select v-model="research.riskStatus"><option value="PENDING">待核查</option><option value="ACCEPTABLE">可接受</option><option value="BLOCKED">阻断</option></select></label>
            <label>风险核查依据<textarea
              v-model="research.riskReason"
              maxlength="2000"
            /></label>
            <label>本次评审试算<select v-model="research.estimateId"><option value="">未指定</option><option
              v-for="item in data?.estimates"
              :key="item.id"
              :value="item.id"
            >{{ item.input.name }} · v{{ item.revision }}</option></select></label>
            <label
              v-for="(criterion, index) in research.criteria"
              :key="index"
            >{{ names[index] }}<select v-model="criterion.score"><option :value="null">未评分</option><option
              v-for="n in [0,1,2,3,4,5]"
              :key="n"
              :value="n"
            >{{ n }}</option></select><textarea
              v-model="criterion.reason"
              :aria-label="`${names[index]}依据`"
              placeholder="评分依据或证据说明"
              maxlength="1000"
            /></label>
            <label>审核结论<textarea
              v-model="research.conclusion"
              maxlength="2000"
            /></label>
          </div>
          <div
            v-else
            class="research-grid"
          >
            <label>方案名称<input
              v-model="estimate.name"
              required
              maxlength="200"
            ></label><label>目标售价 USD<input
              v-model.number="estimate.price"
              required
              type="number"
              min="0.000001"
              step="0.000001"
            ></label>
            <fieldset
              v-for="cost in estimate.costs"
              :key="cost.name"
            >
              <legend>{{ cost.name }}（每件）</legend><label>金额<input
                v-model.number="cost.amount"
                required
                type="number"
                min="0"
                step="0.000001"
              ></label><label>币种<select v-model="cost.currency"><option>USD</option><option>CNY</option></select></label><label>1单位费用币种折合USD<input
                v-model.number="cost.exchangeRate"
                required
                type="number"
                min="0.000001"
                step="0.000001"
              ></label><label>依据（零费用也需说明）<input
                v-model="cost.basis"
                required
                maxlength="1000"
              ></label>
            </fieldset>
            <label>平台佣金率（0–1）<input
              v-model.number="estimate.commissionRate"
              required
              type="number"
              min="0"
              max="1"
              step="0.000001"
            ></label><label>广告率（0–1）<input
              v-model.number="estimate.advertisingRate"
              required
              type="number"
              min="0"
              max="1"
              step="0.000001"
            ></label><label>退货/损耗率（0–1）<input
              v-model.number="estimate.lossRate"
              required
              type="number"
              min="0"
              max="1"
              step="0.000001"
            ></label><label>汇率日期<input
              v-model="estimate.rateDate"
              required
              type="date"
            ></label><label>费率与试算依据<textarea
              v-model="estimate.basis"
              required
              maxlength="2000"
            /></label>
          </div>
        </div>
        <footer>
          <button
            type="button"
            :disabled="busy"
            class="secondary-button"
            @click="close"
          >
            取消
          </button><button
            type="submit"
            :disabled="busy"
          >
            {{ busy ? '保存中…' : mode === 'research' ? '保存研究' : '计算并保存版本' }}
          </button>
        </footer>
      </form>
    </dialog>
    <UnsavedChangesDialog
      v-if="unsaved"
      @keep="unsaved = false"
      @discard="discard"
    />
  </article>
</template>
<style scoped>
.research-panel{min-width:0}.research-panel dd{margin:4px 0 12px;overflow-wrap:anywhere}.estimate-card{padding:12px 0;border-top:1px solid #e2e8f0;overflow-wrap:anywhere}.research-dialog{width:min(920px,calc(100vw - 24px));max-height:calc(100dvh - 24px);padding:0;border:1px solid #cbd5e1;border-radius:10px}.research-dialog::backdrop{background:#0f172a80}.research-dialog form{display:flex;flex-direction:column;max-height:calc(100dvh - 28px);min-width:0}.research-dialog header,.research-dialog footer{flex-shrink:0;padding:12px 16px;display:flex;gap:12px;justify-content:space-between;align-items:center;flex-wrap:wrap;border-bottom:1px solid #e2e8f0}.research-dialog footer{justify-content:flex-end;border-top:1px solid #e2e8f0}.research-body{overflow:auto;padding:16px}.research-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.research-grid label{display:flex;flex-direction:column;gap:6px;min-width:0}.research-grid input,.research-grid select,.research-grid textarea{width:100%;box-sizing:border-box;min-height:40px}.research-grid fieldset{min-width:0;border:1px solid #e2e8f0;border-radius:6px;display:grid;gap:8px}@media(max-width:600px){.research-grid{grid-template-columns:minmax(0,1fr)}}
</style>
