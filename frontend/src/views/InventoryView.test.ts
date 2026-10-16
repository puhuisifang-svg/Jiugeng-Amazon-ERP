import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { transact, updateSafetyStock } = vi.hoisted(() => ({ transact: vi.fn(), updateSafetyStock: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    balances: vi.fn(async () => ({ items: [{ warehouseCode: 'MAIN', materialId: 'm1', materialCode: 'MAT-1',
      materialName: '纸箱', quantity: 2, safetyStock: 3, unit: 'PCS', lowStock: true }], mockData: false })),
    materials: vi.fn(async () => ({ items: [{ id: 'm1', materialCode: 'MAT-1', name: '纸箱', materialType: '包装',
      unit: 'PCS', status: 'ACTIVE' }], mockData: false })),
    transact,
    updateSafetyStock,
  },
}))

import InventoryView from './InventoryView.vue'

describe('InventoryView formal mode', () => {
  beforeEach(() => { transact.mockReset(); updateSafetyStock.mockReset() })

  it('shows low stock and submits a formal non-opening command', async () => {
    transact.mockResolvedValue({ item: { id: 't1' }, balance: { warehouseCode: 'MAIN', materialId: 'm1',
      materialCode: 'MAT-1', materialName: '纸箱', quantity: 7, safetyStock: 3, unit: 'PCS', lowStock: false }, replayed: false })
    const wrapper = mount(InventoryView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('低库存')
    expect(wrapper.find('select').text()).not.toContain('OPENING')
    await wrapper.find('select').setValue('m1')
    const selects = wrapper.findAll('select')
    await selects[1]!.setValue('INBOUND')
    await wrapper.find('input[type="number"]').setValue('5')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(transact).toHaveBeenCalledWith(expect.objectContaining({ materialId: 'm1', type: 'INBOUND', quantity: 5,
      unit: 'PCS' }))
  })

  it('keeps the legacy inventory controls integer-only', async () => {
    const wrapper = mount(InventoryView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    await wrapper.find('select').setValue('m1')
    const quantity = wrapper.find('form').get('input[type="number"]')
    expect(quantity.attributes('min')).toBe('1')
    expect(quantity.attributes('step')).toBe('1')
    await quantity.setValue('1.5')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('数量必须是大于 0 的整数')
    expect(transact).not.toHaveBeenCalled()

    const safety = wrapper.get('input[type="number"][min="0"]')
    expect(safety.attributes('step')).toBe('1')
    await safety.setValue('2.5')
    await wrapper.findAll('button').find(button => button.text() === '保存阈值')!.trigger('click')
    expect(wrapper.get('[role="alert"]').text()).toContain('安全库存必须是大于或等于 0 的整数')
    expect(updateSafetyStock).not.toHaveBeenCalled()
  })
})
