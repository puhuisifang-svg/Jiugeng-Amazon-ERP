import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductForm from './ProductForm.vue'

describe('ProductForm', () => {
  it('submits the unified product name with enabled product and SKU defaults', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    const productInputs = wrapper.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('PROD-001'); await productInputs[1]!.setValue('旅行收纳袋')
    const skuInputs = wrapper.find('.variant-editor').findAll('input[required]')
    await skuInputs[0]!.setValue('SKU-001-BLK'); await skuInputs[1]!.setValue('黑色款')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ productCode: 'PROD-001', productName: '旅行收纳袋', status: 'ACTIVE', variants: [{ skuCode: 'SKU-001-BLK', variantName: '黑色款', status: 'ACTIVE', suppliers: [], materials: [] }] })
    expect((wrapper.get('.product-form select').element as HTMLSelectElement).value).toBe('ACTIVE')
    expect((wrapper.get('.nested-form select').element as HTMLSelectElement).value).toBe('ACTIVE')
    expect(wrapper.text()).not.toContain('动态属性')
    expect(wrapper.text()).not.toContain('重量单位')
  })

  it('adds independent SKU aggregate editors', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    await wrapper.findAll('button').find(button => button.text() === '新增 SKU')!.trigger('click')
    expect(wrapper.findAll('.variant-editor')).toHaveLength(2)
    expect(wrapper.findAll('select[aria-label="添加辅料"]')).toHaveLength(2)
  })

  it('offers a cancel action when embedded in a dialog', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品', showCancel: true } })
    await wrapper.findAll('button').find(button => button.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('accepts an HTTPS SKU image URL as an alternative to upload', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    await wrapper.get('input[aria-label="SKU 1 图片 URL"]').setValue('https://images.example.com/sku-1.png')
    const skuInputs = wrapper.find('.variant-editor').findAll('input[required]')
    await skuInputs[0]!.setValue('SKU-URL')
    await skuInputs[1]!.setValue('URL 图片款')
    const productInputs = wrapper.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('PROD-URL')
    await productInputs[1]!.setValue('URL 图片产品')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      variants: [{ imageUrl: 'https://images.example.com/sku-1.png', imageAssetId: undefined }],
    })
    expect(wrapper.get('.sku-image-preview').attributes('src')).toBe('https://images.example.com/sku-1.png')
  })
})
