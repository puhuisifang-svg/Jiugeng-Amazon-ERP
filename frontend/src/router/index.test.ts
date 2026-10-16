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

    expect(groups).toEqual(new Set(['工作台', '工作台维护', '产品管理', '采购管理', '产品监控', '选品管理', '系统管理']))
  })

  it('merges inventory into material management while preserving the legacy route guard', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const inventory = shell?.children?.find((route) => route.name === 'inventory-balances')
    const materials = shell?.children?.find((route) => route.name === 'materials')

    expect(inventory?.meta?.capabilities).toEqual(['inventory.read'])
    expect(inventory?.meta?.hidden).toBe(true)
    expect(inventory?.redirect).toBe('/materials')
    expect(materials?.meta?.title).toBe('辅料管理')
  })

  it('matches the reviewed UI IA module routes', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const paths = new Set(shell?.children?.map((route) => `/${route.path}`))
    const expectedPaths = [
      '/home', '/products', '/materials', '/suppliers', '/inventory/balances',
      '/inventory/ledger', '/imports', '/purchases', '/purchases/orders', '/purchases/receipts',
      '/selection/candidates', '/watch/stores',
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

  it('protects all purchase workspaces and keeps selection users out', () => {
    const shell = appRoutes.find((route) => route.path === '/')
    const purchases = shell?.children?.filter((route) => String(route.path).startsWith('purchases')) ?? []
    expect(purchases).toHaveLength(4)
    expect(purchases.filter(route => !route.meta?.hidden).map(route => route.path)).toEqual(['purchases/orders'])
    expect(purchases.find(route => route.path === 'purchases')?.redirect).toBe('/purchases/orders')
    expect(purchases.find(route => route.path === 'purchases/receipts')?.redirect).toBe('/purchases/orders')
    expect(purchases.every((route) => route.meta?.capabilities?.includes('purchases.read'))).toBe(true)
    expect(capabilitiesForRole('ADMIN')).toEqual(expect.arrayContaining(['purchases.read', 'purchases.write', 'purchases.receive']))
    expect(capabilitiesForRole('PRODUCT_MATERIAL')).toEqual(expect.arrayContaining(['purchases.read', 'purchases.write', 'purchases.receive']))
    expect(capabilitiesForRole('SELECTION')).not.toContain('purchases.read')
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
