import { vi, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { domainApi } from '@/api/domain'
import { session } from '@/state/session'
import ProductForm from './ProductForm.vue'
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password',apiBaseUrl:'/api'}}))
it('requests only enabled product suppliers for SKU assignment',async()=>{
 const access=vi.spyOn(session,'canAccess').mockReturnValue(true)
 const suppliers=vi.spyOn(domainApi,'suppliers').mockResolvedValue({items:[],totalCount:0,page:0,size:100})
 const materials=vi.spyOn(domainApi,'materials').mockResolvedValue({items:[]} as never)
 const balances=vi.spyOn(domainApi,'balances').mockResolvedValue({items:[]} as never)
 const wrapper=mount(ProductForm,{props:{submitLabel:'保存'},global:{stubs:{RouterLink:true}}});await flushPromises()
 expect(suppliers).toHaveBeenCalledWith(expect.objectContaining({status:'ACTIVE',category:'PRODUCT'}))
 wrapper.unmount();access.mockRestore();suppliers.mockRestore();materials.mockRestore();balances.mockRestore()
})
