import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,afterEach,it,expect,vi} from 'vitest'
import PurchaseShipmentsDialog from './PurchaseShipmentsDialog.vue'
const h=vi.hoisted(()=>({order:vi.fn(),shipments:vi.fn(),shipment:vi.fn()}))
vi.mock('@/api/purchases',()=>({purchaseApi:h}))
vi.mock('@/api/purchaseManagement',()=>({purchaseManagementApi:h}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'synthetic'}}}}))
const order={id:'o1',orderNo:'SYN',supplierName:'合成',status:'SUBMITTED',version:2,lines:[{id:'l1',itemName:'合成商品',remainingQuantity:8,unit:'件'}]}
const wrappers:ReturnType<typeof mount>[]=[]
beforeEach(()=>{vi.clearAllMocks();sessionStorage.clear();h.order.mockResolvedValue(order);h.shipments.mockResolvedValue([{id:'s1',shippedOn:'2026-11-20',packageCount:1,trackingNumber:'SYN1',lines:[{orderLineId:'l1',quantity:4,receivedQuantity:2}]}]);h.shipment.mockResolvedValue([]);HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function setup(writable=true){const w=mount(PurchaseShipmentsDialog,{props:{id:'o1',writable},global:{stubs:{SingleFileUpload:true,PurchaseItemImage:true}}});wrappers.push(w);await flushPromises();return w}
it('subtracts outstanding batch allocations and rejects over-allocation',async()=>{const w=await setup();await w.findAll('button').find(b=>b.text()==='登记下一批发货')!.trigger('click');const q=w.get('[aria-label="合成商品本批发出"]');expect(q.attributes('max')).toBe('6');await q.setValue('7');await w.get('form').trigger('submit');expect(h.shipment).not.toHaveBeenCalled();expect(w.text()).toContain('未分配余量')})
it('reuses original shipment request across network failure and reopening',async()=>{const first=await setup();await first.findAll('button').find(b=>b.text()==='登记下一批发货')!.trigger('click');await first.get('[aria-label="合成商品本批发出"]').setValue('3');h.shipment.mockRejectedValueOnce(new Error('timeout'));await first.get('form').trigger('submit');await flushPromises();const original=JSON.parse(JSON.stringify(h.shipment.mock.calls[0]![1]));first.unmount();wrappers.pop();const second=await setup();expect(second.get('fieldset').attributes('disabled')).toBeDefined();await second.get('form').trigger('submit');await flushPromises();expect(h.shipment.mock.calls[1]![1]).toEqual(original)})
it('a reader can view all batches but cannot create shipment records',async()=>{const w=await setup(false);expect(w.text()).toContain('第 1 批');expect(w.text()).not.toContain('登记下一批发货');expect(w.find('form').exists()).toBe(false)})
