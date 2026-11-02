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
 it('only assigns selected stores not already owned by the current user',async()=>{
  vi.mocked(apiRequest).mockImplementation(async(path)=>path==='/v1/watch/stores'?[{...store,owner:'Tester'},{...store,id:'store2',storeName:'Other Store',owner:'Other'}]:path==='/v1/watch/events'?[]:store)
  const w=await setup();const button=w.findAll('button').find(b=>b.text()==='设为我负责')!
  expect(button.attributes('disabled')).toBeDefined()
  await w.get('[aria-label="选择 Test Store"]').setValue(true);expect(button.attributes('disabled')).toBeDefined()
  await w.get('[aria-label="选择 Other Store"]').setValue(true);expect(button.attributes('disabled')).toBeUndefined()
  await button.trigger('click');await flushPromises()
  expect(vi.mocked(apiRequest).mock.calls.filter(c=>c[1]?.method==='PUT').map(c=>c[0])).toEqual(['/v1/watch/stores/store2'])
  w.unmount()
 })
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

 it('closes a saved add form without waiting for list refresh and does not enqueue twice',async()=>{
  const w=await setup()
  await w.findAll('button').find(b=>b.text().includes('添加店铺'))!.trigger('click')
  await w.get('#add-watch-store textarea').setValue('https://www.amazon.com/sp?seller=NEWSELLER')
  let finishList!:(value:unknown)=>void
  vi.mocked(apiRequest).mockImplementation(async(path)=>{
   if(path==='/v1/watch/stores/from-url')return {...store,owner:'Tester'}
   if(path==='/v1/watch/stores')return new Promise(resolve=>{finishList=resolve})
   return []
  })
  await w.get('#add-watch-store').trigger('submit');await flushPromises()
  expect(HTMLDialogElement.prototype.close).toHaveBeenCalled()
  expect(w.text()).toContain('已保存 1 个店铺')
  expect(apiRequest).not.toHaveBeenCalledWith('/v1/watch/stores/store1/collect',expect.anything())
  expect(collect).not.toHaveBeenCalled()
  finishList([store]);await flushPromises();w.unmount()
 })
 it('retains failed addresses and never reports a saved store as an add failure',async()=>{
  const w=await setup();await w.findAll('button').find(b=>b.text().includes('添加店铺'))!.trigger('click')
  await w.get('#add-watch-store textarea').setValue('good\nbad')
  vi.mocked(apiRequest).mockImplementation(async(path,init)=>{
   if(path==='/v1/watch/stores/from-url'){if(JSON.parse(init!.body as string).url==='bad')throw new Error('地址无效');return store}
   if(init?.method==='PUT')return {...store,owner:'Tester',version:4}
   if(path.endsWith('/collect'))throw new Error('排队失败')
   return path==='/v1/watch/stores'?[store]:[]
  })
  await w.get('#add-watch-store').trigger('submit');await flushPromises()
  expect((w.get('#add-watch-store textarea').element as HTMLTextAreaElement).value).toBe('bad')
  expect(w.text()).toContain('成功 1 个，剩余 1 个未完成')
  expect(w.text()).toContain('已保存，排队失败')
  expect(apiRequest).toHaveBeenCalledWith('/v1/watch/stores/store1/collect',{method:'POST'})
  w.unmount()
 })
 it('sorts all matching stores before pagination in both directions',async()=>{
  const stores=Array.from({length:25},(_,i)=>({...store,id:`s${i}`,storeName:`Store ${i}`,createdAt:new Date(Date.UTC(2026,0,i+1)).toISOString()}))
  const records=Array.from({length:3},(_,i)=>({id:`e${i}`,storeId:'s24',detectedAt:new Date().toISOString()}))
  vi.mocked(apiRequest).mockImplementation(async(path)=>path==='/v1/watch/stores'?stores:path==='/v1/watch/events'?records:store)
  const w=await setup()
  const first=()=>w.find('tbody tr a').text()
  expect(first()).toBe('Store 0')
  await w.get('[aria-label="按今日上架排序"]').trigger('click')
  expect(first()).toBe('Store 24');expect(w.find('th[aria-sort="descending"]').text()).toContain('今日上架')
  await w.get('[aria-label="按今日上架排序"]').trigger('click');expect(first()).not.toBe('Store 24')
  await w.get('[aria-label="按开始监控时间排序"]').trigger('click');expect(first()).toBe('Store 24')
  await w.get('[aria-label="按开始监控时间排序"]').trigger('click');expect(first()).toBe('Store 0')
  w.unmount()
 })

 it('explains an existing store immediately without any write or collection request',async()=>{
  const w=await setup();await w.findAll('button').find(b=>b.text().includes('添加店铺'))!.trigger('click')
  await w.get('#add-watch-store textarea').setValue('https://www.amazon.com/s?me=TESTSELLER&marketplaceID=ATVPDKIKX0DER')
  vi.mocked(apiRequest).mockClear()
  await w.get('#add-watch-store').trigger('submit');await flushPromises()
  expect(w.get('#add-watch-store [role="status"]').text()).toContain('Test Store（TESTSELLER）已添加，无需重复添加')
  expect(apiRequest).not.toHaveBeenCalled();expect(collect).not.toHaveBeenCalled()
  expect(HTMLDialogElement.prototype.close).not.toHaveBeenCalled();w.unmount()
 })
 it('skips existing stores in mixed batches and recognizes equivalent URLs in the same batch',async()=>{
  const w=await setup();await w.findAll('button').find(b=>b.text().includes('添加店铺'))!.trigger('click')
  await w.get('#add-watch-store textarea').setValue('https://www.amazon.com/sp?seller=TESTSELLER\nhttps://www.amazon.com/sp?seller=NEWSELLER\nhttps://www.amazon.com/s?me=NEWSELLER')
  vi.mocked(apiRequest).mockImplementation(async(path)=>path==='/v1/watch/stores/from-url'?{...store,id:'new',storeKey:'NEWSELLER',owner:'Tester'}:path==='/v1/watch/stores'?[store]:[])
  await w.get('#add-watch-store').trigger('submit');await flushPromises()
  expect(vi.mocked(apiRequest).mock.calls.filter(c=>c[1]?.method==='POST')).toHaveLength(1)
  expect(w.text()).toContain('已跳过 2 个已添加的店铺');w.unmount()
 })

})
