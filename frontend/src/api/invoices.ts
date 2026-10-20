import { apiRequest } from './client'
import type { CustomerPage, InvoiceTitle } from './customers'

export interface InvoiceInput {
  customerProductId: string; invoiceTitle: string; taxNumber: string; registeredAddress: string
  registeredPhone: string; bankName: string; bankAccount: string; itemName: string; specification: string
  unit: string; quantity: number | string; netUnitPrice: number | string | null; taxRate: number | string
  shipped: boolean; invoiced: boolean; logistics: string; version?: number | undefined
}
export interface Invoice {
  id: string; customerId: string; customerName: string; details: InvoiceInput
  netAmount: string; taxAmount: string; totalAmount: string; updatedAt: string
}
export const invoicesApi = {
  list: (filters: { q: string; shipped: string; invoiced: string; page: number }) => {
    const params = new URLSearchParams({ q: filters.q, page: String(filters.page), size: '20' })
    if (filters.shipped !== '') params.set('shipped', filters.shipped)
    if (filters.invoiced !== '') params.set('invoiced', filters.invoiced)
    return apiRequest<CustomerPage<Invoice>>(`/v1/customers/invoices?${params}`)
  },
  draft: (id: string) => apiRequest<InvoiceInput & { invoiceTitles: InvoiceTitle[] }>(`/v1/customers/invoices/draft?customerProductId=${encodeURIComponent(id)}`),
  save: (id: string | undefined, input: InvoiceInput) => apiRequest<Invoice>(`/v1/customers/invoices${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
}
