<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MaterialDetailDto } from '@/api/domain'
import AccessDialog from './AccessDialog.vue'
import ListPagination from './ListPagination.vue'
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
    wide
    @close="emit('close')"
  >
    <div class="material-picker-filters">
      <label>搜索辅料<input
        v-model="keyword"
        type="search"
        placeholder="编码、名称或规格"
      ></label>
      <label>类型<select
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
        <thead><tr><th>选择</th><th>辅料编码 / 名称</th><th>类型</th><th>规格</th><th>单位</th><th>库存</th></tr></thead><tbody>
          <tr
            v-for="item in rows"
            :key="item.id"
          >
            <td>
              <input
                v-model="selected"
                type="checkbox"
                :value="item.id"
                :aria-label="`选择 ${item.materialCode}`"
              >
            </td><td><strong>{{ item.name }}</strong><small>{{ item.materialCode }}</small></td><td>{{ item.materialType }}</td><td>{{ item.specification || '—' }}</td><td>{{ item.unit }}</td><td>{{ stockLabel(item) }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td
              colspan="6"
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
.material-picker-filters{display:flex;flex-wrap:wrap;gap:12px}.material-picker-filters label{display:flex;align-items:center;gap:8px;min-width:0;font-size:13px}.material-picker-filters input{width:260px;max-width:100%;min-width:0;height:32px;border:1px solid var(--line);border-radius:4px;padding:0 10px;font-size:13px}.material-picker-table{overflow:auto;min-height:240px}.material-picker-table table{min-width:650px;width:100%}.material-picker-table small{display:block;color:var(--muted);margin-top:4px}.material-picker-table input{width:16px;height:16px;accent-color:var(--accent)}.material-picker-actions{display:flex;align-items:center;gap:10px;padding:10px 14px;width:100%}.material-picker-actions span{margin-right:auto;font-size:13px;color:var(--muted)}
</style>
