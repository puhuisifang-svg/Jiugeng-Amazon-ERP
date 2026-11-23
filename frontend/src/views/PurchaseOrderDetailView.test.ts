import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,it,expect,vi} from 'vitest'
import PurchaseOrderDetailView from './PurchaseOrderDetailView.vue'
const h=vi.hoisted(()=>({order:vi.fn(),cancel:vi.fn(),checks:vi.fn(),history:vi.fn(),receipts:vi.fn(),shipments:vi.fn(),allowed:true}))
vi.mock('@/api/purchases',()=>({purchaseApi:h}))
vi.mock('@/api/purchaseManagement',()=>({purchaseManagementApi:h}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.allowed}}))
vi.mock('vue-router',()=>({useRoute:()=>({params:{orderId:'o1'}}),onBeforeRouteLeave:vi.fn(),RouterLink:{template:'<a><slot/></a>'}}))
const order={id:'o1',orderNo:'SYN-1',orderType:'MATERIAL',supplierId:'s1',supplierName:'合成供应商',status:'SUBMITTED',version:2,currency:'CNY',createdAt:'2026-11-20',lines:[{id:'l1',itemId:'m1',itemName:'合成物品',unit:'个',orderedQuantity:10,receivedQuantity:0,remainingQuantity:10,unitPrice:0.1234,snapshotSaved:true}]}
beforeEach(()=>{vi.clearAllMocks();h.allowed=true;h.order.mockResolvedValue(order);h.checks.mockResolvedValue([]);h.history.mockResolvedValue({items:[],total:0});h.receipts.mockResolvedValue({items:[],total:0});h.shipments.mockResolvedValue([]);h.cancel.mockResolvedValue({});HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
async function setup(action?:'cancel'){const w=mount(PurchaseOrderDetailView,{props:{embedded:true,...(action?{action}:{})},global:{stubs:{PurchaseItemImage:true,PurchaseRelatedItem:true,ProductHistoryTable:true}}});await flushPromises();return w}
it('shows retained images and full unit price without eagerly loading history',async()=>{const w=await setup();expect(w.text()).toContain('0.1234');expect(w.text()).toContain('下单留存');expect(h.history).not.toHaveBeenCalled();await w.findAll('button').find(b=>b.text()==='变更历史')!.trigger('click');await flushPromises();expect(h.history).toHaveBeenCalledWith('o1',0,20);w.unmount()})
it('requires a reason and uses the latest version for closing remainder',async()=>{const w=await setup('cancel');const confirm=w.findAll('button').find(b=>b.text()==='确认关闭')!;expect(confirm.attributes('disabled')).toBeDefined();await w.get('textarea').setValue('合成测试关闭');await confirm.trigger('click');await flushPromises();expect(h.cancel).toHaveBeenCalledWith('o1',2,'合成测试关闭');expect(w.emitted('completed')).toHaveLength(1);w.unmount()})
it('keeps read-only detail free of receipt and cancel actions',async()=>{h.allowed=false;const w=await setup();expect(w.text()).not.toContain('登记收货');expect(w.text()).not.toContain('关闭未收余量');w.unmount()})

it('shows the shipment waybill on its receipt record',async()=>{h.receipts.mockResolvedValue({items:[{receipt:{id:"r1",receivedAt:"2026-11-22",actorLoginName:"synthetic",lines:[]},shipmentId:"sh1"}],total:1});h.shipments.mockResolvedValue([{id:"sh1",shippedOn:"2026-11-21",carrier:"合成物流",trackingNumber:"SYN-WAYBILL"}]);const w=await setup();await w.findAll("button").find(b=>b.text()==="收货记录")!.trigger("click");await flushPromises();expect(w.text()).toContain("合成物流 · SYN-WAYBILL");w.unmount()})
