import { afterEach, describe, expect, it, vi } from 'vitest'
import { invoicesApi, type InvoiceInput } from './invoices'
afterEach(() => vi.unstubAllGlobals())
describe('invoice API', () => {
  it('retains false filters and encodes queries and draft identifiers', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [] }) }); vi.stubGlobal('fetch', fetch)
    await invoicesApi.list({ q: '合成 & A', shipped: 'false', invoiced: 'true', page: 2 })
    expect(fetch.mock.calls[0]![0]).toContain('q=%E5%90%88%E6%88%90+%26+A&page=2&size=20&shipped=false&invoiced=true')
    await invoicesApi.draft('cp&1')
    expect(fetch.mock.calls[1]![0]).toContain('customerProductId=cp%261')
  })
  it('preserves decimal strings and version and surfaces server conflicts', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ code: 'INVOICE_VERSION_CONFLICT', message: '请刷新后重新编辑' }) }); vi.stubGlobal('fetch', fetch)
    const input: InvoiceInput = { customerProductId: 'cp', invoiceTitle: '合成', taxNumber: '', registeredAddress: '', registeredPhone: '', bankName: '', bankAccount: '', itemName: '合成', specification: '', unit: '件', quantity: '3', netUnitPrice: '10.000001', taxRate: '1', shipped: false, invoiced: false, logistics: '', version: 3 }
    await expect(invoicesApi.save('i1', input)).rejects.toThrow('请刷新后重新编辑')
    expect(fetch.mock.calls[0]![1]).toEqual(expect.objectContaining({ method: 'PUT', body: JSON.stringify(input) }))
  })
})
