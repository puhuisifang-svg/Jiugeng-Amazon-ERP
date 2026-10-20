package com.jiugeng.erp.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductCustomsService {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final JdbcClient jdbc;
    private final AuditTrail audit;
    ProductCustomsService(JdbcClient jdbc,AuditTrail audit) { this.jdbc=jdbc;this.audit=audit; }
    record Row(String productId,String productCode,String productName,String variantId,String skuCode,String skuName,
        String productStatus,String skuStatus,CustomsProfile customs) {}
    record Page(List<Row> items,long totalCount,int page,int size) {}
    private static final String SELECT="""
        SELECT p.id product_id,p.product_code,COALESCE(p.product_name,p.name_zh) product_name,p.status product_status,
            v.id,v.sku_code,v.variant_name,v.lifecycle_status,v.customs_data,v.customs_version
        FROM product_variant v JOIN product p ON p.id=v.product_id
        """;
    static CustomsProfile decode(String text,long version) {
        if(text==null) return null;
        try {
            var node=JSON.readTree(text); ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("version",version);
            return JSON.treeToValue(node,CustomsProfile.class);
        } catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored customs profile",e); }
    }
    private String encode(CustomsProfile value) {
        try { return JSON.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot encode customs profile",e); }
    }
    @Transactional(readOnly=true)
    Page list(String q,String state,int page,int size) {
        // State is kept in the profile, not duplicated in another master record.
        var values=jdbc.sql(SELECT+" WHERE p.deleted_at IS NULL AND v.deleted_at IS NULL ORDER BY v.sku_code_normalized").query((r,n)->new Row(r.getString("product_id"),r.getString("product_code"),
            r.getString("product_name"),r.getString("id"),r.getString("sku_code"),r.getString("variant_name"),r.getString("product_status"),
            r.getString("lifecycle_status"),decode(r.getString("customs_data"),r.getLong("customs_version")))).list();
        String term=q.trim().toLowerCase(Locale.ROOT);
        var filtered=values.stream().filter(v->state.isEmpty() || (v.customs()==null?"MISSING":v.customs().state().name()).equals(state))
            .filter(v->term.isEmpty() || (v.productCode()+" "+v.productName()+" "+v.skuCode()+" "+v.skuName()+" "+
                (v.customs()==null?"":encode(v.customs()))).toLowerCase(Locale.ROOT).contains(term)).toList();
        int from=Math.min(page*size,filtered.size());
        return new Page(filtered.subList(from,Math.min(from+size,filtered.size())),filtered.size(),page,size);
    }
    @Transactional
    CustomsProfile save(String variantId,CustomsProfile value,AuthenticatedActor actor) {
        if(value.netWeightKg()!=null && value.grossWeightKg()!=null && value.netWeightKg().compareTo(value.grossWeightKg())>0)
            throw new ProductException(400,"CUSTOMS_WEIGHT_INVALID","毛重不能小于净重");
        if(value.state()==CustomsProfile.State.REVIEWED && java.util.stream.Stream.of(value.nameZh(),value.nameEn(),value.composition(),value.usage(),value.originCountry(),value.unit(),value.chinaCode(),value.declarationElements()).anyMatch(v->v==null||v.isBlank()))
            throw new ProductException(400,"CUSTOMS_REVIEW_INCOMPLETE","标记资料已核对前，请填写中英文品名、材质、用途、原产国、申报单位、中国商品编码及申报要素");
        var old=jdbc.sql("SELECT customs_data,customs_version FROM product_variant WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",variantId)
            .query((r,n)->new Stored(r.getString(1),r.getLong(2))).optional().orElseThrow(()->new ProductException(404,"CUSTOMS_SKU_NOT_FOUND","SKU不存在"));
        if(value.version()==null || old.version()!=value.version()) throw new ProductException(409,"CUSTOMS_VERSION_CONFLICT","报关资料已被其他页面修改，请刷新后重新编辑");
        var before=decode(old.data(),old.version());
        if(Objects.equals(before,value)) return before;
        jdbc.sql("UPDATE product_variant SET customs_data=:data,customs_version=customs_version+1 WHERE id=:id")
            .param("data",encode(value)).param("id",variantId).update();
        var after=decode(encode(value),old.version()+1);
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"PRODUCT_CUSTOMS_UPDATE","PRODUCT_VARIANT",variantId,true,
            before==null?null:Map.of("version",before.version(),"state",before.state()),Map.of("version",after.version(),"state",after.state()),"报关资料维护（非海关申报或审核）"));
        return after;
    }
    private record Stored(String data,long version) {}
}
