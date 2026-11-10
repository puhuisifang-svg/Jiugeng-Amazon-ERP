import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,expect,it,vi} from 'vitest'
import SupplierDeleteDialog from './SupplierDeleteDialog.vue'
const h=vi.hoisted(()=>({supplier:vi.fn(),supplierDependencies:vi.fn(),deleteSupplier:vi.fn(),disableSupplier:vi.fn(),unlinkArchivedSupplierVariant:vi.fn()}))
vi.mock('@/api/domain',()=>({domainApi:h}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true}}))
const supplier={id:'s1',name:'合成供应商',version:3,status:'ACTIVE'} as const
beforeEach(()=>{vi.clearAllMocks();h.supplier.mockResolvedValue(supplier);h.supplierDependencies.mockResolvedValue([]);HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
it('lists exact edit links, blocks deletion and enables it only after a successful refresh',async()=>{
 h.supplierDependencies.mockResolvedValue([{type:'PRODUCT',id:'v1',parentId:'p1',code:'SYN-SKU',name:'合成SKU',accessible:true}])
 const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(w.get('a').attributes('href')).toBe('/products?editProduct=p1&variantId=v1');expect(w.get('a').attributes('target')).toBe('_blank')
 expect(w.get('.supplier-confirm-delete').attributes('disabled')).toBeDefined();expect(h.deleteSupplier).not.toHaveBeenCalled()
 h.supplierDependencies.mockResolvedValue([]);await w.findAll('button').find(b=>b.text()==='刷新清单')!.trigger('click');await flushPromises();await w.get('.supplier-confirm-delete').trigger('click');await flushPromises();expect(h.deleteSupplier).toHaveBeenCalledWith('s1',3);w.unmount()
})
it('explains historical purchase restrictions and preserves errors without enabling deletion',async()=>{
 h.supplierDependencies.mockResolvedValue([{type:'PURCHASE',id:'po1',code:'SYN-PO',name:'CANCELLED',accessible:true}]);const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(w.text()).toContain('未收货的单据可在采购列表删除');expect(w.text()).toContain('停用供应商')
 h.supplierDependencies.mockRejectedValue(new Error('检查失败'));await w.findAll('button').find(b=>b.text()==='刷新清单')!.trigger('click');await flushPromises();expect(w.get('.supplier-confirm-delete').attributes('disabled')).toBeDefined();expect(w.text()).toContain('检查失败');w.unmount()
})

it('offers confirmed unlinking instead of a broken edit link for archived products',async()=>{
 h.supplierDependencies.mockResolvedValue([{type:'PRODUCT',id:'v1',parentId:'p1',code:'SYN-OLD',name:'合成历史SKU',accessible:true,archived:true}]);const w=mount(SupplierDeleteDialog,{props:{supplier}});await flushPromises();expect(w.find('a').exists()).toBe(false);expect(w.text()).toContain('已删除');await w.findAll('button').find(b=>b.text()==='解除历史关联')!.trigger('click');expect(h.unlinkArchivedSupplierVariant).not.toHaveBeenCalled();h.supplierDependencies.mockResolvedValue([]);await w.findAll('button').find(b=>b.text()==='确认解除')!.trigger('click');await flushPromises();expect(h.unlinkArchivedSupplierVariant).toHaveBeenCalledWith('s1','v1',3);expect(w.text()).toContain('没有关联记录');w.unmount()
})
