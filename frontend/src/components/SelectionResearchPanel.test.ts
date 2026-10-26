import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { researchApi } from '@/api/selectionResearch'
import SelectionResearchPanel from './SelectionResearchPanel.vue'
vi.mock('@/api/selectionResearch', () => ({ researchApi: { get: vi.fn(), save: vi.fn(), estimate: vi.fn() } }))
beforeEach(() => {
  vi.resetAllMocks()
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
  vi.mocked(researchApi.get).mockResolvedValue({ version: 7, weightedScore: null, estimates: [], research: { criteria: [{ score: 4, reason: '样本依据' }] } })
})
async function panel(status = 'NEW', writable = true) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  await router.push('/')
  const wrapper = mount(SelectionResearchPanel, { props: { candidateId: 'candidate', writable, status }, global: { plugins: [router] } })
  await flushPromises(); return wrapper
}
describe('selection research', () => {
  it('keeps conflict input and submits the loaded version', async () => {
    vi.mocked(researchApi.save).mockRejectedValue(new Error('版本冲突'))
    const wrapper = await panel()
    await wrapper.get('button').trigger('click')
    await wrapper.get('textarea').setValue('仍须保留的研究')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(researchApi.save).toHaveBeenCalledWith('candidate', expect.objectContaining({ version: 7, customer: '仍须保留的研究' }))
    expect(wrapper.get('textarea').element.value).toBe('仍须保留的研究')
    expect(wrapper.text()).toContain('版本冲突')
    await wrapper.get('button[type="button"]').trigger('click')
    expect(wrapper.findComponent({ name: 'UnsavedChangesDialog' }).exists()).toBe(true)
    wrapper.unmount()
  })
  it.each([['APPROVED', true], ['REJECTED', true], ['NEW', false]])('keeps %s read-only and exposes score evidence', async (status, writable) => {
    const wrapper = await panel(status as string, writable as boolean)
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.text()).toContain('样本依据')
    wrapper.unmount()
  })
})
