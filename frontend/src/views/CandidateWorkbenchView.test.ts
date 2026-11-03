import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import CandidateWorkbenchView from './CandidateWorkbenchView.vue'
const h=vi.hoisted(()=>({list:vi.fn(),remove:vi.fn(),confirm:vi.fn(),write:true}))
vi.mock('vue-router',()=>({useRoute:()=>({params:{}}),useRouter:()=>({push:vi.fn()}),onBeforeRouteLeave:vi.fn(),onBeforeRouteUpdate:vi.fn()}))
vi.mock('@/api/candidates',()=>({candidatesApi:{list:h.list,remove:h.remove}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:h.confirm}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.write,user:{value:{displayName:'Synthetic'}}}}))
function view(){return mount(CandidateWorkbenchView,{global:{stubs:{TableViewport:{template:'<div><slot/></div>'},ListPagination:true,SuccessToast:true,RouterLink:true}}})}
beforeEach(()=>{vi.clearAllMocks();h.write=true;h.list.mockResolvedValue({items:[],total:0,counts:{NEW:2,EVALUATING:3},owners:['Researcher']})})
describe('candidate list',()=>{
 it('confirms deletion, sends displayed version and refreshes the list',async()=>{h.list.mockResolvedValue({items:[{id:'one',title:'Example',state:'NEW',document:{},version:7}],total:1,counts:{NEW:1}});h.confirm.mockResolvedValue(false);const w=view();await flushPromises();await w.get('.candidate-delete').trigger('click');await flushPromises();expect(h.remove).not.toHaveBeenCalled();h.confirm.mockResolvedValue(true);await w.get('.candidate-delete').trigger('click');await flushPromises();expect(h.remove).toHaveBeenCalledWith('one',7);expect(h.list).toHaveBeenCalledTimes(2);w.unmount()})
 it('shows deletion failure without removing the row',async()=>{h.list.mockResolvedValue({items:[{id:'one',title:'Example',state:'NEW',document:{},version:7}],total:1,counts:{NEW:1}});h.confirm.mockResolvedValue(true);h.remove.mockRejectedValueOnce(new Error('候选已更新'));const w=view();await flushPromises();await w.get('.candidate-delete').trigger('click');await flushPromises();expect(w.text()).toContain('候选已更新');expect(w.find('.candidate-delete').exists()).toBe(true);w.unmount()})
 it('searches only after submit, sends source and owner filters together',async()=>{const w=view();await flushPromises();await w.get('input').setValue('foldable');await w.get('select').setValue('OTHER');expect(h.list).toHaveBeenCalledTimes(1);await w.get('form').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith(expect.objectContaining({q:'foldable',source:'OTHER',page:1}));w.unmount()})
 it('switches state with counts and resets pagination',async()=>{const w=view();await flushPromises();await w.findAll('.candidate-state-tabs button')[2]!.trigger('click');await flushPromises();expect(h.list).toHaveBeenLastCalledWith(expect.objectContaining({state:'EVALUATING',page:1}));expect(w.get('.candidate-state-tabs').text()).toContain('全部 5');w.unmount()})
 it('displays a recoverable request error and hides create for readers',async()=>{h.write=false;h.list.mockRejectedValue(new Error('网络暂时不可用'));const w=view();await flushPromises();expect(w.text()).toContain('网络暂时不可用');expect(w.text()).not.toContain('添加候选');expect(w.text()).toContain('重试');w.unmount()})
})
