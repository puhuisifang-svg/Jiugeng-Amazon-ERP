import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createProduct } = vi.hoisted(() => ({ createProduct: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: { products: vi.fn(async () => ({ items: [
    { id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', skuCount: 1, variants: [] },
  ], mockData: false })), createProduct,
  materials: vi.fn(async () => ({ items: [], mockData: false })),
  balances: vi.fn(async () => ({ items: [], mockData: false })),
  suppliers: vi.fn(async () => ({ items: [], totalCount: 0, page: 0, size: 100 })),
  },
}))

import ProductsView from './ProductsView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('ProductsView', () => {
  beforeEach(() => {
    createProduct.mockReset().mockResolvedValue({ item: {
      id: 'p2', productCode: 'P-2', productName: '新产品', productType: '家居', status: 'DRAFT', skuCount: 1,
      variants: [{ id: 'v2', skuCode: 'SKU-2', variantName: '标准款', status: 'DRAFT', attributes: [], suppliers: [], materials: [] }],
    }, mockData: false })
  })

  it('orders search, create action and product list consistently', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    const sections = wrapper.findAll('.filter-panel, .material-toolbar, .card-list')
    expect(sections.map(item => item.classes())).toEqual([
      expect.arrayContaining(['filter-panel']),
      expect.arrayContaining(['material-toolbar']),
      expect.arrayContaining(['card-list']),
    ])
    expect(wrapper.get('.material-toolbar button').text()).toContain('新增产品')
  })

  it('creates a product from an independent dialog without replacing the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.get('.material-toolbar button').trigger('click')
    const dialog = wrapper.get('[role="dialog"][aria-labelledby="product-create-dialog-title"]')
    expect(dialog.text()).toContain('产品基础信息')
    expect(wrapper.text()).toContain('P-1')

    const productInputs = dialog.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('P-2')
    await productInputs[1]!.setValue('新产品')
    const skuInputs = dialog.find('.variant-editor').findAll('input')
    await skuInputs[1]!.setValue('SKU-2')
    await skuInputs[2]!.setValue('标准款')
    await dialog.get('form.product-create-form').trigger('submit')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'P-2', productName: '新产品', variants: [expect.objectContaining({ skuCode: 'SKU-2', variantName: '标准款' })],
    }))
    expect(wrapper.find('[aria-labelledby="product-create-dialog-title"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('产品及首个 SKU 已创建')
    expect(wrapper.text()).toContain('P-2')
  })
})
