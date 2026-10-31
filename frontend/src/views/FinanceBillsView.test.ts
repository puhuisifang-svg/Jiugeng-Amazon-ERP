import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api=vi.hoisted(()=>({shops:vi.fn(),list:vi.fn(),save:vi.fn(),saveShop:vi.fn(),void:vi.fn(),attachments:vi.fn(),history:vi.fn(),export:vi.fn()}))
vi.mock('@/api/finance',()=>({financeApi:api}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true}}))
import FinanceBillsView from './FinanceBillsView.vue'
import ListPagination from '@/components/ListPagination.vue'
import { settleConfirmation } from '@/state/uiConfirmation'
const shop={id:'s1',name:'合成店铺甲',remark:'',active:true,version:0}
const bill={id:'b1',shopId:'s1',shopName:'合成店铺甲',date:'2026-10-01',direction:'EXPENSE',category:'物流运费',detail:'合成运输',account:'合成账户',amount:12.34,remark:'',voided:false,version:0,attachments:0}
const mountView=()=>mount(FinanceBillsView,{global:{stubs:{FinanceVoucher:true}}})
describe('FinanceBillsView',()=>{
 beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn();api.shops.mockResolvedValue([shop,{...shop,id:'s2',name:'合成店铺乙'}]);api.list.mockResolvedValue({items:[bill],totalCount:1,totals:[{income:0,expense:12.34,balance:-12.34,count:1}],accounts:[],months:[]});api.attachments.mockResolvedValue([]);api.history.mockResolvedValue([])})
 it('applies shop and text filters only on search and resets pagination',async()=>{const w=mountView();await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({shopId:'s1'}),0,20);await w.get('input[type=search]').setValue('物流');await w.get('.filter-panel select').setValue('s2');expect(api.list).toHaveBeenCalledTimes(1);await w.get('.filter-panel').trigger('submit');await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.objectContaining({shopId:'s2',q:'物流'}),0,20);w.unmount()})
 it('converts page numbers and makes only one request when size resets the page',async()=>{const w=mountView();await flushPromises();w.getComponent(ListPagination).vm.$emit('update:page',2);await flushPromises();expect(api.list).toHaveBeenLastCalledWith(expect.anything(),1,20);api.list.mockClear();w.getComponent(ListPagination).vm.$emit('update:pageSize',50);w.getComponent(ListPagination).vm.$emit('update:page',1);await flushPromises();expect(api.list).toHaveBeenCalledTimes(1);expect(api.list).toHaveBeenCalledWith(expect.anything(),0,50);w.unmount()})
 it('requires confirmation for void and refreshes totals after success',async()=>{const w=mountView();await flushPromises();await w.findAll('button').find(b=>b.text()==='作废')!.trigger('click');settleConfirmation(null);await flushPromises();expect(api.void).not.toHaveBeenCalled();await w.findAll('button').find(b=>b.text()==='作废')!.trigger('click');settleConfirmation('yes');await flushPromises();expect(api.void).toHaveBeenCalledWith(bill);expect(api.list).toHaveBeenCalledTimes(2);w.unmount()})
 it('keeps entered bill on failure and asks before discarding edits',async()=>{api.save.mockRejectedValue(new Error('版本冲突'));const w=mountView();await flushPromises();await w.findAll('button').find(b=>b.text()==='编辑')!.trigger('click');await flushPromises();await w.get('textarea[required]').setValue('修改后的合成明细');await w.get('dialog form').trigger('submit');await flushPromises();expect(w.get('dialog').text()).toContain('版本冲突');expect((w.get('textarea[required]').element as HTMLTextAreaElement).value).toBe('修改后的合成明细');await w.get('[aria-label="关闭弹窗"]').trigger('click');expect(w.findComponent({name:'UnsavedChangesDialog'}).exists()).toBe(true);w.unmount()})
})
