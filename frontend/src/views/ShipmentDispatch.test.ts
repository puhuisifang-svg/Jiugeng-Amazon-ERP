import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
const state = vi.hoisted(() => ({ manage: true, shipped: false, count: 1 }))
vi.mock('@/ui/shipmentExport', () => ({ createShipmentImage: vi.fn(async () => ({ blob: new Blob(['png']), missingImages: 0 })) }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => state.manage } }))
vi.mock('@/api/domain', () => ({ domainApi: { product: vi.fn(async () => ({ id: 'p1', variants: [] })) } }))
vi.mock('@/api/dashboard', () => ({ dashboardApi: {
  home: vi.fn(async (kind: string) => {
    const item = { id: 't1', productId: 'p1', variantId: 'v1', customerProduct: { id: 'v1', customerId: 'c1', customerName: '合成客户', productId: 'p1', variantId: 'v1', customerProductCode: 'CP-1', requirements: '' }, productName: '测试产品', productCode: 'P1', quantity: 5, boxCount: 2, remark: '小心轻放', version: 4,
      shipDate: '2026-10-16', plannedShipDate: '2026-10-17', latestShipAt: '2026-10-20T15:59:00Z' }
    const items = kind === 'pending-products' || (state.shipped && kind === 'today-shipments') ? [] : Array.from({ length: state.count }, (_, i) => ({ ...item, id: `t${i + 1}` }))
    return { items, totalCount: items.length, truncated: false, businessDate: '2026-10-16' }
  }), documents: vi.fn(async () => []), dispatch: vi.fn(), statistics: vi.fn(),
} }))
import { dashboardApi } from '@/api/dashboard'
import { createShipmentImage } from '@/ui/shipmentExport'
import DashboardView from './DashboardView.vue'
import ShipmentStatisticsView from './ShipmentStatisticsView.vue'
const wrappers: ReturnType<typeof mount>[] = []
function home() { const wrapper = mount(DashboardView, { global: { stubs: { RouterLink: true } } }); wrappers.push(wrapper); return wrapper }
beforeEach(() => { state.manage = true; state.shipped = false; state.count = 1; vi.clearAllMocks() })
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('shipment confirmation', () => {
  it('does not select unbound legacy rows for dispatch or export', async () => {
    vi.mocked(dashboardApi.home).mockResolvedValueOnce({ items: [{ id: 'legacy', productId: 'p1', productName: '旧父体', quantity: 2, boxCount: 1 }], totalCount: 1 } as never)
    const wrapper = home(); await flushPromises()
    expect(wrapper.get('tbody input').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('待补选 SKU')
    await wrapper.get('thead input').setValue(true)
    expect(wrapper.get('.shipment-export-button').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').find(b => b.text() === '一键发货')!.attributes('disabled')).toBeDefined()
  })
  it('selects all rows, shows partial selection and retries only unfinished dispatches', async () => {
    state.count = 2
    vi.mocked(dashboardApi.dispatch).mockResolvedValueOnce({} as never).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({} as never)
    const wrapper = home(); await flushPromises()
    await wrapper.get('thead input').setValue(true)
    expect(wrapper.findAll('tbody input').every(input => (input.element as HTMLInputElement).checked)).toBe(true)
    await wrapper.findAll('tbody input')[0]!.setValue(false)
    expect((wrapper.get('thead input').element as HTMLInputElement).indeterminate).toBe(true)
    await wrapper.get('thead input').setValue(true)
    await wrapper.findAll('button').find(b => b.text() === '一键发货')!.trigger('click')
    await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(dashboardApi.dispatch).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.shipment-transfer-summary')).toHaveLength(1)
    await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(vi.mocked(dashboardApi.dispatch).mock.calls.map(call => call[0])).toEqual(['t1', 't2', 't2'])
  })
  it('requires selection, highlights export in green and exports only selected rows', async () => {
    state.count = 2
    URL.createObjectURL = vi.fn(() => 'blob:shipment-export'); URL.revokeObjectURL = vi.fn()
    const wrapper = home(); await flushPromises()
    expect(wrapper.get('.shipment-export-button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.shipment-export-button').classes()).not.toContain('is-selected')
    await wrapper.get('.shipment-export-button').trigger('click'); await flushPromises()
    expect(createShipmentImage).not.toHaveBeenCalled()
    await wrapper.findAll('tbody input')[1]!.setValue(true)
    expect(wrapper.get('.shipment-export-button').classes()).toContain('is-selected')
    await wrapper.findAll('button').find(b => b.text() === '导出')!.trigger('click'); await flushPromises()
    expect(vi.mocked(createShipmentImage).mock.calls[0]![0].map(item => item.id)).toEqual(['t2'])
    expect(wrapper.get('a[download]').attributes('download')).toBe('今日发货清单_2026-10-16.png')
    await wrapper.findAll('button').find(b => b.text() === '关闭')!.trigger('click')
    await wrapper.findAll('tbody input')[1]!.setValue(false)
    expect(wrapper.get('.shipment-export-button').attributes('disabled')).toBeDefined()
    expect(dashboardApi.dispatch).not.toHaveBeenCalled()
  })
  it('selects one row, cancels without writes, then confirms and removes the shipped row', async () => {
    vi.mocked(dashboardApi.dispatch).mockImplementation(async () => { state.shipped = true; return {} as never })
    const wrapper = home(); await flushPromises()
    expect(wrapper.findAll('th').map(t => t.text())).not.toContain('发货日期')
    const button = () => wrapper.findAll('button').find(b => b.text() === '一键发货')!
    expect(button().attributes('disabled')).toBeDefined()
    await wrapper.get('tbody input[type="checkbox"]').setValue(true)
    await button().trigger('click'); expect(wrapper.get('[role="dialog"]').text()).toContain('小心轻放')
    await wrapper.get('[aria-label="关闭发货确认"]').trigger('click')
    expect(dashboardApi.dispatch).not.toHaveBeenCalled()
    await button().trigger('click'); await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(dashboardApi.dispatch).toHaveBeenCalledWith('t1', 4, expect.any(String))
    expect(wrapper.find('tbody input[type="checkbox"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('发货已确认，已计入发货统计')
  })
  it('keeps confirmation and idempotency key on retry after failure', async () => {
    vi.mocked(dashboardApi.dispatch).mockRejectedValue(new Error('offline'))
    const wrapper = home(); await flushPromises()
    await wrapper.get('tbody input[type="checkbox"]').setValue(true)
    await wrapper.findAll('button').find(b => b.text() === '一键发货')!.trigger('click')
    await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('发货确认失败')
    const key = vi.mocked(dashboardApi.dispatch).mock.calls[0]![2]
    await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(vi.mocked(dashboardApi.dispatch).mock.calls[1]![2]).toBe(key)
  })
  it('hides shipping actions from readers and highlights pending deadlines', async () => {
    state.manage = false
    const wrapper = home(); await flushPromises()
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('一键发货')
    await wrapper.findAll('[role="tab"]').find(t => t.text().startsWith('待发货'))!.trigger('click'); await flushPromises()
    expect(wrapper.get('.shipment-deadline').text()).toBe('最晚 2026-10-20')
  })
})
describe('shipment statistics', () => {
  it('shows totals and snapshots and validates date filters as a toast', async () => {
    vi.mocked(dashboardApi.statistics).mockResolvedValue({ summary: { records: 1, quantity: 5, boxCount: 2, products: 1 }, daily: [], products: [],
      details: { items: [{ id: 'dispatch-1', productName: '产品快照', productCode: 'P1', skuCodes: 'SKU1', quantity: 5, boxCount: 2, actorName: '操作员', dispatchedAt: '2026-10-16T01:00:00Z', remark: '发货备注' }], page: 0, size: 20, totalElements: 1, totalPages: 1 } } as never)
    const wrapper = mount(ShipmentStatisticsView); wrappers.push(wrapper); await flushPromises()
    expect(wrapper.text()).toContain('发货 SKU / 历史父体数'); expect(wrapper.text()).toContain('产品快照'); expect(wrapper.text()).toContain('发货备注')
    const dates = wrapper.findAll('input[type="date"]')
    await dates[0]!.setValue('2026-10-20'); await dates[1]!.setValue('2026-10-16')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(dashboardApi.statistics).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.success-toast--error').text()).toContain('开始日期不能晚于结束日期')
  })
})
