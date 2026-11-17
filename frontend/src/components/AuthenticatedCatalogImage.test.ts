import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Image from './AuthenticatedCatalogImage.vue'
import { catalogApi } from '@/api/catalog'
import { IMAGE_SESSION_CHANGED } from '@/utils/catalogImageCache'
vi.mock('@/api/catalog',()=>({catalogApi:{imageBlob:vi.fn(async()=>new Blob(['image']))}}))
afterEach(()=>{vi.unstubAllGlobals();vi.clearAllMocks();window.localStorage.clear()})
describe('catalog lazy images',()=>{
 it('waits until near viewport, preserves dimensions and cancels on unmount',async()=>{
  let enter!:(entries:{isIntersecting:boolean}[])=>void
  const disconnect=vi.fn()
  vi.stubGlobal('IntersectionObserver',class {constructor(callback:typeof enter){enter=callback}observe=vi.fn();disconnect=disconnect})
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
})
