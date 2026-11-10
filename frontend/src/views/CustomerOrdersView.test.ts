import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const state = vi.hoisted(() => ({ write: true, route: { params: {} as Record<string,string>, query: {} }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => state.route, useRouter: () => ({ push: state.push }), onBeforeRouteLeave: vi.fn(), RouterLink: { template: '<a><slot /></a>' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => state.write } }))
vi.mock('@/api/orders', () => ({ ordersApi: { list: vi.fn(), get: vi.fn(), allocations: vi.fn(async () => []), history: vi.fn(async () => []), arrange: vi.fn(async () => []), transition: vi.fn(), save: vi.fn(), generateInvoice: vi.fn() } }))
import { ordersApi } from '@/api/orders'
import type { CustomerOrder } from '@/api/orders'
import CustomerOrdersView from './CustomerOrdersView.vue'
const order = { id: 'o1', orderNumber: 'SYN-ORDER', customerName: '合成客户', status: 'CONFIRMED', version: 2, details: { source: 'OFFLINE', externalNumber:'SYN-CLIENT-ORDER', recipient: '合成人', phone: 'SYN-PHONE', address: '合成地址', invoiceRequired: true, orderDate: '2026-10-26', deliveryDate: '2026-10-27', lines: [] }, lines: [{ id: 'l1', product: { customerProductCode: 'CLIENT-ORDER' }, details: { name: '合成SKU', quantity: 10.5, unit: '米', deliveryDate: '2026-10-27', remark: '' }, allocatedQuantity: 2, shippedQuantity: 3 }], invoiceTitle: { invoiceTitle: '合成抬头' }, totalAmount: '101.00' } as unknown as CustomerOrder
beforeEach(() => { vi.clearAllMocks(); state.write = true; state.route.params = {}; vi.mocked(ordersApi.list).mockResolvedValue({ items: [order], totalCount: 1, page: 0, size: 20 }); vi.mocked(ordersApi.get).mockResolvedValue(order) })
describe('客户订单', () => {
 it('shows the saved customer code in order details',async()=>{state.route.params={id:'o1'};const w=mount(CustomerOrdersView,{global:{stubs:{OrderDocuments:true}}});await flushPromises();expect(w.text()).toContain('CLIENT-ORDER');w.unmount()})
  it('shows order and multi-item progress; opening an order never creates shipments', async () => {
    const w = mount(CustomerOrdersView); await flushPromises()
    expect(w.text()).toContain('SYN-CLIENT-ORDER'); expect(w.text()).not.toContain('SYN-ORDER');expect(w.get('.order-delivery-date').text()).toBe('2026-10-27'); expect(w.text()).toContain('合成SKU')
    expect(ordersApi.arrange).not.toHaveBeenCalled()
    w.unmount()
  })
  it('validates explicit selection and remaining quantity before creating an arrangement', async () => {
    const w = mount(CustomerOrdersView); await flushPromises()
    await w.findAll('button').find(b => b.text() === '安排发货')!.trigger('click'); await flushPromises()
    const dialog = w.get('[aria-label="安排待发货"]')
    expect(dialog.text()).toContain('5.5 米'); expect(dialog.text()).toContain('CLIENT-ORDER')
    await dialog.get('form').trigger('submit'); expect(ordersApi.arrange).not.toHaveBeenCalled()
    await dialog.get('input[type=checkbox]').setValue(true)
    await dialog.get('[aria-label="本次数量"]').setValue('6')
    await dialog.get('form').trigger('submit'); expect(ordersApi.arrange).not.toHaveBeenCalled()
    await dialog.get('[aria-label="本次数量"]').setValue('1.25')
    await dialog.get('form').trigger('submit'); await flushPromises()
    expect(ordersApi.arrange).toHaveBeenCalledWith('o1', expect.objectContaining({ version: 2, items: [expect.objectContaining({ quantity: 1.25, lineId: 'l1' })] }), expect.any(String))
    expect(state.push).toHaveBeenCalledWith({ path: '/dashboard-maintenance/pending-shipments', query: { orderId: 'o1' } }); w.unmount()
  })
  it('hides write actions from readers and renders failures without success', async () => {
    state.write = false; vi.mocked(ordersApi.list).mockRejectedValue(new Error('加载失败'))
    const w = mount(CustomerOrdersView); await flushPromises()
    expect(w.text()).not.toContain('新增客户订单'); expect(w.text()).toContain('加载失败'); w.unmount()
  })
})

it('places order history after documents and supplies SKU references to the thumbnail',async()=>{
 state.route.params={id:'o1'};vi.mocked(ordersApi.get).mockResolvedValue({...order,status:'DRAFT',lines:order.lines.map(l=>({...l,product:{...l.product,productId:'p1',variantId:'v1'}}))})
 const w=mount(CustomerOrdersView,{global:{stubs:{OrderDocuments:{template:'<div>合成订单附件</div>'},CustomerProductImage:true}}});await flushPromises()
 expect(w.text().indexOf('变更记录')).toBeGreaterThan(w.text().indexOf('合成订单附件'))
 expect(w.get('.order-delete-button').text()).toBe('删除草稿');expect(w.findComponent({name:'CustomerProductImage'}).props()).toMatchObject({productId:'p1',variantId:'v1'});w.unmount()
})

it('renders thumbnails for every order item using its SKU reference',async()=>{
 vi.mocked(ordersApi.list).mockResolvedValue({items:[{...order,lines:order.lines.map(l=>({...l,product:{...l.product,productId:'p1',variantId:'v1'}}))}],totalCount:1,page:0,size:20})
 const w=mount(CustomerOrdersView,{global:{stubs:{CustomerProductImage:true}}});await flushPromises()
 expect(w.findComponent({name:'CustomerProductImage'}).props()).toMatchObject({productId:'p1',variantId:'v1'});w.unmount()
})
