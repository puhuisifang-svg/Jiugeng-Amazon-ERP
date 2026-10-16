import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createProduct, skus } = vi.hoisted(() => ({ createProduct: vi.fn(), skus: vi.fn() }))
const product = { id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', version: 0, skuCount: 1, updatedAt: '2026-10-15', variants: [{ id: 'v1', skuCode: 'SKU-1', variantName: '标准款', status: 'ACTIVE', version: 0, imageUrl: 'https://images.example.com/sku-1.png', attributes: [], suppliers: [], materials: [] }] }

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { user: { value: { id: 'user-1' } }, canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    products: vi.fn(async () => ({ items: [product], mockData: false })),
    skus,
    product: vi.fn(async () => product),
    createProduct,
    updateProduct: vi.fn(),
    batchUpdateSkus: vi.fn(),
    materials: vi.fn(async () => ({ items: [], mockData: false })),
    balances: vi.fn(async () => ({ items: [], mockData: false })),
    suppliers: vi.fn(async () => ({ items: [], totalCount: 0, page: 0, size: 100 })),
  },
}))

import ProductsView from './ProductsView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('ProductsView', () => {
  beforeEach(() => {
    localStorage.clear()
    skus.mockReset().mockResolvedValue({ items: [{ productId: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', productStatus: 'ACTIVE', businessAvailable: true, updatedAt: '2026-10-15', variant: product.variants[0] }], totalCount: 1, page: 0, size: 20 })
    createProduct.mockReset().mockResolvedValue({ item: product, mockData: false })
  })

  it('renders Sellfox-style filters, toolbar and the SKU-first table', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.get('.sku-filter-card').text()).toContain('商品类型')
    expect(wrapper.get('.sku-filter-card').text()).toContain('SKU 状态')
    expect(wrapper.get('.material-toolbar').text()).toContain('新增产品')
    expect(wrapper.get('.material-toolbar').text()).toContain('列设置')
    const row = wrapper.get('.sku-master-table tbody tr')
    expect(row.text()).toContain('SKU-1')
    expect(row.text()).toContain('测试产品')
    expect(row.text()).toContain('详情')
    expect(row.get('img').attributes('src')).toBe('https://images.example.com/sku-1.png')
  })

  it('creates a draft product in the centered editor without replacing the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.get('.material-toolbar button').trigger('click')
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('产品基础信息')
    expect(wrapper.text()).toContain('SKU-1')

    const productInputs = dialog.find('.product-form').findAll('input')
    await productInputs[0]!.setValue('P-2')
    await productInputs[1]!.setValue('新产品')
    const skuInputs = dialog.find('.variant-editor').findAll('input[required]')
    await skuInputs[0]!.setValue('SKU-2')
    await skuInputs[1]!.setValue('标准款')
    await dialog.get('form.product-create-form').trigger('submit')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productCode: 'P-2', productName: '新产品', status: 'DRAFT', variants: [expect.objectContaining({ skuCode: 'SKU-2', variantName: '标准款', status: 'DRAFT' })],
    }))
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('产品及 SKU 已创建')
  })

  it('persists selected table columns for the current user', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    const buttons = wrapper.findAll('button')
    await buttons.find(button => button.text() === '列设置')!.trigger('click')
    const dialog = wrapper.get('.column-dialog')
    const imageColumn = dialog.findAll('input[type="checkbox"]')[0]!
    await imageColumn.setValue(false)
    await dialog.findAll('button').find(button => button.text() === '保存')!.trigger('click')
    expect(localStorage.getItem('jiugeng.product-sku.columns.user-1')).not.toContain('image')
  })
})
