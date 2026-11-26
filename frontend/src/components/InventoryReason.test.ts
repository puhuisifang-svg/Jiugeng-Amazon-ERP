import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { LedgerEntryDto } from '@/api/domain'
const mocks=vi.hoisted(()=>({lookup:vi.fn(),allowed:true}))
vi.mock('@/api/purchases',()=>({purchaseApi:{inventoryOrder:mocks.lookup}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>mocks.allowed}}))
import InventoryReason from './InventoryReason.vue'
const entry={id:'transaction-1',requestId:'PURCHASE:receipt-1:line-1',reason:'采购收货 PO-TEST-001'} as LedgerEntryDto
async function setup(value=entry){
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div />'}},{path:'/purchases/orders/:id',component:{template:'<div />'}}]})
  await router.push('/');await router.isReady()
  return {router,w:mount(InventoryReason,{props:{entry:value},global:{plugins:[router]}})}
}
beforeEach(()=>{mocks.allowed=true;mocks.lookup.mockReset().mockResolvedValue({orderId:'order-1',orderNo:'PO-TEST-001'})})
describe('purchase receipt ledger links',()=>{
  it('resolves the real receipt relationship only when clicked, then navigates in the same session',async()=>{
    const {w,router}=await setup();expect(mocks.lookup).not.toHaveBeenCalled()
    await w.get('button').trigger('click');await flushPromises()
    expect(mocks.lookup).toHaveBeenCalledWith('transaction-1');expect(router.currentRoute.value.path).toBe('/purchases/orders/order-1');w.unmount()
  })
  it('preserves reason text without links for ordinary notes or missing permission',async()=>{
    const a=await setup({...entry,requestId:'manual-1'});expect(a.w.find('button').exists()).toBe(false);expect(a.w.text()).toBe(entry.reason);a.w.unmount()
    mocks.allowed=false;const b=await setup();expect(b.w.find('button').exists()).toBe(false);b.w.unmount()
  })
  it('keeps the ledger open with a retryable error when lookup fails',async()=>{
    mocks.lookup.mockRejectedValueOnce(new Error('未找到该流水关联的采购单'));const {w,router}=await setup()
    await w.get('button').trigger('click');await flushPromises();expect(w.get('[role="alert"]').text()).toContain('未找到');expect(router.currentRoute.value.path).toBe('/')
    await w.get('button').trigger('click');await flushPromises();expect(router.currentRoute.value.path).toBe('/purchases/orders/order-1');w.unmount()
  })
})
