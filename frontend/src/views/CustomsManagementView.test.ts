import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ list: vi.fn(), save: vi.fn(), canAccess: vi.fn(), leave: vi.fn() }))
vi.mock('@/api/customs', async importOriginal => ({ ...await importOriginal<typeof import('@/api/customs')>(), customsApi: mocks }))
vi.mock('@/state/session', () => ({ session: { canAccess: mocks.canAccess } }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: mocks.leave, RouterLink: { template: '<a><slot /></a>' } }))
import CustomsManagementView from './CustomsManagementView.vue'
import { blankCustoms } from '@/api/customs'
const row = { productId: 'p', productCode: 'SYN-P', productName: '合成父体', variantId: 'v', skuCode: 'SYN-S', skuName: '合成蓝色子体', productStatus: 'DRAFT', skuStatus: 'DRAFT', customs: null }
const render = () => mount(CustomsManagementView)
beforeEach(() => {
  vi.clearAllMocks(); mocks.canAccess.mockReturnValue(true); mocks.list.mockResolvedValue({ items: [row], totalCount: 1 }); mocks.save.mockResolvedValue({ ...blankCustoms(), version: 1 })
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
describe('报关资料联动', () => {
  it('shows every SKU including missing profiles and filters by keyword/status with paging', async () => {
    mocks.list.mockResolvedValue({ items: [row], totalCount: 21 })
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('tbody').text()).toContain('合成蓝色子体'); expect(wrapper.get('tbody').text()).toContain('未维护')
    await wrapper.get('.filter-panel input').setValue('SYN'); await wrapper.get('.filter-panel select').setValue('MISSING')
    await wrapper.get('.filter-panel').trigger('submit'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith('SYN', 'MISSING', 0)
    await wrapper.findAll('.pagination button')[1]!.trigger('click'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith('SYN', 'MISSING', 1); wrapper.unmount()
  })
  it('saves drafts, preserves unsaved input and surfaces conflicts without closing', async () => {
    const wrapper = render(); await flushPromises(); await wrapper.get('tbody button').trigger('click'); await flushPromises()
    const input = wrapper.findAll('dialog label').find(label => label.text().startsWith('英文申报品名'))!.get('input')
    await input.setValue('Synthetic pouch'); await wrapper.get('dialog').trigger('click')
    expect((input.element as HTMLInputElement).value).toBe('Synthetic pouch')
    expect(mocks.leave.mock.calls[0]![0]()).toBe(false); await flushPromises()
    await wrapper.get('dialog button').trigger('click'); await flushPromises()
    mocks.save.mockRejectedValueOnce(new Error('报关资料已被其他页面修改'))
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('其他页面修改')
    await wrapper.get('dialog form').trigger('submit'); await flushPromises()
    expect(mocks.save).toHaveBeenLastCalledWith('v', expect.objectContaining({ nameEn: 'Synthetic pouch', state: 'DRAFT', version: 0 }))
    expect(wrapper.find('dialog').exists()).toBe(false); expect(wrapper.text()).toContain('产品管理同步生效'); wrapper.unmount()
  })
  it('keeps reviewed profiles versioned and disables all edits for read-only users', async () => {
    mocks.canAccess.mockReturnValue(false); mocks.list.mockResolvedValue({ items: [{ ...row, customs: { ...blankCustoms(), nameZh: '合成申报名', state: 'REVIEWED', version: 5 } }], totalCount: 1 })
    const wrapper = render(); await flushPromises(); expect(wrapper.get('tbody').text()).toContain('资料已核对')
    await wrapper.get('tbody button').trigger('click'); await flushPromises()
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('dialog button[type="submit"]')).toHaveLength(0); wrapper.unmount()
  })
})
