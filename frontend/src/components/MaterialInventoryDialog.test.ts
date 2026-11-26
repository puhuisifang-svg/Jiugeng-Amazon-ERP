import { mount,flushPromises } from '@vue/test-utils'
import { beforeEach,describe,it,expect,vi } from 'vitest'
const mocks=vi.hoisted(()=>({inventory:vi.fn(),request:vi.fn(),reasons:vi.fn(),removeReason:vi.fn(),confirm:vi.fn()}))
vi.mock('@/api/inventoryOptions',()=>({inventoryOptionsApi:{reasons:mocks.reasons,removeReason:mocks.removeReason}}))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:mocks.confirm}))
vi.mock('@/api/materialManagement',()=>({materialManagementApi:{inventory:mocks.inventory}}))
vi.mock('@/api/client',async()=>{const actual=await vi.importActual('@/api/client');return {...actual,apiRequest:mocks.request}})
vi.mock('@/state/session',()=>({session:{user:{value:{id:'stock-user'}},canAccess:()=>true}}))
vi.mock('./MaterialImage.vue',()=>({default:{template:'<span>图片</span>'}}))
import MaterialInventoryDialog from './MaterialInventoryDialog.vue'
const material={id:'m1',materialCode:'M1',name:'合成胶带',materialType:'耗材',unit:'米',status:'ACTIVE' as const,version:1}
const global={stubs:{AccessDialog:{props:['title','busy','dirty'],template:'<div><slot /><slot name="footer" /></div>',methods:{requestClose(){}}},TableViewport:{template:'<div><slot /></div>'}}}
beforeEach(()=>{sessionStorage.clear();mocks.inventory.mockReset().mockResolvedValue({balance:{quantity:2,safetyStock:3},ledger:{items:[],totalCount:0}});mocks.request.mockReset();mocks.reasons.mockReset().mockResolvedValue(['日常领用']);mocks.removeReason.mockReset().mockResolvedValue(undefined);mocks.confirm.mockReset().mockResolvedValue(true)})
describe('material inventory request recovery',()=>{
 it('keeps only inbound/outbound, shows projection beside quantity, and removes a suggestion without rewriting the draft',async()=>{
  const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises()
  expect(w.findAll('.material-operation-tabs button').map(b=>b.text())).toEqual(['入库','出库'])
  expect(w.text()).not.toContain('按包装换算');expect(w.text()).not.toContain('安全库存')
  await w.get('input[aria-label="操作数量"]').setValue('1.5');expect(w.get('.material-quantity-line').text()).toContain('3.5')
  await w.get('input[aria-label="操作原因"]').setValue('当前草稿');await w.get('button[aria-label="选择操作原因"]').trigger('click');await w.get('button[aria-label="选择操作原因"]').trigger('click')
  await w.get('button[aria-label="删除操作原因 日常领用"]').trigger('click');await flushPromises()
  expect(mocks.removeReason).toHaveBeenCalledWith('日常领用');expect((w.get('input[aria-label="操作原因"]').element as HTMLInputElement).value).toBe('当前草稿');expect(mocks.request).not.toHaveBeenCalled();w.unmount()
 })
 it('replays legacy adjustment requests without converting their type or quantity',async()=>{
  const old={requestId:'legacy-adjust',materialId:'m1',type:'ADJUST_OUT',quantity:'0.5',unit:'米',reason:'历史盘点'}
  sessionStorage.setItem('jiugeng.material-stock.pending.stock-user.m1',JSON.stringify(old));mocks.request.mockResolvedValue({})
  const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises();await w.get('form').trigger('submit');await flushPromises()
  expect(JSON.parse(mocks.request.mock.calls[0]![1].body)).toEqual(old);w.unmount()
 })
 it('retries an unknown outcome using exactly the original payload',async()=>{mocks.request.mockRejectedValueOnce(new Error('network')).mockResolvedValue({});const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises();await w.get('input[inputmode="decimal"]').setValue('0.125');await w.get('input[aria-label="操作原因"]').setValue('合成测试');await w.get('form').trigger('submit');await flushPromises();const first=mocks.request.mock.calls[0]![1].body;expect(w.text()).toContain('待确认');expect(w.get('fieldset').attributes('disabled')).toBeDefined();await w.get('form').trigger('submit');await flushPromises();expect(mocks.request.mock.calls[1]![1].body).toBe(first);expect(JSON.parse(first).quantity).toBe('0.125');expect(sessionStorage.getItem('jiugeng.material-stock.pending.stock-user.m1')).toBeNull();w.unmount()})
 it('does not interpret a failed balance read as zero stock',async()=>{mocks.inventory.mockRejectedValue(new Error('offline'));const w=mount(MaterialInventoryDialog,{props:{material},global});await flushPromises();expect(w.text()).toContain('库存读取失败，不能据此记账');await w.get('form').trigger('submit');expect(mocks.request).not.toHaveBeenCalled();w.unmount()})
})
