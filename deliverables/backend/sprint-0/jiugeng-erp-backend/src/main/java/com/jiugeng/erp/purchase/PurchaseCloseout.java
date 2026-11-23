package com.jiugeng.erp.purchase;

import static com.jiugeng.erp.purchase.PurchaseTypes.*;
import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Shared invariants for legacy and current purchase routes. */
@Component
class PurchaseCloseout {
 final JdbcClient jdbc; final CatalogImageGateway images;
 PurchaseCloseout(JdbcClient jdbc,CatalogImageGateway images){this.jdbc=jdbc;this.images=images;}
 static final Set<String> MEASURED=Set.of("米","厘米","毫米","千克","公斤","克","升","毫升","平方米","立方米","M","CM","MM","KG","G","L","ML","M2","M3");
 static void quantity(OrderType type,String unit,BigDecimal q){if(q==null||q.signum()<=0||q.stripTrailingZeros().scale()>(type==OrderType.MANUAL||type==OrderType.MATERIAL&&MEASURED.contains(unit.trim().toUpperCase(Locale.ROOT))?4:0)||q.compareTo(new BigDecimal("99999999999999.9999"))>0)throw new PurchaseException(400,"PURCHASE_QUANTITY_INVALID","请按单位填写数量：产品及计数辅料须为整数，计量辅料最多四位小数");}
 long shipments(String id){return jdbc.sql("SELECT COUNT(*) FROM purchase_shipment WHERE order_id=:id").param("id",id).query(Long.class).single();}
 void snapshot(String id,OrderInput input,OrderView before,AuthenticatedActor actor){
  String name=before!=null&&Objects.equals(before.supplierId(),input.supplierId())?before.supplierName():input.orderType()==OrderType.MANUAL?input.supplierName():jdbc.sql("SELECT name FROM supplier WHERE id=:id").param("id",input.supplierId()).query(String.class).single();
  jdbc.sql("UPDATE purchase_order SET supplier_name=:name,snapshot_saved=TRUE WHERE id=:id").param("id",id).param("name",name).update();
  for(var line:input.lines()){
   if(input.orderType()==OrderType.MANUAL){jdbc.sql("UPDATE purchase_order_line SET snapshot_saved=TRUE WHERE order_id=:id").param("id",id).update();continue;}
   var prior=before==null?Optional.<LineView>empty():before.lines().stream().filter(l->l.itemId().equals(line.itemId())&&l.snapshotSaved()).findFirst();
   String image,url,spec;
   if(prior.isPresent()){image=prior.get().imageAssetId();url=prior.get().imageUrl();spec=prior.get().specification();}
   else{
    String sql=input.orderType()==OrderType.PRODUCT?"SELECT main_image_asset_id image,main_image_url url,specification spec FROM product_variant WHERE id=:id":"SELECT image_asset_id image,image_url url,specification spec FROM auxiliary_material WHERE id=:id";
    var row=jdbc.sql(sql).param("id",line.itemId()).query((r,n)->new String[]{r.getString("image"),r.getString("url"),r.getString("spec")}).single();
    image=images.copyForPurchase(row[0],id,actor);url=row[1];spec=row[2];
   }
   jdbc.sql("UPDATE purchase_order_line SET image_asset_id=:image,snapshot_image_url=:url,specification=:spec,snapshot_saved=TRUE WHERE order_id=:id AND item_id=:item")
    .param("image",image).param("url",url).param("spec",spec).param("id",id).param("item",line.itemId()).update();
  }
 }
 void checkReceipt(OrderView order,ReceiptInput input){
  if(shipments(order.id())>0&&input.shipmentId()==null)throw new PurchaseException(409,"PURCHASE_SHIPMENT_REQUIRED","该采购单已分批发货，请选择本次收货批次");
  if(input.shipmentId()==null)return;
  if(jdbc.sql("SELECT COUNT(*) FROM purchase_shipment WHERE id=:id AND order_id=:order").param("id",input.shipmentId()).param("order",order.id()).query(Long.class).single()!=1)throw new PurchaseException(409,"PURCHASE_SHIPMENT_MISMATCH","发货批次不属于当前采购单");
  for(var line:input.lines()){
   var left=jdbc.sql("SELECT quantity-received_quantity FROM purchase_shipment_line WHERE shipment_id=:id AND order_line_id=:line").param("id",input.shipmentId()).param("line",line.orderLineId()).query(BigDecimal.class).optional();
   if(left.isEmpty()||line.quantity().compareTo(left.get())>0)throw new PurchaseException(409,"PURCHASE_SHIPMENT_OVER_RECEIPT","收货数量超过该批次的待收数量");
  }
 }
 void recordReceipt(String receiptId,ReceiptInput input){if(input.shipmentId()==null)return;
  jdbc.sql("UPDATE purchase_receipt SET shipment_id=:shipment WHERE id=:id").param("shipment",input.shipmentId()).param("id",receiptId).update();
  for(var line:input.lines())jdbc.sql("UPDATE purchase_shipment_line SET received_quantity=received_quantity+:q WHERE shipment_id=:id AND order_line_id=:line").param("q",line.quantity()).param("id",input.shipmentId()).param("line",line.orderLineId()).update();
 }
}
