<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MaterialDetailDto } from '@/api/domain'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
import MaterialImage from './MaterialImage.vue'
import { vSingleSelect } from '@/directives/singleSelect'
const props = defineProps<{ materials: MaterialDetailDto[]; selectedIds: string[]; stockLabel: (material: MaterialDetailDto) => string }>()
const emit = defineEmits<{ close: []; select: [ids: string[]] }>()
const keyword = ref(''), category = ref(''), selected = ref<string[]>([]), page = ref(1), size = ref(20)
const available = computed(() => props.materials.filter(item => item.status === 'ACTIVE' && !props.selectedIds.includes(item.id)))
const categories = computed(() => [...new Set(available.value.map(item => item.materialType))])
const filtered = computed(() => available.value.filter(item => (!category.value || item.materialType === category.value) && `${item.materialCode} ${item.name} ${item.specification ?? ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const rows = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
watch([keyword, category, size], () => { page.value = 1 })
function confirm() { emit('select', selected.value.filter(id => available.value.some(item => item.id === id))) }
</script>
<template>
  <AccessDialog
    title="选择辅料"
    dialog-class="product-material-picker"
    wide
    @close="emit('close')"
  >
    <div class="material-picker-filters">
      <label class="material-picker-search"><span>搜索辅料</span><input
        v-model="keyword"
        type="search"
        placeholder="名称或规格"
      ></label>
      <label class="material-picker-category"><span>类型</span><select
        v-model="category"
        v-single-select
      ><option value="">全部类型</option><option
        v-for="type in categories"
        :key="type"
        :value="type"
      >{{ type }}</option></select></label>
    </div>
    <p class="access-muted">
      已关联的辅料不重复展示；勾选后点击“添加所选”加入当前 SKU。
    </p>
    <div class="material-picker-table">
      <table class="data-table">
        <thead>
          <tr>
            <th>选择</th><th class="material-picker-image">
              图片
            </th><th>辅料名称</th><th>类型</th><th>规格</th><th>单位</th><th>库存</th>
          </tr>
        </thead><tbody>
          <tr
            v-for="item in rows"
            :key="item.id"
          >
            <td>
              <input
                v-model="selected"
                type="checkbox"
                :value="item.id"
                :aria-label="`选择 ${item.name}`"
              >
            </td><td class="material-picker-image">
              <MaterialImage
                :name="item.name"
                :image-asset-id="item.imageAssetId"
                :image-url="item.imageUrl"
              />
            </td><td><strong>{{ item.name }}</strong></td><td>{{ item.materialType }}</td><td>{{ item.specification || '—' }}</td><td>{{ item.unit }}</td><td>{{ stockLabel(item) }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td
              colspan="7"
              class="empty-state"
            >
              没有符合条件的可选辅料
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #pagination>
      <ListPagination
        v-model:page="page"
        v-model:page-size="size"
        :total="filtered.length"
      />
      <div class="material-picker-actions ui-dialog-actions">
        <span>已选 {{ selected.length }} 项</span><button
          type="button"
          @click="emit('close')"
        >
          取消
        </button><button
          type="button"
          class="primary-button"
          :disabled="!selected.length"
          @click="confirm"
        >
          添加所选
        </button>
      </div>
    </template>
  </AccessDialog>
</template>
<style scoped>
.material-picker-filters{display:flex;align-items:center;flex-wrap:wrap;gap:10px 16px}
.material-picker-filters label{display:flex;align-items:center;gap:8px;min-width:0;font-size:13px}
.material-picker-filters label>span{flex:none;white-space:nowrap;color:var(--ui-label-color)}
.material-picker-search{flex:1 1 280px}
.material-picker-category{flex:0 1 220px}
.material-picker-table{overflow:auto;min-height:240px}
.material-picker-table table{min-width:700px;width:100%}
.material-picker-table td{vertical-align:middle;padding:8px 10px}
.material-picker-table input[type=checkbox]{width:16px;height:16px;accent-color:var(--accent)}
.material-picker-image{width:64px}
.material-picker-image :deep(.material-thumb),.material-picker-image :deep(.material-thumb>button),.material-picker-image :deep(.material-image-empty){width:48px;height:48px;box-sizing:border-box}
.material-picker-image :deep(.material-thumb>button){display:grid;place-items:center;padding:0;overflow:hidden;border:1px solid var(--line);border-radius:4px;background:#fff}
.material-picker-image :deep(.catalog-image){width:100%;height:100%;object-fit:contain;border-radius:3px}
.material-picker-image :deep(.material-image-empty){display:grid;place-items:center;border:1px solid var(--line);border-radius:4px;background:#f7f9fc;color:var(--muted);font-size:10px;white-space:nowrap}
.material-picker-actions{display:flex;align-items:center;gap:10px;padding:10px 14px;width:100%}
.material-picker-actions span{margin-right:auto;font-size:13px;color:var(--muted)}
@media(max-width:600px){.material-picker-search,.material-picker-category{flex:1 1 100%}.material-picker-filters label>span{width:52px}}
</style>
<style>
/* Match the shared compact filter height, including the directive-created trigger. */
#app .product-material-picker .material-picker-filters :is(input,.single-select-trigger){flex:1;width:100%;min-width:0;max-width:100%;height:var(--ui-control-height,32px);min-height:var(--ui-control-height,32px);box-sizing:border-box;border:1px solid var(--ui-field-border);border-radius:var(--ui-field-radius);padding:0 10px;font-size:var(--ui-text-size,13px);font-weight:400}
</style>
