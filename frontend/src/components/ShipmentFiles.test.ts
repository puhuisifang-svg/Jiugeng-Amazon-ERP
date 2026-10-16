import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShipmentFiles from './ShipmentFiles.vue'
import ShipmentManuals from './ShipmentManuals.vue'
import ShipmentProductImage from './ShipmentProductImage.vue'
import { dashboardApi } from '@/api/dashboard'
import { catalogApi } from '@/api/catalog'
import type { ProductDetailDto } from '@/api/domain'
vi.mock('@/api/dashboard', () => ({ dashboardApi: { documents: vi.fn(), uploadDocument: vi.fn(), downloadDocument: vi.fn() } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { documents: vi.fn(), downloadDocument: vi.fn() } }))
const file = { id: 'd1', kind: 'LABEL' as const, fileName: '标签.pdf', byteSize: 10, uploadedAt: '' }
describe('shipment files and previews', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(dashboardApi.documents).mockResolvedValue([]) })
  it('uploads labels and downloads from their shipment', async () => {
    vi.mocked(dashboardApi.uploadDocument).mockResolvedValue(file)
    const wrapper = mount(ShipmentFiles, { props: { shipmentId: 't1', scope: 'today-shipments', kind: 'LABEL', editable: true } })
    await flushPromises()
    const input = wrapper.get('input'); const upload = new File(['%PDF-test'], '标签.pdf', { type: 'application/pdf' })
    Object.defineProperty(input.element, 'files', { value: [upload], configurable: true })
    await input.trigger('change'); await flushPromises()
    expect(dashboardApi.uploadDocument).toHaveBeenCalledWith('t1', 'LABEL', upload)
    expect(wrapper.text()).toContain('标签上传成功')
    await wrapper.get('.shipment-file-link').trigger('click'); await flushPromises()
    expect(dashboardApi.downloadDocument).toHaveBeenCalledWith('today-shipments', 't1', file)
    wrapper.unmount()
  })
  it('reports failed reads and prevents empty uploads', async () => {
    vi.mocked(dashboardApi.documents).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(ShipmentFiles, { props: { shipmentId: 't1', scope: 'today-shipments', kind: 'CARTON_LABEL', editable: true } })
    await flushPromises(); expect(wrapper.get('[role="alert"]').text()).toContain('文件加载失败')
    const input = wrapper.get('input'); Object.defineProperty(input.element, 'files', { value: [new File([], 'empty.pdf')] })
    await input.trigger('change'); await flushPromises()
    expect(dashboardApi.uploadDocument).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('文件不能为空')
    wrapper.unmount()
  })
  it('reads product manuals with SKU labels and uses original download API', async () => {
    const manual = { ...file, kind: 'MANUAL' as const, fileName: '说明书.pdf' }
    vi.mocked(catalogApi.documents).mockResolvedValue([manual])
    const product = { id: 'p1', variants: [{ id: 'v1', skuCode: 'SKU-1' }] } as ProductDetailDto
    const wrapper = mount(ShipmentManuals, { props: { product } }); await flushPromises()
    expect(catalogApi.documents).toHaveBeenCalledWith('p1', 'v1', 'MANUAL')
    expect(wrapper.text()).toContain('SKU-1 · 说明书.pdf')
    await wrapper.get('button').trigger('click'); await flushPromises()
    expect(catalogApi.downloadDocument).toHaveBeenCalledWith('p1', 'v1', manual)
    wrapper.unmount()
  })
  it('opens and closes product image preview', async () => {
    const product = { id: 'p1', variants: [{ imageUrl: 'https://example.com/test.png' }] } as ProductDetailDto
    const wrapper = mount(ShipmentProductImage, { props: { productId: 'p1', product, alt: '测试产品' }, global: { stubs: { Teleport: true } } })
    await wrapper.get('.image-button').trigger('click')
    expect(wrapper.get('[role="dialog"] img').attributes('src')).toBe('https://example.com/test.png')
    await wrapper.get('[aria-label="关闭图片预览"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
