import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SamplesView from './SamplesView.vue'
const h=vi.hoisted(()=>({list:vi.fn(),create:vi.fn(),update:vi.fn(),remove:vi.fn(),skus:vi.fn(),confirm:vi.fn(),write:true}))
vi.mock('@/api/samples',()=>({samplesApi:h}))
vi.mock('@/api/domain',()=>({domainApi:{skus:h.skus}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.write}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:h.confirm}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const row={id:'s1',productId:'p1',variantId:'v1',productName:'Synthetic product',skuName:'Synthetic SKU',skuCode:'SYN-1',locationCode:'A-01',version:2,updatedAt:'2026-10-29T00:00:00Z'}
function view(){return mount(SamplesView,{global:{stubs:{TableViewport:{template:'<div><slot/></div>'},ListPagination:true,SuccessToast:true,UnsavedChangesDialog:{template:'<div>放弃填写确认</div>'}}}})}
const button=(w:ReturnType<typeof view>,name:string)=>w.findAll('button').find(b=>b.text()===name)!
beforeEach(()=>{vi.clearAllMocks();h.write=true;h.list.mockResolvedValue({items:[row],totalCount:1});h.skus.mockResolvedValue({items:[{productId:'p1',productName:'Synthetic product',variant:{id:'v1',skuCode:'SYN-1',variantName:'Synthetic SKU'}}],totalCount:1});h.confirm.mockResolvedValue(true);HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()})
describe('样品库位管理',()=>{
 it('requires SKU selection and preserves inputs after failed save',async()=>{const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await flushPromises();await w.get('dialog form').trigger('submit');expect(h.create).not.toHaveBeenCalled();expect(w.text()).toContain('请先选择 SKU');await w.get('input[type=radio]').setValue();await w.get('.location-field input').setValue('B-02');h.create.mockRejectedValue(new Error('该 SKU 已登记在此库位'));await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith({productId:'p1',variantId:'v1',locationCode:'B-02'});expect(w.text()).toContain('该 SKU 已登记在此库位');expect((w.get('.location-field input').element as HTMLInputElement).value).toBe('B-02');await button(w,'取消').trigger('click');expect(w.text()).toContain('放弃填写确认');w.unmount()})
 it('edits location with version and protects cancelled removal',async()=>{const w=view();await flushPromises();await button(w,'编辑').trigger('click');await flushPromises();expect(w.find('input[type=radio]').exists()).toBe(false);await w.get('.location-field input').setValue('C-03');h.update.mockResolvedValue({...row,locationCode:'C-03',version:3});await w.get('dialog form').trigger('submit');await flushPromises();expect(h.update).toHaveBeenCalledWith('s1',{productId:'p1',variantId:'v1',locationCode:'C-03',version:2});h.confirm.mockResolvedValue(false);await button(w,'移除').trigger('click');await flushPromises();expect(h.remove).not.toHaveBeenCalled();w.unmount()})
 it('searches only on submit and hides writes for read-only users',async()=>{h.write=false;const w=view();await flushPromises();await w.get('.filter-panel input').setValue('SYN');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('SYN',0,20);expect(w.text()).not.toContain('新增样品');expect(w.text()).not.toContain('移除');w.unmount()})
})
