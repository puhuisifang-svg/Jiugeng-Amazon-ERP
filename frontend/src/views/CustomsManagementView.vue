<script setup lang="ts">
import ListPagination from '@/components/ListPagination.vue'
const listSize = ref(20)
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, RouterLink } from 'vue-router'
import { customsApi, blankCustoms, type CustomsProfile, type CustomsRow } from '@/api/customs'
import { session } from '@/state/session'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
import CustomsFields from '@/components/CustomsFields.vue'
const rows = ref<CustomsRow[]>([]), total = ref(0), page = ref(0), query = ref(''), state = ref('')
const loading = ref(false), saving = ref(false), error = ref(''), success = ref(''), formError = ref('')
const editing = ref<CustomsRow>(), form = ref<CustomsProfile>(blankCustoms()), dialog = ref<HTMLDialogElement>(), discard = ref(false), initial = ref('')
const writable = computed(() => session.canAccess(['products.write']))
const statusLabel = (item: CustomsRow) => !item.customs ? '未维护' : item.customs.state === 'REVIEWED' ? '资料已核对' : '草稿 / 待核对'
let sequence = 0
const message = (e: unknown) => e instanceof Error ? e.message : '请求失败，请重试。'
async function load() {
  const seq = ++sequence; loading.value = true; error.value = ''
  try { const result = await customsApi.list(query.value.trim(), state.value, page.value, listSize.value); if (seq === sequence) { rows.value = result.items; total.value = result.totalCount } }
  catch (e) { if (seq === sequence) { rows.value = []; total.value = 0; error.value = message(e) } }
  finally { if (seq === sequence) loading.value = false }
}
function search() { page.value = 0; void load() }
function turn(delta: number) { page.value += delta; void load() }
async function open(row: CustomsRow) {
  editing.value = row; form.value = structuredClone(row.customs ? JSON.parse(JSON.stringify(row.customs)) : blankCustoms())
  initial.value = JSON.stringify(form.value); formError.value = ''; discard.value = false
  await nextTick(); dialog.value?.showModal()
}
function close() { dialog.value?.close(); editing.value = undefined; discard.value = false }
function requestClose() { if (saving.value) return; if (JSON.stringify(form.value) !== initial.value) discard.value = true; else close() }
async function save() {
  if (!editing.value || saving.value) return
  saving.value = true; formError.value = ''; success.value = ''
  try { await customsApi.save(editing.value.variantId, form.value); close(); success.value = '报关资料已保存，产品管理同步生效。'; await load() }
  catch (e) { formError.value = message(e) }
  finally { saving.value = false }
}
onBeforeRouteLeave(() => { if (!editing.value) return true; if (saving.value) return false; if (JSON.stringify(form.value) !== initial.value) { discard.value = true; return false }; close(); return true })
onMounted(load)
</script>
<template>
  <section class="domain-grid customs-management">
    <SuccessToast :message="success" /><SuccessToast
      :message="error"
      tone="error"
    />
    <div class="material-toolbar">
      <p>按 SKU 维护产品申报资料，与产品管理实时共用；不是逐票报关业务。</p><RouterLink to="/products">
        前往产品管理
      </RouterLink>
    </div>
    <form
      class="panel operation-form filter-panel supplier-filters"
      @submit.prevent="search"
    >
      <label>关键字<input
        v-model="query"
        maxlength="128"
        placeholder="搜索SKU、产品名称、中英文品名或商品编码"
      ></label><label>资料状态<select v-model="state"><option value="">全部</option><option value="MISSING">未维护</option><option value="DRAFT">草稿 / 待核对</option><option value="REVIEWED">资料已核对</option></select></label><button
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '查询中…' : '查询' }}
      </button>
    </form>
    <article class="panel table-panel">
      <p
        v-if="loading"
        class="data-state"
      >
        正在加载…
      </p><TableViewport v-else>
        <table class="data-table">
          <thead>
            <tr>
              <th>产品 / SKU</th><th>中文申报品名</th><th>英文申报品名</th><th>中国商品编码</th><th>美国 HTS</th><th>欧盟 CN / TARIC</th><th>英国编码</th><th>原产国 / 单位</th><th>材质 / 用途</th><th>申报要素</th><th>资料状态</th><th class="customs-actions">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.variantId"
            >
              <td>{{ row.skuName }}<small>{{ row.skuCode }}</small><small>父体：{{ row.productName }}</small><small v-if="row.skuStatus !== 'ACTIVE' || row.productStatus !== 'ACTIVE'">产品或SKU未启用</small></td><td>{{ row.customs?.nameZh || '—' }}</td><td>{{ row.customs?.nameEn || '—' }}</td><td>{{ row.customs?.chinaCode || '—' }}</td><td>{{ row.customs?.usCode || '—' }}</td><td>{{ row.customs?.euCode || '—' }}</td><td>{{ row.customs?.ukCode || '—' }}</td><td>{{ row.customs?.originCountry || '—' }} / {{ row.customs?.unit || '—' }}</td><td class="customs-long">
                {{ row.customs?.composition || '—' }}<small>{{ row.customs?.usage }}</small>
              </td><td class="customs-long">
                {{ row.customs?.declarationElements || '—' }}
              </td><td>{{ statusLabel(row) }}</td><td class="customs-actions">
                <button
                  type="button"
                  @click="open(row)"
                >
                  {{ writable ? '维护资料' : '查看资料' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </TableViewport><p
        v-if="!loading && !rows.length"
        class="data-state"
      >
        {{ error ? '加载失败，请重试。' : '暂无符合条件的SKU；新增产品后将自动出现在此列表。' }}
      </p><ListPagination
        v-model:page-size="listSize"
        :page="page + 1"
        :total="total"
        :disabled="loading"
        @update:page="turn($event - 1 - page)"
      />
    </article>
    <dialog
      v-if="editing"
      ref="dialog"
      class="customs-dialog"
      aria-label="维护报关资料"
      @cancel.prevent="discard ? discard = false : requestClose()"
    >
      <div
        v-if="discard"
        class="panel operation-form operation-dialog__card"
      >
        <h2 class="form-wide">
          放弃未保存的修改？
        </h2><button
          type="button"
          @click="discard = false"
        >
          继续填写
        </button><button
          type="button"
          class="danger-button"
          @click="close"
        >
          放弃修改
        </button>
      </div>
      <form
        v-show="!discard"
        class="panel operation-form operation-dialog__card"
        @submit.prevent="save"
      >
        <h2 class="form-wide">
          {{ editing.skuName }} · {{ editing.skuCode }}
        </h2><button
          type="button"
          class="operation-dialog__close"
          aria-label="关闭表单"
          :disabled="saving"
          @click="requestClose"
        >
          ×
        </button><CustomsFields
          v-model="form"
          :disabled="saving || !writable"
        /><p
          v-if="formError"
          role="alert"
          class="form-wide data-state--error"
        >
          {{ formError }}
        </p><div class="operation-form__actions">
          <button
            type="button"
            :disabled="saving"
            @click="requestClose"
          >
            关闭
          </button><button
            v-if="writable"
            type="submit"
            :disabled="saving"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </dialog>
  </section>
</template>
<style scoped>
.customs-management { min-width: 0; grid-template-columns: minmax(0, 1fr); }
.table-panel { min-width: 0; max-width: 100%; }.data-table { min-width: 1900px; }
.customs-actions { position: sticky; right: 0; min-width: 100px; background: white; z-index: 2; box-shadow: -5px 0 8px -5px #9aa4b5; }
thead .customs-actions { z-index: 5; background: #f8f9fb; }
.customs-long { min-width: 180px; max-width: 300px; white-space: pre-wrap; overflow-wrap: anywhere; }
small { display: block; color: #68758c; margin-top: 6px; }
.customs-dialog { padding: 0; border: 0; border-radius: 14px; width: min(900px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); overflow: auto; }
.customs-dialog::backdrop { background: #17213b66; }.customs-dialog .operation-dialog__card { width: 100%; max-height: none; margin: 0; box-sizing: border-box; }
</style>
