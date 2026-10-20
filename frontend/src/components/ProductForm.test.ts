import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ProductForm from './ProductForm.vue'
import { catalogApi } from '@/api/catalog'

describe('ProductForm', () => {
  it('uploads a parent image independently, blocks saving during upload, and supports removal', async () => {
    const spy = vi.spyOn(catalogApi, 'uploadImage')
    let done!: (value: Awaited<ReturnType<typeof catalogApi.uploadImage>>) => void
    spy.mockImplementationOnce(() => new Promise(resolve => { done = resolve }))
    const wrapper = mount(ProductForm, { props: { submitLabel: '保存', initial: { productCode: 'SYN-P', productName: '合成父体', imageAssetId: 'old-parent', status: 'DRAFT', variants: [{ skuCode: 'SYN-S', variantName: '子体', imageAssetId: 'sku-image', status: 'DRAFT', suppliers: [], materials: [] }] } }, global: { stubs: { AuthenticatedCatalogImage: true } } })
    const field = wrapper.get('input[aria-label="上传父体图片"]')
    const file = new File(['synthetic'], 'synthetic.png', { type: 'image/png' })
    Object.defineProperty(field.element, 'files', { value: [file], configurable: true })
    await field.trigger('change')
    expect(spy).toHaveBeenCalledWith('PRODUCT_MAIN', file)
    await wrapper.get('form').trigger('submit'); expect(wrapper.emitted('submit')).toBeUndefined()
    done({ id: 'new-parent' } as Awaited<ReturnType<typeof catalogApi.uploadImage>>); await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ imageAssetId: 'new-parent', variants: [{ imageAssetId: 'sku-image' }] })
    spy.mockRejectedValueOnce(new Error('合成上传失败'))
    await field.trigger('change'); await flushPromises()
    expect(wrapper.text()).toContain('合成上传失败')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[1]?.[0]).toMatchObject({ imageAssetId: 'new-parent' })
    await wrapper.findAll('button').find(b => b.text() === '移除父体图片')!.trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[2]?.[0]).toMatchObject({ imageAssetId: null, variants: [{ imageAssetId: 'sku-image' }] })
    wrapper.unmount(); spy.mockRestore()
  })

  it('submits customs with the SKU aggregate and preserves edits across tabs', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品', initial: { productCode: 'SYN-P', productName: '合成父体', status: 'DRAFT', variants: [{ skuCode: 'SYN-S', variantName: '合成子体', status: 'DRAFT', suppliers: [], materials: [] }] } } })
    await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '报关资料')!.trigger('click')
    const fields = wrapper.findAll('.customs-fields label')
    await fields.find(label => label.text().startsWith('英文申报品名'))!.get('input').setValue('Synthetic pouch')
    await fields.find(label => label.text().startsWith('申报要素'))!.get('textarea').setValue('合成材质：示例')
    await wrapper.findAll('.product-editor-tabs button').find(button => button.text() === '基础信息')!.trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ variants: [{ customs: { nameEn: 'Synthetic pouch', declarationElements: '合成材质：示例', state: 'DRAFT', version: 0 } }] })
    wrapper.unmount()
  })

  it('submits the unified product name with draft product and SKU defaults', async () => {
    const wrapper = mount(ProductForm, { props: { submitLabel: '创建产品' } })
    const productInputs = wrapper.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('PROD-001'); await productInputs[1]!.setValue('旅行收纳袋')
    const skuInputs = wrapper.find('.variant-editor').findAll('input[required]')
    await skuInputs[0]!.setValue('SKU-001-BLK'); await skuInputs[1]!.setValue('黑色款')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ productCode: 'PROD-001', productName: '旅行收纳袋', status: 'DRAFT', variants: [{ skuCode: 'SKU-001-BLK', variantName: '黑色款', status: 'DRAFT', suppliers: [], materials: [] }] })
    expect((wrapper.get('.product-form select').element as HTMLSelectElement).value).toBe('DRAFT')
    expect((wrapper.get('.nested-form select').element as HTMLSelectElement).value).toBe('DRAFT')
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
