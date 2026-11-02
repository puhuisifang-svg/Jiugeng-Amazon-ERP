import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import UserRolesEditor from './UserRolesEditor.vue'
import { rolesApi } from '@/api/roles'
import type { AppUser } from '@/api/auth'
vi.mock('@/api/roles', () => ({ rolesApi: { previewAssignment: vi.fn(), assign: vi.fn() } }))
it.each(['', '调整', '调'])('allows last-role removal with optional reason %j', async (reason) => {
  vi.mocked(rolesApi.previewAssignment).mockResolvedValue({ added: [], removed: ['products.read'], members: [] })
  const w = mount(UserRolesEditor, { props: {
    user: { id: 'synthetic', displayName: 'Synthetic', roles: [{ id: 'test', name: 'Test', status: 'ENABLED' }], authorizationVersion: 3 } as AppUser,
    roles: [{ id: 'test', name: 'Test', status: 'ENABLED', kind: 'CUSTOM', permissions: [], description: '', memberCount: 1, version: 0, updatedAt: '' }], catalog: [],
  }, global: { stubs: { AccessDialog: { template: '<div><slot/><slot name="footer"/></div>' } } } })
  await flushPromises()
  expect(w.get('.primary-button').attributes('disabled')).toBeDefined()
  await w.get('input[type="checkbox"]').setValue(false)
  expect(w.get('.primary-button').attributes('disabled')).toBeUndefined()
  await w.get('textarea').setValue(reason)
  expect(w.get('[role="status"]').text()).toContain('同时停用')
  await w.get('.primary-button').trigger('click'); await flushPromises()
  expect(rolesApi.previewAssignment).toHaveBeenCalledWith('synthetic', { roleIds: [], version: 3, reason })
  expect(w.get('.primary-button').text()).toBe('移除角色并停用账号')
  await w.get('.primary-button').trigger('click'); await flushPromises()
  expect(rolesApi.assign).toHaveBeenCalledWith('synthetic', { roleIds: [], version: 3, reason })
  expect(w.emitted('saved')).toHaveLength(1)
  w.unmount()
})
