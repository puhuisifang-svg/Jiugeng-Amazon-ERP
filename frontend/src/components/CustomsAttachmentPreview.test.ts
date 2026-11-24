import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import CustomsAttachmentPreview from './CustomsAttachmentPreview.vue'
import { customsFilesApi, type CustomsAttachment } from '@/api/customsDocuments'
vi.mock('@/api/customsDocuments', () => ({ customsFilesApi: { blob: vi.fn(), spreadsheetPreview: vi.fn(), download: vi.fn() } }))
vi.mock('@/directives/singleSelect', () => ({ vSingleSelect: {} }))
const file: CustomsAttachment = { id: 'file', category: 'INVOICE', name: 'Synthetic.png', size: 30, mediaType: 'image/png', createdAt: '2026-11-24T00:00:00Z' }
const wrappers: ReturnType<typeof mount>[] = []
function setup(input = file) {
  const w = mount(CustomsAttachmentPreview, { props: { documentId: 'document', file: input }, global: { stubs: { Teleport: true, AccessDialog: { template: '<div><slot/><slot name="footer"/></div>' } } } })
  wrappers.push(w); return w
}
beforeEach(() => { vi.mocked(customsFilesApi.blob).mockResolvedValue(new Blob(['synthetic'], { type: 'image/png' })); vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:synthetic'); vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {}) })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks(); vi.clearAllMocks() })
it('previews an authenticated image, downloads only on request and releases its URL', async () => {
  const w = setup(); await flushPromises()
  expect(customsFilesApi.blob).toHaveBeenCalledWith('document', 'file', expect.any(AbortSignal))
  expect(w.get('img').attributes('src')).toBe('blob:synthetic'); expect(customsFilesApi.download).not.toHaveBeenCalled()
  await w.findAll('button').find(b => b.text() === '下载原文件')!.trigger('click')
  expect(customsFilesApi.download).toHaveBeenCalledWith('document', file)
  w.unmount(); wrappers.pop(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic')
})
it('does not recreate a preview after it closes with a pending request', async () => {
  let resolve!: (blob: Blob) => void
  vi.mocked(customsFilesApi.blob).mockImplementationOnce(() => new Promise(done => { resolve = done }))
  const w = setup(), signal = vi.mocked(customsFilesApi.blob).mock.calls[0]![2]!
  w.unmount(); wrappers.pop(); resolve(new Blob(['late'])); await flushPromises()
  expect(signal.aborted).toBe(true); expect(URL.createObjectURL).not.toHaveBeenCalled()
})
it('shows a retry for preview failures without starting a download', async () => {
  vi.mocked(customsFilesApi.blob).mockRejectedValueOnce(new Error('合成加载失败'))
  const w = setup(); await flushPromises(); expect(w.get('[role=alert]').text()).toContain('合成加载失败')
  await w.get('[role=alert] button').trigger('click'); await flushPromises()
  expect(w.find('img').exists()).toBe(true); expect(customsFilesApi.download).not.toHaveBeenCalled()
})
it('renders spreadsheet values as text and can change sheets', async () => {
  vi.mocked(customsFilesApi.spreadsheetPreview).mockResolvedValue({ truncated: true, sheets: [
    { name: '合成发票', columns: ['A'], rows: [['<script>not executed</script>']], truncated: false },
    { name: '合成明细', columns: ['A'], rows: [['100.00']], truncated: false },
  ] })
  const w = setup({ ...file, name: 'Synthetic.xlsx', mediaType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }); await flushPromises()
  expect(w.find('script').exists()).toBe(false); expect(w.get('td').text()).toBe('<script>not executed</script>')
  expect(w.get('.preview-truncated').text()).toContain('完整内容请下载查看')
  await w.get('select').setValue('1'); expect(w.get('td').text()).toBe('100.00')
  expect(customsFilesApi.blob).not.toHaveBeenCalled(); expect(customsFilesApi.download).not.toHaveBeenCalled()
})
