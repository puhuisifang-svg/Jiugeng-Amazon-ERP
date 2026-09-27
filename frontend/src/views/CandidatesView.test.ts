import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { session } from '@/state/session'
import CandidatesView from './CandidatesView.vue'

describe('CandidatesView', () => {
  afterEach(() => {
    session.logout()
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('lets a selection user create a normalized synthetic candidate', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          token: 'mock-session-selection',
          user: { id: 'mock-selection', displayName: '模拟选品专员', role: 'SELECTION' },
          mockData: true,
          source: 'SYNTHETIC',
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ items: [], mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          item: {
            id: 'candidate-new',
            asin: 'B0TEST12345',
            title: '模拟候选',
            status: 'NEW',
            score: 68,
            sourceUrl: 'https://www.amazon.com/dp/B0TEST12345',
          },
          mockData: true,
          source: 'SYNTHETIC',
        }),
      })
    vi.stubGlobal('fetch', fetchMock)
    await session.login('SELECTION')

    const wrapper = mount(CandidatesView)
    await flushPromises()
    await wrapper.get('input[placeholder^="B0"]').setValue('https://www.amazon.com/dp/B0TEST12345')
    await wrapper.get('input[placeholder="候选标题"]').setValue('模拟候选')
    await wrapper.get('input[type="number"]').setValue('68')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/selection/candidates'), expect.objectContaining({ method: 'POST' }))
    expect(wrapper.text()).toContain('候选 B0TEST12345 已创建并归一化')
    expect(wrapper.get('a').attributes('href')).toBe('https://www.amazon.com/dp/B0TEST12345')
  })
})
