import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImagePreviewDialog from './ImagePreviewDialog.vue'

const wrappers: ReturnType<typeof mount>[] = []
const originalShowModal = HTMLDialogElement.prototype.showModal
const originalClose = HTMLDialogElement.prototype.close
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) { this.open = true })
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) { this.open = false })
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  HTMLDialogElement.prototype.showModal = originalShowModal
  HTMLDialogElement.prototype.close = originalClose
  vi.restoreAllMocks()
})
function setup() {
  const wrapper = mount(ImagePreviewDialog, {
    attachTo: document.body,
    props: { title: '合成图片预览', description: '合成长名称图片' },
    slots: { default: '<img src="data:image/svg+xml,%3Csvg/%3E" alt="合成图片">', actions: '<button type="button">保存图片</button>' },
    global: { stubs: { Teleport: true } },
  })
  wrappers.push(wrapper)
  return wrapper
}
describe('shared image preview', () => {
  it('opens a native modal and returns focus to the triggering control when removed', () => {
    const trigger = document.createElement('button')
    document.body.append(trigger)
    trigger.focus()
    const wrapper = setup()
    expect(wrapper.get<HTMLDialogElement>('dialog').element.open).toBe(true)
    wrapper.get<HTMLButtonElement>('[aria-label="关闭图片预览"]').element.focus()
    wrapper.unmount(); wrappers.pop()
    expect(document.activeElement).toBe(trigger)
    trigger.remove()
  })
  it('requests closing from the header, Escape and backdrop but not the image or save action', async () => {
    const wrapper = setup()
    await wrapper.get('img').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '保存图片')!.trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()
    await wrapper.get('[aria-label="关闭图片预览"]').trigger('click')
    await wrapper.get('dialog').trigger('cancel')
    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(3)
  })
})
