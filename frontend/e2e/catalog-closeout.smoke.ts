import { expect, test } from '@playwright/test'

// All requests use synthetic fixtures; these checks never reach business data.
const user = { id: 'synthetic-closeout', loginName: 'synthetic-closeout', displayName: '合成验收', role: 'ADMIN', status: 'ENABLED', mustChangePassword: false, capabilities: ['products.read', 'products.write', 'inventory.read', 'inventory.inbound', 'inventory.outbound', 'samples.read', 'samples.write', 'samples.remove'] }
const variant = { id: 'synthetic-v', skuCode: 'SYN-V', variantName: '合成子体 · 长名称与规格', status: 'ACTIVE', version: 0, attributes: [], suppliers: [], materials: [] }
const product = { id: 'synthetic-p', productCode: 'SYN-P', productName: '合成父体', nameZh: '合成父体', status: 'ACTIVE', version: 0, skuCount: 1, variants: [variant] }
const sample = { id: 'synthetic-s', productId: null, variantId: null, productName: '', skuCode: '', skuName: '合成样品 · 长名称与规格', locationCode: 'A-01-02', version: 0, updatedAt: '2026-11-20T00:00:00Z', specification: '40 × 30 × 20 cm', remark: '合成备注', imageSnapshotTaken: true, linkedStatus: 'MANUAL', imageSource: 'SNAPSHOT' }

for (const viewport of [{ width: 1280, height: 800 }, { width: 800, height: 600 }, { width: 360, height: 800 }]) {
  test(`stock and sample dialogs keep their actions accessible at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors: string[] = []
    const unexpected: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/v1/auth/me') return route.fulfill({ json: user })
      if (path === '/api/v1/products/page') return route.fulfill({ json: { items: [product], totalCount: 1, matchingSkuCount: 1, matchingVariantIds: [variant.id], balances: [{ variantId: variant.id, quantity: 8 }], page: 0, size: 20 } })
      if (path === '/api/v1/products/inventory/balances') return route.fulfill({ json: [{ variantId: variant.id, quantity: 8 }] })
      if (path === `/api/v1/products/inventory/${variant.id}/transactions`) return route.fulfill({ json: { items: [], total: 0, page: 0, size: 20 } })
      if (path === '/api/v1/samples/capabilities') return route.fulfill({ json: { closeoutVersion: 1 } })
      if (path === '/api/v1/samples/parents' || path === '/api/v1/samples/locations') return route.fulfill({ json: [] })
      if (path === '/api/v1/samples') return route.fulfill({ json: { items: [sample], totalCount: 1 } })
      if (path === `/api/v1/samples/${sample.id}`) return route.fulfill({ json: sample })
      if (path === `/api/v1/samples/${sample.id}/history`) return route.fulfill({ json: { items: [], total: 0 } })
      unexpected.push(path)
      return route.abort()
    })
    await page.addInitScript(account => {
      localStorage.setItem('jiugeng.session.user', JSON.stringify(account))
      localStorage.setItem('jiugeng.session.token', 'synthetic-ui-fixture')
    }, user)
    await page.goto('/products')
    await page.locator('.parent-toggle').click()
    await page.locator('.product-child-row .product-stock-link').click()
    const stock = page.getByRole('dialog', { name: '库存操作与流水' })
    await expect(stock).toBeVisible()
    await expect(stock.locator('.product-stock-balance')).toContainText('8')
    await expect(stock.getByRole('button', { name: '确认入库', exact: true })).toBeInViewport()
    await expect(stock.getByRole('button', { name: '关闭', exact: true })).toBeInViewport()
    await expect(stock.getByRole('button', { name: '出库', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`stock-${viewport.width}.png`) })
    await stock.getByRole('button', { name: '关闭', exact: true }).click()
    await expect(stock).toHaveCount(0)
    await page.goto('/samples')
    await page.getByRole('button', { name: sample.skuName, exact: true }).click()
    const detail = page.getByRole('dialog', { name: '样品详情' })
    await expect(detail).toContainText(sample.locationCode)
    await expect(detail.getByLabel('关闭', { exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`sample-detail-${viewport.width}.png`) })
    await detail.getByLabel('关闭', { exact: true }).click()
    await page.getByRole('button', { name: '新增样品', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '新增样品' })
    await editor.getByRole('tab', { name: '手动录入' }).click()
    await expect(editor.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
    await expect(editor.getByRole('button', { name: '取消', exact: true })).toBeInViewport()
    await expect(editor.getByRole('button', { name: '关闭样品编辑' })).toBeInViewport()
    await expect(editor.getByPlaceholder('输入未建立 SKU 的产品名称')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const bounds = await editor.boundingBox()
    expect(bounds!.width).toBeLessThanOrEqual(viewport.width)
    expect(bounds!.height).toBeLessThanOrEqual(viewport.height)
    await page.screenshot({ path: testInfo.outputPath(`sample-editor-${viewport.width}.png`) })
    expect(errors).toEqual([])
    expect(unexpected).toEqual([])
  })
}
