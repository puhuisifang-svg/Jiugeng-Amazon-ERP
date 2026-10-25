import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { domainApi } from '@/api/domain'
import { purchaseApi, type PurchaseOrder, type PurchaseReferenceItem } from '@/api/purchases'
import { session } from '@/state/session'
import PurchasesView from './PurchasesView.vue'

vi.mock('@/api/domain', () => ({ domainApi: { suppliers: vi.fn() } }))
vi.mock('@/api/purchases', () => ({ purchaseApi: { orders: vi.fn(), create: vi.fn(), update: vi.fn(), references: vi.fn() } }))
const order: PurchaseOrder = {
  id: 'order-1', orderNo: 'PO-MATERIAL', orderType: 'MATERIAL', supplierId: 'supplier-1', supplierName: '测试供应商', status: 'DRAFT', currency: 'CNY', version: 1,
  lines: [{ id: 'line-1', lineNo: 1, itemId: 'material-1', itemCode: 'MAT-001', itemName: '纸箱', unit: 'PCS', orderedQuantity: 10, receivedQuantity: 0, remainingQuantity: 10, unitPrice: 3 }],
  createdAt: '2026-10-16T08:00:00Z', updatedAt: '2026-10-16T08:00:00Z',
}
const item: PurchaseReferenceItem = { id: 'material-1', code: 'MAT-001', name: '纸箱', unit: 'PCS', primarySupplier: true }
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(session, 'canAccess').mockReturnValue(true)
  vi.mocked(purchaseApi.orders).mockResolvedValue([order, { ...order, id: 'order-2', orderNo: 'PO-PRODUCT', orderType: 'PRODUCT' }])
  vi.mocked(purchaseApi.references).mockResolvedValue([item])
  vi.mocked(purchaseApi.create).mockResolvedValue(order)
  vi.mocked(purchaseApi.update).mockResolvedValue(order)
  vi.mocked(domainApi.suppliers).mockResolvedValue({ items: [{ id: 'supplier-1', name: '供应商一' }, { id: 'supplier-2', name: '供应商二' }] as never, page: 0, size: 100, totalCount: 2 })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })
async function setup(query = '') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/purchases/orders', component: PurchasesView }] })
  await router.push(`/purchases/orders${query}`); await router.isReady()
  const wrapper = mount(PurchasesView, { global: { plugins: [router] } }); wrappers.push(wrapper); await flushPromises()
  return { wrapper, router }
}
describe('purchase order workspace', () => {
  it('separates product and material orders and persists tab in the URL', async () => {
    const { wrapper, router } = await setup()
    expect(wrapper.text()).toContain('PO-PRODUCT'); expect(wrapper.text()).not.toContain('PO-MATERIAL')
    await wrapper.get('#tab-MATERIAL').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.query.type).toBe('MATERIAL')
    expect(wrapper.text()).toContain('PO-MATERIAL'); expect(wrapper.text()).not.toContain('PO-PRODUCT')
  })
  it('filters by item or supplier and keeps write actions hidden for read-only users', async () => {
    vi.mocked(session.canAccess).mockReturnValue(false)
    const { wrapper } = await setup('?type=MATERIAL')
    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('新建辅料采购单')
    await wrapper.get('[aria-label="搜索采购单"]').setValue('no-match')
    expect(wrapper.text()).not.toContain('PO-MATERIAL')
    await wrapper.get('[aria-label="搜索采购单"]').setValue('纸箱')
    expect(wrapper.text()).toContain('PO-MATERIAL')
  })
  it('creates the current tab type with delivery details and never mixes item types', async () => {
    const { wrapper } = await setup('?type=MATERIAL')
    await wrapper.get('.purchase-header button').trigger('click'); await flushPromises()
    await wrapper.get('fieldset select').setValue('supplier-1'); await flushPromises()
    expect(purchaseApi.references).toHaveBeenCalledWith('MATERIAL', 'supplier-1')
    await wrapper.get('[aria-label="采购项目 1"]').setValue('material-1')
    await wrapper.get('[aria-label="采购数量"]').setValue(4)
    await wrapper.get('[aria-label="采购单价"]').setValue(2.5)
    await wrapper.get('input[type="date"]').setValue('2026-11-01')
    await wrapper.get('input[placeholder="收货地址、联系人或内部仓库说明"]').setValue('测试固定仓')
    expect(wrapper.text()).toContain('合计 CNY 10.00')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(purchaseApi.create).toHaveBeenCalledWith(expect.objectContaining({ orderType: 'MATERIAL', expectedDeliveryDate: '2026-11-01', deliveryAddress: '测试固定仓', lines: [{ itemId: 'material-1', quantity: 4, unitPrice: 2.5 }] }))
  })
  it('ignores a stale supplier response and refuses invalid quantities', async () => {
    let resolveFirst!: (items: PurchaseReferenceItem[]) => void
    vi.mocked(purchaseApi.references).mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
      .mockResolvedValueOnce([{ ...item, id: 'other', code: 'OTHER' }])
    const { wrapper } = await setup('?type=MATERIAL')
    await wrapper.get('.purchase-header button').trigger('click'); await flushPromises()
    await wrapper.get('fieldset select').setValue('supplier-1')
    await wrapper.get('fieldset select').setValue('supplier-2'); await flushPromises()
    resolveFirst([item]); await flushPromises()
    expect(wrapper.get('[aria-label="采购项目 1"]').text()).toContain('OTHER')
    expect(wrapper.get('[aria-label="采购项目 1"]').text()).not.toContain('MAT-001')
    await wrapper.get('[aria-label="采购项目 1"]').setValue('other')
    await wrapper.get('[aria-label="采购数量"]').setValue(0)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(purchaseApi.create).not.toHaveBeenCalled(); expect(wrapper.text()).toContain('数量必须大于 0')
  })
  it('resets pagination when filters or tab change', async () => {
    vi.mocked(purchaseApi.orders).mockResolvedValue(Array.from({ length: 25 }, (_, i) => ({ ...order, id: `order-${i}`, orderNo: `PO-${i}` })))
    const { wrapper } = await setup('?type=MATERIAL')
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    expect(wrapper.get('.purchase-pagination').text()).toContain('2 / 2')
    await wrapper.get('[aria-label="搜索采购单"]').setValue('PO-24')
    expect(wrapper.get('.purchase-pagination').text()).toContain('1 / 1')
    expect(wrapper.text()).toContain('PO-24')
  })
})
