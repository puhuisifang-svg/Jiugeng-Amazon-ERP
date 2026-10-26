import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import DashboardMaintenanceView from './DashboardMaintenanceView.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), RouterLink: { template: '<a><slot /></a>' } }))

describe('DashboardMaintenanceView in mock mode', () => {
  it('does not call formal maintenance APIs or misrepresent synthetic records', async () => {
    const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(DashboardMaintenanceView, { props: { kind: 'pending-products' } })
    await flushPromises()
    expect(wrapper.text()).toContain('正式工作台维护仅在密码登录模式启用')
    expect(wrapper.text()).toContain('不会将模拟记录写入正式工作台')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
