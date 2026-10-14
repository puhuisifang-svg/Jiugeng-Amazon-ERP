package com.jiugeng.erp.importexport;

import static com.jiugeng.erp.importexport.ImportTypes.ImportType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ImportTemplateRegistry {
    private final Path root;
    private static final Map<ImportType, Definition> DEFINITIONS = Map.of(
            ImportType.PRODUCTS, new Definition("01_products_v1.0.xlsx", List.of("导入模板")),
            ImportType.VARIANTS, new Definition("02_variants_v1.0.xlsx", List.of("variants_导入模板", "attributes_导入模板")),
            ImportType.AUXILIARY_MATERIALS, new Definition("04_auxiliary_materials_v1.0.xlsx", List.of("导入模板")),
            ImportType.PRODUCT_AUXILIARY_MATERIALS, new Definition("05_sku_auxiliary_materials_v1.0.xlsx", List.of("导入模板")),
            ImportType.AUXILIARY_MATERIAL_OPENING_INVENTORY, new Definition("06_initial_inventory_v1.0.xlsx", List.of("导入模板")));
    ImportTemplateRegistry(@Value("${app.imports.templates-dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    Definition definition(ImportType type) { return DEFINITIONS.get(type); }
    byte[] bytes(ImportType type) {
        try {
            Path file = root.resolve(definition(type).fileName()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Template missing");
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new ImportException(404, "IMPORT_TEMPLATE_NOT_FOUND", "导入模板不可用");
        }
    }
    record Definition(String fileName, List<String> dataSheets) {}
}
