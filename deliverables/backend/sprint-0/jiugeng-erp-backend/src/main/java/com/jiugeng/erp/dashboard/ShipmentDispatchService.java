package com.jiugeng.erp.dashboard;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.product.ProductLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ShipmentDispatchService {
    record Dispatch(String id,String todayShipmentId,String productId,String productCode,String productName,String skuCodes,
                    long quantity,long boxCount,String remark,LocalDate plannedDate,LocalDate businessDate,Instant dispatchedAt,String actorName,String variantId,com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct customerProduct,ProductLookup.NamePresentation namePresentation) {}
    record Summary(long records,long quantity,long boxCount,long products) {}
    record Daily(LocalDate date,long records,long quantity,long boxCount) {}
    record ProductTotal(String productId,String productCode,String productName,long records,long quantity,long boxCount,String variantId,com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct customerProduct,ProductLookup.NamePresentation namePresentation) {}
    record Statistics(Summary summary,List<Daily> daily,List<ProductTotal> products,DashboardTypes.Page<Dispatch> details) {}
    private final JdbcClient jdbc;
    private final DashboardRepository repository;
    private final ProductLookup products;
    private final AuditTrail audit;
    private final Clock clock;
    private final com.jiugeng.erp.customer.CustomerProductLookup customerProducts;
    ShipmentDispatchService(JdbcClient jdbc,DashboardRepository repository,ProductLookup products,AuditTrail audit,Clock clock,com.jiugeng.erp.customer.CustomerProductLookup customerProducts) {
        this.jdbc=jdbc;this.repository=repository;this.products=products;this.audit=audit;this.clock=clock;this.customerProducts=customerProducts;
    }
    private DashboardException conflict(String message) { return new DashboardException(409,"SHIPMENT_DISPATCH_CONFLICT",message); }
    @Transactional
    Dispatch dispatch(String id,long version,String key,AuthenticatedActor actor) {
        if(key==null || key.isBlank() || key.length()>128) throw new DashboardException(400,"DISPATCH_KEY_INVALID","请求标识无效");
        // Lock the source before checking the ledger; concurrent confirmations cannot double-count.
        jdbc.sql("SELECT id FROM dashboard_today_shipment WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional()
            .orElseThrow(()->new DashboardException(404,"SHIPMENT_NOT_FOUND","发货记录不存在"));
        var prior=jdbc.sql("SELECT * FROM dashboard_shipment_dispatch WHERE actor_id=:actor AND request_key=:key")
            .param("actor",actor.userId()).param("key",key).query((rs,n)-> {
                if(!id.equals(rs.getString("today_shipment_id")) || version!=rs.getLong("source_version")) throw conflict("请求标识已用于其他操作");
                return map(rs,n);
            }).optional();
        if(prior.isPresent()) return prior.get();
        var source=repository.today(id).orElseThrow();
        var now=clock.instant(); var date=LocalDate.ofInstant(now,DashboardService.BUSINESS_ZONE);
        if(source.status()!=DashboardTypes.TodayStatus.ACTIVE || source.version()!=version) throw conflict("记录状态已变化，请刷新后重试");
        if(source.shipDate().isAfter(date)) throw conflict("不能提前确认未来日期的发货安排");
        String dispatchId=UUID.randomUUID().toString();
        if(source.variantId()==null)throw conflict("该历史记录尚未绑定 SKU，请先编辑补选后再发货");
        var sku=products.findExistingSku(source.productId(),source.variantId()).orElseThrow(()->conflict("SKU 已不存在，请先编辑发货记录"));
        if(!sku.active())throw conflict("SKU 或所属产品已停用，不能发货");
        if(source.customerProduct()==null)throw conflict("请先编辑补选客户商品");
        var binding=customerProducts.forExisting(source.customerProduct().id()).orElseThrow(()->conflict("客户或客户商品已停用，不能发货"));
        if(!binding.productId().equals(source.productId())||!binding.variantId().equals(source.variantId()))throw conflict("客户商品与 SKU 不匹配");
        String skus=source.productCode();
        jdbc.sql("""
            INSERT INTO dashboard_shipment_dispatch(id,today_shipment_id,product_id,product_code,product_name,sku_codes,
              quantity,box_count,remark,planned_date,business_date,dispatched_at,actor_id,actor_name,request_key,source_version,variant_id,customer_product_id,customer_snapshot)
            VALUES(:id,:today,:product,:code,:name,:skus,:quantity,:boxes,:remark,:planned,:date,:at,:actor,:actorName,:key,:version,:variant,:customerProduct,:customerSnapshot)
            """).param("id",dispatchId).param("today",id).param("product",source.productId()).param("code",source.productCode())
            .param("name",source.productName()).param("skus",skus).param("quantity",source.quantity()).param("boxes",source.boxCount())
            .param("remark",source.remark()).param("planned",source.shipDate()).param("date",date).param("at",now)
            .param("actor",actor.userId()).param("actorName",actor.displayName()).param("key",key).param("version",version).param("variant",source.variantId()).param("customerProduct",source.customerProduct().id()).param("customerSnapshot",repository.customerJson(source.customerProduct())).update();
        if(jdbc.sql("UPDATE dashboard_today_shipment SET status='SHIPPED',version=version+1,updated_at=:at,updated_by=:actor WHERE id=:id AND status='ACTIVE' AND version=:version")
            .param("at",now).param("actor",actor.userId()).param("id",id).param("version",version).update()!=1) throw conflict("记录已变化");
        var result=jdbc.sql("SELECT * FROM dashboard_shipment_dispatch WHERE id=:id").param("id",dispatchId).query(this::map).single();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"SHIPMENT_DISPATCH","TODAY_SHIPMENT",id,true,
            Map.of("status","ACTIVE","version",version),Map.of("status","SHIPPED","dispatchId",dispatchId,"quantity",source.quantity(),"boxCount",source.boxCount()),"确认发货，已计入发货统计（不联动库存或物流）"));
        return result;
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Statistics statistics(LocalDate from,LocalDate to,String keyword,int page,int size) {
        if(from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>365)
            throw new DashboardException(400,"DISPATCH_DATE_RANGE_INVALID","开始日期不能晚于结束日期，且查询范围不能超过 366 天");
        String where=" WHERE business_date>=:from AND business_date<=:to";
        Map<String,Object> params=new HashMap<>();params.put("from",from);params.put("to",to);
        if(keyword!=null && !keyword.isBlank()) {
            where+=" AND (LOWER(product_code) LIKE :keyword ESCAPE '!' OR LOWER(product_name) LIKE :keyword ESCAPE '!' OR LOWER(sku_codes) LIKE :keyword ESCAPE '!' OR LOWER(COALESCE(customer_snapshot,'')) LIKE :keyword ESCAPE '!' OR LOWER(COALESCE(remark,'')) LIKE :keyword ESCAPE '!')";
            params.put("keyword","%"+keyword.trim().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        String table=" FROM dashboard_shipment_dispatch"+where;
        var summary=jdbc.sql("SELECT COUNT(*) records,COALESCE(SUM(quantity),0) quantity,COALESCE(SUM(box_count),0) boxes,COUNT(DISTINCT COALESCE(customer_product_id,variant_id,product_id)) products"+table)
            .params(params).query((rs,n)->new Summary(rs.getLong("records"),rs.getLong("quantity"),rs.getLong("boxes"),rs.getLong("products"))).single();
        var daily=jdbc.sql("SELECT business_date,COUNT(*) records,SUM(quantity) quantity,SUM(box_count) boxes"+table+" GROUP BY business_date ORDER BY business_date DESC")
            .params(params).query((rs,n)->new Daily(rs.getDate("business_date").toLocalDate(),rs.getLong("records"),rs.getLong("quantity"),rs.getLong("boxes"))).list();
        var grouped=jdbc.sql("SELECT product_id,variant_id,customer_product_id,MAX(customer_snapshot) customer_snapshot,MAX(product_code) code,MAX(product_name) name,COUNT(*) records,SUM(quantity) quantity,SUM(box_count) boxes"+table+" GROUP BY product_id,variant_id,customer_product_id ORDER BY quantity DESC,product_id,variant_id,customer_product_id LIMIT 10")
            .params(params).query((rs,n)->new ProductTotal(rs.getString("product_id"),rs.getString("code"),rs.getString("name"),rs.getLong("records"),rs.getLong("quantity"),rs.getLong("boxes"),rs.getString("variant_id"),repository.customerSnapshot(rs.getString("customer_snapshot")),products.namePresentation(rs.getString("product_id"),rs.getString("variant_id"),rs.getString("name")))).list();
        params.put("limit",size);params.put("offset",(long)page*size);
        var rows=jdbc.sql("SELECT *"+table+" ORDER BY dispatched_at DESC,id LIMIT :limit OFFSET :offset").params(params).query(this::map).list();
        return new Statistics(summary,daily,grouped,DashboardTypes.Page.of(rows,page,size,summary.records()));
    }
    private Dispatch map(ResultSet rs,int index) throws SQLException {
        return new Dispatch(rs.getString("id"),rs.getString("today_shipment_id"),rs.getString("product_id"),rs.getString("product_code"),rs.getString("product_name"),rs.getString("sku_codes"),
            rs.getLong("quantity"),rs.getLong("box_count"),rs.getString("remark"),rs.getDate("planned_date").toLocalDate(),rs.getDate("business_date").toLocalDate(),rs.getTimestamp("dispatched_at").toInstant(),rs.getString("actor_name"),rs.getString("variant_id"),repository.customerSnapshot(rs.getString("customer_snapshot")),products.namePresentation(rs.getString("product_id"),rs.getString("variant_id"),rs.getString("product_name")));
    }
}
