import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { appRoutes } from './index'

describe('application routes', () => {
  it('redirects the root shell to the home page', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: appRoutes })

    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('exposes every approved PRD navigation group', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const groups = new Set(shell?.children?.map((route) => route.meta?.group).filter(Boolean))

    expect(groups).toEqual(new Set(['工作台', '工作台维护', '产品管理', '选品管理', '系统管理']))
  })

  it('restricts inventory navigation to the required capability', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const inventory = shell?.children?.find((route) => route.name === 'inventory-balances')

    expect(inventory?.meta?.capabilities).toEqual(['inventory.read'])
  })

  it('matches the reviewed UI IA module routes', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const paths = new Set(shell?.children?.map((route) => `/${route.path}`))
    const expectedPaths = [
      '/home', '/products', '/materials', '/inventory/balances',
      '/inventory/ledger', '/imports', '/selection/candidates', '/watch/stores',
      '/watch/events', '/system/users', '/system/audit',
      '/dashboard-maintenance/today-shipments', '/dashboard-maintenance/pending-shipments',
      '/dashboard-maintenance/pending-products',
    ]

    expect(expectedPaths.every((path) => paths.has(path))).toBe(true)
    expect(paths.has('/listing-mappings')).toBe(false)
  })
})
