import { mount,flushPromises } from '@vue/test-utils'
import { createRouter,createMemoryHistory } from 'vue-router'
import { it,expect,vi } from 'vitest'
import LoginView from './LoginView.vue'
const login=vi.hoisted(()=>vi.fn().mockResolvedValue(undefined))
vi.mock('@/state/session',()=>({session:{login,user:{value:{mustChangePassword:false}}}}))
vi.mock('@/config/runtime',()=>({runtimeConfig:{authMode:'password'}}))
vi.mock('@/api/auth',()=>({securitySettingsApi:{loginSettings:vi.fn().mockResolvedValue({staySignedInDays:7})}}))
it.each(['test-user','15500009901'])('submits username or phone through the same password form: %s',async identifier=>{
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/login',component:LoginView},{path:'/home',component:{template:'<p>Home</p>'}}]});await router.push('/login');await router.isReady()
 const w=mount(LoginView,{global:{plugins:[router]}});await flushPromises()
 expect(w.get('label[for="login-name"]').text()).toBe('用户名 / 手机号')
 await w.get('#login-name').setValue(identifier);await w.get('#password').setValue('Synthetic-Phone-42');await w.get('form').trigger('submit');await flushPromises()
 expect(login).toHaveBeenLastCalledWith({loginName:identifier,password:'Synthetic-Phone-42',staySignedIn:false});expect(router.currentRoute.value.path).toBe('/home');w.unmount()
})
