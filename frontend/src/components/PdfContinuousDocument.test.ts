import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import PdfContinuousDocument from './PdfContinuousDocument.vue'
vi.mock('./PdfPagePreview.vue', () => ({ default: { template: '<div><slot /></div>' } }))
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => { vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} }) })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.unstubAllGlobals() })
it('tracks scrolling without snapping back and navigates to a short final page', async () => {
  let callback: FrameRequestCallback | undefined
  vi.stubGlobal('requestAnimationFrame', (next: FrameRequestCallback) => { callback = next; return 1 })
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
  const w = mount(PdfContinuousDocument, { props: {
    bytes: new Uint8Array([1]), sizes: [{ width: 600, height: 800 }, { width: 800, height: 300 }, { width: 800, height: 200 }], page: 0, busy: false,
  } }); wrappers.push(w)
  const viewport = w.get('.pdf-document-scroll').element as HTMLElement
  Object.defineProperties(viewport, { clientHeight: { value: 600 }, scrollHeight: { value: 1300 } })
  w.findAll('.pdf-document-page').forEach((p, i) => Object.defineProperty(p.element, 'offsetTop', { value: [0, 600, 1000][i] }))
  viewport.scrollTop = 550
  await w.get('.pdf-document-scroll').trigger('scroll'); callback!(0)
  expect(w.emitted('update:page')?.at(-1)).toEqual([1])
  await w.setProps({ page: 1 })
  expect(viewport.scrollTop).toBe(550)
  viewport.scrollTop = 700
  await w.get('.pdf-document-scroll').trigger('scroll'); callback!(0)
  expect(w.emitted('update:page')?.at(-1)).toEqual([2])
  await w.setProps({ page: 2 })
  await w.get('[aria-label="前往第1页"]').trigger('click'); await flushPromises()
  expect(viewport.scrollTop).toBe(0)
  await w.setProps({ busy: true })
  await w.get('[aria-label="前往第3页"]').trigger('click')
  expect(viewport.scrollTop).toBe(0)
})

it('zooms only the document for Ctrl or Command wheel and leaves ordinary scrolling alone', async () => {
  const w = mount(PdfContinuousDocument, { props: { bytes: new Uint8Array([1]), sizes: [{ width: 600, height: 800 }], page: 0, busy: false } }); wrappers.push(w)
  const viewport = w.get('.pdf-document-scroll')
  const normal = new WheelEvent('wheel', { deltaY: -100, bubbles: true, cancelable: true })
  viewport.element.dispatchEvent(normal); await flushPromises()
  expect(normal.defaultPrevented).toBe(false)
  expect(w.get('output').text()).toBe('100%')
  const zoom = new WheelEvent('wheel', { deltaY: -100, ctrlKey: true, bubbles: true, cancelable: true })
  viewport.element.dispatchEvent(zoom); await flushPromises()
  expect(zoom.defaultPrevented).toBe(true)
  expect(w.get('output').text()).toBe('110%')
  viewport.element.dispatchEvent(new WheelEvent('wheel', { deltaY: 100, metaKey: true, bubbles: true, cancelable: true })); await flushPromises()
  expect(w.get('output').text()).toBe('100%')
  await w.setProps({ busy: true }); viewport.element.dispatchEvent(new WheelEvent('wheel', { deltaY: -100, ctrlKey: true, bubbles: true, cancelable: true })); await flushPromises()
  expect(w.get('output').text()).toBe('100%')
})
