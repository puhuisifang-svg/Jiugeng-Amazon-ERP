import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach,afterEach} from 'vitest'
import {reactive} from 'vue'
import FactoryEditor from './FactoryEditor.vue'
const mocks=vi.hoisted(()=>({command:vi.fn(),preview:vi.fn(),list:vi.fn(),operations:vi.fn(),canAccess:vi.fn()}))
vi.mock('@/api/factory',()=>({factoryApi:mocks}))
vi.mock('@/state/session',()=>({session:{canAccess:mocks.canAccess}}))
const global={stubs:{AccessDialog:{template:'<div><slot/><slot name="footer"/></div>'},FactoryPicker:true,DatePicker:true},directives:{singleSelect:{}}}
beforeEach(()=>{vi.clearAllMocks();mocks.canAccess.mockReturnValue(true);mocks.operations.mockResolvedValue([]);mocks.command.mockResolvedValue({id:'synthetic'})})
afterEach(()=>vi.unstubAllGlobals())
describe('factory editor',()=>{
 it('saves optional identity and insurance and replaces operation with one project field',async()=>{
  const w=mount(FactoryEditor,{props:{kind:'employees',mode:'save'},global});await w.get('input[placeholder="身份证号（选填）"]').setValue('00000000000000000X');await w.findAll('select')[0]!.setValue(true);await w.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({identityNumber:'00000000000000000X',insured:true}));w.unmount();
  const r=mount(FactoryEditor,{props:{kind:'rates',mode:'save',record:{id:'legacy',version:0,status:'ACTIVE',date:'2026-01-01',project:'Synthetic',operation:'Sewing'}},global});expect(r.text()).not.toContain('工序');expect((r.get('input[placeholder="工人熟悉的加工名称，规格不同请分开"]').element as HTMLInputElement).value).toBe('Synthetic / Sewing');await r.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('rates','save',expect.objectContaining({project:'Synthetic / Sewing'}));expect(mocks.command.mock.calls.at(-1)![2]).not.toHaveProperty('operation');r.unmount()
 })

 it('opens every factory editor and submits on HTTP without native randomUUID',async()=>{
  const getRandomValues=globalThis.crypto.getRandomValues.bind(globalThis.crypto);vi.stubGlobal('crypto',{getRandomValues});
  for(const kind of ['employees','reports','rates','payroll'] as const){const w=mount(FactoryEditor,{props:{kind,mode:kind==='payroll'?'generate':'save'},global});await flushPromises();expect(w.find('form').exists()).toBe(true);await w.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith(kind,kind==='payroll'?'generate':'save',expect.objectContaining({requestId:expect.stringMatching(/^[0-9a-f-]{36}$/)}));w.unmount()}
 })

 it('defaults new employee team and preserves existing employee data while hiding operations',async()=>{
  const w=mount(FactoryEditor,{props:{kind:'employees',mode:'save'},global});
  expect(w.findAll('label').find(l=>l.text().startsWith('班组'))!.get('select').element.value).toBe('缝制组');expect(w.findAll('label').find(l=>l.text().startsWith('班组'))!.get('select').findAll('option').map(o=>o.text())).toEqual(['缝制组','包装组','铁艺组']);expect(w.text()).not.toContain('常做工序');expect(w.findAll('label').at(-1)!.text()).toContain('班组');
  await w.findAll('label').find(l=>l.text().startsWith('班组'))!.get('select').setValue('包装组');await w.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({team:'包装组',insured:false}));w.unmount();
  const old=mount(FactoryEditor,{props:{kind:'employees',mode:'save',record:{id:'e1',version:1,status:'ACTIVE',date:'2026-11-06',team:'原有班组'}},global});
  expect(old.findAll('label').find(l=>l.text().startsWith('班组'))!.get('select').element.value).toBe('原有班组');await old.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({team:'原有班组'}));old.unmount();
 })
 it('defaults draft payroll disbursement to advance and reuses request id after a failed response',async()=>{
  mocks.command.mockRejectedValueOnce(new Error('网络连接失败'));const w=mount(FactoryEditor,{props:{kind:'payroll',mode:'pay',record:{id:'p',version:2,status:'DRAFT',date:'2026-01',amount:'100',remaining:'100'}},global});await w.get('input[type="number"]').setValue('30');await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('网络连接失败');await w.get('form').trigger('submit');await flushPromises();expect(mocks.command.mock.calls[0]?.[2].advance).toBe(true);expect(mocks.command.mock.calls[0]?.[2].requestId).toBe(mocks.command.mock.calls[1]?.[2].requestId);expect(w.emitted('saved')).toHaveLength(1)
 })
 it('copies reactive adjustment data without modifying the source payroll',async()=>{
  const record=reactive({id:'p',version:0,status:'DRAFT',date:'2026-01',adjustments:[{direction:'ADD' as const,amount:'20',remark:'Synthetic subsidy'}]});const w=mount(FactoryEditor,{props:{kind:'payroll',mode:'adjust',record},global});await w.get('input[type="number"]').setValue('35');expect(record.adjustments[0]?.amount).toBe('20');expect((w.get('input[type="number"]').element as HTMLInputElement).value).toBe('35')
 })
 it('does not offer self review without permission',()=>{mocks.canAccess.mockReturnValue(false);const w=mount(FactoryEditor,{props:{kind:'reports',mode:'save'},global});expect(w.find('option[value="REVIEWED"]').exists()).toBe(false);expect(w.find('option[value="DRAFT"]').exists()).toBe(true)})
})

it('preserves bank account text while saving employee details',async()=>{
 const w=mount(FactoryEditor,{props:{kind:'employees',mode:'save'},global});
 await w.get('input[placeholder="银行名称及支行（选填）"]').setValue('合成开户行');
 await w.get('input[placeholder="银行卡号（选填）"]').setValue('0012345678901234567');
 await w.get('form').trigger('submit');await flushPromises();
 expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({bankName:'合成开户行',bankAccount:'0012345678901234567'}));w.unmount();
})
