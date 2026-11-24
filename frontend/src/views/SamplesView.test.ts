import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SamplesView from './SamplesView.vue'
import { catalogApi } from '@/api/catalog'
import { ApiError } from '@/api/client'
const h=vi.hoisted(()=>({capabilities:vi.fn(),locations:vi.fn(),duplicates:vi.fn(),detail:vi.fn(),history:vi.fn(),selection:vi.fn(),restore:vi.fn(),export:vi.fn(),parents:vi.fn(),upload:vi.fn(),list:vi.fn(),create:vi.fn(),update:vi.fn(),remove:vi.fn(),skus:vi.fn(),confirm:vi.fn(),write:true}))
vi.mock('@/api/catalog',()=>({catalogApi:{uploadImage:h.upload,externalImageBlob:vi.fn()}}))
vi.mock('@/api/samples',async importOriginal=>({...await importOriginal<typeof import('@/api/samples')>(),samplesApi:h}))
vi.mock('@/api/domain',()=>({domainApi:{skus:h.skus}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.write}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:h.confirm}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const row={id:'s1',productId:'p1',variantId:'v1',productName:'Synthetic product',skuName:'Synthetic SKU',skuCode:'SYN-1',locationCode:'A-01',version:2,updatedAt:'2026-10-29T00:00:00Z'}
function view(){return mount(SamplesView,{global:{stubs:{RowMoreActions:{template:'<span><slot/></span>'},TableViewport:{template:'<div><slot/></div>'},ListPagination:true,SuccessToast:true,UnsavedChangesDialog:{template:'<div>放弃填写确认</div>'}}}})}
const button=(w:ReturnType<typeof view>,name:string)=>w.findAll('button').find(b=>b.text()===name)!
beforeEach(()=>{vi.resetAllMocks();URL.createObjectURL=vi.fn(()=>'blob:synthetic-controlled');URL.revokeObjectURL=vi.fn();vi.mocked(catalogApi.externalImageBlob).mockResolvedValue(new Blob(['synthetic-image']));sessionStorage.clear();h.write=true;h.capabilities.mockResolvedValue({closeoutVersion:1});h.locations.mockResolvedValue(['A-01']);h.duplicates.mockResolvedValue([]);h.create.mockResolvedValue(row);h.update.mockResolvedValue(row);h.parents.mockResolvedValue([{id:'p1',name:'Synthetic product'}]);h.list.mockResolvedValue({items:[row],totalCount:1});h.skus.mockResolvedValue({items:[{productId:'p1',productName:'Synthetic product',variant:{id:'v1',skuCode:'SYN-1',variantName:'Synthetic SKU'}}],totalCount:1});h.confirm.mockResolvedValue(true);HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()})
describe('样品库位管理',()=>{
 it('filters by parent only on search and resets it',async()=>{const w=view();await flushPromises();await w.get('.filter-panel select').setValue('p1');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('',0,20,'p1',expect.any(Object));await button(w,'重置').trigger('click');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('',0,20,'',expect.any(Object));w.unmount()})
 it('creates a manual sample and retains photo after save failure',async()=>{const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click');await w.get('dialog form').trigger('submit');expect(w.text()).toContain('请输入样品名称');await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic manual');await w.get('.location-field input').setValue('M-01');h.upload.mockResolvedValue({id:'photo-1'});const upload=w.findAllComponents({name:'SingleFileUpload'}).find(c=>c.props('title')==='存放照片')!;await upload.props('upload')(new File(['image'],'synthetic.png',{type:'image/png'}));h.create.mockRejectedValue(new ApiError(400,'INVALID','暂时无法保存'));await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith(expect.objectContaining({productId:null,variantId:null,sampleName:'Synthetic manual',storageImageId:'photo-1'}),expect.any(String));expect(w.text()).toContain('暂时无法保存');expect(w.text()).toContain('移除照片');await button(w,'移除照片').trigger('click');await w.get('dialog form').trigger('submit');expect(h.create).toHaveBeenLastCalledWith(expect.objectContaining({storageImageId:null}),expect.any(String));w.unmount()})

 it('requires SKU selection and preserves inputs after failed save',async()=>{const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await flushPromises();await w.get('dialog form').trigger('submit');expect(h.create).not.toHaveBeenCalled();expect(w.text()).toContain('请先选择 SKU');await button(w,'选择产品 SKU').trigger('click');await flushPromises();await w.get('input[type=radio]').setValue();await button(w,'确认选择').trigger('click');await w.get('.location-field input').setValue('B-02');h.create.mockRejectedValue(new ApiError(409,'SAMPLE_LOCATION_EXISTS','该 SKU 已登记在此库位'));await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith(expect.objectContaining({productId:'p1',variantId:'v1',locationCode:'B-02'}),expect.any(String));expect(w.text()).toContain('该 SKU 已登记在此库位');expect((w.get('.location-field input').element as HTMLInputElement).value).toBe('B-02');await button(w,'取消').trigger('click');expect(w.text()).toContain('放弃填写确认');w.unmount()})
 it('edits location with version and protects cancelled removal',async()=>{const w=view();await flushPromises();await button(w,'编辑').trigger('click');await flushPromises();expect(w.find('input[type=radio]').exists()).toBe(false);await w.get('.location-field input').setValue('C-03');h.update.mockResolvedValue({...row,locationCode:'C-03',version:3});await w.get('dialog form').trigger('submit');await flushPromises();expect(h.update).toHaveBeenCalledWith('s1',expect.objectContaining({productId:'p1',variantId:'v1',locationCode:'C-03',version:2}),expect.any(String));h.confirm.mockResolvedValue(false);await button(w,'移除').trigger('click');await flushPromises();expect(h.remove).not.toHaveBeenCalled();w.unmount()})
 it('searches only on submit and hides writes for read-only users',async()=>{h.write=false;const w=view();await flushPromises();await w.get('.filter-panel input').setValue('SYN');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('SYN',0,20,'',expect.any(Object));expect(w.text()).not.toContain('新增样品');expect(button(w,'移除')).toBeUndefined();w.unmount()})
})

it('keeps manual sample image separate from the storage photo',async()=>{
 const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click')
 await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic sample');await w.get('.location-field input').setValue('A-1')
 h.upload.mockResolvedValue({id:'sample-image'});const uploader=w.findAllComponents({name:'SingleFileUpload'}).find(c=>c.props('title')==='样品图片')!
 const file=new File(['image'],'synthetic.png',{type:'image/png'});await uploader.props('upload')(file)
 expect(h.upload).toHaveBeenCalledWith('SAMPLE_IMAGE',file)
 await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.create).toHaveBeenCalledWith(expect.objectContaining({sampleImageId:'sample-image',storageImageId:null}),expect.any(String));w.unmount()
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
 expect(w.get('.selected-sku-preview img').attributes('src')).toBe('blob:synthetic-controlled')
 expect(catalogApi.externalImageBlob).toHaveBeenCalledWith('https://example.com/synthetic.png','thumbnail',expect.any(AbortSignal),expect.any(String),'purchases.read')
 await w.get('.selected-sku-preview button').trigger('click')
 expect(w.findComponent({name:'PurchaseItemImage'}).findComponent({name:'ImagePreviewDialog'}).exists()).toBe(true)
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
 expect(h.update).toHaveBeenCalledWith('s1',expect.objectContaining({sampleCode:'LEGACY'}),expect.any(String))
 w.unmount()
})

it('blocks Enter in sample inputs and carries editable SKU specifications',async()=>{
 const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'选择产品 SKU').trigger('click');await flushPromises()
 const sku={productId:'p1',productName:'Synthetic product',variant:{id:'v1',variantName:'Synthetic SKU',itemLengthCm:30,itemWidthCm:20,itemHeightCm:5,packageWeightGrams:120}}
 w.findComponent({name:'CustomerSkuPicker'}).vm.$emit('select',sku);await flushPromises()
 const spec=w.get('input[placeholder="选填，如颜色、尺寸"]')
 expect((spec.element as HTMLInputElement).value).toBe('产品：长 30 cm × 宽 20 cm × 高 5 cm；包装重量：120 g')
 await spec.setValue('自定义规格');await w.get('.location-field input').setValue('A-02')
 for(const input of w.findAll('dialog input')){
  const event=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});input.element.dispatchEvent(event);expect(event.defaultPrevented).toBe(true)
 }
 expect(h.create).not.toHaveBeenCalled()
 await button(w,'更换 SKU').trigger('click');await flushPromises();w.findComponent({name:'CustomerSkuPicker'}).vm.$emit('select',sku);await flushPromises()
 expect((spec.element as HTMLInputElement).value).toBe('自定义规格')
 await w.get('dialog form').trigger('submit');await flushPromises();expect(h.create).toHaveBeenCalledWith(expect.objectContaining({specification:'自定义规格'}),expect.any(String));w.unmount()
})
it('keeps saved sample specifications when editing',async()=>{
 h.list.mockResolvedValue({items:[{...row,specification:'已有样品说明'}],totalCount:1})
 const w=view();await flushPromises();await button(w,'编辑').trigger('click');await flushPromises()
 expect((w.get('input[placeholder="选填，如颜色、尺寸"]').element as HTMLInputElement).value).toBe('已有样品说明');w.unmount()
})

it('opens printing for read-only users with the selected sample fields',async()=>{
 h.write=false;h.list.mockResolvedValue({items:[{...row,specification:'30×20cm'}],totalCount:1})
 const w=view();await flushPromises();await button(w,'标签打印').trigger('click');await flushPromises()
 expect(w.findComponent({name:'SampleLabelDialog'}).props('sample')).toEqual(expect.objectContaining({skuName:row.skuName,specification:'30×20cm',locationCode:'A-01'}))
 expect(h.update).not.toHaveBeenCalled();w.unmount()
})


it('keeps the same request and locks inputs after an uncertain save',async()=>{
 const w=view();await flushPromises();await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click')
 await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic retry');await w.get('.location-field input').setValue('R-01')
 h.create.mockRejectedValueOnce(new TypeError('network failed'));await w.get('dialog form').trigger('submit');await flushPromises()
 const first=h.create.mock.calls[0]!;expect(w.get('.location-field input').attributes('disabled')).toBeDefined();expect(w.text()).toContain('保存结果待确认')
 h.create.mockResolvedValueOnce({...row,productId:null,variantId:null});await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.create.mock.calls[1]).toEqual(first);expect(sessionStorage.getItem('jiugeng.samples.pending.current')).toBeNull();w.unmount()
})
it('blocks writes when the old backend does not advertise recoverable removal',async()=>{
 h.capabilities.mockRejectedValue(new ApiError(404,'NOT_FOUND','old backend'));const w=view();await flushPromises()
 expect(button(w,'新增样品').attributes('disabled')).toBeDefined();expect(w.text()).toContain('新接口尚不可用');expect(w.text()).not.toContain('复制到其他库位');expect(h.remove).not.toHaveBeenCalled();w.unmount()
})
it('recovers an existing copy request without offering new copy actions',async()=>{
 const input={copyFromId:'s1',productId:row.productId,variantId:row.variantId,locationCode:'B-09',storageImageId:null,sampleImageId:null}
 sessionStorage.setItem('jiugeng.samples.pending.current',JSON.stringify({key:'original-copy-key',input,copying:row,again:true}))
 const w=view();await flushPromises()
 expect(w.findAll('.sample-actions button').some(b=>b.text()==='复制到其他库位')).toBe(false)
 await button(w,'继续确认原请求').trigger('click');await flushPromises()
 await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.create).toHaveBeenCalledWith(input,'original-copy-key');expect(w.find('dialog form').exists()).toBe(false);w.unmount()
})
it('preserves the draft until a user explicitly adopts the new version',async()=>{
 const w=view();await flushPromises();await button(w,'编辑').trigger('click');await w.get('.location-field input').setValue('DRAFT-01')
 h.update.mockRejectedValueOnce(new ApiError(409,'VERSION','记录已变化'));await w.get('dialog form').trigger('submit');await flushPromises()
 h.detail.mockResolvedValue({...row,locationCode:'OTHER-01',version:8});await button(w,'加载最新资料并对照草稿').trigger('click');await flushPromises()
 expect((w.get('.location-field input').element as HTMLInputElement).value).toBe('DRAFT-01');expect(w.text()).toContain('OTHER-01')
 await button(w,'保留草稿，采用最新版本继续').trigger('click');await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.update).toHaveBeenLastCalledWith('s1',expect.objectContaining({version:8,locationCode:'DRAFT-01'}),expect.any(String));w.unmount()
})
it('keeps selected rows across pages and refreshes them before batch printing',async()=>{
 const w=view();await flushPromises();await w.get('input[aria-label="全选本页样品"]').setValue(true)
 h.selection.mockResolvedValue([{...row,skuName:'Fresh name'}]);const print=w.findAll('button').find(b=>b.text().startsWith('批量标签'))!;await print.trigger('click');await flushPromises()
 expect(h.selection).toHaveBeenCalledWith(['s1']);expect(w.findComponent({name:'SampleLabelDialog'}).props('samples')[0].skuName).toBe('Fresh name');w.unmount()
})

it('offers one save action and closes the editor after successful creation',async()=>{
 const w=view();await flushPromises()
 expect(button(w,'导出')).toBeDefined();expect(w.text()).not.toContain('导出盘点清单')
 await button(w,'新增样品').trigger('click');await button(w,'手动录入').trigger('click')
 expect(w.text()).not.toContain('保存并继续')
 await w.get('input[placeholder="输入未建立 SKU 的产品名称"]').setValue('Synthetic sample');await w.get('.location-field input').setValue('NEW-01')
 await w.get('dialog form').trigger('submit');await flushPromises()
 expect(h.create).toHaveBeenCalledTimes(1);expect(w.find('dialog form').exists()).toBe(false);w.unmount()
})

it('uses free-text location input without native suggestions', async () => {
 const w=view();await flushPromises()
 await button(w,'新增样品').trigger('click');await flushPromises()
 expect(w.get('.location-field input').attributes('list')).toBeUndefined()
 expect(w.find('datalist').exists()).toBe(false)
 expect(h.locations).not.toHaveBeenCalled()
 expect(w.get('.location-field > span').text()).toBe('库位 *')
 w.unmount()
})

it('omits the source / parent product column and keeps empty-state cells aligned', async () => {
 const w=view();await flushPromises()
 expect(w.findAll('.sample-table th').map(th=>th.text())).not.toContain('来源 / 所属产品')
 expect(w.findAll('.sample-table tbody tr')[0]!.findAll('td')).toHaveLength(w.findAll('.sample-table th').length)
 w.unmount()
})
