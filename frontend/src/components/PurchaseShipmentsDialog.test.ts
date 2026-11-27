import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,afterEach,it,expect,vi} from 'vitest'
import PurchaseShipmentsDialog from './PurchaseShipmentsDialog.vue'
const h=vi.hoisted(()=>({order:vi.fn(),shipments:vi.fn(),shipment:vi.fn(),editShipment:vi.fn()}))
vi.mock('@/api/purchases',()=>({purchaseApi:h}))
vi.mock('@/api/purchaseManagement',()=>({purchaseManagementApi:h}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'synthetic'}}}}))
const order={id:'o1',orderNo:'SYN',supplierName:'合成',status:'SUBMITTED',version:2,lines:[{id:'l1',itemName:'合成商品',remainingQuantity:8,unit:'件'}]}
const wrappers:ReturnType<typeof mount>[]=[]
beforeEach(()=>{vi.clearAllMocks();sessionStorage.clear();h.order.mockResolvedValue(order);h.shipments.mockResolvedValue([{id:'s1',shippedOn:'2026-11-20',packageCount:1,trackingNumber:'SYN1',version:0,lines:[{orderLineId:'l1',quantity:4,receivedQuantity:2}]}]);h.shipment.mockResolvedValue([]);HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function setup(writable=true){const w=mount(PurchaseShipmentsDialog,{props:{id:'o1',writable},global:{stubs:{SingleFileUpload:true,PurchaseItemImage:true}}});wrappers.push(w);await flushPromises();return w}
it('subtracts outstanding batch allocations and rejects over-allocation',async()=>{const w=await setup();await w.findAll('button').find(b=>b.text()==='新增运单')!.trigger('click');const q=w.get('[aria-label="合成商品运单发货数量"]');expect(q.attributes('max')).toBe('6');await q.setValue('7');await w.get('form').trigger('submit');expect(h.shipment).not.toHaveBeenCalled();expect(w.text()).toContain('未分配余量')})
it('reuses original shipment request across network failure and reopening',async()=>{const first=await setup();await first.findAll('button').find(b=>b.text()==='新增运单')!.trigger('click');await first.get('[aria-label="合成商品运单发货数量"]').setValue('3');await first.get('input[maxlength="64"]').setValue('SYN-NEW');h.shipment.mockRejectedValueOnce(new Error('timeout'));await first.get('form').trigger('submit');await flushPromises();const original=JSON.parse(JSON.stringify(h.shipment.mock.calls[0]![1]));first.unmount();wrappers.pop();const second=await setup();expect(second.get('fieldset').attributes('disabled')).toBeDefined();await second.get('form').trigger('submit');await flushPromises();expect(h.shipment.mock.calls[1]![1]).toEqual(original)})
it('a reader can view all batches but cannot create shipment records',async()=>{const w=await setup(false);expect(w.text()).toContain('运单 1');expect(w.text()).not.toContain('新增运单');expect(w.find('form').exists()).toBe(false)})

it('fills only the unallocated balance and shows product context instead of PO number',async()=>{
 const w=await setup();expect(w.get('.shipment-order-summary').text()).toContain('合成商品');expect(w.get('.shipment-order-summary').text()).not.toContain('SYN')
 await w.findAll('button').find(b=>b.text()==='新增运单')!.trigger('click')
 await w.findAll('button').find(b=>b.text()==='填入全部剩余数量')!.trigger('click')
 expect((w.get('[aria-label="合成商品运单发货数量"]').element as HTMLInputElement).value).toBe('6')
})
it('provides a clear first-batch entry for an empty order',async()=>{
 h.shipments.mockResolvedValue([]);const w=await setup();expect(w.text()).toContain('尚未登记运单')
 await w.findAll('button').find(b=>b.text()==='登记运单')!.trigger('click');expect(w.find('form').exists()).toBe(true)
})

it('edits the selected waybill and preserves received quantity lower bounds',async()=>{
 h.editShipment.mockResolvedValue([]);const w=await setup()
 await w.findAll('button').find(b=>b.text()==='编辑运单')!.trigger('click')
 const quantity=w.get('[aria-label="合成商品运单发货数量"]');expect(quantity.attributes('max')).toBe('10')
 await quantity.setValue('1');await w.get('form').trigger('submit');expect(h.editShipment).not.toHaveBeenCalled()
 await quantity.setValue('5');await w.get('form').trigger('submit');await flushPromises()
 expect(h.editShipment).toHaveBeenCalledWith('o1','s1',expect.objectContaining({shipmentVersion:0,lines:[{orderLineId:'l1',quantity:5}]}));expect(h.shipment).not.toHaveBeenCalled()
})

it('copies the carrier together with the tracking number',async()=>{
 const writeText=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText}})
 h.shipments.mockResolvedValue([{id:'s1',carrier:'合成物流',trackingNumber:'SYN1',lines:[]}])
 const w=await setup();await w.findAll('button').find(b=>b.text()==='复制物流信息')!.trigger('click');await flushPromises()
 expect(writeText).toHaveBeenCalledWith('合成物流 SYN1');expect(w.text()).toContain('已复制')
})
