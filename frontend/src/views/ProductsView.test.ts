import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createProduct } = vi.hoisted(() => ({ createProduct: vi.fn() }))

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: { products: vi.fn(async () => ({ items: [
    { id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', skuCount: 1, updatedAt: '2026-10-15',
      variants: [{ id: 'v1', skuCode: 'SKU-1', variantName: '标准款', status: 'ACTIVE', imageUrl: 'https://images.example.com/sku-1.png' }] },
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
      id: 'p2', productCode: 'P-2', productName: '新产品', productType: '家居', status: 'ACTIVE', skuCount: 1,
      variants: [{ id: 'v2', skuCode: 'SKU-2', variantName: '标准款', status: 'ACTIVE', attributes: [], suppliers: [], materials: [] }],
    }, mockData: false })
  })

  it('orders search, create action and product list consistently', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    const sections = wrapper.findAll('.filter-panel, .material-toolbar, .table-panel')
    expect(sections.map(item => item.classes())).toEqual([
      expect.arrayContaining(['filter-panel']),
      expect.arrayContaining(['material-toolbar']),
      expect.arrayContaining(['table-panel']),
    ])
    expect(wrapper.get('.material-toolbar button').text()).toContain('新增产品')
    const row = wrapper.get('.product-table tbody tr')
    expect(row.text()).toContain('SKU-1')
    expect(row.text()).toContain('启用')
    expect(row.get('img').attributes('src')).toBe('https://images.example.com/sku-1.png')
    expect(row.get('a[href="/products/p1"]').text()).toBe('查看')
  })

  it('creates a product from an independent dialog without replacing the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.get('.material-toolbar button').trigger('click')
    const dialog = wrapper.get('[role="dialog"][aria-labelledby="product-create-dialog-title"]')
    expect(dialog.text()).toContain('产品基础信息')
    expect(wrapper.text()).toContain('P-1')
    expect(dialog.find('.product-create-form > .form-footer').exists()).toBe(true)

    const productInputs = dialog.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('P-2')
    await productInputs[1]!.setValue('新产品')
    const skuInputs = dialog.find('.variant-editor').findAll('input[required]')
    await skuInputs[0]!.setValue('SKU-2')
    await skuInputs[1]!.setValue('标准款')
    await dialog.get('form.product-create-form').trigger('submit')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'P-2', productName: '新产品', status: 'ACTIVE', variants: [expect.objectContaining({ skuCode: 'SKU-2', variantName: '标准款', status: 'ACTIVE' })],
    }))
    expect(wrapper.find('[aria-labelledby="product-create-dialog-title"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('产品及首个 SKU 已创建')
    expect(wrapper.text()).toContain('P-2')
  })
})
