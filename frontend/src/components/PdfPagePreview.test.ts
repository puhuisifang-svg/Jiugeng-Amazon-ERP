import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ acquire: vi.fn(), release: vi.fn(), getPage: vi.fn() }))
vi.mock('@/utils/pdfPreviewPool', () => ({ acquirePdfPreview: mocks.acquire, withPdfRenderSlot: (_signal: AbortSignal, render: () => Promise<void>) => render() }))
import PdfPagePreview from './PdfPagePreview.vue'
const wrappers: ReturnType<typeof mount>[] = []
const page = (render: () => unknown) => ({ getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }), render, cleanup: vi.fn() })
beforeEach(() => {
  mocks.acquire.mockImplementation(() => ({ document: Promise.resolve({ getPage: mocks.getPage }), release: mocks.release }))
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D)
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks(); vi.clearAllMocks(); vi.unstubAllGlobals() })
it('cancels the obsolete render on rapid page change and retains the shared document', async () => {
  let reject!: (reason: Error) => void
  const old = new Promise<void>((_resolve, fail) => { reject = fail })
  const cancel = vi.fn(() => reject(new Error('cancelled')))
  mocks.getPage.mockResolvedValueOnce(page(() => ({ promise: old, cancel })))
  mocks.getPage.mockResolvedValueOnce(page(() => ({ promise: Promise.resolve(), cancel: vi.fn() })))
  const w = mount(PdfPagePreview, { props: { bytes: new Uint8Array([1]), page: 0 } }); wrappers.push(w)
  await flushPromises(); await w.setProps({ page: 1 }); await flushPromises()
  expect(cancel).toHaveBeenCalledTimes(1); expect(mocks.acquire).toHaveBeenCalledTimes(1)
  expect(w.find('[role=alert]').exists()).toBe(false)
  expect(w.attributes('aria-busy')).toBe('false')
})
it('retries a failed document in place without asking the parent to replace bytes', async () => {
  mocks.acquire.mockImplementationOnce(() => ({ document: Promise.reject(new Error('temporary')), release: mocks.release }))
  mocks.getPage.mockResolvedValue(page(() => ({ promise: Promise.resolve(), cancel: vi.fn() })))
  const bytes = new Uint8Array([1]), w = mount(PdfPagePreview, { props: { bytes, page: 0 } }); wrappers.push(w)
  await flushPromises(); expect(w.get('[role=alert]').text()).toContain('已保留')
  await w.get('button').trigger('click'); await flushPromises()
  expect(mocks.acquire).toHaveBeenNthCalledWith(2, bytes)
  expect(w.find('[role=alert]').exists()).toBe(false)
})

it('only renders nearby pages and releases offscreen canvases while keeping page geometry', async () => {
  let intersect!: (entries: { isIntersecting: boolean }[]) => void
  const disconnect = vi.fn()
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback: typeof intersect) { intersect = callback }
    observe() {} disconnect = disconnect
  })
  mocks.getPage.mockResolvedValue(page(() => ({ promise: Promise.resolve(), cancel: vi.fn() })))
  const w = mount(PdfPagePreview, { props: { bytes: new Uint8Array([1]), page: 0, lazy: true, size: { width: 600, height: 800 } } }); wrappers.push(w)
  await flushPromises(); expect(mocks.acquire).not.toHaveBeenCalled()
  intersect([{ isIntersecting: true }]); await flushPromises()
  expect(mocks.acquire).toHaveBeenCalledTimes(1)
  expect((w.get('canvas').element as HTMLCanvasElement).width).toBeGreaterThan(0)
  intersect([{ isIntersecting: false }]); await flushPromises()
  expect(mocks.release).toHaveBeenCalledTimes(1)
  expect((w.get('canvas').element as HTMLCanvasElement).width).toBe(0)
  expect(w.attributes('style')).toContain('aspect-ratio: 600 / 800')
  intersect([{ isIntersecting: true }]); await flushPromises()
  expect((w.get('canvas').element as HTMLCanvasElement).width).toBeGreaterThan(0)
})
