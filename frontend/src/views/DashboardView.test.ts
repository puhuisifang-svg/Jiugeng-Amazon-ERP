import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import DashboardView from './DashboardView.vue'

describe('DashboardView', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('renders clearly marked mock summary data from the backend', async () => {
    window.localStorage.setItem('jiugeng.mock.session.token', 'mock-session-admin')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        mockData: true,
        source: 'SYNTHETIC',
        generatedAt: '2026-09-23T00:00:00Z',
        items: [
          { label: '待处理导入', value: 3, note: '模拟批次，不进入正式台账' },
          { label: '库存预警', value: 5, note: '模拟单仓库存' },
          { label: '待复核新品', value: 8, note: '模拟公开店铺快照' },
        ],
      }),
    }))

    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('本地模拟数据模式')
    expect(wrapper.text()).toContain('SYNTHETIC')
    expect(wrapper.text()).toContain('待处理导入')
    expect(wrapper.text()).toContain('3')
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      expect.stringContaining('/dashboard/summary'),
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
  })

  it('shows a useful error when the mock backend is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('mock profile')
  })
})
