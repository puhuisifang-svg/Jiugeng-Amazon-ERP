import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { purchaseApi, type PurchaseOrderDetail } from '@/api/purchases'
import { session } from '@/state/session'
import PurchaseOrderDetailView from './PurchaseOrderDetailView.vue'

vi.mock('@/api/purchases', () => ({ purchaseApi: { detail: vi.fn(), submit: vi.fn(), cancel: vi.fn(), receive: vi.fn() } }))

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks(); vi.clearAllMocks() })

async function receiptSetup(allowed = true) {
  vi.spyOn(session, 'canAccess').mockReturnValue(allowed)
  vi.mocked(purchaseApi.detail).mockResolvedValue({ order: { id: 'o1', orderNo: 'PO-001', orderType: 'MATERIAL', supplierId: 's1', supplierName: '供应商A', status: 'PARTIALLY_RECEIVED', currency: 'CNY', version: 2, createdAt: '2026-10-16T08:00:00Z', updatedAt: '2026-10-16T09:00:00Z', expectedDeliveryDate: '2026-11-20', deliveryAddress: '测试仓', lines: [{ id: 'l1', lineNo: 1, itemId: 'm1', itemCode: 'MAT-1', itemName: '纸箱', unit: '个', orderedQuantity: 10, receivedQuantity: 4, remainingQuantity: 6, unitPrice: 2 }] }, receipts: [], history: [] } satisfies PurchaseOrderDetail)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/purchases/orders/:orderId', component: PurchaseOrderDetailView }] })
  await router.push('/purchases/orders/o1'); await router.isReady()
  const wrapper = mount(PurchaseOrderDetailView, { global: { plugins: [router] } }); wrappers.push(wrapper); await flushPromises()
  return wrapper
}

describe('PurchaseOrderDetailView', () => {
  it('keeps the same receipt payload after an uncertain network result', async () => {
    const wrapper = await receiptSetup()
    vi.mocked(purchaseApi.receive).mockRejectedValueOnce(new Error('network failure')).mockResolvedValueOnce({} as never)
    await wrapper.findAll('button').find(button => button.text() === '登记收货')!.trigger('click')
    expect((wrapper.get('input[type="number"]').element as HTMLInputElement).value).toBe('')
    await wrapper.get('input[type="number"]').setValue(2)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    const original = JSON.parse(JSON.stringify(vi.mocked(purchaseApi.receive).mock.calls[0]?.[1]))
    expect(wrapper.text()).toContain('重试本次收货')
    expect(wrapper.get('input[type="number"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(purchaseApi.receive).toHaveBeenNthCalledWith(2, 'o1', original)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
  it('validates empty and excessive receipts before sending and shows delivery details', async () => {
    const wrapper = await receiptSetup()
    expect(wrapper.text()).toContain('2026-11-20'); expect(wrapper.text()).toContain('测试仓')
    await wrapper.findAll('button').find(button => button.text() === '登记收货')!.trigger('click')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('至少填写一项')
    await wrapper.get('input[type="number"]').setValue(7)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(purchaseApi.receive).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('待收数量之间')
  })
  it('requires a closure reason and sends the current version', async () => {
    const wrapper = await receiptSetup()
    vi.mocked(purchaseApi.cancel).mockResolvedValue({} as never)
    await wrapper.findAll('button').find(button => button.text() === '关闭未收余量')!.trigger('click')
    expect(wrapper.text()).toContain('库存不会撤销')
    await wrapper.get('textarea').setValue('供应商不再供货')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(purchaseApi.cancel).toHaveBeenCalledWith('o1', 2, '供应商不再供货')
  })
  it('hides receipt and cancellation controls from read-only users', async () => {
    const wrapper = await receiptSetup(false)
    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('登记收货')
    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('关闭未收余量')
  })
  it('shows lines, receipts and audit history in one workspace', async () => {
    vi.spyOn(session, 'canAccess').mockReturnValue(true)
    vi.mocked(purchaseApi.detail).mockResolvedValue({
      order: { id: 'o1', orderNo: 'PO-001', orderType: 'MATERIAL', supplierId: 's1', supplierName: '供应商A', status: 'PARTIALLY_RECEIVED', currency: 'CNY', version: 2, createdAt: '2026-10-16T08:00:00Z', updatedAt: '2026-10-16T09:00:00Z', lines: [{ id: 'l1', lineNo: 1, itemId: 'm1', itemCode: 'MAT-1', itemName: '纸箱', unit: '个', orderedQuantity: 10, receivedQuantity: 4, remainingQuantity: 6, unitPrice: 2 }] },
      receipts: [{ id: 'r1', orderId: 'o1', orderNo: 'PO-001', requestId: 'req-1', receivedAt: '2026-10-16T09:00:00Z', actorLoginName: 'buyer', createdAt: '2026-10-16T09:00:00Z', replayed: false, lines: [{ orderLineId: 'l1', itemCode: 'MAT-1', itemName: '纸箱', quantity: 4 }] }],
      history: [{ id: 'h1', action: 'PURCHASE_ORDER_CREATE', actorLoginName: 'buyer', occurredAt: '2026-10-16T08:00:00Z' }],
    })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/purchases/orders/:orderId', component: PurchaseOrderDetailView }] })
    await router.push('/purchases/orders/o1'); await router.isReady()
    const wrapper = mount(PurchaseOrderDetailView, { global: { plugins: [router] } }); wrappers.push(wrapper); await flushPromises()
    expect(wrapper.text()).toContain('PO-001'); expect(wrapper.text()).toContain('MAT-1'); expect(wrapper.text()).toContain('buyer'); expect(wrapper.text()).toContain('创建采购单')
  })
})
