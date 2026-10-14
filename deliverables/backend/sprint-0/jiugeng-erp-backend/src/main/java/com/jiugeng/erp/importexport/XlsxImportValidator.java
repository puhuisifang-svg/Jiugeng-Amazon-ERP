package com.jiugeng.erp.importexport;

import static com.jiugeng.erp.importexport.ImportTypes.*;

import com.jiugeng.erp.materialinventory.MaterialImportGateway;
import com.jiugeng.erp.product.ProductImportGateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
class XlsxImportValidator {
    private static final long MAX_EXPANDED = 50L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2048;
    private final ImportTemplateRegistry templates;
    private final ProductImportGateway productImports;
    private final MaterialImportGateway materialImports;
    XlsxImportValidator(ImportTemplateRegistry templates, ProductImportGateway productImports,
            MaterialImportGateway materialImports) {
        this.templates = templates; this.productImports = productImports; this.materialImports = materialImports;
    }

    ValidationResult validate(ImportType type, String version, byte[] bytes) {
        inspectZip(bytes);
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_EXPANDED);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             XSSFWorkbook expected = new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(type)))) {
            verifyMetadata(workbook, type, version);
            var issues = new ArrayList<IssueInput>(); var previews = new ArrayList<PreviewInput>(); int total = 0;
            for (String name : templates.definition(type).dataSheets()) {
                Sheet sheet = workbook.getSheet(name); Sheet expectedSheet = expected.getSheet(name);
                if (sheet == null) throw invalid("IMPORT_SHEET_MISSING", "缺少工作表：" + name);
                List<String> headers = headers(sheet); List<String> expectedHeaders = headers(expectedSheet);
                if (!headers.equals(expectedHeaders)) throw invalid("IMPORT_HEADER_MISMATCH", "工作表表头不匹配：" + name);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex); if (empty(row, headers.size())) continue;
                    total++; if (total > 1000) throw invalid("IMPORT_ROW_LIMIT_EXCEEDED", "导入数据行超过 1000");
                    Map<String, String> data = new LinkedHashMap<>(); boolean formula = false;
                    for (int column = 0; column < headers.size(); column++) {
                        Cell cell = row == null ? null : row.getCell(column);
                        if (cell != null && cell.getCellType() == CellType.FORMULA) formula = true;
                        data.put(headers.get(column), safe(cell));
                    }
                    if (formula) issues.add(new IssueInput("ERROR", true, "ROW", name, rowIndex + 1, null,
                            "IMPORT_FORMULA_NOT_ALLOWED", "导入数据不得包含公式", null, "粘贴为纯值后重试"));
                    String key = data.values().stream().filter(value -> !value.isBlank()).findFirst().orElse("row-" + (rowIndex + 1));
                    previews.add(new PreviewInput(name, rowIndex + 1, key, data));
                }
            }
            if ((type == ImportType.PRODUCTS || type == ImportType.VARIANTS)
                    && issues.stream().noneMatch(IssueInput::blocking)) {
                List<ProductImportGateway.ImportRow> rows = previews.stream()
                        .map(row -> new ProductImportGateway.ImportRow(row.sheet(), row.row(), row.data())).toList();
                ProductImportGateway.Validation semantic = type == ImportType.PRODUCTS
                        ? productImports.validateProducts(rows) : productImports.validateVariants(rows);
                semantic.errors().forEach(error -> issues.add(new IssueInput("ERROR", true, "FIELD", error.sheet(),
                        error.rowNumber(), error.field(), error.code(), error.reason(), error.rawValue(), "修正后重新上传")));
            }
            if ((type == ImportType.AUXILIARY_MATERIALS || type == ImportType.PRODUCT_AUXILIARY_MATERIALS)
                    && issues.stream().noneMatch(IssueInput::blocking)) {
                List<MaterialImportGateway.ImportRow> rows = previews.stream()
                        .map(row -> new MaterialImportGateway.ImportRow(row.sheet(), row.row(), row.data())).toList();
                MaterialImportGateway.Validation semantic = type == ImportType.AUXILIARY_MATERIALS
                        ? materialImports.validateMaterials(rows) : materialImports.validateRelations(rows);
                semantic.errors().forEach(error -> issues.add(new IssueInput("ERROR", true, "FIELD", error.sheet(),
                        error.rowNumber(), error.field(), error.code(), error.reason(), error.rawValue(), "修正后重新上传")));
            }
            return new ValidationResult(total, List.copyOf(issues), List.copyOf(previews));
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw invalid("IMPORT_FILE_TYPE_UNSUPPORTED", "文件不是有效的 OOXML XLSX"); }
    }

    private void inspectZip(byte[] bytes) {
        int entries = 0; long expanded = 0; byte[] buffer = new byte[8192]; boolean contentTypes = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++; if (entries > MAX_ENTRIES) throw unsafe("OOXML 条目过多");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains(":") || name.indexOf('\0') >= 0)
                    throw unsafe("OOXML 包含非法路径");
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.equals("[content_types].xml")) contentTypes = true;
                if (lower.contains("vbaproject") || lower.startsWith("xl/externallinks/")
                        || lower.startsWith("xl/embeddings/") || lower.endsWith(".bin")) throw unsafe("禁止宏、外链或嵌入对象");
                int read; long entrySize = 0;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read; entrySize += read;
                    if (expanded > MAX_EXPANDED || entrySize > MAX_EXPANDED) throw unsafe("OOXML 解压后体积超限");
                }
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && entrySize / (double) compressed > 100d) throw unsafe("OOXML 压缩比异常");
            }
        } catch (ImportException exception) { throw exception; }
        catch (IOException exception) { throw invalid("IMPORT_FILE_TYPE_UNSUPPORTED", "文件不是有效 ZIP/OOXML"); }
        if (!contentTypes) throw invalid("IMPORT_FILE_TYPE_UNSUPPORTED", "缺少 OOXML Content Types");
    }

    private void verifyMetadata(XSSFWorkbook workbook, ImportType type, String version) {
        Sheet meta = workbook.getSheet("_meta"); if (meta == null) throw invalid("IMPORT_TEMPLATE_VERSION_MISMATCH", "缺少模板元数据");
        Map<String, String> values = new LinkedHashMap<>(); DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Row row : meta) if (row.getCell(0) != null) values.put(formatter.formatCellValue(row.getCell(0)).trim(),
                row.getCell(1) == null ? "" : formatter.formatCellValue(row.getCell(1)).trim());
        if (!"jiugeng.amazon.erp.import".equals(values.get("schemaIdentifier"))
                || !type.name().equals(values.get("importType")) || !"1.0".equals(version)
                || !version.equals(values.get("templateVersion")))
            throw invalid("IMPORT_TEMPLATE_VERSION_MISMATCH", "请求、模板类型或版本不一致");
    }
    private List<String> headers(Sheet sheet) {
        if (sheet == null || sheet.getRow(0) == null) return List.of(); DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<String> values = new ArrayList<>(); Row row = sheet.getRow(0);
        for (int index = 0; index < row.getLastCellNum(); index++) values.add(formatter.formatCellValue(row.getCell(index)).trim());
        while (!values.isEmpty() && values.getLast().isBlank()) values.removeLast(); return values;
    }
    private boolean empty(Row row, int columns) {
        if (row == null) return true; for (int index = 0; index < columns; index++) if (!safe(row.getCell(index)).isBlank()) return false; return true;
    }
    private String safe(Cell cell) { return cell == null || cell.getCellType() == CellType.FORMULA ? "" : new DataFormatter(Locale.ROOT).formatCellValue(cell).trim(); }
    private ImportException invalid(String code, String message) { return new ImportException(422, code, message); }
    private ImportException unsafe(String message) { return new ImportException(422, "IMPORT_UNSAFE_CONTENT", message); }
}
