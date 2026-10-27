import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi} from 'vitest'
import MonitorInlineEditor from './MonitorInlineEditor.vue'
import {monitoringApi,type MonitorTarget} from '@/api/monitoring'
vi.mock('@/api/monitoring',()=>({monitoringApi:{edit:vi.fn()}}))
it('saves manual sales with version and retains input after failure',async()=>{
 HTMLDialogElement.prototype.showModal=vi.fn();HTMLDialogElement.prototype.close=vi.fn()
 const w=mount(MonitorInlineEditor,{props:{target:{id:'test',asin:'B0TEST0001',version:7,remark:'',sales30Days:12} as MonitorTarget,field:'sales30Days'}});await flushPromises();expect(w.get('dialog').classes()).toContain('mw-remark-popover');await w.get('input').setValue('123')
 vi.mocked(monitoringApi.edit).mockRejectedValueOnce(new Error('记录已更新'));await w.get('form').trigger('submit');await flushPromises();expect(w.text()).toContain('记录已更新');expect((w.get('input').element as HTMLInputElement).value).toBe('123');expect(w.emitted('saved')).toBeUndefined()
 vi.mocked(monitoringApi.edit).mockResolvedValueOnce({} as MonitorTarget);await w.get('form').trigger('submit');await flushPromises();expect(monitoringApi.edit).toHaveBeenLastCalledWith('test',{version:7,field:'sales30Days',value:'123'});expect(w.emitted('saved')).toHaveLength(1);w.unmount()
})
