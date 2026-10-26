package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.CostTypes.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/product-costs")
class CostController {
    private final CostService service;private final AuditHistoryLookup audit;
    CostController(CostService service,AuditHistoryLookup audit){this.service=service;this.audit=audit;}
    @GetMapping @PreAuthorize("hasAuthority('costs.read')")
    CostService.Page list(@RequestParam(defaultValue="") @Size(max=128) String q,@RequestParam(defaultValue="") String productId,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(q,productId,state,page,size);}
    @GetMapping("/materials/{id}/price") @PreAuthorize("hasAuthority('costs.read')") CostService.Price price(@PathVariable String id){return service.price(id);}
    @PutMapping("/materials/{id}/price") @PreAuthorize("hasAuthority('costs.write') and hasAuthority('materials.write')") CostService.Price price(@PathVariable String id,@Valid @RequestBody PriceInput input,@AuthenticationPrincipal AuthenticatedActor actor){return service.savePrice(id,input,actor);}
    @GetMapping("/materials/{id}/purchases") @PreAuthorize("hasAuthority('costs.read') and hasAuthority('purchases.read')") java.util.List<CostService.PurchaseReference> purchases(@PathVariable String id){return service.purchases(id);}
    @GetMapping("/operations") @PreAuthorize("hasAuthority('costs.read')") java.util.List<CostService.Operation> operations(){return service.operations();}
    @PostMapping("/operations") @PreAuthorize("hasAuthority('costs.write')") CostService.Operation createOperation(@Valid @RequestBody OperationInput input,@AuthenticationPrincipal AuthenticatedActor actor){return service.operation(null,input,actor);}
    @PutMapping("/operations/{id}") @PreAuthorize("hasAuthority('costs.write')") CostService.Operation updateOperation(@PathVariable String id,@Valid @RequestBody OperationInput input,@AuthenticationPrincipal AuthenticatedActor actor){return service.operation(id,input,actor);}
    @GetMapping("/skus/{id}/initial") @PreAuthorize("hasAuthority('costs.read')") Input initial(@PathVariable String id){return service.initial(id);}
    @PostMapping("/skus/{id}/preview") @PreAuthorize("hasAuthority('costs.read')") Snapshot preview(@PathVariable String id,@Valid @RequestBody Input input){return service.preview(id,input);}
    @PostMapping("/skus/{id}/draft") @PreAuthorize("hasAuthority('costs.write')") Version save(@PathVariable String id,@Valid @RequestBody Save input,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(id,input,actor);}
    @GetMapping("/skus/{id}/versions") @PreAuthorize("hasAuthority('costs.read')") java.util.List<Version> versions(@PathVariable String id){return service.history(id);}
    @GetMapping("/skus/{id}/history") @PreAuthorize("hasAuthority('costs.read')") java.util.List<AuditHistoryLookup.HistoryEvent> history(@PathVariable String id){return audit.find("PRODUCT_COST",id);}
    @GetMapping("/versions/{id}") @PreAuthorize("hasAuthority('costs.read')") Version version(@PathVariable String id){return service.version(id);}
    @PostMapping("/versions/{id}/publish") @PreAuthorize("hasAuthority('costs.publish') and hasAuthority('products.write') and hasAuthority('materials.write')") Version publish(@PathVariable String id,@Valid @RequestBody Publish input,@AuthenticationPrincipal AuthenticatedActor actor){return service.publish(id,input,actor);}
    @DeleteMapping("/versions/{id}") @PreAuthorize("hasAuthority('costs.write')") void delete(@PathVariable String id,@RequestParam @Min(0) long revision,@AuthenticationPrincipal AuthenticatedActor actor){service.delete(id,revision,actor);}
    @GetMapping("/versions/{id}/export") @PreAuthorize("hasAuthority('costs.read') and hasAuthority('costs.export')")
    ResponseEntity<byte[]> export(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){
        var v=service.version(id);var s=v.snapshot();var out=new StringBuilder("\uFEFF");
        row(out,"产品",s.name(),"SKU",s.sku(),"版本",String.valueOf(v.versionNumber()),"状态",v.status());
        row(out,"币种","人民币","物料税口径","不含税","单位",s.input().unit(),"核算基数",s.input().baseQuantity().toPlainString());
        row(out,"类型","名称","用量/次数","单位/方式","换算系数","分摊成品数","损耗率%","单价","单位成品金额","备注");
        for(var m:s.materials())row(out,"物料",m.name(),m.input().quantity().toPlainString(),m.input().usageUnit(),m.input().conversion().toPlainString(),m.input().perProducts().toPlainString(),m.input().lossPercent().toPlainString(),m.price()==null?"缺价格":m.price(),m.amount(),m.input().remark());
        for(var l:s.labor())row(out,"人工",l.name(),l.input().times().toPlainString(),l.input().basis(),"",l.input().perProducts().toPlainString(),"",l.input().price().toPlainString(),l.amount(),l.input().remark());
        row(out,"物料合计",s.materialTotal(),"人工合计",s.laborTotal(),"单位标准成本",s.total());row(out,"校验提示",String.join("；",s.errors()));
        service.record(actor,"COST_EXPORT",v.variantId(),null,null,"导出成本版本 "+v.versionNumber());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=product-cost-"+v.versionNumber()+".csv").body(out.toString().getBytes(StandardCharsets.UTF_8));
    }
    static void row(StringBuilder out,String... values){for(int i=0;i<values.length;i++){if(i>0)out.append(',');String v=values[i]==null?"":values[i];String trimmed=v.stripLeading();if(!trimmed.isEmpty()&&"=+@-".indexOf(trimmed.charAt(0))>=0)v="'"+v;out.append('"').append(v.replace("\"","\"\"")).append('"');}out.append("\r\n");}
}
