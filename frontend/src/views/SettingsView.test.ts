import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsView from './SettingsView.vue'
const h = vi.hoisted(() => ({ list: vi.fn(), get: vi.fn(), update: vi.fn(), change: vi.fn(), reset: vi.fn(), remove: vi.fn(), confirm: vi.fn(), defaultsGet: vi.fn(), defaultsSave: vi.fn(), admin: true }))
vi.mock('@/api/auth', () => ({ usersApi: { list: h.list, resetPassword: h.reset, remove: h.remove }, securitySettingsApi: { get: h.get, update: h.update }, userDefaultsApi: { get: h.defaultsGet, save: h.defaultsSave }, changePassword: h.change }))
vi.mock('@/state/session', () => ({ session: { canAccess: () => h.admin, user: { value: { id: 'self' } }, refresh: vi.fn() } }))
vi.mock('@/state/uiConfirmation', () => ({ confirmAction: h.confirm }))
vi.mock('@/config/runtime', () => ({ runtimeConfig: { authMode: 'password' } }))
const rows = Array.from({ length: 23 }, (_, i) => ({ id: `u${i}`, loginName: `synthetic-${i}`, displayName: 'Synthetic', role: 'ADMIN', status: 'ENABLED' }))
function view() { return mount(SettingsView, { global: { stubs: { TableViewport: { template: '<div><slot/></div>' }, SuccessToast: true, RouterLink: { template: '<a><slot/></a>' } } } }) }
function button(w: ReturnType<typeof view>, text: string) { return w.findAll('button').find(b => b.text() === text)! }
beforeEach(() => {
  vi.clearAllMocks(); h.admin = true; h.list.mockResolvedValue(rows); h.get.mockResolvedValue({ staySignedInDays: 7 }); h.confirm.mockResolvedValue(false)
  HTMLDialogElement.prototype.showModal = vi.fn(); HTMLDialogElement.prototype.close = vi.fn()
})
describe('settings compact actions and pagination', () => {
  it('loads and saves the default initial password in its own dialog',async()=>{
    h.defaultsGet.mockResolvedValue({initialPassword:'Synthetic123'});h.defaultsSave.mockResolvedValue({initialPassword:'Changed123'})
    const w=view();await flushPromises();await button(w,'用户初始密码').trigger('click');await flushPromises()
    expect((w.get('#settings-default-password').element as HTMLInputElement).value).toBe('Synthetic123')
    await w.get('#settings-default-password').setValue('Changed123');await w.get('form').trigger('submit');await flushPromises()
    expect(h.defaultsSave).toHaveBeenCalledWith('Changed123');expect(w.find('form').exists()).toBe(false);w.unmount()
  })
  it('uses a plain text duration field and rejects invalid days without a request', async () => {
    const w = view(); await flushPromises(); await button(w, '安全设置').trigger('click')
    expect(w.get('#session-duration-days').attributes('type')).toBe('text')
    expect(w.find('input[type=password]').exists()).toBe(false)
    for (const value of ['', '0', '31', '1.5', '1e1', 'abc']) {
      await w.get('#session-duration-days').setValue(value); await w.get('form').trigger('submit')
      expect(h.update).not.toHaveBeenCalled(); expect(w.text()).toContain('1 至 30 的整数')
    }
    w.unmount()
  })
  it('keeps only security actions without duplicate management content', async () => {
    const w = view(); await flushPromises()
    expect(w.find('table').exists()).toBe(false)
    expect(w.text()).not.toContain('用户管理')
    expect(w.text()).not.toContain('角色管理')
    expect(h.list).not.toHaveBeenCalled()
    w.unmount()
  })
  it('retains password inputs on validation and API errors; protects cancellation', async () => {
    const w = view(); await flushPromises(); await button(w, '修改密码').trigger('click')
    await w.get('#settings-current-password').setValue('Oldpass1')
    await w.get('#settings-new-password').setValue('Newpass1')
    await w.get('#settings-confirm-password').setValue('Mismatch1')
    await w.get('form').trigger('submit'); expect(h.change).not.toHaveBeenCalled()
    expect(w.get('dialog').text()).toContain('两次输入的新密码不一致')
    await w.get('#settings-confirm-password').setValue('Newpass1')
    h.change.mockRejectedValue(new Error('failed')); await w.get('form').trigger('submit'); await flushPromises()
    expect((w.get('#settings-new-password').element as HTMLInputElement).value).toBe('Newpass1')
    await button(w, '取消').trigger('click'); await flushPromises(); expect(w.find('form').exists()).toBe(true)
    h.confirm.mockResolvedValue(true); await button(w, '取消').trigger('click'); await flushPromises(); expect(w.find('form').exists()).toBe(false)
    w.unmount()
  })
  it('saves security policy and keeps admin actions hidden for ordinary users', async () => {
    const w = view(); await flushPromises(); await button(w, '安全设置').trigger('click')
    await w.get('#session-duration-days').setValue('14'); h.update.mockResolvedValue({ staySignedInDays: 14 })
    await w.get('form').trigger('submit'); await flushPromises()
    expect(h.update).toHaveBeenCalledWith(14); expect(w.text()).toContain('免登录期限：14 天'); expect(w.find('form').exists()).toBe(false); w.unmount()
    h.admin = false; const ordinary = view(); await flushPromises()
    expect(ordinary.text()).not.toContain('安全设置'); expect(ordinary.find('table').exists()).toBe(false); expect(ordinary.find('[aria-label="列表分页"]').exists()).toBe(false); ordinary.unmount()
  })
})
