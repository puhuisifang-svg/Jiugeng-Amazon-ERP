package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/products/{productId}/skus/{variantId}/documents")
class SkuDocumentController {
    enum Kind { MANUAL, LABEL }
    record Document(String id, String kind, String fileName, long byteSize, String uploadedAt) {}
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;
    SkuDocumentController(JdbcTemplate jdbc, AuditTrail audit) { this.jdbc = jdbc; this.audit = audit; }

    private void requireVariant(String productId, String variantId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE id=? AND product_id=?", Integer.class, variantId, productId) != 1)
            throw new ProductException(404, "SKU_NOT_FOUND", "SKU 不存在或不属于当前产品");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('products.read')")
    List<Document> list(@PathVariable String productId, @PathVariable String variantId, @RequestParam Kind kind) {
        requireVariant(productId, variantId);
        return jdbc.query("SELECT id,kind,file_name,byte_size,uploaded_at FROM sku_document WHERE variant_id=? AND kind=? ORDER BY uploaded_at DESC,id",
            (rs, index) -> new Document(rs.getString("id"), rs.getString("kind"), rs.getString("file_name"), rs.getLong("byte_size"), rs.getTimestamp("uploaded_at").toInstant().toString()),
            variantId, kind.name());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('products.write')")
    @Transactional
    Document upload(@PathVariable String productId, @PathVariable String variantId, @RequestParam Kind kind,
                    @RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedActor actor) throws IOException {
        requireVariant(productId, variantId);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE id=? AND deleted_at IS NULL",Integer.class,variantId)!=1)
            throw new ProductException(404,"SKU_NOT_FOUND","SKU 已删除，不能上传新资料");
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new ProductException(400, "SKU_DOCUMENT_SIZE_INVALID", "文件不能为空，且不能超过 10 MB");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c -> c < 32 || c == 127))
            throw new ProductException(400, "SKU_DOCUMENT_NAME_INVALID", "文件名称无效或过长");
        String lower = name.toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();
        boolean valid = (lower.endsWith(".pdf") && starts(bytes, "%PDF-"))
            || (lower.endsWith(".png") && bytes.length >= 8 && bytes[0] == (byte) 137 && starts(java.util.Arrays.copyOfRange(bytes, 1, bytes.length), "PNG\r\n"))
            || ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255)
            || ((lower.endsWith(".docx") || lower.endsWith(".xlsx")) && validOffice(bytes, lower.endsWith(".docx") ? "word/document.xml" : "xl/workbook.xml"))
            || ((lower.endsWith(".txt") || lower.endsWith(".zpl")) && validText(bytes));
        if (!valid) throw new ProductException(400, "SKU_DOCUMENT_TYPE_INVALID", "支持 PDF、PNG、JPG、DOCX、XLSX、TXT、ZPL，请检查文件格式及内容");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sku_document(id,variant_id,kind,file_name,byte_size,content,uploaded_by) VALUES(?,?,?,?,?,?,?)",
            id, variantId, kind.name(), name, bytes.length, bytes, actor.userId());
        audit.record(new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), "SKU_DOCUMENT_UPLOAD", "PRODUCT", productId, true,
            null, Map.of("documentId", id, "variantId", variantId, "kind", kind.name(), "byteSize", bytes.length), "上传 SKU 说明书或商品标签"));
        return list(productId, variantId, kind).stream().filter(document -> document.id().equals(id)).findFirst().orElseThrow();
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAuthority('products.read')")
    ResponseEntity<byte[]> download(@PathVariable String productId, @PathVariable String variantId, @PathVariable String documentId) {
        requireVariant(productId, variantId);
        return jdbc.query("SELECT file_name,content FROM sku_document WHERE id=? AND variant_id=?", (rs, index) ->
            ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(rs.getString("file_name"), StandardCharsets.UTF_8).build().toString())
                .body(rs.getBytes("content")), documentId, variantId).stream().findFirst()
            .orElseThrow(() -> new ProductException(404, "SKU_DOCUMENT_NOT_FOUND", "文件不存在或不属于当前 SKU"));
    }
    private boolean starts(byte[] bytes, String prefix) {
        return new String(bytes, 0, Math.min(bytes.length, prefix.length()), StandardCharsets.US_ASCII).equals(prefix);
    }
    private boolean validText(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return value.chars().noneMatch(c -> c == 0);
        } catch (java.nio.charset.CharacterCodingException e) { return false; }
    }
    private boolean validOffice(byte[] bytes, String requiredEntry) {
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            boolean types = false, document = false;
            long total = 0; int entries = 0;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 2000) return false;
                types |= entry.getName().equals("[Content_Types].xml");
                document |= entry.getName().equals(requiredEntry);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) { total += read; if (total > 50L * 1024 * 1024) return false; }
            }
            return types && document;
        } catch (IOException e) { return false; }
    }
}
