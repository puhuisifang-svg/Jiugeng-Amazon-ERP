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
            ImportType.PRODUCTS, new Definition("01_products_v2.0.xlsx", "2.0", List.of("导入模板")),
            ImportType.VARIANTS, new Definition("02_variants_v2.1.xlsx", "2.1", List.of("variants_导入模板")),
            ImportType.AUXILIARY_MATERIALS, new Definition("04_auxiliary_materials_v1.0.xlsx", "1.0", List.of("导入模板")),
            ImportType.PRODUCT_AUXILIARY_MATERIALS, new Definition("05_sku_auxiliary_materials_v1.0.xlsx", "1.0", List.of("导入模板")),
            ImportType.AUXILIARY_MATERIAL_OPENING_INVENTORY, new Definition("06_initial_inventory_v1.0.xlsx", "1.0", List.of("导入模板")));
    ImportTemplateRegistry(@Value("${app.imports.templates-dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    Definition definition(ImportType type) { return DEFINITIONS.get(type); }
    Definition definition(ImportType type, String version) {
        if (type == ImportType.VARIANTS && "2.0".equals(version))
            return new Definition("02_variants_v2.0.xlsx", "2.0", List.of("variants_导入模板"));
        Definition definition = definition(type);
        return definition != null && definition.version().equals(version) ? definition : null;
    }
    byte[] bytes(ImportType type) {
        return bytes(definition(type));
    }
    byte[] bytes(ImportType type, String version) {
        Definition definition = definition(type, version);
        if (definition == null) throw new ImportException(422, "IMPORT_TEMPLATE_VERSION_MISMATCH", "导入模板版本不受支持");
        return bytes(definition);
    }
    private byte[] bytes(Definition definition) {
        try {
            Path file = root.resolve(definition.fileName()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Template missing");
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new ImportException(404, "IMPORT_TEMPLATE_NOT_FOUND", "导入模板不可用");
        }
    }
    boolean supports(ImportType type, String version) {
        return definition(type, version) != null;
    }
    record Definition(String fileName, String version, List<String> dataSheets) {}
}
