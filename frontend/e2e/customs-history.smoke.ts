import { expect, test } from '@playwright/test'

const user = { id: 'synthetic-hs-ui', loginName: 'synthetic-hs-ui', displayName: '合成验收', role: 'ADMIN', status: 'ENABLED', mustChangePassword: false, capabilities: ['customers.read', 'customers.write', 'products.read'] }
const fields = [{ key: 'brandType', label: '品牌类型' }, { key: 'composition', label: '成分' }, { key: 'other', label: '其他' }]
const customs = { chinaCode: '6307909000', schemaCode: '6307909000', elementFields: fields, nameZh: '当前品名', elements: { brandType: '当前品牌', composition: '', other: '' }, measurementUnit: '千克', state: 'DRAFT', version: 7 }
const customer = { id: 'synthetic-customer', name: '合成客户', defaultCustomer: true, status: 'ACTIVE', version: 0 }
const product = { id: 'synthetic-current', customerId: customer.id, customerName: customer.name, customerStatus: 'ACTIVE', productId: 'synthetic-product', variantId: 'synthetic-variant', productName: '合成当前商品', skuCode: 'SYN-CURRENT', supplyPrice: null, priceUnit: '件', status: 'ACTIVE', version: 7, customs }
const history = Array.from({ length: 21 }, (_, index) => ({ id: `synthetic-source-${index}`, customerName: '合成来源客户', productName: `合成来源商品 ${index + 1}`, skuCode: `SYN-SOURCE-${index}`, status: 'ACTIVE', updatedAt: '2026-11-24T00:00:00Z', customs: { ...customs, nameZh: `历史品名 ${index + 1}`, version: 99, state: 'REVIEWED', elements: { brandType: '历史品牌', composition: '合成成分', other: '' } } }))
for (const viewport of [{ width: 1280, height: 800 }, { width: 800, height: 600 }, { width: 360, height: 800 }]) {
  test(`HS history confirms a copy and saves only the destination at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors: string[] = [], unexpected: string[] = [], writes: { path: string; body: typeof product }[] = []
    let failHistory = false, queries = 0
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', route => {
      const request = route.request(), url = new URL(request.url()), path = url.pathname
      if (path === '/api/v1/auth/me') return route.fulfill({ json: user })
      if (request.method() === 'PUT' && path === `/api/v1/customers/products/${product.id}`) {
        const body = request.postDataJSON(); writes.push({ path, body }); return route.fulfill({ json: { ...product, ...body } })
      }
      if (request.method() !== 'GET') { unexpected.push(`${request.method()} ${path}`); return route.abort() }
      if (path === '/api/v1/customers') return route.fulfill({ json: { items: [customer], totalCount: 1 } })
      if (path === '/api/v1/customers/products') return route.fulfill({ json: { items: [product], totalCount: 1 } })
      if (path === '/api/v1/products/synthetic-product') return route.fulfill({ json: { id: product.productId, variants: [] } })
      if (path === '/api/v1/customers/products/customs-history') {
        queries++
        expect(url.searchParams.get('excludeId')).toBe(product.id)
        if (failHistory) { failHistory = false; return route.fulfill({ status: 503, json: { detail: '合成查询失败，请重试' } }) }
        const offset = Number(url.searchParams.get('page')) * 20
        return route.fulfill({ json: { items: history.slice(offset, offset + 20), totalCount: 21, page: offset / 20, size: 20 } })
      }
      if (path.startsWith('/api/v1/hs-declaration-schemas/')) return route.fulfill({ json: { code: '6307909000', fields, measurementUnit: '千克', sourceUrl: '', stale: false } })
      unexpected.push(path); return route.abort()
    })
    await page.addInitScript(account => { localStorage.setItem('jiugeng.session.user', JSON.stringify(account)); localStorage.setItem('jiugeng.session.token', 'synthetic-ui-fixture') }, user)
    await page.goto('/customers/products')
    await page.getByRole('button', { name: '报关资料', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '编辑客户商品', exact: true }), dialog = page.getByRole('dialog', { name: '核对历史报关资料', exact: true })
    await expect(editor).toBeVisible(); expect(queries).toBe(0)
    const currentName = editor.locator('.customs-fields').getByLabel('品名', { exact: true })
    await editor.getByRole('button', { name: '查看同 HS 历史资料' }).click()
    await expect(dialog).toBeVisible()
    const trigger = dialog.getByRole('combobox', { name: '历史资料来源' })
    await expect(trigger).toBeVisible()
    const native = await dialog.locator('.history-source-picker select').evaluate(el => ({ opacity: getComputedStyle(el).opacity, position: getComputedStyle(el).position, insideApp: !!el.closest('#app') }))
    expect(native).toEqual({ opacity: '0', position: 'absolute', insideApp: true })
    await trigger.click(); await page.getByRole('option', { name: '合成来源商品 2 · 合成来源客户 · SYN-SOURCE-1', exact: true }).click()
    await expect(dialog.getByLabel('品名', { exact: true })).toHaveValue('历史品名 2')
    await dialog.getByRole('button', { name: '下一页', exact: true }).click()
    await expect(dialog.getByLabel('品名', { exact: true })).toHaveValue('历史品名 21')
    await expect(dialog.getByRole('button', { name: '确认带入', exact: true })).toBeDisabled()
    await dialog.getByRole('button', { name: '暂不带入', exact: true }).click()
    await expect(currentName).toHaveValue('当前品名'); expect(writes).toHaveLength(0)
    failHistory = true
    await editor.getByRole('button', { name: '查看同 HS 历史资料' }).click()
    await expect(editor.getByRole('alert')).toContainText('合成查询失败')
    await editor.getByRole('button', { name: '查看同 HS 历史资料' }).click()
    await expect(dialog).toBeVisible()
    await dialog.getByLabel('已核对，可带入').check()
    await dialog.getByLabel('品名', { exact: true }).fill('核对后的当前品名')
    await expect(dialog.getByLabel('已核对，可带入')).not.toBeChecked()
    await dialog.getByLabel('已核对，可带入').check()
    await expect(dialog.getByRole('button', { name: '确认带入', exact: true })).toBeInViewport()
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await dialog.screenshot({ path: testInfo.outputPath(`hs-history-${viewport.width}.png`) })
    await dialog.getByRole('button', { name: '确认带入', exact: true }).click()
    await expect(currentName).toHaveValue('核对后的当前品名'); expect(writes).toHaveLength(0)
    await editor.getByRole('button', { name: '保存', exact: true }).click()
    await expect(editor).toBeHidden()
    expect(writes).toHaveLength(1)
    expect(writes[0]?.body.customs).toMatchObject({ nameZh: '核对后的当前品名', state: 'DRAFT', version: 7 })
    expect(writes[0]?.path).toBe(`/api/v1/customers/products/${product.id}`)
    expect(errors).toEqual([]); expect(unexpected).toEqual([])
  })
}
