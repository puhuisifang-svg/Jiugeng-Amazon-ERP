import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductionDevelopmentView from './ProductionDevelopmentView.vue'
const h=vi.hoisted(()=>({page:vi.fn()}))
vi.mock('@/api/dashboard',()=>({dashboardApi:{productionProducts:h.page}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true}}))
vi.mock('vue-router',()=>({RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'}}))
beforeEach(()=>{vi.clearAllMocks();h.page.mockResolvedValue({items:[{id:'p1',developmentId:'p1',productName:'Synthetic approved project',number:'ND-1',owner:'Production',developmentState:'ACTIVE',stage:1,expectedCompletionDate:null}],totalElements:1})})
describe('生产端新品清单',()=>{
 it('uses approved projection, links details and has no manual bypass',async()=>{const w=mount(ProductionDevelopmentView);await flushPromises();expect(w.text()).toContain('已通过');expect(w.text()).toContain('待安排');expect(w.get('a').attributes('href')).toBe('/product-development/p1');expect(w.text()).not.toContain('新增');expect(h.page).toHaveBeenCalledWith({keyword:'',from:'',to:'',page:0,size:20});w.unmount()})
 it('applies filters only on search and reports failures',async()=>{const w=mount(ProductionDevelopmentView);await flushPromises();await w.get('input').setValue('changed');expect(h.page).toHaveBeenCalledTimes(1);h.page.mockRejectedValueOnce(new Error('暂时无法读取'));await w.get('form').trigger('submit');await flushPromises();expect(h.page).toHaveBeenLastCalledWith({keyword:'changed',from:'',to:'',page:0,size:20});expect(w.get('[role="alert"]').text()).toContain('暂时无法读取');w.unmount()})
})
