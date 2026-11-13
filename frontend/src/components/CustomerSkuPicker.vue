<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { domainApi, type SkuListItemDto } from '@/api/domain'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import AuthenticatedCatalogImage from './AuthenticatedCatalogImage.vue'
const props = withDefaults(defineProps<{ selectedId?: string; activeOnly?: boolean; showCodes?: boolean }>(), { activeOnly: true, selectedId: '', showCodes: true })
const emit = defineEmits<{ close: []; select: [sku: SkuListItemDto] }>()
const query = ref(''), applied = ref(''), page = ref(1), size = ref(20), total = ref(0)
const rows = ref<SkuListItemDto[]>([]), chosen = ref<SkuListItemDto>(), loading = ref(false), error = ref('')
let sequence = 0
async function load() {
  const request = ++sequence; loading.value = true; error.value = ''; rows.value = []
  try {
    const result = await domainApi.skus({ q: applied.value, exact: false, ...(props.activeOnly ? { status: 'ACTIVE' as const, productStatus: 'ACTIVE' as const } : {}), page: page.value - 1, size: size.value })
    if (request !== sequence) return
    rows.value = result.items; total.value = result.totalCount
    if (!chosen.value) chosen.value = rows.value.find(item => item.variant.id === props.selectedId)
  } catch (e) { if (request === sequence) { error.value = e instanceof Error ? e.message : 'SKU 加载失败'; total.value = 0 } }
  finally { if (request === sequence) loading.value = false }
}
function search() { applied.value = query.value.trim(); page.value = 1; void load() }
function resize(value: number) { size.value = value; page.value = 1; void load() }
onMounted(load)
</script>
<template>
  <AccessDialog
    title="选择产品 SKU"
    wide
    dialog-class="customer-sku-picker"
    @close="emit('close')"
  >
    <form
      class="sku-picker-filter"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="query"
        type="search"
        maxlength="128"
        :placeholder="showCodes ? 'SKU 编码、产品名称' : '产品名称'"
        aria-label="筛选 SKU"
      ></label><button
        type="submit"
        class="primary-button"
        :disabled="loading"
      >
        搜索
      </button>
    </form>
    <p class="access-muted">
      {{ activeOnly ? '仅展示启用的产品及 SKU，' : '' }}一次选择一个 SKU。
    </p>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }} <button
        type="button"
        @click="load"
      >
        重试
      </button>
    </p>
    <p
      v-else-if="loading"
      role="status"
    >
      正在加载 SKU…
    </p>
    <div
      v-else
      class="sku-picker-table"
    >
      <table class="data-table">
        <thead><tr><th>选择</th><th>图片</th><th>{{ showCodes ? 'SKU 名称 / 编码' : 'SKU 名称' }}</th><th>所属父体</th></tr></thead><tbody>
          <tr
            v-for="row in rows"
            :key="row.variant.id"
            :class="{ selected: chosen?.variant.id === row.variant.id }"
          >
            <td>
              <input
                type="radio"
                name="customer-sku"
                :checked="chosen?.variant.id === row.variant.id"
                :aria-label="`选择 ${showCodes ? row.variant.skuCode : row.variant.variantName}`"
                @change="chosen = row"
              >
            </td><td>
              <AuthenticatedCatalogImage
                v-if="row.variant.imageAssetId || !row.variant.imageUrl"
                :image-id="row.variant.imageAssetId"
                :alt="row.variant.variantName"
              /><img
                v-else
                class="catalog-image"
                :src="row.variant.imageUrl"
                :alt="row.variant.variantName"
                referrerpolicy="no-referrer"
              >
            </td><td>{{ row.variant.variantName }}<small v-if="showCodes">{{ row.variant.skuCode }}</small></td><td>{{ row.productName }}<small v-if="showCodes">{{ row.productCode }}</small></td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="4">
              没有符合条件的 SKU
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #pagination>
      <ListPagination
        :page="page"
        :page-size="size"
        :total="total"
        :disabled="loading"
        @update:page="page = $event; load()"
        @update:page-size="resize"
      /><div class="sku-picker-footer ui-dialog-actions">
        <span>{{ chosen ? `已选：${showCodes ? chosen.variant.skuCode : chosen.variant.variantName}` : '尚未选择 SKU' }}</span><button
          type="button"
          @click="emit('close')"
        >
          取消
        </button><button
          class="primary-button"
          type="button"
          :disabled="!chosen || loading || !!error"
          @click="chosen && emit('select', chosen)"
        >
          确认选择
        </button>
      </div>
    </template>
  </AccessDialog>
</template>
<style scoped>
.sku-picker-filter{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.sku-picker-filter label{display:flex;align-items:center;gap:8px;font-size:13px;min-width:0}.sku-picker-filter input{width:280px;max-width:100%;min-width:0;height:32px;border:1px solid var(--line);border-radius:4px;padding:0 10px;font-size:13px}.sku-picker-table{overflow:auto}.sku-picker-table table{width:100%;min-width:600px}.sku-picker-table small{display:block;color:var(--muted);font-size:12px;margin-top:5px}.sku-picker-table .selected{background:#edf5ff}.sku-picker-table input{width:16px;height:16px;accent-color:var(--accent)}.sku-picker-table .catalog-image{object-fit:contain}.sku-picker-footer{display:flex;align-items:center;gap:10px;padding:10px 14px;width:100%}.sku-picker-footer span{margin-right:auto;font-size:13px;overflow-wrap:anywhere}
</style>
