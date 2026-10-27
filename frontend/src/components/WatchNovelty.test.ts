import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import WatchStoreDetail from './WatchStoreDetail.vue'
import { apiRequest } from '@/api/client'
import { confirmAction } from '@/state/uiConfirmation'
import type { WatchStoreDto } from '@/api/resources'
vi.mock('@/api/client',()=>({apiRequest:vi.fn()}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn(async()=>true)}))
const {collect}=vi.hoisted(()=>({collect:vi.fn(async()=>true)}))
vi.mock('@/composables/useCollectionStatus',()=>({useCollectionStatus:()=>({state:()=>undefined,label:()=>'',pending:()=>false,collect,error:{value:''}})}))
const records=[
 {id:'candidate',asin:'B0TEST0001',title:'Recent product',detectedAt:'2026-10-27T08:00:00Z',noveltyStatus:'CANDIDATE',availableDate:'2026-09-01',noveltyReason:'统一窗口60天',noveltyVersion:0},
 {id:'pending',asin:'B0TEST0002',title:'Unknown date product',detectedAt:'2026-10-27T08:00:00Z',noveltyStatus:'PENDING'},
 {id:'old',asin:'B0TEST0003',title:'Old product',detectedAt:'2026-10-27T08:00:00Z',noveltyStatus:'OLD'},
 {id:'late',asin:'B0TEST0004',title:'Late enrichment',detectedAt:'2026-10-26T08:00:00Z',noveltyStatus:'CANDIDATE',noveltyCheckedAt:'2026-10-27T12:00:00Z'}
]
beforeEach(()=>{
 vi.clearAllMocks();vi.useFakeTimers({toFake:['Date']});vi.setSystemTime(new Date('2026-10-27T12:00:00Z'))
 HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()
 vi.mocked(apiRequest).mockImplementation(async(path)=>path.endsWith('/events')?records:{automaticProducts:[]})
})
afterEach(()=>vi.useRealTimers())
async function open(writable=true){const w=mount(WatchStoreDetail,{props:{store:undefined,writable,initialTab:'novelty'}});await w.setProps({store:{id:'test',storeName:'Synthetic',status:'ACTIVE'} as WatchStoreDto});await flushPromises();return w}
it('keeps discovery counts separate and applies novelty and date filters only on search',async()=>{
 const w=await open();expect(w.find('tbody').text()).toContain('Recent product');expect(w.find('tbody').text()).not.toContain('Unknown date product');expect(w.find('tbody').text()).not.toContain('Late enrichment')
 expect(w.findAll('.store-overview strong').map(e=>e.text()).slice(0,3)).toEqual(['0','3','1'])
 await w.get('[aria-label="新品判断结果"]').setValue('PENDING');expect(w.find('tbody').text()).toContain('Recent product')
 await w.findAll('button').find(b=>b.text()==='搜索')!.trigger('click');expect(w.find('tbody').text()).toContain('Unknown date product');expect(w.find('tbody').text()).not.toContain('Recent product')
 await w.findAll('button').find(b=>b.text()==='补采日期')!.trigger('click');await flushPromises();expect(collect).toHaveBeenCalledWith('pending')
 await w.findAll('.store-detail-tabs button').find(b=>b.text()==='上架记录')!.trigger('click');expect(w.findAll('tbody tr')).toHaveLength(3);w.unmount()
})
it('confirms with version, can undo and preserves errors for retry',async()=>{
 const w=await open();vi.mocked(apiRequest).mockResolvedValueOnce({...records[0],noveltyOverride:'CONFIRMED',noveltyVersion:1})
 await w.findAll('button').find(b=>b.text()==='确认新品')!.trigger('click');await flushPromises()
 expect(apiRequest).toHaveBeenCalledWith('/v1/watch/events/candidate/novelty',{method:'PUT',body:JSON.stringify({version:0,action:'CONFIRMED'})});expect(w.text()).toContain('已确认新品')
 vi.mocked(apiRequest).mockRejectedValueOnce(new Error('版本冲突'))
 await w.findAll('button').find(b=>b.text()==='撤销标记')!.trigger('click');await flushPromises();expect(w.text()).toContain('版本冲突');expect(w.text()).toContain('已确认新品');w.unmount()
})
it('cancellation and read-only mode cannot mark novelty',async()=>{
 const w=await open();vi.mocked(confirmAction).mockResolvedValueOnce(false);await w.findAll('button').find(b=>b.text()==='排除')!.trigger('click');await flushPromises();expect(vi.mocked(apiRequest).mock.calls.some(c=>c[1]?.method==='PUT')).toBe(false)
 await w.setProps({writable:false});expect(w.findAll('button').filter(b=>['确认新品','排除','补采日期'].includes(b.text())).every(b=>b.attributes('disabled')!==undefined)).toBe(true);w.unmount()
})
