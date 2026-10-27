import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/api/client'
import { useCollectionStatus, type CollectionState } from './useCollectionStatus'
vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/api/client', () => ({ apiRequest: vi.fn() }))
let next: CollectionState[], status: ReturnType<typeof useCollectionStatus>
const completed = vi.fn(), updated = vi.fn()
function row(targetId='s1', state='SUCCEEDED', createdAt='old'): CollectionState { return { targetId, status:state, createdAt, reason:null } }
async function setup() {
 const wrapper=mount(defineComponent({setup(){status=useCollectionStatus('STORE',updated,completed);return()=>null}}))
 await flushPromises();return wrapper
}
beforeEach(()=>{vi.useFakeTimers();vi.clearAllMocks();next=[row()];vi.mocked(apiRequest).mockImplementation(async(path)=>path==='/v1/collection/status'?{sources:[{provider:'amazon-public-us',approved:true}]}:path.includes('/targets')?next:undefined)})
afterEach(()=>vi.useRealTimers())
describe('manual collection feedback',()=>{
 it('keeps queued feedback over the previous completion and prevents duplicate submissions',async()=>{
  const w=await setup();await status.collect('s1')
  expect(status.label('s1')).toBe('排队中…');expect(status.pending('s1')).toBe(true)
  await status.collect('s1');expect(vi.mocked(apiRequest).mock.calls.filter(c=>c[1]?.method==='POST')).toHaveLength(1)
  expect(completed).not.toHaveBeenCalled()
  next=[row('s1','RUNNING','new')];await status.refresh();expect(status.label('s1')).toBe('采集中…')
  next=[row('s1','SUCCEEDED','new')];await status.refresh();expect(status.pending('s1')).toBe(false)
  expect(completed).toHaveBeenCalledExactlyOnceWith('s1',next[0]);await status.refresh();expect(completed).toHaveBeenCalledTimes(1);w.unmount()
 })
 it('reports a fast completion without requiring an intermediate running poll',async()=>{
  const w=await setup();await status.collect('s1');next=[row('s1','PARTIAL','new')];await status.refresh()
  expect(completed).toHaveBeenCalledWith('s1',next[0]);w.unmount()
 })
 it('tracks different stores independently and retains failure reasons',async()=>{
  const w=await setup();await status.collect('s1');await status.collect('s2')
  next=[{...row('s1','FAILED','new'),reason:'网络失败'},row('s2','RUNNING','new')];await status.refresh()
  expect(status.pending('s1')).toBe(false);expect(status.pending('s2')).toBe(true)
  expect(completed).toHaveBeenCalledWith('s1',expect.objectContaining({reason:'网络失败'}));w.unmount()
 })
 it('keeps the current request pending when status polling fails',async()=>{
  const w=await setup();await status.collect('s1');vi.mocked(apiRequest).mockRejectedValueOnce(new Error('状态连接中断'))
  await status.refresh();expect(status.error.value).toBe('状态连接中断');expect(status.pending('s1')).toBe(true);expect(completed).not.toHaveBeenCalled();w.unmount()
 })
 it('releases submit failures and permits retry',async()=>{
  const w=await setup();vi.mocked(apiRequest).mockRejectedValueOnce(new Error('排队失败'))
  expect(await status.collect('s1')).toBe(false);expect(status.error.value).toBe('排队失败');expect(status.pending('s1')).toBe(false)
  expect(await status.collect('s1')).toBe(true);w.unmount()
 })
 it('does not claim success when no new job starts; continues polling after two minutes',async()=>{
  const w=await setup();await status.collect('s1');await vi.advanceTimersByTimeAsync(125000)
  expect(status.label('s1')).toBe('尚未确认启动');expect(status.pending('s1')).toBe(false);expect(completed).not.toHaveBeenCalled()
  next=[row('s1','SUCCEEDED','new')];await status.refresh();expect(completed).toHaveBeenCalledTimes(1);w.unmount()
 })
})
