import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,afterEach,it,expect,vi} from 'vitest'
import {ApiError} from '@/api/client'
import PurchaseReceiveFlow from './PurchaseReceiveFlow.vue'
const h=vi.hoisted(()=>({order:vi.fn(),receive:vi.fn(),shipments:vi.fn(),checks:vi.fn(),allowed:true}))
vi.mock('@/api/purchases',()=>({purchaseApi:h}))
vi.mock('@/api/purchaseManagement',()=>({purchaseManagementApi:h}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'synthetic'}},canAccess:()=>h.allowed}}))
const order={id:'o1',orderNo:'SYN-1',orderType:'MATERIAL',supplierName:'合成供应商',status:'SUBMITTED',lines:[{id:'l1',itemId:'m1',itemName:'合成纸箱',unit:'个',orderedQuantity:10,receivedQuantity:2,remainingQuantity:8}]}
const wrappers:ReturnType<typeof mount>[]=[]
beforeEach(()=>{vi.clearAllMocks();sessionStorage.clear();h.allowed=true;h.order.mockResolvedValue(JSON.parse(JSON.stringify(order)));h.receive.mockResolvedValue({});h.shipments.mockResolvedValue([]);h.checks.mockResolvedValue([]);HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function setup(){const w=mount(PurchaseReceiveFlow,{props:{id:'o1'},global:{stubs:{PurchaseItemImage:true}}});wrappers.push(w);await flushPromises();return w}
async function submit(w:ReturnType<typeof mount>){await w.findAll('button').find(b=>b.text().includes('确认本次收货')||b.text().includes('重试本次收货'))!.trigger('click');await flushPromises()}
it('validates empty, fractional count and excessive receipts before posting',async()=>{const w=await setup();await submit(w);expect(h.receive).not.toHaveBeenCalled();for(const value of ['1.5','9','-1']){await w.get('input[type=number]').setValue(value);await submit(w);expect(h.receive).not.toHaveBeenCalled()}await w.get('input[type=number]').setValue('3');await submit(w);expect(h.receive).toHaveBeenCalledWith('o1',expect.objectContaining({lines:[{orderLineId:'l1',quantity:3}]}));expect(w.emitted('completed')).toHaveLength(1)})
it('restores the identical uncertain receipt after remount and clears it only on success',async()=>{const first=await setup();h.receive.mockRejectedValueOnce(new Error('timeout'));await first.get('input[type=number]').setValue('3');await submit(first);const original=JSON.parse(JSON.stringify(h.receive.mock.calls[0]![1]));first.unmount();wrappers.pop();const second=await setup();expect(second.get('input[type=number]').attributes('disabled')).toBeDefined();await submit(second);expect(h.receive.mock.calls[1]![1]).toEqual(original);expect(sessionStorage.getItem('jiugeng.purchase.receipt.synthetic.o1')).toBeNull()})
it('keeps request for authentication failures because the result is still unknown',async()=>{const w=await setup();h.receive.mockRejectedValueOnce(new ApiError(401,'AUTH','会话过期'));await w.get('input[type=number]').setValue('1');await submit(w);expect(sessionStorage.getItem('jiugeng.purchase.receipt.synthetic.o1')).not.toBeNull();expect(w.text()).toContain('原请求')})
it('limits receipt to selected batch and sends that batch identity',async()=>{h.shipments.mockResolvedValue([{id:'s1',shippedOn:'2026-11-20',trackingNumber:'SYN',lines:[{orderLineId:'l1',quantity:4,receivedQuantity:2}]}]);const w=await setup();expect(w.get('input[type=number]').attributes('max')).toBe('2');await w.get('input[type=number]').setValue('3');await submit(w);expect(h.receive).not.toHaveBeenCalled();await w.get('input[type=number]').setValue('2');await submit(w);expect(h.receive).toHaveBeenCalledWith('o1',expect.objectContaining({shipmentId:'s1'}))})
it('refreshes remaining quantities after a definitive concurrency rejection and preserves user input',async()=>{const w=await setup();h.receive.mockRejectedValueOnce(new ApiError(409,'PURCHASE_OVER_RECEIPT','余量变化'));h.order.mockResolvedValue({...order,lines:[{...order.lines[0],remainingQuantity:1}]});await w.get('input[type=number]').setValue('2');await submit(w);expect(w.get('input[type=number]').attributes('max')).toBe('1');expect(w.get('input[type=number]').element).toHaveProperty('value','2');expect(sessionStorage.getItem('jiugeng.purchase.receipt.synthetic.o1')).toBeNull()})
it('failed reference loads block submission and allow retry',async()=>{h.shipments.mockRejectedValueOnce(new Error('批次读取失败'));const w=await setup();expect(w.find('input[type=number]').exists()).toBe(false);expect(w.text()).toContain('批次读取失败');await w.findAll('button').find(b=>b.text()==='重新读取')!.trigger('click');await flushPromises();expect(w.find('input[type=number]').exists()).toBe(true)})

it('shows two waybills as independent blocks and keeps the other draft after one receipt',async()=>{
 const batches=[{id:'s1',carrier:'测试快递',trackingNumber:'WAY1',shippedOn:'2026-11-27',packageCount:1,lines:[{orderLineId:'l1',quantity:4,receivedQuantity:0}]},{id:'s2',carrier:'测试快递',trackingNumber:'WAY2',shippedOn:'2026-11-27',packageCount:1,lines:[{orderLineId:'l1',quantity:4,receivedQuantity:0}]}]
 h.shipments.mockResolvedValue(batches);const w=await setup();expect(w.find('select').exists()).toBe(false);expect(w.findAll('.waybill-receipt-block')).toHaveLength(2)
 await w.get('[data-shipment-id="s1"] input[type=number]').setValue('2');await w.get('[data-shipment-id="s2"] input[type=number]').setValue('3')
 h.shipments.mockResolvedValue([{...batches[0],lines:[{orderLineId:'l1',quantity:4,receivedQuantity:2}]},batches[1]])
 await w.get('[data-shipment-id="s1"] .waybill-confirm').trigger('click');await flushPromises()
 expect(h.receive).toHaveBeenLastCalledWith('o1',expect.objectContaining({shipmentId:'s1',lines:[{orderLineId:'l1',quantity:2}]}))
 expect(w.emitted('completed')).toBeUndefined();expect(w.emitted('updated')).toHaveLength(1)
 expect(w.get('[data-shipment-id="s2"] input[type=number]').element).toHaveProperty('value','3')
 await w.get('[data-shipment-id="s2"] .waybill-confirm').trigger('click');await flushPromises()
 expect(h.receive).toHaveBeenLastCalledWith('o1',expect.objectContaining({shipmentId:'s2',lines:[{orderLineId:'l1',quantity:3}]}))
})
it('locks other waybills while a receipt is uncertain and retries only its original payload',async()=>{
 h.shipments.mockResolvedValue(['s1','s2'].map(id=>({id,trackingNumber:id,lines:[{orderLineId:'l1',quantity:4,receivedQuantity:0}]})))
 const w=await setup();h.receive.mockRejectedValueOnce(new Error('timeout'));await w.get('[data-shipment-id="s2"] input[type=number]').setValue('1');await w.get('[data-shipment-id="s2"] .waybill-confirm').trigger('click');await flushPromises()
 const original=h.receive.mock.calls[0]![1];expect(w.get('[data-shipment-id="s1"] .waybill-confirm').attributes('disabled')).toBeDefined()
 await w.get('[data-shipment-id="s2"] .waybill-confirm').trigger('click');await flushPromises();expect(h.receive.mock.calls[1]![1]).toEqual(original)
})

it('provides an explicit success result and keeps the receipt dialog mounted during refresh',async()=>{
 h.shipments.mockResolvedValue([{id:'s1',trackingNumber:'SYN1',lines:[{orderLineId:'l1',quantity:4,receivedQuantity:0}]}])
 const w=await setup();const originalDialog=w.get('[aria-label="按运单收货"]').element
 await w.get('input[type=number]').setValue('1')
 let resolve!:(value:unknown)=>void;h.order.mockImplementationOnce(()=>new Promise(r=>{resolve=r}))
 await w.get('.waybill-confirm').trigger('click');await flushPromises()
 expect(w.emitted('updated')?.[0]).toEqual(['收货成功，已登记本次 1 项商品。']);expect(w.get('[aria-label="按运单收货"]').element).toBe(originalDialog)
 resolve(order);await flushPromises()
})
it('shows visible failure feedback and preserves inputs for a rejected receipt',async()=>{
 const w=await setup();h.receive.mockRejectedValueOnce(new ApiError(409,'PURCHASE_OVER_RECEIPT','待收数量已变化'))
 await w.get('input[type=number]').setValue('2');await submit(w)
 expect(w.get('.success-toast--error').text()).toContain('收货失败：待收数量已变化');expect(w.emitted('completed')).toBeUndefined();expect(w.get('input[type=number]').element).toHaveProperty('value','2')
})
