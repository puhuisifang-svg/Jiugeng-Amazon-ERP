import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import RowMoreActions from './RowMoreActions.vue'

describe('row secondary actions', () => {
  it('renders the opened menu outside the table stacking context and cleans it up', async () => {
    const w = mount(RowMoreActions, { attachTo: document.body, props: { label: '独立浮层' }, slots: { default: '<button type="button">编辑</button>' } })
    await w.get('.row-more-actions__trigger').trigger('click'); await flushPromises()
    expect(document.querySelector('body > .row-more-actions__menu')).not.toBeNull()
    w.unmount()
    expect(document.querySelector('body > .row-more-actions__menu')).toBeNull()
  })
  it('ignores queued scroll events but closes when its anchor moves', async () => {
    const w = mount(RowMoreActions, { attachTo: document.body, props: { label: '滚动定位' }, slots: { default: '<button type="button">编辑</button>' } })
    const trigger = w.get('.row-more-actions__trigger')
    let y = 100
    vi.spyOn(trigger.element, 'getBoundingClientRect').mockImplementation(() => new DOMRect(20, y, 28, 28))
    await trigger.trigger('click'); await flushPromises()
    document.dispatchEvent(new Event('scroll')); await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    y = 80
    document.dispatchEvent(new Event('scroll')); await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    w.unmount()
  })
  it('hides actions until expanded, closes on selection and restores focus on Escape', async () => {
    const w = mount(RowMoreActions, { attachTo: document.body, global: { stubs: { Teleport: true } }, props: { label: 'SKU 更多操作' }, slots: { default: '<button type="button">编辑</button><button type="button" disabled>删除</button>' } })
    expect(w.get('.row-more-actions__menu').isVisible()).toBe(false)
    await w.get('.row-more-actions__trigger').trigger('click'); await flushPromises()
    expect(w.get('.row-more-actions__menu').isVisible()).toBe(true)
    await w.get('.row-more-actions__menu button').trigger('click')
    expect(w.get('.row-more-actions__menu').isVisible()).toBe(false)
    await w.get('.row-more-actions__trigger').trigger('click'); await flushPromises()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); await flushPromises()
    expect(w.get('.row-more-actions__menu').isVisible()).toBe(false)
    expect(document.activeElement).toBe(w.get('.row-more-actions__trigger').element)
    w.unmount()
  })
})
