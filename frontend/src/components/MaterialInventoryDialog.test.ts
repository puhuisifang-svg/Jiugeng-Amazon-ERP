import { mount,flushPromises } from '@vue/test-utils'
import { beforeEach,describe,it,expect,vi } from 'vitest'
const mocks=vi.hoisted(()=>({inventory:vi.fn(),request:vi.fn()}))
vi.mock('@/api/materialManagement',()=>({materialManagementApi:{inventory:mocks.inventory}}))
vi.mock('@/api/client',async()=>{const actual=await vi.importActual('@/api/client');return {...actual,apiRequest:mocks.request}})
vi.mock('@/state/session',()=>({session:{user:{value:{id:'stock-user'}},canAccess:()=>true}}))
vi.mock('./MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialInventoryDialog from './MaterialInventoryDialog.vue'
const material={id:'m1',materialCode:'M1',name:'合成胶带',materialType:'耗材',unit:'米',status:'ACTIVE' as const,version:1}
const global={stubs:{AccessDialog:{props:['title','busy','dirty'],template:'<div><slot /><slot name="footer" /></div>',methods:{requestClose(){}}},TableViewport:{template:'<div><slot /></div>'}}}
beforeEach(()=>{sessionStorage.clear();mocks.inventory.mockReset().mockResolvedValue({balance:{quantity:2,safetyStock:3},ledger:{items:[],totalCount:0}});mocks.request.mockReset()})
describe('material inventory request recovery',()=>{
 it('retries an unknown outcome using exactly the original payload',async()=>{mocks.request.mockRejectedValueOnce(new Error('network')).mockResolvedValue({});const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises();await w.get('input[inputmode="decimal"]').setValue('0.125');await w.get('input[placeholder="例如：日常领用、盘点差异"]').setValue('合成测试');await w.get('form').trigger('submit');await flushPromises();const first=mocks.request.mock.calls[0]![1].body;expect(w.text()).toContain('待确认');expect(w.get('fieldset').attributes('disabled')).toBeDefined();await w.get('form').trigger('submit');await flushPromises();expect(mocks.request.mock.calls[1]![1].body).toBe(first);expect(JSON.parse(first).quantity).toBe('0.125');expect(sessionStorage.getItem('jiugeng.material-stock.pending.stock-user.m1')).toBeNull();w.unmount()})
 it('does not interpret a failed balance read as zero stock',async()=>{mocks.inventory.mockRejectedValue(new Error('offline'));const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises();expect(w.text()).toContain('库存读取失败，不能据此记账');await w.get('form').trigger('submit');expect(mocks.request).not.toHaveBeenCalled();w.unmount()})
})
