import { mount,flushPromises } from '@vue/test-utils'
import { describe,it,expect,vi,beforeEach } from 'vitest'
import { createRouter,createMemoryHistory } from 'vue-router'
const mocks=vi.hoisted(()=>({page:vi.fn(),capabilities:vi.fn(),permissions:new Set<string>()}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'test'}},canAccess:(codes:string[])=>codes.every(c=>mocks.permissions.has(c))}}))
vi.mock('@/api/materialManagement',async()=>{const original=await vi.importActual('@/api/materialManagement');return {...original,materialManagementApi:{page:mocks.page,capabilities:mocks.capabilities}}})
vi.mock('@/api/materialTypes',()=>({materialTypesApi:{list:vi.fn(async()=>['包装'])}}))
vi.mock('@/components/MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialsManagement from './MaterialsManagement.vue'
const material={id:'m1',materialCode:'M1',name:'合成纸箱',materialType:'包装',unit:'件',status:'ACTIVE',version:1}
async function setup(){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div />'}}]});await router.push('/');await router.isReady();const w=mount(MaterialsManagement,{global:{plugins:[router],stubs:{TableViewport:{template:'<div><slot /></div>'},Teleport:true,MaterialEditor:true,MaterialDetailDialog:true,MaterialInventoryDialog:true}}});await flushPromises();return w}
beforeEach(()=>{sessionStorage.clear();mocks.permissions=new Set(['materials.read','materials.write','suppliers.read','suppliers.write','inventory.read','costs.read','purchases.read','purchases.write']);mocks.capabilities.mockReset().mockResolvedValue({closeoutVersion:1});mocks.page.mockReset().mockResolvedValue({items:[{material,quantity:2,safetyStock:3,initialized:true,netPrice:'0.000123',priceUnit:'件'}],totalCount:1})})
describe('material management page',()=>{
 it('applies filters only on query and displays precise cost and stock',async()=>{const w=await setup();expect(w.text()).toContain('0.000123');expect(w.text()).toContain('低于安全库存 1');await w.get('input[placeholder="名称或规格"]').setValue('纸箱');expect(mocks.page).toHaveBeenCalledTimes(1);await w.get('form.material-filters').trigger('submit');await flushPromises();expect(mocks.page).toHaveBeenLastCalledWith(expect.objectContaining({q:'纸箱'}),0,20);w.unmount()})
 it('allows purchasing selection without material write permission',async()=>{mocks.permissions.delete('materials.write');const w=await setup();expect(w.findAll('button').some(b=>b.text()==='添加辅料')).toBe(false);await w.get('[aria-label="选择辅料 合成纸箱"]').setValue(true);const purchase=w.findAll('button').find(b=>b.text().startsWith('批量采购'))!;expect(purchase.attributes('disabled')).toBeUndefined();w.unmount()})
 it('blocks new writes against old backend and distinguishes failures',async()=>{mocks.capabilities.mockRejectedValue(new Error('旧接口'));const w=await setup();expect(mocks.page).not.toHaveBeenCalled();expect(w.text()).toContain('写入已禁用');expect(w.findAll('button').find(b=>b.text()==='添加辅料')!.attributes('disabled')).toBeDefined();w.unmount()})
 it('retains prior results with an explicit stale warning on refresh failure',async()=>{const w=await setup();mocks.page.mockRejectedValue(new Error('暂时断网'));await w.findAll('button').find(b=>b.text()==='刷新')!.trigger('click');await flushPromises();expect(w.text()).toContain('当前显示上次结果');expect(w.text()).toContain('合成纸箱');w.unmount()})
})
