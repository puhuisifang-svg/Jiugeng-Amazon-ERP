import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { transact, updateSafetyStock } = vi.hoisted(() => ({ transact: vi.fn(), updateSafetyStock: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    materials: vi.fn(async () => ({ items: [
      { id: 'm1', materialCode: 'MAT-1', name: '纸箱', materialType: '包装', specification: '30×20', unit: 'PCS', status: 'ACTIVE' },
      { id: 'm2', materialCode: 'MAT-2', name: '胶带', materialType: '耗材', unit: 'ROLL', status: 'ACTIVE' },
    ], mockData: false })),
    balances: vi.fn(async () => ({ items: [{ warehouseCode: 'MAIN', materialId: 'm1', materialCode: 'MAT-1',
      materialName: '纸箱', quantity: 2, safetyStock: 3, unit: 'PCS', lowStock: true }], mockData: false })),
    suppliers: vi.fn(async () => ({ items: [], totalCount: 0, page: 0, size: 100 })),
    materialSupplierLinks: vi.fn(async () => []),
    transact,
    updateSafetyStock,
  },
}))

import MaterialsView from './MaterialsView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('MaterialsView merged material management', () => {
  beforeEach(() => { transact.mockReset(); updateSafetyStock.mockReset() })

  it('shows master data and inventory in one filterable table', async () => {
    const wrapper = mount(MaterialsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.text()).toContain('MAT-1')
    expect(wrapper.text()).toContain('低库存')
    expect(wrapper.text()).toContain('未初始化')
    expect(wrapper.get('a[href="/inventory/ledger"]').text()).toContain('库存流水')

    await wrapper.get('input[placeholder="辅料编码、名称或规格"]').setValue('胶带')
    expect(wrapper.text()).not.toContain('MAT-1')
    expect(wrapper.text()).toContain('MAT-2')
  })

  it('opens an inventory command from a material row and updates the merged balance', async () => {
    transact.mockResolvedValue({ item: { id: 't1' }, balance: { warehouseCode: 'MAIN', materialId: 'm2',
      materialCode: 'MAT-2', materialName: '胶带', quantity: 5, safetyStock: 0, unit: 'ROLL', lowStock: false }, replayed: false })
    const wrapper = mount(MaterialsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-2'))!
    await row.findAll('button').find(button => button.text() === '库存')!.trigger('click')
    const form = wrapper.findAll('form').find(item => item.text().includes('提交正式库存命令'))!
    expect((form.get('select').element as HTMLSelectElement).value).toBe('m2')
    await form.get('input[type="number"]').setValue('5')
    await form.trigger('submit')
    await flushPromises()

    expect(transact).toHaveBeenCalledWith(expect.objectContaining({ materialId: 'm2', type: 'INBOUND', quantity: 5, unit: 'ROLL' }))
    expect(wrapper.findAll('tbody tr').find(item => item.text().includes('MAT-2'))!.text()).toContain('5')
  })
})
