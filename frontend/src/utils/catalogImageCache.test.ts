import { afterEach, describe, expect, it, vi } from 'vitest'
import { cachedImage, clearImageCache, peekCachedImage, IMAGE_SESSION_CHANGED } from './catalogImageCache'
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
    await Promise.resolve();a.abort();await expect(first).rejects.toMatchObject({name:'AbortError'});expect(shared.aborted).toBe(false)
    finish(new Blob(['image']));await second
    const failed=vi.fn().mockRejectedValue(new Error('forbidden'))
    await expect(cachedImage('a','denied',failed)).rejects.toThrow('forbidden')
    await expect(cachedImage('a','denied',failed)).rejects.toThrow('forbidden');expect(failed).toHaveBeenCalledTimes(2)
  })
  it('expires cached data and aborts orphaned transfers',async()=>{
    vi.useFakeTimers();const load=vi.fn(async()=>new Blob(['x']))
    await cachedImage('a','id',load);vi.advanceTimersByTime(60001);await cachedImage('a','id',load);expect(load).toHaveBeenCalledTimes(2)
    let shared!:AbortSignal;const ctrl=new AbortController()
    const p=cachedImage('a','pending',signal=>{shared=signal;return new Promise<Blob>((_resolve,reject)=>signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true}))},ctrl.signal)
    await Promise.resolve();ctrl.abort();await expect(p).rejects.toMatchObject({name:'AbortError'});expect(shared.aborted).toBe(true)
  })
  it('limits parallel transfers and removes cancelled queued work', async () => {
    let active = 0, peak = 0
    const finish: Array<() => void> = []
    const load = vi.fn(() => new Promise<Blob>(resolve => {
      active++; peak = Math.max(peak, active)
      finish.push(() => { active--; resolve(new Blob(['image'])) })
    }))
    const pending = Array.from({ length: 8 }, (_, i) => cachedImage('limit', `image-${i}`, load))
    const cancelled = new AbortController()
    const extra = cachedImage('limit', 'cancelled', load, cancelled.signal)
    const complete = Promise.allSettled([...pending, extra])
    cancelled.abort()
    await Promise.resolve();expect(load).toHaveBeenCalledTimes(5)
    while (finish.length) {
      finish.shift()!()
      await new Promise(resolve => setTimeout(resolve, 0))
    }
    const results = await complete
    expect(peak).toBe(5)
    expect(load).toHaveBeenCalledTimes(8)
    expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(8)
    expect(results[8]).toMatchObject({ status: 'rejected', reason: { name: 'AbortError' } })
  })
  it('reserves capacity for previews and runs visible work ahead of prefetch', async () => {
    const starts: string[] = [], finish: Array<() => void> = []
    const load = (id: string) => async () => { starts.push(id); return new Promise<Blob>(resolve => finish.push(() => resolve(new Blob([id])))) }
    const pending = Array.from({length:5},(_,i) => cachedImage('p',String(i),load(String(i))))
    pending.push(cachedImage('p','near',load('near'),undefined,'prefetch'))
    pending.push(cachedImage('p','visible',load('visible'),undefined,'visible'))
    pending.push(cachedImage('p','large',load('large'),undefined,'preview'))
    await Promise.resolve();expect(starts).toEqual(['0','1','2','3','4','large'])
    finish.shift()!();await new Promise(resolve => setTimeout(resolve,0));expect(starts[6]).toBe('visible')
    while(finish.length){finish.shift()!();await new Promise(resolve => setTimeout(resolve,0))}
    await Promise.all(pending)
    expect(peekCachedImage('p','large')).toBeInstanceOf(Blob);expect(peekCachedImage('other','large')).toBeUndefined()
  })

})
