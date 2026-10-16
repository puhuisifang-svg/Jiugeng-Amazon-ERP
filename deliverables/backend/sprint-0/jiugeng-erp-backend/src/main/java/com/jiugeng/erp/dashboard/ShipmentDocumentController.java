package com.jiugeng.erp.dashboard;

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
@RequestMapping("/api/v1/shipment-documents")
class ShipmentDocumentController {
    enum Kind { LABEL, CARTON_LABEL }
    record Document(String id, String kind, String fileName, long byteSize, String uploadedAt) {}
    record Owner(String todayId, String productId) {}
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;
    ShipmentDocumentController(JdbcTemplate jdbc, AuditTrail audit) { this.jdbc=jdbc; this.audit=audit; }

    private Owner owner(String scope, String id, boolean writing) {
        if (scope.equals("today-shipments")) {
            return jdbc.query("SELECT id,product_id,status FROM dashboard_today_shipment WHERE id=?" + (writing ? " FOR UPDATE" : ""), (rs,n) -> {
                if (writing && !rs.getString("status").equals("ACTIVE"))
                    throw new DashboardException(409,"SHIPMENT_STATE_INVALID","仅有效的今日发货记录可以上传标签");
                return new Owner(rs.getString("id"),rs.getString("product_id"));
            },id).stream().findFirst().orElseThrow(() -> missing());
        }
        if (!writing && scope.equals("pending-shipments")) {
            return jdbc.query("SELECT p.product_id,t.id FROM dashboard_pending_shipment p LEFT JOIN dashboard_today_shipment t ON t.source_pending_shipment_id=p.id WHERE p.id=?",
                (rs,n) -> new Owner(rs.getString("id"),rs.getString("product_id")),id)
                .stream().findFirst().orElseThrow(() -> missing());
        }
        throw missing();
    }
    private DashboardException missing() { return new DashboardException(404,"SHIPMENT_DOCUMENT_NOT_FOUND","发货记录或文件不存在"); }

    @GetMapping("/{scope}/{shipmentId}")
    @PreAuthorize("hasAuthority('dashboard.read')")
    List<Document> list(@PathVariable String scope,@PathVariable String shipmentId,@RequestParam Kind kind) {
        var owner=owner(scope,shipmentId,false);
        return jdbc.query("SELECT id,kind,file_name,byte_size,uploaded_at FROM dashboard_shipment_document WHERE today_shipment_id=? AND product_id=? AND kind=? ORDER BY uploaded_at DESC,id",
            (rs,n)->new Document(rs.getString("id"),rs.getString("kind"),rs.getString("file_name"),rs.getLong("byte_size"),rs.getTimestamp("uploaded_at").toInstant().toString()),
            owner.todayId(),owner.productId(),kind.name());
    }

    @PostMapping(value="/today-shipments/{shipmentId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    @Transactional
    Document upload(@PathVariable String shipmentId,@RequestParam Kind kind,@RequestParam MultipartFile file,
                    @AuthenticationPrincipal AuthenticatedActor actor) throws IOException {
        var owner=owner("today-shipments",shipmentId,true);
        if(file.isEmpty() || file.getSize()>10*1024*1024)
            throw new DashboardException(400,"DOCUMENT_SIZE_INVALID","文件不能为空，且不能超过 10 MB");
        String name=file.getOriginalFilename();
        if(name==null || name.isBlank() || name.length()>200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c->c<32 || c==127))
            throw new DashboardException(400,"DOCUMENT_NAME_INVALID","文件名称无效或过长");
        byte[] bytes=file.getBytes(); String lower=name.toLowerCase(Locale.ROOT);
        boolean valid=(lower.endsWith(".pdf") && starts(bytes,new byte[]{37,80,68,70,45}))
            || (lower.endsWith(".png") && starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10}))
            || ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255}))
            || ((lower.endsWith(".txt") || lower.endsWith(".zpl")) && validText(bytes));
        if(!valid)throw new DashboardException(400,"DOCUMENT_TYPE_INVALID","支持 PDF、PNG、JPG、TXT、ZPL，请检查文件格式及内容");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO dashboard_shipment_document(id,today_shipment_id,product_id,kind,file_name,byte_size,content,uploaded_by) VALUES(?,?,?,?,?,?,?,?)",
            id,owner.todayId(),owner.productId(),kind.name(),name,bytes.length,bytes,actor.userId());
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"SHIPMENT_DOCUMENT_UPLOAD","TODAY_SHIPMENT",shipmentId,true,
            null,Map.of("documentId",id,"kind",kind.name(),"fileName",name),"上传"+(kind==Kind.LABEL?"商品标签":"外箱标签")+"："+name));
        return list("today-shipments",shipmentId,kind).stream().filter(d->d.id().equals(id)).findFirst().orElseThrow();
    }

    @GetMapping("/{scope}/{shipmentId}/{documentId}/download")
    @PreAuthorize("hasAuthority('dashboard.read')")
    ResponseEntity<byte[]> download(@PathVariable String scope,@PathVariable String shipmentId,@PathVariable String documentId) {
        var owner=owner(scope,shipmentId,false);
        return jdbc.query("SELECT file_name,content FROM dashboard_shipment_document WHERE id=? AND today_shipment_id=? AND product_id=?",(rs,n)->
            ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(rs.getString("file_name"),StandardCharsets.UTF_8).build().toString())
                .body(rs.getBytes("content")),documentId,owner.todayId(),owner.productId()).stream().findFirst().orElseThrow(()->missing());
    }
    private boolean starts(byte[] bytes,byte[] prefix) { return bytes.length>=prefix.length && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,prefix.length),prefix); }
    private boolean validText(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).chars().noneMatch(c->c==0); }
        catch(java.nio.charset.CharacterCodingException e) { return false; }
    }
}
