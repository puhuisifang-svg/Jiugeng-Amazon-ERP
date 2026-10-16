import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ListPagination from './ListPagination.vue'

describe('ListPagination', () => {
  it('bounds navigation and resets the page when changing page size', async () => {
    const wrapper = mount(ListPagination, { props: { total: 41, page: 1, pageSize: 20 } })
    expect(wrapper.findAll('button')[0]!.attributes('disabled')).toBeDefined()
    await wrapper.findAll('button')[1]!.trigger('click')
    expect(wrapper.emitted('update:page')?.[0]).toEqual([2])
    await wrapper.setProps({ page: 3 })
    expect(wrapper.findAll('button')[1]!.attributes('disabled')).toBeDefined()
    await wrapper.get('select').setValue('50')
    expect(wrapper.emitted('update:pageSize')?.[0]).toEqual([50])
    expect(wrapper.emitted('update:page')?.at(-1)).toEqual([1])
  })
})
