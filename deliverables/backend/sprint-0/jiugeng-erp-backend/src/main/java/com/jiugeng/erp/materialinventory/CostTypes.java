package com.jiugeng.erp.materialinventory;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

final class CostTypes {
    record PriceInput(@NotNull @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal netPrice,
        @NotBlank @Size(max=32) String unit,@Pattern(regexp="MANUAL|PURCHASE") @NotNull String source,
        @Size(max=36) String referenceId,@NotBlank @Size(max=500) String reason,@NotNull Long version,boolean confirmedNet) {}
    record MaterialInput(@NotBlank String materialId,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=10) BigDecimal quantity,
        @NotBlank @Size(max=32) String usageUnit,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=6) BigDecimal conversion,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=6) BigDecimal perProducts,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal lossPercent,@Size(max=1000) String remark) {}
    record LaborInput(@NotBlank String operationId,@NotNull @DecimalMin("0") @Digits(integer=8,fraction=6) BigDecimal price,
        @NotBlank @Pattern(regexp="PIECE|BOX|BATCH") String basis,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=4) BigDecimal times,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=4) BigDecimal perProducts,@Size(max=500) String remark) {}
    record Input(@NotBlank @Size(max=16) String unit,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=4) BigDecimal baseQuantity,
        @NotNull @Size(max=100) List<@NotNull @Valid MaterialInput> materials,@NotNull @Size(max=100) List<@NotNull @Valid LaborInput> labor,
        @Size(max=500) String noMaterialReason,@Size(max=500) String noLaborReason) {}
    record Save(@Valid @NotNull Input input,@NotBlank String sourceHash,Long revision,@NotBlank @Size(max=500) String reason) {}
    record Publish(@NotNull Long revision,@NotBlank @Size(max=500) String reason) {}
    record OperationInput(@NotBlank @Size(max=100) String name,boolean enabled,@NotNull Long version) {}
    record MaterialLine(MaterialInput input,String name,String code,String unit,String price,Long priceVersion,String amount,String error) {}
    record LaborLine(LaborInput input,String name,String amount,String error) {}
    record Snapshot(String productId,String variantId,String productName,String name,String sku,String imageId,
        Input input,List<MaterialLine> materials,List<LaborLine> labor,String materialTotal,String laborTotal,String total,
        String sourceHash,List<String> errors) {}
    record Version(String id,String variantId,int versionNumber,String status,Input input,Snapshot snapshot,String sourceHash,long revision,String reason,String createdAt,String publishedAt,boolean stale) {}
}
