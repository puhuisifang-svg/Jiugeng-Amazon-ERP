import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Image from './AuthenticatedCatalogImage.vue'
import { catalogApi } from '@/api/catalog'
import { IMAGE_SESSION_CHANGED, cachedImage, clearImageCache } from '@/utils/catalogImageCache'
const mode = vi.hoisted(() => ({ authMode: 'password' }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: mode }))
vi.mock('@/api/catalog',()=>({catalogApi:{imageBlob:vi.fn(async()=>new Blob(['image'])),externalImageBlob:vi.fn(async()=>new Blob(['external']))}}))
afterEach(()=>{mode.authMode = 'password';clearImageCache();vi.unstubAllGlobals();vi.clearAllMocks();window.localStorage.clear()})
describe('catalog lazy images',()=>{
 it('retains native external images only in the mock environment',async()=>{
  mode.authMode = 'mock'
  const w=mount(Image,{props:{imageUrl:'https://example.invalid/mock.png',alt:'模拟图片',variant:'original'}})
  await flushPromises();expect(w.get('img').attributes('src')).toBe('https://example.invalid/mock.png');expect(catalogApi.externalImageBlob).not.toHaveBeenCalled();w.unmount()
 })
 it('waits until near viewport, preserves dimensions and cancels on unmount',async()=>{
  let enter!:(entries:{isIntersecting:boolean}[])=>void
  const disconnect=vi.fn()
  vi.stubGlobal('IntersectionObserver',class {constructor(callback:typeof enter){enter=callback}observe=vi.fn();unobserve=vi.fn();disconnect=disconnect})
  URL.createObjectURL=vi.fn(()=> 'blob:synthetic');URL.revokeObjectURL=vi.fn()
  const w=mount(Image,{props:{imageId:'id',alt:'image'}});await flushPromises();expect(catalogApi.imageBlob).not.toHaveBeenCalled()
  enter([{isIntersecting:true}]);await flushPromises();expect(w.get('img').attributes('src')).toBe('blob:synthetic')
  const signal=vi.mocked(catalogApi.imageBlob).mock.calls[0]![2]!
  w.unmount();expect(signal.aborted).toBe(true);expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic')
 })
 it('clears already rendered content when the session ends',async()=>{
  URL.createObjectURL=vi.fn(()=> 'blob:synthetic');URL.revokeObjectURL=vi.fn()
  const w=mount(Image,{props:{imageId:'id',alt:'image',variant:'original'}});await flushPromises();expect(w.find('img').exists()).toBe(true)
  window.dispatchEvent(new Event(IMAGE_SESSION_CHANGED));await flushPromises();expect(w.find('img').exists()).toBe(false);w.unmount()
 })
 it('retries a failed transfer from the placeholder without opening its parent preview', async () => {
  vi.mocked(catalogApi.imageBlob).mockRejectedValueOnce(new Error('network'))
  URL.createObjectURL=vi.fn(()=> 'blob:synthetic');URL.revokeObjectURL=vi.fn()
  const w=mount(Image,{props:{imageId:'retry-image',alt:'合成图片',variant:'original'}})
  await flushPromises()
  await w.get('[role="button"]').trigger('click');await flushPromises()
  expect(catalogApi.imageBlob).toHaveBeenCalledTimes(2)
  expect(w.find('img').exists()).toBe(true)
  w.unmount()
 })
 it('shows the cached thumbnail while a full image is loading',async()=>{
  let finish!: (value:Blob)=>void
  await cachedImage('','progressive/thumbnail',async()=>new Blob(['small']))
  vi.mocked(catalogApi.imageBlob).mockImplementationOnce(()=>new Promise<Blob>(resolve=>{finish=resolve}))
  URL.createObjectURL=vi.fn().mockReturnValueOnce('blob:small').mockReturnValueOnce('blob:large');URL.revokeObjectURL=vi.fn()
  const w=mount(Image,{props:{imageId:'progressive',alt:'合成图片',variant:'original'}})
  await flushPromises();expect(w.get('img').attributes('src')).toBe('blob:small');expect(w.get('img').attributes('loading')).toBe('eager')
  finish(new Blob(['large']));await flushPromises();expect(w.get('img').attributes('src')).toBe('blob:large');expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:small');w.unmount()
 })
 it('loads external URLs through controlled storage and reacts to source changes',async()=>{
  URL.createObjectURL=vi.fn(()=> 'blob:controlled');URL.revokeObjectURL=vi.fn()
  const w=mount(Image,{props:{imageUrl:'https://example.invalid/first.png',alt:'合成图片',variant:'original'}})
  await flushPromises();expect(catalogApi.externalImageBlob).toHaveBeenCalledWith('https://example.invalid/first.png','original',expect.any(AbortSignal),'preview',undefined)
  expect(w.get('img').attributes('src')).toBe('blob:controlled')
  await w.setProps({imageUrl:'https://example.invalid/next.png'});await flushPromises();expect(catalogApi.externalImageBlob).toHaveBeenCalledTimes(2);w.unmount()
 })

})
