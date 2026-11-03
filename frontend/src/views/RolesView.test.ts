import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import RolesView from './RolesView.vue'
import { usersApi, type AppUser } from '@/api/auth'
import { pageTitles } from '@/state/pageTitles'
import { rolesApi, type Role } from '@/api/roles'
vi.mock('@/api/auth', () => ({ usersApi: { list:vi.fn() } }))
vi.mock('@/api/roles', () => ({ rolesApi: { list:vi.fn(), permissions:vi.fn(), members:vi.fn(), history:vi.fn(), preview:vi.fn(), save:vi.fn() } }))
vi.mock('@/state/uiConfirmation',()=>({confirmAction:vi.fn().mockResolvedValue(true),promptReason:vi.fn()}))
const role:Role={id:'custom',name:'只读角色',description:'合成测试',kind:'CUSTOM',status:'ENABLED',version:2,permissions:['products.read'],memberCount:0,updatedAt:'2026-01-01T00:00:00Z'}
const wrappers:ReturnType<typeof mount>[]=[]
beforeEach(()=>{
 vi.clearAllMocks();HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')}
 vi.mocked(rolesApi.list).mockResolvedValue([role]);vi.mocked(rolesApi.members).mockResolvedValue([]);vi.mocked(rolesApi.history).mockResolvedValue([])
 vi.mocked(rolesApi.permissions).mockResolvedValue([{code:'products.read',label:'查看产品',module:'产品管理',dependencies:[],sensitive:false,assignable:true},{code:'products.write',label:'编辑产品',module:'产品管理',dependencies:['products.read'],sensitive:false,assignable:true}])
})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function setup(path = '/system/roles/custom'){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/system/roles/:id?',component:RolesView}]});await router.push(path);await router.isReady();const w=mount(RolesView,{global:{plugins:[router]}});wrappers.push(w);await flushPromises();return w}
describe('role authorization editor',()=>{
 it('excludes existing members and scopes the picker to the current role',async()=>{
  const member={id:'existing',loginName:'existing',displayName:'已有成员',roles:[{id:'custom',name:'只读角色',status:'ENABLED'}],status:'ENABLED'} as AppUser
  const candidate={id:'new',loginName:'new',displayName:'待添加用户',roles:[],status:'ENABLED'} as unknown as AppUser
  vi.mocked(rolesApi.members).mockResolvedValue([member])
  vi.mocked(usersApi.list).mockResolvedValue([member,candidate])
  const w=await setup('/system/roles/custom?tab=members')
  expect(w.text()).not.toContain('分配或移除角色')
  expect(w.findAll('button').some(b=>b.text()==='移除成员')).toBe(true)
  await w.get('.role-member-add').trigger('click');await flushPromises()
  expect(w.get('.member-picker-list').text()).not.toContain('已有成员')
  expect(w.get('.member-picker-list').text()).toContain('待添加用户')
  await w.get('.member-picker-select').trigger('click');await flushPromises()
  expect(w.get('.assignment-summary').text()).toContain('本次仅添加「只读角色」')
  expect(w.find('.assignment-dialog input[type=checkbox]').exists()).toBe(false)
 })

 it('separates role basics and previews edits from both tabs together',async()=>{
  const w=await setup();expect(w.find('.role-profile-fields').exists()).toBe(false)
  await w.get('[title="products.write"] input').setValue(true)
  await w.get('#role-tab-basics').trigger('click')
  await w.get('.role-profile-fields input').setValue('采购专员')
  await w.get('.role-profile-fields textarea').setValue('负责采购')
  expect(w.find('.permission-browser').exists()).toBe(false)
  await w.get('#role-tab-permissions').trigger('click')
  expect((w.get('[title="products.write"] input').element as HTMLInputElement).checked).toBe(true)
  vi.mocked(rolesApi.preview).mockResolvedValue({added:['products.write'],removed:[],members:[]})
  await w.get('.role-save-primary').trigger('click');await flushPromises()
  expect(w.get('.role-basic-diff').text()).toContain('采购专员')
  expect(w.get('.role-basic-diff').text()).toContain('负责采购')
  expect(rolesApi.preview).toHaveBeenCalledWith('custom',expect.objectContaining({name:'采购专员',description:'负责采购',permissions:['products.read','products.write']}))
  await w.findAll('dialog button').find(b=>b.text()==='返回修改')!.trigger('click')
  expect(rolesApi.save).not.toHaveBeenCalled()
  await w.get('#role-tab-basics').trigger('click')
  expect((w.get('.role-profile-fields input').element as HTMLInputElement).value).toBe('采购专员')
 })
 it('allows permission changes without a reason and disables save after reverting',async()=>{
  const w=await setup();const save=w.get('.role-save-primary')
  expect(save.attributes('disabled')).toBeDefined()
  await w.get('[title="products.write"] input').setValue(true)
  expect(save.attributes('disabled')).toBeUndefined()
  await w.get('[title="products.write"] input').setValue(false)
  expect(save.attributes('disabled')).toBeDefined()
  await w.get('[title="products.read"] input').setValue(false)
  expect(save.attributes('disabled')).toBeUndefined()
  vi.mocked(rolesApi.preview).mockResolvedValue({added:[],removed:['products.read'],members:[]})
  await save.trigger('click');await flushPromises()
  expect(rolesApi.preview).toHaveBeenCalledWith('custom',expect.objectContaining({reason:'',permissions:[]}))
  vi.mocked(rolesApi.save).mockResolvedValue(role)
  await w.findAll('dialog button').find(b=>b.text()==='确认保存')!.trigger('click');await flushPromises()
  expect(rolesApi.save).toHaveBeenCalledWith('custom',expect.objectContaining({reason:''}))
 })
 it('uses Create in the new role dialog',async()=>{
  const w=await setup('/system/roles');await w.findAll('button').find(b=>b.text()==='新增角色')!.trigger('click')
  expect(w.get('button[form="create-role"]').text()).toBe('创建')
 })
 it('filters by nested menu and search without discarding selections',async()=>{
  const w=await setup();await w.get('[aria-label="展开商品"]').trigger('click')
  const menu=w.findAll('.permission-menu-children button').find(b=>b.text().startsWith('产品管理'))!
  await menu.trigger('click');expect(w.get('.permission-content-heading').text()).toContain('商品 / 产品管理')
  await w.get('[aria-label="搜索权限"]').setValue('编辑产品');expect(w.findAll('.permission-row')).toHaveLength(1)
  await w.get('[title="products.write"] input').setValue(true)
  await w.get('[aria-label="清空权限搜索"]').trigger('click');expect(w.findAll('.permission-row')).toHaveLength(2)
  expect((w.get('[title="products.write"] input').element as HTMLInputElement).checked).toBe(true)
  await w.get('[aria-label="收起商品"]').trigger('click');expect(w.findAll('.permission-menu-children button').some(b=>b.text().startsWith('产品管理'))).toBe(false)
 })
 it('allows editing preset business roles and shows save controls',async()=>{
  vi.mocked(rolesApi.list).mockResolvedValue([{...role,id:'SELECTION',kind:'BUILTIN'}]);const w=await setup('/system/roles/SELECTION')
  expect(w.get('[title="products.write"] input').attributes('disabled')).toBeUndefined()
  await w.get('[title="products.write"] input').setValue(true)
  expect(w.find('.role-reason').exists()).toBe(true)
  expect(w.find('.role-savebar').exists()).toBe(true)
 })
 it('pins admin first and permits deletion only of unassigned business roles',async()=>{
  vi.mocked(rolesApi.list).mockResolvedValue([{...role,id:'SELECTION',kind:'BUILTIN'}, {...role,id:'PRODUCT_MATERIAL',kind:'BUILTIN',memberCount:1}, {...role,id:'ADMIN',name:'系统管理员',kind:'BUILTIN'}])
  const w=await setup('/system/roles');const rows=w.findAll('tbody tr')
  expect(rows[0]!.text()).toContain('系统管理员');expect(rows[0]!.text()).not.toContain('删除')
  expect(rows[1]!.text()).not.toContain('编辑');expect(rows[1]!.text()).toContain('详情');expect(rows[1]!.findAll('button').find(b=>b.text()==='删除')!.attributes('disabled')).toBeUndefined()
  expect(rows[2]!.findAll('button').find(b=>b.text()==='删除')!.attributes('disabled')).toBeDefined()
 })
 it('uses a stable detail breadcrumb and keyboard accessible tabs without discarding edits',async()=>{
  const w=await setup(); expect(pageTitles['/system/roles/custom']).toBe('详情')
  await w.get('.role-reason input').setValue('保留草稿内容')
  await w.get('#role-tab-permissions').trigger('keydown',{key:'ArrowRight'})
  expect(w.get('[role=tabpanel]').attributes('aria-labelledby')).toBe('role-tab-members')
  expect(w.get('#role-tab-members').attributes('aria-selected')).toBe('true')
  await w.get('#role-tab-permissions').trigger('click')
  expect((w.get('.role-reason input').element as HTMLInputElement).value).toBe('保留草稿内容')
 })
 it('previews dependency-closed changes before saving and retains edits after conflict',async()=>{
  const w=await setup();await w.get('[title="products.write"] input').setValue(true)
  await w.get('.role-reason input').setValue('合成测试变更')
  vi.mocked(rolesApi.preview).mockResolvedValue({added:['products.write'],removed:[],members:[]})
  await w.findAll('button').find(b=>b.text() === '保存变更')!.trigger('click');await flushPromises()
  expect(rolesApi.preview).toHaveBeenCalledWith('custom',expect.objectContaining({version:2,permissions:['products.read','products.write']}))
  expect(rolesApi.save).not.toHaveBeenCalled()
  vi.mocked(rolesApi.save).mockRejectedValue(new Error('角色已更新，请重新加载后确认'))
  await w.findAll('dialog button').find(b=>b.text().includes('确认'))!.trigger('click');await flushPromises()
  expect(w.text()).toContain('角色已更新')
  expect((w.get('[title="products.write"] input').element as HTMLInputElement).checked).toBe(true)
  expect((w.get('.role-reason input').element as HTMLInputElement).value).toBe('合成测试变更')
 })
 it('removes dependent edits when read permission is removed',async()=>{
  const w=await setup();await w.get('[title="products.write"] input').setValue(true);await w.get('[title="products.read"] input').setValue(false);await flushPromises()
  expect((w.get('[title="products.write"] input').element as HTMLInputElement).checked).toBe(false)
 })
 it('keeps the system administrator protected',async()=>{
  vi.mocked(rolesApi.list).mockResolvedValue([{...role,id:'ADMIN',kind:'BUILTIN'}]);const w=await setup('/system/roles/ADMIN')
  expect(w.get('[title="products.write"] input').attributes('disabled')).toBeDefined()
  expect(w.find('.role-reason').exists()).toBe(false)
 })
})
