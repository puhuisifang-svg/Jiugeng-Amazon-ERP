import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { costsApi, type CostRow, type CostSnapshot } from '@/api/costs'
import { domainApi } from '@/api/domain'
import ProductCostsView from './ProductCostsView.vue'
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/costs', () => ({ costsApi: { list: vi.fn(), versions: vi.fn(), history: vi.fn(), initial: vi.fn(), operations: vi.fn(), preview: vi.fn(), save: vi.fn() } }))
vi.mock('@/api/domain', () => ({ domainApi: { materials: vi.fn() } }))
const access = vi.hoisted(() => ({ write: true }))
vi.mock('@/state/session', () => ({ session: { canAccess: (caps: string[]) => caps.every(c => c === 'costs.read' || access.write) } }))
const row: CostRow = { sku: { id: 's1', productId: 'p1', name: '合成四方罩', code: 'SYN-SKU', productName: '合成产品', imageId: null }, current: null, draft: null, stale: false }
const input = { unit: '件', baseQuantity: 1, materials: [], labor: [], noMaterialReason: '', noLaborReason: '' }
beforeEach(() => { vi.clearAllMocks(); access.write = true; vi.mocked(costsApi.list).mockResolvedValue({ items: [row], totalCount: 1 }); vi.mocked(costsApi.history).mockResolvedValue([]); vi.mocked(costsApi.versions).mockResolvedValue([]); vi.mocked(costsApi.initial).mockResolvedValue(input); vi.mocked(costsApi.operations).mockResolvedValue([]); vi.mocked(domainApi.materials).mockResolvedValue({ items: [], mockData: false }) })
function setup() { return mount(ProductCostsView, { global: { stubs: { TableViewport: { template: '<div><slot /></div>' }, ListPagination: true, AuthenticatedCatalogImage: true } } }) }
it('keeps draft input on backdrop and confirms explicit discard', async () => {
  const w = setup(); await flushPromises(); await w.findAll('button').find(b => b.text() === '详情 / 维护')!.trigger('click'); await flushPromises()
  await w.findAll('button').find(b => b.text() === '新建成本草稿')!.trigger('click'); await flushPromises()
  const label = w.findAll('label').find(l => l.text().startsWith('成品单位'))!; await label.get('input').setValue('套')
  await w.get('[aria-label="产品成本详情"]').trigger('click'); expect(label.get('input').element).toHaveProperty('value', '套')
  await w.findAll('button').find(b => b.text() === '关闭')!.trigger('click'); expect(w.text()).toContain('放弃未保存修改？')
  await w.findAll('button').find(b => b.text() === '取消')!.trigger('click'); expect(label.get('input').element).toHaveProperty('value', '套'); w.unmount()
})
it('requires recalculation after edits and preserves draft after failed save', async () => {
  vi.mocked(costsApi.preview).mockResolvedValue({ name: '合成产品', sku: 'SYN-SKU', materials: [], labor: [], input, materialTotal: '0', laborTotal: '0', total: '0', sourceHash: 'hash', errors: ['无物料须填写原因'] } satisfies CostSnapshot)
  vi.mocked(costsApi.save).mockRejectedValue(new Error('来源已变化'))
  const w = setup(); await flushPromises(); const button = (name: string) => w.findAll('button').find(b => b.text() === name)!
  await button('详情 / 维护').trigger('click'); await flushPromises(); await button('新建成本草稿').trigger('click'); await flushPromises()
  await w.findAll('label').find(l => l.text().startsWith('变更原因'))!.get('input').setValue('首次核算')
  expect(button('保存草稿').attributes('disabled')).toBeDefined(); await button('重新核算').trigger('click'); await flushPromises(); expect(button('保存草稿').attributes('disabled')).toBeUndefined()
  await button('保存草稿').trigger('click'); await flushPromises(); expect(w.text()).toContain('来源已变化'); expect(w.find('[aria-label="产品成本详情"]').exists()).toBe(true)
  await w.findAll('label').find(l => l.text().startsWith('成品单位'))!.get('input').setValue('套'); expect(button('保存草稿').attributes('disabled')).toBeDefined(); w.unmount()
})
it('does not expose write actions to a read-only account', async () => { access.write = false; const w = setup(); await flushPromises(); await w.findAll('button').find(b => b.text() === '详情 / 维护')!.trigger('click'); await flushPromises(); expect(w.text()).not.toContain('新建成本草稿'); expect(w.text()).not.toContain('工序管理'); w.unmount() })
