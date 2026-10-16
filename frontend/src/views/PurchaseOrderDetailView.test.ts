import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { purchaseApi } from '@/api/purchases'
import { session } from '@/state/session'
import PurchaseOrderDetailView from './PurchaseOrderDetailView.vue'

vi.mock('@/api/purchases', () => ({ purchaseApi: { detail: vi.fn(), submit: vi.fn(), cancel: vi.fn(), receive: vi.fn() } }))

afterEach(() => vi.restoreAllMocks())

describe('PurchaseOrderDetailView', () => {
  it('shows lines, receipts and audit history in one workspace', async () => {
    vi.spyOn(session, 'canAccess').mockReturnValue(true)
    vi.mocked(purchaseApi.detail).mockResolvedValue({
      order: { id: 'o1', orderNo: 'PO-001', orderType: 'MATERIAL', supplierId: 's1', supplierName: '供应商A', status: 'PARTIALLY_RECEIVED', currency: 'CNY', version: 2, createdAt: '2026-10-16T08:00:00Z', updatedAt: '2026-10-16T09:00:00Z', lines: [{ id: 'l1', lineNo: 1, itemId: 'm1', itemCode: 'MAT-1', itemName: '纸箱', unit: '个', orderedQuantity: 10, receivedQuantity: 4, remainingQuantity: 6, unitPrice: 2 }] },
      receipts: [{ id: 'r1', orderId: 'o1', orderNo: 'PO-001', requestId: 'req-1', receivedAt: '2026-10-16T09:00:00Z', actorLoginName: 'buyer', createdAt: '2026-10-16T09:00:00Z', replayed: false, lines: [{ orderLineId: 'l1', itemCode: 'MAT-1', itemName: '纸箱', quantity: 4 }] }],
      history: [{ id: 'h1', action: 'PURCHASE_ORDER_CREATE', actorLoginName: 'buyer', occurredAt: '2026-10-16T08:00:00Z' }],
    })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/purchases/orders/:orderId', component: PurchaseOrderDetailView }] })
    await router.push('/purchases/orders/o1'); await router.isReady()
    const wrapper = mount(PurchaseOrderDetailView, { global: { plugins: [router] } }); await flushPromises()
    expect(wrapper.text()).toContain('PO-001'); expect(wrapper.text()).toContain('MAT-1'); expect(wrapper.text()).toContain('buyer'); expect(wrapper.text()).toContain('创建采购单')
  })
})
