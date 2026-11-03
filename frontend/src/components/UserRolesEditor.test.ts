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
  expect(w.get('[role="status"]').text()).toContain('账号状态保持不变')
  await w.get('.primary-button').trigger('click'); await flushPromises()
  expect(rolesApi.previewAssignment).toHaveBeenCalledWith('synthetic', { roleIds: [], version: 3, reason })
  expect(w.get('.primary-button').text()).toBe('确认移除全部角色')
  await w.get('.primary-button').trigger('click'); await flushPromises()
  expect(rolesApi.assign).toHaveBeenCalledWith('synthetic', { roleIds: [], version: 3, reason })
  expect(w.emitted('saved')).toHaveLength(1)
  w.unmount()
})
it.each([false, true])('limits role member changes to current role (removing=%s)', async (removing) => {
  const current = { id:'current', name:'当前岗位', status:'ENABLED' as const, kind:'CUSTOM' as const, permissions:[], description:'', memberCount:0, version:0, updatedAt:'' }
  const other = { ...current, id:'other', name:'其他岗位' }
  const w = mount(UserRolesEditor, { props: {
    user: { id:'scoped-user', displayName:'Synthetic', loginName:'synthetic', roles:removing ? [current,other] : [other], authorizationVersion:4 } as unknown as AppUser,
    roles:[current,other], catalog:[], scopedRole:current,
  }, global:{stubs:{AccessDialog:{template:'<div><slot/><slot name="footer"/></div>'}}} })
  await flushPromises()
  expect(w.find('input[type="checkbox"]').exists()).toBe(false)
  expect(w.text()).toContain('其他角色保持不变')
  await w.get('.assignment-primary').trigger('click'); await flushPromises()
  expect(rolesApi.previewAssignment).toHaveBeenLastCalledWith('scoped-user', {roleIds:removing ? ['other'] : ['other','current'],version:4,reason:''})
  expect(w.get('.assignment-primary').text()).toBe(removing ? '确认移除成员' : '确认添加成员')
  await w.get('.assignment-primary').trigger('click'); await flushPromises()
  expect(rolesApi.assign).toHaveBeenLastCalledWith('scoped-user', {roleIds:removing ? ['other'] : ['other','current'],version:4,reason:''})
  w.unmount()
})
