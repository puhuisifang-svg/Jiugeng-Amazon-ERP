import { apiRequest } from './client'
import type { CustomerPage, InvoiceTitle } from './customers'
import type { NamePresentation, ShippingCustomerProduct } from './dashboard'

export interface OrderLineInput { id?: string; customerProductId: string; name: string; specification: string; unit: string; quantity: string | number; netUnitPrice: string | number; taxRate: string | number; deliveryDate: string; remark: string }
export interface OrderInput { customerId: string; source: '1688' | 'OFFLINE'; externalNumber: string; contractNumber: string; orderDate: string; deliveryDate: string; invoiceRequired: boolean; invoiceTitle: string; taxNumber: string; recipient: string; phone: string; address: string; remark: string; lines: OrderLineInput[]; version?: number }
export interface OrderLine { id: string; details: OrderLineInput; product: ShippingCustomerProduct; skuCode: string; namePresentation?: NamePresentation; netAmount: string; taxAmount: string; totalAmount: string; allocatedQuantity: number; shippedQuantity: number }
export interface CustomerOrder { id: string; orderNumber: string; customerName: string; status: string; details: OrderInput; invoiceTitle: InvoiceTitle; lines: OrderLine[]; netAmount: string; taxAmount: string; totalAmount: string; version: number; invoiceId?: string; invoiced: boolean }
export interface OrderInvoice { id: string; orderId: string; snapshot: CustomerOrder; invoiced: boolean; invoiceNumber: string; invoiceDate?: string; version: number }
export interface ArrangementItem { lineId: string; quantity: number; boxCount: number; plannedDate: string; latestDate: string; remark: string }
export interface OrderAllocation { id: string; orderId: string; orderNumber: string; lineId: string; name: string; unit: string; skuCode: string; details: ArrangementItem; state: string; pendingId?: string; todayId?: string; recipient: string; phone: string; address: string; reference: string; logistics: string; version: number }
const base = '/v1/customer-orders'
function query(filters: Record<string, string | number | undefined>) { const p = new URLSearchParams(); Object.entries(filters).forEach(([k, v]) => { if (v !== undefined && v !== '') p.set(k, String(v)) }); return p.toString() }
export const ordersApi = {
  list: (filters: Record<string, string | number | undefined>) => apiRequest<CustomerPage<CustomerOrder>>(`${base}?${query(filters)}`),
  get: (id: string) => apiRequest<CustomerOrder>(`${base}/${encodeURIComponent(id)}`),
  save: (id: string | undefined, input: OrderInput, key: string) => apiRequest<CustomerOrder>(id ? `${base}/${encodeURIComponent(id)}` : base, { method: id ? 'PUT' : 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) }),
  transition: (id: string, version: number, action: string, reason: string) => apiRequest<CustomerOrder>(`${base}/${id}/transition`, { method: 'POST', body: JSON.stringify({ version, action, reason }) }),
  arrange: (id: string, input: { version: number; items: ArrangementItem[]; recipient: string; phone: string; address: string }, key: string) => apiRequest<OrderAllocation[]>(`${base}/${id}/arrangements`, { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(input) }),
  allocations: (id: string) => apiRequest<OrderAllocation[]>(`${base}/${id}/arrangements`),
  logistics: (id: string, version: number, logistics: string) => apiRequest<void>(`${base}/arrangements/${id}/logistics`, { method: 'PUT', body: JSON.stringify({ version, logistics }) }),
  history: (id: string) => apiRequest<import('./dashboard').DashboardHistoryEvent[]>(`${base}/${id}/history`),
  generateInvoice: (id: string) => apiRequest<OrderInvoice>(`${base}/${id}/invoice`, { method: 'POST' }),
  invoices: (filters: Record<string, string | number | undefined>) => apiRequest<CustomerPage<OrderInvoice>>(`${base}/invoices?${query(filters)}`),
  invoice: (id: string) => apiRequest<OrderInvoice>(`${base}/invoices/${id}`),
  updateInvoice: (id: string, input: { version: number; invoiced: boolean; invoiceNumber: string; invoiceDate: string | null; refreshFromOrder: boolean }) => apiRequest<OrderInvoice>(`${base}/invoices/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
}
