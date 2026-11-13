import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const list=vi.hoisted(()=>vi.fn())
vi.mock('@/api/customers',()=>({customersApi:{list}}))
import CustomerSearchSelect from './CustomerSearchSelect.vue'
beforeEach(()=>{vi.useFakeTimers();list.mockReset();list.mockResolvedValue({items:[{id:'c2',name:'其他客户'}],totalCount:21})})
afterEach(()=>vi.useRealTimers())
it('searches remotely and commits only an option, retaining the selected name on cancellation',async()=>{
 const w=mount(CustomerSearchSelect,{props:{selected:{id:'c1',name:'原客户'},label:'客户'}})
 await w.get('input').trigger('click');await flushPromises()
 await w.get('input').setValue('其他');await vi.advanceTimersByTimeAsync(250);await flushPromises()
 expect(list).toHaveBeenLastCalledWith({q:'其他',status:'ACTIVE',page:0,size:20})
 expect(w.emitted('select')).toBeUndefined()
 await w.get('input').trigger('keydown',{key:'Escape'})
 expect((w.get('input').element as HTMLInputElement).value).toBe('原客户')
 await w.get('input').trigger('click');await flushPromises()
 await w.get('[role=option]').trigger('click');expect(w.emitted('select')?.[0]?.[0]).toEqual({id:'c2',name:'其他客户'})
 w.unmount()
})
it('supports paging and keyboard selection, and reports request failures',async()=>{
 const w=mount(CustomerSearchSelect,{props:{label:'客户'}})
 await w.get('input').trigger('click');await flushPromises()
 await w.findAll('button').find(b=>b.text()==='下一页')!.trigger('click');await flushPromises()
 expect(list).toHaveBeenLastCalledWith(expect.objectContaining({page:1}))
 await w.get('input').trigger('keydown',{key:'ArrowDown'});await w.get('input').trigger('keydown',{key:'Enter'})
 expect(w.emitted('select')).toHaveLength(1)
 list.mockRejectedValue(new Error('加载失败'))
 await w.get('input').trigger('click');await flushPromises();expect(w.get('[role=alert]').text()).toContain('加载失败')
 await w.setProps({disabled:true});expect(w.get('input').attributes('aria-expanded')).toBe('false');w.unmount()
})
it('discards stale results after typing a new query',async()=>{
 let finish:((value:unknown)=>void)|undefined
 list.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}))
 const w=mount(CustomerSearchSelect,{props:{label:'客户'}})
 await w.get('input').trigger('click');await flushPromises()
 await w.get('input').setValue('其他');await vi.advanceTimersByTimeAsync(250);await flushPromises()
 finish?.({items:[{id:'old',name:'旧结果'}],totalCount:1});await flushPromises()
 expect(w.text()).not.toContain('旧结果');expect(w.text()).toContain('其他客户');w.unmount()
})
