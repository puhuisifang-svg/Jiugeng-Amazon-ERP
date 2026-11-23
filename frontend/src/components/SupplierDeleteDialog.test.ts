import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,expect,it,vi} from 'vitest'
const h=vi.hoisted(()=>({supplier:vi.fn(),deleteSupplier:vi.fn(),unlinkArchivedSupplierVariant:vi.fn(),impact:vi.fn(),relations:vi.fn(),status:vi.fn()}))
vi.mock('@/api/domain',()=>({domainApi:h}))
vi.mock('@/api/supplierManagement',()=>({supplierManagementApi:h}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true}}))
import SupplierDeleteDialog from './SupplierDeleteDialog.vue'
const supplier={id:'s1',name:'合成供应商',version:3,status:'ACTIVE'} as const
const empty={counts:{PRODUCT:0,MATERIAL:0,PURCHASE:0},total:0,restrictedCount:0,drafts:0}
const button=(w:ReturnType<typeof mount>,text:string)=>w.findAll('button').find(b=>b.text()===text)!
beforeEach(()=>{vi.clearAllMocks();h.supplier.mockResolvedValue(supplier);h.impact.mockResolvedValue(empty);h.relations.mockResolvedValue({items:[],totalCount:0});HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
it('keeps deletion blocked for inaccessible dependencies and enables only after refresh',async()=>{h.impact.mockResolvedValue({...empty,total:1,restrictedCount:1});const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(button(w,'确认删除').attributes('disabled')).toBeDefined();expect(w.text()).toContain('受限关联');h.impact.mockResolvedValue(empty);await button(w,'刷新清单').trigger('click');await flushPromises();await button(w,'确认删除').trigger('click');await flushPromises();expect(h.deleteSupplier).toHaveBeenCalledWith('s1',3);w.unmount()})
it('never treats a failed impact read as no references',async()=>{h.impact.mockRejectedValue(new Error('检查失败'));const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(button(w,'确认删除').attributes('disabled')).toBeDefined();expect(w.text()).toContain('检查失败');w.unmount()})
it('requires confirmation before removing an archived relationship',async()=>{h.impact.mockResolvedValue({...empty,total:1,counts:{PRODUCT:1}});h.relations.mockResolvedValue({items:[{type:'PRODUCT',id:'v1',parentId:'p1',name:'历史子体',code:'SYN',archived:true,accessible:true}],totalCount:1});const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(w.find('a').exists()).toBe(false);await button(w,'解除历史关联').trigger('click');expect(h.unlinkArchivedSupplierVariant).not.toHaveBeenCalled();h.impact.mockResolvedValue(empty);await button(w,'确认解除').trigger('click');await flushPromises();expect(h.unlinkArchivedSupplierVariant).toHaveBeenCalledWith('s1','v1',3);w.unmount()})
