import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import Manager from './CustomsWarehouseManager.vue'
const api=vi.hoisted(()=>({list:vi.fn(),save:vi.fn(),import:vi.fn(),status:vi.fn(),sync:vi.fn()}))
vi.mock('@/api/customsDocuments',()=>({customsWarehousesApi:api}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn().mockResolvedValue(true)}))
const row={code:'SYN1',country:'US',region:'CA',city:'Synthetic',postalCode:'00000',address:'Synthetic address',source:'Synthetic source',verifiedOn:'',version:2}
beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn();api.status.mockResolvedValue({state:'SUCCESS',finishedAt:'',nextRunAt:'',message:'更新完成',added:0,updated:0,preserved:0,rejected:0,sourceDate:'',automatic:true});api.list.mockResolvedValue([row]);api.save.mockResolvedValue(row)})
it('retains inputs on failed save and sends the version when retrying',async()=>{
 const w=mount(Manager,{props:{writable:true,initialCode:'syn1'}});await flushPromises()
 expect(w.get('input[aria-label="仓库编码"]').attributes('readonly')).toBeDefined()
 await w.get('form textarea').setValue('Updated synthetic address');api.save.mockRejectedValueOnce(new Error('版本冲突'))
 await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('版本冲突');expect((w.get('form textarea').element as HTMLTextAreaElement).value).toBe('Updated synthetic address')
 await w.get('form').trigger('submit');await flushPromises();expect(api.save).toHaveBeenLastCalledWith(expect.objectContaining({code:'SYN1',version:2,address:'Updated synthetic address'}),true);expect(w.emitted('saved')).toHaveLength(1);w.unmount()
})
it('previews pasted rows and retains them when the batch is rejected',async()=>{
 const w=mount(Manager,{props:{writable:true}});await flushPromises();await w.findAll('button').find(b=>b.text()==='批量导入')!.trigger('click')
 await w.get('textarea').setValue('SYN2\tCA\tSynthetic\t00000\tSynthetic address\tSynthetic source\t')
 expect(w.text()).toContain('待导入 1 条');api.import.mockRejectedValueOnce(new Error('仓库编码已存在'))
 await w.findAll('button').find(b=>b.text().includes('确认导入'))!.trigger('click');await flushPromises();expect(w.text()).toContain('仓库编码已存在');expect((w.get('textarea').element as HTMLTextAreaElement).value).toContain('SYN2');w.unmount()
})
it('hides editing and import actions without write permission',async()=>{
 const w=mount(Manager,{props:{writable:false}});await flushPromises();expect(w.text()).toContain('SYN1');expect(w.text()).not.toContain('新增仓库');expect(w.text()).not.toContain('批量导入');expect(w.findAll('button').some(b=>b.text()==='编辑')).toBe(false);w.unmount()
})

it('keeps existing data editable without requesting internet updates or status',async()=>{
 const w=mount(Manager,{props:{writable:true}});await flushPromises();expect(w.text()).toContain('SYN1');expect(w.text()).not.toContain('网上地址更新');expect(w.text()).not.toContain('立即更新');expect(api.status).not.toHaveBeenCalled();expect(api.sync).not.toHaveBeenCalled();expect(w.findAll('button').some(b=>b.text()==='编辑')).toBe(true);w.unmount()
})

it('renders the standalone page without a dialog and guards unsaved route changes',async()=>{
 const {createRouter,createMemoryHistory,RouterView}=await import('vue-router')
 const {confirmAction}=await import('@/state/uiConfirmation')
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:Manager,props:{writable:true,standalone:true}},{path:'/other',component:{template:'<div>other</div>'}}]})
 await router.push('/');await router.isReady()
 const w=mount(RouterView,{global:{plugins:[router]}});await flushPromises()
 expect(w.find('dialog').exists()).toBe(false);expect(w.text()).toContain('SYN1')
 await w.findAll('button').find(b=>b.text()==='编辑')!.trigger('click');await w.get('textarea').setValue('Synthetic unsaved address')
 vi.mocked(confirmAction).mockResolvedValueOnce(false)
 await router.push('/other');expect(router.currentRoute.value.path).toBe('/')
 expect(w.text()).toContain('保存修改');w.unmount()
})
