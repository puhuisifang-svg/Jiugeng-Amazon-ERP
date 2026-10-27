import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/api/client'
import { confirmAction } from '@/state/uiConfirmation'
import WatchStoresView from './WatchStoresView.vue'
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true,user:{value:{displayName:'Tester'}}}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn()}))
vi.mock('@/api/client',()=>({apiRequest:vi.fn()}))
const {collect}=vi.hoisted(()=>({collect:vi.fn(async()=>true)}))
vi.mock('@/composables/useCollectionStatus',()=>({useCollectionStatus:()=>({error:{value:''},enabled:{value:true},state:()=>undefined,label:()=>'',pending:()=>false,collect,refresh:vi.fn()})}))
const store={id:'store1',storeKey:'TESTSELLER',storeName:'Test Store',status:'ACTIVE',version:3,remark:'Original'}
async function setup(){
 HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()
 const w=mount(WatchStoresView,{global:{stubs:{RouterLink:RouterLinkStub,WatchStoreDetail:true,CollectionPanel:true}}});await flushPromises();return w
}
beforeEach(()=>{vi.clearAllMocks();vi.mocked(apiRequest).mockImplementation(async(path)=>path==='/v1/watch/stores'?[store]:path==='/v1/watch/events'?[]:store)})
describe('watch list actions',()=>{
 it('links the store name to its Amazon storefront and underlines the today action',async()=>{
  const w=await setup();const link=w.get('a[aria-label="Test Store（打开亚马逊店铺）"]')
  expect(link.attributes('href')).toBe('https://www.amazon.com/s?me=TESTSELLER&marketplaceID=ATVPDKIKX0DER')
  expect(link.attributes('target')).toBe('_blank')
  expect(w.get('[aria-label="Test Store 今日上架 0"]').classes()).toContain('mw-count-link')
  await w.get('[aria-label="编辑 Test Store 备注"]').trigger('click')
  expect(w.get('[aria-label="编辑店铺备注"]').classes()).toContain('mw-remark-popover');w.unmount()
 })
 it('edits only the remark with optimistic version and keeps input on failure',async()=>{
  const w=await setup();await w.get('[aria-label="编辑 Test Store 备注"]').trigger('click')
  await w.get('#watch-remark-form textarea').setValue('New note')
  vi.mocked(apiRequest).mockRejectedValueOnce(new Error('记录已更新'))
  await w.get('#watch-remark-form').trigger('submit');await flushPromises()
  expect(w.text()).toContain('记录已更新');expect((w.get('#watch-remark-form textarea').element as HTMLTextAreaElement).value).toBe('New note')
  await w.get('#watch-remark-form').trigger('submit');await flushPromises()
  expect(apiRequest).toHaveBeenCalledWith('/v1/watch/stores/store1/remark',{method:'PATCH',body:JSON.stringify({version:3,remark:'New note'})});w.unmount()
 })
 it('does not remove on cancel and sends the version after confirmation',async()=>{
  const w=await setup();const remove=w.findAll('button').find(b=>b.text()==='移除')!
  vi.mocked(confirmAction).mockResolvedValueOnce(false);await remove.trigger('click');await flushPromises()
  expect(vi.mocked(apiRequest).mock.calls.some(c=>c[1]?.method==='DELETE')).toBe(false)
  vi.mocked(confirmAction).mockResolvedValueOnce(true);await remove.trigger('click');await flushPromises()
  expect(apiRequest).toHaveBeenCalledWith('/v1/watch/stores/store1?version=3',{method:'DELETE'});w.unmount()
 })
 it('opens today products in the shared detail instead of navigating to events',async()=>{
  const w=await setup();await w.get('[aria-label="Test Store 今日上架 0"]').trigger('click')
  expect(w.findComponent({name:'WatchStoreDetail'}).props('newProducts')).toEqual([])
  expect(w.findComponent({name:'WatchStoreDetail'}).props('store')).toEqual(store);await w.get('[aria-label="Test Store 今日新品 0"]').trigger('click');expect(w.findComponent({name:'WatchStoreDetail'}).props('initialTab')).toBe('novelty');w.unmount()
 })
 it('only refreshes selected stores and removes global events and more entries',async()=>{
  const w=await setup();expect(w.findAll('button').some(b=>['更新','设置'].includes(b.text()))).toBe(false);const refresh=w.get('[aria-label="更新所选店铺"]');expect(refresh.attributes('disabled')).toBeDefined()
  expect(w.findAll('a').some(a=>a.text()==='上架记录')).toBe(false);expect(w.find('summary').exists()).toBe(false)
  await w.get('[aria-label="选择 Test Store"]').setValue(true);expect(refresh.attributes('disabled')).toBeUndefined()
  await refresh.trigger('click');await flushPromises();expect(collect).toHaveBeenCalledExactlyOnceWith('store1')
  expect(w.text()).toContain('已提交 1 家店铺采集');w.unmount()
 })

})
