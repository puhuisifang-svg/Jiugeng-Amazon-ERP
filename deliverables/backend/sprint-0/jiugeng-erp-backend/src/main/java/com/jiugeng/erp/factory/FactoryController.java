package com.jiugeng.erp.factory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("!mock") @RequestMapping("/api/v1/factory") @PreAuthorize("isAuthenticated()")
class FactoryController {
    final FactoryService service;
    FactoryController(FactoryService service){this.service=service;}
    void read(String kind){FactoryService.check(FactoryService.KINDS.contains(kind),"功能不存在");FactoryService.permission("factory."+kind+".read");}
    @GetMapping("/{kind}") FactoryService.Page list(@PathVariable String kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String employeeId,@RequestParam(defaultValue="")String from,@RequestParam(defaultValue="")String to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){read(kind);return service.list(kind,q,status,employeeId,from,to,page,size);}
    @GetMapping("/{kind}/{id}") Map<String,Object> detail(@PathVariable String kind,@PathVariable String id){read(kind);return service.decorate(kind,service.get(kind,id));}
    @GetMapping("/{kind}/{id}/history") Map<String,Object> history(@PathVariable String kind,@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="10")int size){read(kind);return Map.of("items",service.history(kind,id,page,size),"totalCount",service.historyCount(id));}
    @GetMapping("/operations") List<Map<String,Object>> operations(){FactoryService.permission("factory.rates.read");return service.operationList();}
    @PostMapping("/rate-preview") Map<String,Object> preview(@RequestBody Map<String,Object>in){FactoryService.permission("factory.rates.read");return service.preview(in);}
    @PostMapping("/{kind}/actions/{action}") Map<String,Object> command(@PathVariable String kind,@PathVariable String action,@RequestBody Map<String,Object> in,@AuthenticationPrincipal AuthenticatedActor actor){
        String capability=switch(kind){
            case "employees" -> "employees.write";
            case "rates" -> "rates.write";
            case "reports" -> Set.of("review","withdraw","void","reviewBatch").contains(action)?"reports.review":"reports.write";
            case "payroll" -> switch(action){case "lock","lockBatch" -> "payroll.lock";case "correct" -> "payroll.correct";case "pay","voidPayment" -> "payroll.pay";default -> "payroll.write";};
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"功能不存在");
        };
        FactoryService.permission("factory."+capability);return service.mutate(kind,action,in,actor);
    }
    @GetMapping("/payroll-export") ResponseEntity<byte[]> export(@RequestParam String month,@RequestParam(defaultValue="")String employeeId,@RequestParam(defaultValue="summary")String mode){
        FactoryService.check(Set.of("summary","lines").contains(mode),"导出类型无效");
        FactoryService.permission("factory.payroll.export");FactoryService.month(month);var first=service.list("payroll","","",employeeId,month,month,0,100);FactoryService.check(first.totalCount()<=10000,"单次最多导出10000张工资单");
        StringBuilder csv=new StringBuilder(mode.equals("lines")?"\uFEFF月份,工号,姓名,报工日期,加工项目,工序,单位,数量,单价,金额,备注\r\n":"\uFEFF月份,工号,姓名,班组,开户行,银行卡号,计件工资,加项,减项,应发,已发,待发,核算状态,版本\r\n");
        for(int i=0;i<Math.max(1,(first.totalCount()+99)/100);i++){var page=i==0?first:service.list("payroll","","",employeeId,month,month,i,100);for(var d:page.items()){
            if(mode.equals("lines")){for(var report:service.payrollReports(d))for(var line:FactoryService.rows(report,"lines")){var values=new ArrayList<String>();for(String k:List.of("date","number","employeeName"))values.add(FactoryService.str(d,k));values.add(FactoryService.str(report,"date"));for(String k:List.of("project","operation","unit","quantity","price","amount","remark"))values.add(FactoryService.str(line,k));csv.append(String.join(",",values.stream().map(FactoryController::cell).toList())).append("\r\n");}continue;}
            var values=new ArrayList<String>();for(String k:List.of("date","number","employeeName","team","bankName","bankAccount","base","plus","minus","amount","paid","remaining"))values.add(k.equals("bankAccount")&&!FactoryService.str(d,k).isEmpty()?"\t"+FactoryService.str(d,k):FactoryService.str(d,k));values.add(switch(FactoryService.str(d,"status")){case "LOCKED"->"已锁定";case "CORRECTING"->"更正中";default->"草稿（未锁定）";});values.add(FactoryService.str(d,"revision"));csv.append(String.join(",",values.stream().map(FactoryController::cell).toList())).append("\r\n");}}
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=factory-payroll.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    static String cell(String s){if(s.stripLeading().matches("(?s)^[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"请求失败")));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","记录冲突，请刷新后重试"));}
}
