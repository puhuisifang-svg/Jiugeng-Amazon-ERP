import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SupplierContactLinks from './SupplierContactLinks.vue'

describe('SupplierContactLinks', () => {
  it('shows supplier purchase and phone links without editable inputs', () => {
    const wrapper = mount(SupplierContactLinks, { props: { supplier: {
      id: 's1', name: '测试供应商', status: 'ACTIVE', version: 0,
      sourceUrl: 'https://example.com/purchase', phone: '10086',
    } } })
    expect(wrapper.get('a[target="_blank"]').attributes('href')).toBe('https://example.com/purchase')
    expect(wrapper.get('a[href="tel:10086"]').text()).toBe('10086')
    expect(wrapper.find('input').exists()).toBe(false)
  })
  it('preserves a relationship-specific link and rejects unsafe URLs', async () => {
    const wrapper = mount(SupplierContactLinks, { props: { purchaseUrl: 'https://example.com/item' } })
    expect(wrapper.get('a').attributes('href')).toBe('https://example.com/item')
    await wrapper.setProps({ purchaseUrl: 'javascript:alert(1)' })
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.text()).toContain('未填写手机号')
  })
})
