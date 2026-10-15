import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { ledger } = vi.hoisted(() => ({ ledger: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({ domainApi: { ledger, transact: vi.fn() } }))

import InventoryLedgerView from './InventoryLedgerView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('InventoryLedgerView', () => {
  beforeEach(() => {
    ledger.mockReset().mockResolvedValue({ items: [
      { id: 't1', requestId: 'r1', materialCode: 'MAT-1', materialName: '纸箱', type: 'INBOUND', quantity: 5,
        reason: '采购入库', actorLoginName: 'buyer', occurredAt: '2026-10-15T08:00:00Z' },
      { id: 't2', requestId: 'r2', materialCode: 'MAT-2', materialName: '胶带', type: 'OUTBOUND', quantity: 2,
        reason: '生产领用', actorLoginName: 'operator', occurredAt: '2026-10-15T09:00:00Z' },
    ], mockData: false })
  })

  it('filters ledger entries by business keyword and transaction type', async () => {
    const wrapper = mount(InventoryLedgerView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('ERP-013')
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
})
