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
  it('shows overview, previews the parent image and exposes return navigation', async () => {
    const { wrapper } = await setup()
    expect(wrapper.text()).toContain('SKU 概览')
    expect(wrapper.text()).toContain('合成父体')
    expect(wrapper.get('a[href="/products"]').text()).toBe('返回产品管理')
    await wrapper.get('[aria-label="查看父体图片"]').trigger('click')
    expect(wrapper.get('[aria-label="产品图片预览"]').text()).toContain('合成父体')
    await wrapper.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[aria-label="产品图片预览"]').exists()).toBe(false)
  })
  it('shows structured dimensions, supplier names and customs data', async () => {
    const { wrapper } = await setup('/products/p1/variants')
    expect(wrapper.text()).toContain('10 × 20 × 30 cm')
    expect(wrapper.text()).toContain('合成供应商')
    expect(wrapper.text()).toContain('测试申报品名')
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
  it('loads actual history and offers retry after failure', async () => {
    vi.mocked(domainApi.productHistory).mockRejectedValueOnce(new Error('记录暂不可用')).mockResolvedValueOnce([{ id: 'h1', action: 'UPDATE', actorLoginName: 'tester', detail: '数量 3 → 5', occurredAt: '2026-01-01' }])
    const { wrapper } = await setup('/products/p1/changes')
    expect(wrapper.text()).toContain('记录暂不可用')
    await wrapper.findAll('button').find(b => b.text() === '重试')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('数量 3 → 5')
  })
  it('does not fetch protected references or show edit actions without permission', async () => {
    vi.mocked(session.canAccess).mockImplementation(caps => !caps?.some(c => ['products.write', 'suppliers.read', 'materials.read'].includes(c)))
    const { wrapper } = await setup('/products/p1/variants')
    expect(wrapper.text()).toContain('无查看权限')
    expect(domainApi.supplier).not.toHaveBeenCalled()
    expect(domainApi.productMaterialRelations).not.toHaveBeenCalled()
    expect(wrapper.findAll('button').some(b => b.text() === '编辑')).toBe(false)
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
  it('mounts SKU documents only in the documents tab', async () => {
    const { wrapper, router } = await setup()
    expect(wrapper.find('sku-documents-panel-stub').exists()).toBe(false)
    await router.push('/products/p1/documents'); await flushPromises()
    expect(wrapper.findAll('sku-documents-panel-stub').map(e => e.attributes('kind'))).toEqual(['MANUAL', 'LABEL'])
  })
})
