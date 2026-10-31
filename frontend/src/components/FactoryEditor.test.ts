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
 it('defaults draft payroll disbursement to advance and reuses request id after a failed response',async()=>{
  mocks.command.mockRejectedValueOnce(new Error('网络连接失败'));const w=mount(FactoryEditor,{props:{kind:'payroll',mode:'pay',record:{id:'p',version:2,status:'DRAFT',date:'2026-01',amount:'100',remaining:'100'}},global});await w.get('input[type="number"]').setValue('30');await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('网络连接失败');await w.get('form').trigger('submit');await flushPromises();expect(mocks.command.mock.calls[0]?.[2].advance).toBe(true);expect(mocks.command.mock.calls[0]?.[2].requestId).toBe(mocks.command.mock.calls[1]?.[2].requestId);expect(w.emitted('saved')).toHaveLength(1)
 })
 it('copies reactive adjustment data without modifying the source payroll',async()=>{
  const record=reactive({id:'p',version:0,status:'DRAFT',date:'2026-01',adjustments:[{direction:'ADD' as const,amount:'20',remark:'Synthetic subsidy'}]});const w=mount(FactoryEditor,{props:{kind:'payroll',mode:'adjust',record},global});await w.get('input[type="number"]').setValue('35');expect(record.adjustments[0]?.amount).toBe('20');expect((w.get('input[type="number"]').element as HTMLInputElement).value).toBe('35')
 })
 it('does not offer self review without permission',()=>{mocks.canAccess.mockReturnValue(false);const w=mount(FactoryEditor,{props:{kind:'reports',mode:'save'},global});expect(w.find('option[value="REVIEWED"]').exists()).toBe(false);expect(w.find('option[value="DRAFT"]').exists()).toBe(true)})
})
