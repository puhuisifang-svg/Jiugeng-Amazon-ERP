package com.jiugeng.erp.materialinventory;

import java.time.Instant;

final class MaterialTypes {
    private MaterialTypes() {}

    enum MaterialStatus { ACTIVE, DISABLED }

    record MaterialInput(String materialCode, String name, String materialType, String specification, String unit,
            String imageUrl, String remark) {}

    record MaterialView(String id, String materialCode, String name, String materialType, String specification,
            String unit, String imageUrl, String remark, MaterialStatus status, Instant createdAt, Instant updatedAt) {}
}
