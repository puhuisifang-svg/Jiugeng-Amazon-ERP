import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SuccessToast from './SuccessToast.vue'

afterEach(() => vi.useRealTimers())
describe('SuccessToast', () => {
  it('announces success, dismisses automatically and can show again', async () => {
    vi.useFakeTimers()
    const wrapper = mount(SuccessToast, { props: { message: '记录已创建。' } })
    expect(wrapper.get('[role="status"]').text()).toContain('记录已创建。')
    await vi.advanceTimersByTimeAsync(4000)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    await wrapper.setProps({ message: '' })
    await wrapper.setProps({ message: '记录已创建。' })
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    await wrapper.get('button').trigger('click')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('renders nothing for an empty message', () => {
    const wrapper = mount(SuccessToast, { props: { message: '' } })
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
