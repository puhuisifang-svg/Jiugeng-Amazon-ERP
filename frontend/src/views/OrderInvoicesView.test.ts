import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const state = vi.hoisted(() => ({ write: true, query: {} as Record<string,string> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: state.query }), useRouter: () => ({ push: vi.fn() }), onBeforeRouteLeave: vi.fn(), RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => state.write } }))
vi.mock('@/api/orders', () => ({ ordersApi: { invoices: vi.fn(), invoice: vi.fn(), updateInvoice: vi.fn() } }))
import { ordersApi, type OrderInvoice } from '@/api/orders'
import OrderInvoicesView from './OrderInvoicesView.vue'
const invoice = { id: 'i1', orderId: 'o1', version: 0, invoiced: false, invoiceNumber: '', snapshot: { orderNumber: 'SYN-ORDER', customerName: '合成客户', invoiceTitle: { invoiceTitle: '合成抬头' }, netAmount: '30.00', taxAmount: '0.30', totalAmount: '30.30', lines: [{ id: 'l1', details: { name: '商品甲', quantity: 1, unit: '米' } }, { id: 'l2', details: { name: '商品乙', quantity: 2, unit: '件' } }] } } as unknown as OrderInvoice
beforeEach(() => { vi.clearAllMocks(); state.write = true; state.query = {}; vi.mocked(ordersApi.invoices).mockResolvedValue({ items: [invoice], totalCount: 1, page: 0, size: 20 }); vi.mocked(ordersApi.invoice).mockResolvedValue(invoice) })
describe('订单发票', () => {
  it('keeps invoice edits on cancellation and closes only after explicit discard', async () => {
    HTMLDialogElement.prototype.showModal = function () { this.open = true }
    HTMLDialogElement.prototype.close = function () { this.open = false }
    const w = mount(OrderInvoicesView, { global: { stubs: { OrderDocuments: true } } }); await flushPromises()
    await w.findAll('button').find(b => b.text() === '详情')!.trigger('click')
    const input = w.get('.invoice-card input[type="text"], .invoice-card input:not([type])')
    await input.setValue('SYN-INVOICE')
    await w.get('[aria-label="订单发票详情"]').trigger('keydown', { key: 'Escape' })
    expect(w.get('[role="alertdialog"]').text()).toContain('要放弃本次填写吗')
    await w.get('.unsaved-keep').trigger('click')
    expect(input.element).toHaveProperty('value', 'SYN-INVOICE')
    await w.get('[aria-label="订单发票详情"]').trigger('keydown', { key: 'Escape' })
    await w.get('.unsaved-discard').trigger('click')
    expect(w.find('.invoice-card').exists()).toBe(false)
    expect(ordersApi.updateInvoice).not.toHaveBeenCalled()
    w.unmount()
  })
  it('shows one invoice with multiple items, without shipping or logistics controls', async () => {
    const w = mount(OrderInvoicesView, { global: { stubs: { OrderDocuments: true } } }); await flushPromises()
    await w.findAll('button').find(b => b.text() === '详情')!.trigger('click')
    expect(w.text()).toContain('商品甲'); expect(w.text()).toContain('商品乙'); expect(w.text()).toContain('30.30')
    expect(w.text()).not.toMatch(/物流|发货/); w.unmount()
  })
  it('opens linked invoices and locks issued invoice contents', async () => {
    state.query = { invoiceId: 'i1' }; vi.mocked(ordersApi.invoice).mockResolvedValue({ ...invoice, invoiced: true })
    const w = mount(OrderInvoicesView, { global: { stubs: { OrderDocuments: true } } }); await flushPromises()
    expect(ordersApi.invoice).toHaveBeenCalledWith('i1'); expect(w.get('fieldset').attributes('disabled')).toBeDefined()
    expect(w.findAll('button').some(b => b.text() === '保存')).toBe(false); w.unmount()
  })
})
