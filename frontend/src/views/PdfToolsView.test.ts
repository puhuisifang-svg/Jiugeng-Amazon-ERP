import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PDFDocument } from 'pdf-lib'
import PdfToolsView from './PdfToolsView.vue'
vi.mock('@/components/PdfPagePreview.vue', () => ({ default: { props: ['bytes','page'], template: '<div class="preview-stub"><slot /></div>' } }))
vi.mock('@/directives/singleSelect', () => ({ vSingleSelect: {} }))
const wrappers: ReturnType<typeof mount>[] = []
function setup() { const w = mount(PdfToolsView); wrappers.push(w); return w }
async function selectPdf(w: ReturnType<typeof mount>, name = 'Synthetic.pdf') {
  const doc = await PDFDocument.create(); doc.addPage([306,432]).drawText('Synthetic page 1'); doc.addPage([306,432]).drawText('Synthetic page 2')
  const bytes = await doc.save()
  const input = w.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [{ name, size:bytes.length, arrayBuffer: async () => bytes.buffer }] })
  await input.trigger('change'); await flushPromises()
}
beforeEach(() => { vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} }) })
afterEach(() => { wrappers.splice(0).forEach(w=>w.unmount()); vi.unstubAllGlobals() })
describe('PDF tools workspace', () => {
  it('keeps four tools together and disables export until a file is selected', () => {
    const w = setup()
    expect(w.findAll('.pdf-tabs button').map(b=>b.text())).toEqual(['拆分PDF','图片转PDF','编辑PDF文件','FBA外箱标转换'])
    expect(w.get('.primary').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('文件仅在当前浏览器处理')
  })
  it('shows range errors inline instead of starting a broken download', async () => {
    const w = setup(); await selectPdf(w)
    expect(w.text()).toContain('共2页')
    await w.get('input[placeholder="例如：1-3,5,7-9"]').setValue('3-9')
    await w.get('.primary').trigger('click'); await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('页码必须在1～2内')
    expect(w.get('.primary').attributes('disabled')).toBeUndefined()
  })
  it('keeps annotations attached to their page and allows removing a selected annotation', async () => {
    const w = setup()
    await w.findAll('.pdf-tabs button')[2]!.trigger('click')
    await selectPdf(w)
    await w.findAll('.pdf-edit-actions button')[0]!.trigger('click')
    expect(w.findAll('.pdf-layer')).toHaveLength(1)
    await w.find('.pdf-pagebar select').setValue('1')
    expect(w.findAll('.pdf-layer')).toHaveLength(0)
    await w.find('.pdf-pagebar select').setValue('0')
    await w.find('.layer-settings button').trigger('click')
    expect(w.findAll('.pdf-layer')).toHaveLength(0)
  })
  it('isolates uploaded PDFs, ranges, previews and current page between tools', async () => {
    const w = setup(); await selectPdf(w, 'Split.pdf')
    await w.get('input[placeholder="例如：1-3,5,7-9"]').setValue('2')
    await w.find('.pdf-pagebar select').setValue('1')
    for (const index of [1,2,3]) {
      await w.findAll('.pdf-tabs button')[index]!.trigger('click')
      expect(w.text()).not.toContain('Split.pdf')
      expect(w.find('.preview-stub').exists()).toBe(false)
      expect(w.get('.primary').attributes('disabled')).toBeDefined()
    }
    await selectPdf(w, 'Label.pdf')
    expect(w.find('.preview-stub').exists()).toBe(true)
    await w.findAll('.pdf-tabs button')[0]!.trigger('click')
    expect(w.text()).toContain('Split.pdf')
    expect(w.text()).not.toContain('Label.pdf')
    expect((w.get('input[placeholder="例如：1-3,5,7-9"]').element as HTMLInputElement).value).toBe('2')
    expect((w.get('.pdf-pagebar select').element as HTMLSelectElement).value).toBe('1')
    await w.findAll('.pdf-tabs button')[3]!.trigger('click')
    expect(w.text()).toContain('Label.pdf')
    expect(w.text()).toContain('转换后')
  })
  it('preserves editor annotations when another tool replaces its own file', async () => {
    const w = setup()
    await w.findAll('.pdf-tabs button')[2]!.trigger('click')
    await selectPdf(w, 'Edit.pdf')
    await w.findAll('.pdf-edit-actions button')[0]!.trigger('click')
    await w.get('textarea').setValue('保留的标注')
    await w.findAll('.pdf-tabs button')[0]!.trigger('click')
    await selectPdf(w, 'Other.pdf')
    await w.findAll('.pdf-tabs button')[2]!.trigger('click')
    expect(w.text()).toContain('Edit.pdf')
    expect(w.get('.pdf-layer').text()).toBe('保留的标注')
  })

  it('selects thumbnail pages, clears selection and resets it when replacing the PDF', async () => {
    const w = setup(); await selectPdf(w)
    await w.get('.pdf-settings select').setValue('merge')
    expect(w.findAll('.pdf-thumbnail')).toHaveLength(2)
    expect(w.get('.primary').text()).toContain('合并所选页面')
    await w.get('[aria-label="选择第1页"]').trigger('click')
    expect(w.get('[aria-label="选择第1页"]').attributes('aria-pressed')).toBe('false')
    expect((w.get('input[placeholder="例如：1-3,5,7-9"]').element as HTMLInputElement).value).toBe('2')
    await w.findAll('button').find(b => b.text() === '清空')!.trigger('click')
    await w.get('.primary').trigger('click'); await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('请输入页码')
    await selectPdf(w, 'Replacement.pdf')
    expect(w.findAll('.pdf-thumbnail.checked')).toHaveLength(2)
  })
  it('defaults image margins to two millimetres and rectangles to white fill with an outline option', async () => {
    const w = setup()
    await w.findAll('.pdf-tabs button')[1]!.trigger('click')
    expect((w.get('input[type=number]').element as HTMLInputElement).value).toBe('2')
    await w.findAll('.pdf-tabs button')[2]!.trigger('click'); await selectPdf(w)
    await w.findAll('.pdf-edit-actions button')[3]!.trigger('click')
    expect((w.get('input[type=color]').element as HTMLInputElement).value).toBe('#ffffff')
    expect((w.get('.pdf-layer.rectangle').element as HTMLElement).style.background).toBe('rgb(255, 255, 255)')
    await w.get('.layer-settings select').setValue('false')
    expect((w.get('.pdf-layer.rectangle').element as HTMLElement).style.background).toBe('')
  })

  it('resizes rectangles from corners without moving the opposite corner and clamps to the page', async () => {
    const w = setup(); await w.findAll('.pdf-tabs button')[2]!.trigger('click'); await selectPdf(w)
    await w.findAll('.pdf-edit-actions button')[3]!.trigger('click')
    vi.spyOn(w.get('.pdf-stage').element, 'getBoundingClientRect').mockReturnValue({ width: 600, height: 800 } as DOMRect)
    const pointer = (target: EventTarget, type: string, x: number, y: number) => {
      const e = new Event(type, { bubbles: true }); Object.assign(e, { button: 0, clientX: x, clientY: y }); target.dispatchEvent(e)
    }
    pointer(w.get('.pdf-resize-handle.se').element, 'pointerdown', 270, 152)
    pointer(window, 'pointermove', 330, 232); pointer(window, 'pointerup', 330, 232); await w.vm.$nextTick()
    const layer = w.get('.pdf-layer').element as HTMLElement
    expect(parseFloat(layer.style.left)).toBeCloseTo(10)
    expect(parseFloat(layer.style.width)).toBeCloseTo(45)
    expect(parseFloat(layer.style.height)).toBeCloseTo(19)
    pointer(w.get('.pdf-resize-handle.nw').element, 'pointerdown', 60, 80)
    pointer(window, 'pointermove', -600, -800); pointer(window, 'pointerup', -600, -800); await w.vm.$nextTick()
    expect(parseFloat(layer.style.left)).toBe(0)
    expect(parseFloat(layer.style.top)).toBe(0)
    expect(parseFloat(layer.style.width)).toBeCloseTo(55)
    pointer(window, 'pointermove', 400, 400); await w.vm.$nextTick()
    expect(parseFloat(layer.style.width)).toBeCloseTo(55)
  })

})
