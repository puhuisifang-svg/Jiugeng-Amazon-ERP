import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FilterMultiSelect from './FilterMultiSelect.vue'

describe('FilterMultiSelect', () => {
  it('searches labels and codes, selects visible results and clears selections', async () => {
    const wrapper = mount(FilterMultiSelect, { props: { label: '动作', modelValue: ['LOGOUT'], options: [{ value: 'LOGIN', label: '登录' }, { value: 'LOGOUT', label: '退出登录' }] } })
    await wrapper.get('button').trigger('click')
    await wrapper.get('input[type=search]').setValue('LOGIN')
    expect(wrapper.findAll('.filter-multi-options label')).toHaveLength(1)
    await wrapper.get('.filter-multi-tools input').setValue(true)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([['LOGOUT', 'LOGIN']])
    await wrapper.get('.filter-multi-tools button').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([[]])
    await wrapper.get('[role=dialog]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role=dialog]').exists()).toBe(false)
    wrapper.unmount()
  })
})
