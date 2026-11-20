import { afterEach, describe, expect, it, vi } from 'vitest'
import { cachedImage, clearImageCache, IMAGE_SESSION_CHANGED } from './catalogImageCache'
afterEach(() => { clearImageCache(); vi.useRealTimers() })
describe('protected image cache', () => {
  it('deduplicates requests and reuses bytes only inside the same session', async () => {
    const load = vi.fn(async () => new Blob(['image']))
    const [a,b] = await Promise.all([cachedImage('a','id',load),cachedImage('a','id',load)])
    expect(a).toBe(b); await cachedImage('a','id',load); expect(load).toHaveBeenCalledTimes(1)
    await cachedImage('b','id',load); expect(load).toHaveBeenCalledTimes(2)
    window.dispatchEvent(new Event(IMAGE_SESSION_CHANGED));await cachedImage('b','id',load);expect(load).toHaveBeenCalledTimes(3)
  })
  it('cancels only after all consumers leave and never caches failed responses', async () => {
    let finish!: (b:Blob)=>void;let shared!:AbortSignal
    const load=vi.fn((signal:AbortSignal)=>{shared=signal;return new Promise<Blob>(r=>{finish=r})})
    const a=new AbortController(),b=new AbortController()
    const first=cachedImage('a','id',load,a.signal),second=cachedImage('a','id',load,b.signal)
    a.abort();await expect(first).rejects.toMatchObject({name:'AbortError'});expect(shared.aborted).toBe(false)
    finish(new Blob(['image']));await second
    const failed=vi.fn().mockRejectedValue(new Error('forbidden'))
    await expect(cachedImage('a','denied',failed)).rejects.toThrow('forbidden')
    await expect(cachedImage('a','denied',failed)).rejects.toThrow('forbidden');expect(failed).toHaveBeenCalledTimes(2)
  })
  it('expires cached data and aborts orphaned transfers',async()=>{
    vi.useFakeTimers();const load=vi.fn(async()=>new Blob(['x']))
    await cachedImage('a','id',load);vi.advanceTimersByTime(60001);await cachedImage('a','id',load);expect(load).toHaveBeenCalledTimes(2)
    let shared!:AbortSignal;const ctrl=new AbortController()
    const p=cachedImage('a','pending',signal=>{shared=signal;return new Promise(()=>{})},ctrl.signal)
    ctrl.abort();await expect(p).rejects.toMatchObject({name:'AbortError'});expect(shared.aborted).toBe(true)
  })
})
