import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/api/client'
import WatchStoreDetail from './WatchStoreDetail.vue'
import { monitoringApi } from '@/api/monitoring'
import type { WatchStoreDto, WatchEventDto } from '@/api/resources'
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
  it('paginates beyond twenty products and labels offer prices and unobserved ratings', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({products:[],runs:[],automaticProducts:Array.from({length:32},(_,i)=>({provider:'test',asin:`B0TEST${String(i).padStart(4,'0')}`,title:`Synthetic ${i}`,firstAt:'2026-10-26T12:00:00Z',lastAt:'2026-10-26T12:00:00Z',missingCount:0,price:12.99,priceType:'OFFER_FROM'}))})
    const wrapper=await open(true)
    expect(wrapper.text()).toContain('共 32 条');expect(wrapper.findAll('tbody tr')).toHaveLength(20)
    expect(wrapper.text()).toContain('US$ 12.99 起');expect(wrapper.text()).toContain('暂无公开评分')
    await wrapper.findAll('button').find(b=>b.attributes('aria-label')==='下一页')!.trigger('click')
    expect(wrapper.findAll('tbody tr')).toHaveLength(12);expect(wrapper.text()).toContain('Synthetic 31');wrapper.unmount()
  })
  it('renders only today event products with the same product table, including empty days', async () => {
    const wrapper = await open(true)
    await wrapper.setProps({newProducts:[{asin:'B000000002',title:'Today item',detectedAt:'2026-10-27T12:00:00Z'} as WatchEventDto]})
    expect(wrapper.text()).toContain('今日上新')
    expect(wrapper.text()).toContain('Today item')
    expect(wrapper.text()).not.toContain('Test product')
    expect(wrapper.find('table.mw-table').exists()).toBe(true)
    await wrapper.setProps({newProducts:[]})
    expect(wrapper.findAll('tbody input[type=checkbox]')).toHaveLength(0)
    wrapper.unmount()
  })
  it('clears selected products when filters or tabs change', async () => {
    const wrapper = await open(true)
    const batch = () => wrapper.findAll('button').find(b => b.text() === '批量添加产品监控')!
    await wrapper.get('input[aria-label="选择 B000000001"]').setValue(true)
    expect(batch().attributes('disabled')).toBeUndefined()
    await wrapper.get('input[aria-label="搜索标题或ASIN"]').setValue('not-matched')
    expect(batch().attributes('disabled')).toBeDefined()
    await wrapper.get('input[aria-label="搜索标题或ASIN"]').setValue('')
    await wrapper.get('input[aria-label="选择 B000000001"]').setValue(true)
    await wrapper.findAll('button').find(b => b.text() === '疑似下架产品')!.trigger('click')
    expect(batch().attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
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
