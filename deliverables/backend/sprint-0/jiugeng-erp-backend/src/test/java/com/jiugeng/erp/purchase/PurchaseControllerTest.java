package com.jiugeng.erp.purchase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseControllerTest {
    private static final String PASSWORD = "Purchase-Test-42";
    @Autowired MockMvc mvc; @Autowired ObjectMapper objectMapper; @Autowired JdbcTemplate jdbc;
    String supplierId; String materialId; String variantId;

    @BeforeEach void setup() {
        cleanupPurchases();
        supplierId=UUID.randomUUID().toString(); materialId=UUID.randomUUID().toString(); variantId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(id,name,phone,status,categories,created_by,updated_by) VALUES(?,?,'10086','ACTIVE','MATERIAL,PRODUCT','test','test')",supplierId,"采购供应商");
        String suffix=materialId.substring(0,8).toUpperCase(); String materialCode="PUR-MAT-"+suffix;
        jdbc.update("INSERT INTO auxiliary_material(id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES(?,?,?,?, '包装','PCS','ACTIVE','test','test')",materialId,materialCode,materialCode,"采购辅料");
        String productId=UUID.randomUUID().toString();
        String productCode="PUR-PROD-"+suffix,skuCode="PUR-SKU-"+suffix;
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,?, 'ACTIVE','test','test')",productId,productCode,productCode,"采购产品");
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?, '采购SKU','ACTIVE',?)",variantId,productId,skuCode,skuCode,"a".repeat(64));
        jdbc.update("INSERT INTO product_variant_supplier(id,variant_id,supplier_id,primary_slot,created_by,updated_by) VALUES(?,?,?,'PRIMARY','test','test')",UUID.randomUUID().toString(),variantId,supplierId);
        jdbc.update("INSERT INTO auxiliary_material_supplier(id,auxiliary_material_id,supplier_id,primary_slot,created_by,updated_by) VALUES(?,?,?,'PRIMARY','test','test')",UUID.randomUUID().toString(),materialId,supplierId);
    }
    @AfterEach void cleanup(){
        cleanupPurchases();
        jdbc.update("DELETE FROM product_inventory_transaction WHERE variant_id=?",variantId);jdbc.update("DELETE FROM product_inventory_balance WHERE variant_id=?",variantId);
        // Release this fixture's links before another test class deletes catalog rows.
        jdbc.update("DELETE FROM auxiliary_material_supplier WHERE supplier_id=?",supplierId);
        jdbc.update("DELETE FROM product_variant_supplier WHERE supplier_id=?",supplierId);
    }

    @Test void deletingDeliveryHistoryPreservesOrdersAndStaysDeleted() throws Exception {
        String token=login("dh-"+UUID.randomUUID().toString().substring(0,8),"PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2");
        String id=order.get("id").asText();
        jdbc.update("UPDATE purchase_order SET delivery_address=? WHERE id=?"," 测试历史收货处 ",id);
        mvc.perform(get("/api/v1/purchases/delivery-options").header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("测试历史收货处"));
        for(int n=0;n<2;n++) mvc.perform(post("/api/v1/purchases/delivery-options/remove").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name","测试历史收货处")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/purchases/delivery-options").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryAddress").value(" 测试历史收货处 "));
        mvc.perform(post("/api/v1/purchases/delivery-options/remove").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name"," ")))).andExpect(status().isBadRequest());
        String viewer=login("delivery-viewer","SELECTION");
        mvc.perform(post("/api/v1/purchases/delivery-options/remove").header("Authorization",bearer(viewer)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name","测试历史收货处")))).andExpect(status().isForbidden());
    }

    @Test void packageCountsPersistAndRejectFractionalNegativeOrOverflowValues() throws Exception {
        String token=login("package-buyer","PRODUCT_MATERIAL");
        for (String type : List.of("PRODUCT","MATERIAL","MANUAL")) {
            Map<String,Object> line=new java.util.LinkedHashMap<>(Map.of("itemId",type.equals("PRODUCT")?variantId:materialId,"itemName","测试物品","unit","个","quantity",10,"unitPrice",2,"packageCount",3));
            Map<String,Object> payload=new java.util.LinkedHashMap<>(Map.of("orderType",type,"supplierId",supplierId,"currency","CNY","deliveryAddress","测试仓库","lines",List.of(line)));
            JsonNode created=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/orders/place").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].packageCount").value(3)).andReturn().getResponse().getContentAsString());
            String id=created.get("id").asText();
            payload.put("version",created.get("version").asLong());line.put("packageCount",7);
            mvc.perform(put("/api/v1/purchases/orders/{id}/place",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].packageCount").value(7));
            mvc.perform(get("/api/v1/purchases/orders/{id}/detail",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.lines[0].packageCount").value(7));
            for (Object invalid : List.of(-1,new java.math.BigDecimal("1.5"),2147483648L)) {
                line.put("packageCount",invalid);
                mvc.perform(post("/api/v1/purchases/orders/place").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload))).andExpect(status().isBadRequest());
            }
        }
    }

    @Test void inventoryLinkResolvesReceiptAndRequiresPermissions() throws Exception {
        String token=login("ledger-link-buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2.5");String id=order.get("id").asText();
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",order.get("version").asLong())))).andExpect(status().isOk());
        var receipt=Map.of("requestId","ledger-link-receipt","receivedAt","2026-10-16T08:00:00Z",
                "lines",List.of(Map.of("orderLineId",order.at("/lines/0/id").asText(),"quantity",2)));
        JsonNode received=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id)
                .header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String transaction=received.at("/lines/0/inventoryTransactionId").asText();
        mvc.perform(get("/api/v1/purchases/inventory-transactions/{id}/order",transaction).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id)).andExpect(jsonPath("$.orderNo").value(order.get("orderNo").asText()));
        mvc.perform(get("/api/v1/purchases/inventory-transactions/{id}/order",UUID.randomUUID()).header("Authorization",bearer(token)))
                .andExpect(status().isNotFound());
        String denied=login("ledger-link-no-purchase","SELECTION");
        mvc.perform(get("/api/v1/purchases/inventory-transactions/{id}/order",transaction).header("Authorization",bearer(denied)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/purchases/inventory-transactions/{id}/order",transaction)).andExpect(status().isUnauthorized());
    }

    @Test void materialReceiptPostsInventoryInSameWorkflowAndIsIdempotent() throws Exception {
        String token=login("buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10", "2.5"); String id=order.get("id").asText(); long version=order.get("version").asLong();
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",version))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        String lineId=order.at("/lines/0/id").asText(); Map<String,Object> receipt=Map.of("requestId","RECEIPT-001","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",4)));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false)).andExpect(jsonPath("$.lines[0].inventoryTransactionId").isNotEmpty());
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance WHERE auxiliary_material_id=?",java.math.BigDecimal.class,materialId)).isEqualByComparingTo("4.0000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE request_id LIKE 'PURCHASE:%' AND auxiliary_material_id=?",Integer.class,materialId)).isEqualTo(1);
    }

    @Test void productReceiptUpdatesProductInventoryOnceAndRejectsOverReceipt() throws Exception {
        String token=login("product-buyer","PRODUCT_MATERIAL"); JsonNode order=createOrder(token,"PRODUCT",variantId,"2","18"); String id=order.get("id").asText();
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",0)))).andExpect(status().isOk());
        String lineId=order.at("/lines/0/id").asText();
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","PRODUCT-OVER","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",3)))))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_OVER_RECEIPT"));
        var receipt=Map.of("requestId","PRODUCT-OK","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",2)));
        for(int n=0;n<2;n++)mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt))).andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(n==1)).andExpect(jsonPath("$.lines[0].inventoryTransactionId").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT quantity FROM product_inventory_balance WHERE variant_id=?",java.math.BigDecimal.class,variantId)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_inventory_transaction WHERE variant_id=?",Integer.class,variantId)).isEqualTo(1);
    }

    @Test void managedSaveIsIdempotentAndRejectsChangedPayloadAndStaleVersion() throws Exception {
        String token=login("managed-save","PRODUCT_MATERIAL");var input=managedInput("MATERIAL",materialId,10);var save=new java.util.LinkedHashMap<String,Object>(Map.of("requestId","save-once","order",input));
        var first=managedSave(token,save);var second=managedSave(token,save);assertThat(second.path("id")).isEqualTo(first.path("id"));
        input.put("remark","changed");mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(save))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_REQUEST_CONFLICT"));
        save.put("requestId","update-stale");save.put("id",first.path("id").asText());input.put("version",0);
        mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(save))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_VERSION_CONFLICT"));
    }

    @Test void countQuantitiesAreValidatedBeforePurchaseAndDisabledMaterialCanFulfillExistingOrder() throws Exception {
        String token=login("quantity-buyer","PRODUCT_MATERIAL");
        for(String type:List.of("PRODUCT","MATERIAL"))mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",UUID.randomUUID().toString(),"order",managedInput(type,type.equals("PRODUCT")?variantId:materialId,new java.math.BigDecimal("1.5")))))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PURCHASE_QUANTITY_INVALID"));
        var order=managedSave(token,Map.of("requestId","disabled-receive","order",managedInput("MATERIAL",materialId,4)));jdbc.update("UPDATE auxiliary_material SET status='DISABLED' WHERE id=?",materialId);jdbc.update("UPDATE supplier SET status='DISABLED' WHERE id=?",supplierId);
        var increase=managedInput("MATERIAL",materialId,5);increase.put("version",1);mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","disabled-increase","id",order.path("id").asText(),"order",increase)))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",order.path("id").asText()).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","disabled-receipt","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",order.at("/lines/0/id").asText(),"quantity",4)))))).andExpect(status().isOk());
    }

    @Test void multiShipmentsAllocateOnlyRemainingQuantityAndRequireMatchingReceipt() throws Exception {
        String token=login("shipment-buyer","PRODUCT_MATERIAL");var order=managedSave(token,Map.of("requestId","multi-order","order",managedInput("PRODUCT",variantId,10)));String id=order.path("id").asText(),line=order.at("/lines/0/id").asText();
        var input=new java.util.LinkedHashMap<String,Object>(Map.of("requestId","batch-one","version",1,"carrier","Synthetic","trackingNumber","SYN001","shippedOn","2026-10-16","packageCount",1,"lines",List.of(Map.of("orderLineId",line,"quantity",4))));
        var result=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());String shipment=result.at("/0/id").asText();
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        input.put("requestId","batch-over");input.put("trackingNumber","SYN002");input.put("version",2);input.put("lines",List.of(Map.of("orderLineId",line,"quantity",7)));
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
        var receipt=new java.util.LinkedHashMap<String,Object>(Map.of("requestId","batch-receipt","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",line,"quantity",2))));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_SHIPMENT_REQUIRED"));
        receipt.put("shipmentId",shipment);
        for(int n=0;n<2;n++)mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT received_quantity FROM purchase_shipment_line WHERE shipment_id=?",java.math.BigDecimal.class,shipment)).isEqualByComparingTo("2");
        receipt.put("requestId","batch-too-many");receipt.put("lines",List.of(Map.of("orderLineId",line,"quantity",3)));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_SHIPMENT_OVER_RECEIPT"));
        input.put("requestId","batch-two");input.put("version",3);input.put("lines",List.of(Map.of("orderLineId",line,"quantity",6)));
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","4").header("Authorization",bearer(token))).andExpect(status().isConflict());
    }

    @Test void listUsesRealPagingAndLiteralSearchAndExportsSafeDecimals() throws Exception {
        String token=login("paged-buyer","PRODUCT_MATERIAL");for(int i=0;i<11;i++)managedSave(token,Map.of("requestId","page-"+i,"order",managedInput("MATERIAL",materialId,2)));
        mvc.perform(get("/api/v1/purchases/manage/orders").param("type","MATERIAL").param("page","1").param("size","10").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/purchases/manage/orders").param("q","%").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        String csv=mvc.perform(post("/api/v1/purchases/manage/export").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("filters",Map.of("type","MATERIAL"),"ids",List.of())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("0.1234","0.25");
    }

    @Test void snapshotKeepsSupplierNameAndMultilineRemarkAndProtectsNewRoutes() throws Exception {
        String token=login("snapshot-buyer","PRODUCT_MATERIAL");var input=managedInput("MATERIAL",materialId,2);input.put("remark","合成说明\n第二行");var order=managedSave(token,Map.of("requestId","snapshot-order","order",input));jdbc.update("UPDATE supplier SET name='新合成名称' WHERE id=?",supplierId);
        mvc.perform(get("/api/v1/purchases/orders/{id}",order.path("id").asText()).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.supplierName").value("采购供应商")).andExpect(jsonPath("$.remark").value("合成说明\n第二行")).andExpect(jsonPath("$.lines[0].snapshotSaved").value(true));
        String denied=login("managed-denied","SELECTION");mvc.perform(get("/api/v1/purchases/manage/orders").header("Authorization",bearer(denied))).andExpect(status().isForbidden());mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(denied)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","no","order",input)))).andExpect(status().isForbidden());
    }

    @Test void catalogImageIsCopiedIndependentlyAndRetainedOnEdit() throws Exception {
        String token=login("snapshot-image-buyer","PRODUCT_MATERIAL");var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
        String image=objectMapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images").file(file).param("purpose","MATERIAL_IMAGE").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        jdbc.update("UPDATE catalog_image_asset SET status='ACTIVE',attached_resource_type='AUXILIARY_MATERIAL',attached_resource_id=? WHERE id=?",materialId,image);jdbc.update("UPDATE auxiliary_material SET image_asset_id=?,specification='合成规格' WHERE id=?",image,materialId);
        var input=managedInput("MATERIAL",materialId,2);var order=managedSave(token,Map.of("requestId","image-snapshot","order",input));String snapshot=order.at("/lines/0/imageAssetId").asText();assertThat(snapshot).isNotBlank().isNotEqualTo(image);assertThat(order.at("/lines/0/specification").asText()).isEqualTo("合成规格");
        jdbc.update("UPDATE auxiliary_material SET image_asset_id=NULL,specification='改变后的规格' WHERE id=?",materialId);input.put("version",order.path("version").asLong());input.put("remark","合成修改");var updated=managedSave(token,Map.of("requestId","image-update","id",order.path("id").asText(),"order",input));assertThat(updated.at("/lines/0/imageAssetId").asText()).isEqualTo(snapshot);assertThat(updated.at("/lines/0/specification").asText()).isEqualTo("合成规格");
        mvc.perform(get("/api/v1/purchases/manage/{id}/checks",order.path("id").asText()).header("Authorization",bearer(token))).andExpect(status().isOk());
    }

    private java.util.LinkedHashMap<String,Object> managedInput(String type,String item,Object quantity){return new java.util.LinkedHashMap<>(Map.of("orderType",type,"supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",item,"quantity",quantity,"unitPrice","0.1234"))));}
    @Test void manualCopyRejectsMissingOrNullLinesAsValidationErrors() throws Exception {
        String token=login("managed-invalid-copy","PRODUCT_MATERIAL");
        for (String lines:List.of("null","[null]","[]")) {
            mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"invalid-copy\",\"copyFromId\":\"synthetic-source\",\"order\":{\"orderType\":\"MANUAL\",\"lines\":"+lines+"}}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PURCHASE_FIELD_INVALID"));
        }
    }

    @Test void itemRemarksPersistIndependentlyThroughEditCopyAuditAndExport() throws Exception {
        String token=login("item-remark-buyer","PRODUCT_MATERIAL");
        var input=new java.util.LinkedHashMap<String,Object>(Map.of("orderType","MANUAL","supplierName","Synthetic store","currency","CNY","remark","历史整单备注","lines",List.of(
            Map.of("itemName","合成商品甲","unit","件","quantity",2,"unitPrice",3,"remark","  单独包装\n贴标  "),
            Map.of("itemName","合成商品乙","unit","件","quantity",1,"unitPrice",4,"remark","无需贴标"))));
        var order=managedSave(token,Map.of("requestId","item-note-create","order",input));String id=order.path("id").asText();
        assertThat(order.at("/lines/0/remark").asText()).isEqualTo("单独包装\n贴标");assertThat(order.at("/lines/1/remark").asText()).isEqualTo("无需贴标");
        input.put("version",order.path("version").asLong());input.put("lines",List.of(Map.of("itemName","合成商品甲","unit","件","quantity",2,"unitPrice",3,"remark","改为纸箱"),Map.of("itemName","合成商品乙","unit","件","quantity",1,"unitPrice",4)));
        order=managedSave(token,Map.of("requestId","item-note-edit","id",id,"order",input));assertThat(order.at("/lines/0/remark").asText()).isEqualTo("改为纸箱");assertThat(order.at("/lines/1/remark").isNull()).isTrue();
        input.remove("version");var copied=managedSave(token,Map.of("requestId","item-note-copy","copyFromId",id,"order",input));assertThat(copied.at("/lines/0/remark").asText()).isEqualTo("改为纸箱");
        String history=mvc.perform(get("/api/v1/purchases/manage/{id}/history",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(history).contains("改为纸箱");
        String csv=mvc.perform(post("/api/v1/purchases/manage/export").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids",List.of(id))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("商品备注,整单备注","改为纸箱","历史整单备注");
        input.put("lines",List.of(Map.of("itemName","合成商品甲","unit","件","quantity",1,"unitPrice",1,"remark","x".repeat(1001))));
        mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","item-note-too-long","order",input)))).andExpect(status().isBadRequest());
    }

    private JsonNode managedSave(String token,Object value)throws Exception{return objectMapper.readTree(mvc.perform(post("/api/v1/purchases/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(value))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

    @Test void selectionRoleCannotReadOrWritePurchases() throws Exception {
        String token=login("selector-purchase","SELECTION");
        mvc.perform(get("/api/v1/purchases/orders").header("Authorization",bearer(token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void referencesOnlyExposeItemsLinkedToTheSelectedSupplierAndDetailIncludesHistory() throws Exception {
        String token=login("reference-buyer","PRODUCT_MATERIAL");
        mvc.perform(get("/api/v1/purchases/references").param("orderType","PRODUCT").param("supplierId",supplierId)
                .header("Authorization",bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(variantId)).andExpect(jsonPath("$[0].primarySupplier").value(true));
        JsonNode order=createOrder(token,"PRODUCT",variantId,"2","18");
        mvc.perform(get("/api/v1/purchases/orders/{id}/detail",order.get("id").asText())
                .header("Authorization",bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNo").value(order.get("orderNo").asText()))
                .andExpect(jsonPath("$.receipts").isArray()).andExpect(jsonPath("$.history[0].action").value("PURCHASE_ORDER_CREATE"));
    }

    @Test void orderCannotUseAnItemThatIsNotLinkedToItsSupplier() throws Exception {
        String token=login("supplier-link-buyer","PRODUCT_MATERIAL");
        String otherSupplier=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(id,name,phone,status,categories,created_by,updated_by) VALUES(?,?,'10010','ACTIVE','MATERIAL,PRODUCT','test','test')",otherSupplier,"无关联供应商");
        mvc.perform(get("/api/v1/purchases/references").param("orderType","MATERIAL").param("supplierId",otherSupplier)
                .header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        Map<String,Object> request=Map.of("orderType","MATERIAL","supplierId",otherSupplier,"currency","CNY",
                "lines",List.of(Map.of("itemId",materialId,"quantity",1,"unitPrice",1)));
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_ITEM_LINK_REQUIRED"));
    }

    @Test void deliveryDetailsSurviveDraftUpdatesAndCancellationRequiresReason() throws Exception {
        String token=login("history-delete-buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2"); String id=order.get("id").asText();
        var input=Map.of("orderType","MATERIAL","supplierId",supplierId,"currency","CNY","version",0,
                "expectedDeliveryDate","2026-11-20","deliveryAddress","验收固定仓",
                "lines",List.of(Map.of("itemId",materialId,"quantity",10,"unitPrice",2)));
        mvc.perform(put("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expectedDeliveryDate").value("2026-11-20"))
                .andExpect(jsonPath("$.deliveryAddress").value("验收固定仓"));
        mvc.perform(put("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/purchases/orders/{id}/cancel",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"reason"," "))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/purchases/orders/{id}/cancel",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"reason","需求取消"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("需求取消"));
        mvc.perform(get("/api/v1/purchases/orders/{id}/detail",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.expectedDeliveryDate").value("2026-11-20"));
    }

    @Test void submissionRevalidatesSupplierAndItemAvailability() throws Exception {
        String token=login("submission-buyer","PRODUCT_MATERIAL"); JsonNode order=createOrder(token,"PRODUCT",variantId,"2","1");
        String id=order.get("id").asText();
        jdbc.update("UPDATE supplier SET status='DISABLED' WHERE id=?",supplierId);
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE supplier SET status='ACTIVE' WHERE id=?",supplierId);
        jdbc.update("DELETE FROM product_variant_supplier WHERE variant_id=?",variantId);
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token)))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.version").value(0));
    }

    @Test void closingPartiallyReceivedOrderPreservesStockAndRejectsFurtherReceipts() throws Exception {
        String token=login("closing-buyer","PRODUCT_MATERIAL"); JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2");
        String id=order.get("id").asText(),lineId=order.at("/lines/0/id").asText();
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        var receipt=Map.of("requestId",UUID.randomUUID().toString(),"receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",4)));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/purchases/orders/{id}/cancel",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"reason","余量不再供货"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/purchases/orders/{id}/cancel",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",2,"reason","余量不再供货"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].receivedQuantity").value(4));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",UUID.randomUUID().toString(),"receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",1))))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance WHERE auxiliary_material_id=?",java.math.BigDecimal.class,materialId)).isEqualByComparingTo("4");
    }

    @Test void rejectsNullLinesAndOutOfRangeNumbersWithoutServerErrors() throws Exception {
        String token=login("bounds-buyer","PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderType\":\"MATERIAL\",\"supplierId\":\""+supplierId+"\",\"currency\":\"CNY\",\"lines\":[null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderType","MATERIAL","supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",materialId,"quantity","100000000000000","unitPrice",1))))))
                .andExpect(status().isBadRequest());
    }

    @Test void placeCreatesSubmittedOrderAndLegacyDraftCanBePlacedAtomically()throws Exception {
        String token=login("place-buyer","PRODUCT_MATERIAL");
        var input=new java.util.LinkedHashMap<String,Object>(Map.of("orderType","MATERIAL","supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",materialId,"quantity",2,"unitPrice",3))));
        mvc.perform(post("/api/v1/purchases/orders/place").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        JsonNode draft=createOrder(token,"MATERIAL",materialId,"2","3");String id=draft.path("id").asText();input.put("version",0);
        mvc.perform(put("/api/v1/purchases/orders/{id}/place",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(put("/api/v1/purchases/orders/{id}/place",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
        input.put("version",2);
        input.put("lines",java.util.List.of(Map.of("itemId",materialId,"quantity",5,"unitPrice",4)));
        mvc.perform(put("/api/v1/purchases/orders/{id}/place",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.lines[0].orderedQuantity").value(5));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE status='DRAFT'",Integer.class)).isZero();
    }

    @Test void lineTaxDefaultsAndValidationRoundTrip()throws Exception {
        String token=login("tax-buyer","PRODUCT_MATERIAL");
        var line=new java.util.LinkedHashMap<String,Object>(Map.of("itemId",materialId,"quantity",2,"unitPrice",101,"taxIncluded",true));
        var input=new java.util.LinkedHashMap<String,Object>(Map.of("orderType","MATERIAL","supplierId",supplierId,"currency","CNY","lines",List.of(line)));
        String body=mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].taxIncluded").value(true)).andExpect(jsonPath("$.lines[0].taxRate").value(1)).andExpect(jsonPath("$.lines[0].unitPrice").value(101)).andReturn().getResponse().getContentAsString();
        String id=objectMapper.readTree(body).path("id").asText();
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(jsonPath("$.lines[0].taxRate").value(1));
        line.put("taxRate",101);
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
        line.put("taxIncluded",false);input.put("version",0);
        mvc.perform(put("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].taxIncluded").value(false)).andExpect(jsonPath("$.lines[0].taxRate").value(0));
    }

    @Test void waybillOnlyLogisticsPersistsAndIsProtected()throws Exception {
        String token=login("waybill-buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"2","3");String id=order.path("id").asText();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new org.springframework.mock.web.MockMultipartFile("file","waybill.png","image/png",bytes.toByteArray());
        String image=objectMapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images").file(file).param("purpose","PURCHASE_WAYBILL").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        var payload=Map.of("version",0,"trackingNumber","","waybillImageId",image);
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.waybillImageId").value(image)).andExpect(jsonPath("$.trackingNumber").value(""));
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.waybillImageId").value(image));
        mvc.perform(get("/api/v1/catalog-images/{id}/thumbnail",image).header("Authorization",bearer(token))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"trackingNumber","","waybillImageId","")))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"trackingNumber","SYN-123","waybillImageId","missing")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1)))).andExpect(status().isOk());
        var shipmentInput=Map.of("requestId","legacy-waybill-copy","version",2,"imageId",image,"shippedOn","2026-10-16","packageCount",1,"lines",List.of(Map.of("orderLineId",order.at("/lines/0/id").asText(),"quantity",2)));
        var converted=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(shipmentInput))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String copy=converted.at("/0/imageId").asText();assertThat(copy).isNotBlank().isNotEqualTo(image);
        assertThat(jdbc.queryForObject("SELECT attached_resource_type FROM catalog_image_asset WHERE id=?",String.class,image)).isEqualTo("PURCHASE_WAYBILL");
        assertThat(jdbc.queryForObject("SELECT attached_resource_type FROM catalog_image_asset WHERE id=?",String.class,copy)).isEqualTo("PURCHASE_SHIPMENT");
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(shipmentInput))).andExpect(status().isOk()).andExpect(jsonPath("$[0].imageId").value(copy));
    }

    @Test void logisticsPreservesVersionAndExposesMaterialImage()throws Exception {
        String token=login("logistics-buyer","PRODUCT_MATERIAL");
        jdbc.update("UPDATE auxiliary_material SET image_url=? WHERE id=?","https://example.com/synthetic.png",materialId);
        JsonNode order=createOrder(token,"MATERIAL",materialId,"2","3");String id=order.path("id").asText();
        assertThat(order.at("/lines/0/imageUrl").asText()).isEqualTo("https://example.com/synthetic.png");
        var payload=Map.of("version",0,"carrier","合成物流","trackingNumber","SYN000123");
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.trackingNumber").value("SYN000123")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"trackingNumber","invalid value")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.carrier").value("合成物流"));
    }

    @Test void deletesOnlyUnreceivedOrdersWithVersionAuditAndNoSupplierDependency() throws Exception {
        String token=login("delete-buyer","PRODUCT_MATERIAL");
        for(String state:List.of("DRAFT","SUBMITTED","CANCELLED")) {
            JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2");
            String id=order.path("id").asText();
            jdbc.update("UPDATE purchase_order SET status=? WHERE id=?",state,id);
            mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","99").header("Authorization",bearer(token))).andExpect(status().isConflict());
            mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","0").header("Authorization",bearer(token))).andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_line WHERE order_id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE supplier_id=?",Integer.class,supplierId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='PURCHASE_ORDER_DELETE' AND resource_id=?",Integer.class,id)).isEqualTo(1);
        }
        // With all other associations removed, deleted orders no longer block supplier deletion.
        jdbc.update("DELETE FROM product_variant_supplier WHERE supplier_id=?",supplierId);
        jdbc.update("DELETE FROM auxiliary_material_supplier WHERE supplier_id=?",supplierId);
        mvc.perform(delete("/api/v1/suppliers/{id}",supplierId).param("version","0").header("Authorization",bearer(token))).andExpect(status().isNoContent());
    }

    @Test void receivedEvenCancelledOrdersAndUnauthorizedDeletionAreProtected() throws Exception {
        String token=login("delete-received-buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10","2");
        String id=order.path("id").asText();
        String denied=login("delete-denied","SELECTION");
        mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","0").header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",0)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("requestId","DELETE-PROTECTED","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",order.at("/lines/0/id").asText(),"quantity",2)))))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","2").header("Authorization",bearer(token))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/purchases/orders/{id}/cancel",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",2,"reason","测试余量关闭")))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/purchases/orders/{id}",id).param("version","3").header("Authorization",bearer(token))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_receipt WHERE order_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test void manualItemsNeedNoCatalogAndReceiveWithoutInventory() throws Exception {
        String token=login("manual-buyer","PRODUCT_MATERIAL");
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
        String image=objectMapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images").file(file).param("purpose","PURCHASE_ITEM").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        var payload=Map.of("orderType","MANUAL","supplierName","Synthetic store","currency","CNY","lines",List.of(Map.of("itemName","Synthetic bulb","unit","个","specification","12 W","quantity",4,"unitPrice",8,"imageAssetId",image)));
        JsonNode order=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.supplierName").value("Synthetic store")).andExpect(jsonPath("$.lines[0].imageAssetId").value(image)).andReturn().getResponse().getContentAsString());
        String id=order.path("id").asText();
        var edit=new java.util.LinkedHashMap<String,Object>(payload);edit.put("version",0);
        order=objectMapper.readTree(mvc.perform(put("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.lines[0].imageAssetId").value(image)).andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,image)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/catalog-images/{id}/thumbnail",image).header("Authorization",bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1)))).andExpect(status().isOk());
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","MANUAL-RECEIPT-"+i,"receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",order.at("/lines/0/id").asText(),"quantity",2)))))).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].inventoryTransactionId").doesNotExist());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.lines[0].specification").value("12 W"));
    }

    @Test void supplierCategoriesPreventCrossTypePurchases() throws Exception {
        String token=login("category-buyer","PRODUCT_MATERIAL");
        jdbc.update("UPDATE supplier SET categories='MATERIAL' WHERE id=?",supplierId);
        mvc.perform(get("/api/v1/purchases/references").param("orderType","PRODUCT").param("supplierId",supplierId).header("Authorization",bearer(token)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_CATEGORY_MISMATCH"));
        var input=Map.of("orderType","PRODUCT","supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",variantId,"quantity",1,"unitPrice",1)));
        mvc.perform(post("/api/v1/purchases/orders/place").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_CATEGORY_MISMATCH"));
        mvc.perform(get("/api/v1/purchases/references").param("orderType","MATERIAL").param("supplierId",supplierId).header("Authorization",bearer(token))).andExpect(status().isOk());
        jdbc.update("UPDATE supplier SET categories='MATERIAL,PRODUCT' WHERE id=?",supplierId);
        mvc.perform(post("/api/v1/purchases/orders/place").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk());
    }

    @Test void unifiedWaybillsBlockLegacyWritesAndPreserveReceiptQuantitiesAcrossIdempotentEdits() throws Exception {
        String token=login("unified-waybill","PRODUCT_MATERIAL");var order=managedSave(token,Map.of("requestId","unified-order","order",managedInput("MATERIAL",materialId,10)));
        String id=order.path("id").asText(),line=order.at("/lines/0/id").asText();
        var input=new java.util.LinkedHashMap<String,Object>(Map.of("requestId","unified-first","version",1,"carrier","Synthetic","trackingNumber","WAY001","shippedOn","2026-10-16","packageCount",1,"lines",List.of(Map.of("orderLineId",line,"quantity",5))));
        var first=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());String sid=first.at("/0/id").asText();
        input.put("requestId","duplicate-tracking");input.put("version",2);
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
        input.put("requestId","unified-second");input.put("trackingNumber","WAY002");
        mvc.perform(post("/api/v1/purchases/manage/{id}/shipments",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/purchases/orders/{id}/logistics",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",3,"carrier","Other","trackingNumber","DETACHED")))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_UNIFIED_LOGISTICS_REQUIRED"));
        var edit=new java.util.LinkedHashMap<String,Object>(input);edit.put("requestId","waybill-edit");edit.put("version",3);edit.put("shipmentVersion",0);edit.put("trackingNumber","WAY001-EDIT");edit.put("lines",List.of(Map.of("orderLineId",line,"quantity",6)));
        mvc.perform(put("/api/v1/purchases/manage/{id}/shipments/{sid}",id,sid).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isBadRequest());
        var receipt=Map.of("requestId","unified-receipt","shipmentId",sid,"receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",line,"quantity",2)));
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt))).andExpect(status().isOk());
        edit.put("version",4);edit.put("lines",List.of(Map.of("orderLineId",line,"quantity",1)));
        mvc.perform(put("/api/v1/purchases/manage/{id}/shipments/{sid}",id,sid).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isBadRequest());
        edit.put("lines",List.of(Map.of("orderLineId",line,"quantity",5)));
        for(int i=0;i<2;i++)mvc.perform(put("/api/v1/purchases/manage/{id}/shipments/{sid}",id,sid).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT version FROM purchase_order WHERE id=?",Long.class,id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT received_quantity FROM purchase_shipment_line WHERE shipment_id=?",java.math.BigDecimal.class,sid)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_receipt WHERE order_id=?",Long.class,id)).isEqualTo(1);
        edit.put("trackingNumber","altered-replay");mvc.perform(put("/api/v1/purchases/manage/{id}/shipments/{sid}",id,sid).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_REQUEST_CONFLICT"));
        var summary=objectMapper.readTree(mvc.perform(post("/api/v1/purchases/manage/shipment-summaries").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(List.of(id)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(summary.path(id).size()).isEqualTo(2);assertThat(summary.path(id).toString()).contains("WAY001-EDIT","WAY002");
        String denied=login("unified-denied","SELECTION");mvc.perform(put("/api/v1/purchases/manage/{id}/shipments/{sid}",id,sid).header("Authorization",bearer(denied)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isForbidden());
    }

    private JsonNode createOrder(String token,String type,String itemId,String quantity,String price)throws Exception{
        Map<String,Object> request=Map.of("orderType",type,"supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",itemId,"quantity",quantity,"unitPrice",price)));
        String body=mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();return objectMapper.readTree(body);
    }
    private String login(String name,String role)throws Exception{String uid=UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",uid,name,name,encoder.encode(PASSWORD),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",uid);String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginName",name,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return objectMapper.readTree(body).get("token").asText();}
    private void cleanupPurchases(){jdbc.update("DELETE FROM purchase_write_request");jdbc.update("DELETE FROM purchase_shipment_line");jdbc.update("DELETE FROM purchase_shipment");jdbc.update("DELETE FROM purchase_delivery_hidden");jdbc.update("DELETE FROM purchase_receipt_line");jdbc.update("DELETE FROM purchase_receipt");jdbc.update("DELETE FROM purchase_order_line");jdbc.update("DELETE FROM purchase_order");}
    private String json(Object value)throws Exception{return objectMapper.writeValueAsString(value);}private String bearer(String token){return "Bearer "+token;}
}
