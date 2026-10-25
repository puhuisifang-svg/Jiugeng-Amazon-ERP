package com.jiugeng.erp.dashboard;

import static com.jiugeng.erp.dashboard.DashboardTypes.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class DashboardRepository {
    private final JdbcClient jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final com.jiugeng.erp.product.ProductLookup products;
    DashboardRepository(JdbcClient jdbc, com.fasterxml.jackson.databind.ObjectMapper mapper, com.jiugeng.erp.product.ProductLookup products) { this.jdbc = jdbc; this.mapper=mapper; this.products=products; }
    String customerJson(com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct value) {
        if(value==null)return null;
        try{return mapper.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct customerSnapshot(String value) {
        if(value==null)return null;
        try{return mapper.readValue(value,com.jiugeng.erp.customer.CustomerProductLookup.ShippingProduct.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }

    void insertImage(ImageAsset image) {
        jdbc.sql("""
                INSERT INTO dashboard_image_asset (id,purpose,status,original_storage_key,thumbnail_storage_key,
                    source_file_name,source_media_type,stored_media_type,byte_size,sha256,pixel_width,pixel_height,
                    uploaded_by,created_at)
                VALUES (:id,:purpose,:status,:original,:thumbnail,:fileName,:sourceMedia,:storedMedia,:size,:sha,
                    :width,:height,:uploadedBy,:createdAt)
                """).param("id", image.id()).param("purpose", image.purpose().name()).param("status", image.status().name())
                .param("original", image.originalStorageKey()).param("thumbnail", image.thumbnailStorageKey())
                .param("fileName", image.sourceFileName()).param("sourceMedia", image.sourceMediaType())
                .param("storedMedia", image.storedMediaType()).param("size", image.byteSize()).param("sha", image.sha256())
                .param("width", image.width()).param("height", image.height()).param("uploadedBy", image.uploadedBy())
                .param("createdAt", image.createdAt()).update();
    }

    Optional<ImageAsset> image(String id) {
        return jdbc.sql("SELECT * FROM dashboard_image_asset WHERE id=:id").param("id", id)
                .query(this::mapImage).optional();
    }

    int attachImage(String id, ImagePurpose purpose, String actorId, String resourceType, String resourceId, Instant at) {
        return jdbc.sql("""
                UPDATE dashboard_image_asset SET status='ACTIVE',attached_resource_type=:resourceType,
                    attached_resource_id=:resourceId,attached_at=:at
                WHERE id=:id AND purpose=:purpose AND uploaded_by=:actor AND status='PENDING'
                """).param("resourceType", resourceType).param("resourceId", resourceId).param("at", at)
                .param("id", id).param("purpose", purpose.name()).param("actor", actorId).update();
    }

    int transferImage(String id, String sourceId, String targetId, Instant at) {
        return jdbc.sql("""
                UPDATE dashboard_image_asset SET attached_resource_type='TODAY_SHIPMENT',attached_resource_id=:target,
                    attached_at=:at WHERE id=:id AND status='ACTIVE' AND purpose='SHIPMENT'
                    AND attached_resource_type='PENDING_SHIPMENT' AND attached_resource_id=:source
                """).param("target", targetId).param("at", at).param("id", id).param("source", sourceId).update();
    }

    int transferTodayImage(String id, String sourceId, String targetId, Instant at) {
        return jdbc.sql("""
                UPDATE dashboard_image_asset SET attached_resource_type='PENDING_SHIPMENT',attached_resource_id=:target,
                    attached_at=:at WHERE id=:id AND status='ACTIVE' AND purpose='SHIPMENT'
                    AND attached_resource_type='TODAY_SHIPMENT' AND attached_resource_id=:source
                """).param("target",targetId).param("at",at).param("id",id).param("source",sourceId).update();
    }

    void releaseImage(String id, String resourceType, String resourceId, Instant at) {
        if (id == null) return;
        jdbc.sql("""
                UPDATE dashboard_image_asset SET status='PENDING',attached_resource_type=NULL,attached_resource_id=NULL,
                    attached_at=NULL,created_at=:at WHERE id=:id AND status='ACTIVE'
                    AND attached_resource_type=:type AND attached_resource_id=:resource
                """).param("at", at).param("id", id).param("type", resourceType).param("resource", resourceId).update();
    }

    boolean imageVisible(ImageAsset image, LocalDate businessDate, boolean shipmentManager, boolean productManager) {
        if (image.status() != ImageStatus.ACTIVE) return false;
        String type=image.attachedResourceType(); String id=image.attachedResourceId();
        if ("TODAY_SHIPMENT".equals(type)) return shipmentManager || jdbc.sql(
                "SELECT COUNT(*) FROM dashboard_today_shipment WHERE id=:id AND status='ACTIVE' AND ship_date=:date")
                .param("id",id).param("date",businessDate).query(Long.class).single()>0;
        if ("PENDING_SHIPMENT".equals(type)) return shipmentManager || jdbc.sql(
                "SELECT COUNT(*) FROM dashboard_pending_shipment WHERE id=:id AND status='PENDING'")
                .param("id",id).query(Long.class).single()>0;
        if ("PENDING_PRODUCT".equals(type)) return productManager || jdbc.sql(
                "SELECT COUNT(*) FROM dashboard_pending_product WHERE id=:id AND status='PENDING'")
                .param("id",id).query(Long.class).single()>0;
        return false;
    }

    List<ImageAsset> orphanImages(Instant cutoff) {
        return jdbc.sql("SELECT * FROM dashboard_image_asset WHERE status='PENDING' AND created_at < :cutoff")
                .param("cutoff", cutoff).query(this::mapImage).list();
    }

    void deletePendingImage(String id) {
        jdbc.sql("DELETE FROM dashboard_image_asset WHERE id=:id AND status='PENDING'").param("id", id).update();
    }

    List<TodayShipment> homeToday(LocalDate date) {
        return jdbc.sql(todaySelect() + " WHERE status='ACTIVE' AND ship_date=:date"
                + " ORDER BY latest_ship_at,product_code_snapshot,id LIMIT 10")
                .param("date", date).query(this::mapToday).list();
    }

    long countHomeToday(LocalDate date) {
        return jdbc.sql("SELECT COUNT(*) FROM dashboard_today_shipment WHERE status='ACTIVE' AND ship_date=:date")
                .param("date", date).query(Long.class).single();
    }

    List<PendingShipment> homePendingShipments() {
        return jdbc.sql(pendingShipmentSelect() + " WHERE status='PENDING'"
                + " ORDER BY planned_ship_date,latest_ship_at,product_code_snapshot,id LIMIT 10")
                .query(this::mapPendingShipment).list();
    }

    long countHomePendingShipments() {
        return jdbc.sql("SELECT COUNT(*) FROM dashboard_pending_shipment WHERE status='PENDING'")
                .query(Long.class).single();
    }

    List<PendingProduct> homePendingProducts() {
        return jdbc.sql(pendingProductSelect() + " WHERE status='PENDING'"
                + " ORDER BY expected_completion_date,created_at,id LIMIT 10")
                .query(this::mapPendingProduct).list();
    }

    long countHomePendingProducts() {
        return jdbc.sql("SELECT COUNT(*) FROM dashboard_pending_product WHERE status='PENDING'")
                .query(Long.class).single();
    }

    Page<TodayShipment> todayPage(String keyword, TodayStatus status, LocalDate from, LocalDate to, int page, int size) {
        Query query = filters(todaySelect(), "ship_date", keyword, status == null ? null : status.name(), from, to);
        long total = count(query, "dashboard_today_shipment");
        query.sql.append(" ORDER BY ship_date DESC,latest_ship_at,product_code_snapshot,id LIMIT :limit OFFSET :offset");
        query.params.put("limit", size); query.params.put("offset", page * size);
        return Page.of(jdbc.sql(query.sql.toString()).params(query.params).query(this::mapToday).list(), page, size, total);
    }

    Page<PendingShipment> pendingShipmentPage(String keyword, PendingShipmentStatus status, LocalDate from, LocalDate to,
                                                int page, int size) {
        Query query = filters(pendingShipmentSelect(), "planned_ship_date", keyword,
                status == null ? null : status.name(), from, to);
        if(status==null)query.sql.append(" AND status<>'ARCHIVED'");
        long total = count(query, "dashboard_pending_shipment");
        query.sql.append(" ORDER BY planned_ship_date,latest_ship_at,product_code_snapshot,id LIMIT :limit OFFSET :offset");
        query.params.put("limit", size); query.params.put("offset", page * size);
        return Page.of(jdbc.sql(query.sql.toString()).params(query.params).query(this::mapPendingShipment).list(), page, size, total);
    }

    Page<PendingProduct> pendingProductPage(String keyword, PendingProductStatus status, LocalDate from, LocalDate to,
                                             int page, int size) {
        StringBuilder sql = new StringBuilder(pendingProductSelect()).append(" WHERE 1=1");
        Map<String,Object> params = new HashMap<>();
        if (keyword != null) { sql.append(" AND UPPER(product_name) LIKE :keyword"); params.put("keyword", "%"+keyword.toUpperCase(java.util.Locale.ROOT)+"%"); }
        if (status != null) { sql.append(" AND status=:status"); params.put("status", status.name()); }
        if (from != null) { sql.append(" AND expected_completion_date>=:fromDate"); params.put("fromDate", from); }
        if (to != null) { sql.append(" AND expected_completion_date<=:toDate"); params.put("toDate", to); }
        Query query = new Query(sql, params);
        long total = count(query, "dashboard_pending_product");
        sql.append(" ORDER BY expected_completion_date,created_at,id LIMIT :limit OFFSET :offset");
        params.put("limit", size); params.put("offset", page * size);
        return Page.of(jdbc.sql(sql.toString()).params(params).query(this::mapPendingProduct).list(), page, size, total);
    }

    Optional<TodayShipment> today(String id) {
        return jdbc.sql(todaySelect()+" WHERE id=:id").param("id",id).query(this::mapToday).optional();
    }
    Optional<TodayShipment> todayByPending(String id) {
        return jdbc.sql(todaySelect()+" WHERE source_pending_shipment_id=:id").param("id",id).query(this::mapToday).optional();
    }
    int reopenToday(String id,long version) {
        return jdbc.sql("UPDATE dashboard_today_shipment SET status='ACTIVE',archived_by=NULL,archived_at=NULL,archive_reason=NULL WHERE id=:id AND version=:version AND status='ARCHIVED'")
                .param("id",id).param("version",version).update();
    }
    int reopenPending(String id,long version) {
        return jdbc.sql("UPDATE dashboard_pending_shipment SET status='PENDING',archived_by=NULL,archived_at=NULL,archive_reason=NULL WHERE id=:id AND version=:version AND status IN ('TRANSFERRED_TO_TODAY','ARCHIVED')")
                .param("id",id).param("version",version).update();
    }
    void linkTodayToPending(String id,String pendingId) {
        jdbc.sql("UPDATE dashboard_today_shipment SET source_pending_shipment_id=:pending WHERE id=:id AND source_pending_shipment_id IS NULL")
                .param("pending",pendingId).param("id",id).update();
    }
    Optional<PendingShipment> pendingShipment(String id) {
        return jdbc.sql(pendingShipmentSelect()+" WHERE id=:id").param("id",id).query(this::mapPendingShipment).optional();
    }
    Optional<PendingShipment> pendingShipmentForUpdate(String id) {
        return jdbc.sql(pendingShipmentSelect()+" WHERE id=:id FOR UPDATE").param("id",id).query(this::mapPendingShipment).optional();
    }
    Optional<PendingProduct> pendingProduct(String id) {
        return jdbc.sql(pendingProductSelect()+" WHERE id=:id").param("id",id).query(this::mapPendingProduct).optional();
    }

    void insertToday(String id, ShipmentInput input, String productCode, String productName, String sourcePendingId,
                     String actorId) {
        jdbc.sql("""
                INSERT INTO dashboard_today_shipment (id,ship_date,product_id,product_code_snapshot,
                    product_name_snapshot,image_asset_id,quantity,quantity_unit,box_count,remark,latest_ship_at,
                    status,source_pending_shipment_id,created_by,updated_by,variant_id,customer_product_id,customer_snapshot)
                VALUES (:id,:date,:productId,:code,:name,:image,:quantity,'SET',:boxes,:remark,:latest,'ACTIVE',
                    :source,:actor,:actor,:variant,:customerProduct,:customerSnapshot)
                """).param("id",id).param("date",input.date()).param("productId",input.productId())
                .param("code",productCode).param("name",productName).param("image",input.imageId()).param("variant",input.variantId()).param("customerProduct",input.customerProductId()).param("customerSnapshot",customerJson(input.customerProduct()))
                .param("quantity",input.quantity()).param("boxes",input.boxCount()).param("remark",input.remark())
                .param("latest",input.latestShipAt()).param("source",sourcePendingId).param("actor",actorId).update();
    }

    int updateToday(String id, ShipmentInput input, String code, String name, long version, String actorId) {
        return jdbc.sql("""
                UPDATE dashboard_today_shipment SET ship_date=:date,product_id=:productId,
                    product_code_snapshot=:code,product_name_snapshot=:name,image_asset_id=:image,quantity=:quantity,variant_id=:variant,customer_product_id=:customerProduct,customer_snapshot=:customerSnapshot,
                    box_count=:boxes,remark=:remark,latest_ship_at=:latest,updated_by=:actor,
                    updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=:id AND version=:version AND status='ACTIVE'
                """).param("date",input.date()).param("productId",input.productId()).param("code",code).param("name",name)
                .param("image",input.imageId()).param("quantity",input.quantity()).param("boxes",input.boxCount()).param("variant",input.variantId()).param("customerProduct",input.customerProductId()).param("customerSnapshot",customerJson(input.customerProduct()))
                .param("remark",input.remark()).param("latest",input.latestShipAt()).param("actor",actorId)
                .param("id",id).param("version",version).update();
    }

    int archiveToday(String id, long version, String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE dashboard_today_shipment SET status='ARCHIVED',archived_by=:actor,archived_at=:now,
                    archive_reason=:reason,updated_by=:actor,updated_at=:now,version=version+1
                WHERE id=:id AND version=:version AND status='ACTIVE'
                """).param("actor",actorId).param("now",now).param("reason",reason).param("id",id)
                .param("version",version).update();
    }

    void insertPendingShipment(String id, ShipmentInput input, String productCode, String productName, String actorId) {
        jdbc.sql("""
                INSERT INTO dashboard_pending_shipment (id,planned_ship_date,product_id,product_code_snapshot,
                    product_name_snapshot,image_asset_id,quantity,quantity_unit,box_count,remark,latest_ship_at,
                    status,created_by,updated_by,variant_id,customer_product_id,customer_snapshot)
                VALUES (:id,:date,:productId,:code,:name,:image,:quantity,'SET',:boxes,:remark,:latest,'PENDING',
                    :actor,:actor,:variant,:customerProduct,:customerSnapshot)
                """).param("id",id).param("date",input.date()).param("productId",input.productId())
                .param("code",productCode).param("name",productName).param("image",input.imageId()).param("variant",input.variantId()).param("customerProduct",input.customerProductId()).param("customerSnapshot",customerJson(input.customerProduct()))
                .param("quantity",input.quantity()).param("boxes",input.boxCount()).param("remark",input.remark())
                .param("latest",input.latestShipAt()).param("actor",actorId).update();
    }

    int updatePendingShipment(String id, ShipmentInput input, String code, String name, long version, String actorId) {
        return jdbc.sql("""
                UPDATE dashboard_pending_shipment SET planned_ship_date=:date,product_id=:productId,
                    product_code_snapshot=:code,product_name_snapshot=:name,image_asset_id=:image,quantity=:quantity,variant_id=:variant,customer_product_id=:customerProduct,customer_snapshot=:customerSnapshot,
                    box_count=:boxes,remark=:remark,latest_ship_at=:latest,updated_by=:actor,
                    updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=:id AND version=:version AND status='PENDING'
                """).param("date",input.date()).param("productId",input.productId()).param("code",code).param("name",name)
                .param("image",input.imageId()).param("quantity",input.quantity()).param("boxes",input.boxCount()).param("variant",input.variantId()).param("customerProduct",input.customerProductId()).param("customerSnapshot",customerJson(input.customerProduct()))
                .param("remark",input.remark()).param("latest",input.latestShipAt()).param("actor",actorId)
                .param("id",id).param("version",version).update();
    }

    int archivePendingShipment(String id, long version, String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE dashboard_pending_shipment SET status='ARCHIVED',archived_by=:actor,archived_at=:now,
                    archive_reason=:reason,updated_by=:actor,updated_at=:now,version=version+1
                WHERE id=:id AND version=:version AND status IN ('PENDING','TRANSFERRED_TO_TODAY')
                """).param("actor",actorId).param("now",now).param("reason",reason).param("id",id)
                .param("version",version).update();
    }

    int markTransferred(String id, long version, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE dashboard_pending_shipment SET status='TRANSFERRED_TO_TODAY',updated_by=:actor,
                    updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status='PENDING'
                """).param("actor",actorId).param("now",now).param("id",id).param("version",version).update();
    }

    void insertPendingProduct(String id, PendingProductInput input, String actorId) {
        jdbc.sql("""
                INSERT INTO dashboard_pending_product (id,product_name,image_asset_id,expected_quantity,quantity_unit,
                    remark,expected_completion_date,status,created_by,updated_by)
                VALUES (:id,:name,:image,:quantity,'SET',:remark,:date,'PENDING',:actor,:actor)
                """).param("id",id).param("name",input.productName()).param("image",input.imageId())
                .param("quantity",input.expectedQuantity()).param("remark",input.remark())
                .param("date",input.expectedCompletionDate()).param("actor",actorId).update();
    }

    int updatePendingProduct(String id, PendingProductInput input, long version, String actorId) {
        return jdbc.sql("""
                UPDATE dashboard_pending_product SET product_name=:name,image_asset_id=:image,
                    expected_quantity=:quantity,remark=:remark,expected_completion_date=:date,updated_by=:actor,
                    updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=:id AND version=:version AND status='PENDING'
                """).param("name",input.productName()).param("image",input.imageId())
                .param("quantity",input.expectedQuantity()).param("remark",input.remark())
                .param("date",input.expectedCompletionDate()).param("actor",actorId).param("id",id)
                .param("version",version).update();
    }

    int completePendingProduct(String id, long version, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE dashboard_pending_product SET status='COMPLETED',completed_by=:actor,completed_at=:now,
                    updated_by=:actor,updated_at=:now,version=version+1
                WHERE id=:id AND version=:version AND status='PENDING'
                """).param("actor",actorId).param("now",now).param("id",id).param("version",version).update();
    }

    int archivePendingProduct(String id, long version, String reason, String actorId, Instant now) {
        return jdbc.sql("""
                UPDATE dashboard_pending_product SET status='ARCHIVED',archived_by=:actor,archived_at=:now,
                    archive_reason=:reason,updated_by=:actor,updated_at=:now,version=version+1
                WHERE id=:id AND version=:version AND status='PENDING'
                """).param("actor",actorId).param("now",now).param("reason",reason).param("id",id)
                .param("version",version).update();
    }

    Optional<Command> command(String actor, String operation, String key) {
        return jdbc.sql("SELECT * FROM dashboard_command WHERE actor_user_id=:actor AND operation=:operation AND idempotency_key=:key")
                .param("actor",actor).param("operation",operation).param("key",key)
                .query((rs,n)->new Command(rs.getString("actor_user_id"),rs.getString("operation"),
                        rs.getString("idempotency_key"),rs.getString("payload_hash"),rs.getString("resource_type"),
                        rs.getString("resource_id"))).optional();
    }

    void insertCommand(Command command) {
        jdbc.sql("""
                INSERT INTO dashboard_command (id,actor_user_id,operation,idempotency_key,payload_hash,resource_type,resource_id)
                VALUES (:id,:actor,:operation,:key,:hash,:type,:resource)
                """).param("id",java.util.UUID.randomUUID().toString()).param("actor",command.actorId())
                .param("operation",command.operation()).param("key",command.key()).param("hash",command.payloadHash())
                .param("type",command.resourceType()).param("resource",command.resourceId()).update();
    }

    private Query filters(String select, String dateColumn, String keyword, String status, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(select).append(" WHERE 1=1");
        Map<String,Object> params = new HashMap<>();
        if (keyword != null) { sql.append(" AND (UPPER(product_code_snapshot) LIKE :keyword OR UPPER(product_name_snapshot) LIKE :keyword OR UPPER(COALESCE(customer_snapshot,'')) LIKE :keyword OR UPPER(COALESCE(remark,'')) LIKE :keyword)"); params.put("keyword","%"+keyword.toUpperCase(java.util.Locale.ROOT)+"%"); }
        if (status != null) { sql.append(" AND status=:status"); params.put("status",status); }
        if (from != null) { sql.append(" AND ").append(dateColumn).append(">=:fromDate"); params.put("fromDate",from); }
        if (to != null) { sql.append(" AND ").append(dateColumn).append("<=:toDate"); params.put("toDate",to); }
        return new Query(sql,params);
    }

    private long count(Query query, String table) {
        int where = query.sql.indexOf(" WHERE");
        return jdbc.sql("SELECT COUNT(*) FROM "+table+query.sql.substring(where)).params(query.params)
                .query(Long.class).single();
    }

    private ImageAsset mapImage(ResultSet rs,int n)throws SQLException { return new ImageAsset(rs.getString("id"),
            ImagePurpose.valueOf(rs.getString("purpose")),ImageStatus.valueOf(rs.getString("status")),
            rs.getString("original_storage_key"),rs.getString("thumbnail_storage_key"),rs.getString("source_file_name"),
            rs.getString("source_media_type"),rs.getString("stored_media_type"),rs.getLong("byte_size"),
            rs.getString("sha256"),rs.getInt("pixel_width"),rs.getInt("pixel_height"),rs.getString("uploaded_by"),
            rs.getString("attached_resource_type"),rs.getString("attached_resource_id"),instant(rs,"created_at"),
            nullableInstant(rs,"attached_at")); }
    private TodayShipment mapToday(ResultSet rs,int n)throws SQLException { return new TodayShipment(rs.getString("id"),
            rs.getDate("ship_date").toLocalDate(),rs.getString("product_id"),rs.getString("product_code_snapshot"),
            rs.getString("product_name_snapshot"),rs.getString("image_asset_id"),rs.getInt("quantity"),
            rs.getString("quantity_unit"),rs.getInt("box_count"),rs.getString("remark"),instant(rs,"latest_ship_at"),
            TodayStatus.valueOf(rs.getString("status")),rs.getString("source_pending_shipment_id"),rs.getLong("version"),
            rs.getString("created_by"),rs.getString("updated_by"),rs.getString("archived_by"),nullableInstant(rs,"archived_at"),
            rs.getString("archive_reason"),instant(rs,"created_at"),instant(rs,"updated_at"),rs.getString("variant_id"),customerSnapshot(rs.getString("customer_snapshot")),products.namePresentation(rs.getString("product_id"),rs.getString("variant_id"),rs.getString("product_name_snapshot"))); }
    private PendingShipment mapPendingShipment(ResultSet rs,int n)throws SQLException { return new PendingShipment(rs.getString("id"),
            rs.getDate("planned_ship_date").toLocalDate(),rs.getString("product_id"),rs.getString("product_code_snapshot"),
            rs.getString("product_name_snapshot"),rs.getString("image_asset_id"),rs.getInt("quantity"),
            rs.getString("quantity_unit"),rs.getInt("box_count"),rs.getString("remark"),instant(rs,"latest_ship_at"),
            PendingShipmentStatus.valueOf(rs.getString("status")),rs.getLong("version"),rs.getString("created_by"),
            rs.getString("updated_by"),rs.getString("archived_by"),nullableInstant(rs,"archived_at"),rs.getString("archive_reason"),
            instant(rs,"created_at"),instant(rs,"updated_at"),rs.getString("variant_id"),customerSnapshot(rs.getString("customer_snapshot")),products.namePresentation(rs.getString("product_id"),rs.getString("variant_id"),rs.getString("product_name_snapshot"))); }
    private PendingProduct mapPendingProduct(ResultSet rs,int n)throws SQLException { return new PendingProduct(rs.getString("id"),
            rs.getString("product_name"),rs.getString("image_asset_id"),rs.getInt("expected_quantity"),
            rs.getString("quantity_unit"),rs.getString("remark"),rs.getDate("expected_completion_date").toLocalDate(),
            PendingProductStatus.valueOf(rs.getString("status")),rs.getLong("version"),rs.getString("created_by"),
            rs.getString("updated_by"),rs.getString("completed_by"),nullableInstant(rs,"completed_at"),
            rs.getString("archived_by"),nullableInstant(rs,"archived_at"),rs.getString("archive_reason"),
            instant(rs,"created_at"),instant(rs,"updated_at")); }
    private Instant instant(ResultSet rs,String column)throws SQLException{return rs.getTimestamp(column).toInstant();}
    private Instant nullableInstant(ResultSet rs,String column)throws SQLException{var value=rs.getTimestamp(column);return value==null?null:value.toInstant();}
    private String todaySelect(){return "SELECT * FROM dashboard_today_shipment";}
    private String pendingShipmentSelect(){return "SELECT * FROM dashboard_pending_shipment";}
    private String pendingProductSelect(){return "SELECT * FROM dashboard_pending_product";}
    private record Query(StringBuilder sql,Map<String,Object> params){}
}
