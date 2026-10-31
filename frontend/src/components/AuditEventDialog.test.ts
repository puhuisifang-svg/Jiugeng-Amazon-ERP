import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import AuditEventDialog from './AuditEventDialog.vue'
import type { AuditEvent } from '@/api/audit'
const event: AuditEvent = { id:'synthetic-event', category:'ACCOUNT', action:'ROLE_UPDATE', actorLoginName:'synthetic',actorDisplayName:'测试操作人', resourceType:'ROLE', resourceId:'synthetic-role', outcome:'SUCCESS', occurredAt:'2026-10-30T10:00:00Z', detail:'修改职责权限', beforeValue:{name:'旧角色',status:'ENABLED',permissions:['products.read']},afterValue:{name:'新角色',status:'ENABLED',permissions:['products.read','products.write']} }
beforeEach(() => {
 HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')}
 HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}
})
describe('audit event detail dialog',()=>{
 it('compares snapshots with changed rows and keeps metadata and complete values',()=>{
  const w=mount(AuditEventDialog,{props:{event}})
  expect(w.get('dialog').attributes('open')).toBeDefined()
  expect(w.text()).toContain('操作成功');expect(w.text()).toContain('测试操作人');expect(w.text()).toContain('修改职责权限')
  expect(w.findAll('.is-changed')).toHaveLength(2);expect(w.text()).toContain('products.write');expect(w.text()).toContain('旧角色');expect(w.text()).toContain('新角色')
  w.unmount()
 })
 it('handles empty failure events and cancel without modifying audit data',async()=>{
  const w=mount(AuditEventDialog,{props:{event:{...event,outcome:'FAILURE',beforeValue:null,afterValue:null,detail:'Synthetic failure'}}})
  expect(w.text()).toContain('操作失败');expect(w.text()).toContain('此操作未记录字段变更')
  await w.get('dialog').trigger('cancel');expect(w.emitted('close')).toHaveLength(1);w.unmount()
 })
 it('renders arbitrary snapshot text safely and supports a one-sided snapshot',()=>{
  const text='<img src=x onerror=alert(1)>'
  const w=mount(AuditEventDialog,{props:{event:{...event,beforeValue:null,afterValue:{remark:text,quantity:0,enabled:false,empty:''}}}})
  expect(w.find('img').exists()).toBe(false);expect(w.text()).toContain(text);expect(w.text()).toContain('未记录');expect(w.text()).toContain('否');expect(w.text()).toContain('（空）')
  expect(w.findAll('.is-changed')).toHaveLength(4);w.unmount()
 })
 it('restores focus to the list action when closed',()=>{
  const button=document.createElement('button');document.body.append(button);button.focus()
  const w=mount(AuditEventDialog,{props:{event},attachTo:document.body});w.unmount()
  expect(document.activeElement).toBe(button);button.remove()
 })
})
