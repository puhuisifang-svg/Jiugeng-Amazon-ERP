import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { domainApi } from '@/api/domain'
import { purchaseApi, type PurchaseOrder } from '@/api/purchases'
import { session } from '@/state/session'
import PurchasesView from './PurchasesView.vue'

vi.mock('@/api/domain', () => ({
  domainApi: {
    suppliers: vi.fn(),
    skus: vi.fn(),
    materials: vi.fn(),
  },
}))
vi.mock('@/api/purchases', () => ({
  purchaseApi: {
    dashboard: vi.fn(),
    orders: vi.fn(),
    receipts: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    submit: vi.fn(),
    cancel: vi.fn(),
    receive: vi.fn(),
    references: vi.fn(),
  },
}))

const order: PurchaseOrder = {
  id: 'order-1', orderNo: 'PO-20261016-ABC123', orderType: 'MATERIAL', supplierId: 'supplier-1',
  supplierName: '测试供应商', status: 'SUBMITTED', currency: 'CNY', version: 1,
  lines: [{ id: 'line-1', lineNo: 1, itemId: 'material-1', itemCode: 'MAT-001', itemName: '纸箱', unit: 'PCS', orderedQuantity: 10, receivedQuantity: 2, remainingQuantity: 8, unitPrice: 3 }],
  createdAt: '2026-10-16T08:00:00Z', updatedAt: '2026-10-16T08:00:00Z',
}

function prepare() {
  vi.mocked(purchaseApi.orders).mockResolvedValue([order])
  vi.mocked(purchaseApi.receipts).mockResolvedValue([])
  vi.mocked(purchaseApi.dashboard).mockResolvedValue({ draft: 1, pendingReceipt: 2, partial: 3, completed: 4, recent: [order] })
  vi.mocked(purchaseApi.references).mockResolvedValue([])
  vi.mocked(domainApi.suppliers).mockResolvedValue({ items: [], page: 0, size: 100, totalCount: 0 })
  vi.mocked(domainApi.skus).mockResolvedValue({ items: [], page: 0, size: 100, totalCount: 0 })
  vi.mocked(domainApi.materials).mockResolvedValue({ items: [], mockData: false })
}

afterEach(() => vi.restoreAllMocks())

describe('PurchasesView', () => {
  it('renders the dashboard counts and recent purchase orders', async () => {
    prepare()
    vi.spyOn(session, 'canAccess').mockReturnValue(true)
    const wrapper = mount(PurchasesView, { props: { mode: 'dashboard' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()

    expect(wrapper.get('[aria-label="采购统计"]').text()).toContain('1草稿')
    expect(wrapper.get('[aria-label="采购统计"]').text()).toContain('2待收货')
    expect(wrapper.text()).toContain('PO-20261016-ABC123')
    expect(wrapper.text()).toContain('测试供应商')
  })

  it('keeps write and receipt actions hidden without their capabilities', async () => {
    prepare()
    vi.spyOn(session, 'canAccess').mockReturnValue(false)
    const wrapper = mount(PurchasesView, { props: { mode: 'orders' }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()

    expect(wrapper.text()).toContain('PO-20261016-ABC123')
    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('新建采购单')
    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('收货')
  })
})
