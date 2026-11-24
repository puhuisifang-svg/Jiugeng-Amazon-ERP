import {mount,flushPromises} from '@vue/test-utils'
import {beforeEach,describe,expect,it,vi} from 'vitest'
import CustomsFilesDialog from './CustomsFilesDialog.vue'
import CustomsAttachmentPreview from './CustomsAttachmentPreview.vue'
import {customsFilesApi,exportFilename} from '@/api/customsDocuments'
vi.mock('@/api/customsDocuments',async importOriginal=>({...await importOriginal<typeof import('@/api/customsDocuments')>(),customsFilesApi:{list:vi.fn(),upload:vi.fn(),remove:vi.fn(),download:vi.fn()}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn().mockResolvedValue(true)}))
const file=(category:'SCREENSHOT'|'INVOICE'|'OTHER')=>({id:category,category,name:`${category}.png`,size:30,mediaType:'image/png',createdAt:'2026-01-01'})
const render=()=>mount(CustomsFilesDialog,{props:{id:'synthetic',documentNo:'SYN',writable:true},global:{stubs:{AccessDialog:{template:'<div><slot/></div>'},CustomsAttachmentPreview:{name:'CustomsAttachmentPreview',props:['documentId','file'],template:'<div />'},SingleFileUpload:{name:'SingleFileUpload',template:'<div/>',props:['upload','accept']}}}})
describe('customs document files',()=>{
 beforeEach(()=>{vi.clearAllMocks();vi.mocked(customsFilesApi.list).mockResolvedValue([])})
 it('opens a preview from the file name without downloading',async()=>{vi.mocked(customsFilesApi.list).mockResolvedValue([file('SCREENSHOT')]);const w=render();await flushPromises();await w.get('.file-name').trigger('click');expect(w.getComponent(CustomsAttachmentPreview).props('file')).toEqual(file('SCREENSHOT'));expect(customsFilesApi.download).not.toHaveBeenCalled();w.unmount()})
 it('uses RFC encoded export filename',()=>expect(exportFilename("attachment; filename*=UTF-8''SYN%E6%8A%A5%E5%85%B3%E5%8D%95%E8%B5%84%E6%96%99-11-4-FBA.xlsx",'fallback')).toBe('SYN报关单资料-11-4-FBA.xlsx'))
 it('requires both categories and switches tabs',async()=>{vi.mocked(customsFilesApi.list).mockResolvedValue([file('SCREENSHOT'),file('OTHER')]);const w=render();await flushPromises();expect(w.get('.customs-file-badge.pending').text()).toContain('发票');await w.get('#customs-file-tab-INVOICE').trigger('click');expect(w.get('[role=tabpanel]').attributes('id')).toBe('customs-file-panel-INVOICE');expect(w.text()).toContain('暂无发票')})
 it('refreshes completion after upload and removal',async()=>{const w=render();await flushPromises();vi.mocked(customsFilesApi.list).mockResolvedValue([file('SCREENSHOT'),file('INVOICE')]);await w.getComponent({name:'SingleFileUpload'}).props('upload')(new File(['x'],'synthetic.png'));await flushPromises();expect(w.findAll('.customs-file-badge.uploaded')).toHaveLength(2);expect(w.emitted('changed')).toHaveLength(1);vi.mocked(customsFilesApi.list).mockResolvedValue([file('INVOICE')]);await w.findAll('button').find(b=>b.text()==='移除')!.trigger('click');await flushPromises();expect(w.get('.customs-file-badge.pending').text()).toContain('申报订单截图');expect(customsFilesApi.remove).toHaveBeenCalledWith('synthetic','SCREENSHOT')})
})
