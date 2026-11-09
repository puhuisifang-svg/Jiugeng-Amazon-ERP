import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRequestId } from './requestId'
afterEach(() => vi.unstubAllGlobals())
describe('request IDs on secure and HTTP origins', () => {
  it('uses native UUID support when present', () => {
    const randomUUID = vi.fn(() => 'synthetic-native-id')
    vi.stubGlobal('crypto', { randomUUID })
    expect(createRequestId()).toBe('synthetic-native-id')
    expect(randomUUID).toHaveBeenCalledOnce()
  })
  it('uses secure random bytes on HTTP and preserves UUID v4 format', () => {
    const getRandomValues = globalThis.crypto.getRandomValues.bind(globalThis.crypto)
    vi.stubGlobal('crypto', { getRandomValues })
    const ids = Array.from({ length: 1000 }, createRequestId)
    expect(new Set(ids).size).toBe(1000)
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })
})
