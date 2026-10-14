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

  it('filters candidates and saves evaluation details', async () => {
    const candidate = { id: 'candidate-1', asin: 'B0CAMP12345', title: '露营灯', status: 'REVIEWING', score: 80, category: '户外', owner: '旧负责人', tags: ['露营'], source: 'MANUAL' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ token: 'mock-session-selection', user: { id: 'mock-selection', displayName: '模拟选品专员', role: 'SELECTION' }, mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ items: [candidate, { ...candidate, id: 'candidate-2', asin: 'B0PET123456', title: '宠物碗', category: '宠物' }], mockData: true, source: 'SYNTHETIC' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ item: { ...candidate, owner: '新负责人', selectionReason: '需求稳定' }, mockData: true, source: 'SYNTHETIC' }),
      })
    vi.stubGlobal('fetch', fetchMock)
    await session.login('SELECTION')

    const wrapper = mount(CandidatesView)
    await flushPromises()
    await wrapper.get('input[placeholder^="ASIN"]').setValue('B0CAMP')
    expect(wrapper.findAll('.compact-panel')).toHaveLength(1)
    expect(wrapper.get('button[type="button"]').text()).toContain('导出当前结果（1）')

    await wrapper.get('input[placeholder^="ASIN"]').setValue('不存在')
    expect(wrapper.get('button[type="button"]').attributes('disabled')).toBeDefined()
    await wrapper.get('input[placeholder^="ASIN"]').setValue('B0CAMP')

    await wrapper.get('.evaluation-form input[name="owner"]').setValue('新负责人')
    await wrapper.get('.evaluation-form input[name="selectionReason"]').setValue('需求稳定')
    await wrapper.get('.evaluation-form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/selection/candidates/candidate-1/evaluation'), expect.objectContaining({ method: 'PUT' }))
    expect(wrapper.text()).toContain('候选 B0CAMP12345 的评估资料已保存')
  })
})
