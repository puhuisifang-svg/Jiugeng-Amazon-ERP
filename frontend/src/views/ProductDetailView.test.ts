import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { domainApi, type ProductDetailDto } from '@/api/domain'
import { blankCustoms } from '@/api/customs'
import { session } from '@/state/session'
import ProductForm from '@/components/ProductForm.vue'
import ProductDetailView from './ProductDetailView.vue'

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: vi.fn(() => true) } }))
vi.mock('@/api/domain', () => ({ domainApi: { product: vi.fn(), productHistory: vi.fn(), updateProduct: vi.fn(), disableProduct: vi.fn(), supplier: vi.fn(), productMaterialRelations: vi.fn() } }))
const product: ProductDetailDto = { id: 'p1', productCode: 'P-TEST', productName: '合成父体', nameZh: '合成父体', imageAssetId: 'parent-image', status: 'ACTIVE', version: 2, skuCount: 1, supplierDetailsAvailable: true, variants: [{ id: 'v1', skuCode: 'SKU-TEST', variantName: '测试子体', status: 'ACTIVE', itemLengthCm: 10, itemWidthCm: 20, itemHeightCm: 30, customs: { ...blankCustoms(), nameZh: '测试申报品名' }, attributes: [], suppliers: [{ supplierId: 's1', primary: true }], materials: [] }] }
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(session.canAccess).mockReturnValue(true)
  vi.mocked(domainApi.product).mockReset().mockResolvedValue(structuredClone(product))
  vi.mocked(domainApi.productHistory).mockReset().mockResolvedValue([])
  vi.mocked(domainApi.productMaterialRelations).mockResolvedValue([])
  vi.mocked(domainApi.supplier).mockResolvedValue({ id: 's1', name: '合成供应商', status: 'ACTIVE', version: 0 })
  vi.mocked(domainApi.updateProduct).mockReset().mockResolvedValue(product)
  vi.mocked(domainApi.disableProduct).mockResolvedValue({ ...product, status: 'DISABLED' })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))
async function setup(path = '/products/p1') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/products/:productId/:section?', component: ProductDetailView }, { path: '/products', component: { template: '<p>产品管理</p>' } }] })
  await router.push(path); await router.isReady()
  const wrapper = mount(ProductDetailView, { global: { plugins: [router], stubs: { ProductForm: true, AuthenticatedCatalogImage: true, SkuDocumentsPanel: true, TableViewport: { template: '<div><slot /></div>' } } } })
  wrappers.push(wrapper); await flushPromises()
  return { wrapper, router }
}
describe('parent product details', () => {
  it('restores real history with semantic operations, empty states and retry', async () => {
    vi.mocked(domainApi.productHistory).mockRejectedValueOnce(new Error('加载失败')).mockResolvedValueOnce([
      { id: 'h1', action: 'PRODUCT_UPDATE', actorLoginName: 'tester', detail: '修改描述', occurredAt: '2026-01-01' },
      { id: 'h2', action: 'FUTURE_ACTION', actorLoginName: 'tester', occurredAt: '2026-01-02' },
    ])
    const { wrapper, router } = await setup('/products/p1/changes')
    expect(wrapper.text()).toContain('加载失败')
    await wrapper.findAll('button').find(b => b.text() === '重试')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('修改产品资料')
    expect(wrapper.text()).toContain('其他操作')
    expect(wrapper.get('td[title="FUTURE_ACTION"]').text()).toBe('其他操作')
    expect(wrapper.text()).not.toContain('PRODUCT_UPDATE')
    await router.push('/products/p1/overview'); await flushPromises()
    expect(wrapper.text()).toContain('SKU 概览')
  })
  it('uses the shared edit dialog without replacing the detail page', async () => {
    const { wrapper } = await setup()
    await wrapper.findAll('button').find(b => b.text() === '编辑')!.trigger('click')
    expect(wrapper.get('[role="dialog"][aria-label="编辑产品"]').find('.product-create-dialog .product-create-form').exists()).toBe(true)
    expect(wrapper.text()).toContain('SKU 概览')
    await wrapper.get('[aria-label="关闭编辑"]').trigger('click')
    expect(wrapper.findComponent(ProductForm).exists()).toBe(false)
  })
  it('opens the exact selected SKU using the existing dialog and closes back to overview', async () => {
    vi.mocked(domainApi.product).mockResolvedValue({ ...product, variants: [...product.variants, { ...product.variants[0]!, id: 'v2', skuCode: 'SECOND-SKU', variantName: '第二子体' }] })
    const { wrapper } = await setup()
    await wrapper.findAll('.sku-overview-link')[1]!.trigger('click'); await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('SECOND-SKU')
    expect(wrapper.get('[role="dialog"]').text()).toContain('第二子体')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('查看产品完整资料')
    await wrapper.get('[aria-label="关闭"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('SKU 概览')
  })
  it('opens the selected SKU editor without switching to another child', async () => {
    const { wrapper } = await setup()
    await wrapper.get('.sku-overview-link').trigger('click'); await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '编辑 SKU')!.trigger('click')
    expect(wrapper.getComponent(ProductForm).props('focusVariantId')).toBe('v1')
  })
  it.each(['variants', 'materials', 'documents'])('shows only basics for legacy section %s without fetching removed content', async section => {
    const { wrapper } = await setup('/products/p1/' + section)
    expect(wrapper.text()).toContain('SKU 概览')
    expect(wrapper.findAll('.detail-tabs a').map(a => a.text())).toEqual(['基础信息', '变更记录'])
    expect(domainApi.productHistory).not.toHaveBeenCalled()
    expect(domainApi.supplier).not.toHaveBeenCalled()
    expect(domainApi.productMaterialRelations).not.toHaveBeenCalled()
  })
  it('does not offer parent or SKU editing without write permission', async () => {
    vi.mocked(session.canAccess).mockReturnValue(false)
    const { wrapper } = await setup()
    expect(wrapper.findAll('button').some(b => b.text() === '编辑')).toBe(false)
    await wrapper.get('.sku-overview-link').trigger('click'); await flushPromises()
    expect(wrapper.findAll('button').some(b => b.text() === '编辑 SKU')).toBe(false)
  })
  it('shows only parent basics and overview without redundant navigation', async () => {
    const { wrapper } = await setup()
    expect(wrapper.text()).toContain('SKU 概览')
    expect(wrapper.text()).toContain('合成父体')
    expect(wrapper.text()).not.toContain('返回产品管理')
    expect(wrapper.findAll('button').some(b => b.text() === '刷新')).toBe(false)
    expect(wrapper.findAll('.detail-tabs a').map(a => a.text())).toEqual(['基础信息', '变更记录'])
    await wrapper.get('[aria-label="查看父体图片"]').trigger('click')
    expect(wrapper.get('[aria-label="产品图片预览"]').text()).toContain('合成父体')
    await wrapper.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[aria-label="产品图片预览"]').exists()).toBe(false)
  })
  it('preserves parent image and customs during edit, supports cancel and keeps failed edits open', async () => {
    const { wrapper } = await setup()
    await wrapper.findAll('button').find(b => b.text() === '编辑')!.trigger('click')
    const form = wrapper.getComponent(ProductForm)
    const input = form.props('initial')!
    expect(input.imageAssetId).toBe('parent-image')
    expect(input.variants[0]!.customs?.nameZh).toBe('测试申报品名')
    vi.mocked(domainApi.updateProduct).mockRejectedValueOnce(new Error('版本冲突，请刷新'))
    form.vm.$emit('submit', input); await flushPromises()
    expect(wrapper.text()).toContain('版本冲突')
    expect(wrapper.findComponent(ProductForm).exists()).toBe(true)
    form.vm.$emit('cancel'); await flushPromises()
    expect(wrapper.findComponent(ProductForm).exists()).toBe(false)
  })
  it('reloads for another parent and does not show stale data on failure', async () => {
    const { wrapper, router } = await setup()
    vi.mocked(domainApi.product).mockRejectedValueOnce(new Error('产品不存在'))
    await router.push('/products/missing'); await flushPromises()
    expect(wrapper.text()).toContain('产品不存在')
    expect(wrapper.text()).not.toContain('合成父体')
  })
  it('uses a styled confirmation before disabling', async () => {
    const { wrapper } = await setup()
    await wrapper.findAll('button').find(b => b.text() === '停用')!.trigger('click')
    expect(domainApi.disableProduct).not.toHaveBeenCalled()
    expect(wrapper.get('[role="dialog"]').text()).toContain('历史 SKU')
    await wrapper.findAll('button').find(b => b.text() === '确认停用')!.trigger('click'); await flushPromises()
    expect(domainApi.disableProduct).toHaveBeenCalledWith('p1')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
