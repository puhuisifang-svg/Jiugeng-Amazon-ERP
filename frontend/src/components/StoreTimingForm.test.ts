import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/api/client'
import StoreTimingForm from './StoreTimingForm.vue'
vi.mock('@/api/client',()=>({apiRequest:vi.fn()}))
const timing={intervalDays:1,localTime:'03:00',timeZone:'America/Los_Angeles',version:0,nextAt:'2026-10-28T10:00:00Z',enabled:true}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(apiRequest).mockResolvedValue({...timing})})
describe('store collection timing',()=>{
 it('persists interval time and zone with version and retains failed edits',async()=>{
  const w=mount(StoreTimingForm,{props:{storeId:'store1',writable:true}});await flushPromises()
  await w.get('[aria-label="采集间隔天数"]').setValue(3);await w.get('input[type=time]').setValue('09:30');await w.get('select').setValue('Asia/Shanghai')
  vi.mocked(apiRequest).mockRejectedValueOnce(new Error('计划冲突'));await w.get('form').trigger('submit');await flushPromises()
  expect(w.text()).toContain('计划冲突');expect((w.get('input[type=time]').element as HTMLInputElement).value).toBe('09:30')
  vi.mocked(apiRequest).mockResolvedValueOnce({...timing,intervalDays:3,localTime:'09:30',timeZone:'Asia/Shanghai',version:1})
  await w.get('form').trigger('submit');await flushPromises()
  const last=vi.mocked(apiRequest).mock.calls.at(-1)!;expect(last[0]).toBe('/v1/collection/stores/store1/timing');expect(JSON.parse(last[1]!.body as string)).toMatchObject({intervalDays:3,localTime:'09:30',timeZone:'Asia/Shanghai',version:0})
  expect(w.emitted('saved')).toHaveLength(1);expect(w.get('form button').attributes('disabled')).toBeDefined();w.unmount()
 })
 it('does not permit a read-only user to save',async()=>{
  const w=mount(StoreTimingForm,{props:{storeId:'store1',writable:false}});await flushPromises();expect(w.get('fieldset').attributes('disabled')).toBeDefined()
  await w.get('form').trigger('submit');expect(apiRequest).toHaveBeenCalledTimes(1);w.unmount()
 })
})
