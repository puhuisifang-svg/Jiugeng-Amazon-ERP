package com.jiugeng.erp.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.jiugeng.erp.product.ProductLookup.NamePresentation;

final class CustomerOrderTypes {
    private CustomerOrderTypes() {}
    record LineInput(String id, @NotBlank @Size(max=36) String customerProductId,
        @NotBlank @Size(max=128) String name, @Size(max=200) String specification,
        @NotBlank @Size(max=16) String unit,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=9,fraction=3) BigDecimal quantity,
        @NotNull @DecimalMin("0") @Digits(integer=8,fraction=6) @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal netUnitPrice,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        @NotNull LocalDate deliveryDate, @Size(max=500) String remark) {}
    record Input(@NotBlank @Size(max=36) String customerId,
        @NotBlank @Pattern(regexp="1688|OFFLINE") String source,
        @Size(max=100) String externalNumber, @Size(max=100) String contractNumber,
        @NotNull LocalDate orderDate, @NotNull LocalDate deliveryDate, boolean invoiceRequired,
        @Size(max=200) String invoiceTitle, @Size(max=64) String taxNumber,
        @Size(max=100) String recipient, @Size(max=32) String phone, @Size(max=500) String address,
        @Size(max=1000) String remark, @NotEmpty @Size(max=100) List<@Valid LineInput> lines, Long version) {}
    record Line(String id, LineInput details, CustomerProductLookup.ShippingProduct product,
        String skuCode, NamePresentation namePresentation, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal netAmount, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal taxAmount,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalAmount, BigDecimal allocatedQuantity, BigDecimal shippedQuantity) {}
    record View(String id, String orderNumber, String customerName, String status, Input details,
        CustomerTypes.InvoiceTitle invoiceTitle, List<Line> lines, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal netAmount,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal taxAmount, @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal totalAmount, long version, String invoiceId, boolean invoiced) {}
    record Invoice(String id, String orderId, View snapshot, boolean invoiced, String invoiceNumber,
        LocalDate invoiceDate, long version) {}
    record InvoiceUpdate(@NotNull Long version, boolean invoiced, @Size(max=100) String invoiceNumber,
        LocalDate invoiceDate, boolean refreshFromOrder) {}
    record Transition(@NotNull Long version, @NotBlank @Pattern(regexp="CONFIRM|CANCEL|CLOSE|DELETE") String action,
        @Size(max=500) String reason) {}
}
