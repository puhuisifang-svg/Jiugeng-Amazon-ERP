import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import DevelopmentWorkbenchView from './DevelopmentWorkbenchView.vue'
const h=vi.hoisted(()=>({list:vi.fn(),write:true}))
vi.mock('vue-router',()=>({useRoute:()=>({params:{},path:"/product-development"}),useRouter:()=>({push:vi.fn()}),onBeforeRouteLeave:vi.fn(),onBeforeRouteUpdate:vi.fn()}))
vi.mock('@/api/developmentWorkbench',()=>({developmentWorkbenchApi:{list:h.list}}))
vi.mock('@/state/session',()=>({session:{canAccess:()=>h.write,user:{value:{displayName:'Synthetic'}}}}))
function view(){return mount(DevelopmentWorkbenchView,{global:{stubs:{TableViewport:{template:'<div><slot/></div>'},ListPagination:true,SuccessToast:true,RouterLink:true}}})}
beforeEach(()=>{vi.clearAllMocks();h.write=true;h.list.mockResolvedValue({items:[],total:0,counts:{DRAFT:2,ACTIVE:3},owners:['Researcher']})})
describe('development list',()=>{
 it('searches only after submit, sends stage and owner filters together',async()=>{const w=view();await flushPromises();await w.get('input').setValue('foldable');await w.get('select').setValue('2');expect(h.list).toHaveBeenCalledTimes(1);await w.get('form').trigger('submit');await flushPromises();expect(h.list).toHaveBeenLastCalledWith(expect.objectContaining({q:'foldable',stage:2,page:1}));w.unmount()})
 it('switches state with counts and resets pagination',async()=>{const w=view();await flushPromises();await w.findAll('.candidate-state-tabs button')[2]!.trigger('click');await flushPromises();expect(h.list).toHaveBeenLastCalledWith(expect.objectContaining({state:'PROJECT_REVIEW',page:1}));expect(w.get('.candidate-state-tabs').text()).toContain('全部 5');w.unmount()})
 it('displays a recoverable request error and hides create for readers',async()=>{h.write=false;h.list.mockRejectedValue(new Error('网络暂时不可用'));const w=view();await flushPromises();expect(w.text()).toContain('网络暂时不可用');expect(w.text()).not.toContain('新建开发项目');expect(w.text()).toContain('重试');w.unmount()})
})
