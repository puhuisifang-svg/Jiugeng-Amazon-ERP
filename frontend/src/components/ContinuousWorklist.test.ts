import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ContinuousWorklist from './ContinuousWorklist.vue'

describe('ContinuousWorklist', () => {
  afterEach(() => vi.restoreAllMocks())
  it('duplicates seven rows for continuous scrolling and pauses on focus', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const items = Array.from({ length: 7 }, (_, index) => ({ id: String(index) }))
    const wrapper = mount(ContinuousWorklist, { props: { items, label: '待发货' }, slots: { default: '<span>记录</span>' } })
    expect(wrapper.findAll('.worklist-row')).toHaveLength(14)
    await wrapper.get('.worklist-viewport').trigger('focusin')
    expect(wrapper.get('.worklist-track').classes()).toContain('worklist-track--paused')
  })
  it('stays static at six rows', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const wrapper = mount(ContinuousWorklist, { props: { items: Array.from({ length: 6 }, (_, index) => ({ id: String(index) })), label: '今日发货' } })
    expect(wrapper.findAll('.worklist-row')).toHaveLength(6)
    expect(wrapper.find('.worklist-track--animated').exists()).toBe(false)
  })
})
