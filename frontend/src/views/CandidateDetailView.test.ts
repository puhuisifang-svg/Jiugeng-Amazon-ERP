import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { session } from '@/state/session'
import CandidateDetailView from './CandidateDetailView.vue'

describe('CandidateDetailView', () => {
  afterEach(() => {
    session.logout()
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('loads candidate detail and appends an attributable review note', async () => {
    const candidate = { id: 'candidate-1', asin: 'B0CAMP12345', title: '露营灯', status: 'REVIEWING', score: 80, owner: '模拟选品员' }
    const firstNote = { id: 'note-1', content: '初审通过', authorId: 'mock-selection', authorName: '模拟选品专员', authorRole: 'SELECTION', createdAt: '2026-10-14T10:00:00Z' }
    const secondNote = { ...firstNote, id: 'note-2', content: '补充供应链报价' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ token: 'mock-session-selection', user: { id: 'mock-selection', displayName: '模拟选品专员', role: 'SELECTION' }, mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ item: { candidate, notes: [firstNote] }, mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ item: { candidate, notes: [firstNote, secondNote] }, mockData: true, source: 'SYNTHETIC' }),
      })
    vi.stubGlobal('fetch', fetchMock)
    await session.login('SELECTION')

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/selection/candidates', component: { template: '<div />' } },
        { path: '/selection/candidates/:candidateId', component: CandidateDetailView },
      ],
    })
    await router.push('/selection/candidates/candidate-1')
    await router.isReady()
    const wrapper = mount(CandidateDetailView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('初审通过')
    await wrapper.get('textarea').setValue('补充供应链报价')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/selection/candidates/candidate-1/notes'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ content: '补充供应链报价' }),
    }))
    expect(wrapper.text()).toContain('评审记录已追加')
    expect(wrapper.text()).toContain('补充供应链报价')
  })
})
