<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import CustomsFields from './CustomsFields.vue'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { customersApi, type CustomsHistoryItem } from '@/api/customers'
import { profileElements, profileFields, serializeElements, type CustomsProfile } from '@/api/customs'
import { beijingDateTime } from '@/utils/dateTime'
const model = defineModel<CustomsProfile>({ required: true })
const props = withDefaults(defineProps<{ disabled?: boolean; active?: boolean; excludeId?: string }>(), { disabled: false, active: true, excludeId: '' })
const rows = ref<CustomsHistoryItem[]>([]), total = ref(0), page = ref(1), pageSize = ref(20)
const loading = ref(false), error = ref(''), notice = ref(''), open = ref(false), automatic = ref(false), code = ref('')
const chosenId = ref(''), checked = ref(false)
const draft = ref({ nameZh: '', measurementUnit: '', elements: {} as Record<string, string> })
let sequence = 0, timer: ReturnType<typeof setTimeout> | undefined
const enabled = computed(() => props.active && !props.disabled)
const schemaReady = computed(() => code.value === model.value.chinaCode && code.value === model.value.schemaCode && !!model.value.elementFields?.length)
const fields = computed(() => profileFields(model.value))
const chosen = computed(() => rows.value.find(item => item.id === chosenId.value))
const unmapped = computed(() => {
  if (!chosen.value) return []
  const allowed = new Set(fields.value.map(([key]) => key)), values = profileElements(chosen.value.customs)
  return profileFields(chosen.value.customs).filter(([key]) => !allowed.has(key) && values[key]?.trim()).map(([key, label]) => `${label}：${values[key]}`)
})
function selectSource() {
  checked.value = false
  if (!chosen.value) return
  const profile = chosen.value.customs, values = profileElements(profile)
  draft.value = { nameZh: profile.nameZh || '', measurementUnit: profile.measurementUnit || model.value.measurementUnit || '', elements: Object.fromEntries(fields.value.map(([key]) => [key, values[key] || ''])) }
}
function close() { open.value = false; automatic.value = false; checked.value = false }
function invalidate() {
  sequence++; clearTimeout(timer); loading.value = false; close(); rows.value = []; chosenId.value = ''; notice.value = ''; error.value = ''; code.value = ''
}
async function search(target = model.value.chinaCode, requestedPage = 1) {
  if (!enabled.value || !/^[0-9]{10}$/.test(target)) return
  const request = ++sequence
  code.value = target; page.value = requestedPage; loading.value = true; error.value = ''; notice.value = ''; checked.value = false
  try {
    const result = await customersApi.customsHistory(target, props.excludeId, requestedPage - 1, pageSize.value)
    if (request !== sequence || !enabled.value || model.value.chinaCode !== target) return
    rows.value = result.items; total.value = result.totalCount; page.value = requestedPage
    chosenId.value = rows.value[0]?.id || ''
    if (!rows.value.length) notice.value = '没有找到相同 HS 的历史资料，可继续手动填写。'
    if (schemaReady.value && rows.value.length) selectSource()
  } catch (e) {
    if (request === sequence) { rows.value = []; chosenId.value = ''; error.value = e instanceof Error ? e.message : '历史资料查询失败，请重试' }
  } finally { if (request === sequence) loading.value = false }
}
function entered(value: string) {
  invalidate()
  if (!enabled.value || !/^[0-9]{10}$/.test(value)) return
  automatic.value = true
  timer = setTimeout(() => void search(value), 350)
}
function manualSearch() { automatic.value = true; void search() }
watch([schemaReady, rows, loading], () => {
  if (automatic.value && schemaReady.value && rows.value.length && !loading.value && enabled.value) {
    selectSource(); open.value = true; automatic.value = false
  }
})
watch(() => model.value.chinaCode, value => { if (code.value && value !== code.value) invalidate() })
watch([enabled, () => props.excludeId], () => invalidate())
onBeforeUnmount(invalidate)
function apply(event: Event) {
  if (!enabled.value || loading.value || !schemaReady.value || !chosen.value || !checked.value || !open.value) return
  const form = event.target as HTMLFormElement
  if (!form.reportValidity()) return
  const elements = Object.fromEntries(fields.value.map(([key]) => [key, draft.value.elements[key] || '']))
  model.value = { ...model.value, nameZh: draft.value.nameZh, measurementUnit: draft.value.measurementUnit, elements,
    declarationElements: serializeElements(elements, fields.value), state: 'DRAFT' }
  close(); notice.value = '已带入核对后的历史资料，保存客户商品后生效。'
}
</script>
<template>
  <div class="customer-customs-fields">
    <CustomsFields
      v-model="model"
      :disabled="disabled"
      @hs-input="entered"
    />
    <div
      class="customs-history-status"
      aria-live="polite"
    >
      <button
        type="button"
        class="text-action"
        :disabled="!enabled || loading || !/^[0-9]{10}$/.test(model.chinaCode || '')"
        @click="manualSearch"
      >
        {{ loading ? '正在查找历史资料…' : '查看同 HS 历史资料' }}
      </button>
      <span
        v-if="error"
        role="alert"
        class="error"
      >{{ error }}</span>
      <span v-else-if="rows.length && !schemaReady">请先完成 HS 字段查询，再核对历史资料。</span>
      <span v-else>{{ notice }}</span>
    </div>
    <!-- Keep teleported controls inside the shared #app theme scope. -->
    <Teleport to="#app">
      <AccessDialog
        v-if="open && enabled"
        title="核对历史报关资料"
        dialog-class="hs-history-dialog"
        @close="close"
      >
        <div class="history-context">
          <span class="history-hs">HS {{ code }}</span>
          <span>核对下方资料，可直接修改</span>
        </div>
        <section
          class="history-source"
          aria-label="参考来源"
        >
          <div class="history-source-heading">
            <span class="history-source-caption">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                aria-hidden="true"
              ><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" /><path d="M14 3v5h5M8 12h8M8 16h5" /></svg>
              参考来源 <span
                v-if="total > 1"
                class="history-source-count"
              >{{ total }} 条可选</span>
            </span>
            <span class="history-source-readonly">仅引用，不修改来源</span>
          </div>
          <div class="history-source-title">
            <div
              v-if="total > 1"
              class="history-source-picker"
            >
              <select
                v-model="chosenId"
                v-single-select
                :disabled="loading"
                aria-label="历史资料来源"
                @change="selectSource"
              >
                <option
                  v-for="item in rows"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.productName }} · {{ item.customerName }}{{ item.skuCode ? ` · ${item.skuCode}` : '' }}{{ item.status === 'DISABLED' ? '（已停用）' : '' }}
                </option>
              </select>
            </div>
            <strong v-else-if="chosen">{{ chosen.productName }}</strong>
            <span
              v-if="chosen?.status === 'DISABLED'"
              class="history-source-disabled"
            >已停用</span>
          </div>
          <div
            v-if="chosen"
            class="history-source-meta"
          >
            <div class="history-source-identifiers">
              <span><span class="history-source-key">客户</span>{{ chosen.customerName }}</span>
              <span v-if="chosen.skuCode"><span class="history-source-key">SKU</span><span class="history-source-sku">{{ chosen.skuCode }}</span></span>
            </div>
            <time :datetime="chosen.updatedAt">{{ beijingDateTime(chosen.updatedAt).slice(0, 16) }} 更新</time>
          </div>
        </section>
        <p
          v-if="error"
          role="alert"
          class="error"
        >
          {{ error }} <button
            type="button"
            @click="search(code, page)"
          >
            重试
          </button>
        </p>
        <ListPagination
          v-if="total > pageSize"
          :total="total"
          :page="page"
          :page-size="pageSize"
          :sizes="[20]"
          :disabled="loading"
          @update:page="search(code, $event)"
        />
        <form
          id="customs-history-review"
          class="history-review"
          @submit.prevent.stop="apply"
          @input="checked = false"
        >
          <fieldset :disabled="loading || !chosen || !schemaReady">
            <label><span class="history-field-label">品名</span><input
              v-model="draft.nameZh"
              maxlength="200"
            ></label>
            <label><span class="history-field-label">计量单位</span><input
              v-model="draft.measurementUnit"
              maxlength="100"
            ></label>
            <label
              v-for="[key, label] in fields"
              :key="key"
              :class="{ 'history-wide': key === 'other' }"
            ><span class="history-field-label">{{ label }}</span><textarea
              v-if="key === 'other'"
              v-model="draft.elements[key]"
              rows="2"
              maxlength="8000"
            /><input
              v-else
              v-model="draft.elements[key]"
              maxlength="1000"
            ></label>
          </fieldset>
        </form>
        <details
          v-if="unmapped.length"
          class="history-unmapped"
        >
          <summary>另有 {{ unmapped.length }} 项旧字段未自动匹配，请核对</summary><p
            v-for="item in unmapped"
            :key="item"
          >
            {{ item }}
          </p>
        </details>
        <p class="history-warning">
          仅替换当前商品的品名、申报要素和单位，保存后生效。
        </p>
        <template #footer>
          <label class="history-confirm"><input
            v-model="checked"
            type="checkbox"
            :disabled="loading || !chosen || !schemaReady"
          >已核对，可带入</label>
          <button
            type="button"
            @click="close"
          >
            暂不带入
          </button>
          <button
            type="submit"
            form="customs-history-review"
            class="ui-button-primary"
            :disabled="!checked || loading || !chosen || !schemaReady"
          >
            确认带入
          </button>
        </template>
      </AccessDialog>
    </Teleport>
  </div>
</template>
<style scoped>
.customer-customs-fields { min-width:0; }
.customs-history-status { display:flex; align-items:center; flex-wrap:wrap; gap:6px; margin-top:6px; color:var(--muted); font-size:12px; }
.history-context { display:flex; flex-wrap:wrap; align-items:center; gap:8px; margin-bottom:10px; color:var(--muted); font-size:12px; }
.history-hs { padding:3px 7px; border:1px solid #dce7f4; border-radius:4px; background:#f1f6fc; color:#32649b; font-variant-numeric:tabular-nums; font-weight:500; }
.history-source { min-width:0; padding:9px 11px; margin-bottom:12px; border:1px solid #e2e8f0; border-radius:6px; background:#f8fafc; }
.history-source-heading { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:4px 12px; margin-bottom:6px; font-size:11px; line-height:1.5; }
.history-source-caption { display:flex; align-items:center; gap:5px; color:#596779; font-weight:500; }
.history-source-caption svg { width:14px; height:14px; flex:none; color:#6882a0; }
.history-source-count { margin-left:3px; font-weight:400; color:var(--muted); }
.history-source-readonly { color:#527568; }
.history-source-title { display:flex; align-items:center; gap:8px; min-width:0; }
.history-source-title strong { font-size:13px; font-weight:600; line-height:1.6; overflow-wrap:anywhere; }
.history-source-picker { flex:1; min-width:0; }
.history-source-picker :deep(.single-select-trigger) { width:100%; min-width:0; height:32px; min-height:32px; border-color:#d9e2ed; background:#fff; font-size:13px; font-weight:500; }
.history-source-disabled { flex:none; padding:1px 5px; border-radius:3px; background:#fff3df; color:#936126; font-size:11px; }
.history-source-meta { display:flex; justify-content:space-between; align-items:baseline; flex-wrap:wrap; gap:5px 12px; margin-top:6px; font-size:12px; line-height:1.5; color:#596779; }
.history-source-identifiers { display:flex; flex-wrap:wrap; gap:3px 14px; min-width:0; }
.history-source-identifiers>span { overflow-wrap:anywhere; }
.history-source-key { margin-right:5px; color:var(--muted); font-size:11px; }
.history-source-sku { font-variant-numeric:tabular-nums; }
.history-source-meta time { color:var(--muted); font-size:11px; }
.history-review fieldset { margin:0; padding:0; border:0; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px 14px; }
.history-review label { display:grid; grid-template-columns:82px minmax(0,1fr); align-items:center; gap:6px; min-width:0; font-size:13px; }
.history-field-label { line-height:1.45; color:#596779; overflow-wrap:anywhere; }
.history-review .history-wide { grid-column:1/-1; align-items:start; }.history-wide .history-field-label { padding-top:8px; }
.history-review input,.history-review textarea { width:100%; min-width:0; box-sizing:border-box; min-height:36px; padding:6px 8px; border:1px solid var(--line); border-radius:4px; color:var(--ink); background:#fff; font:inherit; }
.history-review input { height:36px; }.history-review textarea { min-height:58px; resize:vertical; line-height:1.5; }
.history-review input:focus,.history-review textarea:focus { outline:none; border-color:#8aadd7; box-shadow:0 0 0 2px #2864c50c; }
.history-warning { margin:10px 0 0; color:var(--muted); font-size:12px; line-height:1.5; }
.history-confirm { display:flex; align-items:center; gap:6px; min-height:32px; margin-right:auto; color:#596779; font-size:12px; cursor:pointer; }
.history-confirm input { width:16px; height:16px; margin:0; accent-color:#21834a; }
.history-unmapped { margin:8px 0 0; color:#8c682c; font-size:12px; }.history-unmapped summary { cursor:pointer; min-height:24px; }.history-unmapped p { white-space:pre-wrap; overflow-wrap:anywhere; }
@media(max-width:600px) { .history-review fieldset { grid-template-columns:minmax(0,1fr); }.history-review label { grid-template-columns:88px minmax(0,1fr); }.history-source-meta { display:grid; } }
@media(max-width:400px) { .history-confirm { flex-basis:100%; } }
</style>
<style>
.access-dialog.hs-history-dialog { width:min(640px,calc(100vw - 24px)); height:fit-content; max-height:calc(100dvh - 32px); }
.access-dialog.hs-history-dialog>.access-dialog-body { padding:10px 14px; }
.access-dialog.hs-history-dialog>header { padding:9px 14px; }
.access-dialog.hs-history-dialog>footer.ui-dialog-actions { gap:6px; padding:8px 14px; }
.access-dialog.hs-history-dialog .list-pagination { margin:0 0 8px; padding:4px 0; border:0; background:transparent; }
</style>
