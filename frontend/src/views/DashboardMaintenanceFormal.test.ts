import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
const { createShipment, transferToToday, transferToPending, archive } = vi.hoisted(() => ({ createShipment: vi.fn(), transferToToday: vi.fn(), transferToPending: vi.fn(), archive: vi.fn() }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/api/dashboard', () => ({ dashboardApi: {
  page: vi.fn(async () => ({ items: [], totalPages: 0, totalElements: 0 })), history: vi.fn(async () => []), createShipment, transferToToday, transferToPending, archive,
} }))
vi.mock('@/api/domain', () => ({ domainApi: { products: vi.fn(async () => ({ items: [
  { id: 'p1', productCode: 'P-1', productName: '测试产品', status: 'ACTIVE', variants: [{ imageUrl: 'https://example.com/product.png' }] },
] })) } }))
import DashboardMaintenanceView from './DashboardMaintenanceView.vue'
import { dashboardApi, type PendingShipment, type TodayShipment } from '@/api/dashboard'
describe('today shipment dates', () => {
  it('shows old and new values in shipment history', async () => {
    const item = { id: 'pending-history', productId: 'p1', productName: '测试产品', plannedShipDate: '2026-10-17', latestShipAt: '2026-10-20T15:59:00Z', quantity: 5, boxCount: 2, status: 'PENDING', version: 1 } as PendingShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    vi.mocked(dashboardApi.history).mockResolvedValueOnce([{ id: 'audit-1', action: 'DASHBOARD_PENDING_SHIPMENT_UPDATE', actorLoginName: 'tester', actorDisplayName: '测试人员', occurredAt: '2026-10-17T00:00:00Z', detail: '更新待发货安排', beforeValue: { quantity: 3, remark: '旧备注' }, afterValue: { quantity: 5, remark: '新备注' } }])
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' }, global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    await wrapper.get('.shipment-history-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="资源历史记录"]').text()).toContain('发货数量：3 → 5')
    expect(wrapper.get('[aria-label="资源历史记录"]').text()).toContain('备注：旧备注 → 新备注')
    wrapper.unmount()
  })
  it('shows invalid date ranges as a toast and clears filters when switching lists', async () => {
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    const form = wrapper.get('.dashboard-filters')
    await form.get('input[maxlength="128"]').setValue('旧页面搜索')
    const dates = form.findAll('input[type="date"]')
    await dates[0]!.setValue('2026-10-20')
    await dates[1]!.setValue('2026-10-17')
    const calls = vi.mocked(dashboardApi.page).mock.calls.length
    await form.trigger('submit')
    expect(dashboardApi.page).toHaveBeenCalledTimes(calls)
    expect(wrapper.get('.success-toast--error').text()).toContain('开始日期不能晚于结束日期')
    expect(wrapper.find('.data-state--error').exists()).toBe(false)
    await wrapper.setProps({ kind: 'pending-shipments' })
    await flushPromises()
    expect(dashboardApi.page).toHaveBeenLastCalledWith('pending-shipments', { keyword: '', status: '', from: '', to: '', page: 0, size: 20 })
    expect((wrapper.get('.dashboard-filters input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('.success-toast--error').exists()).toBe(false)
    wrapper.unmount()
  })
  it('allows deleting a transferred pending shipment after confirmation', async () => {
    const item = { id: 'old-pending', productId: 'p1', productName: '历史发货', plannedShipDate: '2026-10-17',
      latestShipAt: '2026-10-20T15:59:00Z', quantity: 3, boxCount: 2, status: 'TRANSFERRED_TO_TODAY', version: 1 } as PendingShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    await wrapper.findAll('td button').find(button => button.text() === '删除')!.trigger('click')
    expect(archive).not.toHaveBeenCalled()
    expect(wrapper.get('[aria-labelledby="delete-shipment-title"]').text()).toContain('历史发货')
    await wrapper.get('[aria-labelledby="delete-shipment-title"] form').trigger('submit')
    await flushPromises()
    expect(archive).toHaveBeenCalledWith('pending-shipments', 'old-pending', 1, '用户删除')
    wrapper.unmount()
  })
  it('offers delete and return-to-pending without a history button', async () => {
    const item = { id: 'today-1', productId: 'p1', productName: '测试产品', shipDate: '2026-10-17',
      latestShipAt: '2026-10-17T15:59:00Z', quantity: 3, boxCount: 2, status: 'ACTIVE', version: 4 } as TodayShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    expect(wrapper.get('.material-toolbar button').text()).toBe('新增今日发货')
    const buttons = wrapper.findAll('td.table-actions button')
    expect(buttons.map(button => button.text())).toEqual(['编辑', '转为待发货', '删除'])
    await buttons[2]!.trigger('click')
    expect(archive).not.toHaveBeenCalled()
    await wrapper.get('[aria-label="关闭删除弹窗"]').trigger('click')
    expect(wrapper.find('[aria-labelledby="delete-shipment-title"]').exists()).toBe(false)
    await buttons[2]!.trigger('click')
    await wrapper.get('[aria-labelledby="delete-shipment-title"] form').trigger('submit')
    await flushPromises()
    expect(archive).toHaveBeenCalledWith('today-shipments', 'today-1', 4, '用户删除')
    wrapper.unmount()
  })
  it('returns a today row to pending and reports success as a toast', async () => {
    const item = { id: 'today-1', productId: 'p1', productName: '测试产品', shipDate: '2026-10-17',
      latestShipAt: '2026-10-17T15:59:00Z', quantity: 3, boxCount: 2, status: 'ACTIVE', version: 4 } as TodayShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '转为待发货')!.trigger('click')
    expect(wrapper.get('[role="dialog"] input').attributes('placeholder')).toBe('请输入转为待发货的原因')
    expect(wrapper.get('[role="dialog"] button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[role="dialog"] input').setValue('临时调整计划')
    await wrapper.get('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(transferToPending).toHaveBeenCalledWith('today-1', 4, '临时调整计划')
    expect(wrapper.get('.success-toast').text()).toContain('已转为待发货')
    expect(wrapper.find('.data-state--success').exists()).toBe(false)
    wrapper.unmount()
  })
  it('transfers a pending row directly using its quantity and boxes', async () => {
    const item = { id: 'pending-1', productId: 'p1', productName: '测试产品', plannedShipDate: '2026-10-17',
      latestShipAt: '2026-10-20T15:59:00Z', quantity: 3, boxCount: 2, status: 'PENDING', version: 0 } as PendingShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    expect(wrapper.get('.material-toolbar button').text()).toBe('新增待发货')
    await wrapper.findAll('button').find(button => button.text() === '转到今日发货')!.trigger('click')
    expect(transferToToday).not.toHaveBeenCalled()
    expect(wrapper.get('[aria-labelledby="transfer-today-title"]').text()).toContain('产品名：测试产品')
    await wrapper.findAll('button').find(button => button.text() === '取消')!.trigger('click')
    expect(wrapper.find('[aria-labelledby="transfer-today-title"]').exists()).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '转到今日发货')!.trigger('click')
    await wrapper.get('[aria-labelledby="transfer-today-title"] form').trigger('submit')
    await flushPromises()
    expect(transferToToday).toHaveBeenCalledWith('pending-1', expect.objectContaining({ quantity: 3, boxCount: 2, version: 0 }))
    expect(wrapper.text()).toContain('已转入今日发货')
  })
  it('shows shipment dates first, merges equal dates, and exposes important fields', async () => {
    const base = { productId: 'p1', productName: '测试产品', plannedShipDate: '2026-10-17',
      latestShipAt: '2026-10-20T15:59:00Z', quantity: 3, boxCount: 2, status: 'PENDING', version: 0 }
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [
      { ...base, id: 'pending-1', remark: '优先发货' }, { ...base, id: 'pending-2', productName: '第二产品', remark: '等待到仓' },
    ] as PendingShipment[], totalPages: 1, totalElements: 2, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' },
      global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    expect(wrapper.get('input[placeholder="搜索产品编码、产品名称或备注"]')).toBeTruthy()
    expect(wrapper.findAll('th').map(cell => cell.text()).slice(0, 4)).toEqual(['计划发货日期', '产品图片', '产品', '最晚发货日期'])
    const rows = wrapper.findAll('tbody tr')
    expect(rows[0]!.find('td').attributes('rowspan')).toBe('2')
    expect(rows[1]!.findAll('td').some(cell => cell.text() === '2026-10-17')).toBe(false)
    expect(wrapper.text()).toContain('优先发货')
    expect(wrapper.text()).toContain('2026-10-20')
    expect(wrapper.text()).not.toContain('23:59:00')
  })
  afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })
  it('saves with the Shanghai business date without asking for dates', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-16T18:00:00Z'))
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' },
      global: { stubs: { RouterLink: true, AuthenticatedDashboardImage: true } } })
    await flushPromises()
    expect(wrapper.find('.operation-form').exists()).toBe(false)
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.get('.operation-form')
    expect(form.find('input[type="date"]').exists()).toBe(false)
    expect(form.find('input[type="datetime-local"]').exists()).toBe(false)
    await form.get('select').setValue('p1')
    expect(form.find('input[type="file"]').exists()).toBe(false)
    expect(form.find('textarea').exists()).toBe(false)
    expect(form.get('img').attributes('src')).toBe('https://example.com/product.png')
    await form.trigger('submit')
    await flushPromises()
    expect(createShipment).toHaveBeenCalledWith('today-shipments', expect.objectContaining({
      productId: 'p1', date: '2026-10-17', latestShipTime: '2026-10-17T23:59',
    }))
    expect(wrapper.text()).toContain('记录已创建')
  })
  it('automatically displays the product image for pending shipments too', async () => {
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' },
      global: { stubs: { RouterLink: true, AuthenticatedDashboardImage: true } } })
    await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.get('.operation-form')
    await form.get('select').setValue('p1')
    expect(form.find('input[type="file"]').exists()).toBe(false)
    expect(form.find('textarea').exists()).toBe(false)
    expect(form.get('img').attributes('src')).toBe('https://example.com/product.png')
    const dates = form.findAll('input[type="date"]')
    expect(dates).toHaveLength(2)
    await dates[0]!.setValue('2026-10-17')
    await dates[1]!.setValue('2026-10-20')
    await form.trigger('submit')
    await flushPromises()
    expect(createShipment).toHaveBeenCalledWith('pending-shipments', expect.objectContaining({
      date: '2026-10-17', latestShipTime: '2026-10-20T23:59',
    }))
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
