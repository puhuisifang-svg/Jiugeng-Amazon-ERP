import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProductForm from './ProductForm.vue'

describe('ProductForm', () => {
  it('captures Product, SKU and dynamic attributes in one submission', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('PROD-001')
    await inputs[1]!.setValue('旅行收纳袋')
    await inputs[6]!.setValue('SKU-001-BLK')
    await inputs[7]!.setValue('黑色款')
    await wrapper.get('input[aria-label="属性值"]').setValue('Black')
    await wrapper.get('form').trigger('submit')

    const submitted = wrapper.emitted('submit')?.[0]?.[0]
    expect(submitted).toMatchObject({
      productCode: 'PROD-001',
      nameZh: '旅行收纳袋',
      variants: [{
        skuCode: 'SKU-001-BLK',
        variantName: '黑色款',
        attributes: [{ attributeCode: 'color', attributeValue: 'Black', displayOrder: 0 }],
      }],
    })
  })

  it('adds another SKU and keeps every SKU attribute list independent', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    await wrapper.findAll('button').find(button => button.text() === '新增 SKU')!.trigger('click')
    expect(wrapper.findAll('.variant-editor')).toHaveLength(2)
    expect(wrapper.findAll('input[aria-label="属性编码"]')).toHaveLength(2)
  })
})
