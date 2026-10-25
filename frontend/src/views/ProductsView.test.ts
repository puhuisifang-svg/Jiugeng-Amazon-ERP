import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createProduct, skus } = vi.hoisted(() => ({ createProduct: vi.fn(), skus: vi.fn() }))
const product: import('@/api/domain').ProductDetailDto = { nameZh: '测试产品', supplierDetailsAvailable: true, id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', version: 0, skuCount: 1, updatedAt: '2026-10-15', variants: [{ id: 'v1', skuCode: 'SKU-1', variantName: '标准款', status: 'ACTIVE', version: 0, imageUrl: 'https://images.example.com/sku-1.png', attributes: [], suppliers: [], materials: [] }] }

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { user: { value: { id: 'user-1' } }, canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn(), documents: vi.fn(async () => []) } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    products: vi.fn(async () => ({ items: [product], mockData: false })),
    skus,
    product: vi.fn(async () => product),
    createProduct,
    updateProduct: vi.fn(),
    deleteProduct: vi.fn(), deleteSku: vi.fn(),
    batchUpdateSkus: vi.fn(),
    exportSkus: vi.fn(),
    productHistory: vi.fn(async () => []),
    materials: vi.fn(async () => ({ items: [], mockData: false })),
    balances: vi.fn(async () => ({ items: [], mockData: false })),
    suppliers: vi.fn(async () => ({ items: [], totalCount: 0, page: 0, size: 100 })),
  },
}))

import ProductsView from './ProductsView.vue'
import { domainApi } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('ProductsView', () => {
  it('opens imports in product management instead of materials', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('a').find(link => link.text() === '导入')!.attributes('href')).toBe('/products/imports')
    wrapper.unmount()
  })
  it('shows parent thumbnail, opens its preview and preserves it in the parent editor', async () => {
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items: [{ ...product, imageAssetId: 'parent-asset' }], mockData: false } as Awaited<ReturnType<typeof domainApi.products>>)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink, AuthenticatedCatalogImage: true } } })
    await flushPromises()
    await wrapper.get('[aria-label="查看父体 测试产品 图片"]').trigger('click')
    expect(wrapper.get('[aria-label="测试产品图片预览"]').get('authenticated-catalog-image-stub').attributes('imageid')).toBe('parent-asset')
    await wrapper.get('[aria-label="关闭图片预览"]').trigger('click')
    vi.mocked(domainApi.product).mockResolvedValueOnce({ ...product, imageAssetId: 'parent-asset' } as Awaited<ReturnType<typeof domainApi.product>>)
    await wrapper.findAll('button').find(b => b.text() === '编辑父体')!.trigger('click'); await flushPromises()
    expect(wrapper.findComponent(ProductForm).props('initial')).toMatchObject({ imageAssetId: 'parent-asset' })
    wrapper.unmount()
  })
  it('opens a product image at full size and closes the preview', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('[aria-label="查看标准款大图"]').trigger('click')
    const preview = wrapper.get('[aria-label="标准款图片预览"]')
    expect(preview.get('img').attributes('src')).toBe(product.variants[0]!.imageUrl)
    await preview.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[aria-label="标准款图片预览"]').exists()).toBe(false)
  })
  beforeEach(() => {
    localStorage.clear()
    HTMLDialogElement.prototype.showModal = function () { this.open = true }
    vi.mocked(domainApi.deleteProduct).mockReset().mockResolvedValue(undefined)
    vi.mocked(domainApi.deleteSku).mockReset().mockResolvedValue(undefined)
    skus.mockReset().mockResolvedValue({ items: [{ productId: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', productStatus: 'ACTIVE', businessAvailable: true, updatedAt: '2026-10-15', variant: product.variants[0] }], totalCount: 1, page: 0, size: 20 })
    createProduct.mockReset().mockResolvedValue({ item: product, mockData: false })
  })

  it('requires parent and SKU confirmation and retains rejection reasons', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '删除父体')!.trigger('click')
    expect(domainApi.deleteProduct).not.toHaveBeenCalled()
    expect(wrapper.get('dialog').text()).toContain('全部 SKU')
    expect(wrapper.get('dialog').text()).toContain('采用逻辑删除')
    expect(wrapper.get('dialog').text()).toContain('保留图片、资料和历史单据')
    expect(wrapper.get('dialog').text()).not.toContain('存在发货、发票、采购或收货记录时将拒绝删除')
    await wrapper.get('dialog .secondary-button').trigger('click')
    expect(wrapper.find('dialog').exists()).toBe(false)
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '删除 SKU')!.trigger('click')
    vi.mocked(domainApi.deleteSku).mockRejectedValueOnce(new Error('存在历史发货记录'))
    await wrapper.get('dialog .danger-button').trigger('click'); await flushPromises()
    expect(domainApi.deleteSku).toHaveBeenCalledWith('p1', 'v1', 0, 0)
    expect(wrapper.get('dialog [role="alert"]').text()).toBe('存在历史发货记录')
    await wrapper.get('dialog .secondary-button').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '删除父体')!.trigger('click')
    await wrapper.get('dialog .danger-button').trigger('click'); await flushPromises()
    expect(domainApi.deleteProduct).toHaveBeenCalledWith('p1', 0)
    expect(wrapper.find('dialog').exists()).toBe(false); wrapper.unmount()
  })
  it('renders Sellfox-style filters, toolbar and the SKU-first table', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.get('.product-management-filters').text()).toContain('产品状态')
    expect(wrapper.get('.product-management-filters').text()).toContain('库存状态')
    expect(wrapper.get('.material-toolbar').text()).toContain('新增产品')
    expect(wrapper.get('.material-toolbar').text()).toContain('列设置')
    expect(wrapper.find('.product-child-row').exists()).toBe(false)
    await wrapper.get('.parent-toggle').trigger('click')
    const row = wrapper.get('.product-child-row')
    expect(row.get('.hierarchy-cell').text()).toContain('SKU-1')
    expect(row.text()).toContain('家居')
    expect(row.text()).toContain('详情')
    expect(row.get('img').attributes('src')).toBe('https://images.example.com/sku-1.png')
  })

  it('creates a draft product in the centered editor without replacing the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.get('.material-toolbar button').trigger('click')
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('产品基础信息')
    expect(wrapper.text()).toContain('P-1')

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
    expect(localStorage.getItem('jiugeng.product-management.columns.v2.user-1')).not.toContain('image')
  })

  it('opens and saves a reactive SKU without losing sibling or dimension data', async () => {
    const sibling = { ...product.variants[0]!, id: 'v2', skuCode: 'SKU-2', itemLengthCm: 10, itemWidthCm: 20, itemHeightCm: 30 }
    vi.mocked(domainApi.product).mockResolvedValueOnce({ ...product, variants: [...product.variants, sibling] } as never)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('.product-child-row').findAll('button').find(button => button.text() === '编辑')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('编辑 SKU')
    await wrapper.get('form.product-create-form').trigger('submit')
    await flushPromises()
    expect(domainApi.updateProduct).toHaveBeenCalledWith('p1', expect.objectContaining({ variants: [expect.objectContaining({ id: 'v1' }), expect.objectContaining({ id: 'v2', itemLengthCm: 10 })] }))
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('copies to a new child with draft state and no copied relations', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('.product-child-row').findAll('button').find(button => button.text() === '复制')!.trigger('click')
    await flushPromises()
    const form = wrapper.getComponent(ProductForm)
    expect(form.props('focusVariantId')).toBe('__NEW__')
    expect(form.props('initial')?.variants).toHaveLength(2)
    expect(form.props('initial')?.variants[1]).toMatchObject({ skuCode: '', status: 'DRAFT', suppliers: [], materials: [] })
  })

  it('filters child SKU and automatically expands its matching parent', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.product-management-filters input[type="search"]').setValue('SKU-1')
    await flushPromises()
    expect(wrapper.get('.product-child-row').text()).toContain('SKU-1')
    await wrapper.get('.product-management-filters input[type="search"]').setValue('missing')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无匹配的产品')
  })

  it('selects children from the parent and submits their versions atomically', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('input[aria-label="选择父体 P-1"]').setValue(true)
    await wrapper.findAll('button').find(button => button.text() === '批量修改')!.trigger('click')
    await wrapper.get('.batch-dialog input[type="checkbox"]').setValue(true)
    await wrapper.get('.batch-dialog').trigger('submit')
    await flushPromises()
    expect(domainApi.batchUpdateSkus).toHaveBeenCalledWith([expect.objectContaining({ productId: 'p1', variantId: 'v1', productVersion: 0, variantVersion: 0, statusSelected: true, status: 'ACTIVE' })])
  })

  it('shows API errors inside the editor and keeps the user input', async () => {
    createProduct.mockRejectedValueOnce(new Error('产品编码已存在'))
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.material-toolbar button').trigger('click')
    wrapper.getComponent(ProductForm).vm.$emit('submit', { productCode: 'P-1', productName: '重复', status: 'DRAFT', variants: [] })
    await flushPromises()
    expect(wrapper.get('[role="dialog"] [role="alert"]').text()).toBe('产品编码已存在')
  })

  it('applies saved column order to the real table', async () => {
    localStorage.setItem('jiugeng.product-management.columns.v2.user-1', JSON.stringify(['name', 'sku', 'status']))
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('thead th').map(cell => cell.text())).toEqual(['', '品名', '产品编码 / SKU', '状态', '操作'])
  })

  it('adds a child from its parent without changing existing siblings', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加子体')!.trigger('click')
    await flushPromises()
    const form = wrapper.getComponent(ProductForm)
    expect(form.props('focusVariantId')).toBe('__NEW__')
    expect(form.props('initial')?.variants[0]?.skuCode).toBe('SKU-1')
    expect(form.props('initial')?.variants[1]?.skuCode).toBe('')
  })

  it('paginates complete parent groups without splitting siblings', async () => {
    const items = Array.from({ length: 21 }, (_, index) => ({ ...product, id: `p${index}`, productCode: `P-${index}`, variants: [0, 1].map(child => ({ ...product.variants[0], id: `v${index}-${child}`, skuCode: `SKU-${index}-${child}` })) }))
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items, mockData: false } as never)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(20)
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(2)
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(1)
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(2)
  })

  it('filters keywords across names and specifications, types and product status', async () => {
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items: [
      { ...product, variants: [{ ...product.variants[0], specification: '蓝色大号' }] },
      { ...product, id: 'p2', productCode: 'P-2', productType: '电子', status: 'DRAFT', variants: [{ ...product.variants[0], id: 'v2', skuCode: 'SKU-2' }] },
    ], mockData: false } as never)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    const input = wrapper.get('.product-management-filters input[type="search"]')
    await input.setValue('蓝色')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(1)
    await input.setValue('测试')
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(2)
    await input.setValue('')
    const selects = wrapper.findAll('.product-management-filters select')
    await selects[0]!.setValue('电子')
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(1)
    await selects[1]!.setValue('ACTIVE')
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(0)
    await selects[1]!.setValue('DRAFT')
    await selects[2]!.setValue('UNINITIALIZED')
    expect(wrapper.get('.product-parent-row').text()).toContain('P-2')
    await wrapper.get('.product-management-filters button').trigger('click')
    await flushPromises()
    expect((input.element as HTMLInputElement).value).toBe('')
    expect((selects[0]!.element as HTMLSelectElement).value).toBe('')
  })

  it('opens the separate manual and label tabs for the selected child', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('.product-child-row .sku-code-button').trigger('click')
    await flushPromises()
    for (const title of ['说明书管理', '商品标签管理']) {
      await wrapper.findAll('button').find(button => button.text() === title)!.trigger('click')
      await flushPromises()
      expect(wrapper.get('.sku-documents h3').text()).toBe(title)
    }
  })

  it('exports the current live keyword filters', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.product-management-filters input[type="search"]').setValue('SKU')
    await wrapper.findAll('button').find(button => button.text() === '导出')!.trigger('click')
    await wrapper.get('.batch-dialog').trigger('submit')
    await flushPromises()
    expect(domainApi.exportSkus).toHaveBeenCalledWith(expect.objectContaining({ q: 'SKU', field: 'KEYWORD', exact: false }))
  })
})
