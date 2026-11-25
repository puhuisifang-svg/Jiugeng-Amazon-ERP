import { expect, test } from '@playwright/test'

const user = { id: 'synthetic-operations', loginName: 'synthetic-operations', displayName: '合成验收', role: 'ADMIN', status: 'ENABLED', mustChangePassword: false, capabilities: ['materials.read', 'materials.write', 'materials.delete', 'suppliers.read', 'suppliers.write', 'suppliers.delete', 'inventory.read', 'inventory.inbound', 'inventory.outbound', 'inventory.write', 'costs.read', 'costs.write', 'products.read', 'purchases.read', 'purchases.write', 'purchases.submit', 'purchases.receive', 'purchases.cancel'] }
const material = { id: 'syn-m', materialCode: 'SYN-M', name: '合成辅料', materialType: '包装', unit: '个', status: 'ACTIVE', version: 1, specification: '合成规格', updatedAt: '2026-11-20T00:00:00Z' }
const supplier = { id: 'syn-s', name: '合成供应商', version: 1, status: 'ACTIVE', categories: ['MATERIAL'], remark: '合成备注' }
const order = { id: 'syn-o', orderNo: 'SYN-PO', orderType: 'MATERIAL', supplierId: supplier.id, supplierName: supplier.name, status: 'SUBMITTED', currency: 'CNY', version: 1, createdAt: '2026-11-20T00:00:00Z', updatedAt: '2026-11-20T00:00:00Z', lines: [{ id: 'syn-l', lineNo: 1, itemId: material.id, itemName: material.name, itemCode: material.materialCode, unit: material.unit, orderedQuantity: 10, receivedQuantity: 0, remainingQuantity: 10, unitPrice: 0.1234, snapshotSaved: true }] }

for (const viewport of [{ width: 1280, height: 800 }, { width: 800, height: 600 }, { width: 360, height: 800 }]) {
  test(`material, supplier and purchase controls remain accessible at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors: string[] = [], unexpected: string[] = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/v1/auth/me') return route.fulfill({ json: user })
      if (path.endsWith('/manage/capabilities')) return route.fulfill({ json: { closeoutVersion: 1 } })
      if (path === '/api/v1/materials/manage/page') return route.fulfill({ json: { items: [{ material, quantity: 8, safetyStock: 10, initialized: true, netPrice: '0.1234', priceUnit: '个' }], totalCount: 1 } })
      if (path === '/api/v1/materials/type-options') return route.fulfill({ json: ['包装'] })
      if (path === '/api/v1/materials/unit-options') return route.fulfill({ json: ['个'] })
      if (path === '/api/v1/materials/manage/syn-m') return route.fulfill({ json: { material, price: { netPrice: '5.600000', unit: material.unit, version: 1 }, suppliers: [{ supplierId: supplier.id, supplierName: supplier.name, status: 'ACTIVE', materialEligible: true, primary: true }], unitLocked: true, quantityScale: 0 } })
      if (path === '/api/v1/materials/manage/syn-m/inventory') return route.fulfill({ json: { balance: { materialId: material.id, materialName: material.name, quantity: 8, safetyStock: 10, unit: '个' }, ledger: { items: [], totalCount: 0 } } })
      if (path === '/api/v1/suppliers') return route.fulfill({ json: { items: [supplier], totalCount: 1 } })
      if (path === '/api/v1/suppliers/syn-s') return route.fulfill({ json: supplier })
      if (path === '/api/v1/suppliers/manage/duplicates') return route.fulfill({ json: [] })
      if (path === '/api/v1/purchases/manage/orders') return route.fulfill({ json: { items: [order], total: 1, page: 0, size: 20 } })
      if (path === '/api/v1/purchases/manage/counts') return route.fulfill({ json: { MATERIAL: 1 } })
      if (path === '/api/v1/purchases/orders/syn-o') return route.fulfill({ json: order })
      if (path === '/api/v1/purchases/manage/syn-o/checks' || path === '/api/v1/purchases/manage/syn-o/shipments' || path === '/api/v1/purchases/delivery-options') return route.fulfill({ json: [] })
      unexpected.push(`${route.request().method()} ${path}`)
      return route.abort()
    })
    await page.addInitScript(account => {
      localStorage.setItem('jiugeng.session.user', JSON.stringify(account))
      localStorage.setItem('jiugeng.session.token', 'synthetic-ui-fixture')
    }, user)

    await page.goto('/materials')
    await expect(page.getByText(material.name, { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '添加辅料', exact: true }).click()
    const materialEditor = page.getByRole('dialog', { name: '新增辅料', exact: true })
    await expect(materialEditor.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
    await expect(materialEditor.getByRole('button', { name: '取消', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`material-editor-${viewport.width}.png`) })
    await materialEditor.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: `${material.name}更多操作`, exact: true }).click()
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    const editMaterial = page.getByRole('dialog', { name: '编辑辅料', exact: true })
    await expect(editMaterial.getByLabel('辅料成本价')).toHaveValue('5.60')
    const note = editMaterial.getByRole('button', { name: '备注/采购链接', exact: true })
    await expect(note).toHaveCSS('color', 'rgb(33, 131, 74)')
    await expect(editMaterial.getByRole('button', { name: '解除', exact: true })).toHaveCSS('color', 'rgb(180, 35, 24)')
    const spec = await editMaterial.getByLabel('规格', { exact: true }).boundingBox()
    expect(spec!.height).toBe(40)
    if (viewport.width > 700) {
      const unit = await editMaterial.getByRole('combobox', { name: '辅料单位', exact: true }).boundingBox()
      expect(spec!.y).toBe(unit!.y)
    }
    await note.click()
    await expect(editMaterial.getByLabel('关系备注', { exact: true })).toBeVisible()
    await expect(editMaterial.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`material-edit-${viewport.width}.png`) })
    await editMaterial.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '库存', exact: true }).click()
    const stock = page.getByRole('dialog', { name: '辅料库存操作', exact: true })
    await expect(stock.getByRole('button', { name: '确认入库', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`material-stock-${viewport.width}.png`) })
    await stock.getByLabel('关闭', { exact: true }).click()

    await page.goto('/suppliers')
    await page.getByRole('button', { name: '新增供应商', exact: true }).click()
    const supplierEditor = page.getByRole('dialog', { name: '新增供应商', exact: true })
    await expect(supplierEditor.getByRole('button', { name: '保存供应商', exact: true })).toBeInViewport()
    await expect(supplierEditor.getByRole('button', { name: '取消', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`supplier-editor-${viewport.width}.png`) })
    await supplierEditor.getByRole('button', { name: '取消', exact: true }).click()

    await page.goto('/purchases/orders?type=MATERIAL')
    await page.getByRole('button', { name: '新建辅料采购单', exact: true }).click()
    const purchaseEditor = page.getByRole('dialog', { name: '新建采购单', exact: true })
    await expect(purchaseEditor.getByRole('button', { name: '创建并确认下单', exact: true })).toBeInViewport()
    expect(await purchaseEditor.locator('thead th').count()).toBe(await purchaseEditor.locator('tbody tr').first().locator('td').count())
    await page.screenshot({ path: testInfo.outputPath(`purchase-editor-${viewport.width}.png`) })
    await purchaseEditor.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '收货', exact: true }).click()
    const receipt = page.getByRole('dialog', { name: '确认采购收货', exact: true })
    await expect(receipt.getByRole('button', { name: '确认本次收货', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`purchase-receipt-${viewport.width}.png`) })
    await receipt.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('button', { name: '详情', exact: true }).click()
    await page.getByRole('button', { name: '发货批次 / 多运单', exact: true }).click()
    const shipments = page.getByRole('dialog', { name: '发货批次 / 多运单', exact: true })
    await shipments.getByRole('button', { name: '登记下一批发货', exact: true }).click()
    await expect(shipments.getByRole('button', { name: '保存发货批次', exact: true })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath(`purchase-shipments-${viewport.width}.png`) })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect(errors).toEqual([])
    expect(unexpected).toEqual([])
  })
}
