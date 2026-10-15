import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from '@/router'
import { session } from '@/state/session'
import AdminLayout from './AdminLayout.vue'

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
})

async function setup(path = '/home', allowed = ref<string[] | null>(null)) {
  vi.spyOn(session, 'canAccess').mockImplementation(capabilities => !capabilities?.length
    || allowed.value === null || capabilities.every(item => allowed.value?.includes(item)))
  const children = appRoutes.find(item => item.path === '/')!.children!
  const router = createRouter({ history: createMemoryHistory(), routes: children.map(item => ({
    path: `/${item.path}`, ...(item.name ? { name: item.name } : {}), ...(item.meta ? { meta: item.meta } : {}), component: { template: '<div />' },
  })) })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(AdminLayout, { attachTo: document.body, global: { plugins: [router], stubs: { RouterView: true } } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

describe('horizontal navigation shell', () => {
  it('navigates to the home page from the brand', async () => {
    const { wrapper, router } = await setup('/products')

    await wrapper.get('a.erp-brand[href="/home"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/home')
  })

  it('groups existing permitted routes and closes the panel after navigating', async () => {
    const { wrapper, router } = await setup()
    await wrapper.get('[data-domain="商品"]').trigger('click')
    expect(wrapper.get('.mega-panel').text()).toContain('产品与 SKU')
    expect(wrapper.get('.mega-panel').text()).toContain('辅料管理')
    expect(wrapper.get('.mega-panel').text()).not.toContain('辅料库存')
    expect(wrapper.get('.mega-panel').text()).toContain('导入中心')
    await wrapper.get('.mega-panel a[href="/imports"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/imports')
    expect(wrapper.find('.mega-panel').exists()).toBe(false)
    expect(wrapper.get('.workspace-tab.is-current').text()).toContain('导入中心')
  })

  it('never exposes hidden routes or unauthorized groups and removes revoked tabs', async () => {
    const allowed = ref<string[] | null>(['dashboard.read', 'products.read'])
    const { wrapper } = await setup('/products', allowed)
    expect(wrapper.find('[data-domain="选品"]').exists()).toBe(false)
    await wrapper.get('[data-domain="商品"]').trigger('click')
    expect(wrapper.find('.mega-panel a[href="/products/new"]').exists()).toBe(false)
    expect(wrapper.find('.mega-panel a[href="/imports"]').exists()).toBe(false)
    allowed.value = ['dashboard.read']
    await nextTick()
    expect(wrapper.find('[data-domain="商品"]').exists()).toBe(false)
    expect(wrapper.find('.workspace-tab').exists()).toBe(false)
    expect(wrapper.find('.mega-panel').exists()).toBe(false)
  })

  it('maps detail pages to their parent and keeps tabs unique', async () => {
    const { wrapper, router } = await setup('/products/example/variants')
    expect(wrapper.get('[data-domain="商品"]').classes()).toContain('is-active')
    expect(wrapper.get('.workspace-tab.is-current').text()).toContain('产品与 SKU')
    await router.push('/imports')
    await router.push('/products')
    expect(wrapper.findAll('.workspace-tab')).toHaveLength(2)
    await router.push('/inventory/ledger')
    expect(wrapper.get('.workspace-tab.is-current').text()).toContain('辅料管理')
  })

  it('closes an active tab and navigates to another available page', async () => {
    const { wrapper, router } = await setup('/products')
    await router.push('/imports')
    await wrapper.get('button[aria-label="关闭导入中心页签"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/products')
    await wrapper.get('button[aria-label="关闭产品与 SKU页签"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/home')
    expect(wrapper.findAll('.workspace-tab')).toHaveLength(0)
  })

  it('supports keyboard entry, Escape focus restoration and outside dismissal', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-domain="商品"]').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(wrapper.get('.mega-panel a').element)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.find('.mega-panel').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('[data-domain="商品"]').element)
    await wrapper.get('[data-domain="选品"]').trigger('click')
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('.mega-panel').exists()).toBe(false)
  })
})
