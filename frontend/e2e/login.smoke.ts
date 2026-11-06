import { expect, test } from '@playwright/test'

test('production bundle boots and sends login to the same-origin API', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/auth/login-settings', route => route.fulfill({
    json: { staySignedInDays: 8 },
  }))
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 401, json: { code: 'INVALID_CREDENTIALS', detail: 'Synthetic login rejected' },
  }))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '账号登录' })).toBeVisible()
  await page.getByRole('textbox', { name: '用户名 / 手机号' }).fill('synthetic-smoke')
  await page.getByLabel('密码', { exact: true }).fill('Synthetic-smoke-only!')
  const request = page.waitForRequest(request => request.method() === 'POST' && request.url().endsWith('/api/v1/auth/login'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await request).url()).toBe('http://127.0.0.1:4188/api/v1/auth/login')
  await expect(page.getByText('Synthetic login rejected')).toBeVisible()
  expect(errors).toEqual([])
})
