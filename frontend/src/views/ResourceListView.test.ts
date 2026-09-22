import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ResourceListView from './ResourceListView.vue'

describe('ResourceListView', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders a clearly labelled synthetic resource list', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        mockData: true,
        source: 'SYNTHETIC',
        items: [{ id: 'p1', productCode: 'PRD-MOCK-001', name: '模拟收纳盒', status: 'ACTIVE', skuCount: 2 }],
      }),
    }))
    const wrapper = mount(ResourceListView, {
      props: {
        endpoint: '/products',
        emptyText: '暂无模拟产品',
        columns: [{ key: 'productCode', label: '产品编码' }, { key: 'name', label: '产品名称' }],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('SYNTHETIC')
    expect(wrapper.text()).toContain('PRD-MOCK-001')
    expect(wrapper.text()).toContain('模拟收纳盒')
  })

  it('shows a useful backend error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const wrapper = mount(ResourceListView, { props: { endpoint: '/materials', emptyText: 'empty', columns: [] } })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('mock profile')
  })

  it('fails closed when the backend response is not explicitly synthetic', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ mockData: false, source: 'UNKNOWN', items: [] }),
    }))
    const wrapper = mount(ResourceListView, { props: { endpoint: '/products', emptyText: 'empty', columns: [] } })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('mock profile')
  })
})
