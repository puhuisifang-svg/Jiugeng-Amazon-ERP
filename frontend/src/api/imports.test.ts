import { afterEach, describe, expect, it, vi } from 'vitest'

import { SESSION_TOKEN_KEY } from './client'
import { activeImportTypes, importsApi } from './imports'

describe('importsApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('exposes exactly the five active import types', () => {
    expect(activeImportTypes).toEqual([
      'PRODUCTS', 'VARIANTS', 'AUXILIARY_MATERIALS',
      'PRODUCT_AUXILIARY_MATERIALS', 'AUXILIARY_MATERIAL_OPENING_INVENTORY',
    ])
    expect(activeImportTypes).not.toContain('STORE_LISTINGS')
  })

  it('uploads multipart without overriding the browser boundary', async () => {
    window.localStorage.setItem(SESSION_TOKEN_KEY, 'token')
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      expect(init?.body).toBeInstanceOf(FormData)
      const headers = new Headers(init?.headers)
      expect(headers.get('Content-Type')).toBeNull()
      expect(headers.get('Authorization')).toBe('Bearer token')
      expect(headers.get('Idempotency-Key')).toBe('upload-key')
      const body = init?.body as FormData
      expect(body.get('importType')).toBe('PRODUCTS')
      expect(body.get('templateVersion')).toBe('1.0')
      return new Response(JSON.stringify({ batch: { id: 'b1' }, replayed: false }), { status: 202 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await importsApi.upload('PRODUCTS', new File(['xlsx'], 'products.xlsx'), 'upload-key')

    expect(result.batch.id).toBe('b1')
  })

  it('commits a validated snapshot and reads accurate result counts', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/commit')) {
        expect(init?.method).toBe('POST')
        expect(new Headers(init?.headers).get('Idempotency-Key')).toBe('commit-key')
        expect(JSON.parse(String(init?.body))).toEqual({ previewVersion: 'pv1', fileHash: 'hash1', warningsAccepted: true })
        return new Response(JSON.stringify({ id: 'b1', status: 'SUCCEEDED' }), { status: 202 })
      }
      return new Response(JSON.stringify({ batch: { id: 'b1' }, createdRows: 1, updatedRows: 2, unchangedRows: 3, writtenRows: 3, message: 'ok' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    expect((await importsApi.commit('b1', 'pv1', 'hash1', true, 'commit-key')).status).toBe('SUCCEEDED')
    expect((await importsApi.result('b1')).writtenRows).toBe(3)
  })
})
