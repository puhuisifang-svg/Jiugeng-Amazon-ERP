import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => true } }))
vi.mock('@/api/domain', () => ({
  domainApi: { products: vi.fn(async () => ({ items: [
    { id: 'p1', productCode: 'P-1', productName: '测试产品', productType: '家居', status: 'ACTIVE', skuCount: 1, variants: [] },
  ], mockData: false })) },
}))

import ProductsView from './ProductsView.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }

describe('ProductsView', () => {
  it('orders search, create action and product list consistently', async () => {
    const wrapper = mount(ProductsView, { global: { stubs: { RouterLink } } })
    await flushPromises()

    const sections = wrapper.findAll('.filter-panel, .material-toolbar, .card-list')
    expect(sections.map(item => item.classes())).toEqual([
      expect.arrayContaining(['filter-panel']),
      expect.arrayContaining(['material-toolbar']),
      expect.arrayContaining(['card-list']),
    ])
    expect(wrapper.get('a[href="/products/new"]').text()).toContain('新增产品')
  })
})
