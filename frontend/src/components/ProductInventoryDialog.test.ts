import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ProductInventoryDialog from './ProductInventoryDialog.vue'
import { productManagementApi } from '@/api/productManagement'
import { session } from '@/state/session'
import { ApiError } from '@/api/client'

vi.mock('@/api/productManagement', () => ({ productManagementApi: { balances: vi.fn(), stockHistory: vi.fn(), transact: vi.fn() } }))
vi.mock('@/state/session', () => ({ session: { user: { value: { id: 'synthetic-user' } }, canAccess: vi.fn(() => true) } }))
enableAutoUnmount(afterEach)
beforeEach(() => {
  sessionStorage.clear(); vi.clearAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
  vi.mocked(session.canAccess).mockReturnValue(true)
  vi.mocked(productManagementApi.balances).mockResolvedValue([{ variantId: 'v1', quantity: 8, updatedAt: '2026-01-01' }])
  vi.mocked(productManagementApi.stockHistory).mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
  vi.mocked(productManagementApi.transact).mockReset()
})
async function setup() {
  const wrapper = mount(ProductInventoryDialog, { props: { variantId: 'v1', name: '合成子体' } })
  await flushPromises(); return wrapper
}
describe('product inventory operations', () => {
  it('reuses and persists an uncertain request instead of double posting stock', async () => {
    vi.mocked(productManagementApi.transact).mockRejectedValueOnce(new Error('网络中断'))
      .mockResolvedValueOnce({ replayed: true, item: {} as never })
    const wrapper = await setup()
    await wrapper.get('input[type="number"]').setValue(3)
    await wrapper.get('input[maxlength="500"]').setValue('合成盘点')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    const request = vi.mocked(productManagementApi.transact).mock.calls[0]![1]
    expect(wrapper.get('input[type="number"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="关闭"]').attributes('disabled')).toBeDefined()
    expect(JSON.parse(sessionStorage.getItem('jiugeng.product-stock.pending.synthetic-user.v1')!)).toEqual(request)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(productManagementApi.transact).toHaveBeenLastCalledWith('v1', request)
    expect(sessionStorage.getItem('jiugeng.product-stock.pending.synthetic-user.v1')).toBeNull()
    expect(wrapper.text()).toContain('没有重复记账')
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })
  it('retains editable input after a definite rejection and guards unsaved closing', async () => {
    vi.mocked(productManagementApi.transact).mockRejectedValue(new ApiError(409, 'INSUFFICIENT_STOCK', '库存不足'))
    const wrapper = await setup()
    await wrapper.findAll('.stock-operation-types button').find(button => button.text() === '− 出库')!.trigger('click')
    await wrapper.get('input[type="number"]').setValue(3)
    await wrapper.get('input[maxlength="500"]').setValue('合成出库')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('库存不足')
    expect(wrapper.get('input[type="number"]').element).toHaveProperty('value', '3')
    expect(wrapper.get('input[type="number"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[aria-label="关闭"]').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(true)
    expect(wrapper.emitted('close')).toBeUndefined()
  })
  it('keeps balances and history readable without inventory movement permissions', async () => {
    vi.mocked(session.canAccess).mockReturnValue(false)
    const wrapper = await setup()
    expect(wrapper.get('.product-stock-balance').text()).toContain('8')
    expect(wrapper.find('form').exists()).toBe(false)
    expect(productManagementApi.transact).not.toHaveBeenCalled()
  })
})

it('previews the balance and prevents an excessive outbound request before posting', async () => {
  const wrapper = await setup()
  await wrapper.findAll('.stock-operation-types button').find(button => button.text() === '− 出库')!.trigger('click')
  await wrapper.get('input[type="number"]').setValue(3)
  expect(wrapper.get('.stock-projection').text()).toContain('预计 5 件')
  await wrapper.get('input[type="number"]').setValue(9)
  expect(wrapper.get('#stock-quantity-error').text()).toContain('最多可减少 8 件')
  await wrapper.get('input[maxlength="500"]').setValue('合成出库')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(productManagementApi.transact).not.toHaveBeenCalled()
})
it('does not treat a failed balance load as zero stock or allow a new operation', async () => {
  vi.mocked(productManagementApi.balances).mockRejectedValue(new Error('合成加载失败'))
  const wrapper = await setup()
  expect(wrapper.get('.product-stock-balance').text()).toContain('—')
  expect(wrapper.get('.product-ui-state--error').text()).toContain('合成加载失败')
  await wrapper.get('input[type="number"]').setValue(3)
  await wrapper.get('input[maxlength="500"]').setValue('合成入库')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(productManagementApi.transact).not.toHaveBeenCalled()
})
it('exposes only operations permitted for the current account', async () => {
  vi.mocked(session.canAccess).mockImplementation(permissions => permissions?.includes('inventory.outbound') ?? false)
  const wrapper = await setup()
  expect(wrapper.findAll('.stock-operation-types button').map(button => button.text())).toEqual(['− 出库'])
})

it('allows an empty reason and only offers inbound / outbound actions', async () => {
 vi.mocked(productManagementApi.transact).mockResolvedValue({replayed:false,item:{type:'INBOUND',quantity:2,balanceAfter:10} as never})
 const w=await setup()
 expect(w.findAll('.stock-operation-types button').map(b=>b.text())).toEqual(['+ 入库','− 出库'])
 expect(w.get('input[maxlength="500"]').attributes('required')).toBeUndefined()
 await w.get('input[type="number"]').setValue(2);await w.get('form').trigger('submit');await flushPromises()
 expect(productManagementApi.transact).toHaveBeenCalledWith('v1',expect.objectContaining({type:'INBOUND',quantity:2,reason:''}))
})
it('confirms a legacy adjustment without offering new adjustment buttons', async () => {
 const request={requestId:'legacy-adjustment',type:'ADJUST_OUT',quantity:1,reason:'合成旧请求'}
 sessionStorage.setItem('jiugeng.product-stock.pending.synthetic-user.v1',JSON.stringify(request))
 vi.mocked(productManagementApi.transact).mockResolvedValue({replayed:true,item:{type:'ADJUST_OUT',quantity:1,balanceAfter:7} as never})
 const w=await setup();expect(w.text()).toContain('盘亏 1 件')
 expect(w.findAll('.stock-operation-types button').map(b=>b.text())).toEqual(['+ 入库','− 出库'])
 await w.get('form').trigger('submit');await flushPromises()
 expect(productManagementApi.transact).toHaveBeenCalledWith('v1',request)
 expect(w.get('button[type="submit"]').text()).toBe('确认入库')
})
