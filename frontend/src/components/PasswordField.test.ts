import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PasswordField from './PasswordField.vue'

describe('PasswordField', () => {
  it('toggles password visibility with an accessible eye button', async () => {
    const wrapper = mount(PasswordField, { props: { id: 'password', modelValue: 'Secret123', required: true } })

    expect(wrapper.get('input').attributes('type')).toBe('password')
    expect(wrapper.get('button').attributes('aria-label')).toBe('显示密码')
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('input').attributes('type')).toBe('text')
    expect(wrapper.get('button').attributes('aria-label')).toBe('隐藏密码')
  })
})
