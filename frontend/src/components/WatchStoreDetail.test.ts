import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import WatchStoreDetail from './WatchStoreDetail.vue'
import { monitoringApi } from '@/api/monitoring'
import type { WatchStoreDto } from '@/api/resources'
vi.mock('@/api/client', () => ({ apiRequest: vi.fn(async () => ({ products: [], runs: [], automaticProducts: [{ asin: 'B000000001', title: 'Test product', firstAt: '2026-10-26T12:00:00Z', lastAt: '2026-10-26T12:00:00Z', missingCount: 0 }] })) }))
vi.mock('@/api/monitoring', () => ({ monitoringApi: { create: vi.fn(async () => [{ asin: 'B000000001', status: 'CREATED' }]) } }))
const store = { id: 'test-store', storeName: 'Test store', status: 'ACTIVE' } as WatchStoreDto
async function open(writable: boolean) {
  HTMLDialogElement.prototype.showModal = vi.fn()
  const wrapper = mount(WatchStoreDetail, { props: { store: undefined, writable }, global: { stubs: { RouterLink: RouterLinkStub } } })
  await wrapper.setProps({ store }); await flushPromises()
  return wrapper
}
describe('store product monitoring', () => {
  it('adds the chosen observed ASIN and keeps absent price unknown', async () => {
    const wrapper = await open(true)
    expect(wrapper.text()).not.toContain('US$ 0')
    await wrapper.findAll('button').find(b => b.text() === '添加产品监控')!.trigger('click')
    await flushPromises()
    expect(monitoringApi.create).toHaveBeenCalledWith(expect.objectContaining({ asins: ['B000000001'] }))
    expect(wrapper.text()).toContain('已添加 1 个')
    wrapper.unmount()
  })
  it('disables monitor mutations for a read-only user', async () => {
    const wrapper = await open(false)
    const buttons = wrapper.findAll('button').filter(b => b.text().includes('添加产品监控'))
    expect(buttons.length).toBeGreaterThan(0)
    expect(buttons.every(b => b.attributes('disabled') !== undefined)).toBe(true)
    wrapper.unmount()
  })
})
