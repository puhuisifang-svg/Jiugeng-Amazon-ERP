import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api=vi.hoisted(()=>({save:vi.fn(),export:vi.fn(),list:vi.fn(),shippers:vi.fn(),addShipper:vi.fn(),updateShipper:vi.fn(),warehouses:vi.fn()}))
vi.mock('@/api/customsDocuments',()=>({customsDocumentsApi:api,customsWarehousesApi:{list:api.warehouses},customsShippersApi:{list:api.shippers,add:api.addShipper,update:api.updateShipper}}))
vi.mock('@/api/customs',async importOriginal=>({...await importOriginal<typeof import('@/api/customs')>(),customsApi:{list:api.list}}))
vi.mock('vue-router',()=>({onBeforeRouteLeave:vi.fn()}))
import { confirmAction } from '@/state/uiConfirmation'
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn().mockResolvedValue(true)}))
import Editor from './CustomsDocumentEditor.vue'
import { blankItem, missingFields, contractNumber, cartonTotal } from '@/ui/customsDocumentFields'
const source={id:'syn',documentNo:'SYN',status:'DRAFT' as const,fields:{seller:'Synthetic Seller'},items:[{...blankItem(),sku:'SYN-SKU',name:'合成品名'}],version:2,updatedAt:''}
const render=(props={})=>{const w=mount(Editor,{props:{writable:true,exportable:true,...props}});void w.get('input[aria-label="FBA 编号"]').setValue('FBA-TEST1234');void w.get('input[aria-label="FBA 仓库代码"]').setValue('SYN1');return w}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(confirmAction).mockResolvedValue(true);HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn();api.shippers.mockResolvedValue([{id:'default',name:'Synthetic Seller',prefix:'SYN',address:'Synthetic address',phone:'',fax:''}]);api.warehouses.mockResolvedValue([]);api.save.mockResolvedValue({...source,version:3});api.list.mockResolvedValue({items:[],totalCount:0})})
async function saveReady(w:ReturnType<typeof render>){
 await flushPromises()
 await w.findAll('.ui-section-tabs button').find(b=>b.text()==='报关单')!.trigger('click')
 const seller=w.get('select[aria-label="境内发货人"]');if(!(seller.element as HTMLSelectElement).value)await seller.setValue('default')
 await w.get('input[aria-label="运费金额"]').setValue('10')
 for(const select of w.findAll('select.customs-inline-unit'))if(!(select.element as HTMLSelectElement).value)await select.setValue('件')
 for(const input of w.findAll('.customs-inline-input[required]'))if(!(input.element as HTMLInputElement).value)await input.setValue(input.attributes('type')==='number'?'1':'个')
 await w.findAll('button').find(b=>b.text()==='保存草稿')!.trigger('click')
}
describe('报关单据编辑',()=>{
 it('locks linked sheet fields and items while keeping contract-only terms editable',async()=>{
  const w=render({document:{...source,fields:{...source.fields,sellerAddress:'Test address',buyerAddress:'Warehouse address',date:'2026-11-05'},items:[{...source.items[0],quantity:'2',unit:'件',price:'3',grossWeight:'5',netWeight:'4'}]}});await flushPromises()
  for(const tab of ['合同','发票','装箱单']){
   await w.findAll('.ui-section-tabs button').find(b=>b.text()===tab)!.trigger('click')
   for(const key of ['seller','sellerAddress','buyer','buyerAddress','date']){const field=w.get('.field-'+key);expect(field.text()).toContain('自动');expect(field.get('input,textarea').attributes('readonly')).toBeDefined()}
   expect(w.find('select[aria-label="境内发货人"]').exists()).toBe(false)
   expect(w.find('.customs-items-table input,.customs-items-table select,.customs-items-table button').exists()).toBe(false)
   expect(w.get('.customs-items-table thead').text()).toContain('自动')
   if(tab==='合同'){expect(w.get('.field-payment input').attributes('readonly')).toBeUndefined();await w.get('.field-payment input').setValue('Custom payment');expect(w.get('.field-tolerance input').attributes('readonly')).toBeUndefined()}
   if(tab==='装箱单'){expect(w.get('.field-payment input').attributes('readonly')).toBeDefined();expect((w.get('.field-payment input').element as HTMLInputElement).value).toBe('Custom payment');for(const key of ['fbaNo','warehouse','grossWeight','netWeight'])expect(w.get('.field-'+key+' input').attributes('readonly')).toBeDefined()}
  }
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='报关单')!.trigger('click');await w.get('input[aria-label="商品 1 成交数量"]').setValue('7')
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='发票')!.trigger('click');expect(w.get('.customs-items-table tbody tr').text()).toContain('7');w.unmount()
 })
 it('shows five percent for new and legacy blank contracts and preserves zero',async()=>{
  for(const props of [{},{document:{...source,fields:{...source.fields,tolerance:''}}},{document:{...source,fields:{...source.fields,tolerance:'0'}}}]){
   const w=render(props);await w.findAll('.ui-section-tabs button').find(b=>b.text()==='合同')!.trigger('click');
   const label=w.findAll('label').find(l=>l.text().includes('数量及总值增减比例'))!;
   expect((label.get('input').element as HTMLInputElement).value).toBe('document' in props&&props.document?.fields.tolerance==='0'?'0':'5');w.unmount()
  }
 })
 it('shares default payment between contract and packing and preserves explicit terms',async()=>{
  for(const payment of ['', '30 days after invoice']){
   const w=render({document:{...source,fields:{...source.fields,payment}}});
   for(const tab of ['合同','装箱单']){await w.findAll('.ui-section-tabs button').find(b=>b.text()===tab)!.trigger('click');const label=w.findAll('label').find(l=>l.text().startsWith('付款条件'))!;expect((label.get('input').element as HTMLInputElement).value).toBe(payment||'T/T')}
   w.unmount()
  }
 })
 it('includes missing and invalid freight in export checks',()=>{
  expect(missingFields({},[])).toContain('运费（含币制）');expect(missingFields({freight:'USD/'},[])).toContain('运费金额须为非负数字（USD）');expect(missingFields({freight:'USD/0'},[]).some(x=>x.includes('运费'))).toBe(false)
 })
 it('opens completed documents as read-only but allows a new copy',async()=>{
  const doc={...source,fileStatus:'COMPLETED' as const};const w=mount(Editor,{props:{document:doc,writable:true,exportable:true}});await flushPromises();expect(w.get('fieldset.customs-fields').attributes('disabled')).toBeDefined();expect(w.text()).toContain('已完成 · 仅可查看');expect(w.findAll('button').some(b=>b.text()==='保存')).toBe(false);w.unmount()
  const copy=mount(Editor,{props:{document:doc,copy:true,writable:true,exportable:true}});await flushPromises();expect(copy.get('fieldset.customs-fields').attributes('disabled')).toBeUndefined();copy.unmount()
 })
 it('renumbers saved documents when the document date changes',async()=>{
  const w=mount(Editor,{props:{writable:true,exportable:true,document:{...source,documentNo:'SYN202611041234',fields:{...source.fields,sellerPrefix:'SYN',date:'2026-11-04',fbaNo:'FBA-1234',contractNo:'SYN202611041234',orderNo:'SYN202611041234'}}}});await flushPromises()
  const date=w.findComponent({name:'DatePicker'});expect(date.exists()).toBe(true);date.vm.$emit('update:modelValue','2026-11-05');await flushPromises()
  expect((w.get('.document-number input').element as HTMLInputElement).value).toBe('SYN202611051234');expect((w.get('.field-contractNo input').element as HTMLInputElement).value).toBe('SYN202611051234');expect((w.get('.field-orderNo input').element as HTMLInputElement).value).toBe('SYN202611051234');w.unmount()
 })
 it('links signing place to source place and displays automatic packing sequence',async()=>{
  const w=render({document:{...source,items:[{...source.items[0],sourcePlace:'泉州市',cartonNo:'old'},{...source.items[0],sourcePlace:'泉州市',cartonNo:'old2'}]}});await flushPromises()
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='合同')!.trigger('click');expect((w.get('.field-signPlace input').element as HTMLInputElement).value).toBe('泉州市');expect(w.get('.field-signPlace input').attributes('readonly')).toBeDefined()
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='装箱单')!.trigger('click');expect(w.findAll('tbody tr').map(r=>r.findAll('td')[1]?.text())).toEqual(['1','2']);expect(w.find('input[aria-label="商品 1 箱号范围"]').exists()).toBe(false);w.unmount()
 })

 it('defaults shared declaration values and uses unit selects for manual and selected products',async()=>{
  const w=render();await flushPromises()
  for(const [key,value] of Object.entries({origin:'中国',destination:'美国',sourcePlace:'厦门市',exemption:'照章征税'}))expect((w.get(`.field-item-${key} input`).element as HTMLInputElement).value).toBe(value)
  await w.findAll('button').find(b=>b.text()==='手动添加商品')!.trigger('click')
  expect(w.get('.customs-line-editor select[aria-label="成交单位"]').findAll('option').map(o=>o.text())).toEqual(['件','个','套'])
  expect((w.get('.customs-line-editor select').element as HTMLSelectElement).value).toBe('件')
  expect(w.findAll('.customs-line-editor details')).toHaveLength(2);expect(w.findAll('.customs-line-editor details[open]')).toHaveLength(0)
  const labels=w.findAll('.customs-line-editor label');await labels.find(l=>l.text().startsWith('SKU'))!.get('input').setValue('SYN-MANUAL');await labels.find(l=>l.text().startsWith('中文申报品名'))!.get('input').setValue('测试产品');await w.get('.customs-line-editor select').setValue('套')
  await w.findAll('button').find(b=>b.text()==='保存到本票')!.trigger('click');expect((w.get('select.customs-inline-unit').element as HTMLSelectElement).value).toBe('套');await saveReady(w);await flushPromises()
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({items:[expect.objectContaining({unit:'套',origin:'中国',destination:'美国',sourcePlace:'厦门市',exemption:'照章征税'})]}),undefined);w.unmount()
 })
 it('preserves historical declaration values and nonstandard units when opened',async()=>{
  const w=render({document:{...source,items:[{...source.items[0],origin:'越南',destination:'加拿大',sourcePlace:'泉州市',exemption:'其他',unit:'千克'}]}});await flushPromises()
  expect((w.get('.field-item-origin input').element as HTMLInputElement).value).toBe('越南');expect((w.get('select.customs-inline-unit').element as HTMLSelectElement).value).toBe('千克');expect(w.get('select.customs-inline-unit').text()).toContain('千克（原记录）');w.unmount()
 })

 it('requires shipper, USD freight and six line fields before saving',async()=>{
  const w=render();await flushPromises();const save=()=>w.findAll('button').find(b=>b.text()==='保存草稿')!.trigger('click')
  await save();expect(w.text()).toContain('请选择境内发货人');expect(api.save).not.toHaveBeenCalled()
  await w.get('select[aria-label="境内发货人"]').setValue('default');await save();expect(w.text()).toContain('请填写运费金额');expect(api.save).not.toHaveBeenCalled();w.unmount()
  const v=render({document:source});await flushPromises();await v.get('input[aria-label="运费金额"]').setValue('25.50');await v.findAll('button').find(b=>b.text()==='保存草稿')!.trigger('click');expect(v.text()).toContain('商品 1：请填写成交数量');expect(api.save).not.toHaveBeenCalled();v.unmount()
 })
 it('sums multiple SKU weights, moves common fields, and preserves hidden export data',async()=>{
  const rows=[{...source.items[0],quantity:'2',unit:'个',price:'1.5',cartons:'1',grossWeight:'3.1',netWeight:'2.1',origin:'CN',cartonNo:'1',quantity2:'5',unit2:'千克'},{...source.items[0],quantity:'3',unit:'个',price:'2',cartons:'2',grossWeight:'4.2',netWeight:'3.2',origin:'US'}]
  const w=render({document:{...source,fields:{...source.fields,freight:'USD/12',relatedParty:'否',sourcePlace:'原货源地',exemption:'照章征税'},items:rows}});await flushPromises()
  expect((w.get('.field-grossWeight input').element as HTMLInputElement).value).toBe('7.3');expect((w.get('.field-netWeight input').element as HTMLInputElement).value).toBe('5.3')
  expect(w.find('.field-relatedParty').exists()).toBe(false);expect(w.get('.field-item-origin').text()).toContain('当前各 SKU 不同')
  const headers=w.findAll('.customs-items-table th').map(n=>n.text());expect(headers.some(t=>t.includes('箱号范围')||t.includes('第二数量')||t.includes('原产国'))).toBe(false);expect(w.findAll('.customs-items-table th .customs-required')).toHaveLength(6)
  await w.get('.field-item-destination input').setValue('美国');await w.findAll('button').find(b=>b.text()==='保存草稿')!.trigger('click');await flushPromises()
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({fields:expect.objectContaining({grossWeight:'7.3',netWeight:'5.3',freight:'USD/12',relatedParty:'否',sourcePlace:'原货源地'}),items:[expect.objectContaining({origin:'CN',destination:'美国',cartonNo:'1',quantity2:'5',unit2:'千克'}),expect.objectContaining({origin:'US',destination:'美国'})]}),'syn');w.unmount()
 })

 it('matches normalized warehouse codes and keeps the address shared across sheets',async()=>{
  api.warehouses.mockResolvedValue([{code:'SYN1',address:'Synthetic warehouse address',verifiedOn:'2026-01-01'}])
  const w=render();await flushPromises();await w.get('input[aria-label="FBA 仓库代码"]').setValue(' syn1 ');await flushPromises()
  expect((w.get('.warehouse-address-block textarea').element as HTMLTextAreaElement).value).toBe('Synthetic warehouse address')
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='发票')!.trigger('click')
  expect((w.get('.field-buyerAddress textarea').element as HTMLTextAreaElement).value).toBe('Synthetic warehouse address');w.unmount()
 })
 it('does not overwrite manual or historical addresses without confirmation',async()=>{
  api.warehouses.mockResolvedValue([{code:'SYN1',address:'New warehouse address'}]);vi.mocked(confirmAction).mockResolvedValue(false)
  const w=render({document:{...source,fields:{...source.fields,warehouse:'SYN1',buyerAddress:'Existing document address'}}});await flushPromises()
  expect((w.get('.warehouse-address-block textarea').element as HTMLTextAreaElement).value).toBe('Existing document address')
  expect(confirmAction).toHaveBeenCalled();expect(w.text()).toContain('已保留本票地址');w.unmount()
 })
 it('ignores late lookups and allows retry after network failure',async()=>{
  const w=render();await flushPromises();let resolve!:(r:unknown[])=>void
  api.warehouses.mockImplementationOnce(()=>new Promise(r=>{resolve=r}))
  await w.get('input[aria-label="FBA 仓库代码"]').setValue('SYN2')
  api.warehouses.mockResolvedValueOnce([{code:'SYN3',address:'Newest address'}]);await w.get('input[aria-label="FBA 仓库代码"]').setValue('SYN3');await flushPromises()
  resolve([{code:'SYN2',address:'Old address'}]);await flushPromises();expect((w.get('.warehouse-address-block textarea').element as HTMLTextAreaElement).value).toBe('Newest address')
  api.warehouses.mockRejectedValueOnce(new Error('网络失败'));await w.get('input[aria-label="FBA 仓库代码"]').setValue('SYN4');await flushPromises();expect(w.text()).toContain('网络失败');expect(w.findAll('button').some(b=>b.text()==='重试查询')).toBe(true);w.unmount()
 })
 it('requires FBA fields, hides duplicate prefix and restricts adding products to declaration',async()=>{
  const w=render();await flushPromises()
  expect(w.find('input[aria-label="公司简称"]').exists()).toBe(false)
  for(const label of ['FBA 编号','FBA 仓库代码']){expect(w.get(`input[aria-label="${label}"]`).attributes('required')).toBeDefined();await w.get(`input[aria-label="${label}"]`).setValue('')}
  expect(w.get('.customs-paper-meta').text()).toContain('*')
  await saveReady(w);expect(api.save).not.toHaveBeenCalled();expect(w.text()).toContain('请填写 FBA 编号和 FBA 仓库代码后保存')
  for(const label of ['申报要素','合同','发票','装箱单']){await w.findAll('.ui-section-tabs button').find(b=>b.text()===label)!.trigger('click');expect(w.findAll('button').some(b=>['选择产品 SKU','手动添加商品'].includes(b.text()))).toBe(false)}
  w.unmount()
 })
 it('edits core values inline, shares totals across sheets and retains failed saves',async()=>{
  const w=render({document:source});await flushPromises()
  await w.get('input[aria-label="商品 1 成交数量"]').setValue('6')
  await w.get('input[aria-label="商品 1 申报单价"]').setValue('2.5')
  await w.get('input[aria-label="商品 1 箱数"]').setValue('3')
  expect(w.get('.customs-paper-total').text()).toContain('15.00');expect(w.get('.customs-paper-total').text()).toContain('件数：3')
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='装箱单')!.trigger('click')
  expect(w.find('input[aria-label="商品 1 箱数"]').exists()).toBe(false)
  expect(w.get('.customs-paper-total').text()).toContain('件数：3')
  await w.findAll('.ui-section-tabs button').find(b=>b.text()==='报关单')!.trigger('click')
  await w.get('input[aria-label="商品 1 总毛重（kg）"]').setValue('12.5')
  api.save.mockRejectedValueOnce(new Error('保存失败'))
  await saveReady(w);await flushPromises()
  expect((w.get('input[aria-label="商品 1 总毛重（kg）"]').element as HTMLInputElement).value).toBe('12.5')
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({items:[expect.objectContaining({quantity:'6',price:'2.5',cartons:'3',grossWeight:'12.5'})]}),'syn')
  expect((source.items[0] as Record<string,string>).quantity).toBe('');w.unmount()
 })
 it('locks broker fields, defaults declaration fields and allows air transport',async()=>{
  const w=render();await flushPromises()
  for(const key of ['customsPort','exportDate','declarationDate','recordNo','voyage','billNo','license','destinationPort','exitPort']){expect(w.get(`.field-${key}`).text()).toContain('免填写');expect(w.get(`.field-${key} input`).attributes('disabled')).toBeDefined()}
  expect((w.get('select[aria-label="运输方式"]').element as HTMLSelectElement).value).toBe('水路运输')
  await w.get('select[aria-label="运输方式"]').setValue('空运')
  expect((w.get('.field-supervision input').element as HTMLInputElement).value).toBe('跨境电商出口海外仓')
  expect((w.get('.field-taxNature input').element as HTMLInputElement).value).toBe('一般征税')
  expect((w.get('.field-tradeCountry input').element as HTMLInputElement).value).toBe('美国')
  expect(w.get('.field-documents textarea').classes()).toContain('customs-documents-red')
  await saveReady(w);await flushPromises()
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({fields:expect.objectContaining({transport:'空运',documents:expect.stringContaining('随附单证3：合同；发票；装箱单')})}),undefined);w.unmount()
 })
 it('edits only a selected shipper and preserves the form on failure',async()=>{
  const shipper={id:'s',name:'Synthetic Company',prefix:'SYN',address:'Synthetic address',phone:'',fax:''};api.shippers.mockResolvedValue([shipper]);const w=render();await flushPromises()
  expect(w.findAll('.customs-shipper-control button').some(b=>b.text()==='编辑')).toBe(false)
  await w.get('select[aria-label="境内发货人"]').setValue('s');await w.findAll('.customs-shipper-control button').find(b=>b.text()==='编辑')!.trigger('click')
  await w.get('#customs-shipper-form input').setValue('Synthetic Updated');api.updateShipper.mockRejectedValueOnce(new Error('保存失败'))
  await w.get('#customs-shipper-form').trigger('submit');await flushPromises();expect(w.get('#customs-shipper-form').text()).toContain('保存失败')
  api.updateShipper.mockResolvedValue({...shipper,name:'Synthetic Updated'});await w.get('#customs-shipper-form').trigger('submit');await flushPromises()
  expect(api.updateShipper).toHaveBeenLastCalledWith('s',expect.objectContaining({name:'Synthetic Updated'}));expect(w.find('#customs-shipper-form').exists()).toBe(false)
  expect((w.get('.field-producer input').element as HTMLInputElement).value).toBe('Synthetic Updated');w.unmount()
 })
 it('defaults US shipment fields and links the read-only invoice number to document number',async()=>{
  const w=render();await flushPromises();const input=(key:string)=>w.get(`.field-${key} input`)
  expect((input('currency').element as HTMLInputElement).value).toBe('USD');expect(input('currency').attributes('readonly')).toBeDefined()
  expect((input('terms').element as HTMLInputElement).value).toBe('c&f');expect((input('packaging').element as HTMLInputElement).value).toBe('纸制或纤维板制盒/箱');expect((input('destination').element as HTMLInputElement).value).toBe('美国')
  for(const key of ['grossWeight','netWeight']){expect(w.find(`.field-${key} .customs-required`).exists()).toBe(false);expect(input(key).attributes('readonly')).toBeDefined()}
  await w.get('.document-number input').setValue('SYN-INVOICE-123');await w.findAll('.ui-section-tabs button').find(b=>b.text()==='发票')!.trigger('click');expect((input('invoiceNo').element as HTMLInputElement).value).toBe('SYN-INVOICE-123');expect(input('invoiceNo').attributes('readonly')).toBeDefined();w.unmount()
 })
 it('uses five document sheets and generates linked numbers from shipper, date and FBA',async()=>{api.shippers.mockResolvedValue([{id:'s',name:'Synthetic Company',prefix:'SYN',address:'Synthetic address',phone:'',fax:''}]);const w=render();await flushPromises();expect(w.findAll('.ui-section-tabs button').map(b=>b.text())).toEqual(['报关单','申报要素','合同','发票','装箱单']);await w.get('select[aria-label="境内发货人"]').setValue('s');await w.get('input[aria-label="FBA 编号"]').setValue('FBA-TEST1234');const no=contractNumber({sellerPrefix:'SYN',date:new Date().toLocaleDateString('sv-SE'),fbaNo:'FBA-TEST1234'});expect((w.findAll('.customs-sheet-grid label').find(l=>l.text().startsWith('合同协议号'))!.get('input').element as HTMLInputElement).value).toBe(no);expect((w.findAll('.customs-sheet-grid label').find(l=>l.text().startsWith('生产销售单位'))!.get('input').element as HTMLInputElement).value).toBe('Synthetic Company');await w.findAll('.ui-section-tabs button').find(b=>b.text()==='合同')!.trigger('click');expect((w.findAll('.customs-sheet-grid label').find(l=>l.text().startsWith('境外收货人'))!.get('input').element as HTMLInputElement).value).toBe('Amazon.com Services.Inc');expect(cartonTotal([{cartons:'2'},{cartons:'3'}])).toBe(5);w.unmount()})
 it('requires all numbering inputs and preserves four-character suffix',()=>{expect(contractNumber({sellerPrefix:'xmh',date:'2026-11-04',fbaNo:'STAR-ABCDE0123'})).toBe('XMH202611040123');expect(contractNumber({sellerPrefix:'XMH',date:'2026-11-04',fbaNo:'12'})).toBe('')})
 it('retains failed drafts and persists later with version protection',async()=>{const w=render({document:source});await w.get('.document-number input').setValue('SYN-NEW');api.save.mockRejectedValueOnce(new Error('单据已变化'));await saveReady(w);await flushPromises();expect(w.text()).toContain('单据已变化');expect((w.get('.document-number input').element as HTMLInputElement).value).toBe('SYN-NEW');await saveReady(w);await flushPromises();expect(api.save).toHaveBeenLastCalledWith(expect.objectContaining({documentNo:'SYN-NEW',version:2}),'syn');expect(w.text()).toContain('单据已保存');w.unmount()})
 it('keeps numeric line inputs as strings and saves the completed line without render errors',async()=>{const w=render();await w.findAll('button').find(b=>b.text()==='手动添加商品')!.trigger('click');const labels=w.findAll('.customs-line-editor label');await labels.find(l=>l.text().startsWith('SKU'))!.get('input').setValue('SYN-NUM');await labels.find(l=>l.text().startsWith('中文申报品名'))!.get('input').setValue('合成测试');await labels.find(l=>l.text().startsWith('成交数量'))!.get('input').setValue('6');await labels.find(l=>l.text().startsWith('申报单价'))!.get('input').setValue('2.5');await w.findAll('button').find(b=>b.text()==='保存到本票')!.trigger('click');expect(w.get('.customs-paper-total').text()).toContain('15.00');await saveReady(w);await flushPromises();expect(api.save).toHaveBeenCalledWith(expect.objectContaining({items:[expect.objectContaining({quantity:'6',price:'2.5'})]}),undefined);w.unmount()})
 it('copies a document into an independent new draft',async()=>{const w=render({document:source,copy:true});expect((w.get('.document-number input').element as HTMLInputElement).value).toBe('');await w.get('.document-number input').setValue('SYN-COPY');await saveReady(w);await flushPromises();expect(api.save.mock.calls[0]![1]).toBeUndefined();expect(source.documentNo).toBe('SYN');w.unmount()})
 it('adds selected SKU profiles as editable snapshots and leaves shipment quantities blank',async()=>{api.list.mockResolvedValue({items:[{variantId:'v',skuCode:'SKU-TEST',skuName:'Synthetic',customs:{nameZh:'合成申报品名',chinaCode:'1234567890',unit:'件',originCountry:'CN',declarationElements:'材质：合成'}}],totalCount:1});const w=render();await w.findAll('button').find(b=>b.text()==='选择产品 SKU')!.trigger('click');await flushPromises();await w.get('input[aria-label="选择 SKU-TEST"]').setValue(true);await w.findAll('button').find(b=>b.text()==='添加所选（1）')!.trigger('click');expect(w.get('.customs-items-table').text()).toContain('合成申报品名');await saveReady(w);await flushPromises();expect(api.save).toHaveBeenCalledWith(expect.objectContaining({items:[expect.objectContaining({sku:'SKU-TEST',quantity:'1',grossWeight:'1'})]}),undefined);w.unmount()})
 it('copies dynamic HS fields and measurement unit into an independent document snapshot',async()=>{
  const customs={nameZh:'合成品名',chinaCode:'6307909000',schemaCode:'6307909000',elementFields:[{key:'articleNumber',label:'货号'}],elements:{articleNumber:'TEST-01'},measurementUnit:'千克/个',unit:'个'}
  api.list.mockResolvedValue({items:[{variantId:'v',skuCode:'SYN-DYN',customs}],totalCount:1});const w=render();await w.findAll('button').find(b=>b.text()==='选择产品 SKU')!.trigger('click');await flushPromises();await w.get('input[aria-label="选择 SYN-DYN"]').setValue(true);await w.findAll('button').find(b=>b.text()==='添加所选（1）')!.trigger('click');customs.elements.articleNumber='CHANGED'
  await w.findAll('button').find(b=>b.text()==='申报要素')!.trigger('click');expect(w.get('.customs-element-table').text()).toContain('TEST-01');expect(w.get('.customs-element-table').text()).toContain('千克/个');expect(w.get('.customs-element-table').text()).not.toContain('纤维成分')
  await saveReady(w);await flushPromises();expect(api.save).toHaveBeenCalledWith(expect.objectContaining({items:[expect.objectContaining({elementsVersion:'2',schemaCode:'6307909000',articleNumber:'TEST-01',unit:'件',measurementUnit:'千克/个'})]}),undefined);w.unmount()
 })
 it('keeps read-only documents immutable and shows missing export requirements',async()=>{const w=render({document:source,writable:false});expect(w.findAll('button').some(b=>b.text()==='保存草稿')).toBe(false);expect(w.get('fieldset').attributes('disabled')).toBeDefined();expect(w.find('.customs-inline-input').exists()).toBe(false);await w.findAll('button').find(b=>b.text().startsWith('查看导出检查'))!.trigger('click');expect(w.text()).toContain('合同协议号');expect(w.findAll('button').find(b=>b.text()==='导出 Excel')!.attributes('disabled')).toBeDefined();expect(missingFields({},[])).toContain('至少添加一项商品');w.unmount()})
})
