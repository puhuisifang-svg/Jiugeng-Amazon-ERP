package com.jiugeng.erp.purchase;

import static com.jiugeng.erp.purchase.PurchaseTypes.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PurchaseRepository {
    private final JdbcClient jdbc;
    PurchaseRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    SupplierRef activeSupplier(String id) {
        return jdbc.sql("SELECT id,name FROM supplier WHERE id=:id AND status='ACTIVE'").param("id", id)
                .query((rs, row) -> new SupplierRef(rs.getString("id"), rs.getString("name"))).optional()
                .orElseThrow(() -> new PurchaseException(409, "ACTIVE_SUPPLIER_REQUIRED", "采购单必须选择启用供应商"));
    }

    ItemRef activeItem(OrderType type, String supplierId, String id) {
        String sql = type == OrderType.PRODUCT ? """
                SELECT v.id, v.sku_code AS code, v.variant_name AS name, '套' AS unit
                FROM product_variant v JOIN product p ON p.id=v.product_id
                JOIN product_variant_supplier link ON link.variant_id=v.id AND link.supplier_id=:supplierId
                WHERE v.id=:id AND v.status='ACTIVE' AND p.status='ACTIVE'
                """ : """
                SELECT m.id, m.material_code AS code, m.name, m.unit FROM auxiliary_material m
                JOIN auxiliary_material_supplier link ON link.auxiliary_material_id=m.id AND link.supplier_id=:supplierId
                WHERE m.id=:id AND m.status='ACTIVE'
                """;
        return jdbc.sql(sql).param("id", id).param("supplierId", supplierId).query((rs, row) -> new ItemRef(rs.getString("id"),
                rs.getString("code"), rs.getString("name"), rs.getString("unit"))).optional()
                .orElseThrow(() -> new PurchaseException(409, "SUPPLIER_ITEM_LINK_REQUIRED", "该供应商未关联所选商品或辅料"));
    }

    List<ReferenceItem> referenceItems(OrderType type, String supplierId) {
        activeSupplier(supplierId);
        String sql = type == OrderType.PRODUCT ? """
                SELECT v.id,v.sku_code AS code,v.variant_name AS name,'套' AS unit,
                       CASE WHEN link.primary_slot='PRIMARY' THEN TRUE ELSE FALSE END AS primary_supplier
                FROM product_variant_supplier link
                JOIN product_variant v ON v.id=link.variant_id
                JOIN product p ON p.id=v.product_id
                WHERE link.supplier_id=:supplierId AND v.status='ACTIVE' AND p.status='ACTIVE'
                ORDER BY primary_supplier DESC,v.sku_code
                """ : """
                SELECT m.id,m.material_code AS code,m.name,m.unit,
                       CASE WHEN link.primary_slot='PRIMARY' THEN TRUE ELSE FALSE END AS primary_supplier
                FROM auxiliary_material_supplier link
                JOIN auxiliary_material m ON m.id=link.auxiliary_material_id
                WHERE link.supplier_id=:supplierId AND m.status='ACTIVE'
                ORDER BY primary_supplier DESC,m.material_code
                """;
        return jdbc.sql(sql).param("supplierId", supplierId).query((rs, row) -> new ReferenceItem(
                rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("unit"),
                rs.getBoolean("primary_supplier"))).list();
    }

    boolean orderNoExists(String orderNo) {
        return jdbc.sql("SELECT COUNT(*) FROM purchase_order WHERE order_no=:value").param("value", orderNo)
                .query(Integer.class).single() > 0;
    }

    void insertOrder(String id, String orderNo, OrderInput input, String actorId) {
        jdbc.sql("""
                INSERT INTO purchase_order (id,order_no,order_type,supplier_id,status,currency,remark,created_by,updated_by,expected_delivery_date,delivery_address)
                VALUES (:id,:orderNo,:type,:supplier,'DRAFT',:currency,:remark,:actor,:actor,:deliveryDate,:address)
                """).param("id", id).param("orderNo", orderNo).param("type", input.orderType().name())
                .param("supplier", input.supplierId()).param("currency", input.currency()).param("remark", input.remark())
                .param("actor", actorId).param("deliveryDate", input.expectedDeliveryDate())
                .param("address", input.deliveryAddress()).update();
    }

    void replaceLines(String orderId, List<ResolvedLine> lines) {
        jdbc.sql("DELETE FROM purchase_order_line WHERE order_id=:id").param("id", orderId).update();
        for (ResolvedLine line : lines) jdbc.sql("""
                INSERT INTO purchase_order_line (id,order_id,line_no,item_id,item_code,item_name,unit,ordered_quantity,unit_price)
                VALUES (:id,:orderId,:lineNo,:itemId,:code,:name,:unit,:quantity,:price)
                """).param("id", line.id()).param("orderId", orderId).param("lineNo", line.lineNo())
                .param("itemId", line.item().id()).param("code", line.item().code()).param("name", line.item().name())
                .param("unit", line.item().unit()).param("quantity", line.quantity()).param("price", line.unitPrice()).update();
    }

    int updateDraft(String id, OrderInput input, String actorId, long version) {
        return jdbc.sql("""
                UPDATE purchase_order SET supplier_id=:supplier,currency=:currency,remark=:remark,version=version+1,
                  updated_by=:actor,updated_at=CURRENT_TIMESTAMP,expected_delivery_date=:deliveryDate,delivery_address=:address
                WHERE id=:id AND status='DRAFT' AND version=:version
                """).param("supplier", input.supplierId()).param("currency", input.currency()).param("remark", input.remark())
                .param("actor", actorId).param("id", id).param("version", version)
                .param("deliveryDate", input.expectedDeliveryDate()).param("address", input.deliveryAddress()).update();
    }

    void cancellationReason(String id, String reason) {
        jdbc.sql("UPDATE purchase_order SET cancellation_reason=:reason WHERE id=:id")
                .param("reason", reason).param("id", id).update();
    }

    int transition(String id, Status from, Status to, String actorId, long version) {
        return jdbc.sql("""
                UPDATE purchase_order SET status=:to,version=version+1,updated_by=:actor,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status=:from AND version=:version
                """).param("to", to.name()).param("actor", actorId).param("id", id).param("from", from.name())
                .param("version", version).update();
    }

    int setReceiptStatus(String id, Status status, String actorId) {
        return jdbc.sql("""
                UPDATE purchase_order SET status=:status,version=version+1,updated_by=:actor,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status IN ('SUBMITTED','PARTIALLY_RECEIVED')
                """).param("status", status.name()).param("actor", actorId).param("id", id).update();
    }

    Optional<OrderView> find(String id, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbc.sql("""
                SELECT o.*,s.name AS supplier_name FROM purchase_order o JOIN supplier s ON s.id=o.supplier_id
                WHERE o.id=:id
                """ + suffix).param("id", id).query(this::mapOrder).optional().map(this::withLines);
    }

    List<OrderView> list(Status status) {
        String where = status == null ? "" : " WHERE o.status=:status";
        var query = jdbc.sql("SELECT o.*,s.name AS supplier_name FROM purchase_order o JOIN supplier s ON s.id=o.supplier_id" +
                where + " ORDER BY o.updated_at DESC,o.id DESC");
        if (status != null) query = query.param("status", status.name());
        return query.query(this::mapOrder).list().stream().map(this::withLines).toList();
    }

    List<LineView> lines(String orderId) {
        return jdbc.sql("""
                SELECT * FROM purchase_order_line WHERE order_id=:id ORDER BY line_no
                """).param("id", orderId).query((rs, row) -> {
                    BigDecimal ordered = rs.getBigDecimal("ordered_quantity");
                    BigDecimal received = rs.getBigDecimal("received_quantity");
                    return new LineView(rs.getString("id"), rs.getInt("line_no"), rs.getString("item_id"),
                            rs.getString("item_code"), rs.getString("item_name"), rs.getString("unit"), ordered,
                            received, ordered.subtract(received), rs.getBigDecimal("unit_price"));
                }).list();
    }

    void lockLine(String id) {
        jdbc.sql("SELECT id FROM purchase_order_line WHERE id=:id FOR UPDATE").param("id", id).query(String.class)
                .optional().orElseThrow(() -> new PurchaseException(404, "PURCHASE_LINE_NOT_FOUND", "采购明细不存在"));
    }

    void addReceived(String lineId, BigDecimal quantity) {
        int changed = jdbc.sql("""
                UPDATE purchase_order_line SET received_quantity=received_quantity+:quantity
                WHERE id=:id AND received_quantity+:quantity<=ordered_quantity
                """).param("quantity", quantity).param("id", lineId).update();
        if (changed != 1) throw new PurchaseException(409, "PURCHASE_OVER_RECEIPT", "收货数量超过剩余数量");
    }

    Optional<ReceiptStored> receiptByRequest(String requestId) {
        return jdbc.sql("SELECT id,order_id,payload_hash FROM purchase_receipt WHERE request_id=:id")
                .param("id", requestId).query((rs, row) -> new ReceiptStored(rs.getString("id"),
                        rs.getString("order_id"), rs.getString("payload_hash"))).optional();
    }

    void insertReceipt(String id, String orderId, String requestId, String hash, Instant receivedAt, String remark,
            String actorId, String actorLogin) {
        jdbc.sql("""
                INSERT INTO purchase_receipt (id,order_id,request_id,payload_hash,received_at,remark,actor_user_id,actor_login_name)
                VALUES (:id,:orderId,:requestId,:hash,:receivedAt,:remark,:actorId,:actorLogin)
                """).param("id", id).param("orderId", orderId).param("requestId", requestId).param("hash", hash)
                .param("receivedAt", receivedAt).param("remark", remark).param("actorId", actorId)
                .param("actorLogin", actorLogin).update();
    }

    void insertReceiptLine(String id, String receiptId, String lineId, BigDecimal quantity, String inventoryId) {
        jdbc.sql("""
                INSERT INTO purchase_receipt_line (id,receipt_id,order_line_id,quantity,inventory_transaction_id)
                VALUES (:id,:receiptId,:lineId,:quantity,:inventoryId)
                """).param("id", id).param("receiptId", receiptId).param("lineId", lineId)
                .param("quantity", quantity).param("inventoryId", inventoryId).update();
    }

    ReceiptView receipt(String id, boolean replayed) {
        ReceiptHead head = jdbc.sql("""
                SELECT r.*,o.order_no FROM purchase_receipt r JOIN purchase_order o ON o.id=r.order_id WHERE r.id=:id
                """).param("id", id).query((rs, row) -> new ReceiptHead(rs.getString("id"), rs.getString("order_id"),
                        rs.getString("order_no"), rs.getString("request_id"), instant(rs,"received_at"),
                        rs.getString("remark"), rs.getString("actor_login_name"), instant(rs,"created_at"))).single();
        List<ReceiptLineView> values = jdbc.sql("""
                SELECT rl.order_line_id,l.item_code,l.item_name,rl.quantity,rl.inventory_transaction_id
                FROM purchase_receipt_line rl JOIN purchase_order_line l ON l.id=rl.order_line_id
                WHERE rl.receipt_id=:id ORDER BY l.line_no
                """).param("id", id).query((rs, row) -> new ReceiptLineView(rs.getString("order_line_id"),
                        rs.getString("item_code"), rs.getString("item_name"), rs.getBigDecimal("quantity"),
                        rs.getString("inventory_transaction_id"))).list();
        return new ReceiptView(head.id(), head.orderId(), head.orderNo(), head.requestId(), head.receivedAt(),
                head.remark(), head.actorLoginName(), values, head.createdAt(), replayed);
    }

    List<ReceiptView> receipts(String orderId) {
        String where = orderId == null || orderId.isBlank() ? "" : " WHERE order_id=:orderId";
        var query = jdbc.sql("SELECT id FROM purchase_receipt" + where + " ORDER BY created_at DESC,id DESC");
        if (!where.isEmpty()) query = query.param("orderId", orderId.trim());
        return query.query(String.class).list().stream().map(id -> receipt(id, false)).toList();
    }

    long count(Status status) {
        return jdbc.sql("SELECT COUNT(*) FROM purchase_order WHERE status=:status").param("status", status.name())
                .query(Long.class).single();
    }

    private OrderView withLines(OrderView value) {
        return new OrderView(value.id(), value.orderNo(), value.orderType(), value.supplierId(), value.supplierName(),
                value.status(), value.currency(), value.remark(), value.version(), lines(value.id()), value.createdAt(),
                value.updatedAt(), value.expectedDeliveryDate(), value.deliveryAddress(), value.cancellationReason());
    }

    private OrderView mapOrder(ResultSet rs, int row) throws SQLException {
        return new OrderView(rs.getString("id"), rs.getString("order_no"), OrderType.valueOf(rs.getString("order_type")),
                rs.getString("supplier_id"), rs.getString("supplier_name"), Status.valueOf(rs.getString("status")),
                rs.getString("currency"), rs.getString("remark"), rs.getLong("version"), List.of(),
                instant(rs,"created_at"), instant(rs,"updated_at"), rs.getObject("expected_delivery_date", java.time.LocalDate.class),
                rs.getString("delivery_address"), rs.getString("cancellation_reason"));
    }
    private Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }

    record SupplierRef(String id, String name) {}
    record ItemRef(String id, String code, String name, String unit) {}
    record ResolvedLine(String id, int lineNo, ItemRef item, BigDecimal quantity, BigDecimal unitPrice) {}
    record ReceiptStored(String id, String orderId, String payloadHash) {}
    record ReceiptHead(String id,String orderId,String orderNo,String requestId,Instant receivedAt,String remark,
                       String actorLoginName,Instant createdAt) {}
}
