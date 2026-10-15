import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { session } from '@/state/session'
import { capabilitiesForRole } from '@/auth/capabilities'

import { appRoutes, refreshAccessIfStale } from './index'

afterEach(() => vi.restoreAllMocks())

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
      '/home', '/products', '/materials', '/suppliers', '/inventory/balances',
      '/inventory/ledger', '/imports', '/selection/candidates', '/watch/stores',
      '/watch/events', '/system/users', '/system/audit',
      '/dashboard-maintenance/today-shipments', '/dashboard-maintenance/pending-shipments',
      '/dashboard-maintenance/pending-products',
    ]

    expect(expectedPaths.every((path) => paths.has(path))).toBe(true)
    expect(paths.has('/listing-mappings')).toBe(false)
  })

  it('protects supplier contact data with its dedicated capability', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const suppliers = shell?.children?.find((route) => route.name === 'suppliers')
    expect(suppliers?.meta?.capabilities).toEqual(['suppliers.read'])
    expect(capabilitiesForRole('SELECTION')).not.toContain('suppliers.read')
  })

  it('refreshes server capabilities before treating a persisted session as forbidden', async () => {
    const canAccess = vi.spyOn(session, 'canAccess')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)
    const refresh = vi.spyOn(session, 'refresh').mockResolvedValue()

    await expect(refreshAccessIfStale(['dashboard.read'])).resolves.toBe(true)
    expect(refresh).toHaveBeenCalledOnce()
    expect(canAccess).toHaveBeenCalledTimes(2)
  })

  it('keeps a genuine server-side capability denial forbidden after refresh', async () => {
    vi.spyOn(session, 'canAccess').mockReturnValue(false)
    vi.spyOn(session, 'refresh').mockResolvedValue()

    await expect(refreshAccessIfStale(['dashboard.shipments.manage'])).resolves.toBe(false)
  })
})
