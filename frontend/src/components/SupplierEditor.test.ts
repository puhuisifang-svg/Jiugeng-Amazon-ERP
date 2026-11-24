import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,describe,expect,it,vi} from 'vitest'
const h=vi.hoisted(()=>({supplier:vi.fn(),save:vi.fn(),impact:vi.fn(),duplicates:vi.fn(),uploadImage:vi.fn()}))
vi.mock('@/api/domain',()=>({domainApi:{supplier:h.supplier}}))
vi.mock('@/api/supplierManagement',()=>({supplierManagementApi:h}))
vi.mock('@/api/catalog',()=>({catalogApi:{uploadImage:h.uploadImage}}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'supplier-test'}}}}))
import SupplierEditor from './SupplierEditor.vue'
import {ApiError} from '@/api/client'
const item={id:'s1',name:'合成供应商',version:3,status:'ACTIVE',wechatId:'synthetic',categories:['MATERIAL','PRODUCT'],remark:'第一行\n第二行'}
const global={stubs:{SupplierImage:true,SupplierDetailDialog:true,SingleFileUpload:true}}
beforeEach(()=>{sessionStorage.clear();vi.clearAllMocks();h.supplier.mockResolvedValue(item);h.save.mockResolvedValue(item);h.duplicates.mockResolvedValue([]);h.impact.mockResolvedValue({counts:{PRODUCT:1},total:1,restrictedCount:0,drafts:1});HTMLDialogElement.prototype.showModal=function(){this.open=true};HTMLDialogElement.prototype.close=function(){this.open=false}})
describe('supplier editor recovery',()=>{
 it('blocks incomplete loading and asks before discarding a draft',async()=>{h.supplier.mockRejectedValueOnce(new Error('读取失败'));const w=mount(SupplierEditor,{props:{id:'s1'},global});await flushPromises();await w.get('form').trigger('submit');expect(h.save).not.toHaveBeenCalled();await w.findAll('button').find(b=>b.text()==='重新加载')!.trigger('click');await flushPromises();await w.get('input[required]').setValue('未保存名称');await w.get('button[aria-label="关闭"]').trigger('click');expect(w.text()).toContain('继续填写');expect(w.emitted('close')).toBeUndefined();w.unmount()})
 it('reuses the exact original request after a timeout',async()=>{h.save.mockRejectedValueOnce(new Error('timeout'));const w=mount(SupplierEditor,{props:{id:'s1'},global});await flushPromises();await w.get('form').trigger('submit');await flushPromises();const input=JSON.parse(JSON.stringify(h.save.mock.calls[0]![0]));expect(w.text()).toContain('保存结果待确认');await w.get('form').trigger('submit');await flushPromises();expect(h.save.mock.calls[1]![0]).toEqual(input);expect(sessionStorage.getItem('jiugeng.supplier.pending.supplier-test')).toBeNull();w.unmount()})
 it('requires impact confirmation for removed categories',async()=>{const w=mount(SupplierEditor,{props:{id:'s1'},global});await flushPromises();await w.get('input[value="PRODUCT"]').setValue(false);await w.get('form').trigger('submit');await flushPromises();expect(h.save).not.toHaveBeenCalled();expect(w.text()).toContain('采购草稿');const checks=w.findAll('input[type="checkbox"]');await checks[checks.length-1]!.setValue(true);await w.get('form').trigger('submit');await flushPromises();expect(h.save).toHaveBeenCalledWith(expect.objectContaining({id:'s1',impactConfirmed:true,supplier:expect.objectContaining({categories:['MATERIAL']})}));w.unmount()})
 it('resolves a conflict without losing the draft',async()=>{h.save.mockRejectedValueOnce(new ApiError(409,'SUPPLIER_VERSION_CONFLICT','版本冲突'));const w=mount(SupplierEditor,{props:{id:'s1'},global});await flushPromises();await w.get('input[required]').setValue('我的草稿');h.supplier.mockResolvedValue({...item,name:'其他人修改',version:4});await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('其他人修改');await w.findAll('button').find(b=>b.text()==='保留草稿，重新核对')!.trigger('click');await w.get('form').trigger('submit');await flushPromises();expect(h.save.mock.calls[1]![0].supplier).toEqual(expect.objectContaining({name:'我的草稿',version:4}));w.unmount()})
})

it('confirms a legacy save-and-continue request once and closes without starting another draft',async()=>{
 const input={requestId:'legacy-supplier-request',supplier:item,impactConfirmed:false}
 sessionStorage.setItem('jiugeng.supplier.pending.supplier-test',JSON.stringify({input,again:true}))
 const w=mount(SupplierEditor,{props:{resume:true},global});await flushPromises()
 expect(w.text()).not.toContain('保存并继续')
 await w.get('form').trigger('submit');await flushPromises()
 expect(h.save).toHaveBeenCalledWith(input);expect(h.save).toHaveBeenCalledTimes(1)
 expect(w.emitted('close')).toHaveLength(1);expect(sessionStorage.getItem('jiugeng.supplier.pending.supplier-test')).toBeNull();w.unmount()
})
