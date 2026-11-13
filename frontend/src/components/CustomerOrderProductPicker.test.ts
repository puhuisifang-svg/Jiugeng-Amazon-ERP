import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { customersApi, type CustomerProduct } from '@/api/customers'
import CustomerOrderProductPicker from './CustomerOrderProductPicker.vue'
vi.mock('@/api/customers',()=>({customersApi:{products:vi.fn()}}))
const product=(id:string)=>({id,productName:`商品${id}`,skuCode:`SKU-${id}`,priceUnit:'件'} as CustomerProduct)
beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=function(){this.open=true};vi.mocked(customersApi.products).mockImplementation(async filters=>({items:[product(filters?.page?'2':'1')],totalCount:21,page:filters?.page??0,size:20}))})
it('keeps selections across pages and only searches after submit',async()=>{
 const w=mount(CustomerOrderProductPicker,{props:{customerId:'c1',existing:[]}});await flushPromises()
 await w.get('input[type=checkbox]').setValue(true)
 await w.get('button[aria-label="下一页"]').trigger('click');await flushPromises()
 await w.get('input[type=checkbox]').setValue(true)
 await w.get('input[aria-label="搜索客户商品"]').setValue('红色')
 expect(customersApi.products).toHaveBeenCalledTimes(2)
 await w.get('form').trigger('submit');await flushPromises()
 expect(customersApi.products).toHaveBeenLastCalledWith(expect.objectContaining({q:'红色',page:0,customerId:'c1'}))
 await w.findAll('button').find(b=>b.text()==='添加所选（2）')!.trigger('click')
 expect(w.emitted('confirm')?.[0]?.[0]).toEqual([product('1'),product('2')]);w.unmount()
})
it('disables existing products and cancel never commits selection',async()=>{
 const w=mount(CustomerOrderProductPicker,{props:{customerId:'c1',existing:['1']}});await flushPromises()
 expect(w.get('input[type=checkbox]').attributes('disabled')).toBeDefined()
 await w.findAll('button').find(b=>b.text()==='取消')!.trigger('click');expect(w.emitted('close')).toHaveLength(1);expect(w.emitted('confirm')).toBeUndefined();w.unmount()
})
it('shows load failures and can retry',async()=>{
 vi.mocked(customersApi.products).mockRejectedValueOnce(new Error('连接失败'))
 const w=mount(CustomerOrderProductPicker,{props:{customerId:'c1',existing:[]}});await flushPromises()
 expect(w.get('[role=alert]').text()).toContain('连接失败')
 await w.findAll('button').find(b=>b.text()==='重试')!.trigger('click');await flushPromises()
 expect(w.find('[role=alert]').exists()).toBe(false);expect(w.text()).toContain('商品1');w.unmount()
})
it('keeps exactly one product across pages in shipment mode',async()=>{
 const w=mount(CustomerOrderProductPicker,{props:{customerId:'c1',existing:[],single:true}});await flushPromises()
 await w.get('input[type=radio]').setValue(true)
 await w.get('button[aria-label="下一页"]').trigger('click');await flushPromises()
 await w.get('input[type=radio]').setValue(true)
 await w.findAll('button').find(b=>b.text()==='确认选择')!.trigger('click')
 expect(w.emitted('confirm')?.[0]?.[0]).toEqual([product('2')]);w.unmount()
})
