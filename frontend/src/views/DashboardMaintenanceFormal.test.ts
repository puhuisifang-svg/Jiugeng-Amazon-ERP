import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
const { createShipment, transferToToday, transferToPending, archive } = vi.hoisted(() => ({ createShipment: vi.fn(), transferToToday: vi.fn(), transferToPending: vi.fn(), archive: vi.fn() }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/api/dashboard', () => ({ dashboardApi: {
  page: vi.fn(async () => ({ items: [], totalPages: 0, totalElements: 0 })), history: vi.fn(async () => []), dispatch: vi.fn(), createShipment, transferToToday, transferToPending, archive,
} }))
vi.mock('@/api/domain', () => ({ domainApi: { products: vi.fn(async () => ({ items: [
  { id: 'p1', productCode: 'P-1', productName: '测试产品', status: 'ACTIVE', variants: [{ id: 'v1', skuCode: 'SKU-1', variantName: '测试 SKU', status: 'ACTIVE', imageUrl: 'https://example.com/product.png' }] },
] })) } }))
vi.mock('@/components/ShipmentCustomerPicker.vue', () => ({ default: {
  props: ['initial'], emits: ['select'],
  data() { return { selected: '' } },
  template: '<select v-model="selected" @change="$emit(\'select\', { id: selected, productId: \'p1\', variantId: selected })"><option value="">请选择客户商品</option><option value="v1">测试客户商品</option></select>',
} }))
import DashboardMaintenanceView from './DashboardMaintenanceView.vue'
import { dashboardApi, type PendingShipment, type TodayShipment } from '@/api/dashboard'
describe('today shipment dates', () => {
  it('confirms overdue dispatch in the fixed action column and reuses the key after failure', async () => {
    const item = { id: 'overdue', productId: 'p1', variantId: 'v1', customerProduct: { id: 'v1', customerId: 'c1', customerName: '合成客户', productId: 'p1', variantId: 'v1', customerProductCode: 'CP-1', requirements: '' }, productCode: 'SKU-1', productName: '测试 SKU', shipDate: '2020-01-01', latestShipAt: '2020-01-01T15:59:00Z', quantity: 5, boxCount: 2, status: 'ACTIVE', version: 3 } as TodayShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    vi.mocked(dashboardApi.dispatch).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({} as never)
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' }, global: { stubs: { RouterLink: true, ShipmentProductImage: true, ShipmentFiles: true } } })
    await flushPromises()
    expect(wrapper.find('th.shipping-actions-cell').exists()).toBe(true)
    const dispatch = () => wrapper.findAll('td.shipping-actions-cell button').find(button => button.text() === '发货')!
    await dispatch().trigger('click')
    expect(wrapper.get('[aria-label="确认发货"]').text()).toContain('2020-01-01')
    expect(wrapper.get('[aria-label="确认发货"]').text()).toContain('按实际确认日期')
    await wrapper.get('[aria-label="关闭发货确认"]').trigger('click')
    expect(dashboardApi.dispatch).not.toHaveBeenCalled()
    await dispatch().trigger('click')
    await wrapper.get('[aria-label="确认发货"] form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[aria-label="确认发货"] [role="alert"]').text()).toContain('请重试')
    const key = vi.mocked(dashboardApi.dispatch).mock.calls[0]![2]
    await wrapper.get('[aria-label="关闭发货确认"]').trigger('click')
    await dispatch().trigger('click')
    await wrapper.get('[aria-label="确认发货"] form').trigger('submit'); await flushPromises()
    expect(dashboardApi.dispatch).toHaveBeenNthCalledWith(2, 'overdue', 3, key)
    expect(wrapper.find('[aria-label="确认发货"]').exists()).toBe(false)
    expect(wrapper.get('.success-toast').text()).toContain('按实际确认日期计入发货统计')
    wrapper.unmount()
  })
  it.each(['today-shipments', 'pending-shipments'] as const)('protects %s input from mask clicks and confirms all intentional close paths', async kind => {
    const wrapper = mount(DashboardMaintenanceView, { props: { kind }, global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click')
    await wrapper.get('.operation-form select').setValue('v1')
    await wrapper.get('.operation-form input[maxlength="500"]').setValue('请勿丢失这条备注')
    await wrapper.get('.operation-form input[type="number"]').setValue(8)
    for (const input of wrapper.findAll('.operation-form input[type="date"]')) await input.setValue('2026-10-20')
    await wrapper.get('[aria-label="发货记录"]').trigger('click')
    expect(wrapper.find('[aria-label="发货记录"] form').exists()).toBe(true)
    const assertRetained = () => {
      expect((wrapper.get('.operation-form select').element as HTMLSelectElement).value).toBe('v1')
      expect((wrapper.get('.operation-form input[maxlength="500"]').element as HTMLInputElement).value).toBe('请勿丢失这条备注')
      expect((wrapper.get('.operation-form input[type="number"]').element as HTMLInputElement).value).toBe('8')
      for (const input of wrapper.findAll('.operation-form input[type="date"]')) expect((input.element as HTMLInputElement).value).toBe('2026-10-20')
    }
    assertRetained()
    await wrapper.get('[aria-label="关闭发货弹窗"]').trigger('click')
    expect(wrapper.get('[role="alertdialog"]').text()).toContain('放弃未保存的修改')
    expect(wrapper.get('[role="alertdialog"] button').text()).toBe('继续填写')
    await wrapper.get('[role="alertdialog"]').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    await wrapper.get('[role="alertdialog"] button').trigger('click'); assertRetained()
    await wrapper.get('[aria-label="发货记录"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    await wrapper.get('[role="alertdialog"]').trigger('keydown', { key: 'Escape' }); assertRetained()
    await wrapper.findAll('.operation-form button').find(button => button.text() === '取消')!.trigger('click')
    await wrapper.get('[role="alertdialog"] .danger-button').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(createShipment).not.toHaveBeenCalled()
    await wrapper.get('.material-toolbar button').trigger('click')
    expect((wrapper.get('.operation-form select').element as HTMLSelectElement).value).toBe('')
    await wrapper.get('[aria-label="关闭发货弹窗"]').trigger('click')
    expect(wrapper.find('.operation-dialog').exists()).toBe(false)
    wrapper.unmount()
  })
  it('blocks closing during save and retains input after failed save', async () => {
    let failSave!: (error: Error) => void
    createShipment.mockImplementationOnce(() => new Promise((_resolve, reject) => { failSave = reject }))
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'today-shipments' }, global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises(); await wrapper.get('.material-toolbar button').trigger('click')
    await wrapper.get('.operation-form select').setValue('v1')
    await wrapper.get('.operation-form input[maxlength="500"]').setValue('失败后保留')
    await wrapper.get('.operation-form').trigger('submit')
    expect(wrapper.get('[aria-label="关闭发货弹窗"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.operation-form button').find(button => button.text() === '取消')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="发货记录"]').trigger('click')
    await wrapper.get('[aria-label="发货记录"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    failSave(new Error('offline')); await flushPromises()
    expect((wrapper.get('.operation-form input[maxlength="500"]').element as HTMLInputElement).value).toBe('失败后保留')
    expect(wrapper.text()).toContain('保存失败')
    await wrapper.get('[aria-label="关闭发货弹窗"]').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    wrapper.unmount()
  })
  it('requires explicit SKU selection for legacy pending records', async () => {
    const item = { id: 'legacy', productId: 'p1', productName: '旧父体', productCode: 'P-1', plannedShipDate: '2026-10-17', latestShipAt: '2026-10-20T15:59:00Z', quantity: 2, boxCount: 1, status: 'PENDING', version: 0 } as PendingShipment
    vi.mocked(dashboardApi.page).mockResolvedValueOnce({ items: [item], totalPages: 1, totalElements: 1, page: 0, size: 20 })
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' }, global: { stubs: { RouterLink: true, ShipmentProductImage: true } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '转到今日发货')!.trigger('click')
    expect(transferToToday).not.toHaveBeenCalled()
    expect(wrapper.get('.success-toast--error').text()).toContain('请先编辑补选')
    await wrapper.findAll('td button').find(button => button.text() === '编辑')!.trigger('click')
    expect((wrapper.get('.operation-form select').element as HTMLSelectElement).value).toBe('')
    await wrapper.get('.operation-form').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('请选择客户商品')
    wrapper.unmount()
  })
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
    expect(buttons.map(button => button.text())).toEqual(['发货', '编辑', '转为待发货', '删除'])
    await buttons[3]!.trigger('click')
    expect(archive).not.toHaveBeenCalled()
    await wrapper.get('[aria-label="关闭删除弹窗"]').trigger('click')
    expect(wrapper.find('[aria-labelledby="delete-shipment-title"]').exists()).toBe(false)
    await buttons[3]!.trigger('click')
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
    const item = { id: 'pending-1', productId: 'p1', variantId: 'v1', customerProduct: { id: 'v1', customerId: 'c1', customerName: '合成客户', productId: 'p1', variantId: 'v1', customerProductCode: 'CP-1', requirements: '' }, productName: '测试产品', plannedShipDate: '2026-10-17',
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
    expect(wrapper.get('input[placeholder="搜索客户、客户产品编码、SKU或备注"]')).toBeTruthy()
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
    await form.get('select').setValue('v1')
    expect(form.find('input[type="file"]').exists()).toBe(false)
    expect(form.find('textarea').exists()).toBe(false)
    expect(form.get('img').attributes('src')).toBe('https://example.com/product.png')
    await form.trigger('submit')
    await flushPromises()
    expect(createShipment).toHaveBeenCalledWith('today-shipments', expect.objectContaining({
      productId: 'p1', variantId: 'v1', customerProductId: 'v1', date: '2026-10-17', latestShipTime: '2026-10-17T23:59',
    }))
    expect(wrapper.text()).toContain('记录已创建')
  })
  it('automatically displays the product image for pending shipments too', async () => {
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-shipments' },
      global: { stubs: { RouterLink: true, AuthenticatedDashboardImage: true } } })
    await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.get('.operation-form')
    await form.get('select').setValue('v1')
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
