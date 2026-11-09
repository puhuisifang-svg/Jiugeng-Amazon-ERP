import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import {reactive} from 'vue'
import FactoryEditor from './FactoryEditor.vue'
const mocks=vi.hoisted(()=>({command:vi.fn(),preview:vi.fn(),list:vi.fn(),operations:vi.fn(),canAccess:vi.fn()}))
vi.mock('@/api/factory',()=>({factoryApi:mocks}))
vi.mock('@/state/session',()=>({session:{canAccess:mocks.canAccess}}))
const global={stubs:{AccessDialog:{template:'<div><slot/><slot name="footer"/></div>'},FactoryPicker:true,DatePicker:true},directives:{singleSelect:{}}}
beforeEach(()=>{vi.clearAllMocks();mocks.canAccess.mockReturnValue(true);mocks.operations.mockResolvedValue([]);mocks.command.mockResolvedValue({id:'synthetic'})})
describe('factory editor',()=>{
 it('defaults new employee team and preserves existing employee data while hiding operations',async()=>{
  const w=mount(FactoryEditor,{props:{kind:'employees',mode:'save'},global});
  expect(w.get('select').element.value).toBe('缝制组');expect(w.findAll('option').map(o=>o.text())).toEqual(['缝制组','包装组','铁艺组']);expect(w.text()).not.toContain('常做工序');
  await w.get('select').setValue('包装组');await w.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({team:'包装组',operations:''}));w.unmount();
  const old=mount(FactoryEditor,{props:{kind:'employees',mode:'save',record:{id:'e1',version:1,status:'ACTIVE',date:'2026-11-06',team:'原有班组',operations:'原有工序'}},global});
  expect(old.get('select').element.value).toBe('原有班组');await old.get('form').trigger('submit');await flushPromises();expect(mocks.command).toHaveBeenLastCalledWith('employees','save',expect.objectContaining({team:'原有班组',operations:'原有工序'}));old.unmount();
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
