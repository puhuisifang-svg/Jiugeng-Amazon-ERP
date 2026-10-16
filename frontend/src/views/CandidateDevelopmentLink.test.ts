import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import CandidatesView from './CandidatesView.vue'
import CandidateDetailView from './CandidateDetailView.vue'
import { domainApi } from '@/api/domain'
import { session } from '@/state/session'
const config = vi.hoisted(() => ({ authMode: 'password', apiBaseUrl: '/api' }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: config }))
afterEach(() => vi.restoreAllMocks())
describe('candidate development entry', () => {
  it.each(['APPROVED', 'NEW', 'REVIEWING', 'REJECTED'])('only exposes approved candidates: %s', async status => {
    config.authMode = 'password'
    const candidate = { id: 'c1', asin: 'B0DEV33001', title: '候选测试', score: 80, status }
    vi.spyOn(session, 'canAccess').mockReturnValue(false)
    vi.spyOn(domainApi, 'candidates').mockResolvedValue({ items: [candidate], mockData: true, source: 'SYNTHETIC' })
    vi.spyOn(domainApi, 'candidateDetail').mockResolvedValue({ item: { candidate, notes: [], evidence: [] }, mockData: true, source: 'SYNTHETIC' })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/selection/candidates/:candidateId', component: CandidateDetailView }, { path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
    await router.push('/selection/candidates/c1'); await router.isReady()
    for (const component of [CandidatesView, CandidateDetailView]) {
      const wrapper = mount(component, { global: { plugins: [router] } }); await flushPromises()
      expect(wrapper.find('a[href="/product-development/c1"]').exists()).toBe(status === 'APPROVED')
      expect(wrapper.findComponent({ name: 'SyntheticNotice' }).exists()).toBe(false)
      wrapper.unmount()
    }
    config.authMode = 'mock'
    const wrapper = mount(CandidateDetailView, { global: { plugins: [router] } }); await flushPromises()
    expect(wrapper.find('a[href="/product-development/c1"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
