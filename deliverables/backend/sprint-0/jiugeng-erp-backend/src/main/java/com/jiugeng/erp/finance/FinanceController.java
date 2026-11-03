package com.jiugeng.erp.finance;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("!mock") @RequestMapping("/api/v1/finance") @Transactional
class FinanceController {
    private final JdbcClient jdbc; private final AuditTrail audit;
    FinanceController(JdbcClient jdbc,AuditTrail audit){this.jdbc=jdbc;this.audit=audit;}
    record Shop(String id,String name,String remark,boolean active,long version){}
    record ShopInput(String name,String remark,boolean active,Long version,List<String> accounts){}
    record Input(String shopId,LocalDate date,String direction,String category,String detail,String account,BigDecimal amount,String remark,Long version){}
    record Bill(String id,String shopId,String shopName,LocalDate date,String direction,String category,String detail,String account,BigDecimal amount,String remark,boolean voided,long version,long attachments){}
    record Total(String label,BigDecimal income,BigDecimal expense,BigDecimal balance,long count){}
    record Page(List<Bill> items,long totalCount,List<Total> totals,List<Total> accounts,List<Total> months){}
    private final org.springframework.jdbc.core.RowMapper<Shop> shopMapper=(r,n)->new Shop(r.getString("id"),r.getString("name"),r.getString("remark"),r.getBoolean("active"),r.getLong("version"));
    private final org.springframework.jdbc.core.RowMapper<Bill> billMapper=(r,n)->new Bill(r.getString("id"),r.getString("shop_id"),r.getString("shop_name"),r.getDate("bill_date").toLocalDate(),r.getString("direction"),r.getString("category"),r.getString("detail"),r.getString("account_name"),r.getBigDecimal("amount"),r.getString("remark"),r.getBoolean("voided"),r.getLong("version"),r.getLong("attachments"));
    private static final String SELECT="SELECT b.*,s.name shop_name,(SELECT COUNT(*) FROM finance_attachment a WHERE a.bill_id=b.id) attachments FROM finance_bill b JOIN finance_shop s ON s.id=b.shop_id";
    @GetMapping("/shops") @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
    List<Shop> shops(){return jdbc.sql("SELECT * FROM finance_shop ORDER BY active DESC,name,id").query(shopMapper).list();}
    @PostMapping("/shops") @PreAuthorize("hasAuthority('finance.write')")
    Shop createShop(@RequestBody ShopInput in,@AuthenticationPrincipal AuthenticatedActor actor){
        String name=text(in.name(),128,true),remark=text(in.remark(),500,false),id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO finance_shop(id,name,name_key,remark,active) VALUES(:id,:n,:k,:r,:a)").param("id",id).param("n",name).param("k",key(name)).param("r",remark).param("a",in.active()).update();
        List<String> accounts=in.accounts()==null?List.of():in.accounts();
        require(accounts.size()<=100,"一次最多添加100个账户");
        Set<String> names=new HashSet<>();
        for(String value:accounts){String accountName=text(value,100,true);require(names.add(key(accountName)),"收支账户名称重复，请检查后重试");createAccount(id,new AccountInput(accountName,true,null),actor);}
        Shop result=shop(id);record(actor,"FINANCE_SHOP_CREATE",id,null,result);return result;
    }
    @PutMapping("/shops/{id}") @PreAuthorize("hasAuthority('finance.write')")
    Shop updateShop(@PathVariable String id,@RequestBody ShopInput in,@AuthenticationPrincipal AuthenticatedActor actor){
        Shop before=shop(id);String name=text(in.name(),128,true),remark=text(in.remark(),500,false);version(in.version());
        changed(jdbc.sql("UPDATE finance_shop SET name=:n,name_key=:k,remark=:r,active=:a,version=version+1 WHERE id=:id AND version=:v").param("n",name).param("k",key(name)).param("r",remark).param("a",in.active()).param("id",id).param("v",in.version()).update());
        Shop after=shop(id);record(actor,"FINANCE_SHOP_UPDATE",id,before,after);return after;
    }
    record Account(String id,String shopId,String name,boolean active,long version){}
    record AccountInput(String name,boolean active,Long version){}
    private final org.springframework.jdbc.core.RowMapper<Account> accountMapper=(r,n)->new Account(r.getString("id"),r.getString("shop_id"),r.getString("name"),r.getBoolean("active"),r.getLong("version"));
    @GetMapping("/shops/{shopId}/accounts") @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
    List<Account> accounts(@PathVariable String shopId){shop(shopId);return jdbc.sql("SELECT * FROM finance_account WHERE shop_id=:shop ORDER BY active DESC,name,id").param("shop",shopId).query(accountMapper).list();}
    private Account account(String shopId,String id){return jdbc.sql("SELECT * FROM finance_account WHERE shop_id=:shop AND id=:id").param("shop",shopId).param("id",id).query(accountMapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"收支账户不存在"));}
    @PostMapping("/shops/{shopId}/accounts") @PreAuthorize("hasAuthority('finance.write')")
    Account createAccount(@PathVariable String shopId,@RequestBody AccountInput in,@AuthenticationPrincipal AuthenticatedActor actor){
        shop(shopId);String name=text(in.name(),100,true),id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO finance_account(id,shop_id,name,name_key,active) VALUES(:id,:shop,:name,:key,:active)").param("id",id).param("shop",shopId).param("name",name).param("key",key(name)).param("active",in.active()).update();
        Account after=account(shopId,id);record(actor,"FINANCE_ACCOUNT_CREATE",id,null,after);return after;
    }
    @PutMapping("/shops/{shopId}/accounts/{id}") @PreAuthorize("hasAuthority('finance.write')")
    Account updateAccount(@PathVariable String shopId,@PathVariable String id,@RequestBody AccountInput in,@AuthenticationPrincipal AuthenticatedActor actor){
        Account before=account(shopId,id);String name=text(in.name(),100,true);version(in.version());
        changed(jdbc.sql("UPDATE finance_account SET name=:name,name_key=:key,active=:active,version=version+1 WHERE id=:id AND shop_id=:shop AND version=:version").param("name",name).param("key",key(name)).param("active",in.active()).param("id",id).param("shop",shopId).param("version",in.version()).update());
        Account after=account(shopId,id);record(actor,"FINANCE_ACCOUNT_UPDATE",id,before,after);return after;
    }
    @GetMapping("/bills") @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
    Page list(@RequestParam String shopId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="")String direction,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="false")boolean voided,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        shop(shopId);require(from!=null&&to!=null&&!from.isAfter(to),"日期范围无效");require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页参数无效");require(Set.of("","INCOME","EXPENSE").contains(direction),"收支类型无效");text(q,200,false);
        var params=new HashMap<String,Object>();params.put("shop",shopId);params.put("from",from);params.put("to",to);params.put("direction",direction);params.put("voided",voided);params.put("q","%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        String where=" WHERE b.shop_id=:shop AND b.bill_date BETWEEN :from AND :to AND b.voided=:voided AND (:direction='' OR b.direction=:direction) AND LOWER(CONCAT(b.detail,' ',b.category,' ',b.account_name,' ',b.remark)) LIKE :q ESCAPE '!'";
        long count=jdbc.sql("SELECT COUNT(*) FROM finance_bill b"+where).params(params).query(Long.class).single();
        var rows=jdbc.sql(SELECT+where+" ORDER BY b.bill_date DESC,b.created_at DESC,b.id LIMIT :size OFFSET :offset").params(params).param("size",size).param("offset",(long)page*size).query(billMapper).list();
        return new Page(rows,count,totals("'合计'",where,params,false),totals("b.account_name",where,params,true),totals("CONCAT(EXTRACT(YEAR FROM b.bill_date),'-',LPAD(EXTRACT(MONTH FROM b.bill_date),2,'0'))",where,params,true));
    }
    @GetMapping("/export") @PreAuthorize("hasAuthority('finance.export')") @Transactional(readOnly=true)
    ResponseEntity<byte[]> export(@RequestParam String shopId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="")String direction,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="false")boolean voided){
        Page first=list(shopId,from,to,direction,q,voided,0,100);require(first.totalCount()<=10000,"单次导出最多10000条，请缩小日期范围");
        StringBuilder csv=new StringBuilder("\uFEFF店铺,日期,类型,明细,收支账户,金额(CNY),备注,状态\r\n");
        for(int p=0;p<Math.max(1,(first.totalCount()+99)/100);p++){
            var rows=p==0?first.items():list(shopId,from,to,direction,q,voided,p,100).items();
            for(Bill b:rows)csv.append(String.join(",",List.of(b.shopName(),b.date().toString(),b.direction().equals("INCOME")?"收入":"支出",b.detail(),b.account(),b.amount().toPlainString(),b.remark(),b.voided()?"已作废":"有效").stream().map(this::csvCell).toList())).append("\r\n");
        }
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=bills.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String csvCell(String value){String s=value;if(s.stripLeading().matches("(?s)^[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    private List<Total> totals(String label,String where,Map<String,Object> params,boolean grouped){
        return jdbc.sql("SELECT "+label+" label,COALESCE(SUM(CASE WHEN direction='INCOME' THEN amount ELSE 0 END),0) income,COALESCE(SUM(CASE WHEN direction='EXPENSE' THEN amount ELSE 0 END),0) expense,COUNT(*) cnt FROM finance_bill b"+where+(grouped?" GROUP BY "+label+" ORDER BY "+label:"")).params(params).query((r,n)->new Total(r.getString("label"),r.getBigDecimal("income"),r.getBigDecimal("expense"),r.getBigDecimal("income").subtract(r.getBigDecimal("expense")),r.getLong("cnt"))).list();
    }
    @PostMapping("/bills") @PreAuthorize("hasAuthority('finance.write')")
    Bill create(@RequestBody Input input,@AuthenticationPrincipal AuthenticatedActor actor){
        Input in=valid(input,null);String id=UUID.randomUUID().toString();
        bind(jdbc.sql("INSERT INTO finance_bill(id,shop_id,bill_date,direction,category,detail,account_name,amount,remark) VALUES(:id,:shop,:date,:direction,:category,:detail,:account,:amount,:remark)"),id,in).update();
        Bill after=bill(id);record(actor,"FINANCE_BILL_CREATE",id,null,after);return after;
    }
    @PutMapping("/bills/{id}") @PreAuthorize("hasAuthority('finance.write')")
    Bill update(@PathVariable String id,@RequestBody Input input,@AuthenticationPrincipal AuthenticatedActor actor){
        Bill before=bill(id);require(!before.voided(),"已作废账单不可修改");require(Objects.equals(before.shopId(),input.shopId()),"不能将已保存账单转移到其他店铺");Input in=valid(input,before);version(in.version());
        changed(bind(jdbc.sql("UPDATE finance_bill SET bill_date=:date,direction=:direction,category=:category,detail=:detail,account_name=:account,amount=:amount,remark=:remark,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND shop_id=:shop AND version=:version AND voided=FALSE"),id,in).param("version",in.version()).update());
        Bill after=bill(id);record(actor,"FINANCE_BILL_UPDATE",id,before,after);return after;
    }
    @PostMapping("/bills/{id}/void") @PreAuthorize("hasAuthority('finance.void')")
    Bill voidBill(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){
        Bill before=bill(id);require(!before.voided(),"账单已作废");changed(jdbc.sql("UPDATE finance_bill SET voided=TRUE,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v AND voided=FALSE").param("id",id).param("v",version).update());Bill after=bill(id);record(actor,"FINANCE_BILL_VOID",id,before,after);return after;
    }
    @GetMapping("/bills/{id}/history") @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
    List<Map<String,Object>> history(@PathVariable String id){bill(id);return jdbc.sql("SELECT action,actor_display_name,occurred_at,before_value,after_value FROM audit_event WHERE resource_type='FINANCE' AND resource_id=:id ORDER BY occurred_at DESC,id DESC").param("id",id).query().listOfRows();}
    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec sql,String id,Input i){return sql.param("id",id).param("shop",i.shopId()).param("date",i.date()).param("direction",i.direction()).param("category",i.category()).param("detail",i.detail()).param("account",i.account()).param("amount",i.amount()).param("remark",i.remark());}
    Bill bill(String id){return jdbc.sql(SELECT+" WHERE b.id=:id").param("id",id).query(billMapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"账单不存在"));}
    private Shop shop(String id){return jdbc.sql("SELECT * FROM finance_shop WHERE id=:id").param("id",id).query(shopMapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"店铺不存在"));}
    private Input valid(Input i,Bill before){
        String accountName=text(i.account(),100,true);
        if(before==null || !before.account().equals(accountName)) require(jdbc.sql("SELECT COUNT(*) FROM finance_account WHERE shop_id=:shop AND name=:name AND active=TRUE").param("shop",i.shopId()).param("name",accountName).query(Long.class).single()>0,"请在店铺管理中维护并选择已启用的收支账户");
require(shop(i.shopId()).active(),"该店铺已停用，请先在店铺管理中启用");require(i.date()!=null&&i.date().getYear()>=2000&&i.date().getYear()<=2100,"日期须在2000至2100年之间");require(i.direction()!=null&&Set.of("INCOME","EXPENSE").contains(i.direction()),"收支类型无效");require(i.amount()!=null&&i.amount().signum()>0&&i.amount().scale()<=2&&i.amount().compareTo(new BigDecimal("999999999999.99"))<=0,"金额须大于0，最多两位小数且不超过12位整数");return new Input(i.shopId(),i.date(),i.direction(),text(i.category(),64,false),text(i.detail(),500,true),text(i.account(),100,true),i.amount(),text(i.remark(),1000,false),i.version());}
    static String text(String v,int max,boolean required){String s=v==null?"":v.strip();require((!required||!s.isEmpty())&&s.length()<=max&&!s.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'),"字段必填或长度不正确（最多"+max+"字）");return s;}
    private String key(String v){return java.text.Normalizer.normalize(v,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private void version(Long v){require(v!=null&&v>=0,"缺少有效版本号");}
    static void require(boolean ok,String msg){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    static void changed(int count){if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请刷新后重试");}
    void record(AuthenticatedActor a,String action,String id,Object before,Object after){audit.record(new AuditTrail.BusinessOperation(a.userId(),a.loginName(),a.displayName(),action,"FINANCE",id,true,snapshot(before),snapshot(after),"店铺运营记账"));}
    private Map<String,Object> snapshot(Object o){if(o==null)return Map.of();return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().convertValue(o,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"操作失败")));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","名称已存在，请勿重复添加"));}
}
