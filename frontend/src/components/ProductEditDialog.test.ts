import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductEditDialog from './ProductEditDialog.vue'
import ProductForm from './ProductForm.vue'
const initial = { productCode: 'TEST', productName: '合成产品', status: 'ACTIVE' as const, variants: [] }
describe('shared product edit dialog', () => {
  it('uses the list editor layout and forwards save/cancel', async () => {
    const wrapper = mount(ProductEditDialog, { props: { initial }, global: { stubs: { ProductForm: true } } })
    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('编辑产品')
    expect(wrapper.find('.product-create-dialog .product-create-form').exists()).toBe(true)
    wrapper.getComponent(ProductForm).vm.$emit('submit', initial)
    expect(wrapper.emitted('submit')?.[0]).toEqual([initial])
    await wrapper.get('.operation-dialog').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()
    wrapper.getComponent(ProductForm).vm.$emit('cancel')
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
  it('preserves selected SKU and locks dismissal while saving', async () => {
    const wrapper = mount(ProductEditDialog, { props: { initial, focusVariantId: 'v2', busy: true, error: '保存失败' }, global: { stubs: { ProductForm: true } } })
    expect(wrapper.getComponent(ProductForm).props('focusVariantId')).toBe('v2')
    expect(wrapper.get('[role="alert"]').text()).toBe('保存失败')
    await wrapper.get('.operation-dialog').trigger('keydown', { key: 'Escape' })
    wrapper.getComponent(ProductForm).vm.$emit('cancel')
    expect(wrapper.emitted('close')).toBeUndefined()
    await wrapper.setProps({ busy: false })
    await wrapper.get('[aria-label="关闭编辑"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
