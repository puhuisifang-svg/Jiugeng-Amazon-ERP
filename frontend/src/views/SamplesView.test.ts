import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SamplesView from './SamplesView.vue'
const h=vi.hoisted(()=>({parents:vi.fn(),upload:vi.fn(),list:vi.fn(),create:vi.fn(),update:vi.fn(),remove:vi.fn(),skus:vi.fn(),confirm:vi.fn(),write:true}))
vi.mock('@/api/catalog',()=>({catalogApi:{uploadImage:h.upload}}))
vi.mock('@/api/samples',()=>({samplesApi:h}))
vi.mock('@/api/domain',()=>({domainApi:{skus:h.skus}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.write}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:h.confirm}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const row={id:'s1',productId:'p1',variantId:'v1',productName:'Synthetic product',skuName:'Synthetic SKU',skuCode:'SYN-1',locationCode:'A-01',version:2,updatedAt:'2026-10-29T00:00:00Z'}
function view(){return mount(SamplesView,{global:{stubs:{TableViewport:{template:'<div><slot/></div>'},ListPagination:true,SuccessToast:true,UnsavedChangesDialog:{template:'<div>放弃填写确认</div>'}}}})}
const button=(w:ReturnType<typeof view>,name:string)=>w.findAll('button').find(b=>b.text()===name)!
beforeEach(()=>{vi.clearAllMocks();h.write=true;h.parents.mockResolvedValue([{id:'p1',name:'Synthetic product'}]);h.list.mockResolvedValue({items:[row],totalCount:1});h.skus.mockResolvedValue({items:[{productId:'p1',productName:'Synthetic product',variant:{id:'v1',skuCode:'SYN-1',variantName:'Synthetic SKU'}}],totalCount:1});h.confirm.mockResolvedValue(true);HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()})
describe('样品库位管理',()=>{
 it('filters by parent only on search and resets it',async()=>{const w=view();await flushPromises();await w.get('.filter-panel select').setValue('p1');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('',0,20,'p1');await button(w,'重置').trigger('click');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('',0,20,'');w.unmount()})
 it('creates a manual sample and retains photo after save failure',async()=>{const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click');await w.get('dialog form').trigger('submit');expect(w.text()).toContain('请输入样品名称');await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic manual');await w.get('.location-field input').setValue('M-01');h.upload.mockResolvedValue({id:'photo-1'});const upload=w.findAllComponents({name:'SingleFileUpload'}).find(c=>c.props('title')==='存放照片')!;await upload.props('upload')(new File(['image'],'synthetic.png',{type:'image/png'}));h.create.mockRejectedValue(new Error('暂时无法保存'));await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith(expect.objectContaining({productId:null,variantId:null,sampleName:'Synthetic manual',storageImageId:'photo-1'}));expect(w.text()).toContain('暂时无法保存');expect(w.text()).toContain('移除照片');await button(w,'移除照片').trigger('click');await w.get('dialog form').trigger('submit');expect(h.create).toHaveBeenLastCalledWith(expect.objectContaining({storageImageId:null}));w.unmount()})

 it('requires SKU selection and preserves inputs after failed save',async()=>{const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await flushPromises();await w.get('dialog form').trigger('submit');expect(h.create).not.toHaveBeenCalled();expect(w.text()).toContain('请先选择 SKU');await button(w,'选择产品 SKU').trigger('click');await flushPromises();await w.get('input[type=radio]').setValue();await button(w,'确认选择').trigger('click');await w.get('.location-field input').setValue('B-02');h.create.mockRejectedValue(new Error('该 SKU 已登记在此库位'));await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith(expect.objectContaining({productId:'p1',variantId:'v1',locationCode:'B-02'}));expect(w.text()).toContain('该 SKU 已登记在此库位');expect((w.get('.location-field input').element as HTMLInputElement).value).toBe('B-02');await button(w,'取消').trigger('click');expect(w.text()).toContain('放弃填写确认');w.unmount()})
 it('edits location with version and protects cancelled removal',async()=>{const w=view();await flushPromises();await button(w,'编辑').trigger('click');await flushPromises();expect(w.find('input[type=radio]').exists()).toBe(false);await w.get('.location-field input').setValue('C-03');h.update.mockResolvedValue({...row,locationCode:'C-03',version:3});await w.get('dialog form').trigger('submit');await flushPromises();expect(h.update).toHaveBeenCalledWith('s1',expect.objectContaining({productId:'p1',variantId:'v1',locationCode:'C-03',version:2}));h.confirm.mockResolvedValue(false);await button(w,'移除').trigger('click');await flushPromises();expect(h.remove).not.toHaveBeenCalled();w.unmount()})
 it('searches only on submit and hides writes for read-only users',async()=>{h.write=false;const w=view();await flushPromises();await w.get('.filter-panel input').setValue('SYN');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('SYN',0,20,'');expect(w.text()).not.toContain('新增样品');expect(w.text()).not.toContain('移除');w.unmount()})
})

it('keeps manual sample image separate from the storage photo',async()=>{
 const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click')
 await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic sample');await w.get('.location-field input').setValue('A-1')
 h.upload.mockResolvedValue({id:'sample-image'});const uploader=w.findAllComponents({name:'SingleFileUpload'}).find(c=>c.props('title')==='样品图片')!
 const file=new File(['image'],'synthetic.png',{type:'image/png'});await uploader.props('upload')(file)
 expect(h.upload).toHaveBeenCalledWith('SAMPLE_IMAGE',file)
 await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.create).toHaveBeenCalledWith(expect.objectContaining({sampleImageId:'sample-image',storageImageId:null}));w.unmount()
})

it('hides list SKU codes and shows a preview for the selected product',async()=>{
 const w=view();await flushPromises()
 expect(w.get('.sample-table').text()).not.toContain('SYN-1')
 expect(w.get('.sample-table').text()).not.toContain('SKU 编码')
 expect(w.get('.sample-table').text()).toContain('Synthetic SKU')
 await button(w,'新增样品').trigger('click')
 await button(w,'选择产品 SKU').trigger('click');await flushPromises()
 expect(w.findComponent({name:'CustomerSkuPicker'}).props('showCodes')).toBe(false)
 w.findComponent({name:'CustomerSkuPicker'}).vm.$emit('select',{productId:'p1',productName:'Synthetic product',variant:{id:'v1',variantName:'Synthetic SKU',skuCode:'SYN-1',imageUrl:'https://example.com/synthetic.png'}})
 await flushPromises()
 expect(w.get('.selected-sku-preview').text()).not.toContain('SYN-1')
 expect(w.get('.selected-sku-preview img').attributes('src')).toBe('https://example.com/synthetic.png')
 await w.get('.selected-sku-preview button').trigger('click')
 expect(w.findComponent({name:'PurchaseItemImage'}).findComponent({name:'AccessDialog'}).exists()).toBe(true)
 w.unmount()
})

it('displays sample remarks and names in editing without SKU codes',async()=>{
 h.list.mockResolvedValue({items:[{...row,remark:'合成备注\n第二行'}],totalCount:1})
 const w=view();await flushPromises()
 expect(w.get('.sample-remark').text()).toBe('合成备注\n第二行')
 await button(w,'编辑').trigger('click');await flushPromises()
 expect(w.get('.selected-sku').text()).toContain('Synthetic SKU')
 expect(w.get('.selected-sku').text()).toContain('父体：Synthetic product')
 expect(w.get('.selected-sku').text()).not.toContain('SYN-1')
 w.unmount()
})
it('removes the manual code input while preserving existing saved codes on edit',async()=>{
 h.list.mockResolvedValue({items:[{...row,productId:null,variantId:null,skuCode:'LEGACY'}],totalCount:1})
 const w=view();await flushPromises();await button(w,'编辑').trigger('click');await flushPromises()
 expect(w.get('dialog').text()).not.toContain('样品编码')
 expect(w.find('input[placeholder="选填，如供应商货号"]').exists()).toBe(false)
 await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.update).toHaveBeenCalledWith('s1',expect.objectContaining({sampleCode:'LEGACY'}))
 w.unmount()
})
