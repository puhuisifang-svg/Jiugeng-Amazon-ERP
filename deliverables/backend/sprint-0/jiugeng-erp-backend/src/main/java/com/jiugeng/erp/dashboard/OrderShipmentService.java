package com.jiugeng.erp.dashboard;

import com.jiugeng.erp.customer.OrderFulfillment;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderShipmentService {
    private final OrderFulfillment orders;private final DashboardRepository repository;private final JdbcClient jdbc;
    OrderShipmentService(OrderFulfillment orders,DashboardRepository repository,JdbcClient jdbc) {this.orders=orders;this.repository=repository;this.jdbc=jdbc;}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    List<OrderFulfillment.Allocation> arrange(String id,OrderFulfillment.Request request,String key,AuthenticatedActor actor) {
        var allocations=orders.reserve(id,request,key,actor);
        for(var a:allocations) {
            if(a.pendingId()!=null)continue;
            String pending=UUID.randomUUID().toString();var d=a.details();var cp=a.product();
            var input=new DashboardTypes.ShipmentInput(d.plannedDate(),cp.productId(),null,d.quantity(),d.boxCount(),d.remark(),d.latestDate().plusDays(1).atStartOfDay(DashboardService.BUSINESS_ZONE).toInstant().minusSeconds(1),cp.variantId(),cp.id(),cp);
            repository.insertPendingShipment(pending,input,a.skuCode(),a.name(),actor.userId());
            jdbc.sql("UPDATE dashboard_pending_shipment SET quantity_unit=:unit,order_allocation_id=:allocation WHERE id=:id")
                .param("unit",a.unit()).param("allocation",a.id()).param("id",pending).update();
            orders.link(a.id(),pending,null,"PENDING",actor);
        }
        return orders.allocations(id);
    }
    String allocation(String kind,String id) {
        String table=kind.equals("TODAY")?"dashboard_today_shipment":"dashboard_pending_shipment";
        return jdbc.sql("SELECT order_allocation_id FROM "+table+" WHERE id=:id").param("id",id).query((r,n)->r.getString(1)).optional().orElse(null);
    }
    void change(String kind,String id,BigDecimal quantity,String cp,String action,AuthenticatedActor actor) {
        var allocation=allocation(kind,id);if(allocation==null)return;
        var a=orders.get(allocation);
        // Deleting the historical pending row must not release a live today shipment.
        if(action.equals("DELETE")&&kind.equals("PENDING")&&List.of("TODAY","SHIPPED").contains(a.state()))return;
        orders.change(allocation,quantity,cp,action,actor);
    }
    void updatePlan(String kind,String id,DashboardTypes.ShipmentInput input) {
        var allocation=allocation(kind,id);if(allocation==null)return;
        var a=orders.get(allocation);
        orders.updatePlan(allocation,input.boxCount(),input.date(),input.latestShipAt()==null?a.details().latestDate():input.latestShipAt().atZone(DashboardService.BUSINESS_ZONE).toLocalDate(),input.remark());
    }
    List<String> allocationIds(String orderId) {return orders.allocations(orderId).stream().map(OrderFulfillment.Allocation::id).toList();}
    void transfer(String from,String source,String target,AuthenticatedActor actor) {
        String allocation=allocation(from,source);if(allocation==null)return;
        var a=orders.get(allocation);boolean toToday=from.equals("PENDING");
        String table=toToday?"dashboard_today_shipment":"dashboard_pending_shipment";
        jdbc.sql("UPDATE "+table+" SET order_allocation_id=:allocation,quantity_unit=:unit WHERE id=:id")
            .param("allocation",allocation).param("unit",a.unit()).param("id",target).update();
        orders.link(allocation,toToday?source:target,toToday?target:source,toToday?"TODAY":"PENDING",actor);
    }
    void dispatch(String todayId,BigDecimal quantity,String cp,AuthenticatedActor actor) {
        change("TODAY",todayId,quantity,cp,"DISPATCH",actor);
        jdbc.sql("UPDATE dashboard_shipment_dispatch SET quantity_unit=(SELECT quantity_unit FROM dashboard_today_shipment WHERE id=:id),order_allocation_id=(SELECT order_allocation_id FROM dashboard_today_shipment WHERE id=:id) WHERE today_shipment_id=:id").param("id",todayId).update();
    }
}
