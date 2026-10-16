import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/config/runtime', () => ({ runtimeConfig: { apiBaseUrl: 'http://api.test/api', authMode: 'password' } }))
vi.mock('vue-router', () => ({ RouterLink: { props: ['to'], template: '<a><slot /></a>' } }))

describe('DashboardView in formal mode', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })

  it('loads three worklists, shows full totals, SET quantities as 套, and truncated guidance', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
    const base = { totalCount: 0, truncated: false, businessDate: '2026-10-15', generatedAt: '2026-10-15T01:00:00Z', items: [] }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ ...base, totalCount: 11, truncated: true, items: [{ id: 'today-1', shipDate: '2026-10-15', productId: 'p1', productCode: 'P-1', productName: '今日产品', quantity: 1200, quantityUnit: 'SET', boxCount: 2, latestShipAt: '2099-10-15T10:00:00Z', status: 'ACTIVE', version: 0, createdAt: '', updatedAt: '' }] }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => base })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => base })
    vi.stubGlobal('fetch', fetchMock)
    const { default: DashboardView } = await import('./DashboardView.vue')
    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledTimes(6)
    expect(fetchMock.mock.calls[3]?.[0]).toContain('/v1/products/p1')
    expect(wrapper.text()).toContain('今日发货 11')
    expect(wrapper.text()).toContain('1,200 套')
    expect(wrapper.text()).toContain('仅展示前 10 条')
    expect(wrapper.text()).toContain('当日安排，不代表已确认出库')
    expect(wrapper.findAll('th').map(th => th.text())).toEqual(['', '产品图片', '产品名称 / SKU', '发货数量', '发货箱数', '备注', '商品标签', '外箱标签', '说明书下载'])
    wrapper.unmount()
  })
})
