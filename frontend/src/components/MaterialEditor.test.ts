import { mount,flushPromises } from '@vue/test-utils'
import { beforeEach,describe,it,expect,vi } from 'vitest'
const mocks=vi.hoisted(()=>({detail:vi.fn(),save:vi.fn()}))
vi.mock('@/api/materialManagement',async()=>({...await vi.importActual('@/api/materialManagement'),materialManagementApi:{detail:mocks.detail,save:mocks.save,page:vi.fn(async()=>({items:[]}))}}))
vi.mock('@/api/materialTypes',()=>({materialTypesApi:{list:vi.fn(async()=>['包装']),units:vi.fn(async()=>['件'])}}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'editor-test'}},canAccess:()=>true}}))
vi.mock('./MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialEditor from './MaterialEditor.vue'
const material={id:'m1',materialCode:'M1',name:'合成纸箱',materialType:'包装',unit:'件',status:'ACTIVE',version:4,remark:''}
const global={stubs:{AccessDialog:{props:['title','busy','dirty'],template:'<div><slot /><slot name="footer" /></div>',methods:{requestClose(){}}},SingleFileUpload:true,MaterialSupplierDetail:true}}
beforeEach(()=>{sessionStorage.clear();mocks.detail.mockReset().mockResolvedValue({material,suppliers:[],price:{netPrice:'0.000123',unit:'件',version:0},unitLocked:true,quantityScale:0});mocks.save.mockReset().mockResolvedValue(material)})
describe('material aggregate editor',()=>{
 it('keeps a six-decimal price unchanged while editing another field',async()=>{const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();expect(w.get('[aria-label="辅料成本价"]').element).toHaveProperty('value','0.000123');await w.get('input[maxlength="200"]').setValue('更新名称');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({id:'m1',version:4,suppliers:[],material:expect.not.objectContaining({costPrice:expect.anything()})}));w.unmount()})
 it('copies eligible suppliers while explaining omitted historical links',async()=>{mocks.detail.mockResolvedValue({material,suppliers:[{supplierId:'active',supplierName:'当前供应商',status:'ACTIVE',materialEligible:true,primary:false},{supplierId:'old',supplierName:'历史供应商',status:'ACTIVE',materialEligible:false,primary:true}],price:null,unitLocked:true,quantityScale:0});const w=mount(MaterialEditor,{props:{id:'m1',copy:true},global});await flushPromises();expect(w.text()).toContain('复制时未带入');expect(w.text()).toContain('历史供应商');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({id:null,copyFromId:'m1',suppliers:[expect.objectContaining({supplierId:'active'})]}));w.unmount()})
 it('blocks saving when aggregate detail fails, preserving associations',async()=>{mocks.detail.mockRejectedValue(new Error('关系不可用'));const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();expect(w.text()).toContain('关系不可用');await w.get('form').trigger('submit');expect(mocks.save).not.toHaveBeenCalled();w.unmount()})
 it('persists and reuses the original save after timeout',async()=>{mocks.save.mockRejectedValueOnce(new Error('timeout')).mockResolvedValue(material);const w=mount(MaterialEditor,{props:{id:'m1'},global});await flushPromises();await w.get('form').trigger('submit');await flushPromises();const command=JSON.parse(JSON.stringify(mocks.save.mock.calls[0]![0]));expect(w.text()).toContain('保存结果待确认');await w.get('form').trigger('submit');await flushPromises();expect(mocks.save.mock.calls[1]![0]).toEqual(command);expect(sessionStorage.getItem('jiugeng.material.pending.editor-test')).toBeNull();w.unmount()})
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
