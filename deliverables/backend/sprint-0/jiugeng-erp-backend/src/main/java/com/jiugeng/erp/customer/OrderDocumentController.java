package com.jiugeng.erp.customer;

import static com.jiugeng.erp.customer.CustomerOrderService.invalid;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.objectstorage.PrivateObjectStorage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customer-orders/{orderId}/documents/{scope}")
class OrderDocumentController {
    record Document(String id,String fileName,long byteSize) {}
    private final CustomerOrderService orders;private final JdbcClient jdbc;private final PrivateObjectStorage storage;
    OrderDocumentController(CustomerOrderService orders,JdbcClient jdbc,PrivateObjectStorage storage) {this.orders=orders;this.jdbc=jdbc;this.storage=storage;}
    void owner(String orderId,String scope) {
        var order=orders.get(orderId);
        if(!List.of("ORDER","INVOICE").contains(scope))throw invalid("附件类型无效");
        if(scope.equals("INVOICE")&&order.invoiceId()==null)throw invalid("请先生成发票");
    }
    @GetMapping @PreAuthorize("hasAuthority('customers.read')")
    List<Document> list(@PathVariable String orderId,@PathVariable String scope) {
        owner(orderId,scope);return jdbc.sql("SELECT id,file_name,byte_size FROM customer_order_document WHERE order_id=:order AND scope=:scope ORDER BY uploaded_at,id")
            .param("order",orderId).param("scope",scope).query((r,n)->new Document(r.getString("id"),r.getString("file_name"),r.getLong("byte_size"))).list();
    }
    @PostMapping @PreAuthorize("hasAuthority('customers.write')") @Transactional
    Document upload(@PathVariable String orderId,@PathVariable String scope,@RequestParam MultipartFile file,@AuthenticationPrincipal AuthenticatedActor actor) throws java.io.IOException {
        orders.lock(orderId);owner(orderId,scope);
        if(file.isEmpty()||file.getSize()>10*1024*1024)throw invalid("附件不能为空且不得超过10MB");
        String name=file.getOriginalFilename();
        if(name==null||name.isBlank()||name.length()>200||name.contains("/")||name.contains("\\")||name.codePoints().anyMatch(Character::isISOControl))throw invalid("文件名无效");
        var bytes=file.getBytes();String lower=name.toLowerCase(Locale.ROOT);
        boolean valid=(lower.endsWith(".pdf")&&starts(bytes,new byte[]{37,80,68,70,45}))
            ||(lower.endsWith(".png")&&starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10}))
            ||((lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255}));
        if(!valid)throw invalid("仅支持内容匹配的PDF、PNG、JPG附件");
        String id=UUID.randomUUID().toString();storage.putDocument("order-documents",id,bytes);
        jdbc.sql("INSERT INTO customer_order_document(id,order_id,scope,file_name,byte_size,content) VALUES(:id,:order,:scope,:name,:size,:content)")
            .param("id",id).param("order",orderId).param("scope",scope).param("name",name).param("size",bytes.length).param("content",bytes).update();
        orders.record(actor,orderId,"ORDER_DOCUMENT_UPLOAD","新增"+(scope.equals("ORDER")?"订单":"发票")+"附件；文件ID："+id);
        return new Document(id,name,bytes.length);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('customers.read')")
    ResponseEntity<byte[]> download(@PathVariable String orderId,@PathVariable String scope,@PathVariable String id) {
        owner(orderId,scope);return jdbc.sql("SELECT file_name,content FROM customer_order_document WHERE id=:id AND order_id=:order AND scope=:scope")
            .param("id",id).param("order",orderId).param("scope",scope).query((r,n)->ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(r.getString("file_name"),StandardCharsets.UTF_8).build().toString())
                .body(storage.readOrLegacy("order-documents",id,r.getBytes("content")))).optional().orElseThrow(()->new CustomerException(404,"ORDER_DOCUMENT_NOT_FOUND","附件不存在"));
    }
    private boolean starts(byte[] bytes,byte[] prefix) {return bytes.length>=prefix.length&&Arrays.equals(Arrays.copyOf(bytes,prefix.length),prefix);}
}
