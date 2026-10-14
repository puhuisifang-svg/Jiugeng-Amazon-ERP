package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.List;
import java.util.Map;

/** Stable public boundary used by the import module without exposing product internals. */
public interface ProductImportGateway {
    Validation validateProducts(List<ImportRow> rows);
    Validation validateVariants(List<ImportRow> rows);
    Result commitProducts(List<ImportRow> rows, AuthenticatedActor actor);
    Result commitVariants(List<ImportRow> rows, AuthenticatedActor actor);

    record ImportRow(String sheet, int rowNumber, Map<String, String> data) {}
    record Error(String sheet, int rowNumber, String field, String code, String reason, String rawValue) {}
    record Validation(List<Error> errors) {
        public boolean valid() { return errors.isEmpty(); }
    }
    record Result(int created, int updated, int unchanged) {}
}
