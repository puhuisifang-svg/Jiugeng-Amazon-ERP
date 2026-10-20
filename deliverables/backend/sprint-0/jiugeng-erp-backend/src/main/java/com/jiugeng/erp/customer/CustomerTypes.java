package com.jiugeng.erp.customer;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class CustomerTypes {
    private CustomerTypes() {}
    enum Status { ACTIVE, DISABLED }
    record InvoiceTitle(@NotBlank @Size(max=200) String invoiceTitle,
        @Size(max=64) String taxNumber, @Size(max=500) String registeredAddress,
        @Size(max=32) String registeredPhone, @Size(max=200) String bankName,
        @Size(max=64) String bankAccount) {}
    record CustomerInput(@NotBlank @Size(max=128) String name,
        @Size(max=100) String account1688, @Size(max=64) String contactPerson,
        @Size(max=32) String phone, @Size(max=100) String wechatId,
        @Size(max=500) String address, @Size(max=500) String remark,
        @NotNull Status status, @PositiveOrZero Long version,
        @Size(max=200) String invoiceTitle, @Size(max=64) String taxNumber,
        @Size(max=500) String registeredAddress, @Size(max=32) String registeredPhone,
        @Size(max=200) String bankName, @Size(max=64) String bankAccount, Boolean defaultCustomer,
        @Size(max=50) List<@NotNull @Valid InvoiceTitle> invoiceTitles) {}
    record CustomerView(String id, String name, String account1688, String contactPerson,
        String phone, String wechatId, String address, String remark, Status status,
        long version, Instant updatedAt, String invoiceTitle, String taxNumber, String registeredAddress,
        String registeredPhone, String bankName, String bankAccount, boolean defaultCustomer, List<InvoiceTitle> invoiceTitles) {}
    record ProductInput(@NotBlank @Size(max=36) String customerId,
        @NotBlank @Size(max=36) String productId, @NotBlank @Size(max=36) String variantId,
        @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal supplyPrice,
        @NotBlank @Size(max=16) String priceUnit, @Size(max=128) String customerProductCode,
        @Size(max=2000) String requirements, @NotNull Status status, @PositiveOrZero Long version) {}
    record ProductView(String id, String customerId, String customerName, Status customerStatus,
        String productId, String variantId, String skuCode, String productName,
        BigDecimal supplyPrice, String priceUnit, String customerProductCode, String requirements,
        Status status, long version, Instant updatedAt) {}
    record Page<T>(List<T> items, long totalCount, int page, int size) {}
}
