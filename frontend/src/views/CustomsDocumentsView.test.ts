import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const api=vi.hoisted(()=>({list:vi.fn(),remove:vi.fn(),canAccess:vi.fn(),confirm:vi.fn()}))
vi.mock('@/api/customsDocuments',()=>({customsDocumentsApi:api}))
vi.mock('@/state/session',()=>({session:{canAccess:api.canAccess}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:api.confirm}))
vi.mock('@/components/CustomsDocumentEditor.vue',()=>({default:{template:'<div />'}}))
import View from './CustomsDocumentsView.vue'
const row={id:'syn',documentNo:'SYN-DELETE',status:'VOID',seller:'Synthetic',buyer:'',destination:'',itemCount:1,version:4,updatedAt:'2026-11-04T00:00:00Z'}
beforeEach(()=>{vi.clearAllMocks();api.canAccess.mockReturnValue(true);api.confirm.mockResolvedValue(true);api.remove.mockResolvedValue(undefined);api.list.mockResolvedValue({items:[row],totalCount:1})})
const removeButton=(w:ReturnType<typeof mount>)=>w.findAll('tbody button').find(b=>b.text()==='删除')!
describe('报关单据删除',()=>{
 it('confirms deletion and removes the row after success',async()=>{
  const w=mount(View);await flushPromises();api.confirm.mockResolvedValueOnce(false);await removeButton(w).trigger('click');await flushPromises();expect(api.remove).not.toHaveBeenCalled()
  api.list.mockResolvedValue({items:[],totalCount:0});await removeButton(w).trigger('click');await flushPromises()
  expect(api.confirm).toHaveBeenCalledWith(expect.stringContaining('SYN-DELETE'));expect(api.remove).toHaveBeenCalledWith('syn',4);expect(w.get('tbody').text()).not.toContain('SYN-DELETE');expect(w.text()).toContain('单据已删除');w.unmount()
 })
 it('keeps the row and shows deletion failures',async()=>{
  const w=mount(View);await flushPromises();api.remove.mockRejectedValueOnce(new Error('单据已变化，请刷新后再删除'));await removeButton(w).trigger('click');await flushPromises()
  expect(w.get('[role="alert"]').text()).toContain('单据已变化');expect(w.get('tbody').text()).toContain('SYN-DELETE');expect(removeButton(w).attributes('disabled')).toBeUndefined();w.unmount()
 })
 it('returns to the previous page after deleting its last row',async()=>{
  api.list.mockResolvedValue({items:[row],totalCount:21});const w=mount(View);await flushPromises();await w.get('[aria-label="下一页"]').trigger('click');await flushPromises()
  api.list.mockResolvedValueOnce({items:[],totalCount:20}).mockResolvedValueOnce({items:[{...row,id:'other',documentNo:'SYN-OTHER'}],totalCount:20})
  await removeButton(w).trigger('click');await flushPromises();expect(api.list).toHaveBeenLastCalledWith('','',0,20);expect(w.get('tbody').text()).toContain('SYN-OTHER');w.unmount()
 })
 it('hides deletion for read-only users',async()=>{api.canAccess.mockReturnValue(false);const w=mount(View);await flushPromises();expect(removeButton(w)).toBeUndefined();w.unmount()})
})
