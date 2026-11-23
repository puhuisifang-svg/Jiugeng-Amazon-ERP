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
