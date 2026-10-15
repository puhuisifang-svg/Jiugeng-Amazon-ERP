<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { catalogApi } from '@/api/catalog'
import { ApiError } from '@/api/client'
import { domainApi, type SupplierDto, type SupplierInput } from '@/api/domain'
import AuthenticatedCatalogImage from '@/components/AuthenticatedCatalogImage.vue'
import { session } from '@/state/session'

const items = ref<SupplierDto[]>([]); const query = ref(''); const status = ref(''); const loading = ref(true); const saving = ref(false); const error = ref(''); const success = ref(''); const editing = ref<SupplierDto | null>(null); const preview = ref<string>()
const form = reactive<SupplierInput>({ name: '', contactPerson: '', phone: '', sourceUrl: '', qrImageId: '', remark: '', status: 'ACTIVE' })
const writable = computed(() => session.canAccess(['suppliers.write']))
function reset() { Object.assign(form, { name: '', contactPerson: '', phone: '', sourceUrl: '', qrImageId: '', remark: '', status: 'ACTIVE', version: undefined }); editing.value = null }
function edit(item: SupplierDto) { editing.value = item; Object.assign(form, { name: item.name, contactPerson: item.contactPerson ?? '', phone: item.phone ?? '', sourceUrl: item.sourceUrl ?? '', qrImageId: item.qrImageId ?? '', remark: item.remark ?? '', status: item.status, version: item.version }) }
async function load() { loading.value = true; error.value = ''; try { const result = await domainApi.suppliers({ ...(query.value ? { q: query.value } : {}), ...(status.value ? { status: status.value as 'ACTIVE' | 'DISABLED' } : {}), size: 100 }); items.value = result.items } catch (exception) { error.value = exception instanceof ApiError ? exception.message : '供应商加载失败。' } finally { loading.value = false } }
async function upload(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return; saving.value = true; try { form.qrImageId = (await catalogApi.uploadImage('SUPPLIER_QR', file)).id } catch (exception) { error.value = exception instanceof Error ? exception.message : '二维码上传失败。' } finally { saving.value = false } }
function supplierInput(): SupplierInput {
  return {
    name: form.name.trim(), status: form.status,
    ...(form.contactPerson?.trim() ? { contactPerson: form.contactPerson.trim() } : {}),
    ...(form.phone?.trim() ? { phone: form.phone.trim() } : {}),
    ...(form.sourceUrl?.trim() ? { sourceUrl: form.sourceUrl.trim() } : {}),
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
        placeholder="名称 / 电话"
      ></label><label>状态<select
        v-model="status"
      ><option value="">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><button
        type="submit"
      >
        查询
      </button>
    </form>
    <form
      v-if="writable"
      class="panel operation-form"
      @submit.prevent="save"
    >
      <h2>{{ editing ? '编辑供应商' : '新增供应商' }}</h2><label>供应商名称<input
        v-model="form.name"
        required
        maxlength="128"
      ></label><label>联系人<input
        v-model="form.contactPerson"
        maxlength="64"
      ></label><label>电话<input
        v-model="form.phone"
        maxlength="32"
      ></label><label>1688 链接<input
        v-model="form.sourceUrl"
        type="url"
        maxlength="500"
        placeholder="https://detail.1688.com/..."
      ></label><label>微信二维码<input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="upload"
      ></label><label>状态<select v-model="form.status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><label class="form-wide">备注<textarea
        v-model="form.remark"
        maxlength="500"
      /></label><div
        v-if="form.qrImageId"
        class="form-wide catalog-image-editor"
      >
        <button
          type="button"
          class="image-button"
          @click="preview = form.qrImageId"
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
    <p
      v-if="success"
      class="data-state data-state--success"
      role="status"
    >
      {{ success }}
    </p><p
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
        <table class="data-table">
          <thead>
            <tr>
              <th>二维码</th><th>名称</th><th>联系人</th><th>电话</th><th>1688</th><th>状态</th><th v-if="writable">
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
                  @click="preview = item.qrImageId"
                >
                  <AuthenticatedCatalogImage
                    :image-id="item.qrImageId"
                    :alt="`${item.name} 二维码`"
                  />
                </button><span v-else>—</span>
              </td><td>{{ item.name }}</td><td>{{ item.contactPerson || '—' }}</td><td>{{ item.phone || '—' }}</td><td>
                <a
                  v-if="item.sourceUrl"
                  :href="item.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >打开</a><span v-else>—</span>
              </td><td>{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</td><td v-if="writable">
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
          :image-id="preview"
          variant="original"
          alt="供应商微信二维码"
        />
      </div>
    </div>
  </section>
</template>
