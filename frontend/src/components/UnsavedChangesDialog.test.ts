import { mount } from '@vue/test-utils'
import { beforeEach, expect, it } from 'vitest'
import UnsavedChangesDialog from './UnsavedChangesDialog.vue'
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})
it('focuses the safe choice, ignores backdrop and keeps inputs on Escape', async () => {
  const wrapper = mount(UnsavedChangesDialog, { attachTo: document.body })
  expect(document.activeElement).toBe(wrapper.get('.unsaved-keep').element)
  expect(wrapper.get('dialog').element.open).toBe(true)
  await wrapper.get('dialog').trigger('click')
  expect(wrapper.emitted('discard')).toBeUndefined()
  await wrapper.get('dialog').trigger('cancel')
  expect(wrapper.emitted('keep')).toHaveLength(1)
  expect(wrapper.emitted('discard')).toBeUndefined()
  wrapper.unmount()
})
it('discards only explicitly, supports context and restores focus', async () => {
  const opener = document.createElement('button'); document.body.append(opener); opener.focus()
  const wrapper = mount(UnsavedChangesDialog, { attachTo: document.body, props: { message: '已保存的模板不受影响。' } })
  expect(wrapper.text()).toContain('已保存的模板不受影响。')
  await wrapper.get('.unsaved-discard').trigger('click')
  expect(wrapper.emitted('discard')).toHaveLength(1)
  wrapper.unmount()
  expect(document.activeElement).toBe(opener)
  opener.remove()
})
