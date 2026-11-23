import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,expect,it,vi} from 'vitest'
const h=vi.hoisted(()=>({suppliers:vi.fn(),capabilities:vi.fn(),export:vi.fn()}))
vi.mock('@/api/domain',()=>({domainApi:{suppliers:h.suppliers}}))
vi.mock('@/api/supplierManagement',()=>({supplierManagementApi:h}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'supplier-page-test'}},canAccess:()=>true}}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
import SuppliersView from './SuppliersView.vue'
const item={id:'s1',name:'合成供应商',version:3,status:'ACTIVE',categories:['MATERIAL'],remark:'送货前联系仓库'}
const global={stubs:{SupplierImage:true,SupplierContactValue:true,SupplierEditor:{template:'<div>编辑器<button @click="$emit(\'saved\',{id:\'s1\'})">模拟已保存</button></div>'},SupplierDetailDialog:true,SupplierDeleteDialog:true,RowMoreActions:true}}
beforeEach(()=>{sessionStorage.clear();vi.clearAllMocks();h.capabilities.mockResolvedValue({closeoutVersion:1});h.suppliers.mockResolvedValue({items:[item],totalCount:101})})
it('uses server paging and applies filters only on query',async()=>{const w=mount(SuppliersView,{global});await flushPromises();expect(h.suppliers).toHaveBeenLastCalledWith(expect.objectContaining({page:0,size:20}));await w.get('input[type="search"]').setValue('名称');expect(h.suppliers).toHaveBeenCalledTimes(1);await w.get('form').trigger('submit');await flushPromises();expect(h.suppliers).toHaveBeenLastCalledWith(expect.objectContaining({q:'名称',page:0}));expect(w.text()).toContain('101');w.unmount()})
it('refetches the active filter after a save and keeps remarks expandable',async()=>{const w=mount(SuppliersView,{global});await flushPromises();await w.findAll('button').find(b=>b.text()==='查看备注')!.trigger('click');expect(w.text()).toContain('送货前联系仓库');await w.findAll('button').find(b=>b.text()==='编辑')!.trigger('click');h.suppliers.mockResolvedValue({items:[],totalCount:0});await w.findAll('button').find(b=>b.text()==='模拟已保存')!.trigger('click');await flushPromises();expect(w.text()).toContain('当前筛选或页码未包含该记录');w.unmount()})
it('labels retained results after refresh failure',async()=>{const w=mount(SuppliersView,{global});await flushPromises();h.suppliers.mockRejectedValue(new Error('网络异常'));await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('刷新失败，当前显示上次结果');expect(w.text()).toContain('合成供应商');w.unmount()})
it('disables writes against an old backend',async()=>{h.capabilities.mockRejectedValue(new Error('旧后端'));const w=mount(SuppliersView,{global});await flushPromises();expect(w.findAll('button').find(b=>b.text()==='新增供应商')!.attributes('disabled')).toBeDefined();expect(w.text()).toContain('旧后端');w.unmount()})
