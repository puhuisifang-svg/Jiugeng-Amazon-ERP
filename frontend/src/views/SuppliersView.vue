<script setup lang="ts">
import { vPhone } from '@/directives/phone'
import SupplierDeleteDialog from '@/components/SupplierDeleteDialog.vue'
import SingleFileUpload from '@/components/SingleFileUpload.vue'
import { vSingleSelect } from '@/directives/singleSelect'
import { confirmAction } from '@/state/uiConfirmation'
import SuccessToast from "@/components/SuccessToast.vue"
import TableViewport from "@/components/TableViewport.vue"
import { computed, onMounted, reactive, ref } from 'vue'
import { catalogApi } from '@/api/catalog'
import { ApiError } from '@/api/client'
import { domainApi, type SupplierDto, type SupplierInput } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import { session } from '@/state/session'

type QrPreview = { kind: 'asset'; value: string } | { kind: 'url'; value: string }
const items = ref<SupplierDto[]>([]); const query = ref(''); const status = ref(''); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const editing = ref<SupplierDto | null>(null); const preview = ref<QrPreview>(); const showForm = ref(false)
const formError = ref('')
const form = reactive<SupplierInput>({ name: '', contactPerson: '', phone: '', wechatId: '', sourceUrl: '', qrImageUrl: '', qrImageId: '', remark: '', status: 'ACTIVE', categories: [] })
const writable = computed(() => session.canAccess(['suppliers.write']))
function reset() { formError.value = ''; Object.assign(form, { name: '', contactPerson: '', phone: '', wechatId: '', sourceUrl: '', qrImageUrl: '', qrImageId: '', remark: '', status: 'ACTIVE', categories: [], version: undefined }); editing.value = null; showForm.value = false }
function openCreate() { reset(); showForm.value = true; error.value = ''; success.value = '' }
function edit(item: SupplierDto) { formError.value = ''; editing.value = item; Object.assign(form, { name: item.name, contactPerson: item.contactPerson ?? '', phone: item.phone ?? '', wechatId: item.wechatId ?? '', sourceUrl: item.sourceUrl ?? '', qrImageUrl: item.qrImageUrl ?? '', qrImageId: item.qrImageId ?? '', remark: item.remark ?? '', status: item.status, categories: [...(item.categories ?? [])], version: item.version }); showForm.value = true; error.value = ''; success.value = '' }
async function load() { loading.value = true; error.value = ''; try { const result = await domainApi.suppliers({ ...(query.value ? { q: query.value } : {}), ...(status.value ? { status: status.value as 'ACTIVE' | 'DISABLED' } : {}), size: 100 }); items.value = result.items } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商加载失败。' } finally { loading.value = false } }
async function upload(file: File) { if (saving.value) return false; saving.value = true; formError.value = ''; try { form.qrImageId = (await catalogApi.uploadImage('SUPPLIER_QR', file)).id; form.qrImageUrl = '' } catch (exception) { formError.value = exception instanceof Error ? exception.message : '图片上传失败。' ; return false } finally { saving.value = false } }
function supplierInput(): SupplierInput {
  return {
    name: form.name.trim(), status: form.status, categories: [...(form.categories ?? [])],
    ...(form.contactPerson?.trim() ? { contactPerson: form.contactPerson.trim() } : {}),
    ...(form.phone?.trim() ? { phone: form.phone.trim() } : {}),
    ...(form.wechatId?.trim() ? { wechatId: form.wechatId.trim() } : {}),
    ...(form.sourceUrl?.trim() ? { sourceUrl: form.sourceUrl.trim() } : {}),
    ...(form.qrImageUrl?.trim() ? { qrImageUrl: form.qrImageUrl.trim() } : {}),
    ...(form.qrImageId ? { qrImageId: form.qrImageId } : {}),
    ...(form.remark?.trim() ? { remark: form.remark.trim() } : {}),
    ...(editing.value ? { version: editing.value.version } : {}),
  }
}
async function save() { if(!form.categories?.length){formError.value='请选择至少一个供应商分类';return} saving.value = true; formError.value = ''; success.value = ''; try { const input = supplierInput(); const result = editing.value ? await domainApi.updateSupplier(editing.value.id, input) : await domainApi.createSupplier(input); items.value = editing.value ? items.value.map(item => item.id === result.id ? result : item) : [result, ...items.value]; success.value = editing.value ? '供应商已更新。' : '供应商已创建。'; reset() } catch (exception) { formError.value = exception instanceof ApiError ? exception.message : '供应商保存失败。' } finally { saving.value = false } }
async function disable(item: SupplierDto) { if (!await confirmAction(`确认停用供应商 ${item.name}？`)) return; saving.value = true; try { const result = await domainApi.disableSupplier(item.id, item.version); items.value = items.value.map(value => value.id === item.id ? result : value); success.value = '供应商已停用。' } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商停用失败。' } finally { saving.value = false } }
const deleteTarget=ref<SupplierDto>()
function remove(item:SupplierDto){if(saving.value||!session.canAccess(['suppliers.delete']))return;deleteTarget.value=item}
async function deleted(){deleteTarget.value=undefined;success.value='供应商已删除。';await load()}
async function disabled(){deleteTarget.value=undefined;success.value='供应商已停用，历史记录保留。';await load()}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <form
      class="panel filter-panel supplier-filters"
      aria-label="供应商筛选"
      @submit.prevent="load"
    >
      <label>搜索<input
        v-model="query"
        type="search"
        placeholder="名称 / 电话 / 微信号"
      ></label><label>状态<select
        v-model="status"
        v-single-select
      ><option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><button
        type="submit"
      >
        查询
      </button>
    </form>
    <section
      class="panel material-toolbar"
      aria-label="供应商管理操作"
    >
      <div>
        <button
          v-if="writable"
          type="button"
          @click="openCreate"
        >
          新增供应商
        </button>
      </div>
      <span class="toolbar-hint">维护供应商档案、联系方式与采购来源</span>
    </section>
    <div
      v-if="writable && showForm"
      class="operation-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="supplier-dialog-title"
      @keydown.esc="reset"
    >
      <form
        class="panel operation-form operation-dialog__card"
        @submit.prevent="save"
      >
        <button
          class="operation-dialog__close"
          type="button"
          aria-label="关闭供应商操作"
          @click="reset"
        >
          ×
        </button>
        <h2 id="supplier-dialog-title">
          {{ editing ? '编辑供应商' : '新增供应商' }}
        </h2><label>供应商名称<input
          v-model="form.name"
          required
          maxlength="128"
        ></label><label>联系人<input
          v-model="form.contactPerson"
          maxlength="64"
        ></label><label>电话<input
          v-model="form.phone"
          v-phone
          maxlength="32"
        ></label><label>微信号<input
          v-model="form.wechatId"
          maxlength="100"
          autocomplete="off"
        ></label><label>1688 链接<input
          v-model="form.sourceUrl"
          type="url"
          maxlength="500"
          placeholder="https://detail.1688.com/..."
        ></label><fieldset class="supplier-category-field form-wide">
          <legend>供应商分类 <span class="required">*</span></legend>
          <label><input
            v-model="form.categories"
            type="checkbox"
            value="MATERIAL"
            :disabled="saving"
          >辅料</label>
          <label><input
            v-model="form.categories"
            type="checkbox"
            value="PRODUCT"
            :disabled="saving"
          >产品</label>
          <small class="muted">可多选，采购单按分类展示供应商</small>
        </fieldset><SingleFileUpload
          hint="图片立即上传，保存表单后关联"
          title="图片"
          accept=".png,.jpg,.jpeg,.webp"
          :max-mb="5"
          :disabled="saving"
          :upload="upload"
        /><label>状态<select
          v-model="form.status"
          v-single-select
          class="status-select"
        ><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><label class="form-wide">备注<textarea
          v-model="form.remark"
          maxlength="500"
        /></label><div
          v-if="form.qrImageId"
          class="form-wide catalog-image-editor"
        >
          <button
            type="button"
            class="image-button"
            @click="preview = { kind: 'asset', value: form.qrImageId! }"
          >
            <AuthenticatedCatalogImage
              :image-id="form.qrImageId"
              alt="供应商图片"
            />
          </button><button
            type="button"
            class="secondary-button"
            @click="form.qrImageId = undefined"
          >
            移除图片
          </button>
        </div><div
          v-else-if="form.qrImageUrl"
          class="form-wide catalog-image-editor material-image-editor"
        >
          <button
            type="button"
            class="image-button"
            @click="preview = { kind: 'url', value: form.qrImageUrl! }"
          >
            <img
              class="material-image"
              :src="form.qrImageUrl"
              alt="供应商图片"
              referrerpolicy="no-referrer"
            >
          </button><button
            type="button"
            class="secondary-button"
            @click="form.qrImageUrl = undefined"
          >
            移除图片
          </button>
        </div><div class="operation-form__actions ui-dialog-actions">
          <p
            v-if="formError"
            class="supplier-form-error data-state data-state--error"
            role="alert"
          >
            {{ formError }}
          </p>
          <button
            type="button"
            class="secondary-button"
            @click="reset"
          >
            取消
          </button><button
            :disabled="saving"
            type="submit"
          >
            {{ saving ? '保存中…' : '保存供应商' }}
          </button>
        </div>
      </form>
    </div>
    <SuccessToast :message="success" /><p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="loading"
      class="data-state"
    >
      正在加载…
    </p>
    <article
      v-else
      class="panel table-panel"
    >
      <div class="table-scroll">
        <TableViewport>
          <table
            class="data-table supplier-list-table"
            :class="{ 'ui-action-table': writable }"
          >
            <thead>
              <tr>
                <th>图片</th><th>名称</th><th>分类</th><th>联系人</th><th>电话</th><th>微信号</th><th>1688</th><th>状态</th><th>备注</th><th v-if="writable || session.canAccess(['suppliers.delete'])">
                  操作
                </th>
              </tr>
            </thead><tbody>
              <tr
                v-for="item in items"
                :key="item.id"
              >
                <td>
                  <button
                    v-if="item.qrImageId"
                    type="button"
                    class="image-button"
                    @click="preview = { kind: 'asset', value: item.qrImageId }"
                  >
                    <AuthenticatedCatalogImage
                      :image-id="item.qrImageId"
                      :alt="`${item.name} 图片`"
                    />
                  </button><button
                    v-else-if="item.qrImageUrl"
                    type="button"
                    class="image-button"
                    @click="preview = { kind: 'url', value: item.qrImageUrl }"
                  >
                    <img
                      class="material-image"
                      :src="item.qrImageUrl"
                      :alt="`${item.name} 图片`"
                      referrerpolicy="no-referrer"
                    >
                  </button><span v-else>—</span>
                </td><td>{{ item.name }}</td><td>
                  <span v-if="item.categories?.length">{{ item.categories.map(c=>c==='MATERIAL'?'辅料':'产品').join('、') }}</span><span
                    v-else
                    class="muted"
                  >待分类</span>
                </td><td>{{ item.contactPerson || '—' }}</td><td>{{ item.phone || '—' }}</td><td>{{ item.wechatId || '—' }}</td><td>
                  <a
                    v-if="item.sourceUrl"
                    :href="item.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >打开</a><span v-else>—</span>
                </td><td><span :class="['status-badge', `status-badge--${item.status.toLowerCase()}`]">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td><td
                  class="supplier-remark"
                  :title="item.remark || undefined"
                >
                  {{ item.remark || '—' }}
                </td><td v-if="writable || session.canAccess(['suppliers.delete'])">
                  <div class="table-actions supplier-row-actions">
                    <button
                      v-if="session.canAccess(['suppliers.write'])"
                      type="button"
                      :disabled="saving"
                      @click="edit(item)"
                    >
                      编辑
                    </button><button
                      v-if="session.canAccess(['suppliers.write'])"
                      type="button"
                      class="supplier-action supplier-action--disable"
                      :disabled="saving || item.status === 'DISABLED'"
                      @click="disable(item)"
                    >
                      停用
                    </button><button
                      v-if="session.canAccess(['suppliers.delete'])"
                      type="button"
                      class="supplier-action supplier-action--delete"
                      :disabled="saving"
                      @click="remove(item)"
                    >
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
      </div><p
        v-if="!items.length"
        class="data-state"
      >
        暂无供应商。
      </p>
    </article>
    <div
      v-if="preview"
      class="image-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="图片预览"
      @click.self="preview = undefined"
    >
      <div>
        <button
          type="button"
          aria-label="关闭"
          @click="preview = undefined"
        >
          ×
        </button><AuthenticatedCatalogImage
          v-if="preview.kind === 'asset'"
          class="supplier-qr-preview"
          :image-id="preview.value"
          variant="original"
          alt="供应商图片"
        /><img
          v-else
          class="supplier-qr-preview"
          :src="preview.value"
          alt="供应商图片"
          referrerpolicy="no-referrer"
        >
      </div>
    </div>
  </section>
  <SupplierDeleteDialog
    v-if="deleteTarget"
    :supplier="deleteTarget"
    @close="deleteTarget=undefined"
    @deleted="deleted"
    @disabled="disabled"
  />
</template>

<style scoped>
.operation-form__actions { flex-wrap: wrap; }
.supplier-form-error { flex: 1 1 100%; margin: 0; text-align: left; }
#app .supplier-row-actions{display:flex;flex-wrap:nowrap;align-items:center;gap:4px;min-width:0}
#app .supplier-row-actions button{display:inline-flex;align-items:center;justify-content:center;min-height:30px;padding:4px 7px;margin:0;border:1px solid #c9d9f0;background:#f1f6ff;color:#2864c5;border-radius:4px;font-size:13px;font-weight:400;white-space:nowrap;cursor:pointer}
#app .supplier-row-actions .supplier-action--disable{color:#98630b;background:#fff8eb;border-color:#ead5aa}
#app .supplier-row-actions .supplier-action--delete{color:#b42318;background:#fff7f6;border-color:#f1c5c1}
#app .supplier-row-actions button:disabled{opacity:.45;cursor:not-allowed}
#app .supplier-row-actions button:hover:not(:disabled){filter:brightness(.97)}
</style>

<style scoped>
.supplier-category-field{display:flex;align-items:center;flex-wrap:wrap;gap:16px;border:1px solid var(--line);border-radius:4px;padding:10px 12px;margin:0;min-width:0}
.supplier-category-field legend{font-size:13px;padding:0 4px}.supplier-category-field .required{color:#dc2626}
.supplier-category-field label{display:inline-flex;align-items:center;gap:6px;margin:0}
.supplier-category-field input[type=checkbox]{width:16px;height:16px;min-height:0;accent-color:var(--accent);margin:0}
.supplier-remark{min-width:140px;max-width:280px;white-space:normal;overflow-wrap:anywhere}
#app .supplier-list-table.ui-action-table > :is(thead,tbody) > tr > :last-child{width:1%;min-width:0;white-space:nowrap}
</style>
