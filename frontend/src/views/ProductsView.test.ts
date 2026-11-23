import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { createProduct, skus } = vi.hoisted(() => ({ createProduct: vi.fn(), skus: vi.fn() }))
const product: import('@/api/domain').ProductDetailDto = { nameZh: '测试产品', supplierDetailsAvailable: true, id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', version: 0, skuCount: 1, updatedAt: '2026-10-15', variants: [{ id: 'v1', skuCode: 'SKU-1', variantName: '标准款', status: 'ACTIVE', version: 0, imageUrl: 'https://images.example.com/sku-1.png', attributes: [], suppliers: [], materials: [] }] }

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { user: { value: { id: 'user-1' } }, canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn(), externalImageBlob: vi.fn(async () => new Blob(['synthetic-image'])), documents: vi.fn(async () => []) } }))
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

vi.mock('@/api/productManagement', () => ({ productManagementApi: { page: vi.fn(), updateSku: vi.fn(), skuHistory: vi.fn(async () => []), balances: vi.fn(), stockHistory: vi.fn(), transact: vi.fn() } }))
import { productManagementApi, type ProductPage } from '@/api/productManagement'

import ProductsView from './ProductsView.vue'
import { catalogApi } from '@/api/catalog'
import { domainApi } from '@/api/domain'
import ProductForm from '@/components/ProductForm.vue'

enableAutoUnmount(afterEach)
function pageOf(items: import('@/api/domain').ProductDetailDto[]): ProductPage {
  const ids = items.flatMap(p => p.variants.map(v => v.id!))
  return { items, matchingVariantIds: ids, matchingSkuCount: ids.length, totalCount: items.length, page: 0, size: 20, balances: [] }
}
const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('ProductsView', () => {
  it('keeps images before names with legacy column preferences and preserves multiline highlighting', async () => {
    localStorage.setItem('jiugeng.product-management.columns.v2.user-1', JSON.stringify(['image', 'name', 'sku', 'status']))
    const highlighted = { ...product, variants: [{ ...product.variants[0]!, variantName: '合成名称\n规格补充', nameHighlighted: true }] }
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items: [highlighted], mockData: false } as Awaited<ReturnType<typeof domainApi.products>>)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('thead th').slice(1, 3).map(th => th.text())).toEqual(['图片', '品名'])
    expect(wrapper.findAll('thead th').filter(th => th.text() === '品名')).toHaveLength(1)
    expect(wrapper.get('.parent-toggle .product-display-name').text()).toContain('测试产品')
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.get('.sku-code-button .product-display-name').element.textContent).toBe('合成名称\n规格补充')
    expect(wrapper.get('.sku-code-button .product-display-name span').attributes('style')).toContain('color: rgb(198, 40, 40)')
    expect(wrapper.find('.sku-code-button .product-display-code').exists()).toBe(false)
    vi.mocked(domainApi.product).mockResolvedValueOnce(highlighted as Awaited<ReturnType<typeof domainApi.product>>)
    await wrapper.findAll('button').find(b => b.text() === '编辑父体')!.trigger('click'); await flushPromises()
    expect(wrapper.findComponent(ProductForm).props('initial')?.variants[0]).toMatchObject({ nameHighlighted: true, variantName: '合成名称\n规格补充' })
    wrapper.unmount()
  })
  it('uses a compact text reset action and clears search filters', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    vi.mocked(productManagementApi.page).mockResolvedValueOnce(pageOf([]))
    await wrapper.get('input[type="search"]').setValue('no-match')
    await vi.waitFor(() => expect(wrapper.find('.product-parent-row').exists()).toBe(false))
    await wrapper.get('.filter-reset-button').trigger('click'); await flushPromises()
    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('.product-parent-row').exists()).toBe(true)
    wrapper.unmount()
  })
  it('shows only details and more in the parent action column', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink, Teleport: true } } })
    await flushPromises()
    const cell = wrapper.get('.product-parent-row .sticky-action')
    expect(cell.get('button.row-detail-link').text()).toBe('详情')
    expect(cell.text()).not.toContain('展开子体')
    expect(cell.get('.row-more-actions__menu').isVisible()).toBe(false)
    await cell.get('.row-more-actions__trigger').trigger('click'); await flushPromises()
    expect(cell.get('.row-more-actions__menu').isVisible()).toBe(true)
    expect(cell.get('.row-more-actions__menu').text()).toContain('删除父体')
    wrapper.unmount()
  })
  it('opens parent details in a dialog and keeps the list when closing', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink, Teleport: true } } })
    await flushPromises()
    await wrapper.get('.product-parent-row .row-detail-link').trigger('click'); await flushPromises()
    expect(wrapper.get('dialog[aria-label="产品详情"]').text()).toContain('SKU 概览')
    await wrapper.get('dialog[aria-label="产品详情"] button[aria-label="关闭"]').trigger('click')
    expect(wrapper.find('dialog[aria-label="产品详情"]').exists()).toBe(false)
    expect(wrapper.find('.product-parent-row').exists()).toBe(true)
    wrapper.unmount()
  })
  it('opens imports in product management instead of materials', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('a').find(link => link.text() === '导入')!.attributes('href')).toBe('/products/imports')
    wrapper.unmount()
  })
  it('shows parent thumbnail, opens its preview and preserves it in the parent editor', async () => {
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items: [{ ...product, imageAssetId: 'parent-asset' }], mockData: false } as Awaited<ReturnType<typeof domainApi.products>>)
    const wrapper = mount(ProductsView, { global: { stubs: { Teleport: true, RouterLink, AuthenticatedCatalogImage: true } } })
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
    const wrapper = mount(ProductsView, { global: { stubs: { Teleport: true, RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('[aria-label="查看标准款大图"]').trigger('click'); await flushPromises()
    const preview = wrapper.get('[aria-label="标准款图片预览"]')
    expect(preview.get('img').attributes('src')).toBe('blob:synthetic-controlled')
    expect(catalogApi.externalImageBlob).toHaveBeenCalledWith(product.variants[0]!.imageUrl, 'original', expect.any(AbortSignal), 'preview', undefined)
    await preview.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[aria-label="标准款图片预览"]').exists()).toBe(false)
  })
  beforeEach(() => {
    localStorage.clear(); sessionStorage.clear()
    URL.createObjectURL = vi.fn(() => 'blob:synthetic-controlled'); URL.revokeObjectURL = vi.fn()
    vi.mocked(catalogApi.externalImageBlob).mockClear()
    vi.mocked(productManagementApi.page).mockReset().mockImplementation(async () => pageOf((await domainApi.products()).items))
    vi.mocked(productManagementApi.updateSku).mockReset().mockResolvedValue(product)
    vi.mocked(domainApi.products).mockReset().mockResolvedValue({ items: [product], mockData: false } as never)
    vi.mocked(domainApi.updateProduct).mockClear()
    HTMLDialogElement.prototype.showModal = function () { this.open = true }
    HTMLDialogElement.prototype.close = function () { this.open = false }
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
    expect(wrapper.get('dialog').text()).toContain('请先删除全部子体')
    expect(wrapper.get('dialog .danger-button').attributes('disabled')).toBeDefined()
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
    expect(domainApi.deleteProduct).not.toHaveBeenCalled()
    expect(wrapper.find('dialog').exists()).toBe(true); wrapper.unmount()
  })
  it('allows empty parents but keeps a server rejection visible if a child was added meanwhile', async () => {
    vi.mocked(domainApi.products).mockResolvedValueOnce({ items: [{ ...product, variants: [], skuCount: 0 }], mockData: false } as Awaited<ReturnType<typeof domainApi.products>>)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '删除父体')!.trigger('click')
    expect(wrapper.get('dialog .danger-button').attributes('disabled')).toBeUndefined()
    vi.mocked(domainApi.deleteProduct).mockRejectedValueOnce(new Error('请先删除全部子体后再删除父体'))
    await wrapper.get('dialog .danger-button').trigger('click'); await flushPromises()
    expect(wrapper.get('dialog [role="alert"]').text()).toContain('请先删除全部子体')
    await wrapper.get('dialog .danger-button').trigger('click'); await flushPromises()
    expect(domainApi.deleteProduct).toHaveBeenLastCalledWith('p1', 0)
    expect(wrapper.find('dialog').exists()).toBe(false)
    wrapper.unmount()
  })
  it('opens the supplier and material count columns at the corresponding SKU detail tab', async () => {
    localStorage.setItem('jiugeng.product-management.columns.v2.user-1', JSON.stringify(['sku', 'supplier', 'material']))
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink, SkuDetailDialog: true } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('[aria-label="查看标准款的供应商"]').trigger('click')
    let detail = wrapper.findComponent({ name: 'SkuDetailDialog' })
    expect(detail.props('initialTab')).toBe('purchase')
    expect(detail.props('detailRow').variant.id).toBe('v1')
    detail.vm.$emit('close'); await flushPromises()
    await wrapper.get('[aria-label="查看标准款的关联辅料"]').trigger('click')
    detail = wrapper.findComponent({ name: 'SkuDetailDialog' })
    expect(detail.props('initialTab')).toBe('materials')
    detail.vm.$emit('close'); await flushPromises()
    await wrapper.get('.sku-code-button').trigger('click')
    expect(wrapper.findComponent({ name: 'SkuDetailDialog' }).props('initialTab')).toBe('basic')
    wrapper.unmount()
  })
  it('renders Sellfox-style filters, toolbar and the SKU-first table', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    expect(wrapper.get('.product-management-filters').text()).toContain('产品状态')
    expect(wrapper.get('.product-management-filters').text()).toContain('库存')
    expect(wrapper.get('.material-toolbar').text()).toContain('新增产品')
    expect(wrapper.get('.material-toolbar').text()).toContain('列设置')
    expect(wrapper.find('.product-child-row').exists()).toBe(false)
    await wrapper.get('.parent-toggle').trigger('click')
    await flushPromises()
    const row = wrapper.get('.product-child-row')
    expect(row.get('.hierarchy-cell').text()).toContain('标准款')
    expect(wrapper.text()).not.toContain('产品类型')
    expect(row.text()).toContain('详情')
    expect(row.get('img').attributes('src')).toBe('blob:synthetic-controlled')
  })

  it('creates a draft product in the centered editor without replacing the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.get('.material-toolbar button').trigger('click')
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('父体资料')
    expect(wrapper.text()).toContain('测试产品')

    const productInputs = dialog.find('.product-form').findAll('input[required]')
    await productInputs[0]!.setValue('新产品')
    const skuInputs = dialog.find('.variant-editor').findAll('input[required], textarea[required]')
    await skuInputs[0]!.setValue('标准款')
    await dialog.get('form.product-create-form').trigger('submit')
    await flushPromises()

    expect(createProduct).toHaveBeenCalledWith(expect.objectContaining({
      productCode: '', productName: '新产品', status: 'ACTIVE', variants: [expect.objectContaining({ skuCode: '', variantName: '标准款', status: 'ACTIVE' })],
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

  it('migrates saved image order for parent and child rows without losing optional columns', async () => {
    localStorage.setItem('jiugeng.product-management.columns.v2.user-1', JSON.stringify(['sku', 'weight', 'image', 'status']))
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('thead th').map(cell => cell.text())).toEqual(['', '图片', '品名', '包装重量', '状态', '操作'])
    await wrapper.get('.parent-toggle').trigger('click')
    for (const row of wrapper.findAll('tbody tr')) {
      expect(row.findAll('td')[1]!.classes()).toContain('product-column--image')
      expect(row.findAll('td')[2]!.classes()).toContain('hierarchy-cell')
    }
    await wrapper.get('input[aria-label="选择 标准款"]').setValue(true)
    expect(wrapper.get('.product-child-row').classes()).toContain('is-selected')
    expect(wrapper.get('.product-parent-row').classes()).toContain('is-selected')
    wrapper.unmount()
  })

  it('keeps image first when re-enabled and allows other columns to be reordered and restored', async () => {
    localStorage.setItem('jiugeng.product-management.columns.v2.user-1', JSON.stringify(['sku', 'weight', 'status']))
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '列设置')!.trigger('click')
    const dialog = wrapper.get('.column-dialog')
    await dialog.get('input[value="image"]').setValue(true)
    expect(dialog.findAll('ol li > span').map(item => item.text())).toEqual(['图片', '品名', '包装重量', '状态'])
    expect(dialog.get('[aria-label="下移图片"]').attributes('disabled')).toBeDefined()
    expect(dialog.get('[aria-label="上移包装重量"]').attributes('disabled')).toBeDefined()
    await dialog.get('[aria-label="上移状态"]').trigger('click')
    await dialog.findAll('button').find(button => button.text() === '保存')!.trigger('click')
    expect(JSON.parse(localStorage.getItem('jiugeng.product-management.columns.v2.user-1')!)).toEqual(['image', 'sku', 'status', 'weight'])
    wrapper.unmount()
    const restored = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(restored.findAll('thead th').map(cell => cell.text())).toEqual(['', '图片', '品名', '状态', '包装重量', '操作'])
    restored.unmount()
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
    expect(productManagementApi.updateSku).toHaveBeenCalledWith('p1', expect.objectContaining({ id: 'v1' }))
    expect(domainApi.updateProduct).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('copies a child with supplier and material associations but no identity or inventory', async () => {
    const source = { ...product, variants: [{ ...product.variants[0]!, suppliers: [{ supplierId: 's1', primary: true, remark: '保留采购备注' }], materials: [{ materialId: 'm1', standardQuantity: 2, unit: '个', lossRate: 0.025, enabled: true }] }] }
    vi.mocked(domainApi.product).mockResolvedValueOnce(source)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('.product-child-row').findAll('button').find(button => button.text() === '复制')!.trigger('click')
    await flushPromises()
    const form = wrapper.getComponent(ProductForm)
    expect(form.props('focusVariantId')).toBe('__NEW__')
    expect(form.props('initial')?.variants).toHaveLength(2)
    expect(form.props('initial')?.variants[1]).toMatchObject({ skuCode: '', status: 'ACTIVE', suppliers: [{ supplierId: 's1', remark: '保留采购备注' }], materials: [{ materialId: 'm1', standardQuantity: 2, lossRate: 0.025 }] })
    expect(form.props('initial')?.variants[1]?.id).toBeUndefined()
  })

  it('filters child SKU and automatically expands its matching parent', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.product-management-filters input[type="search"]').setValue('SKU-1')
    await vi.waitFor(() => expect(wrapper.get('.product-child-row').text()).toContain('标准款'))
    vi.mocked(productManagementApi.page).mockResolvedValueOnce(pageOf([]))
    await wrapper.get('.product-management-filters input[type="search"]').setValue('missing')
    await vi.waitFor(() => expect(wrapper.text()).toContain('没有符合条件的产品'))
    expect(wrapper.find('[aria-label="列表分页"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="下一页"]').attributes('disabled')).toBeDefined()
  })

  it('enables Add SKU only after selection and removes duplicate toolbar actions', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    const add = wrapper.findAll('.sku-toolbar button').find(button => button.text() === '添加SKU')!
    expect(add.attributes('disabled')).toBeDefined()
    expect(wrapper.get('.sku-toolbar').text()).not.toContain('已选')
    expect(wrapper.get('.sku-toolbar').text()).not.toContain('批量修改')
    await wrapper.get('input[aria-label="选择父体 测试产品"]').setValue(true)
    expect(add.attributes('disabled')).toBeUndefined()
    await add.trigger('click')
    expect((wrapper.get('.batch-dialog select').element as HTMLSelectElement).value).toBe('p1')
    expect(wrapper.get('.batch-dialog').text()).toContain('测试产品')
    wrapper.unmount()
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
    expect(wrapper.findAll('thead th').map(cell => cell.text())).toEqual(['', '品名', '状态', '操作'])
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
    const items = Array.from({ length: 21 }, (_, index) => ({ ...product, id: `p${index}`, productCode: `P-${index}`, variants: [0, 1].map(child => ({ ...product.variants[0]!, id: `v${index}-${child}`, skuCode: `SKU-${index}-${child}` })) }))
    vi.mocked(productManagementApi.page)
      .mockResolvedValueOnce({ ...pageOf(items.slice(0, 20)), totalCount: 21, matchingSkuCount: 42 })
      .mockResolvedValueOnce({ ...pageOf(items.slice(20)), totalCount: 21, matchingSkuCount: 42, page: 1 })
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(20)
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(2)
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(productManagementApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.findAll('.product-parent-row')).toHaveLength(1)
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(2)
  })

  it('shows complete parent inventory while counting only matching children', async () => {
    const sibling = { ...product.variants[0]!, id: 'v2', skuCode: 'SKU-2', variantName: '另一子体' }
    const response = pageOf([{ ...product, variants: [product.variants[0]!, sibling] }])
    response.matchingVariantIds = ['v1']; response.matchingSkuCount = 1
    response.balances = [{ variantId: 'v1', quantity: 3, updatedAt: '' }, { variantId: 'v2', quantity: 7, updatedAt: '' }]
    vi.mocked(productManagementApi.page).mockResolvedValueOnce(response)
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.get('.product-parent-row .product-parent-stock').text()).toContain('10')
    await wrapper.get('.parent-toggle').trigger('click')
    expect(wrapper.findAll('.product-child-row')).toHaveLength(1)
    expect(wrapper.get('.product-child-row .product-stock-link').text()).toBe('3')
  })

  it('restores page size and filters without resetting the saved page', async () => {
    sessionStorage.setItem('jiugeng.products.view.user-1', JSON.stringify({ filters: { q: '合成' }, page: 2, size: 50, expanded: [], scrollTop: 40 }))
    vi.mocked(productManagementApi.page).mockResolvedValueOnce({ ...pageOf([product]), totalCount: 130, page: 2, size: 50 })
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(productManagementApi.page).toHaveBeenCalledTimes(1)
    expect(productManagementApi.page).toHaveBeenCalledWith(expect.objectContaining({ q: '合成', page: 2, size: 50 }))
    expect(wrapper.find('.product-child-row').exists()).toBe(false)
  })

  it('sends keyword, status and inventory filters to the paginated API', async () => {
    const draft = { ...product, status: 'DRAFT' as const }
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    const input = wrapper.get('.product-management-filters input[type="search"]')
    await input.setValue('蓝色')
    await vi.waitFor(() => expect(productManagementApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ q: '蓝色', page: 0 })))
    vi.mocked(productManagementApi.page).mockResolvedValueOnce(pageOf([draft]))
    await input.setValue('')
    const selects = wrapper.findAll('.product-management-filters select')
    await selects[0]!.setValue('DRAFT')
    await selects[1]!.setValue('UNINITIALIZED')
    await vi.waitFor(() => expect(productManagementApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ productStatus: 'DRAFT', stock: 'UNINITIALIZED', q: '' })))
    expect(wrapper.get('.product-parent-row').text()).toContain('待定')
    await wrapper.get('.product-management-filters .filter-reset-button').trigger('click')
    await flushPromises()
    expect((input.element as HTMLInputElement).value).toBe('')
    expect((selects[0]!.element as HTMLSelectElement).value).toBe('')
  })

  it('keeps manuals on SKU detail and removes customer-owned label and parent status', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.get('.product-child-row .sku-code-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.sku-detail-dialog').text()).not.toContain('商品标签管理')
    expect(wrapper.get('.sku-detail-dialog').text()).not.toContain('产品状态')
    expect(wrapper.get('.sku-detail-dialog').text()).not.toContain('父体编码')
    for (const title of ['说明书管理']) {
      await wrapper.findAll('button').find(button => button.text() === title)!.trigger('click')
      await flushPromises()
      expect(wrapper.get('.sku-documents h3').text()).toBe(title)
    }
  })

  it('exports the applied keyword filters used by the list', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()
    await wrapper.get('.product-management-filters input[type="search"]').setValue('SKU')
    await vi.waitFor(() => expect(productManagementApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'SKU' })))
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '导出')!.trigger('click')
    await wrapper.get('.batch-dialog').trigger('submit')
    await flushPromises()
    expect(domainApi.exportSkus).toHaveBeenCalledWith(expect.objectContaining({ q: 'SKU', field: 'KEYWORD', exact: false }))
  })

it('opens stock directly from its value without submitting an inventory change', async () => {
  vi.mocked(productManagementApi.page).mockReset().mockResolvedValue({ ...pageOf([product]), balances: [{ variantId: 'v1', quantity: 8, updatedAt: '2026-01-01' }] })
  vi.mocked(productManagementApi.balances).mockResolvedValue([{ variantId: 'v1', quantity: 8, updatedAt: '2026-01-01' }])
  vi.mocked(productManagementApi.stockHistory).mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
  sessionStorage.clear(); localStorage.clear()
  const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } }); await flushPromises()
  await wrapper.get('.parent-toggle').trigger('click')
  await wrapper.get('.product-stock-link').trigger('click'); await flushPromises()
  expect(wrapper.get('dialog[aria-label="库存操作与流水"]').text()).toContain('标准款')
  expect(productManagementApi.transact).not.toHaveBeenCalled()
})

  it('keeps advanced conditions visible as removable chips when collapsed', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } }); await flushPromises()
    await wrapper.get('.filter-more-button').trigger('click')
    const labels = wrapper.findAll('.product-management-filters > label')
    await labels.find(label => label.text().startsWith('产品状态'))!.get('select').setValue('ACTIVE')
    await labels.find(label => label.text().startsWith('资料缺项'))!.get('select').setValue('weight')
    await vi.waitFor(() => expect(wrapper.find('.product-filter-chips').exists()).toBe(true))
    await wrapper.get('.filter-more-button').trigger('click')
    expect(wrapper.get('.filter-count').text()).toBe('1')
    await wrapper.get('[aria-label="移除筛选：资料缺项：缺重量"]').trigger('click')
    await vi.waitFor(() => expect(productManagementApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ productStatus: 'ACTIVE', missing: '' })))
  })
  it('opens the missing-weight editor at the target child', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } }); await flushPromises()
    await wrapper.get('.parent-toggle').trigger('click')
    await wrapper.findAll('.product-missing-items button').find(button => button.text().startsWith('补充重量'))!.trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(ProductForm).props()).toMatchObject({ focusVariantId: 'v1', focusField: 'weight' })
    expect(wrapper.get('[data-product-field="weight"]').isVisible()).toBe(true)
  })

})
