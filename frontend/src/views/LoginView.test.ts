import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import LoginView from './LoginView.vue'

describe('LoginView', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('logs in with a selected synthetic role and stores the session', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        token: 'local-mock-token',
        user: { id: 'mock-selection', displayName: '模拟选品员', role: 'SELECTION' },
        mockData: true,
        source: 'SYNTHETIC',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/login', component: LoginView }, { path: '/home', component: { template: '<p>home</p>' } }],
    })
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.get('select').setValue('SELECTION')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/auth/login'), expect.objectContaining({ method: 'POST' }))
    expect(router.currentRoute.value.path).toBe('/home')
    expect(window.localStorage.getItem('jiugeng.session.user')).toContain('SELECTION')
  })

  it('refuses a login response without the synthetic marker', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        token: 'unexpected-token',
        user: { id: 'user', displayName: 'Unknown', role: 'ADMIN' },
        mockData: false,
        source: 'UNKNOWN',
      }),
    }))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/login', component: LoginView }, { path: '/home', component: { template: '<p>home</p>' } }],
    })
    await router.push('/login')
    await router.isReady()
    const wrapper = mount(LoginView, { global: { plugins: [router] } })

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.get('[role="alert"]').text()).toContain('mock profile')
    expect(window.localStorage.getItem('jiugeng.session.token')).toBeNull()
  })
})
