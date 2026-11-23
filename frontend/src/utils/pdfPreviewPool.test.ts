import { afterEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ getDocument: vi.fn(), destroy: vi.fn(async () => {}) }))
vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => ({ GlobalWorkerOptions: {}, getDocument: mocks.getDocument }))
import { acquirePdfPreview, withPdfRenderSlot } from './pdfPreviewPool'
afterEach(() => vi.clearAllMocks())
describe('PDF preview resources', () => {
  it('shares a document across thumbnails and frees it only after all leases end', async () => {
    mocks.getDocument.mockImplementation(() => ({ promise: Promise.resolve({}), destroy: mocks.destroy }))
    const bytes = new Uint8Array([1, 2, 3]), first = acquirePdfPreview(bytes), second = acquirePdfPreview(bytes)
    expect(mocks.getDocument).toHaveBeenCalledTimes(1)
    expect(mocks.getDocument.mock.calls[0]![0].data).not.toBe(bytes)
    first.release(); await Promise.resolve(); expect(mocks.destroy).not.toHaveBeenCalled()
    second.release(); const replacement = acquirePdfPreview(bytes); await Promise.resolve()
    expect(mocks.destroy).not.toHaveBeenCalled()
    replacement.release(); await Promise.resolve(); expect(mocks.destroy).toHaveBeenCalledTimes(1)
  })
  it('runs at most two renders and removes cancelled requests before decoding pages', async () => {
    const finish: (() => void)[] = [], controller = new AbortController(), cancelled = vi.fn()
    const hold = () => new Promise<void>(resolve => finish.push(resolve))
    const first = withPdfRenderSlot(new AbortController().signal, hold), second = withPdfRenderSlot(new AbortController().signal, hold)
    await Promise.resolve(); expect(finish).toHaveLength(2)
    const third = withPdfRenderSlot(controller.signal, cancelled)
    const rejected = expect(third).rejects.toMatchObject({ name: 'AbortError' })
    controller.abort(); await rejected
    finish.forEach(done => done()); await Promise.all([first, second])
    expect(cancelled).not.toHaveBeenCalled()
    await expect(withPdfRenderSlot(new AbortController().signal, async () => 'available')).resolves.toBe('available')
  })
})
