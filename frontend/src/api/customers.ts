import { apiRequest } from './client'

export type CustomerStatus = 'ACTIVE' | 'DISABLED'
export interface InvoiceTitle {
  invoiceTitle: string; taxNumber: string; registeredAddress: string; registeredPhone: string; bankName: string; bankAccount: string
}
export interface CustomerInput {
  invoiceTitles?: InvoiceTitle[]
  name: string; account1688: string; contactPerson: string; phone: string; wechatId: string
  invoiceTitle?: string; taxNumber?: string; registeredAddress?: string; registeredPhone?: string; bankName?: string; bankAccount?: string; defaultCustomer?: boolean
  address: string; remark: string; status: CustomerStatus; version?: number | undefined
}
export interface Customer extends CustomerInput { id: string; version: number; updatedAt: string }
export interface CustomerProductInput {
  customerId: string; productId: string; variantId: string; supplyPrice: string | number | null; priceUnit: string
  customerProductCode: string; requirements: string; status: CustomerStatus; version?: number | undefined
}
export interface CustomerProduct extends CustomerProductInput {
  id: string; customerName: string; customerStatus: CustomerStatus; skuCode: string; productName: string
  version: number; updatedAt: string
}
export interface CustomerPage<T> { items: T[]; totalCount: number; page: number; size: number }
interface Filters { q?: string; status?: CustomerStatus | ''; customerId?: string; page?: number; size?: number }
function query(filters: Filters) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '') params.set(key, String(value))
  return params.toString()
}
export const customersApi = {
  defaultCustomer: () => apiRequest<Customer | null>('/v1/customers/default'),
  list: (filters: Filters = {}) => apiRequest<CustomerPage<Customer>>(`/v1/customers?${query(filters)}`),
  products: (filters: Filters = {}) => apiRequest<CustomerPage<CustomerProduct>>(`/v1/customers/products?${query(filters)}`),
  save: (id: string | undefined, input: CustomerInput) => apiRequest<Customer>(`/v1/customers${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
  deleteProduct: (id: string, version: number) => apiRequest<void>(`/v1/customers/products/${encodeURIComponent(id)}?version=${version}`, { method: 'DELETE' }),
  saveProduct: (id: string | undefined, input: CustomerProductInput) => apiRequest<CustomerProduct>(`/v1/customers/products${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) }),
}
