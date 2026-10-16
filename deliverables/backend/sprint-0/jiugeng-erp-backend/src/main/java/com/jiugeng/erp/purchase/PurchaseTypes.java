package com.jiugeng.erp.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.jiugeng.erp.iam.AuditHistoryLookup;

final class PurchaseTypes {
    private PurchaseTypes() {}
    enum OrderType { PRODUCT, MATERIAL }
    enum Status { DRAFT, SUBMITTED, PARTIALLY_RECEIVED, COMPLETED, CANCELLED }
    record LineInput(String itemId, BigDecimal quantity, BigDecimal unitPrice) {}
    record OrderInput(OrderType orderType, String supplierId, String currency, String remark, Long version,
                      List<LineInput> lines, LocalDate expectedDeliveryDate, String deliveryAddress) {}
    record LineView(String id, int lineNo, String itemId, String itemCode, String itemName, String unit,
                    BigDecimal orderedQuantity, BigDecimal receivedQuantity, BigDecimal remainingQuantity,
                    BigDecimal unitPrice) {}
    record OrderView(String id, String orderNo, OrderType orderType, String supplierId, String supplierName,
                     Status status, String currency, String remark, long version, List<LineView> lines,
                     Instant createdAt, Instant updatedAt, LocalDate expectedDeliveryDate, String deliveryAddress,
                     String cancellationReason) {}
    record ReceiptLineInput(String orderLineId, BigDecimal quantity) {}
    record ReceiptInput(String requestId, Instant receivedAt, String remark, List<ReceiptLineInput> lines) {}
    record ReceiptLineView(String orderLineId, String itemCode, String itemName, BigDecimal quantity,
                           String inventoryTransactionId) {}
    record ReceiptView(String id, String orderId, String orderNo, String requestId, Instant receivedAt,
                       String remark, String actorLoginName, List<ReceiptLineView> lines, Instant createdAt,
                       boolean replayed) {}
    record Dashboard(long draft, long pendingReceipt, long partial, long completed, List<OrderView> recent) {}
    record ReferenceItem(String id, String code, String name, String unit, boolean primarySupplier) {}
    record OrderDetail(OrderView order, List<ReceiptView> receipts,
                       List<AuditHistoryLookup.HistoryEvent> history) {}
}
