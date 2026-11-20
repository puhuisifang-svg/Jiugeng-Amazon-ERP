import { expect, test } from '@playwright/test'

// Synthetic fixtures only. Intercept all API requests; never write business data.
const user = { id: 'synthetic-products-ui', loginName: 'synthetic-products-ui', displayName: '合成数据验收', role: 'ADMIN', status: 'ENABLED', mustChangePassword: false, capabilities: ['products.read', 'products.write', 'products.export', 'products.delete', 'suppliers.read', 'materials.read', 'inventory.read'] }
const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><rect width="160" height="160" fill="white"/><path d="M30 53 80 30l50 23v60l-50 25-50-25Z" fill="#d8e5ee" stroke="#7395ae" stroke-width="2"/><path d="m30 53 50 25 50-25M80 78v60" fill="none" stroke="#7395ae" stroke-width="2"/></svg>'
const products = Array.from({ length: 8 }, (_, index) => ({
  id: `synthetic-p${index}`, productCode: `SYNTHETIC-P${index}`, productName: ['收纳盒组合 · UI 合成样例', '户外围栏垫 · UI 合成样例', '多功能保护套 · UI 合成样例'][index % 3],
  nameZh: 'UI 合成样例', status: 'ACTIVE', version: 0, skuCount: 2, supplierDetailsAvailable: false,
  imageAssetId: index === 2 ? undefined : `synthetic-image-${index}`, updatedAt: '2026-11-20T00:00:00Z',
  variants: [0, 1].map(child => ({ id: `synthetic-v${index}-${child}`, skuCode: `SYNTHETIC-SKU-${index}-${child}`, variantName: child ? '大号款 · 蓝灰色\n加厚耐用面料' : '标准款 · 浅灰色', status: child ? 'DISABLED' : 'ACTIVE', version: 0, imageAssetId: child ? undefined : `synthetic-image-${index}`, attributes: [], suppliers: [], materials: [], packageWeightGrams: 260, specification: '40 × 30 × 20 cm' })),
}))

for (const viewport of [{ width: 1280, height: 800 }, { width: 800, height: 600 }, { width: 360, height: 800 }]) {
  test(`product images lead the list and remain usable at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/v1/auth/me') return route.fulfill({ json: user })
      if (path === '/api/v1/products/page') return route.fulfill({ json: { items: products, totalCount: products.length, matchingSkuCount: products.length * 2, matchingVariantIds: products.flatMap(p => p.variants.map(v => v.id)), balances: [], page: 0, size: 20 } })
      if (path === '/api/v1/products') return route.fulfill({ json: products })
      if (path.startsWith('/api/v1/catalog-images/')) return route.fulfill({ contentType: 'image/svg+xml', body: path.includes('synthetic-image-1') ? svg.replace('width="160" height="160"', 'width="640" height="160" viewBox="0 0 160 160"') : svg })
      return route.abort()
    })
    await page.addInitScript(({ account }) => {
      localStorage.setItem('jiugeng.session.user', JSON.stringify(account))
      localStorage.setItem('jiugeng.session.token', 'synthetic-ui-fixture')
      localStorage.setItem(`jiugeng.product-management.columns.v2.${account.id}`, JSON.stringify(['sku', 'image', 'status', 'stock', 'dimensions', 'weight']))
    }, { account: user })
    await page.goto('/products')
    const table = page.locator('.sku-master-table')
    await expect(table.locator('thead th').nth(1)).toHaveText('图片')
    await expect(table.locator('thead th').nth(2)).toHaveText('品名')
    await page.locator('.parent-toggle').first().click()
    await expect(page.locator('.product-child-row')).toHaveCount(2)
    await page.getByRole('region', { name: '可滚动数据列表' }).evaluate(element => { element.scrollLeft = 0 })
    const firstImage = table.locator('.product-parent-row .catalog-image').first()
    await expect(firstImage).toBeVisible()
    await expect.poll(() => firstImage.evaluate(element => element instanceof HTMLImageElement && element.naturalWidth > 0)).toBe(true)
    const imageSize = await firstImage.boundingBox()
    expect(imageSize?.width).toBe(80)
    expect(imageSize?.height).toBe(80)
    const childImage = table.locator('.product-child-row .catalog-image').first()
    const childPlaceholder = table.locator('.product-child-row .product-image-empty').first()
    await expect(childImage).toBeVisible()
    const childSize = await childImage.boundingBox()
    const placeholderSize = await childPlaceholder.boundingBox()
    expect(childSize?.width).toBe(80)
    expect(childSize?.height).toBe(80)
    expect(childSize!.x - imageSize!.x).toBe(24)
    expect(placeholderSize!.x - imageSize!.x).toBe(24)
    await expect(table.locator('.product-child-label').first()).toHaveText('子体')
    const parentBackground = await table.locator('.product-parent-row td').first().evaluate(element => getComputedStyle(element).backgroundColor)
    const childBackground = await table.locator('.product-child-row td').first().evaluate(element => getComputedStyle(element).backgroundColor)
    expect(parentBackground).not.toBe(childBackground)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(page.locator('#workspace-pagination')).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`products-${viewport.width}.png`), fullPage: true })
    await table.locator('.product-parent-row .material-image-button').first().click()
    const previewDialog = page.getByRole('dialog', { name: /图片预览/ })
    await expect(previewDialog).toBeVisible()
    const previewBounds = await previewDialog.boundingBox()
    const closeBounds = await page.getByRole('button', { name: '关闭图片预览' }).boundingBox()
    expect(closeBounds!.x + closeBounds!.width).toBeLessThanOrEqual(previewBounds!.x + previewBounds!.width)
    expect(closeBounds!.y).toBeGreaterThanOrEqual(previewBounds!.y)
    await expect(previewDialog.getByRole('button', { name: '保存图片', exact: true })).toBeInViewport()
    const previewImage = previewDialog.locator('img')
    await expect(previewImage).toBeVisible()
    await expect(previewImage).toHaveCSS('object-fit', 'contain')
    const stageBounds = await previewDialog.locator('.image-preview-dialog__stage').boundingBox()
    const previewImageBounds = await previewImage.boundingBox()
    expect(previewImageBounds!.y).toBeGreaterThan(closeBounds!.y + closeBounds!.height)
    expect(previewImageBounds!.height).toBeLessThanOrEqual(stageBounds!.height)
    await page.screenshot({ path: testInfo.outputPath(`product-preview-${viewport.width}.png`) })
    await page.getByRole('button', { name: '关闭图片预览' }).click()
    await table.locator('.product-parent-row .material-image-button').nth(1).click()
    await expect(previewDialog).toBeVisible()
    await expect(previewDialog.locator('img')).toBeVisible()
    const wideImageBounds = await previewDialog.boundingBox()
    expect(wideImageBounds!.width).toBe(previewBounds!.width)
    expect(wideImageBounds!.height).toBe(previewBounds!.height)
    await page.keyboard.press('Escape')
    await expect(previewDialog).toHaveCount(0)
    const scroll = page.getByRole('region', { name: '可滚动数据列表' })
    await scroll.evaluate(element => { element.scrollLeft = element.scrollWidth; element.scrollTop = 120 })
    await expect(table.locator('thead th').last()).toBeInViewport()
    await expect(table.locator('.product-parent-row .row-detail-link').nth(1)).toBeInViewport()
    if (viewport.width === 360) await expect(table.locator('.sticky-action').first()).toHaveCSS('position', 'static')
    expect(errors).toEqual([])
  })
}
