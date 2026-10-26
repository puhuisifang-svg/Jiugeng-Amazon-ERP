package com.jiugeng.erp.customer;

import static com.jiugeng.erp.customer.CustomerOrderTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerOrderService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final CustomerInvoiceTitles titles;
    private final CustomerProductLookup customerProducts;
    private final ProductLookup products;
    private final AuditTrail audit;
    CustomerOrderService(JdbcClient jdbc,ObjectMapper mapper,CustomerInvoiceTitles titles,
        CustomerProductLookup customerProducts,ProductLookup products,AuditTrail audit) {
        this.jdbc=jdbc;this.mapper=mapper;this.titles=titles;this.customerProducts=customerProducts;this.products=products;this.audit=audit;
    }
    static CustomerException invalid(String message) { return new CustomerException(400,"ORDER_INVALID",message); }
    static CustomerException conflict(String message) { return new CustomerException(409,"ORDER_CONFLICT",message); }
    String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(Exception e) { throw new IllegalStateException(e); } }
    static String text(String value) { return value==null?"":value.trim(); }
    void lock(String id) {
        jdbc.sql("SELECT id FROM customer_order WHERE id=:id AND deleted=FALSE FOR UPDATE").param("id",id).query(String.class).optional()
            .orElseThrow(()->new CustomerException(404,"ORDER_NOT_FOUND","订单不存在"));
    }
    @Transactional(readOnly=true)
    CustomerTypes.Page<View> list(String q,String status,String customerId,String source,LocalDate from,LocalDate to,LocalDate deliveryFrom,LocalDate deliveryTo,String shipping,String invoiceStatus,int page,int size) {
        if(from!=null&&to!=null&&from.isAfter(to))throw invalid("开始日期不能晚于结束日期");
        var p=new HashMap<String,Object>();p.put("q","%"+text(q).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        p.put("status",text(status));p.put("customer",text(customerId));p.put("source",text(source));p.put("from",from);p.put("to",to);
        if(deliveryFrom!=null&&deliveryTo!=null&&deliveryFrom.isAfter(deliveryTo))throw invalid("交期开始日期不能晚于结束日期");
        p.put("deliveryFrom",deliveryFrom);p.put("deliveryTo",deliveryTo);p.put("shipping",text(shipping));p.put("invoiceStatus",text(invoiceStatus));
        String shipped="NOT EXISTS (SELECT 1 FROM customer_order_line l WHERE l.order_id=customer_order.id AND l.quantity>COALESCE((SELECT SUM(a.quantity) FROM customer_order_allocation a WHERE a.line_id=l.id AND a.state='SHIPPED'),0))";
        String invoiced="EXISTS (SELECT 1 FROM customer_order_invoice i WHERE i.order_id=customer_order.id AND i.invoiced=TRUE)";
        String invoiceState="CASE WHEN invoice_required=FALSE THEN 'NONE' WHEN "+invoiced+" THEN 'INVOICED' WHEN EXISTS (SELECT 1 FROM customer_order_invoice i WHERE i.order_id=customer_order.id) THEN 'PENDING' ELSE 'NOT_GENERATED' END";
        String shipmentState="CASE WHEN "+shipped+" THEN 'SHIPPED' WHEN EXISTS (SELECT 1 FROM customer_order_allocation a WHERE a.order_id=customer_order.id AND a.state='SHIPPED') THEN 'PARTIAL' ELSE 'UNSHIPPED' END";
        String actualStatus="CASE WHEN status='CONFIRMED' AND "+shipped+" AND (invoice_required=FALSE OR "+invoiced+") THEN 'COMPLETED' ELSE status END";
        String filter=" FROM customer_order WHERE deleted=FALSE AND (order_number LIKE :q ESCAPE '!' OR customer_name LIKE :q ESCAPE '!' OR external_number LIKE :q ESCAPE '!' OR payload LIKE :q ESCAPE '!') AND (:status='' OR ("+actualStatus+")=:status) AND (:customer='' OR customer_id=:customer) AND (:source='' OR source=:source) AND (:from IS NULL OR order_date>=:from) AND (:to IS NULL OR order_date<=:to) AND (:deliveryFrom IS NULL OR delivery_date>=:deliveryFrom) AND (:deliveryTo IS NULL OR delivery_date<=:deliveryTo) AND (:shipping='' OR ("+shipmentState+")=:shipping) AND (:invoiceStatus='' OR ("+invoiceState+")=:invoiceStatus)";
        long total=jdbc.sql("SELECT COUNT(*)"+filter).params(p).query(Long.class).single();p.put("limit",size);p.put("offset",page*size);
        var rows=jdbc.sql("SELECT *"+filter+" ORDER BY updated_at DESC,id LIMIT :limit OFFSET :offset").params(p).query(this::map).list();
        return new CustomerTypes.Page<>(rows,total,page,size);
    }
    @Transactional(readOnly=true)
    View get(String id) {
        return jdbc.sql("SELECT * FROM customer_order WHERE id=:id AND deleted=FALSE").param("id",id).query(this::map).optional()
            .orElseThrow(()->new CustomerException(404,"ORDER_NOT_FOUND","订单不存在"));
    }
    private View map(ResultSet r,int row) throws SQLException {
        String id=r.getString("id");
        var lines=jdbc.sql("SELECT id,payload FROM customer_order_line WHERE order_id=:id ORDER BY position_index").param("id",id).query((rs,n)-> {
            var line=read(rs.getString("payload"),Line.class);
            var counts=jdbc.sql("SELECT COALESCE(SUM(CASE WHEN state IN ('PENDING','TODAY') THEN quantity ELSE 0 END),0) reserved, COALESCE(SUM(CASE WHEN state='SHIPPED' THEN quantity ELSE 0 END),0) shipped FROM customer_order_allocation WHERE line_id=:id")
                .param("id",line.id()).query((a,b)->List.of(a.getBigDecimal("reserved"),a.getBigDecimal("shipped"))).single();
            return new Line(line.id(),line.details(),line.product(),line.skuCode(),line.namePresentation(),line.netAmount(),line.taxAmount(),line.totalAmount(),counts.get(0),counts.get(1));
        }).list();
        var invoice=jdbc.sql("SELECT id,invoiced FROM customer_order_invoice WHERE order_id=:id").param("id",id)
            .query((a,b)->new Object[]{a.getString("id"),a.getBoolean("invoiced")}).optional().orElse(new Object[]{null,false});
        String status=r.getString("status");
        if(status.equals("CONFIRMED")&&lines.stream().allMatch(l->l.shippedQuantity().compareTo(l.details().quantity())==0)
            &&(!r.getBoolean("invoice_required")||(boolean)invoice[1]))status="COMPLETED";
        return new View(id,r.getString("order_number"),r.getString("customer_name"),status,read(r.getString("payload"),Input.class),
            read(r.getString("invoice_title_snapshot"),CustomerTypes.InvoiceTitle.class),lines,r.getBigDecimal("net_amount"),r.getBigDecimal("tax_amount"),r.getBigDecimal("total_amount"),r.getLong("version"),(String)invoice[0],(boolean)invoice[1]);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    View save(String id,Input input,AuthenticatedActor actor,String key) {
        View before=null;
        if(id!=null) { lock(id);before=get(id);if(input.version()==null||before.version()!=input.version())throw conflict("订单已更新，请刷新");
            if(!List.of("DRAFT","CONFIRMED").contains(before.status()))throw conflict("当前订单状态不可编辑");
            boolean linked=before.invoiceId()!=null||jdbc.sql("SELECT COUNT(*) FROM customer_order_allocation WHERE order_id=:id").param("id",id).query(Long.class).single()>0;
            if(linked&&!before.details().customerId().equals(input.customerId()))throw conflict("已关联发货或发票，不能更换客户");
            if(before.invoiceId()!=null&&before.details().invoiceRequired()!=input.invoiceRequired())throw conflict("已生成发票，不能改变开票要求");
            if(before.invoiced()&&(!Objects.equals(before.details().invoiceTitle(),input.invoiceTitle())||!Objects.equals(before.details().taxNumber(),input.taxNumber())))throw conflict("已开票订单不能更换抬头");
        }
        String customerName=jdbc.sql("SELECT name FROM customer WHERE id=:id AND status='ACTIVE' FOR UPDATE").param("id",input.customerId()).query(String.class).optional()
            .orElseThrow(()->invalid("请选择启用客户"));
        if(before!=null&&before.details().customerId().equals(input.customerId()))customerName=before.customerName();
        String hash;
        try {hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json(input).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        if(id==null) {
            if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw invalid("请求标识无效");
            var prior=jdbc.sql("SELECT payload_hash,order_id FROM customer_order_command WHERE actor_id=:actor AND request_key=:key")
                .param("actor",actor.userId()).param("key",key).query((r,n)-> {if(!r.getString("payload_hash").equals(hash))throw conflict("请求标识已用于不同订单内容");return r.getString("order_id");}).optional();
            if(prior.isPresent())return get(prior.get());
        }
        if(input.source().equals("1688")&&text(input.externalNumber()).isEmpty())throw invalid("1688订单必须填写客户订单号");
        if(input.deliveryDate().isBefore(input.orderDate()))throw invalid("交期不能早于下单日期");
        CustomerTypes.InvoiceTitle title=new CustomerTypes.InvoiceTitle("","","","","","");
        boolean retainedTitle=before!=null&&before.details().customerId().equals(input.customerId())&&Objects.equals(before.details().invoiceTitle(),input.invoiceTitle())&&Objects.equals(text(before.details().taxNumber()),text(input.taxNumber()));
        if(input.invoiceRequired()&&retainedTitle)title=before.invoiceTitle();
        else if(input.invoiceRequired()&&!text(input.invoiceTitle()).isEmpty())title=titles.get(input.customerId()).stream()
            .filter(t->t.invoiceTitle().equals(input.invoiceTitle())&&Objects.equals(text(t.taxNumber()),text(input.taxNumber())))
            .findFirst().orElseThrow(()->invalid("请选择当前客户的有效发票抬头"));
        if(before!=null&&before.status().equals("CONFIRMED"))confirmable(input,title);
        String orderId=id==null?UUID.randomUUID().toString():id;
        var lines=new ArrayList<Line>();var ids=new HashSet<String>();
        for(var d:input.lines()) {
            var original=before==null?null:before.lines().stream().filter(l->l.id().equals(d.id())).findFirst().orElse(null);
            var cp=(original!=null&&original.product().id().equals(d.customerProductId())?customerProducts.forExisting(d.customerProductId()):customerProducts.active(d.customerProductId())).orElseThrow(()->invalid("客户商品已停用或不存在"));
            if(!cp.customerId().equals(input.customerId()))throw invalid("商品不属于当前客户");
            var sku=(original!=null?products.findExistingSku(cp.productId(),cp.variantId()):products.findSku(cp.productId(),cp.variantId())).orElseThrow(()->invalid("SKU不存在"));
            if(!sku.active())throw invalid("SKU已停用");
            if(d.deliveryDate().isBefore(input.orderDate()))throw invalid("明细交期不能早于下单日期");
            String lineId=before==null||d.id()==null?UUID.randomUUID().toString():d.id();
            if(before!=null&&d.id()!=null&&before.lines().stream().noneMatch(l->l.id().equals(d.id())))throw invalid("明细不属于当前订单");
            if(!ids.add(lineId))throw invalid("明细ID重复");
            if(original!=null) {
                boolean referenced=jdbc.sql("SELECT COUNT(*) FROM customer_order_allocation WHERE line_id=:id").param("id",lineId).query(Long.class).single()>0;
                if(referenced&&(!original.product().id().equals(d.customerProductId())||!original.details().unit().equals(d.unit())))throw conflict("已安排发货的明细不能更换商品或单位");
                if(d.quantity().compareTo(original.allocatedQuantity().add(original.shippedQuantity()))<0)throw conflict("订单数量不能小于已安排与已发货数量");
                if(before.invoiced()&&!financial(original.details()).equals(financial(d)))throw conflict("已开票订单不能修改商品和金额相关信息");
            } else if(before!=null&&before.invoiced())throw conflict("已开票订单不能增加明细");
            var net=d.quantity().multiply(d.netUnitPrice()).setScale(2,RoundingMode.HALF_UP);
            var tax=net.multiply(d.taxRate()).movePointLeft(2).setScale(2,RoundingMode.HALF_UP);
            var presentation=original!=null&&original.product().id().equals(cp.id())&&original.details().name().equals(d.name())?original.namePresentation():products.namePresentation(cp.productId(),cp.variantId(),d.name());
            lines.add(new Line(lineId,d,original!=null&&original.product().id().equals(cp.id())?original.product():cp,sku.skuCode(),presentation,net,tax,net.add(tax),BigDecimal.ZERO,BigDecimal.ZERO));
        }
        BigDecimal net=lines.stream().map(Line::netAmount).reduce(BigDecimal.ZERO,BigDecimal::add), tax=lines.stream().map(Line::taxAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        var p=new HashMap<String,Object>();p.put("id",orderId);p.put("customer",input.customerId());p.put("name",customerName);p.put("source",input.source());
        p.put("external",text(input.externalNumber()).isEmpty()?null:text(input.externalNumber()));p.put("required",input.invoiceRequired());p.put("date",input.orderDate());p.put("delivery",input.deliveryDate());
        p.put("payload",json(input));p.put("title",json(title));p.put("net",net);p.put("tax",tax);p.put("total",net.add(tax));
        p.put("number","CO-"+LocalDate.now().toString().replace("-","")+"-"+orderId.substring(0,8).toUpperCase(Locale.ROOT));
        try {
            if(before==null)jdbc.sql("INSERT INTO customer_order(id,order_number,customer_id,customer_name,source,external_number,invoice_required,order_date,delivery_date,payload,invoice_title_snapshot,net_amount,tax_amount,total_amount) VALUES(:id,:number,:customer,:name,:source,:external,:required,:date,:delivery,:payload,:title,:net,:tax,:total)").params(p).update();
            else jdbc.sql("UPDATE customer_order SET customer_id=:customer,customer_name=:name,source=:source,external_number=:external,invoice_required=:required,order_date=:date,delivery_date=:delivery,payload=:payload,invoice_title_snapshot=:title,net_amount=:net,tax_amount=:tax,total_amount=:total,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").params(p).update();
        } catch(org.springframework.dao.DuplicateKeyException e) { throw conflict("同一客户和来源的订单号已存在"); }
        if(before!=null)for(var old:before.lines())if(!ids.contains(old.id())) {
            if(before.invoiceId()!=null||jdbc.sql("SELECT COUNT(*) FROM customer_order_allocation WHERE line_id=:id").param("id",old.id()).query(Long.class).single()>0)throw conflict("已有发票或发货历史的明细不能移除");
            jdbc.sql("DELETE FROM customer_order_line WHERE id=:id").param("id",old.id()).update();
        }
        for(int n=0;n<lines.size();n++) {var line=lines.get(n);
            boolean exists=before!=null&&before.lines().stream().anyMatch(l->l.id().equals(line.id()));
            String sql=exists?"UPDATE customer_order_line SET position_index=:pos,customer_product_id=:cp,quantity=:qty,payload=:payload WHERE id=:id AND order_id=:order":"INSERT INTO customer_order_line(id,order_id,position_index,customer_product_id,quantity,payload) VALUES(:id,:order,:pos,:cp,:qty,:payload)";
            jdbc.sql(sql).param("id",line.id()).param("order",orderId).param("pos",n).param("cp",line.details().customerProductId()).param("qty",line.details().quantity()).param("payload",json(line)).update();}
        if(before==null)try {jdbc.sql("INSERT INTO customer_order_command(actor_id,request_key,payload_hash,order_id) VALUES(:actor,:key,:hash,:id)")
            .param("actor",actor.userId()).param("key",key).param("hash",hash).param("id",orderId).update();}
        catch(org.springframework.dao.DuplicateKeyException e) {throw conflict("请求标识正在处理或已用于其他订单，请刷新后重试");}
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),before==null?"ORDER_CREATE":"ORDER_UPDATE","CUSTOMER_ORDER",orderId,true,
            before==null?null:Map.of("details",before.details(),"lines",before.lines()),Map.of("details",input,"lines",lines),"订单内容已保存"));return get(orderId);
    }
    private Map<String,Object> financial(LineInput l) {return Map.of("customerProductId",l.customerProductId(),"name",l.name(),"specification",text(l.specification()),"unit",l.unit(),"quantity",l.quantity().stripTrailingZeros(),"netUnitPrice",l.netUnitPrice().stripTrailingZeros(),"taxRate",l.taxRate().stripTrailingZeros());}
    private void confirmable(Input i,CustomerTypes.InvoiceTitle title) {
        if(text(i.recipient()).isEmpty()||text(i.phone()).isEmpty()||text(i.address()).isEmpty())throw invalid("确认订单前请填写收货人、电话和地址");
        if(i.invoiceRequired()&&text(title.invoiceTitle()).isEmpty())throw invalid("需开票订单必须选择发票抬头");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    View transition(String id,Transition input,AuthenticatedActor actor) {
        lock(id);var value=get(id);if(value.version()!=input.version())throw conflict("订单已更新，请刷新");
        String status=value.status();boolean deleted=false;
        switch(input.action()) {
            case "CONFIRM" -> {if(!status.equals("DRAFT"))throw conflict("仅草稿可确认");confirmable(value.details(),value.invoiceTitle());status="CONFIRMED";}
            case "DELETE" -> {if(!status.equals("DRAFT"))throw conflict("仅草稿可删除");deleted=true;}
            case "CANCEL","CLOSE" -> {
                if(!List.of("DRAFT","CONFIRMED").contains(status))throw conflict("当前状态不可取消或关闭");
                if(text(input.reason()).isEmpty())throw invalid("请填写原因");
                if(jdbc.sql("SELECT COUNT(*) FROM customer_order_allocation WHERE order_id=:id AND state IN ('PENDING','TODAY')").param("id",id).query(Long.class).single()>0)throw conflict("请先删除未完成发货安排");
                boolean performed=value.lines().stream().anyMatch(l->l.shippedQuantity().signum()>0)||value.invoiced();
                if(input.action().equals("CANCEL")&&performed)throw conflict("已有发货或开票事实，请使用关闭订单");
                if(value.invoiceId()!=null&&!value.invoiced())throw conflict("订单已有待开票发票，不能取消或关闭");
                status=input.action().equals("CANCEL")?"CANCELLED":"CLOSED";
            }
            default -> throw invalid("操作无效");
        }
        jdbc.sql("UPDATE customer_order SET status=:status,deleted=:deleted,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("status",status).param("deleted",deleted).param("id",id).update();
        record(actor,id,"ORDER_"+input.action(),text(input.reason()));return deleted?value:get(id);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Invoice generateInvoice(String orderId,AuthenticatedActor actor) {
        lock(orderId);var order=get(orderId);
        if(order.invoiceId()!=null)return invoice(order.invoiceId());
        if(!order.status().equals("CONFIRMED")||!order.details().invoiceRequired())throw conflict("仅已确认且需开票订单可生成发票");
        confirmable(order.details(),order.invoiceTitle());String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO customer_order_invoice(id,order_id,snapshot) VALUES(:id,:order,:snapshot)").param("id",id).param("order",orderId).param("snapshot",json(order)).update();
        record(actor,orderId,"ORDER_INVOICE_CREATE","生成订单唯一多商品发票");return invoice(id);
    }
    @Transactional(readOnly=true)
    Invoice invoice(String id) { return jdbc.sql("SELECT * FROM customer_order_invoice WHERE id=:id").param("id",id).query((r,n)->new Invoice(r.getString("id"),r.getString("order_id"),read(r.getString("snapshot"),View.class),r.getBoolean("invoiced"),r.getString("invoice_number"),r.getDate("invoice_date")==null?null:r.getDate("invoice_date").toLocalDate(),r.getLong("version"))).optional().orElseThrow(()->new CustomerException(404,"ORDER_INVOICE_NOT_FOUND","发票不存在")); }
    @Transactional(readOnly=true)
    CustomerTypes.Page<Invoice> invoices(String q,int page,int size) {
        var p=Map.of("q","%"+text(q).replace("!","!!").replace("%","!%").replace("_","!_")+"%","limit",size,"offset",page*size);
        String filter=" FROM customer_order_invoice i JOIN customer_order o ON o.id=i.order_id WHERE (o.order_number LIKE :q ESCAPE '!' OR o.customer_name LIKE :q ESCAPE '!' OR i.invoice_number LIKE :q ESCAPE '!')";
        long count=jdbc.sql("SELECT COUNT(*)"+filter).params(p).query(Long.class).single();
        var ids=jdbc.sql("SELECT i.id"+filter+" ORDER BY i.updated_at DESC,i.id LIMIT :limit OFFSET :offset").params(p).query(String.class).list();
        return new CustomerTypes.Page<>(ids.stream().map(this::invoice).toList(),count,page,size);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Invoice updateInvoice(String id,InvoiceUpdate input,AuthenticatedActor actor) {
        var initial=invoice(id);lock(initial.orderId());var value=invoice(id);
        if(value.version()!=input.version())throw conflict("发票已更新，请刷新");
        if(value.invoiced())throw conflict("已开票记录已锁定，不允许直接改写");
        if(input.invoiced()&&(text(input.invoiceNumber()).isEmpty()||input.invoiceDate()==null))throw invalid("标记已开票须填写发票号码与日期");
        var snapshot=input.refreshFromOrder()?get(value.orderId()):value.snapshot();
        if(input.invoiced()&&snapshot.version()!=get(value.orderId()).version())throw conflict("订单已变更，请勾选从订单更新后再确认开票");
        jdbc.sql("UPDATE customer_order_invoice SET snapshot=:snapshot,invoiced=:invoiced,invoice_number=:number,invoice_date=:date,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
            .param("snapshot",json(snapshot)).param("invoiced",input.invoiced()).param("number",text(input.invoiceNumber())).param("date",input.invoiceDate()).param("id",id).update();
        record(actor,value.orderId(),"ORDER_INVOICE_UPDATE",input.invoiced()?"登记已开票":"更新待开票信息");return invoice(id);
    }
    void record(AuthenticatedActor actor,String id,String action,String detail) {
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"CUSTOMER_ORDER",id,true,null,Map.of("orderId",id),detail));
    }
}
