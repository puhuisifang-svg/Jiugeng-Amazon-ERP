import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { session } from '@/state/session'
import WatchStoresView from './WatchStoresView.vue'

describe('WatchStoresView', () => {
  afterEach(() => {
    session.logout()
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('runs a complete synthetic snapshot and reports baseline promotion', async () => {
    const store = { id: 'watch-store-1', storeName: '模拟美国店铺', sourceUrl: 'https://www.amazon.com/sp?seller=SYNTHETIC', status: 'ACTIVE', baselineAsinCount: 0 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ token: 'mock-session-selection', user: { id: 'mock-selection', displayName: '模拟选品专员', role: 'SELECTION' }, mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ items: [store], mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ item: { ...store, baselineRunId: 'run-1', baselineAsinCount: 2, lastRunId: 'run-1', lastRunStatus: 'SUCCEEDED', lastIntegrityStatus: 'COMPLETE', lastDecision: 'PROMOTED' }, events: [], baselineChanged: true, mockData: true, source: 'SYNTHETIC' }),
      })
    vi.stubGlobal('fetch', fetchMock)
    await session.login('SELECTION')

    const wrapper = mount(WatchStoresView)
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/watch/stores/watch-store-1/runs'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ mode: 'COMPLETE' }),
    }))
    expect(wrapper.text()).toContain('完整快照已提升为基线，本次没有新增事件')
    expect(wrapper.text()).toContain('有效基线：run-1')
  })
})
