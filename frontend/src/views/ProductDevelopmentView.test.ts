import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { developmentApi, type DevelopmentItem } from '@/api/development'
import { domainApi } from '@/api/domain'
import { session } from '@/state/session'
import { ApiError } from '@/api/client'
import ProductDevelopmentView from './ProductDevelopmentView.vue'

const config = vi.hoisted(() => ({ authMode: 'password', apiBaseUrl: '/api' }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: config }))
vi.mock('@/api/development', () => ({ developmentApi: { create: vi.fn(), list: vi.fn(), detail: vi.fn(), update: vi.fn(), transition: vi.fn() } }))
vi.mock('@/api/domain', () => ({ domainApi: { products: vi.fn() } }))
let item: DevelopmentItem
let wrapper: VueWrapper
beforeEach(() => {
  vi.clearAllMocks(); config.authMode = 'password'
  vi.spyOn(session, 'canAccess').mockReturnValue(true)
  item = {
    id: 'c1', number: 'ND-B0DEV33001', status: 'DRAFT', version: 0, sourceType: 'CANDIDATE', voidReason: null, createdAt: '', updatedAt: '',
    source: { candidateId: 'c1', asin: 'B0DEV33001', title: '已通过露营灯', category: null, score: 82, referencePrice: 20, sourceUrl: 'https://www.amazon.com/dp/B0DEV33001', selectionReason: '需求稳定' },
    fields: { name: '开发露营灯', developer: null, buyer: null, stars: 0, startDate: null, endDate: null, launchDate: null, productName: '露营灯', brand: null, category: null, plannedSku: null, material: null, purchaseCost: null, weight: null, length: null, width: null, height: null, supplyNotes: null, sampleNotes: null, researchNotes: null, remark: null, linkedProductId: null },
  }
  vi.mocked(developmentApi.list).mockImplementation(async () => ({ items: [item], total: 21, page: 0, size: 20 }))
  vi.mocked(developmentApi.detail).mockImplementation(async () => JSON.parse(JSON.stringify({ item, history: [], product: null })))
  vi.mocked(domainApi.products).mockResolvedValue({ items: [], mockData: false, source: 'PERSISTENT' } as Awaited<ReturnType<typeof domainApi.products>>)
})
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks() })
async function open(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/product-development/:id?', component: ProductDevelopmentView }, { path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push(path); await router.isReady()
  wrapper = mount({ template: '<router-view />' }, { global: { plugins: [router] } }); await flushPromises()
  return router
}
function button(text: string) { return wrapper.findAll('button').find(b => b.text() === text)! }

describe('candidate-linked development', () => {
  it('creates a manual draft with the shared editor and navigates to its detail', async () => {
    const router = await open('/product-development/new')
    expect(wrapper.text()).toContain('需求来源 · 手动创建')
    expect(developmentApi.detail).not.toHaveBeenCalled()
    expect(wrapper.find('a[href*="amazon.com"]').exists()).toBe(false)
    expect(button('开始开发')).toBeUndefined()
    await wrapper.get('input[required]').setValue('手动新品需求')
    vi.mocked(developmentApi.create).mockImplementation(async (_request, fields) => { item = { ...item, id: 'm1', number: 'ND-M-00000001', sourceType: 'MANUAL', source: null, fields }; return item })
    await wrapper.get('.development-editor').trigger('submit'); await flushPromises()
    expect(developmentApi.create).toHaveBeenCalledWith(expect.stringMatching(/^[0-9a-f-]{36}$/), expect.objectContaining({ name: '手动新品需求' }))
    expect(router.currentRoute.value.path).toBe('/product-development/m1')
    expect(wrapper.text()).toContain('新品需求已添加')
    expect(wrapper.text()).toContain('ND-M-00000001')
    expect(wrapper.find('a[href*="/selection/candidates/"]').exists()).toBe(false)
  })
  it('retries an uncertain create with exactly the same request and preserves inputs', async () => {
    await open('/product-development/new')
    await wrapper.get('input[required]').setValue('重试需求')
    vi.mocked(developmentApi.create).mockRejectedValueOnce(new Error('network'))
    await wrapper.get('.development-editor').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('上次创建结果尚未确认')
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    const call = vi.mocked(developmentApi.create).mock.calls[0]
    vi.mocked(developmentApi.create).mockImplementation(async (_request, fields) => { item = { ...item, id: 'm2', sourceType: 'MANUAL', source: null, fields }; return item })
    await button('重试创建').trigger('click'); await flushPromises()
    expect(developmentApi.create).toHaveBeenLastCalledWith(...call!)
  })
  it('lets users correct rejected input without clearing their form', async () => {
    await open('/product-development/new')
    await wrapper.get('input[required]').setValue('资料待修正')
    vi.mocked(developmentApi.create).mockRejectedValue(new ApiError(400, 'INVALID', '日期不正确'))
    await wrapper.get('.development-editor').trigger('submit'); await flushPromises()
    expect(wrapper.get('fieldset').attributes('disabled')).toBeUndefined()
    expect((wrapper.get('input[required]').element as HTMLInputElement).value).toBe('资料待修正')
    expect(wrapper.text()).toContain('日期不正确')
  })
  it('hides manual creation from read-only and mock users, including direct new route', async () => {
    vi.spyOn(session, 'canAccess').mockReturnValue(false)
    await open('/product-development')
    expect(wrapper.find('a[href="/product-development/new"]').exists()).toBe(false)
    wrapper.unmount(); await open('/product-development/new')
    expect(wrapper.text()).toContain('无权添加新品需求')
    expect(wrapper.find('fieldset').exists()).toBe(false)
    expect(developmentApi.create).not.toHaveBeenCalled()
    wrapper.unmount(); config.authMode = 'mock'; vi.spyOn(session, 'canAccess').mockReturnValue(true)
    await open('/product-development/new'); expect(wrapper.find('fieldset').exists()).toBe(false)
  })
  it('uses server-side search, status and pagination with candidate provenance', async () => {
    await open('/product-development')
    expect(wrapper.text()).toContain('ND-B0DEV33001')
    await button('下一页').trigger('click'); await flushPromises()
    expect(developmentApi.list).toHaveBeenLastCalledWith('', '', 1, 20)
    await button('进行中').trigger('click'); await flushPromises()
    expect(developmentApi.list).toHaveBeenLastCalledWith('', 'IN_PROGRESS', 0, 20)
    await wrapper.get('input').setValue('露营'); await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(developmentApi.list).toHaveBeenLastCalledWith('露营', 'IN_PROGRESS', 0, 20)
    await wrapper.get('select').setValue('50'); await flushPromises()
    expect(developmentApi.list).toHaveBeenLastCalledWith('露营', 'IN_PROGRESS', 0, 50)
  })
  it('preserves edits on conflict and protects unsaved navigation', async () => {
    const router = await open('/product-development/c1')
    expect(wrapper.text()).toContain('通过时评分 82')
    await wrapper.get('input[required]').setValue('新版需求')
    expect(button('开始开发').attributes('disabled')).toBeDefined()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await router.push('/product-development'); expect(router.currentRoute.value.path).toBe('/product-development/c1')
    vi.mocked(developmentApi.update).mockRejectedValue(new ApiError(409, 'CONFLICT', '资料已被更新'))
    await wrapper.get('.development-editor').trigger('submit'); await flushPromises()
    expect((wrapper.get('input[required]').element as HTMLInputElement).value).toBe('新版需求')
    expect(wrapper.text()).toContain('资料已被更新')
    expect(developmentApi.update).toHaveBeenCalledWith('c1', 0, expect.objectContaining({ name: '新版需求' }))
  })
  it('saves before transition and renders terminal requests read-only', async () => {
    await open('/product-development/c1')
    await wrapper.get('input[required]').setValue('新版需求')
    vi.mocked(developmentApi.update).mockImplementation(async (_id, _version, fields) => { item = { ...item, fields, version: 1 }; return item })
    await wrapper.get('.development-editor').trigger('submit'); await flushPromises()
    expect(button('开始开发').attributes('disabled')).toBeUndefined()
    vi.mocked(developmentApi.transition).mockImplementation(async () => { item = { ...item, status: 'VOID', version: 2, voidReason: '取消需求' }; return item })
    await button('作废需求').trigger('click')
    await wrapper.get('[role="dialog"] textarea').setValue('取消需求')
    await wrapper.get('[role="dialog"] form').trigger('submit'); await flushPromises()
    expect(developmentApi.transition).toHaveBeenCalledWith('c1', 1, 'VOID', '取消需求')
    expect(wrapper.text()).toContain('需求已作废。')
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    expect(button('保存资料')).toBeUndefined()
  })
  it('keeps selection read-only without loading product-write references', async () => {
    vi.spyOn(session, 'canAccess').mockReturnValue(false)
    await open('/product-development/c1')
    expect(wrapper.text()).toContain('当前账号可查看开发资料')
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    expect(domainApi.products).not.toHaveBeenCalled()
    expect(button('开始开发')).toBeUndefined()
  })
  it('does not call formal APIs in mock mode', async () => {
    config.authMode = 'mock'; await open('/product-development/c1')
    expect(wrapper.text()).toContain('模拟候选不会创建开发需求')
    expect(developmentApi.detail).not.toHaveBeenCalled(); expect(domainApi.products).not.toHaveBeenCalled()
  })
  it('reports missing or unapproved candidate detail without showing an editor', async () => {
    vi.mocked(developmentApi.detail).mockRejectedValue(new ApiError(404, 'NOT_FOUND', '仅审核通过的候选可进入'))
    await open('/product-development/c1')
    expect(wrapper.text()).toContain('仅审核通过的候选可进入')
    expect(wrapper.find('fieldset').exists()).toBe(false)
  })
})
