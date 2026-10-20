package com.jiugeng.erp.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

record CustomsProfile(
    @Size(max=200) String nameZh, @Size(max=200) String nameEn,
    @Size(max=1000) String composition, @Size(max=1000) String usage,
    @Size(max=100) String brand, @Size(max=200) String model,
    @Pattern(regexp="^$|[A-Z]{2}") String originCountry,
    @Size(max=32) String unit,
    @DecimalMin(value="0",inclusive=false) @Digits(integer=6,fraction=6) BigDecimal netWeightKg,
    @DecimalMin(value="0",inclusive=false) @Digits(integer=6,fraction=6) BigDecimal grossWeightKg,
    @Pattern(regexp="^$|[0-9]{10}") String chinaCode,
    @Pattern(regexp="^$|[0-9]{10}") String usCode,
    @Pattern(regexp="^$|[0-9]{8}|[0-9]{10}") String euCode,
    @Pattern(regexp="^$|[0-9]{10}") String ukCode,
    @Size(max=4000) String declarationElements,
    @Size(max=2000) String complianceNotes,
    @Size(max=1000) String remark,
    @NotNull State state, @NotNull @Min(0) Long version) {
    enum State { DRAFT, REVIEWED }
}
