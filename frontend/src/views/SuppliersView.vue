<script setup lang="ts">
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
const form = reactive<SupplierInput>({ name: '', contactPerson: '', phone: '', wechatId: '', sourceUrl: '', qrImageUrl: '', qrImageId: '', remark: '', status: 'ACTIVE' })
const writable = computed(() => session.canAccess(['suppliers.write']))
function reset() { Object.assign(form, { name: '', contactPerson: '', phone: '', wechatId: '', sourceUrl: '', qrImageUrl: '', qrImageId: '', remark: '', status: 'ACTIVE', version: undefined }); editing.value = null; showForm.value = false }
function openCreate() { reset(); showForm.value = true; error.value = ''; success.value = '' }
function edit(item: SupplierDto) { editing.value = item; Object.assign(form, { name: item.name, contactPerson: item.contactPerson ?? '', phone: item.phone ?? '', wechatId: item.wechatId ?? '', sourceUrl: item.sourceUrl ?? '', qrImageUrl: item.qrImageUrl ?? '', qrImageId: item.qrImageId ?? '', remark: item.remark ?? '', status: item.status, version: item.version }); showForm.value = true; error.value = ''; success.value = '' }
async function load() { loading.value = true; error.value = ''; try { const result = await domainApi.suppliers({ ...(query.value ? { q: query.value } : {}), ...(status.value ? { status: status.value as 'ACTIVE' | 'DISABLED' } : {}), size: 100 }); items.value = result.items } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商加载失败。' } finally { loading.value = false } }
async function upload(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; saving.value = true; try { form.qrImageId = (await catalogApi.uploadImage('SUPPLIER_QR', file)).id; form.qrImageUrl = '' } catch (exception) { error.value = exception instanceof Error ? exception.message : '二维码上传失败。' } finally { saving.value = false } }
function useQrUrl() { if (form.qrImageUrl?.trim()) form.qrImageId = '' }
function supplierInput(): SupplierInput {
  return {
    name: form.name.trim(), status: form.status,
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
async function save() { saving.value = true; error.value = ''; success.value = ''; try { const input = supplierInput(); const result = editing.value ? await domainApi.updateSupplier(editing.value.id, input) : await domainApi.createSupplier(input); items.value = editing.value ? items.value.map(item => item.id === result.id ? result : item) : [result, ...items.value]; success.value = editing.value ? '供应商已更新。' : '供应商已创建。'; reset() } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商保存失败。' } finally { saving.value = false } }
async function disable(item: SupplierDto) { if (!window.confirm(`确认停用供应商 ${item.name}？`)) return; saving.value = true; try { const result = await domainApi.disableSupplier(item.id, item.version); items.value = items.value.map(value => value.id === item.id ? result : value); success.value = '供应商已停用。' } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商停用失败。' } finally { saving.value = false } }
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
      @click.self="reset"
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
        ></label><label>微信二维码 URL<input
          v-model="form.qrImageUrl"
          type="url"
          maxlength="500"
          placeholder="https://example.com/wechat-qr.png"
          @input="useQrUrl"
        ></label><label>上传微信二维码<input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="upload"
        ></label><label>状态<select
          v-model="form.status"
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
              alt="供应商微信二维码"
            />
          </button><button
            type="button"
            class="secondary-button"
            @click="form.qrImageId = undefined"
          >
            移除二维码
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
              alt="供应商微信二维码"
              referrerpolicy="no-referrer"
            >
          </button><button
            type="button"
            class="secondary-button"
            @click="form.qrImageUrl = undefined"
          >
            移除二维码 URL
          </button>
        </div><div class="operation-form__actions">
          <button
            v-if="editing"
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
          <table class="data-table">
            <thead>
              <tr>
                <th>二维码</th><th>名称</th><th>联系人</th><th>电话</th><th>微信号</th><th>1688</th><th>状态</th><th v-if="writable">
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
                      :alt="`${item.name} 二维码`"
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
                      :alt="`${item.name} 二维码`"
                      referrerpolicy="no-referrer"
                    >
                  </button><span v-else>—</span>
                </td><td>{{ item.name }}</td><td>{{ item.contactPerson || '—' }}</td><td>{{ item.phone || '—' }}</td><td>{{ item.wechatId || '—' }}</td><td>
                  <a
                    v-if="item.sourceUrl"
                    :href="item.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                  >打开</a><span v-else>—</span>
                </td><td><span :class="['status-badge', `status-badge--${item.status.toLowerCase()}`]">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td><td v-if="writable">
                  <div class="table-actions">
                    <button
                      type="button"
                      @click="edit(item)"
                    >
                      编辑
                    </button><button
                      type="button"
                      class="danger-button"
                      :disabled="item.status === 'DISABLED'"
                      @click="disable(item)"
                    >
                      停用
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
      aria-label="二维码预览"
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
          alt="供应商微信二维码"
        /><img
          v-else
          class="supplier-qr-preview"
          :src="preview.value"
          alt="供应商微信二维码"
          referrerpolicy="no-referrer"
        >
      </div>
    </div>
  </section>
</template>
