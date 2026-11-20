package com.jiugeng.erp.customs;
import java.util.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-shippers") @Transactional
class CustomsShippers {
 record Shipper(String id,String customerId,String name,String prefix,String address,String phone,String fax){}
 private final JdbcClient jdbc;private final AuditTrail audit;
 CustomsShippers(JdbcClient jdbc,AuditTrail audit){this.jdbc=jdbc;this.audit=audit;}
 @GetMapping @PreAuthorize("hasAuthority('products.read')") @Transactional(readOnly=true)
 List<Shipper> list(){return jdbc.sql("SELECT id,export_company_name,export_company_prefix,export_company_address,export_company_phone,export_company_fax FROM customer WHERE export_enabled=TRUE AND status='ACTIVE' ORDER BY export_company_name,id").query((r,n)->new Shipper(r.getString(1),r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6))).list();}
}
