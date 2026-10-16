package com.jiugeng.erp.purchase;

import static com.jiugeng.erp.purchase.PurchaseTypes.*;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchases")
class PurchaseController {
    private final PurchaseService service;
    PurchaseController(PurchaseService service) { this.service = service; }
    @GetMapping("/dashboard") @PreAuthorize("hasAuthority('purchases.read')") Dashboard dashboard(){return service.dashboard();}
    @GetMapping("/orders") @PreAuthorize("hasAuthority('purchases.read')") List<OrderView> orders(@RequestParam(required=false)Status status){return service.orders(status);}
    @GetMapping("/orders/{id}") @PreAuthorize("hasAuthority('purchases.read')") OrderView order(@PathVariable String id){return service.order(id);}
    @PostMapping("/orders") @PreAuthorize("hasAuthority('purchases.write')") OrderView create(@RequestBody OrderRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.create(request.input(),actor);}
    @PutMapping("/orders/{id}") @PreAuthorize("hasAuthority('purchases.write')") OrderView update(@PathVariable String id,@RequestBody OrderRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.update(id,request.input(),actor);}
    @PostMapping("/orders/{id}/submit") @PreAuthorize("hasAuthority('purchases.write')") OrderView submit(@PathVariable String id,@RequestBody VersionRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.submit(id,request.version(),actor);}
    @PostMapping("/orders/{id}/cancel") @PreAuthorize("hasAuthority('purchases.write')") OrderView cancel(@PathVariable String id,@RequestBody VersionRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.cancel(id,request.version(),actor);}
    @PostMapping("/orders/{id}/receipts") @PreAuthorize("hasAuthority('purchases.receive')") ReceiptView receive(@PathVariable String id,@RequestBody ReceiptRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.receive(id,request.input(),actor);}
    @GetMapping("/receipts") @PreAuthorize("hasAuthority('purchases.read')") List<ReceiptView> receipts(@RequestParam(required=false)String orderId){return service.receipts(orderId);}

    record LineRequest(String itemId,BigDecimal quantity,BigDecimal unitPrice){LineInput input(){return new LineInput(itemId,quantity,unitPrice);}}
    record OrderRequest(OrderType orderType,String supplierId,String currency,String remark,Long version,List<LineRequest>lines){OrderInput input(){return new OrderInput(orderType,supplierId,currency,remark,version,lines==null?null:lines.stream().map(LineRequest::input).toList());}}
    record VersionRequest(long version){}
    record ReceiptLineRequest(String orderLineId,BigDecimal quantity){ReceiptLineInput input(){return new ReceiptLineInput(orderLineId,quantity);}}
    record ReceiptRequest(String requestId,Instant receivedAt,String remark,List<ReceiptLineRequest>lines){ReceiptInput input(){return new ReceiptInput(requestId,receivedAt,remark,lines==null?null:lines.stream().map(ReceiptLineRequest::input).toList());}}
}
