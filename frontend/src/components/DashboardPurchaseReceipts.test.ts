import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,it,expect,vi} from 'vitest'
import DashboardPurchaseReceipts from './DashboardPurchaseReceipts.vue'
const h=vi.hoisted(()=>({page:vi.fn(),allowed:true}))
vi.mock('@/api/purchaseManagement',async original=>({...await original<object>(),purchaseManagementApi:h}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.allowed}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn(),RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'}}))
beforeEach(()=>{vi.clearAllMocks();h.allowed=true;h.page.mockResolvedValue({items:[{id:'o1',orderNo:'SYN',supplierName:'合成供应商',status:'SUBMITTED',lines:[]}],total:51})})
it('requests only open server-paged orders and uses the shared receiving flow',async()=>{const w=mount(DashboardPurchaseReceipts,{props:{visible:true},global:{stubs:{PurchaseReceiveFlow:{props:['id'],template:'<div>共用收货 {{id}}</div>'}}}});await flushPromises();expect(h.page).toHaveBeenCalledWith(expect.objectContaining({status:'OPEN',type:''}),0,10);expect(w.emitted('count')?.[0]).toEqual([51]);await w.findAll('button').find(b=>b.text()==='确认收货')!.trigger('click');expect(w.text()).toContain('共用收货 o1');w.findComponent({name:'ListPagination'}).vm.$emit('update:page',2);await flushPromises();expect(h.page).toHaveBeenLastCalledWith(expect.anything(),1,10);w.unmount()})
it('does not request purchase data without reading permission',async()=>{h.allowed=false;const w=mount(DashboardPurchaseReceipts,{props:{visible:true}});await flushPromises();expect(h.page).not.toHaveBeenCalled();w.unmount()})

it('shows purchase date and item notes and keeps success visible after receiving closes',async()=>{
 h.page.mockResolvedValue({items:[{id:'o1',orderNo:'SYN',createdAt:'2026-11-26T17:00:00Z',supplierName:'合成供应商',status:'SUBMITTED',remark:'整单包装要求',lines:[{id:'l1',itemName:'合成商品',remark:'逐件贴标',orderedQuantity:2,receivedQuantity:0,unit:'件'}]}],total:1})
 const w=mount(DashboardPurchaseReceipts,{props:{visible:true},global:{stubs:{PurchaseItemImage:true,PurchaseReceiveFlow:{name:'PurchaseReceiveFlow',props:['id'],template:'<div>共用收货</div>'}}}});await flushPromises()
 expect(w.get('thead').text()).toContain('采购日期 / 供应商');expect(w.findAll('th')).toHaveLength(6);expect(w.get('tbody a').text()).toBe('2026-11-27');expect(w.get('.purchase-group-note').text()).not.toContain('合成商品');expect(w.text()).toContain('2026-11-27');expect(w.text()).toContain('逐件贴标');expect(w.text()).toContain('整单包装要求')
 await w.findAll('button').find(b=>b.text()==='确认收货')!.trigger('click');const flow=w.findComponent({name:'PurchaseReceiveFlow'})
 flow.vm.$emit('updated','收货成功，已登记本次 1 项商品。');flow.vm.$emit('completed','收货成功，已登记本次 1 项商品。');await flushPromises()
 expect(w.findComponent({name:'PurchaseReceiveFlow'}).exists()).toBe(false);expect(w.get('.success-toast').text()).toContain('收货成功');w.unmount()
})
