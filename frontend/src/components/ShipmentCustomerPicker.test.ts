import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ list: vi.fn(), products: vi.fn(), defaultCustomer: vi.fn() }))
vi.mock('@/api/customers', () => ({ customersApi: api }))
import ShipmentCustomerPicker from './ShipmentCustomerPicker.vue'
const binding = { id: 'cp1', customerId: 'c1', customerName: '合成默认客户', productId: 'p1', variantId: 'v1', customerProductCode: 'C-1', requirements: '合成需求' }
beforeEach(() => {
  vi.clearAllMocks()
  api.list.mockResolvedValue({ items: [{ id: 'c1', name: '合成默认客户', status: 'ACTIVE' }, { id: 'c2', name: '合成其他客户', status: 'ACTIVE' }], totalCount: 21 })
  api.defaultCustomer.mockResolvedValue({ id: 'c1', name: '合成默认客户', status: 'ACTIVE' })
  api.products.mockImplementation(({ customerId }) => Promise.resolve({ items: [{ ...binding, id: customerId === 'c1' ? 'cp1' : 'cp2', customerId, productName: '合成SKU', skuCode: 'SKU-1' }], totalCount: 21 }))
})
describe('shipping customer picker', () => {
  it('selects default customer and clears product when changing customer', async () => {
    const wrapper = mount(ShipmentCustomerPicker); await flushPromises()
    expect((wrapper.get('[aria-label="发货客户"]').element as HTMLSelectElement).value).toBe('c1')
    expect(api.products).toHaveBeenLastCalledWith(expect.objectContaining({ customerId: 'c1', page: 0 }))
    await wrapper.get('[aria-label="发货客户商品"]').setValue('cp1')
    expect(wrapper.emitted('select')?.at(-1)?.[0]).toEqual(expect.objectContaining({ id: 'cp1', variantId: 'v1' }))
    await wrapper.get('[aria-label="发货客户"]').setValue('c2'); await flushPromises()
    expect(wrapper.emitted('select')?.at(-1)?.[0]).toBeUndefined()
    expect((wrapper.get('[aria-label="发货客户商品"]').element as HTMLSelectElement).value).toBe('')
    expect(api.products).toHaveBeenLastCalledWith(expect.objectContaining({ customerId: 'c2' }))
    await wrapper.findAll('button').find(button => button.text() === '下一页商品')!.trigger('click'); await flushPromises()
    expect(api.products).toHaveBeenLastCalledWith(expect.objectContaining({ customerId: 'c2', page: 1 }))
    wrapper.unmount()
  })
  it('never overwrites an editing binding with the default', async () => {
    const wrapper = mount(ShipmentCustomerPicker, { props: { initial: { ...binding, customerId: 'c2', id: 'old' }, editing: true, productName: '原商品' } }); await flushPromises()
    expect(api.defaultCustomer).not.toHaveBeenCalled()
    expect((wrapper.get('[aria-label="发货客户"]').element as HTMLSelectElement).value).toBe('c2')
    expect((wrapper.get('[aria-label="发货客户商品"]').element as HTMLSelectElement).value).toBe('old')
    expect(wrapper.emitted('select')).toBeUndefined(); wrapper.unmount()
  })
  it('shows failures without choosing an arbitrary customer', async () => {
    api.defaultCustomer.mockRejectedValue(new Error('offline'))
    const wrapper = mount(ShipmentCustomerPicker); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('默认客户加载失败')
    expect((wrapper.get('[aria-label="发货客户"]').element as HTMLSelectElement).value).toBe('')
    expect(wrapper.emitted('select')).toBeUndefined(); wrapper.unmount()
  })
})
