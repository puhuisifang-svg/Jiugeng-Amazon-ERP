package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.List;
import java.util.Map;

/** Stable public boundary for validated opening-inventory imports. */
public interface InventoryOpeningImportGateway {
    Validation validate(List<ImportRow> rows);
    Result commit(List<ImportRow> rows, AuthenticatedActor actor);

    record ImportRow(String sheet, int rowNumber, Map<String, String> data) {}
    record Error(String sheet, int rowNumber, String field, String code, String reason, String rawValue) {}
    record Validation(List<Error> errors) {
        public boolean valid() { return errors.isEmpty(); }
    }
    record Result(int created, int updated, int unchanged) {}
}
