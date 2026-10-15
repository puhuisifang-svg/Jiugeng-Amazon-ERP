import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductForm from './ProductForm.vue'

describe('ProductForm', () => {
  it('submits the unified product name and draft SKU without retired fields', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    const productInputs = wrapper.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('PROD-001'); await productInputs[1]!.setValue('旅行收纳袋')
    const skuInputs = wrapper.find('.variant-editor').findAll('input')
    await skuInputs[1]!.setValue('SKU-001-BLK'); await skuInputs[2]!.setValue('黑色款')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ productCode: 'PROD-001', productName: '旅行收纳袋', status: 'DRAFT', variants: [{ skuCode: 'SKU-001-BLK', variantName: '黑色款', status: 'DRAFT', suppliers: [], materials: [] }] })
    expect(wrapper.text()).not.toContain('动态属性')
    expect(wrapper.text()).not.toContain('重量单位')
  })

  it('adds independent SKU aggregate editors', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    await wrapper.findAll('button').find(button => button.text() === '新增 SKU')!.trigger('click')
    expect(wrapper.findAll('.variant-editor')).toHaveLength(2)
    expect(wrapper.findAll('select[aria-label="添加辅料"]')).toHaveLength(2)
  })
})
