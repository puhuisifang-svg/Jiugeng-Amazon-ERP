package com.jiugeng.erp.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** UTF-16 offsets, matching the browser textarea selection API. No HTML or arbitrary CSS. */
record NameStyleSpan(@Min(0) int start, @Min(1) int end, @NotNull Color color, @NotNull Boolean bold) {
    enum Color { BLACK, RED }
}
