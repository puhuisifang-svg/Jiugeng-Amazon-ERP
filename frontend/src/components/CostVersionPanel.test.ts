import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import type { CostVersion } from '@/api/costs'
import CostVersionPanel from './CostVersionPanel.vue'
function version(): CostVersion {
  const input = { unit: '件', baseQuantity: 1, materials: [], labor: [], noMaterialReason: '客户提供', noLaborReason: '外购成品' }
  return { id: 'v1', variantId: 's1', versionNumber: 1, status: 'EFFECTIVE', input, snapshot: { input, name: '合成产品', sku: 'SYN-001', materials: [], labor: [], materialTotal: '9.99', laborTotal: '5.55', total: '15.54', errors: [], sourceHash: 'synthetic' }, revision: 0, reason: '首次核定', createdAt: '2026-10-26', publishedAt: null, stale: false }
}
it('shows immutable totals, unit, zero-cost reasons and audit reason', () => {
  const w = mount(CostVersionPanel, { props: { version: version() } })
  expect(w.get('[aria-label="版本成本汇总"]').text()).toContain('15.54')
  for (const text of ['已生效', '人民币', '/ 件', '客户提供', '外购成品', '首次核定']) expect(w.text()).toContain(text)
})
it('marks missing prices and stale sources without presenting zero as a confirmed material price', () => {
  const v = version(); v.stale = true; v.snapshot.errors = ['辅料缺价格']
  v.snapshot.materials = [{ input: { materialId: 'm1', quantity: 1, usageUnit: '件', conversion: 1, perProducts: 1, lossPercent: 0, remark: '' }, name: '合成辅料', unit: '件', price: null, amount: '0', error: '缺价格' }]
  const w = mount(CostVersionPanel, { props: { version: v } })
  expect(w.text()).toContain('当前小计 · 待完善'); expect(w.text()).toContain('来源已变化')
  expect(w.get('tbody td.cost-number').text()).toBe('待完善'); expect(w.text()).toContain('辅料缺价格')
})
