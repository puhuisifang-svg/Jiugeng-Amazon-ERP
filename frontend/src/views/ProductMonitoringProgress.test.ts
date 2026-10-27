import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
import {createMemoryHistory,createRouter} from 'vue-router'
import {apiRequest} from '@/api/client'
import ProductMonitoringView from './ProductMonitoringView.vue'
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'tester'}}}}))
vi.mock('@/api/client',()=>({apiRequest:vi.fn()}))
vi.mock('@/api/monitoring',()=>({monitoringApi:{list:vi.fn(async()=>({items:[{id:'one',asin:'B0TEST0001',enabled:true,fields:{availability:'AVAILABLE'},lastStatus:'PENDING',tags:[],remark:''}],total:1,activeCount:1})),preferences:vi.fn(async()=>({columns:[]}))}}))
it('shows selected update progress separately from availability and polls through completion',async()=>{
 vi.useFakeTimers()
 let status='SUCCEEDED',createdAt='2026-10-27T00:00:00Z',reason=''
 vi.mocked(apiRequest).mockImplementation(async path=>path==='/v1/collection/status'?{sources:[{provider:'amazon-public-us',approved:true}]}:path.startsWith('/v1/collection/targets')?[{targetId:'one',status,createdAt,reason}]:undefined)
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/product-monitoring',component:ProductMonitoringView}]});await router.push('/product-monitoring');await router.isReady()
 const w=mount(ProductMonitoringView,{global:{plugins:[router]}})
 try{
 await flushPromises();const cell=()=>w.get('[aria-label="B0TEST0001 采集状态"]')
 expect(w.findAll('th').map(t=>t.text())).toContain('可售状态');expect(cell().text()).toBe('已更新')
 await w.get('[aria-label="选择 B0TEST0001"]').setValue(true);await w.get('[aria-label="更新所选产品"]').trigger('click');await flushPromises()
 expect(apiRequest).toHaveBeenCalledWith('/v1/product-monitoring/one/collect',{method:'POST'});expect(cell().text()).toContain('排队中');expect(cell().find('.mw-spinner').exists()).toBe(true)
 status='RUNNING';createdAt='2026-10-27T01:00:00Z';await vi.advanceTimersByTimeAsync(5000);await flushPromises();expect(cell().text()).toContain('采集中')
 status='RETRY';await vi.advanceTimersByTimeAsync(5000);await flushPromises();expect(cell().text()).toContain('等待重试')
 status='FAILED';reason='来源暂时不可用';await vi.advanceTimersByTimeAsync(5000);await flushPromises();expect(cell().text()).toContain('采集失败');expect(cell().text()).toContain(reason);expect(cell().find('.mw-spinner').exists()).toBe(false)
 status='SUCCEEDED';createdAt='2026-10-27T02:00:00Z';reason='';await vi.advanceTimersByTimeAsync(5000);await flushPromises();expect(cell().text()).toBe('已更新');expect(w.text()).toContain('可售')
 }finally{w.unmount();vi.useRealTimers()}
})
