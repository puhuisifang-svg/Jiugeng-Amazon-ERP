import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import DashboardPurchaseReceipts from './DashboardPurchaseReceipts.vue'
const h = vi.hoisted(() => ({ orders: vi.fn(), order: vi.fn(), receive: vi.fn(), permissions: ['purchases.read', 'purchases.receive'], guard: vi.fn() }))
vi.mock('@/api/purchases', () => ({ purchaseApi: h }))
vi.mock('@/state/session', () => ({ session: { canAccess: (caps: string[]) => caps.every(cap => h.permissions.includes(cap)) } }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: h.guard, RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } }))
const order = { id: 'po1', orderNo: 'SYN-PO-1', supplierName: '合成供应商', orderType: 'MATERIAL', status: 'SUBMITTED', createdAt: '2026-11-10', lines: [{ id: 'l1', itemCode: 'SYN-SKU', itemName: '测试辅料', unit: '件', orderedQuantity: 10, receivedQuantity: 4, remainingQuantity: 6 }] }
beforeEach(() => {
  vi.clearAllMocks(); h.permissions = ['purchases.read', 'purchases.receive']
  h.orders.mockImplementation(async (status: string) => status === 'SUBMITTED' ? [order, { ...order, id: 'cancel', orderNo: '取消单', status: 'CANCELLED' }] : [])
  h.order.mockResolvedValue(structuredClone(order)); h.receive.mockResolvedValue({})
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
async function opened() {
  const w = mount(DashboardPurchaseReceipts, { props: { visible: true } }); await flushPromises()
  await w.findAll('button').find(b => b.text() === '确认收货')!.trigger('click'); await flushPromises()
  return w
}
it('only shows receivable orders, links purchase orders and filters the list', async () => {
  const w = mount(DashboardPurchaseReceipts, { props: { visible: true } }); await flushPromises()
  expect(h.orders.mock.calls.map(args => args[0])).toEqual(['SUBMITTED', 'PARTIALLY_RECEIVED'])
  expect(w.text()).not.toContain('取消单'); expect(w.get('a').attributes('href')).toBe('/purchases/orders/po1')
  expect(w.emitted('count')?.[0]).toEqual([1])
  await w.get('input[type=search]').setValue('不存在'); expect(w.text()).toContain('没有匹配'); w.unmount()
})
it('requires actual quantities, rejects over receipt and refreshes after partial receipt', async () => {
  const w = await opened()
  const confirm = () => w.findAll('button').find(b => b.text() === '确认本次收货')!
  await confirm().trigger('click'); expect(h.receive).not.toHaveBeenCalled(); expect(w.text()).toContain('请至少填写')
  await w.get('input[type=number]').setValue('7'); await confirm().trigger('click'); expect(h.receive).not.toHaveBeenCalled()
  await w.get('input[type=number]').setValue('2')
  h.orders.mockResolvedValue([])
  await confirm().trigger('click'); await flushPromises()
  expect(h.receive).toHaveBeenCalledWith('po1', expect.objectContaining({ requestId: expect.any(String), lines: [{ orderLineId: 'l1', quantity: 2 }] }))
  expect(w.find('dialog').exists()).toBe(false); expect(w.text()).toContain('暂无待接收'); w.unmount()
})
it('locks uncertain submissions and retries the exact original request without double posting', async () => {
  const w = await opened(); h.receive.mockRejectedValueOnce(new Error('network'))
  await w.get('input[type=number]').setValue('3')
  await w.findAll('button').find(b => b.text() === '确认本次收货')!.trigger('click'); await flushPromises()
  const payload = h.receive.mock.calls[0]![1]
  expect(w.get('input[type=number]').attributes('disabled')).toBeDefined()
  expect(h.guard.mock.calls[0]![0]()).toBe(false)
  await w.findAll('button').find(b => b.text() === '重试本次收货')!.trigger('click'); await flushPromises()
  expect(h.receive.mock.calls[1]![1]).toEqual(payload); expect(h.guard.mock.calls[0]![0]()).toBe(true); w.unmount()
})
it('shows server rejection and refreshes quantities for concurrent receipt correction', async () => {
  const w = await opened(); h.receive.mockRejectedValueOnce(new ApiError(409, 'OVER', '剩余数量已变化'))
  h.order.mockResolvedValue({ ...order, lines: [{ ...order.lines[0], remainingQuantity: 1 }] })
  await w.get('input[type=number]').setValue('3')
  await w.findAll('button').find(b => b.text() === '确认本次收货')!.trigger('click'); await flushPromises()
  expect(w.text()).toContain('剩余数量已变化'); expect(w.get('input[type=number]').attributes('max')).toBe('1')
  expect(w.get('input[type=number]').attributes('disabled')).toBeUndefined(); w.unmount()
})
it('does not fetch without read permission or allow receipt with read-only permission', async () => {
  h.permissions = []
  const w = mount(DashboardPurchaseReceipts, { props: { visible: true } }); await flushPromises()
  expect(h.orders).not.toHaveBeenCalled(); expect(w.text()).toContain('暂无采购查看权限'); w.unmount()
  h.permissions = ['purchases.read']
  const readOnly = mount(DashboardPurchaseReceipts, { props: { visible: true } }); await flushPromises()
  expect(readOnly.text()).toContain('无收货权限'); expect(readOnly.findAll('button').some(b => b.text() === '确认收货')).toBe(false); readOnly.unmount()
})
it('keeps request errors visible and can reload after a failed list request', async () => {
  h.orders.mockRejectedValueOnce(new Error('offline'))
  const w = mount(DashboardPurchaseReceipts, { props: { visible: true } }); await flushPromises()
  expect(w.text()).toContain('加载失败')
  await w.setProps({ visible: false }); await w.setProps({ visible: true }); await flushPromises()
  expect(w.text()).toContain('SYN-PO-1'); expect(w.text()).not.toContain('加载失败'); w.unmount()
})

it('loads count on mount even before the purchase tab is opened',async()=>{
 const w=mount(DashboardPurchaseReceipts,{props:{visible:false}});await flushPromises()
 expect(h.orders).toHaveBeenCalledTimes(2);expect(w.emitted('count')?.[0]).toEqual([1])
 expect(w.findComponent({name:'ListPagination'}).exists()).toBe(false);w.unmount()
})
