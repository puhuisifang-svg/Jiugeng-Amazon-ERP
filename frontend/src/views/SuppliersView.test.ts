import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createSupplier } = vi.hoisted(() => ({ createSupplier: vi.fn() }))

vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { uploadImage: vi.fn(), imageBlob: vi.fn() } }))
vi.mock('@/api/domain', () => ({
  domainApi: {
    suppliers: vi.fn(async () => ({ items: [], totalCount: 0, page: 0, size: 100 })),
    createSupplier,
    updateSupplier: vi.fn(),
    disableSupplier: vi.fn(),
  },
}))

import SuppliersView from './SuppliersView.vue'

const AuthenticatedCatalogImage = { props: ['imageId', 'alt'], template: '<span>{{ alt }}</span>' }

describe('SuppliersView', () => {
  beforeEach(() => {
    createSupplier.mockReset().mockResolvedValue({ id: 's1', name: '包装供应商', status: 'ACTIVE', version: 0 })
  })

  it('submits a plain supplier request and reports success', async () => {
    const wrapper = mount(SuppliersView, { global: { stubs: { AuthenticatedCatalogImage } } })
    await flushPromises()

    const form = wrapper.findAll('form').find(item => item.text().includes('新增供应商'))!
    await form.get('input[required]').setValue('  包装供应商  ')
    await form.findAll('input')[1]!.setValue('张三')
    await form.trigger('submit')
    await flushPromises()

    expect(createSupplier).toHaveBeenCalledWith({ name: '包装供应商', contactPerson: '张三', status: 'ACTIVE' })
    expect(wrapper.get('[role="status"]').text()).toContain('供应商已创建')
    expect(form.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })
})
