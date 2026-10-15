import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createSupplier, suppliers } = vi.hoisted(() => ({ createSupplier: vi.fn(), suppliers: vi.fn() }))

vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    suppliers,
    createSupplier,
    updateSupplier: vi.fn(),
    disableSupplier: vi.fn(),
  },
}))

import SuppliersView from './SuppliersView.vue'

const AuthenticatedCatalogImage = { props: ['imageId', 'alt'], template: '<span>{{ alt }}</span>' }

describe('SuppliersView', () => {
  beforeEach(() => {
    suppliers.mockReset().mockResolvedValue({ items: [], totalCount: 0, page: 0, size: 100 })
    createSupplier.mockReset().mockResolvedValue({ id: 's1', name: '包装供应商', status: 'ACTIVE', version: 0 })
  })

  it('submits a plain supplier request and reports success', async () => {
    const wrapper = mount(SuppliersView, { global: { stubs: { AuthenticatedCatalogImage } } })
    await flushPromises()

    expect(wrapper.findAll('form').some(item => item.text().includes('新增供应商'))).toBe(false)
    await wrapper.get('.material-toolbar button').trigger('click')
    const form = wrapper.findAll('form').find(item => item.text().includes('新增供应商'))!
    expect(wrapper.get('[role="dialog"]').attributes('aria-labelledby')).toBe('supplier-dialog-title')
    expect(form.get('select.status-select').element).toBeInstanceOf(HTMLSelectElement)
    await form.get('input[required]').setValue('  包装供应商  ')
    await form.findAll('input')[1]!.setValue('张三')
    await form.get('input[autocomplete="off"]').setValue('packing_supplier')
    await form.get('input[placeholder="https://example.com/wechat-qr.png"]').setValue('https://images.example.com/packing-qr.png')
    await form.trigger('submit')
    await flushPromises()

    expect(createSupplier).toHaveBeenCalledWith({
      name: '包装供应商', contactPerson: '张三', wechatId: 'packing_supplier',
      qrImageUrl: 'https://images.example.com/packing-qr.png', status: 'ACTIVE',
    })
    expect(wrapper.get('[role="status"]').text()).toContain('供应商已创建')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('uses a consistent responsive canvas for URL QR previews', async () => {
    suppliers.mockResolvedValue({ items: [{ id: 's1', name: '二维码供应商', qrImageUrl: 'https://images.example.com/qr.png', status: 'ACTIVE', version: 0 }], totalCount: 1, page: 0, size: 100 })
    const wrapper = mount(SuppliersView, { global: { stubs: { AuthenticatedCatalogImage } } })
    await flushPromises()

    await wrapper.get('button.image-button').trigger('click')
    const preview = wrapper.get('.image-dialog .supplier-qr-preview')
    expect(preview.attributes('src')).toBe('https://images.example.com/qr.png')
  })
})
