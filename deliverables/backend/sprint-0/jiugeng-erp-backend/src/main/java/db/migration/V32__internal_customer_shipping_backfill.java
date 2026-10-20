package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** One-time, user-authorized attribution. Completed shipments are intentionally untouched. */
public class V32__internal_customer_shipping_backfill extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 32001; }
    @Override public void migrate(Context context) throws Exception {
        Connection db=context.getConnection(); ObjectMapper json=new ObjectMapper();
        String customer;
        try(var statement=db.prepareStatement("SELECT id FROM customer WHERE name=? ORDER BY created_at,id")) {
            statement.setString(1,"九更"); try(var rows=statement.executeQuery()) { customer=rows.next()?rows.getString(1):null; }
        }
        if(customer==null) {
            customer=UUID.randomUUID().toString();
            execute(db,"INSERT INTO customer(id,name,status,created_by,updated_by) VALUES(?,?,'ACTIVE','migration','migration')",customer,"九更");
        } else execute(db,"UPDATE customer SET status='ACTIVE',version=version+1 WHERE id=?",customer);
        execute(db,"UPDATE customer_preferences SET default_customer_id=? WHERE id=1",customer);
        List<String[]> skus=new ArrayList<>();
        try(var s=db.createStatement();var r=s.executeQuery("SELECT v.id,v.product_id,v.sku_code,v.variant_name,v.lifecycle_status,p.status FROM product_variant v JOIN product p ON p.id=v.product_id")) {
            while(r.next()) skus.add(new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)});
        }
        for(var sku:skus) {
            String id=null,code="",requirements="";
            try(var s=db.prepareStatement("SELECT id,customer_product_code,requirements FROM customer_product WHERE customer_id=? AND variant_id=?")) {
                s.setString(1,customer); s.setString(2,sku[0]); try(var r=s.executeQuery()) { if(r.next()){id=r.getString(1);code=r.getString(2);requirements=r.getString(3);} }
            }
            if(id==null) {
                id=UUID.randomUUID().toString();
                execute(db,"INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,supply_price,price_unit,status,created_by,updated_by) VALUES(?,?,?,?,?,?,NULL,'套',?,'migration','migration')",
                    id,customer,sku[1],sku[0],sku[2],sku[3],"ACTIVE".equals(sku[4])&&"ACTIVE".equals(sku[5])?"ACTIVE":"DISABLED");
            }
            String snapshot=json.writeValueAsString(Map.of("id",id,"customerId",customer,"customerName","九更","productId",sku[1],"variantId",sku[0],"customerProductCode",code,"requirements",requirements));
            for(String table:List.of("dashboard_today_shipment","dashboard_pending_shipment")) {
                String status=table.endsWith("today_shipment")?"ACTIVE":"PENDING";
                List<String> targets=new ArrayList<>();
                try(var s=db.prepareStatement("SELECT id FROM "+table+" WHERE status=? AND variant_id=? AND customer_product_id IS NULL")) {
                    s.setString(1,status);s.setString(2,sku[0]);try(var r=s.executeQuery()){while(r.next())targets.add(r.getString(1));}
                }
                for(String target:targets) {
                    execute(db,"UPDATE "+table+" SET customer_product_id=?,customer_snapshot=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id,snapshot,target);
                    execute(db,"INSERT INTO audit_event(id,category,action,actor_login_name,resource_type,resource_id,outcome,after_value,detail) VALUES(?,'BUSINESS','CUSTOMER_SHIPMENT_BACKFILL','migration',?,?,'SUCCESS',?,?)",
                        UUID.randomUUID().toString(),table.endsWith("today_shipment")?"TODAY_SHIPMENT":"PENDING_SHIPMENT",target,snapshot,"ERP-060 用户确认：未完成发货归入默认内部客户");
                }
            }
        }
        execute(db,"UPDATE dashboard_shipment_document SET customer_product_id=(SELECT t.customer_product_id FROM dashboard_today_shipment t WHERE t.id=dashboard_shipment_document.today_shipment_id AND t.variant_id=dashboard_shipment_document.variant_id) WHERE customer_product_id IS NULL");
        execute(db,"INSERT INTO audit_event(id,category,action,actor_login_name,resource_type,resource_id,outcome,detail) VALUES(?,'BUSINESS','CUSTOMER_INTERNAL_INITIALIZE','migration','CUSTOMER',?,'SUCCESS',?)",
            UUID.randomUUID().toString(),customer,"ERP-060 用户确认：设置内部默认客户并建档现有 SKU，未知价格留空");
    }
    private void execute(Connection db,String sql,String...args) throws Exception {
        try(var s=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setString(i+1,args[i]);s.executeUpdate();}
    }
}
