import { afterEach, describe, expect, it, vi } from 'vitest'

import { SESSION_TOKEN_KEY } from './client'
import { activeImportTypes, importTemplateFileNames, importTemplateVersions, importsApi } from './imports'

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
      expect(body.get('templateVersion')).toBe('2.0')
      return new Response(JSON.stringify({ batch: { id: 'b1' }, replayed: false }), { status: 202 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const result = await importsApi.upload('PRODUCTS', new File(['xlsx'], 'products.xlsx'), 'upload-key')

    expect(result.batch.id).toBe('b1')
  })

  it('uses product 2.0 and variant 2.1 while keeping the other templates at 1.0', () => {
    expect(importTemplateVersions).toEqual({
      PRODUCTS: '2.0', VARIANTS: '2.1', AUXILIARY_MATERIALS: '1.0',
      PRODUCT_AUXILIARY_MATERIALS: '1.0', AUXILIARY_MATERIAL_OPENING_INVENTORY: '1.0',
    })
    expect(importTemplateFileNames).toEqual({
      PRODUCTS: '01_products_v2.0.xlsx', VARIANTS: '02_variants_v2.1.xlsx',
      AUXILIARY_MATERIALS: '04_auxiliary_materials_v1.0.xlsx',
      PRODUCT_AUXILIARY_MATERIALS: '05_sku_auxiliary_materials_v1.0.xlsx',
      AUXILIARY_MATERIAL_OPENING_INVENTORY: '06_initial_inventory_v1.0.xlsx',
    })
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

  it('downloads the protected per-row result and revokes its object URL', async () => {
    window.localStorage.setItem(SESSION_TOKEN_KEY, 'token')
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toContain('/v1/import-batches/batch%2F1/result-file')
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token')
      return new Response('\ufeffbatch_id,status\r\nb1,SUCCEEDED\r\n', { status: 200 })
    })
    const createObjectURL = vi.fn(() => 'blob:result')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    await importsApi.downloadResult('batch/1')

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:result')
    click.mockRestore()
  })

  it('downloads the selected template with its registered versioned filename', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('xlsx', { status: 200 })))
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:template'), revokeObjectURL: vi.fn() })
    let downloaded = ''
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloaded = this.download
    })

    await importsApi.downloadTemplate('VARIANTS')

    expect(downloaded).toBe('02_variants_v2.1.xlsx')
    click.mockRestore()
  })
})
