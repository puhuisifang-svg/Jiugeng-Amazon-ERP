export function moduleEntry(html: string, origin: string): string | null {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const src = doc.querySelector<HTMLScriptElement>('script[type="module"][src]')?.getAttribute('src')
  if (!src) return null
  const url = new URL(src, origin)
  return url.origin === new URL(origin).origin && url.pathname.startsWith('/assets/') ? url.pathname : null
}

export async function hasNewRelease(current: string, fetcher: typeof fetch = fetch): Promise<boolean> {
  const response = await fetcher(`/index.html?version-check=${Date.now()}`, { cache: 'no-store', credentials: 'same-origin' })
  if (!response.ok || !response.headers.get('content-type')?.includes('text/html')) return false
  const next = moduleEntry(await response.text(), window.location.origin)
  return next !== null && next !== current
}
