package com.jiugeng.erp.customer;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.jiugeng.erp.customer.InvoiceTypes.*;

@Service
class InvoiceService {
    private final JdbcClient jdbc;
    private final AuditTrail audit;
    private final CustomerInvoiceTitles titles;
    InvoiceService(JdbcClient jdbc,AuditTrail audit,CustomerInvoiceTitles titles) { this.jdbc=jdbc; this.audit=audit; this.titles=titles; }

    @Transactional(readOnly=true)
    CustomerTypes.Page<View> list(String q,Boolean shipped,Boolean invoiced,int page,int size) {
        var params=new HashMap<String,Object>();
        params.put("q",q.trim().toLowerCase(Locale.ROOT)); params.put("term","%"+q.trim().toLowerCase(Locale.ROOT)+"%");
        params.put("shipped",shipped); params.put("invoiced",invoiced); params.put("size",size); params.put("offset",page*size);
        String filter="""
            FROM customer_invoice WHERE (:shipped IS NULL OR shipped=:shipped) AND (:invoiced IS NULL OR invoiced=:invoiced)
            AND (:q='' OR LOWER(customer_name) LIKE :term OR LOWER(invoice_title) LIKE :term
            OR LOWER(item_name) LIKE :term OR LOWER(specification) LIKE :term OR LOWER(logistics) LIKE :term)
            """;
        long total=jdbc.sql("SELECT COUNT(*) "+filter).params(params).query(Long.class).single();
        var items=jdbc.sql("SELECT * "+filter+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset").params(params).query(this::view).list();
        return new CustomerTypes.Page<>(items,total,page,size);
    }
    private record Source(String customerId,String customerName,Input draft) {}
    private Source source(String id) {
        return jdbc.sql("""
            SELECT p.customer_id,c.name,p.product_name,p.price_unit,c.invoice_title,c.tax_number,
                c.registered_address,c.registered_phone,c.bank_name,c.bank_account
            FROM customer_product p JOIN customer c ON c.id=p.customer_id
            WHERE p.id=:id AND p.deleted_at IS NULL AND p.status='ACTIVE' AND c.status='ACTIVE'
            """).param("id",id).query((r,n)->new Source(r.getString("customer_id"),r.getString("name"),
                new Input(id,r.getString("invoice_title"),r.getString("tax_number"),r.getString("registered_address"),
                    r.getString("registered_phone"),r.getString("bank_name"),r.getString("bank_account"),
                    r.getString("product_name"),"",r.getString("price_unit"),BigDecimal.ONE,null,BigDecimal.ONE,false,false,"",null)))
            .optional().orElseThrow(()->new CustomerException(409,"INVOICE_CUSTOMER_PRODUCT_INVALID","请选择启用客户的有效客户商品"));
    }
    @Transactional(readOnly=true)
    Draft draft(String id) {
        var source=source(id); var options=titles.get(source.customerId());
        var selected=options.size()==1?options.get(0):new CustomerTypes.InvoiceTitle("","","","","","");
        return new Draft(withBilling(source.draft(),selected),options);
    }
    private Input withBilling(Input i,CustomerTypes.InvoiceTitle t) {
        return new Input(i.customerProductId(),t.invoiceTitle(),t.taxNumber(),t.registeredAddress(),t.registeredPhone(),
            t.bankName(),t.bankAccount(),i.itemName(),i.specification(),i.unit(),i.quantity(),i.netUnitPrice(),
            i.taxRate(),i.shipped(),i.invoiced(),i.logistics(),i.version());
    }
    private View get(String id) {
        return jdbc.sql("SELECT * FROM customer_invoice WHERE id=:id").param("id",id).query(this::view).optional()
            .orElseThrow(()->new CustomerException(404,"INVOICE_NOT_FOUND","发票信息不存在"));
    }
    @Transactional
    View save(String id,Input input,AuthenticatedActor actor) {
        View before=id==null?null:get(id);
        if(before!=null && !before.details().customerProductId().equals(input.customerProductId()))
            throw new CustomerException(400,"INVOICE_CUSTOMER_IMMUTABLE","已建档发票不能更换客户商品，请另行新增");
        Source source=before==null?source(input.customerProductId()):null;
        if(source!=null) {
            jdbc.sql("SELECT id FROM customer_product WHERE id=:cp FOR UPDATE").param("cp",input.customerProductId()).query(String.class).single();
            jdbc.sql("SELECT id FROM customer WHERE id=:id FOR UPDATE").param("id",source.customerId()).query(String.class).single();
            source=source(input.customerProductId());
        }
        String customerId=before==null?source.customerId():before.customerId();
        String customerName=before==null?source.customerName():before.customerName();
        Input submitted=input;
        if(before==null) {
            var selected=titles.get(customerId).stream().filter(t->t.invoiceTitle().equals(text(submitted.invoiceTitle()))
                && Objects.equals(t.taxNumber(),text(submitted.taxNumber()))).findFirst()
                .orElseThrow(()->new CustomerException(400,"INVOICE_TITLE_INVALID","请选择该客户维护的发票抬头；资料变化后请重新选择客户商品"));
            input=withBilling(input,selected);
        } else {
            var old=before.details();
            input=withBilling(input,new CustomerTypes.InvoiceTitle(old.invoiceTitle(),old.taxNumber(),old.registeredAddress(),
                old.registeredPhone(),old.bankName(),old.bankAccount()));
        }
        if(id==null) id=UUID.randomUUID().toString();
        BigDecimal rate=input.taxRate()==null?BigDecimal.ONE:input.taxRate();
        BigDecimal net=input.quantity().multiply(input.netUnitPrice()).setScale(2,RoundingMode.HALF_UP);
        BigDecimal tax=net.multiply(rate).movePointLeft(2).setScale(2,RoundingMode.HALF_UP);
        BigDecimal total=net.add(tax);
        var p=new HashMap<String,Object>(); p.put("id",id);p.put("cp",input.customerProductId());p.put("customer",customerId);p.put("customerName",customerName);
        p.put("title",text(input.invoiceTitle()));p.put("taxNumber",text(input.taxNumber()));p.put("address",text(input.registeredAddress()));
        p.put("phone",text(input.registeredPhone()));p.put("bank",text(input.bankName()));p.put("account",text(input.bankAccount()));
        p.put("item",text(input.itemName()));p.put("spec",text(input.specification()));p.put("unit",text(input.unit()));
        p.put("quantity",input.quantity());p.put("price",input.netUnitPrice());p.put("rate",rate);p.put("net",net);p.put("tax",tax);p.put("total",total);
        p.put("shipped",input.shipped());p.put("invoiced",input.invoiced());p.put("logistics",text(input.logistics()));
        p.put("actor",actor.userId());p.put("version",input.version());
        if(before==null) jdbc.sql("""
            INSERT INTO customer_invoice(id,customer_product_id,customer_id,customer_name,invoice_title,tax_number,registered_address,
                registered_phone,bank_name,bank_account,item_name,specification,unit,quantity,net_unit_price,tax_rate,net_amount,tax_amount,
                total_amount,shipped,invoiced,logistics,created_by,updated_by)
            VALUES(:id,:cp,:customer,:customerName,:title,:taxNumber,:address,:phone,:bank,:account,:item,:spec,:unit,:quantity,:price,:rate,
                :net,:tax,:total,:shipped,:invoiced,:logistics,:actor,:actor)
            """).params(p).update();
        else if(jdbc.sql("""
            UPDATE customer_invoice SET invoice_title=:title,tax_number=:taxNumber,registered_address=:address,registered_phone=:phone,
                bank_name=:bank,bank_account=:account,item_name=:item,specification=:spec,unit=:unit,quantity=:quantity,net_unit_price=:price,
                tax_rate=:rate,net_amount=:net,tax_amount=:tax,total_amount=:total,shipped=:shipped,invoiced=:invoiced,logistics=:logistics,
                updated_by=:actor,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id AND version=:version
            """).params(p).update()!=1) throw new CustomerException(409,"INVOICE_VERSION_CONFLICT","记录已被修改，请刷新后重新编辑");
        View after=get(id);
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),
            before==null?"CUSTOMER_INVOICE_CREATE":"CUSTOMER_INVOICE_UPDATE","CUSTOMER_INVOICE",id,true,
            before==null?null:summary(before),summary(after),"开票信息台账变更（非税务开票操作）"));
        return after;
    }
    private Map<String,?> summary(View value) {
        return Map.of("customerId",value.customerId(),"customerProductId",value.details().customerProductId(),
            "quantity",value.details().quantity(),"netUnitPrice",value.details().netUnitPrice(),"taxRate",value.details().taxRate(),
            "totalAmount",value.totalAmount(),"shipped",value.details().shipped(),"invoiced",value.details().invoiced(),"version",value.details().version());
    }
    private String text(String value) {
        String result=value==null?"":value.trim();
        if(result.chars().anyMatch(c->c<32 && c!='\n' && c!='\t')) throw new CustomerException(400,"INVOICE_FIELD_INVALID","字段包含无效字符");
        return result;
    }
    private View view(ResultSet r,int row) throws SQLException {
        return new View(r.getString("id"),r.getString("customer_id"),r.getString("customer_name"),
            new Input(r.getString("customer_product_id"),r.getString("invoice_title"),r.getString("tax_number"),r.getString("registered_address"),
                r.getString("registered_phone"),r.getString("bank_name"),r.getString("bank_account"),r.getString("item_name"),r.getString("specification"),
                r.getString("unit"),r.getBigDecimal("quantity"),r.getBigDecimal("net_unit_price"),r.getBigDecimal("tax_rate"),r.getBoolean("shipped"),
                r.getBoolean("invoiced"),r.getString("logistics"),r.getLong("version")),r.getBigDecimal("net_amount"),r.getBigDecimal("tax_amount"),
                r.getBigDecimal("total_amount"),r.getTimestamp("updated_at").toInstant());
    }
}
