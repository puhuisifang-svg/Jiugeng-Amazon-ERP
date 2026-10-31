import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditView from './AuditView.vue'
import { auditApi } from '@/api/audit'
vi.mock('@/api/audit',()=>({auditApi:{events:vi.fn(),options:vi.fn()}}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>true}}))
beforeEach(()=>{
 vi.mocked(auditApi.events).mockResolvedValue({items:[{id:'synthetic',category:'SECURITY',action:'LOGIN',actorLoginName:'synthetic',resourceType:'ACCOUNT',outcome:'SUCCESS',occurredAt:'2026-10-30T10:00:00Z'}],page:0,size:20,totalElements:1,totalPages:1})
 vi.mocked(auditApi.options).mockResolvedValue({actors:[],categories:[],actions:[],resources:[]})
 HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}
})
describe('audit list details',()=>{
 it('opens a dialog without expanding the row and retains the list on close',async()=>{
  const w=mount(AuditView);await flushPromises()
  expect(w.find('tbody details').exists()).toBe(false)
  await w.get('.audit-view-button').trigger('click');expect(w.get('dialog').text()).toContain('审计日志详情')
  await w.get('dialog').trigger('cancel');expect(w.find('dialog').exists()).toBe(false)
  expect(w.findAll('tbody tr')).toHaveLength(1);w.unmount()
 })
})
