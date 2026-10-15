import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { ledger, transact } = vi.hoisted(() => ({ ledger: vi.fn(), transact: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({ domainApi: { ledger, transact } }))

import InventoryLedgerView from './InventoryLedgerView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('InventoryLedgerView', () => {
  beforeEach(() => {
    transact.mockReset().mockResolvedValue({})
    ledger.mockReset().mockResolvedValue({ items: [
      { id: 't1', requestId: 'r1', materialId: 'm1', materialCode: 'MAT-1', materialName: '纸箱', type: 'INBOUND', quantity: 5, unit: '个',
        reason: '采购入库', actorLoginName: 'buyer', occurredAt: '2026-10-15T08:00:00Z' },
      { id: 't2', requestId: 'r2', materialId: 'm2', materialCode: 'MAT-2', materialName: '胶带', type: 'OUTBOUND', quantity: 2, unit: '卷',
        reason: '生产领用', actorLoginName: 'operator', occurredAt: '2026-10-15T09:00:00Z' },
      { id: 't3', requestId: 'r3', materialId: 'm1', materialCode: 'MAT-1', materialName: '纸箱', type: 'REVERSAL', quantity: 5, unit: '个',
        reason: '采购数量录入错误', referenceTransactionId: 't1', actorLoginName: 'buyer', occurredAt: '2026-10-15T10:00:00Z' },
    ], mockData: false })
  })

  it('filters ledger entries by business keyword and transaction type', async () => {
    const wrapper = mount(InventoryLedgerView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('ERP-013')
    expect(wrapper.text()).not.toContain('INBOUND')
    expect(wrapper.text()).toContain('入库')
    expect(wrapper.text()).toContain('MAT-1')
    expect(wrapper.text()).toContain('MAT-2')

    await wrapper.get('input[placeholder="辅料编码、名称、原因或操作人"]').setValue('纸箱')
    expect(wrapper.text()).toContain('MAT-1')
    expect(wrapper.text()).not.toContain('MAT-2')

    await wrapper.get('button').trigger('click')
    await wrapper.get('select').setValue('OUTBOUND')
    expect(wrapper.text()).not.toContain('MAT-1')
    expect(wrapper.text()).toContain('MAT-2')
  })

  it('requires a reason in a confirmation dialog before reversing a ledger entry', async () => {
    const wrapper = mount(InventoryLedgerView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    const reversedRow = wrapper.findAll('tbody tr').find(item => item.text().includes('采购入库'))!
    const reversedButton = reversedRow.get('button')
    expect(reversedButton.text()).toBe('已冲销')
    expect(reversedButton.attributes()).toHaveProperty('disabled')
    await reversedButton.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    const reversibleRow = wrapper.findAll('tbody tr').find(item => item.text().includes('生产领用'))!
    const reverseButton = reversibleRow.get('button')
    await reverseButton.trigger('click')
    expect(transact).not.toHaveBeenCalled()
    expect(wrapper.get('[role="dialog"]').text()).toContain('确认冲销库存流水')
    expect(wrapper.get('button[type="submit"]').attributes()).toHaveProperty('disabled')

    await wrapper.get('textarea[placeholder="请输入本次冲销的具体原因"]').setValue('采购数量录入错误')
    await wrapper.get('button[type="submit"]').trigger('submit')
    await flushPromises()

    expect(transact).toHaveBeenCalledWith(expect.objectContaining({
      materialId: 'm2', type: 'REVERSAL', unit: '卷', reason: '采购数量录入错误', referenceTransactionId: 't2',
    }))
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
