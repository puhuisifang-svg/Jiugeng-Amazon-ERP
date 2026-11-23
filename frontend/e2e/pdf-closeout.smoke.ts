import { expect, test, type Page, type Download } from '@playwright/test'
import { PDFDocument, degrees } from 'pdf-lib'
import JSZip from 'jszip'
import { readFile } from 'node:fs/promises'

const user = { id: 'synthetic-pdf-ui', loginName: 'synthetic-pdf-ui', displayName: '合成验收', role: 'ADMIN', status: 'ENABLED', mustChangePassword: false, capabilities: ['tools.pdf.use'] }

async function pdf() {
  const doc = await PDFDocument.create()
  for (const rotation of [0, 90, 180]) { const page = doc.addPage([306, 432]); page.setCropBox(10, 10, 286, 412); page.setRotation(degrees(rotation)); page.drawText('Synthetic label', { x: 30, y: 300, size: 14 }) }
  return Buffer.from(await doc.save())
}
async function download(page: Page) {
  const ready = page.waitForEvent('download')
  await page.locator('.pdf-actions .primary').click()
  return ready
}
async function bytes(file: Download) { const path = await file.path(); expect(path).not.toBeNull(); return readFile(path!) }
async function expandSettings(page: Page) {
  const toggle = page.locator('.pdf-settings-toggle')
  if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click()
}
for (const viewport of [{ width: 1280, height: 800 }, { width: 800, height: 600 }, { width: 360, height: 800 }]) {
  test(`PDF tools preserve names, output and compact controls at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors: string[] = [], unexpected: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', route => {
      if (new URL(route.request().url()).pathname === '/api/v1/auth/me') return route.fulfill({ json: user })
      unexpected.push(route.request().method()); return route.abort()
    })
    await page.addInitScript(account => { localStorage.setItem('jiugeng.session.user', JSON.stringify(account)); localStorage.setItem('jiugeng.session.token', 'synthetic-ui-fixture') }, user)
    await page.goto('/tools/pdf')
    const buffer = await pdf(), upload = { name: 'Synthetic.source.PDF', mimeType: 'application/pdf', buffer }
    await page.locator('input[type=file]').first().setInputFiles(upload)
    await expandSettings(page)
    await page.getByPlaceholder('例如：1-3,5,7-9').fill('1-2,2')
    await expect(page.locator('#pdf-split-summary')).toContainText('重复输出 1 页')
    const archive = await download(page)
    expect(archive.suggestedFilename()).toBe('Synthetic.source.zip')
    const zip = await JSZip.loadAsync(await bytes(archive))
    expect(Object.keys(zip.files)).toEqual(['Synthetic.source-001.pdf', 'Synthetic.source-002.pdf'])
    expect((await PDFDocument.load(await zip.file('Synthetic.source-001.pdf')!.async('uint8array'))).getPageCount()).toBe(2)
    // Open the native replacement for the page tool's select using its data-bound option.
    await page.locator('.pdf-settings select').selectOption('merge', { force: true })
    await expect(page.locator('.pdf-thumbnail canvas').first()).toBeVisible()
    await expect.poll(() => page.locator('.pdf-thumbnail canvas').first().evaluate(el => (el as HTMLCanvasElement).width)).toBeLessThanOrEqual(360)
    const extracted = await download(page)
    expect(extracted.suggestedFilename()).toBe(upload.name)
    expect((await PDFDocument.load(await bytes(extracted))).getPageCount()).toBe(2)

    await page.locator('.pdf-tabs button').nth(2).click()
    await page.locator('input[type=file]').first().setInputFiles(upload)
    await expandSettings(page)
    await expect(page.locator('.pdf-preview')).toHaveAttribute('aria-busy', 'false')
    await page.getByRole('button', { name: '＋文字', exact: true }).click()
    await page.locator('.layer-settings textarea').fill('中文合成标注\nSynthetic overlay')
    await expect.poll(() => page.locator('.pdf-text-canvas').evaluate(el => (el as HTMLCanvasElement).width)).toBeGreaterThan(0)
    await page.getByRole('button', { name: '复制', exact: true }).click()
    await expect(page.locator('.pdf-layer-list li')).toHaveCount(2)
    await page.getByRole('button', { name: '下移一层', exact: true }).click()
    await expect(page.locator('.pdf-actions .primary')).toBeInViewport()
    if (viewport.width <= 850) await page.locator('.pdf-settings-toggle').click()
    await expect(page.locator('.pdf-stage')).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`pdf-editor-${viewport.width}.png`) })
    const edited = await download(page)
    expect(edited.suggestedFilename()).toBe(upload.name)
    const output = await PDFDocument.load(await bytes(edited))
    expect(output.getPageCount()).toBe(3)
    expect(output.getPage(1).getRotation().angle).toBe(90)
    expect(output.getPage(0).node.Resources()?.toString()).toContain('/Image')

    const imageFixtures = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 64; canvas.height = 48
      const ctx = canvas.getContext('2d')!; ctx.fillStyle = '#4382bf'; ctx.fillRect(8, 8, 32, 24)
      return { png: canvas.toDataURL('image/png').split(',')[1]!, jpg: canvas.toDataURL('image/jpeg').split(',')[1]! }
    })
    const png = Buffer.from(imageFixtures.png, 'base64'), jpg = Buffer.from(imageFixtures.jpg, 'base64')
    await page.locator('.pdf-tabs button').nth(1).click()
    await page.locator('input[type=file]').first().setInputFiles([{ name: 'First.png', mimeType: 'image/png', buffer: png }, { name: 'Second.jpg', mimeType: 'image/jpeg', buffer: jpg }])
    await page.locator('.pdf-image-card').nth(1).getByRole('button', { name: '前移' }).click()
    const pictures = await download(page)
    expect(pictures.suggestedFilename()).toBe('Second.pdf')
    expect((await PDFDocument.load(await bytes(pictures))).getPageCount()).toBe(2)

    await page.locator('.pdf-tabs button').nth(3).click()
    await page.locator('input[type=file]').first().setInputFiles(upload)
    await expect(page.locator('.pdf-actions .primary')).toBeEnabled()
    await expect(page.locator('.pdf-preview')).toHaveAttribute('aria-busy', 'false')
    const label = await download(page)
    expect(label.suggestedFilename()).toBe(upload.name)
    const labels = await PDFDocument.load(await bytes(label))
    expect(labels.getPageCount()).toBe(3)
    for (const item of labels.getPages()) { expect(item.getWidth()).toBeCloseTo(100 * 72 / 25.4); expect(item.getHeight()).toBeCloseTo(100 * 72 / 25.4) }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(page.locator('.pdf-actions .primary')).toBeInViewport()
    await expect(page.locator('.pdf-preview')).toHaveAttribute('aria-busy', 'false')
    await page.screenshot({ path: testInfo.outputPath(`pdf-label-${viewport.width}.png`) })
    expect(unexpected).toEqual([]); expect(errors).toEqual([])
  })
}
