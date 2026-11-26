import { mount,flushPromises } from '@vue/test-utils'
import { describe,it,expect,vi,beforeEach } from 'vitest'
import { createRouter,createMemoryHistory } from 'vue-router'
const mocks=vi.hoisted(()=>({page:vi.fn(),capabilities:vi.fn(),permissions:new Set<string>(),request:vi.fn()}))
vi.mock('@/api/client',async()=>({...await vi.importActual('@/api/client'),apiRequest:mocks.request}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'test'}},canAccess:(codes:string[])=>codes.every(c=>mocks.permissions.has(c))}}))
vi.mock('@/api/materialManagement',async()=>{const original=await vi.importActual('@/api/materialManagement');return {...original,materialManagementApi:{page:mocks.page,capabilities:mocks.capabilities}}})
vi.mock('@/api/materialTypes',()=>({materialTypesApi:{list:vi.fn(async()=>['包装'])}}))
vi.mock('@/components/MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialsManagement from './MaterialsManagement.vue'
const material={id:'m1',materialCode:'M1',name:'合成纸箱',materialType:'包装',unit:'件',status:'ACTIVE',version:1}
async function setup(){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div />'}}]});await router.push('/');await router.isReady();const w=mount(MaterialsManagement,{global:{plugins:[router],stubs:{TableViewport:{template:'<div><slot /></div>'},Teleport:true,MaterialEditor:true,MaterialDetailDialog:true,MaterialInventoryDialog:true}}});await flushPromises();return w}
beforeEach(()=>{sessionStorage.clear();mocks.request.mockReset().mockResolvedValue({});mocks.permissions=new Set(['materials.read','materials.write','suppliers.read','suppliers.write','inventory.read','costs.read','purchases.read','purchases.write']);mocks.capabilities.mockReset().mockResolvedValue({closeoutVersion:1});mocks.page.mockReset().mockResolvedValue({items:[{material,quantity:2,safetyStock:3,initialized:true,netPrice:'0.000123',priceUnit:'件'}],totalCount:1})})
describe('material management page',()=>{
 it('applies filters only on query and displays precise cost and stock',async()=>{const w=await setup();expect(w.text()).toContain('0.000123');expect(w.text()).toContain('低于安全库存 1');await w.get('input[placeholder="名称或规格"]').setValue('纸箱');expect(mocks.page).toHaveBeenCalledTimes(1);await w.get('form.material-filters').trigger('submit');await flushPromises();expect(mocks.page).toHaveBeenLastCalledWith(expect.objectContaining({q:'纸箱'}),0,20);w.unmount()})
 it('allows purchasing selection without material write permission',async()=>{mocks.permissions.delete('materials.write');const w=await setup();expect(w.findAll('button').some(b=>b.text()==='添加辅料')).toBe(false);await w.get('[aria-label="选择辅料 合成纸箱"]').setValue(true);const purchase=w.findAll('button').find(b=>b.text().startsWith('批量采购'))!;expect(purchase.attributes('disabled')).toBeUndefined();w.unmount()})
 it('blocks new writes against old backend and distinguishes failures',async()=>{mocks.capabilities.mockRejectedValue(new Error('旧接口'));const w=await setup();expect(mocks.page).not.toHaveBeenCalled();expect(w.text()).toContain('写入已禁用');expect(w.findAll('button').find(b=>b.text()==='添加辅料')!.attributes('disabled')).toBeDefined();w.unmount()})
 it('retains prior results with an explicit stale warning on refresh failure',async()=>{const w=await setup();mocks.page.mockRejectedValue(new Error('暂时断网'));await w.get('form.material-filters').trigger('submit');await flushPromises();expect(w.text()).toContain('当前显示上次结果');expect(w.text()).toContain('合成纸箱');w.unmount()})
 it('edits safety stock directly in the list and keeps invalid or failed input for correction',async()=>{
  mocks.permissions.add('inventory.write');const w=await setup()
  await w.get('[aria-label="设置合成纸箱安全库存"]').trigger('click')
  expect(w.get('form.material-safety-editor').attributes('popover')).toBe('manual');expect(w.find('td [data-safety-input]').exists()).toBe(false);expect(w.get('[aria-label="设置合成纸箱安全库存"]').text()).toContain('3')
  await w.get('[aria-label="合成纸箱安全库存"]').setValue('1.5');await w.get('form.material-safety-editor').trigger('submit');await flushPromises()
  expect(mocks.request).not.toHaveBeenCalled();expect(w.text()).toContain('非负安全库存')
  await w.get('[aria-label="合成纸箱安全库存"]').setValue('6');mocks.request.mockRejectedValueOnce(new Error('保存失败'))
  await w.get('form.material-safety-editor').trigger('submit');await flushPromises();expect(w.text()).toContain('保存失败');expect(w.find('[data-safety-input]').exists()).toBe(true)
  await w.get('form.material-safety-editor').trigger('submit');await flushPromises()
  expect(mocks.request).toHaveBeenLastCalledWith('/v1/inventory/balances/m1/safety-stock',{method:'PUT',body:JSON.stringify({safetyStock:'6'})});expect(w.find('[data-safety-input]').exists()).toBe(false);w.unmount()
 })
 it('shows safety stock as read-only without inventory write permission',async()=>{const w=await setup();expect(w.find('[aria-label="设置合成纸箱安全库存"]').exists()).toBe(false);expect(w.text()).toContain('安全库存 3');w.unmount()})

})
