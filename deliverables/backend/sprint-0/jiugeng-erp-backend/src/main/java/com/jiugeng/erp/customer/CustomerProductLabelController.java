package com.jiugeng.erp.customer;

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
@RequestMapping("/api/v1/customers/products/{customerProductId}/labels")
class CustomerProductLabelController {
    private com.jiugeng.erp.objectstorage.PrivateObjectStorage objects =
        new com.jiugeng.erp.objectstorage.PrivateObjectStorage(null, "", "erp/");
    @org.springframework.beans.factory.annotation.Autowired
    void configureStorage(com.jiugeng.erp.objectstorage.PrivateObjectStorage storage) { this.objects = storage; }
    record Document(String id, String kind, String fileName, long byteSize, String uploadedAt) {}
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;
    CustomerProductLabelController(JdbcTemplate jdbc, AuditTrail audit) { this.jdbc = jdbc; this.audit = audit; }

    private void requireProduct(String customerProductId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customer_product WHERE id=? AND deleted_at IS NULL", Integer.class, customerProductId) != 1)
            throw new CustomerException(404, "CUSTOMER_PRODUCT_NOT_FOUND", "客户商品不存在或已删除");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customers.read')")
    List<Document> list(@PathVariable String customerProductId) {
        requireProduct(customerProductId);
        return jdbc.query("SELECT id,kind,file_name,byte_size,uploaded_at FROM customer_product_label WHERE customer_product_id=? AND kind=? ORDER BY uploaded_at DESC,id",
            (rs, index) -> new Document(rs.getString("id"), rs.getString("kind"), rs.getString("file_name"), rs.getLong("byte_size"), rs.getTimestamp("uploaded_at").toInstant().toString()),
            customerProductId, "LABEL");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('customers.write')")
    @Transactional
    Document upload(@PathVariable String customerProductId,
                    @RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedActor actor) throws IOException {
        requireProduct(customerProductId);
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new CustomerException(400, "CUSTOMER_PRODUCT_LABEL_SIZE_INVALID", "文件不能为空，且不能超过 10 MB");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c -> c < 32 || c == 127))
            throw new CustomerException(400, "CUSTOMER_PRODUCT_LABEL_NAME_INVALID", "文件名称无效或过长");
        String lower = name.toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();
        boolean valid = (lower.endsWith(".pdf") && starts(bytes, "%PDF-"))
            || (lower.endsWith(".png") && bytes.length >= 8 && bytes[0] == (byte) 137 && starts(java.util.Arrays.copyOfRange(bytes, 1, bytes.length), "PNG\r\n"))
            || ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255)
            || ((lower.endsWith(".docx") || lower.endsWith(".xlsx")) && validOffice(bytes, lower.endsWith(".docx") ? "word/document.xml" : "xl/workbook.xml"))
            || ((lower.endsWith(".txt") || lower.endsWith(".zpl")) && validText(bytes));
        if (!valid) throw new CustomerException(400, "CUSTOMER_PRODUCT_LABEL_TYPE_INVALID", "支持 PDF、PNG、JPG、DOCX、XLSX、TXT、ZPL，请检查文件格式及内容");
        String id = UUID.randomUUID().toString();
        objects.putDocument("customer-product-labels", id, bytes);
        jdbc.update("INSERT INTO customer_product_label(id,customer_product_id,kind,file_name,byte_size,content,uploaded_by) VALUES(?,?,?,?,?,?,?)",
            id, customerProductId, "LABEL", name, bytes.length, bytes, actor.userId());
        audit.record(new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), "CUSTOMER_PRODUCT_LABEL_UPLOAD", "CUSTOMER_PRODUCT", customerProductId, true,
            null, Map.of("documentId", id, "customerProductId", customerProductId, "kind", "LABEL", "byteSize", bytes.length), "上传客户商品标签"));
        return list(customerProductId).stream().filter(document -> document.id().equals(id)).findFirst().orElseThrow();
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAuthority('customers.read')")
    ResponseEntity<byte[]> download(@PathVariable String customerProductId, @PathVariable String documentId) {
        requireProduct(customerProductId);
        return jdbc.query("SELECT file_name,content FROM customer_product_label WHERE id=? AND customer_product_id=?", (rs, index) ->
            ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(rs.getString("file_name"), StandardCharsets.UTF_8).build().toString())
                .body(objects.readOrLegacy("customer-product-labels", documentId, rs.getBytes("content"))), documentId, customerProductId).stream().findFirst()
            .orElseThrow(() -> new CustomerException(404, "CUSTOMER_PRODUCT_LABEL_NOT_FOUND", "文件不存在或不属于当前客户商品"));
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
