package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductInventoryService implements ProductPurchaseInventory {
    @Override @Transactional public String receive(String variantId,String requestId,BigDecimal quantity,String reason,AuthenticatedActor actor) {
        try { return transact(variantId,requestId,"INBOUND",quantity,reason,actor).item().id(); }
        catch(ProductException e){throw new ProductPurchaseInventory.Failure(e.status(),e.code(),e.getMessage());}
    }
    record Balance(String variantId, BigDecimal quantity, Instant updatedAt) {}
    record Entry(String id, String requestId, String variantId, String type, BigDecimal quantity,
                 BigDecimal signedQuantity, BigDecimal balanceAfter, String reason, String actorUserId,
                 String actorLoginName, Instant occurredAt) {}
    record Result(Entry item, boolean replayed) {}
    record History(List<Entry> items, long total, int page, int size) {}
    private final JdbcClient jdbc;
    private final AuditTrail audit;
    ProductInventoryService(JdbcClient jdbc, AuditTrail audit) { this.jdbc = jdbc; this.audit = audit; }
    List<Balance> balances(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        if (ids.size() > 10000) throw invalid("一次最多查询 10000 个子体库存");
        return jdbc.sql("SELECT b.* FROM product_inventory_balance b JOIN product_variant v ON v.id=b.variant_id JOIN product p ON p.id=v.product_id WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL AND b.variant_id IN (:ids)")
            .param("ids",ids).query((r,n)->new Balance(r.getString("variant_id"),r.getBigDecimal("quantity"),r.getTimestamp("updated_at").toInstant())).list();
    }
    History history(String id, int page, int size) {
        if(page<0||size<1||size>100) throw invalid("分页参数无效");
        long total=jdbc.sql("SELECT COUNT(*) FROM product_inventory_transaction WHERE variant_id=:id").param("id",id).query(Long.class).single();
        var entries=jdbc.sql("SELECT * FROM product_inventory_transaction WHERE variant_id=:id ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset")
            .param("id",id).param("size",size).param("offset",(long)page*size).query(this::entry).list();
        return new History(entries,total,page,size);
    }
    private Entry entry(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new Entry(r.getString("id"),r.getString("request_id"),r.getString("variant_id"),r.getString("transaction_type"),r.getBigDecimal("quantity"),r.getBigDecimal("signed_quantity"),r.getBigDecimal("balance_after"),r.getString("reason"),r.getString("actor_user_id"),r.getString("actor_login_name"),r.getTimestamp("occurred_at").toInstant());
    }
    @Transactional Result transact(String variantId,String requestId,String type,BigDecimal quantity,String reason,AuthenticatedActor actor) {
        if(!Set.of("INBOUND","OUTBOUND","ADJUST_IN","ADJUST_OUT").contains(type)) throw invalid("库存操作类型无效");
        if(quantity==null||quantity.signum()<=0||quantity.stripTrailingZeros().scale()>0||quantity.compareTo(new BigDecimal("999999999999999"))>0) throw invalid("数量必须为不超过 15 位的正整数");
        if(requestId==null||requestId.isBlank()||requestId.length()>100) throw invalid("请求编号不能为空或超长");
        if(reason!=null&&reason.length()>500) throw invalid("操作原因不能超过500字");
        String normalizedReason=reason==null?"":reason.trim();
        String parent=jdbc.sql("SELECT product_id FROM product_variant WHERE id=:id").param("id",variantId).query(String.class).optional().orElseThrow(()->new ProductException(404,"SKU_NOT_FOUND","子体不存在"));
        jdbc.sql("SELECT id FROM product WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",parent).query(String.class).optional().orElseThrow(()->new ProductException(409,"PRODUCT_DELETED","父体已删除"));
        jdbc.sql("SELECT id FROM product_variant WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",variantId).query(String.class).optional().orElseThrow(()->new ProductException(409,"SKU_DELETED","子体已删除"));
        var previous=jdbc.sql("SELECT * FROM product_inventory_transaction WHERE variant_id=:id AND request_id=:request").param("id",variantId).param("request",requestId).query(this::entry).optional();
        if(previous.isPresent()) {
            var old=previous.get();
            if(!old.actorUserId().equals(actor.userId())||!old.type().equals(type)||old.quantity().compareTo(quantity)!=0||!old.reason().equals(normalizedReason)) throw new ProductException(409,"REQUEST_ID_CONFLICT","同一请求编号不能用于不同库存操作");
            return new Result(old,true);
        }
        BigDecimal before=jdbc.sql("SELECT quantity FROM product_inventory_balance WHERE variant_id=:id").param("id",variantId).query(BigDecimal.class).optional().orElse(null);
        BigDecimal signed=type.equals("OUTBOUND")||type.equals("ADJUST_OUT")?quantity.negate():quantity;
        BigDecimal after=(before==null?BigDecimal.ZERO:before).add(signed);
        if(after.signum()<0) throw new ProductException(409,"INSUFFICIENT_STOCK","库存不足，请减少数量或先入库");
        if(after.compareTo(new BigDecimal("999999999999999"))>0) throw invalid("库存数量超出范围");
        if(before==null) jdbc.sql("INSERT INTO product_inventory_balance(variant_id,quantity) VALUES (:id,:q)").param("id",variantId).param("q",after).update();
        else jdbc.sql("UPDATE product_inventory_balance SET quantity=:q,updated_at=CURRENT_TIMESTAMP WHERE variant_id=:id").param("id",variantId).param("q",after).update();
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO product_inventory_transaction(id,request_id,variant_id,transaction_type,quantity,signed_quantity,balance_after,reason,actor_user_id,actor_login_name) VALUES (:id,:request,:variant,:type,:quantity,:signed,:after,:reason,:actor,:login)")
            .param("id",id).param("request",requestId).param("variant",variantId).param("type",type).param("quantity",quantity).param("signed",signed).param("after",after).param("reason",normalizedReason).param("actor",actor.userId()).param("login",actor.loginName()).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"PRODUCT_STOCK_"+type,"PRODUCT_VARIANT",variantId,true,Map.of("quantity",before==null?BigDecimal.ZERO:before),Map.of("quantity",after),normalizedReason));
        return new Result(jdbc.sql("SELECT * FROM product_inventory_transaction WHERE id=:id").param("id",id).query(this::entry).single(),false);
    }
    private ProductException invalid(String message) { return new ProductException(400,"PRODUCT_STOCK_INVALID",message); }
}
