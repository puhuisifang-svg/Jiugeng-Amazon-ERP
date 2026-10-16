package com.jiugeng.erp.product;

import static com.jiugeng.erp.product.ProductTypes.SkuListItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
class ProductSkuExportService {
    byte[] export(List<SkuListItem> rows) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("产品SKU");
            String[] headers = {"SKU", "品名", "产品编码", "产品名称", "产品状态", "SKU状态", "业务可用",
                    "产品长(cm)", "产品宽(cm)", "产品高(cm)", "包装长(cm)", "包装宽(cm)", "包装高(cm)",
                    "包装重量(g)", "供应商数", "辅料数", "更新时间"};
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            var title = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) { title.createCell(index).setCellValue(headers[index]); title.getCell(index).setCellStyle(header); }
            int rowIndex = 1;
            for (SkuListItem item : rows) {
                var row = sheet.createRow(rowIndex++); var variant = item.variant(); int column = 0;
                text(row, column++, variant.skuCode()); text(row, column++, variant.variantName());
                text(row, column++, item.productCode()); text(row, column++, item.productName());
                text(row, column++, item.productStatus().name()); text(row, column++, variant.status().name());
                text(row, column++, item.businessAvailable() ? "是" : "否");
                number(row, column++, variant.itemLengthCm()); number(row, column++, variant.itemWidthCm());
                number(row, column++, variant.itemHeightCm()); number(row, column++, variant.packageLengthCm());
                number(row, column++, variant.packageWidthCm()); number(row, column++, variant.packageHeightCm());
                number(row, column++, variant.packageWeightGrams()); row.createCell(column++).setCellValue(variant.suppliers().size());
                row.createCell(column++).setCellValue(variant.materials().size());
                text(row, column, item.updatedAt() == null ? null : item.updatedAt().toString());
            }
            sheet.createFreezePane(0, 1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
            for (int column = 0; column < headers.length; column++) sheet.setColumnWidth(column, Math.min(column < 4 ? 24 : 18, 255) * 256);
            workbook.write(output); return output.toByteArray();
        } catch (IOException exception) { throw new ProductException(500, "SKU_EXPORT_FAILED", "SKU 导出文件生成失败"); }
    }
    private void text(org.apache.poi.ss.usermodel.Row row, int column, String value) { row.createCell(column).setCellValue(value == null ? "" : value); }
    private void number(org.apache.poi.ss.usermodel.Row row, int column, java.math.BigDecimal value) { if (value != null) row.createCell(column).setCellValue(value.doubleValue()); else row.createCell(column).setBlank(); }
}
