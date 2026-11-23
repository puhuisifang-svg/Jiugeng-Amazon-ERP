package com.jiugeng.erp.materialinventory;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Base quantities: counts are integral; measured units use at most four decimal places. */
final class MaterialQuantity {
    private static final Set<String> MEASURED = Set.of("米","厘米","毫米","千克","公斤","克","升","毫升","平方米","立方米","M","CM","MM","KG","G","L","ML","M2","M3");
    static int scale(String unit) { return MEASURED.contains(unit.trim().toUpperCase(Locale.ROOT)) ? 4 : 0; }
    static void validate(BigDecimal value,String unit) {
        if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>scale(unit) || value.compareTo(new BigDecimal("99999999999999.9999"))>0)
            throw new MaterialException(400,"INVENTORY_QUANTITY_INVALID",scale(unit)==0?"该单位数量必须为非负整数且不超过库存上限":"数量必须为非负数、最多四位小数且不超过库存上限");
    }
}
