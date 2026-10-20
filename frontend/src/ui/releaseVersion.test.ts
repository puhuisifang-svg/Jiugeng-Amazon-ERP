import { expect, it, vi } from 'vitest'
import { hasNewRelease, moduleEntry } from './releaseVersion'
const html = (name: string) => `<script type="module" src="/assets/${name}.js"></script>`
it('accepts same-origin hashed entry and rejects fallback/error or remote scripts', () => {
  expect(moduleEntry(html('current'), 'https://erp.example')).toBe('/assets/current.js')
  expect(moduleEntry('<h1>gateway error</h1>', 'https://erp.example')).toBeNull()
  expect(moduleEntry('<script type="module" src="https://other.example/assets/x.js"></script>', 'https://erp.example')).toBeNull()
})
it('fetches fresh HTML and detects changed entry without treating unchanged build as stale', async () => {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(html('new'), { headers: { 'content-type': 'text/html' } }))
  expect(await hasNewRelease('/assets/old.js', fetcher)).toBe(true)
  expect(fetcher).toHaveBeenCalledWith(expect.stringContaining('/index.html?version-check='), expect.objectContaining({ cache: 'no-store' }))
  fetcher.mockResolvedValue(new Response(html('old'), { headers: { 'content-type': 'text/html' } }))
  expect(await hasNewRelease('/assets/old.js', fetcher)).toBe(false)
})
it('does not confuse gateway failures or JSON responses with a deployment', async () => {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response('bad gateway', { status: 502 }))
  expect(await hasNewRelease('/assets/old.js', fetcher)).toBe(false)
  fetcher.mockResolvedValue(new Response('{}', { headers: { 'content-type': 'application/json' } }))
  expect(await hasNewRelease('/assets/old.js', fetcher)).toBe(false)
})
