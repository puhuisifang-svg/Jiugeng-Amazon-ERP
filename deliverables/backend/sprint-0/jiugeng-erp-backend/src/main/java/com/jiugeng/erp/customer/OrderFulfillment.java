package com.jiugeng.erp.customer;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup.NamePresentation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public customer-order boundary; shipping owns its own records and calls these in the same transaction. */
public interface OrderFulfillment {
    record Item(@NotBlank String lineId,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=9,fraction=3) BigDecimal quantity,
        @Positive int boxCount,@NotNull LocalDate plannedDate,@NotNull LocalDate latestDate,@Size(max=500) String remark) {}
    record Request(@NotNull Long version,@NotEmpty @Size(max=100) List<@Valid Item> items,
        @NotBlank @Size(max=100) String recipient,@NotBlank @Size(max=32) String phone,@NotBlank @Size(max=500) String address) {}
    record Allocation(String id,String orderId,String orderNumber,String lineId,String name,String unit,String skuCode,
        CustomerProductLookup.ShippingProduct product,NamePresentation namePresentation,Item details,String state,
        String pendingId,String todayId,String recipient,String phone,String address,String reference,String logistics,long version) {}
    List<Allocation> reserve(String orderId,Request request,String key,AuthenticatedActor actor);
    List<Allocation> allocations(String orderId);
    Allocation get(String id);
    void link(String id,String pendingId,String todayId,String state,AuthenticatedActor actor);
    void change(String id,BigDecimal quantity,String customerProductId,String action,AuthenticatedActor actor);
    void updatePlan(String id,int boxCount,LocalDate plannedDate,LocalDate latestDate,String remark);
    void logistics(String id,long version,String value,AuthenticatedActor actor);
}
