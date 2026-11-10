import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import View from './FactoryExpensesView.vue'
const h=vi.hoisted(()=>({list:vi.fn(),command:vi.fn(),get:vi.fn(),history:vi.fn(),can:true,confirm:vi.fn()}))
vi.mock('@/api/factory',()=>({factoryApi:h}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.can}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:h.confirm}))
vi.mock('@/api/client',()=>({getSessionToken:()=> 'synthetic',apiRequest:vi.fn().mockResolvedValue([])}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
const row={id:'e1',name:'Synthetic expense',date:'2026-01-01',amount:'10',status:'ACTIVE',version:0}
const render=()=>mount(View,{global:{stubs:{AccessDialog:{template:'<div><slot/><slot name="footer"/></div>',methods:{requestClose(){}}},DatePicker:true,TableViewport:{template:'<div><slot/></div>'},ListPagination:true,FactoryAttachments:true,SuccessToast:true}}})
const button=(w:ReturnType<typeof render>,label:string)=>w.findAll('button').find(b=>b.text()===label)!
beforeEach(()=>{vi.clearAllMocks();h.can=true;h.list.mockResolvedValue({items:[row],totalCount:1,amount:10});h.command.mockResolvedValue(row);h.confirm.mockResolvedValue(true);h.get.mockResolvedValue(row)})
afterEach(()=>vi.unstubAllGlobals())
describe('factory expense ledger',()=>{
 it('retains a saved record when voucher upload fails and retries without another create',async()=>{
  const fetch=vi.fn().mockResolvedValueOnce({ok:false,json:async()=>({detail:'上传暂时失败'})}).mockResolvedValueOnce({ok:true});vi.stubGlobal('fetch',fetch);
  const w=render();await flushPromises();await button(w,'新增开销').trigger('click');expect(w.text()).not.toContain('分类');expect(w.text()).not.toContain('支付方式');await w.get('input[placeholder="如：电费、设备维修"]').setValue('Synthetic expense');await w.get('input[type=number]').setValue('10');const uploader=w.findComponent({name:'SingleFileUpload'});await uploader.props('upload')(new File(['%PDF-test'],'synthetic.pdf',{type:'application/pdf'}));await w.get('#expense-form').trigger('submit');await flushPromises();expect(w.text()).toContain('开销已保存，凭证上传失败');await w.get('#expense-form').trigger('submit');await flushPromises();expect(h.command.mock.calls[0]![2]).not.toHaveProperty('id');expect(h.command.mock.calls[1]![2]).toEqual(expect.objectContaining({id:'e1',version:0}));expect(fetch).toHaveBeenCalledTimes(2);expect(w.find('#expense-form').exists()).toBe(false);w.unmount()
 })

 it('preserves inputs after failure and reuses idempotency key on retry',async()=>{const w=render();await flushPromises();await button(w,'新增开销').trigger('click');expect(w.text()).not.toContain('分类');expect(w.text()).not.toContain('支付方式');await w.get('input[placeholder="如：电费、设备维修"]').setValue('Synthetic expense');await w.get('input[type=number]').setValue('10');h.command.mockRejectedValueOnce(new Error('暂时失败'));await w.get('#expense-form').trigger('submit');await flushPromises();expect(w.text()).toContain('暂时失败');const key=h.command.mock.calls[0]![2].requestId;await w.get('#expense-form').trigger('submit');await flushPromises();expect(h.command.mock.calls[1]![2].requestId).toBe(key);expect(w.find('#expense-form').exists()).toBe(false);w.unmount()})
 it('opens optional attachments in details and confirms deletion with version',async()=>{const w=render();await flushPromises();await button(w,'详情 / 凭证').trigger('click');await flushPromises();await button(w,'凭证附件').trigger('click');expect(w.findComponent({name:'FactoryAttachments'}).props()).toEqual({kind:'expenses',id:'e1',writable:true});h.confirm.mockResolvedValueOnce(false);await button(w,'删除').trigger('click');await flushPromises();expect(h.command).not.toHaveBeenCalled();await button(w,'删除').trigger('click');await flushPromises();expect(h.command).toHaveBeenCalledWith('expenses','remove',expect.objectContaining({id:'e1',version:0}));w.unmount()})
 it('read only users can view without mutation controls',async()=>{h.can=false;const w=render();await flushPromises();expect(w.text()).not.toContain('新增开销');expect(w.text()).not.toContain('删除');await w.get('.filter-panel input').setValue('filter');expect(h.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith('expenses',expect.objectContaining({q:'filter',page:0}));w.unmount()})
})
