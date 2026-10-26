import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { monitoringApi, type MonitorTarget } from '@/api/monitoring'
import ProductMonitoringView from './ProductMonitoringView.vue'

vi.mock('@/api/monitoring', () => ({ monitoringApi: { list: vi.fn(), preferences: vi.fn(), create: vi.fn(), detail: vi.fn(), batch: vi.fn(), history: vi.fn(), snapshot: vi.fn(), follow: vi.fn(), columns: vi.fn() } }))
const row: MonitorTarget = { id: 'one', marketplace: 'US', asin: 'B0TEST0001', enabled: true, ownerId: null, ownerName: null, tags: [], remark: '', version: 0, fields: {}, source: null, observedAt: null, lastStatus: 'PENDING', lastReason: null, lastAttemptAt: null, createdAt: '2026-01-01T00:00:00Z', followed: false }
const mounted: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.resetAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  vi.mocked(monitoringApi.list).mockResolvedValue({ items: [row], total: 40, activeCount: 1, limit: 1000 })
  vi.mocked(monitoringApi.preferences).mockResolvedValue({ columns: ['price', 'source'], followed: [] })
  vi.mocked(monitoringApi.history).mockResolvedValue({ items: [], total: 0, activeCount: 0, limit: 0 })
  vi.mocked(monitoringApi.detail).mockResolvedValue({ target: row, snapshots: [] })
})
afterEach(() => mounted.splice(0).forEach(w => w.unmount()))
async function setup(path = '/product-monitoring') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/product-monitoring/:id?', component: ProductMonitoringView }] })
  await router.push(path); await router.isReady()
  const wrapper = mount(ProductMonitoringView, { global: { plugins: [router] } }); mounted.push(wrapper); await flushPromises(); return wrapper
}
describe('product monitoring workflow', () => {
  it('shows honest source status and clears selection when paging', async () => {
    const w = await setup()
    expect(w.find('[title="亚马逊前台观测 · 每日更新"]').exists()).toBe(true)
    expect(w.text()).toContain('待首次观测')
    await w.get('[aria-label="选择 B0TEST0001"]').setValue(true)
    expect(w.text()).toContain('已选 1 条')
    await w.get('[aria-label="下一页"]').trigger('click'); await flushPromises()
    expect(w.text()).toContain('已选 0 条')
    expect(monitoringApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
  })
  it('retains the same request key after an uncertain create failure', async () => {
    const w = await setup()
    await w.findAll('button').find(b => b.text().includes('添加监控'))!.trigger('click')
    await w.get('dialog textarea').setValue('b0test0001')
    vi.mocked(monitoringApi.create).mockRejectedValueOnce(new Error('网络中断'))
    await w.get('dialog form').trigger('submit'); await flushPromises()
    expect(w.get('dialog').text()).toContain('网络中断')
    const key = vi.mocked(monitoringApi.create).mock.calls[0]![0].requestId
    vi.mocked(monitoringApi.create).mockResolvedValue([{ asin: 'B0TEST0001', id: 'one', status: 'CREATED', message: '已添加' }])
    await w.get('dialog form').trigger('submit'); await flushPromises()
    expect(vi.mocked(monitoringApi.create).mock.calls[1]![0].requestId).toBe(key)
    expect(w.get('dialog').text()).toContain('已添加')
  })
  it('reports batch conflicts without pretending all rows succeeded', async () => {
    const w = await setup()
    await w.get('[aria-label="选择 B0TEST0001"]').setValue(true)
    vi.mocked(monitoringApi.batch).mockResolvedValue([{ asin: row.asin, id: row.id, status: 'CONFLICT', message: '记录已更新' }])
    await w.findAll('button').find(b => b.text() === '应用')!.trigger('click'); await flushPromises()
    expect(w.text()).toContain('已更新 0 条')
    expect(w.text()).toContain('记录已更新')
  })
  it('does not save column changes on cancel', async () => {
    const w = await setup()
    await w.findAll('button').find(b => b.text() === '自定义列')!.trigger('click')
    await w.findAll('dialog button').find(b => b.text() === '恢复默认')!.trigger('click')
    await w.findAll('dialog button').find(b => b.text() === '取消')!.trigger('click')
    expect(monitoringApi.columns).not.toHaveBeenCalled()
    expect(w.findAll('th').map(t => t.text())).not.toContain('品牌')
  })
  it('disables observation for a paused target and keeps history visible', async () => {
    vi.mocked(monitoringApi.detail).mockResolvedValue({ target: { ...row, enabled: false }, snapshots: [] })
    const w = await setup('/product-monitoring/one')
    expect(w.findAll('button').find(b => b.text() === '人工补录')!.attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('商品变化与历史快照')
    expect(w.text()).not.toContain('操作日志')
  })
  it('keeps explicit unknown fields in a submitted observation', async () => {
    const w = await setup('/product-monitoring/one')
    await w.findAll('button').find(b => b.text() === '人工补录')!.trigger('click')
    await w.get('#field-title').setValue('测试商品')
    vi.mocked(monitoringApi.snapshot).mockRejectedValue(new Error('校验失败'))
    await w.get('dialog form').trigger('submit'); await flushPromises()
    expect(monitoringApi.snapshot).toHaveBeenCalledWith('one', expect.objectContaining({ fields: { title: '测试商品', brand: null, imageUrl: null, bullets: null, price: null, currency: 'USD', availability: null } }))
    expect(w.get('dialog').text()).toContain('校验失败')
  })
})
