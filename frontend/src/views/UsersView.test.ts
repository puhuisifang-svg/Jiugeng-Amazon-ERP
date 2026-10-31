import { mount,flushPromises } from '@vue/test-utils'
import { beforeEach,afterEach,describe,it,expect,vi } from 'vitest'
import UsersView from './UsersView.vue'
import { rolesApi } from '@/api/roles'
import { userDefaultsApi } from '@/api/auth'
vi.mock('@/api/auth',()=>({usersApi:{list:vi.fn().mockResolvedValue([{id:'u1',loginName:'test',displayName:'测试账号',status:'ENABLED',roles:[{id:'r1',name:'测试角色'}],capabilities:['products.read']}])},userDefaultsApi:{get:vi.fn()}}))
vi.mock('@/api/roles',()=>({rolesApi:{list:vi.fn().mockResolvedValue([{id:'r1',name:'测试角色',status:'ENABLED'}]),permissions:vi.fn().mockResolvedValue([{code:'products.read',label:'查看产品',module:'产品管理',dependencies:[],assignable:true}]),sources:vi.fn().mockResolvedValue({'products.read':[{id:'r1',name:'测试角色'}]}),userHistory:vi.fn(),createUser:vi.fn()}}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('@/state/session',()=>({session:{user:{value:{id:'admin'}}}}))
const wrappers:ReturnType<typeof mount>[]=[]
async function view(){const w=mount(UsersView,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'}}}});wrappers.push(w);await flushPromises();return w}
beforeEach(()=>{vi.clearAllMocks();HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')};vi.mocked(userDefaultsApi.get).mockResolvedValue({initialPassword:'Synthetic123'});vi.mocked(rolesApi.userHistory).mockImplementation(async(_id,page=1,size=10)=>({items:[{id:`e${page}`,action:'ACCOUNT_PROFILE_UPDATE',actorLoginName:'admin',actorDisplayName:'Synthetic',detail:`page-${page}`,beforeValue:null,afterValue:null,occurredAt:'2026-01-01T00:00:00Z'}],total:21,page,size}))})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
describe('user editor and detail',()=>{
 it('omits email on create and prefills an editable password',async()=>{
 const w=await view();await w.findAll('button').find(b=>b.text()==='新增用户')!.trigger('click');await flushPromises()
 expect(w.find('input[type=email]').exists()).toBe(false)
 expect((w.get('#access-user-password').element as HTMLInputElement).value).toBe('Synthetic123')
 await w.get('[aria-label="显示密码"]').trigger('click');expect(w.get('#access-user-password').attributes('type')).toBe('text')
 await w.get('#access-user-password').setValue('Override123');expect((w.get('#access-user-password').element as HTMLInputElement).value).toBe('Override123')
 })
 it('shares the menu permission browser and loads history one page at a time',async()=>{
 const w=await view();await w.findAll('button').find(b=>b.text()==='详情')!.trigger('click');await flushPromises()
 await w.get('#user-tab-permissions').trigger('click');expect(w.find('.permission-navigation').exists()).toBe(true)
 expect(w.get('.permission-row').text()).toContain('授权来源：测试角色');expect(w.get('.permission-row input').attributes('disabled')).toBeDefined()
 await w.get('#user-tab-history').trigger('click');await flushPromises();expect(rolesApi.userHistory).toHaveBeenLastCalledWith('u1',1,10)
 expect(w.find('dialog > footer .list-pagination').exists()).toBe(true)
 expect(w.find('.access-dialog-body .list-pagination').exists()).toBe(false)
 expect(w.get('dialog').classes()).toContain('access-dialog--paginated')
 expect(w.get('dialog > footer').text()).not.toContain('关闭')
 const paginations=w.findAllComponents({name:'ListPagination'});paginations.at(-1)!.vm.$emit('update:page',2);await flushPromises()
 expect(rolesApi.userHistory).toHaveBeenLastCalledWith('u1',2,10);expect(w.get('.user-history').text()).toContain('page-2')
 })
 it('shows history failure and allows retry',async()=>{
 const w=await view();await w.findAll('button').find(b=>b.text()==='详情')!.trigger('click');await flushPromises()
 vi.mocked(rolesApi.userHistory).mockRejectedValueOnce(new Error('加载失败'))
 await w.get('#user-tab-history').trigger('click');await flushPromises();expect(w.get('dialog').text()).toContain('加载失败')
 await w.get('dialog [role=alert] button').trigger('click');await flushPromises();expect(w.find('.user-history').exists()).toBe(true)
 })
})
