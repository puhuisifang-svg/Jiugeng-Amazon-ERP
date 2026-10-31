<script setup lang="ts">
import DatePicker from '@/components/DatePicker.vue'
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { dashboardApi, type HomePendingProduct } from '@/api/dashboard'
import { stateNames } from '@/api/launch'
import TableViewport from '@/components/TableViewport.vue'
import ListPagination from '@/components/ListPagination.vue'
import { session } from '@/state/session'
const filters=reactive({keyword:'',from:'',to:''})
const applied=ref({...filters}),items=ref<HomePendingProduct[]>([]),page=ref(1),size=ref(20),total=ref(0),loading=ref(false),error=ref('')
let sequence=0
async function load(){const current=++sequence;loading.value=true;error.value='';try{const result=await dashboardApi.productionProducts({...applied.value,page:page.value-1,size:size.value});if(current===sequence){items.value=result.items;total.value=result.totalElements}}catch(e){if(current===sequence)error.value=e instanceof Error?e.message:'加载失败，请重试'}finally{if(current===sequence)loading.value=false}}
function search(){applied.value={...filters};page.value=1;void load()}
function reset(){Object.assign(filters,{keyword:'',from:'',to:''});search()}
onMounted(load)
</script>
<template>
  <section class="page-stack">
    <p class="muted">
      生产端清单：仅展示已通过立项审核且未完成的项目（暂停、终止项目不展示），资料与新品开发同步。
    </p>
    <form
      class="filter-panel production-filters"
      @submit.prevent="search"
    >
      <label>关键词<input
        v-model="filters.keyword"
        placeholder="项目名称、编号、负责人"
      ></label>
      <label class="date-range-filter">计划完成日期<DatePicker
        v-model="filters.from"
        v-model:end="filters.to"
        range
      /></label>
      <button
        type="submit"
        class="primary-action"
        :disabled="loading"
      >
        搜索
      </button>
      <button
        type="button"
        class="filter-reset-button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button>
      <button
        type="button"
        class="secondary-action"
        :disabled="loading"
        @click="load"
      >
        刷新
      </button>
    </form>
    <p
      v-if="error"
      class="data-state data-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载已审核项目…
    </p>
    <TableViewport>
      <table class="data-table">
        <thead><tr><th>开发项目 / 编号</th><th>审核状态</th><th>开发状态</th><th>当前阶段</th><th>负责人</th><th>计划完成日期</th><th>说明</th><th>操作</th></tr></thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td><strong>{{ item.productName }}</strong><small class="muted">{{ item.number }}</small></td>
            <td><span class="approved">已通过</span></td>
            <td>{{ stateNames[item.developmentState ?? ''] ?? item.developmentState }}</td>
            <td>{{ item.stage == null ? '待确认' : ['方案确认','供应与打样','样品验证','定型风险复核','定型与建档'][item.stage] }}</td>
            <td>{{ item.owner || '待分配' }}</td><td>{{ item.expectedCompletionDate || '待安排' }}</td><td>{{ item.remark || '—' }}</td>
            <td>
              <RouterLink
                v-if="session.canAccess(['products.read'])"
                :to="`/product-development/${item.developmentId}`"
              >
                详情
              </RouterLink><span
                v-else
                class="muted"
              >只读</span>
            </td>
          </tr>
          <tr v-if="!loading && !items.length">
            <td
              colspan="8"
              class="data-state"
            >
              暂无符合条件的已审核项目
            </td>
          </tr>
        </tbody>
      </table>
    </TableViewport>
    <ListPagination
      :total="total"
      :page="page"
      :page-size="size"
      :disabled="loading"
      @update:page="page=$event;load()"
      @update:page-size="size=$event;page=1;load()"
    />
  </section>
</template>
<style scoped>
.production-filters{display:flex;flex-wrap:wrap;align-items:end;gap:var(--ui-gap,8px)}
.filter-panel.production-filters label{display:flex;flex-direction:row;align-items:center;gap:8px;min-width:260px;flex:0 1 300px}
.filter-panel.production-filters label input{min-height:32px;flex:1;width:0;min-width:120px;max-width:100%;box-sizing:border-box}
.data-table{min-width:980px;width:100%}.data-table small{display:block}.approved{color:#16804a}
@media(max-width:1000px){.filter-panel.production-filters label{flex-direction:column;align-items:stretch;min-width:0}.filter-panel.production-filters label input{width:100%;flex:none}}
@media(max-width:600px){.filter-panel.production-filters label{width:100%;flex:1 1 100%}}
</style>
