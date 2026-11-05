import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,expect,it,vi} from 'vitest'
import Editor from './ForwarderEditor.vue'
import {forwarderTotals} from '@/ui/forwarderFields'
const api=vi.hoisted(()=>({options:vi.fn(),save:vi.fn(),export:vi.fn()}));const warehouses=vi.hoisted(()=>({list:vi.fn()}))
vi.mock('@/api/forwarders',()=>({forwardersApi:api,carrierNames:{KAIQI:'凯琦',HUAMAO:'华茂万通'}}))
vi.mock('@/api/customsDocuments',()=>({customsWarehousesApi:warehouses}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn().mockResolvedValue(true)}))
const document={id:'synthetic',documentNo:'SYN-01',carrier:'KAIQI' as const,status:'DRAFT' as const,fields:{country:'US',warehouse:'SYN1',address:'',waybill:'SYN-FBA'},items:[],version:3,updatedAt:'',templateVersion:'v1'}
beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn();api.options.mockResolvedValue({channels:['Synthetic channel'],addresses:[{code:'SYN1',address:'Synthetic street',city:'Synthetic city',region:'CA',postalCode:'00000',phone:''}],templateVersion:'v1'});warehouses.list.mockResolvedValue([]);api.save.mockResolvedValue(document)})
it('uses distinct carrier forms and keeps entered values on failed save',async()=>{
 const w=mount(Editor,{props:{writable:true,exportable:true}});await flushPromises();await w.get('select[aria-label="合作货代"]').setValue('HUAMAO');await flushPromises();expect(w.text()).toContain('发件人信息');expect(w.text()).toContain('进口商');expect(w.text()).not.toContain('收货渠道 *');await w.get('input[aria-label="运单号 / 批次跟踪号 *"]').setValue('SYN-EDIT');api.save.mockRejectedValueOnce(new Error('单据编号已存在'));await w.findAll('button').find(b=>b.text()==='保存单据')!.trigger('click');await flushPromises();expect(w.text()).toContain('单据编号已存在');expect((w.get('input[aria-label="运单号 / 批次跟踪号 *"]').element as HTMLInputElement).value).toBe('SYN-EDIT');w.unmount()
})
it('uses template fallback address and saves with optimistic version',async()=>{
 const w=mount(Editor,{props:{document,writable:true,exportable:true}});await flushPromises();await w.findAll('button').find(b=>b.text()==='匹配地址')!.trigger('click');await flushPromises();expect((w.get('textarea[aria-label="收件地址 *"]').element as HTMLTextAreaElement).value).toBe('Synthetic street');await w.findAll('button').find(b=>b.text()==='保存单据')!.trigger('click');await flushPromises();expect(api.save).toHaveBeenCalledWith(expect.objectContaining({version:3,fields:expect.objectContaining({address:'Synthetic street'})}),'synthetic');w.unmount()
})
it('prevents exporting unsaved changes and reports server export validation',async()=>{
 const w=mount(Editor,{props:{document,writable:true,exportable:true}});await flushPromises();api.export.mockRejectedValueOnce(new Error('请完善后导出：商品1：产品图片'));const button=w.findAll('button').find(b=>b.text()==='导出凯琦模板')!;await button.trigger('click');await flushPromises();expect(w.text()).toContain('商品1：产品图片');await w.get('input[aria-label="运单号 / 批次跟踪号 *"]').setValue('SYN-CHANGE');expect(button.attributes('disabled')).toBeDefined();w.unmount()
})
it('does not show mutation or export actions to a reader',async()=>{
 const w=mount(Editor,{props:{document,writable:false,exportable:false}});await flushPromises();expect(w.text()).not.toContain('保存单据');expect(w.text()).not.toContain('导出凯琦模板');await w.findAll('button').find(b=>b.text()==='商品与装箱')!.trigger('click');expect(w.text()).not.toContain('选择产品 SKU');expect(w.text()).not.toContain('手动添加商品');w.unmount()
})
it('counts mixed boxes once while adding each SKU quantity and price',()=>{expect(forwarderTotals([{boxStart:'1',boxCount:'2',perBox:'3',price:'1.25'},{boxStart:'1',boxCount:'2',perBox:'4',price:'2'}])).toEqual({boxes:2,quantity:14,amount:'23.50'})})
