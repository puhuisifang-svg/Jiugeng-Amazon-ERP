import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ChangePasswordView from './ChangePasswordView.vue'
import ForgotPasswordView from './ForgotPasswordView.vue'
const mocks = vi.hoisted(() => ({ change: vi.fn(), refresh: vi.fn(), replace: vi.fn(), request: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: mocks.replace }) }))
vi.mock('@/state/session', () => ({ session: { refresh: mocks.refresh } }))
vi.mock('@/api/auth', () => ({ changePassword: mocks.change, passwordRecoveryApi: { request: mocks.request, confirm: vi.fn() } }))
beforeEach(() => { vi.clearAllMocks(); mocks.request.mockResolvedValue({ message: '已发送' }) })
it('accepts a valid letter-and-digit password and refreshes the first-login state after saving', async () => {
 const w=mount(ChangePasswordView)
 await w.get('#current-password').setValue('SyntheticOld42')
 await w.get('#new-password').setValue('SyntheticNew42')
 await w.get('#confirm-password').setValue('SyntheticNew42')
 expect((w.get('form').element as HTMLFormElement).checkValidity()).toBe(true)
 const pattern=new RegExp(`^(?:${w.get('#new-password').attributes('pattern')})$`)
 expect(pattern.test('123456')).toBe(false);expect(pattern.test('abcdef')).toBe(false);expect(pattern.test('Abc123')).toBe(true)
 await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.change).toHaveBeenCalledWith('SyntheticOld42','SyntheticNew42')
 expect(mocks.refresh).toHaveBeenCalledOnce();expect(mocks.replace).toHaveBeenCalledWith('/home');w.unmount()
})
it('keeps the form when confirmation differs and never submits',async()=>{
 const w=mount(ChangePasswordView);await w.get('#new-password').setValue('SyntheticNew42');await w.get('#confirm-password').setValue('SyntheticNew43');await w.get('form').trigger('submit');await flushPromises()
 expect(mocks.change).not.toHaveBeenCalled();expect(w.get('[role=alert]').text()).toContain('不一致');w.unmount()
})
it('accepts six numeric verification digits and valid passwords in password recovery',async()=>{
 const w=mount(ForgotPasswordView)
 await w.get('form').trigger('submit');await flushPromises()
 await w.get('#verification-code').setValue('123456');await w.get('#recovery-password').setValue('SyntheticNew42');await w.get('#recovery-confirmation').setValue('SyntheticNew42')
 for(const id of ['verification-code','recovery-password','recovery-confirmation']) expect((w.get(`#${id}`).element as HTMLInputElement).validity.patternMismatch).toBe(false)
 await w.get('#verification-code').setValue('abcdef');expect((w.get('#verification-code').element as HTMLInputElement).validity.patternMismatch).toBe(true);w.unmount()
})
