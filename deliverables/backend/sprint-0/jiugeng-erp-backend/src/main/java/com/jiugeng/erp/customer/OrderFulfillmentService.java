package com.jiugeng.erp.customer;

import static com.jiugeng.erp.customer.CustomerOrderService.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderFulfillmentService implements OrderFulfillment {
    private final CustomerOrderService orders; private final JdbcClient jdbc;
    OrderFulfillmentService(CustomerOrderService orders,JdbcClient jdbc) {this.orders=orders;this.jdbc=jdbc;}
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<Allocation> reserve(String orderId,Request request,String key,AuthenticatedActor actor) {
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw invalid("请求标识无效");
        orders.lock(orderId);var order=orders.get(orderId);
        String hash;
        try {hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(orders.json(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        var old=jdbc.sql("SELECT id,request_hash FROM customer_order_allocation WHERE order_id=:id AND request_key=:key ORDER BY created_at,id")
            .param("id",orderId).param("key",key).query((r,n)-> {if(!hash.equals(r.getString("request_hash")))throw conflict("请求标识已被用于不同内容");return r.getString("id");}).list();
        if(!old.isEmpty())return old.stream().map(this::get).toList();
        if(order.version()!=request.version()||!order.status().equals("CONFIRMED"))throw conflict("请刷新订单，只有已确认订单可安排发货");
        var ids=new HashSet<String>();var result=new ArrayList<Allocation>();
        for(var item:request.items()) {
            if(!ids.add(item.lineId()))throw invalid("同一次安排不能重复选择明细");
            var line=order.lines().stream().filter(l->l.id().equals(item.lineId())).findFirst().orElseThrow(()->invalid("订单明细不存在"));
            if(item.latestDate().isBefore(item.plannedDate()))throw invalid("最晚发货日期不能早于计划日期");
            if(line.details().quantity().subtract(line.allocatedQuantity()).subtract(line.shippedQuantity()).compareTo(item.quantity())<0)throw conflict("安排数量超过订单剩余可发数量");
            String id=UUID.randomUUID().toString();
            var value=new Allocation(id,orderId,order.orderNumber(),line.id(),line.details().name(),line.details().unit(),line.skuCode(),line.product(),line.namePresentation(),item,"PENDING",null,null,request.recipient(),request.phone(),request.address(),text(order.details().contractNumber()).isEmpty()?text(order.details().externalNumber()):order.details().contractNumber(),"",0);
            jdbc.sql("INSERT INTO customer_order_allocation(id,order_id,line_id,quantity,state,request_key,request_hash,payload) VALUES(:id,:order,:line,:qty,'PENDING',:key,:hash,:payload)")
                .param("id",id).param("order",orderId).param("line",line.id()).param("qty",item.quantity()).param("key",key).param("hash",hash).param("payload",orders.json(value)).update();
            result.add(value);
        }
        orders.record(actor,orderId,"ORDER_ARRANGE","已安排 "+result.size()+" 项分批发货");return result;
    }
    @Override @Transactional(readOnly=true)
    public List<Allocation> allocations(String orderId) { orders.get(orderId);return jdbc.sql("SELECT id FROM customer_order_allocation WHERE order_id=:id ORDER BY created_at,id").param("id",orderId).query(String.class).list().stream().map(this::get).toList(); }
    @Override @Transactional(readOnly=true)
    public Allocation get(String id) {return jdbc.sql("SELECT * FROM customer_order_allocation WHERE id=:id").param("id",id).query((r,n)-> {
        var a=orders.read(r.getString("payload"),Allocation.class);var d=a.details();
        return new Allocation(a.id(),a.orderId(),a.orderNumber(),a.lineId(),a.name(),a.unit(),a.skuCode(),a.product(),a.namePresentation(),new Item(d.lineId(),r.getBigDecimal("quantity"),d.boxCount(),d.plannedDate(),d.latestDate(),d.remark()),r.getString("state"),r.getString("pending_id"),r.getString("today_id"),a.recipient(),a.phone(),a.address(),a.reference(),r.getString("logistics"),r.getLong("version"));
    }).optional().orElseThrow(()->invalid("发货安排不存在"));}
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void link(String id,String pendingId,String todayId,String state,AuthenticatedActor actor) {
        var initial=get(id);orders.lock(initial.orderId());var a=get(id);
        if(!List.of("PENDING","TODAY").contains(a.state())||!List.of("PENDING","TODAY").contains(state))throw conflict("发货安排已完成或删除");
        jdbc.sql("UPDATE customer_order_allocation SET pending_id=COALESCE(:pending,pending_id),today_id=COALESCE(:today,today_id),state=:state,version=version+1 WHERE id=:id")
            .param("pending",pendingId).param("today",todayId).param("state",state).param("id",id).update();
        orders.record(actor,a.orderId(),"ORDER_SHIPMENT_TRANSFER","发货安排状态："+state);
    }
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void change(String id,BigDecimal quantity,String cp,String action,AuthenticatedActor actor) {
        if(id==null)return;
        var initial=get(id);orders.lock(initial.orderId());var a=get(id);var order=orders.get(a.orderId());
        if(!a.product().id().equals(cp))throw conflict("订单关联发货不能更换客户商品");
        if(!List.of("PENDING","TODAY").contains(a.state()))throw conflict("发货安排已完成或删除");
        if(!order.status().equals("CONFIRMED"))throw conflict("订单不可继续发货");
        var line=order.lines().stream().filter(l->l.id().equals(a.lineId())).findFirst().orElseThrow();
        if(quantity==null||quantity.signum()<=0||quantity.scale()>3||quantity.compareTo(new BigDecimal("999999999.999"))>0)throw invalid("发货数量必须为正数，最多3位小数");
        if(line.details().quantity().subtract(line.allocatedQuantity()).subtract(line.shippedQuantity()).add(a.details().quantity()).compareTo(quantity)<0)throw conflict("发货数量超过订单可用数量");
        String state=switch(action) {case "DELETE"->"CANCELLED";case "DISPATCH"->"SHIPPED";case "UPDATE"->a.state();default->throw invalid("操作无效");};
        if(action.equals("DISPATCH")&&!a.state().equals("TODAY"))throw conflict("仅今日发货安排可确认发货");
        jdbc.sql("UPDATE customer_order_allocation SET quantity=:qty,state=:state,version=version+1 WHERE id=:id").param("qty",quantity).param("state",state).param("id",id).update();
        orders.record(actor,a.orderId(),"ORDER_SHIPMENT_"+action,"发货数量："+a.details().quantity()+" → "+quantity+"；状态："+state);
    }
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void updatePlan(String id,int boxCount,java.time.LocalDate plannedDate,java.time.LocalDate latestDate,String remark) {
        var initial=get(id);orders.lock(initial.orderId());var a=get(id);
        if(!List.of("PENDING","TODAY").contains(a.state()))throw conflict("发货安排已完成或删除");
        var d=new Item(a.lineId(),a.details().quantity(),boxCount,plannedDate,latestDate,remark);
        var value=new Allocation(a.id(),a.orderId(),a.orderNumber(),a.lineId(),a.name(),a.unit(),a.skuCode(),a.product(),a.namePresentation(),d,a.state(),a.pendingId(),a.todayId(),a.recipient(),a.phone(),a.address(),a.reference(),a.logistics(),a.version());
        jdbc.sql("UPDATE customer_order_allocation SET payload=:payload,version=version+1 WHERE id=:id").param("payload",orders.json(value)).param("id",id).update();
    }
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void logistics(String id,long version,String value,AuthenticatedActor actor) {
        var initial=get(id);orders.lock(initial.orderId());var a=get(id);
        if(a.version()!=version)throw conflict("发货安排已更新，请刷新");
        if(!a.state().equals("SHIPPED"))throw conflict("请在确认发货后登记物流");
        if(text(value).length()>1000)throw invalid("物流信息过长");
        jdbc.sql("UPDATE customer_order_allocation SET logistics=:value,version=version+1 WHERE id=:id").param("value",text(value)).param("id",id).update();
        orders.record(actor,a.orderId(),"ORDER_LOGISTICS_UPDATE","已更新发货批次物流信息");
    }
}
