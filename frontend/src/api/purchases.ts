import { apiRequest } from './client'

export type PurchaseOrderType = 'PRODUCT' | 'MATERIAL'
export type PurchaseStatus = 'DRAFT' | 'SUBMITTED' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CANCELLED'
export interface PurchaseLine { id: string; lineNo: number; itemId: string; itemCode: string; itemName: string; unit: string; orderedQuantity: number; receivedQuantity: number; remainingQuantity: number; unitPrice: number }
export interface PurchaseOrder { id: string; orderNo: string; orderType: PurchaseOrderType; supplierId: string; supplierName: string; status: PurchaseStatus; currency: string; remark?: string; version: number; lines: PurchaseLine[]; createdAt: string; updatedAt: string; expectedDeliveryDate?: string | null; deliveryAddress?: string | null; cancellationReason?: string | null }
export interface PurchaseReceiptLine { orderLineId: string; itemCode: string; itemName: string; quantity: number; inventoryTransactionId?: string }
export interface PurchaseReceipt { id: string; orderId: string; orderNo: string; requestId: string; receivedAt: string; remark?: string; actorLoginName: string; lines: PurchaseReceiptLine[]; createdAt: string; replayed: boolean }
export interface PurchaseDashboard { draft: number; pendingReceipt: number; partial: number; completed: number; recent: PurchaseOrder[] }
export interface PurchaseReferenceItem { id: string; code: string; name: string; unit: string; primarySupplier: boolean }
export interface PurchaseHistoryEvent { id: string; action: string; actorLoginName: string; actorDisplayName?: string; detail?: string; occurredAt: string }
export interface PurchaseOrderDetail { order: PurchaseOrder; receipts: PurchaseReceipt[]; history: PurchaseHistoryEvent[] }
export interface PurchaseOrderInput { orderType: PurchaseOrderType; supplierId: string; currency: string; remark?: string; version?: number; expectedDeliveryDate?: string | null; deliveryAddress?: string; lines: Array<{ itemId: string; quantity: number; unitPrice: number }> }

export const purchaseApi = {
  dashboard: () => apiRequest<PurchaseDashboard>('/v1/purchases/dashboard'),
  orders: (status?: PurchaseStatus) => apiRequest<PurchaseOrder[]>(`/v1/purchases/orders${status ? `?status=${status}` : ''}`),
  order: (id: string) => apiRequest<PurchaseOrder>(`/v1/purchases/orders/${id}`),
  detail: (id: string) => apiRequest<PurchaseOrderDetail>(`/v1/purchases/orders/${id}/detail`),
  references: (orderType: PurchaseOrderType, supplierId: string) => apiRequest<PurchaseReferenceItem[]>(`/v1/purchases/references?orderType=${orderType}&supplierId=${encodeURIComponent(supplierId)}`),
  create: (input: PurchaseOrderInput) => apiRequest<PurchaseOrder>('/v1/purchases/orders', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: string, input: PurchaseOrderInput) => apiRequest<PurchaseOrder>(`/v1/purchases/orders/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  submit: (id: string, version: number) => apiRequest<PurchaseOrder>(`/v1/purchases/orders/${id}/submit`, { method: 'POST', body: JSON.stringify({ version }) }),
  cancel: (id: string, version: number, reason: string) => apiRequest<PurchaseOrder>(`/v1/purchases/orders/${id}/cancel`, { method: 'POST', body: JSON.stringify({ version, reason }) }),
  receive: (id: string, input: { requestId: string; receivedAt: string; remark?: string; lines: Array<{ orderLineId: string; quantity: number }> }) => apiRequest<PurchaseReceipt>(`/v1/purchases/orders/${id}/receipts`, { method: 'POST', body: JSON.stringify(input) }),
  receipts: (orderId?: string) => apiRequest<PurchaseReceipt[]>(`/v1/purchases/receipts${orderId ? `?orderId=${encodeURIComponent(orderId)}` : ''}`),
}
