package com.jiugeng.erp.customer;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

final class InvoiceTypes {
    private InvoiceTypes() {}
    record Input(
        @NotBlank @Size(max=36) String customerProductId,
        @NotBlank @Size(max=200) String invoiceTitle,
        @Size(max=64) String taxNumber,
        @Size(max=500) String registeredAddress,
        @Size(max=32) String registeredPhone,
        @Size(max=200) String bankName,
        @Size(max=64) String bankAccount,
        @NotBlank @Size(max=200) String itemName,
        @Size(max=200) String specification,
        @NotBlank @Size(max=16) String unit,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=9,fraction=3) BigDecimal quantity,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @NotNull @DecimalMin("0") @Digits(integer=8,fraction=6) BigDecimal netUnitPrice,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        @NotNull Boolean shipped, @NotNull Boolean invoiced,
        @Size(max=1000) String logistics, Long version) {}
    record Draft(@com.fasterxml.jackson.annotation.JsonUnwrapped Input details,
        java.util.List<CustomerTypes.InvoiceTitle> invoiceTitles) {}
    record View(String id,String customerId,String customerName,Input details,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal netAmount,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal taxAmount,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal totalAmount,Instant updatedAt) {}
}
