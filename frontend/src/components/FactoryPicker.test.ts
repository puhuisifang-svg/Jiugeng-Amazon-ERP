import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import FactoryPicker from './FactoryPicker.vue'
const h=vi.hoisted(()=>({skus:vi.fn(),products:vi.fn()}))
vi.mock('@/api/domain',()=>({domainApi:h}))
beforeEach(()=>{vi.clearAllMocks();h.products.mockResolvedValue({items:[{id:'p1',productName:'Synthetic',productCode:'P1',status:'ACTIVE',variants:[{status:'ACTIVE'}]}]});h.skus.mockResolvedValue({items:[],totalCount:0})})
it('combines parent and keyword on search and retains the filter during pagination',async()=>{
 const w=mount(FactoryPicker,{props:{kind:'products',label:'选择 SKU'},global:{stubs:{AccessDialog:{template:'<div><slot/><slot name="pagination"/></div>'},TableViewport:{template:'<div><slot/></div>'},ListPagination:true}}});await w.get('button').trigger('click');await flushPromises();await w.get('select[aria-label="父体筛选"]').setValue('p1');await w.get('input').setValue('SYN');expect(h.skus).toHaveBeenCalledTimes(1);await w.get('form').trigger('submit');await flushPromises();expect(h.skus).toHaveBeenLastCalledWith(expect.objectContaining({productId:'p1',field:'KEYWORD',exact:false,q:'SYN',page:0}));w.findComponent({name:'ListPagination'}).vm.$emit('update:page',2);await flushPromises();expect(h.skus).toHaveBeenLastCalledWith(expect.objectContaining({productId:'p1',q:'SYN',page:1}));await w.get('select').setValue('');await w.get('form').trigger('submit');await flushPromises();expect(h.skus).toHaveBeenLastCalledWith(expect.objectContaining({productId:'',page:0}));w.unmount()
})
