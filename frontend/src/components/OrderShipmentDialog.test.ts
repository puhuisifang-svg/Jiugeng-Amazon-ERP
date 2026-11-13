import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
const state=vi.hoisted(()=>({allowed:true}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>state.allowed}}))
vi.mock('@/api/orders',()=>({ordersApi:{get:vi.fn(),arrange:vi.fn()}}))
import {ordersApi,type CustomerOrder} from '@/api/orders'
import OrderShipmentDialog from './OrderShipmentDialog.vue'
const order = { id: 'o1', orderNumber: 'SYN-ORDER', customerName: '合成客户', status: 'CONFIRMED', version: 2, details: { source: 'OFFLINE', externalNumber:'SYN-CLIENT-ORDER', recipient: '合成人', phone: 'SYN-PHONE', address: '合成地址', invoiceRequired: true, orderDate: '2026-10-26', deliveryDate: '2026-10-27', lines: [] }, lines: [{ id: 'l1', product: { customerProductCode: 'CLIENT-ORDER' }, details: { name: '合成SKU', quantity: 10.5, unit: '米', deliveryDate: '2026-10-27', remark: '' }, allocatedQuantity: 2, shippedQuantity: 3 }], invoiceTitle: { invoiceTitle: '合成抬头' }, totalAmount: '101.00' } as unknown as CustomerOrder

beforeEach(()=>{vi.resetAllMocks();state.allowed=true;vi.mocked(ordersApi.get).mockResolvedValue(order);vi.mocked(ordersApi.arrange).mockResolvedValue([])})
it('prefills remaining quantities and recipient, then saves through the order association API',async()=>{
 const w=mount(OrderShipmentDialog,{props:{orderId:'o1'},global:{stubs:{CustomerProductImage:true}}});await flushPromises()
 expect((w.get('[aria-label="本次数量"]').element as HTMLInputElement).value).toBe('5.5')
 expect(w.text()).toContain('CLIENT-ORDER')
 await w.get('[aria-label="本次数量"]').setValue(6);await w.get('form').trigger('submit');expect(ordersApi.arrange).not.toHaveBeenCalled()
 await w.get('[aria-label="本次数量"]').setValue(1.25);await w.get('form').trigger('submit');await flushPromises()
 expect(ordersApi.arrange).toHaveBeenCalledWith('o1',expect.objectContaining({version:2,recipient:'合成人',items:[expect.objectContaining({lineId:'l1',quantity:1.25})]}),expect.any(String))
 expect(w.emitted('saved')).toHaveLength(1);w.unmount()
})
it('retains the original request and disables edits after an uncertain failure',async()=>{
 vi.mocked(ordersApi.arrange).mockRejectedValueOnce(new Error('网络异常'))
 const w=mount(OrderShipmentDialog,{props:{orderId:'o1'},global:{stubs:{CustomerProductImage:true}}});await flushPromises()
 await w.get('form').trigger('submit');await flushPromises();expect(w.get('[role=alert]').text()).toContain('网络异常')
 expect(w.get('[aria-label="本次数量"]').attributes('disabled')).toBeDefined()
 await w.get('form').trigger('submit');await flushPromises()
 expect(vi.mocked(ordersApi.arrange).mock.calls[1]).toEqual(vi.mocked(ordersApi.arrange).mock.calls[0]);w.unmount()
})
it('rejects unauthorized access and non-confirmed source orders',async()=>{
 state.allowed=false;const w=mount(OrderShipmentDialog,{props:{orderId:'o1'}});await flushPromises();expect(ordersApi.get).not.toHaveBeenCalled();expect(w.text()).toContain('没有安排订单发货的权限');w.unmount()
 state.allowed=true;vi.mocked(ordersApi.get).mockResolvedValue({...order,status:'DRAFT'});const draft=mount(OrderShipmentDialog,{props:{orderId:'o1'}});await flushPromises();expect(draft.text()).toContain('请先确认客户订单');expect(ordersApi.arrange).not.toHaveBeenCalled();draft.unmount()
})
