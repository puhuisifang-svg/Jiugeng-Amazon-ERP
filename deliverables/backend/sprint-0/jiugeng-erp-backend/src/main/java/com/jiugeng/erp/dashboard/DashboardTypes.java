package com.jiugeng.erp.dashboard;

import java.time.Instant;
import com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct;
import java.time.LocalDate;
import java.util.List;

final class DashboardTypes {
    private DashboardTypes() {}

    enum ImagePurpose { SHIPMENT, PENDING_PRODUCT }
    enum ImageStatus { PENDING, ACTIVE }
    enum TodayStatus { ACTIVE, ARCHIVED, SHIPPED }
    enum PendingShipmentStatus { PENDING, TRANSFERRED_TO_TODAY, ARCHIVED }
    enum PendingProductStatus { PENDING, COMPLETED, ARCHIVED }
    record OrderReference(String id,String orderId,String orderNumber) {}

    record HomeSlice<T>(List<T> items, long totalCount, boolean truncated, LocalDate businessDate,
                        Instant generatedAt) {
        static <T> HomeSlice<T> of(List<T> items, long total, LocalDate date, Instant generatedAt) {
            return new HomeSlice<>(List.copyOf(items), total, total > items.size(), date, generatedAt);
        }
    }

    record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        static <T> Page<T> of(List<T> items, int page, int size, long total) {
            int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
            return new Page<>(List.copyOf(items), page, size, total, pages);
        }
    }

    record TodayShipment(String id, LocalDate shipDate, String productId, String productCode, String productName,
                         String imageId, java.math.BigDecimal quantity, String quantityUnit, int boxCount, String remark,
                         Instant latestShipAt, TodayStatus status, String sourcePendingShipmentId, long version,
                         String createdBy, String updatedBy, String archivedBy, Instant archivedAt,
                         String archiveReason, Instant createdAt, Instant updatedAt, String variantId, ShippingProduct customerProduct, com.jiugeng.erp.product.ProductLookup.NamePresentation namePresentation, DashboardTypes.OrderReference orderAllocation) {}

    record PendingShipment(String id, LocalDate plannedShipDate, String productId, String productCode,
                           String productName, String imageId, java.math.BigDecimal quantity, String quantityUnit, int boxCount,
                           String remark, Instant latestShipAt, PendingShipmentStatus status, long version,
                           String createdBy, String updatedBy, String archivedBy, Instant archivedAt,
                           String archiveReason, Instant createdAt, Instant updatedAt, String variantId, ShippingProduct customerProduct, com.jiugeng.erp.product.ProductLookup.NamePresentation namePresentation, DashboardTypes.OrderReference orderAllocation) {}

    record PendingProduct(String id, String productName, String imageId, int expectedQuantity, String quantityUnit,
                          String remark, LocalDate expectedCompletionDate, PendingProductStatus status, long version,
                          String createdBy, String updatedBy, String completedBy, Instant completedAt,
                          String archivedBy, Instant archivedAt, String archiveReason, Instant createdAt,
                          Instant updatedAt) {}

    record ImageAsset(String id, ImagePurpose purpose, ImageStatus status, String originalStorageKey,
                      String thumbnailStorageKey, String sourceFileName, String sourceMediaType,
                      String storedMediaType, long byteSize, String sha256, int width, int height,
                      String uploadedBy, String attachedResourceType, String attachedResourceId,
                      Instant createdAt, Instant attachedAt) {}

    record ImageReceipt(String id, ImagePurpose purpose, String sourceFileName, String mediaType,
                        long byteSize, int width, int height, String sha256) {}

    record ShipmentInput(LocalDate date, String productId, String imageId, java.math.BigDecimal quantity, int boxCount,
                         String remark, Instant latestShipAt, String variantId, String customerProductId, ShippingProduct customerProduct) {}
    record PendingProductInput(String productName, String imageId, int expectedQuantity, String remark,
                               LocalDate expectedCompletionDate) {}
    record Command(String actorId, String operation, String key, String payloadHash, String resourceType,
                   String resourceId) {}
}
