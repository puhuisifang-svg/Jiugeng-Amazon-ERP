import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import { confirmAction, promptReason, settleConfirmation, uiConfirmation } from '@/state/uiConfirmation'
import UiConfirmationHost from './UiConfirmationHost.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ fullPath: '/synthetic' }) }))
afterEach(() => { settleConfirmation(null); vi.restoreAllMocks() })
function setup() {
  if (!HTMLDialogElement.prototype.showModal) HTMLDialogElement.prototype.showModal = function () { this.open = true }
  if (!HTMLDialogElement.prototype.close) HTMLDialogElement.prototype.close = function () { this.open = false }
  vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(function (this: HTMLDialogElement) { this.open = true })
  vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(function (this: HTMLDialogElement) { this.open = false })
  return mount(UiConfirmationHost, { attachTo: document.body, global: { stubs: { teleport: true } } })
}
it('requires an explicit choice, ignores backdrop clicks and cancels with Escape', async () => {
  const w = setup(); const result = confirmAction('确认停用合成记录？'); await flushPromises()
  expect(w.text()).toContain('确认停用合成记录？')
  await w.get('dialog').trigger('click'); expect(uiConfirmation.value).toBeDefined()
  await w.get('dialog').trigger('cancel'); expect(await result).toBe(false); w.unmount()
})
it('validates reasons, rejects concurrent prompts and resolves only once', async () => {
  const w = setup(); const result = promptReason('归档原因'); await flushPromises()
  expect(await confirmAction('重复提交')).toBe(false)
  await w.get('form').trigger('submit'); expect(uiConfirmation.value).toBeDefined()
  await w.get('input').setValue('  合成原因  '); await w.get('form').trigger('submit')
  expect(await result).toBe('合成原因'); expect(uiConfirmation.value).toBeUndefined(); w.unmount()
})
it('resolves cancellation on unmount and does not execute an abandoned action', async () => {
  const w = setup(); const result = confirmAction('确认？'); await flushPromises(); w.unmount(); expect(await result).toBe(false)
})
it('does not open an empty modal when a request is cancelled before rendering', async () => {
  const w = setup(); const result = confirmAction('确认？'); settleConfirmation(null); await flushPromises()
  expect(await result).toBe(false); expect(w.get('dialog').attributes('open')).toBeUndefined(); w.unmount()
})
