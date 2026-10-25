import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ListPagination from './ListPagination.vue'

describe('unified list pagination', () => {
  it('bounds navigation and resets the page when changing page size', async () => {
    const wrapper = mount(ListPagination, { props: { total: 41, page: 1, pageSize: 20 } })
    expect(wrapper.get('[aria-label="上一页"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([2])
    await wrapper.setProps({ page: 3 })
    expect(wrapper.get('[aria-label="下一页"]').attributes('disabled')).toBeDefined()
    await wrapper.get('select').setValue('50')
    expect(wrapper.emitted('update:pageSize')?.[0]).toEqual([50])
    expect(wrapper.emitted('update:page')?.at(-1)).toEqual([1])
    wrapper.unmount()
  })
  it('shows totals, bounded page numbers and an accessible current page', async () => {
    const w = mount(ListPagination, { props: { total: 1000, page: 25, pageSize: 20 } })
    expect(w.text()).toContain('共 1000 条')
    expect(w.get('[aria-current="page"]').text()).toBe('25')
    expect(w.findAll('button').length).toBeLessThanOrEqual(9)
    expect(w.text()).toContain('…')
    await w.get('[aria-label="第 50 页"]').trigger('click')
    expect(w.emitted('update:page')).toEqual([[50]])
    w.unmount()
  })
  it('clamps jumps and rejects invalid values', async () => {
    const w = mount(ListPagination, { props: { total: 120, page: 2, pageSize: 20 } })
    await w.get('input').setValue('999')
    expect(w.emitted('update:page')).toEqual([[6]])
    await w.get('input').setValue('abc')
    await w.get('input').setValue('1.5')
    expect(w.emitted('update:page')).toHaveLength(1)
    expect((w.get('input').element as HTMLInputElement).value).toBe('2')
    w.unmount()
  })
  it('changes size before resetting to the first page, including from page one', async () => {
    const events: string[] = []
    const w = mount(ListPagination, { props: { total: 120, page: 1, pageSize: 20, 'onUpdate:pageSize': value => events.push(`size:${value}`), 'onUpdate:page': value => events.push(`page:${value}`) } })
    await w.get('select').setValue('50')
    expect(events).toEqual(['size:50', 'page:1'])
    w.unmount()
  })
  it('handles empty lists and prevents navigation while loading', async () => {
    const w = mount(ListPagination, { props: { total: 0, page: 1, pageSize: 20 } })
    expect(w.get('[aria-label="上一页"]').attributes('disabled')).toBeDefined()
    expect(w.get('[aria-label="下一页"]').attributes('disabled')).toBeDefined()
    await w.setProps({ total: 100, disabled: true })
    await w.get('[aria-label="下一页"]').trigger('click')
    expect(w.emitted('update:page')).toBeUndefined()
    expect(w.get('select').attributes('disabled')).toBeDefined()
    w.unmount()
  })
})
