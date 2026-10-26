<script setup lang="ts">
import type { CostVersion } from '@/api/costs'
defineProps<{ version: CostVersion }>()
const labels: Record<string, string> = { DRAFT: '草稿', EFFECTIVE: '已生效', HISTORICAL: '历史版本' }
const basis = { PIECE: '每件', BOX: '每箱', BATCH: '每批' }
</script>
<template>
  <article class="cost-version">
    <header class="cost-version__header">
      <div>
        <strong>V{{ version.versionNumber }}</strong><span
          class="cost-badge"
          :class="`cost-badge--${version.status.toLowerCase()}`"
        >{{ labels[version.status] }}</span>
      </div><small>{{ version.publishedAt ?? version.createdAt }}</small>
    </header>
    <div
      class="cost-metrics"
      aria-label="版本成本汇总"
    >
      <div><span>物料成本</span><strong><small>¥</small>{{ version.snapshot.materialTotal }}</strong></div>
      <div><span>人工成本</span><strong><small>¥</small>{{ version.snapshot.laborTotal }}</strong></div>
      <div class="cost-metrics__total">
        <span>{{ version.snapshot.errors.length ? '当前小计 · 待完善' : '单位标准成本' }}</span><strong><small>¥</small>{{ version.snapshot.total }}</strong><small>/ {{ version.input.unit }}</small>
      </div>
    </div>
    <p class="cost-meta">
      人民币 · 物料不含税 · 核算基数 {{ version.input.baseQuantity }} {{ version.input.unit }}
    </p>
    <div
      v-if="version.stale"
      class="cost-notice cost-notice--warning"
    >
      来源已变化。此版本金额保持不变，请在草稿中重新核算。
    </div>
    <section class="cost-detail-section">
      <header><h3><span class="cost-section-mark">01</span>物料明细</h3><span>{{ version.snapshot.materials.length }} 项</span></header>
      <div class="cost-detail-table">
        <table>
          <thead>
            <tr>
              <th>辅料 / 用量依据</th><th class="cost-number">
                成本 / {{ version.input.unit }}
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="m in version.snapshot.materials"
              :key="m.input.materialId"
            >
              <td><strong>{{ m.name }}</strong><small>用量 {{ m.input.quantity }} {{ m.input.usageUnit }} · 单价 {{ m.price ?? '缺价格' }} 元 / {{ m.unit }}</small><small>换算 {{ m.input.conversion }} · 分摊 {{ m.input.perProducts }} · 损耗 {{ m.input.lossPercent }}%</small><small v-if="m.input.remark">{{ m.input.remark }}</small></td><td class="cost-number">
                <span
                  v-if="m.price === null"
                  class="cost-badge cost-badge--warning"
                >待完善</span><strong v-else>{{ m.amount }}</strong>
              </td>
            </tr><tr v-if="!version.snapshot.materials.length">
              <td
                colspan="2"
                class="cost-empty-inline"
              >
                无物料 · {{ version.input.noMaterialReason || '尚未说明原因' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="cost-detail-section">
      <header><h3><span class="cost-section-mark">02</span>人工明细</h3><span>{{ version.snapshot.labor.length }} 道工序</span></header>
      <div class="cost-detail-table">
        <table>
          <thead>
            <tr>
              <th>工序 / 计价依据</th><th class="cost-number">
                成本 / {{ version.input.unit }}
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="(l,index) in version.snapshot.labor"
              :key="index"
            >
              <td><strong>{{ l.name }}</strong><small>工价 {{ l.input.price }} 元 · {{ basis[l.input.basis] }} × {{ l.input.times }} 次 · 每箱成品数 {{ l.input.perProducts }}</small><small v-if="l.input.remark">{{ l.input.remark }}</small></td><td class="cost-number">
                <strong>{{ l.amount }}</strong>
              </td>
            </tr><tr v-if="!version.snapshot.labor.length">
              <td
                colspan="2"
                class="cost-empty-inline"
              >
                无人工 · {{ version.input.noLaborReason || '尚未说明原因' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <div
      v-if="version.snapshot.errors.length"
      class="cost-notice cost-notice--warning"
    >
      <strong>生效前需要完善</strong><ul>
        <li
          v-for="issue in version.snapshot.errors"
          :key="issue"
        >
          {{ issue }}
        </li>
      </ul>
    </div>
    <div class="cost-version__reason">
      <span>变更原因</span><p>{{ version.reason }}</p>
    </div>
  </article>
</template>
