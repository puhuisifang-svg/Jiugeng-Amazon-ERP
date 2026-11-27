import { mount,flushPromises } from '@vue/test-utils'
import { beforeEach,describe,it,expect,vi } from 'vitest'
const mocks=vi.hoisted(()=>({detail:vi.fn(),save:vi.fn(),capabilities:vi.fn(),canAccess:vi.fn(()=>true)}))
vi.mock('@/api/materialManagement',async()=>({...await vi.importActual('@/api/materialManagement'),materialManagementApi:{detail:mocks.detail,save:mocks.save,capabilities:mocks.capabilities,page:vi.fn(async()=>({items:[]}))}}))
vi.mock('@/api/materialTypes',()=>({materialTypesApi:{list:vi.fn(async()=>['包装']),units:vi.fn(async()=>['件'])}}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'editor-test'}},canAccess:mocks.canAccess}}))
vi.mock('./MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialEditor from './MaterialEditor.vue'
import { ApiError } from '@/api/client'
const material={id:'m1',materialCode:'M1',name:'合成纸箱',materialType:'包装',unit:'件',status:'ACTIVE',version:4,remark:''}
const global={stubs:{AccessDialog:{props:['title','busy','dirty'],template:'<div><slot /><slot name="footer" /></div>',methods:{requestClose(){}}},SingleFileUpload:true,MaterialSupplierDetail:true}}
beforeEach(()=>{sessionStorage.clear();mocks.canAccess.mockReset().mockReturnValue(true);mocks.capabilities.mockReset().mockResolvedValue({closeoutVersion:1,simpleCostVersion:1});mocks.detail.mockReset().mockResolvedValue({material,suppliers:[],price:{netPrice:'0.000123',unit:'件',version:0},unitLocked:true,quantityScale:0});mocks.save.mockReset().mockResolvedValue(material)})
describe('material aggregate editor',()=>{
 it('keeps a six-decimal price unchanged while editing another field',async()=>{const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();expect(w.get('[aria-label="辅料成本价"]').element).toHaveProperty('value','0.00');await w.get('input[maxlength="200"]').setValue('更新名称');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({id:'m1',version:4,suppliers:[],material:expect.not.objectContaining({costPrice:expect.anything()})}));w.unmount()})
 it('copies eligible suppliers while explaining omitted historical links',async()=>{mocks.detail.mockResolvedValue({material,suppliers:[{supplierId:'active',supplierName:'当前供应商',status:'ACTIVE',materialEligible:true,primary:false},{supplierId:'old',supplierName:'历史供应商',status:'ACTIVE',materialEligible:false,primary:true}],price:null,unitLocked:true,quantityScale:0});const w=mount(MaterialEditor,{props:{id:'m1',copy:true},global});await flushPromises();expect(w.text()).toContain('复制时未带入');expect(w.text()).toContain('历史供应商');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({id:null,copyFromId:'m1',suppliers:[expect.objectContaining({supplierId:'active'})]}));w.unmount()})
 it('blocks saving when aggregate detail fails, preserving associations',async()=>{mocks.detail.mockRejectedValue(new Error('关系不可用'));const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();expect(w.text()).toContain('关系不可用');await w.get('form').trigger('submit');expect(mocks.save).not.toHaveBeenCalled();w.unmount()})
 it('persists and reuses the original save after timeout',async()=>{mocks.save.mockRejectedValueOnce(new Error('timeout')).mockResolvedValue(material);const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();await w.get('input[maxlength="200"]').setValue('修改后名称');await w.get('form').trigger('submit');await flushPromises();const command=JSON.parse(JSON.stringify(mocks.save.mock.calls[0]![0]));expect(w.text()).toContain('保存结果待确认');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save.mock.calls[1]![0]).toEqual(command);expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()})
})

it('confirms a legacy continue request once and closes without clearing into another draft',async()=>{
 const input={requestId:'legacy-material-request',id:null,material:{name:'合成纸箱',materialType:'包装',unit:'件'},suppliers:[]}
 sessionStorage.setItem('jiugeng.material.pending.editor-test',JSON.stringify({input,again:true}))
 const w=mount(MaterialEditor,{props:{resume:true},global});await flushPromises()
 expect(w.text()).not.toContain('保存并继续')
 await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save).toHaveBeenCalledWith(input);expect(mocks.save).toHaveBeenCalledTimes(1)
 expect(w.emitted('close')).toHaveLength(1);expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()
})

it('saves a normal cost field without reason or confirmation',async()=>{
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 const price=w.get('[aria-label="辅料成本价"]');await price.setValue('5.6');await price.trigger('blur')
 expect(price.element).toHaveProperty('value','5.60');expect(w.text()).not.toContain('核定原因');expect(w.find('input[type="checkbox"][required]').exists()).toBe(false)
 await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({cost:{netPrice:'5.60',version:0}}));w.unmount()
})
it('rejects new prices with more than two decimals',async()=>{
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 await w.get('[aria-label="辅料成本价"]').setValue('5.678');await w.get('form').trigger('submit')
 expect(w.text()).toContain('成本价最多两位小数');expect(mocks.save).not.toHaveBeenCalled();w.unmount()
})

it('clears a cost explicitly and leaves an untouched historical cost out of the command',async()=>{
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 await w.get('[aria-label="辅料成本价"]').setValue('');await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save.mock.calls[0]![0].cost).toEqual({netPrice:null,version:0});w.unmount()
})
it('preserves historical precision when only correcting an unused material unit',async()=>{
 mocks.detail.mockResolvedValue({material,suppliers:[],price:{netPrice:'5.601234',unit:'件',version:2},unitLocked:false,quantityScale:0})
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 await w.get('[aria-label="辅料单位"]').setValue('米');await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save.mock.calls[0]![0].cost).toBeUndefined();expect(mocks.save.mock.calls[0]![0].material.unit).toBe('米');w.unmount()
})
it('copies cost as a normal editable field without reapproval',async()=>{
 const w=mount(MaterialEditor,{props:{id:'m1',copy:true},global});await flushPromises()
 expect(w.get('[aria-label="辅料成本价"]').attributes('disabled')).toBeUndefined()
 await w.get('[aria-label="辅料成本价"]').setValue('2.50');await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save.mock.calls[0]![0].cost).toEqual({netPrice:'2.50',version:-1});w.unmount()
})
it('retains the exact legacy price payload while confirming an old request',async()=>{
 const input={requestId:'legacy-price',id:null,material:{name:'合成纸箱',materialType:'包装',unit:'件',costPrice:{netPrice:'1.234567',unit:'件',source:'MANUAL',referenceId:null,reason:'旧价格原因',version:-1,confirmedNet:true}},suppliers:[]}
 sessionStorage.setItem('jiugeng.material.pending.editor-test',JSON.stringify({input}))
 const w=mount(MaterialEditor,{props:{resume:true},global});await flushPromises();await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save).toHaveBeenCalledWith(input);w.unmount()
})
it('blocks new commands against an older backend instead of silently losing cost',async()=>{
 mocks.capabilities.mockResolvedValue({closeoutVersion:1})
 const w=mount(MaterialEditor,{global});await flushPromises();await w.get('form').trigger('submit')
 expect(w.text()).toContain('服务版本尚未支持');expect(mocks.save).not.toHaveBeenCalled();w.unmount()
})


it('disables unchanged edits and re-disables after reverting without writing or rounding stored price',async()=>{
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 const save=w.get('button[type="submit"]')
 expect(save.attributes('disabled')).toBeDefined()
 await w.get('input[maxlength="200"]').setValue('修改名称');expect(save.attributes('disabled')).toBeUndefined()
 await w.get('input[maxlength="200"]').setValue(material.name);expect(save.attributes('disabled')).toBeDefined()
 await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.save).not.toHaveBeenCalled();expect(w.emitted('close')).toHaveLength(1)
 expect(w.emitted('saved')).toBeUndefined();expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()
})
it('shows saving without an uncertain-result warning while a normal request is in flight',async()=>{
 let complete!:(value:typeof material)=>void
 mocks.save.mockImplementationOnce(()=>new Promise(resolve=>{complete=resolve}))
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 await w.get('input[maxlength="200"]').setValue('修改名称');await w.get('form').trigger('submit');await flushPromises()
 expect(w.text()).toContain('保存中');expect(w.text()).not.toContain('保存结果待确认');expect(w.text()).not.toContain('稍后确认')
 expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).not.toBeNull()
 complete(material);await flushPromises();expect(w.emitted('saved')).toHaveLength(1);expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()
})
it('shows a definitive validation failure without locking an uncertain request',async()=>{
 mocks.save.mockRejectedValueOnce(new ApiError(400,'MATERIAL_FIELD_INVALID','字段无效'))
 const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises()
 await w.get('input[maxlength="200"]').setValue('修改名称');await w.get('form').trigger('submit');await flushPromises()
 expect(w.text()).toContain('字段无效');expect(w.text()).not.toContain('保存结果待确认')
 expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()
})
