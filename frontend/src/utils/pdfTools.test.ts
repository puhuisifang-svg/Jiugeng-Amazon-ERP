// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { PDFDocument, degrees, rgb } from 'pdf-lib'
import { ensurePrintable, imagesToPdf, loadPdf, MM, overlayPdf, pageGroups, splitPdf, squareLabels } from './pdfTools'
const png = Uint8Array.from(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'))
async function fixture() {
  const doc = await PDFDocument.create()
  for (const rotation of [0,90,180,270]) { const page = doc.addPage([306,432]); page.setRotation(degrees(rotation)); page.drawRectangle({x:10,y:20,width:100,height:200,color:rgb(1,0,0)}) }
  return doc.save()
}
describe('browser PDF tools', () => {
  it('validates ranges and keeps requested order, including Chinese separators', () => {
    expect(pageGroups('3-4，1', 4)).toEqual([[2,3],[0]])
    for (const input of ['', '0', '3-1', '1-5', '1,,2', 'NaN', '1.5']) expect(() => pageGroups(input, 4)).toThrow()
  })
  it('splits actual pages without changing dimensions or rotation', async () => {
    const files = await splitPdf(await fixture(), [[2,0],[1]])
    const first = await PDFDocument.load(files[0]!)
    expect(first.getPageCount()).toBe(2)
    expect(first.getPage(0).getRotation().angle).toBe(180)
    expect(first.getPage(1).getSize()).toEqual({ width:306, height:432 })
    expect((await PDFDocument.load(files[1]!)).getPageCount()).toBe(1)
    await expect(splitPdf(await fixture(), [[4]])).rejects.toThrow()
  })
  it('converts every rotated page to exactly 100mm square, keeps vector content and disables print scaling', async () => {
    const doc = await PDFDocument.load(await squareLabels(await fixture(), 2))
    expect(doc.getPageCount()).toBe(4)
    for (const page of doc.getPages()) {
      expect(page.getWidth()).toBeCloseTo(100*MM, 6)
      expect(page.getHeight()).toBeCloseTo(100*MM, 6)
      expect(page.getRotation().angle).toBe(0)
      expect(page.node.Resources()?.toString()).toContain('/EmbeddedPdfPage')
    }
    expect(doc.catalog.getOrCreateViewerPreferences().getPrintScaling()).toBe('None')
    await expect(squareLabels(await fixture(), 51)).rejects.toThrow()
  })
  it('generates one page per image with selected paper, orientation and margin', async () => {
    const doc = await PDFDocument.load(await imagesToPdf([{bytes:png,format:'png'},{bytes:png,format:'png'}], 'a4', true, 5))
    expect(doc.getPageCount()).toBe(2)
    expect(doc.getPage(0).getWidth()).toBeCloseTo(297*MM)
    expect(doc.getPage(0).getHeight()).toBeCloseTo(210*MM)
    await expect(imagesToPdf([], 'a4', false, 0)).rejects.toThrow()
    await expect(imagesToPdf([{bytes:png,format:'png'}], 'square', false, NaN)).rejects.toThrow()
  })
  it('overlays only the selected page and preserves original rotation', async () => {
    const doc = await PDFDocument.load(await overlayPdf(await fixture(), [{page:1,png}]))
    expect(doc.getPageCount()).toBe(4)
    expect(doc.getPage(1).getRotation().angle).toBe(90)
    expect(doc.getPage(1).node.Resources()?.toString()).toContain('/Image')
    expect(doc.getPage(0).node.Resources()?.toString()).not.toContain('/Image')
  })
  it('rejects corrupt files and refuses to silently lose interactive fields', async () => {
    await expect(loadPdf(new Uint8Array([1,2,3]))).rejects.toThrow('PDF无法读取')
    const doc = await PDFDocument.create(); const page = doc.addPage()
    doc.getForm().createTextField('name').addToPage(page)
    expect(() => ensurePrintable(doc)).toThrow('交互表单')
    await expect(splitPdf(await doc.save(), [[0]])).rejects.toThrow('交互表单')
  })
})
