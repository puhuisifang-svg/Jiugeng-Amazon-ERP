<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dashboardApi, type ShipmentStatistics } from '@/api/dashboard'
import { ApiError } from '@/api/client'
import TableViewport from '@/components/TableViewport.vue'
import SuccessToast from '@/components/SuccessToast.vue'
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
const filters = reactive({ from: `${today.slice(0, 7)}-01`, to: today, keyword: '' })
const applied = ref({ ...filters }); const page = ref(0); const result = ref<ShipmentStatistics>(); const loading = ref(false)
const error = ref(''); const errorKey = ref(0); let sequence = 0
const number = (value: number) => value.toLocaleString('zh-CN')
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
async function load(search = false) {
  if (search) {
    const days = (Date.parse(filters.to) - Date.parse(filters.from)) / 86400000
    if (!filters.from || !filters.to || !Number.isFinite(days) || days < 0 || days > 365) { errorKey.value++; error.value = '请选择有效日期范围，开始日期不能晚于结束日期，且最多查询 366 天。'; return }
    page.value = 0; applied.value = { ...filters }
  }
  const request = ++sequence; loading.value = true; error.value = ''
  try { const data = await dashboardApi.statistics({ ...applied.value, page: page.value, size: 20 }); if (request === sequence) result.value = data }
  catch (e) { if (request === sequence) { result.value = undefined; errorKey.value++; error.value = e instanceof ApiError ? e.message : '统计加载失败，请重试。' } }
  finally { if (request === sequence) loading.value = false }
}
function preset(days: number) {
  const start = new Date(`${today}T00:00:00Z`); start.setUTCDate(start.getUTCDate() - days + 1)
  filters.from = start.toISOString().slice(0, 10); filters.to = today; void load(true)
}
onMounted(() => void load(true))
</script>
<template>
  <section class="page-stack shipment-statistics">
    <SuccessToast
      :key="errorKey"
      :message="error"
      tone="error"
    />
    <div class="page-actions">
      <p>按确认发货时间统计（北京时间）；仅包含已确认发货记录，不含删除或转待发货。数量单位为套，箱数单位为箱。</p>
    </div>
    <form
      class="panel operation-form dashboard-filters"
      @submit.prevent="load(true)"
    >
      <label>关键词<input
        v-model="filters.keyword"
        maxlength="128"
        placeholder="产品名称、编号、SKU或备注"
      ></label>
      <label>开始日期<input
        v-model="filters.from"
        type="date"
        required
      ></label><label>结束日期<input
        v-model="filters.to"
        type="date"
        required
      ></label>
      <button
        type="submit"
        :disabled="loading"
      >
        查询
      </button><button
        type="button"
        class="secondary-button"
        :disabled="loading"
        @click="preset(1)"
      >
        今日
      </button><button
        type="button"
        class="secondary-button"
        :disabled="loading"
        @click="preset(7)"
      >
        近7天
      </button><button
        type="button"
        class="secondary-button"
        :disabled="loading"
        @click="preset(30)"
      >
        近30天
      </button>
    </form>
    <p
      v-if="loading"
      class="data-state"
    >
      正在加载发货统计…
    </p>
    <template v-else-if="result">
      <div class="summary-grid dispatch-summary">
        <article class="summary-card">
          <span>发货记录数</span><strong>{{ number(result.summary.records) }}</strong><small>每次确认一条记录，非物流单数</small>
        </article>
        <article class="summary-card">
          <span>发货数量（套）</span><strong>{{ number(result.summary.quantity) }}</strong><small>筛选范围内累计发货</small>
        </article>
        <article class="summary-card">
          <span>发货箱数（箱）</span><strong>{{ number(result.summary.boxCount) }}</strong><small>筛选范围内累计箱数</small>
        </article>
        <article class="summary-card">
          <span>发货产品数</span><strong>{{ number(result.summary.products) }}</strong><small>按产品去重，不按 SKU 分摊</small>
        </article>
      </div>
      <div class="dispatch-breakdowns">
        <article class="panel table-panel">
          <h2>每日发货统计</h2><TableViewport>
            <table class="data-table">
              <thead><tr><th>日期</th><th>记录数</th><th>数量（套）</th><th>箱数</th></tr></thead><tbody>
                <tr
                  v-for="day in result.daily"
                  :key="day.date"
                >
                  <td>{{ day.date }}</td><td>{{ number(day.records) }}</td><td>{{ number(day.quantity) }}</td><td>{{ number(day.boxCount) }}</td>
                </tr><tr v-if="!result.daily.length">
                  <td colspan="4">
                    该日期范围暂无发货记录
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </article>
        <article class="panel table-panel">
          <h2>产品发货排行 · 前10名</h2><TableViewport>
            <table class="data-table">
              <thead><tr><th>产品 / 编号</th><th>记录数</th><th>数量（套）</th><th>箱数</th></tr></thead><tbody>
                <tr
                  v-for="product in result.products"
                  :key="product.productId"
                >
                  <td>{{ product.productName }}<small>{{ product.productCode }}</small></td><td>{{ number(product.records) }}</td><td>{{ number(product.quantity) }}</td><td>{{ number(product.boxCount) }}</td>
                </tr><tr v-if="!result.products.length">
                  <td colspan="4">
                    暂无排行数据
                  </td>
                </tr>
              </tbody>
            </table>
          </TableViewport>
        </article>
      </div>
      <article class="panel table-panel">
        <h2>已发货明细 <small>共 {{ number(result.details.totalElements) }} 条 · 发货时快照</small></h2>
        <TableViewport>
          <table class="data-table">
            <thead><tr><th>确认发货时间</th><th>产品名称 / 编号</th><th>SKU（产品关联）</th><th>发货数量（套）</th><th>发货箱数</th><th>备注</th><th>操作人</th><th>发货记录号</th></tr></thead><tbody>
              <tr
                v-for="item in result.details.items"
                :key="item.id"
              >
                <td>{{ time(item.dispatchedAt) }}</td><td>{{ item.productName }}<small>{{ item.productCode }}</small></td><td class="shipment-remark">
                  {{ item.skuCodes || '—' }}
                </td><td>{{ number(item.quantity) }}</td><td>{{ number(item.boxCount) }}</td><td class="shipment-remark">
                  {{ item.remark || '—' }}
                </td><td>{{ item.actorName }}</td><td>{{ item.id }}</td>
              </tr><tr v-if="!result.details.items.length">
                <td colspan="8">
                  暂无已发货数据
                </td>
              </tr>
            </tbody>
          </table>
        </TableViewport>
        <div class="pagination">
          <span>第 {{ page + 1 }} / {{ Math.max(1, result.details.totalPages) }} 页</span><button
            type="button"
            :disabled="page === 0 || loading"
            @click="page--; load()"
          >
            上一页
          </button><button
            type="button"
            :disabled="page + 1 >= result.details.totalPages || loading"
            @click="page++; load()"
          >
            下一页
          </button>
        </div>
      </article>
    </template>
    <p
      v-else
      class="data-state"
    >
      暂无可显示的统计结果，请重新查询。
    </p>
  </section>
</template>
