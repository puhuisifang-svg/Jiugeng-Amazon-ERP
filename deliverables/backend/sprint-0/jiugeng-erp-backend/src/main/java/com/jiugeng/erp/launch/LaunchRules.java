package com.jiugeng.erp.launch;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Common, deterministic business gates; never treats unknown amounts as zero. */
public final class LaunchRules {
    private LaunchRules() {}
    public static final List<String> RISK_TYPES=List.of("TRADEMARK","COPYRIGHT","DESIGN","PATENT","COMPLIANCE");
    public static final Set<String> RISK_CLEAR=Set.of("CLEAR","LICENSED","NA");
    private static String label(String key){return switch(key){
        case "TRADEMARK"->"商标与品牌";case "COPYRIGHT"->"版权与素材";case "DESIGN"->"外观专利/设计权";case "PATENT"->"功能结构专利";case "COMPLIANCE"->"类目与商品要求";
        case "owner"->"负责人";case "reason"->"理由/判断依据";case "customer"->"目标客户";case "scenario"->"使用场景";case "conclusion"->"建议结论";case "market"->"目标市场";case "scope"->"调查范围";case "method"->"检索方法";case "evidence"->"证据";case "investigator"->"调查人";case "date"->"调查日期";case "pain"->"客户痛点";case "validation"->"验证计划";case "proposal"->"修改建议";case "standard"->"验收标准";case "priority"->"优先级";case "needId"->"关联需求";default->key;};}
    public static String text(JsonNode n,String field){return n.path(field).asText("").trim();}
    public static void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public static void validate(JsonNode d) {
        require(d!=null&&d.isObject(),"资料必须是对象");
        require(d.toString().length()<=300000,"单份资料过大，请将图片/文件保存为附件链接");
        for(String name:List.of("specs","needs","requirements","risks","quotes","samples","evidence","criteria","costs","skus","handover")) {
            JsonNode rows=d.path(name);require(rows.isMissingNode()||rows.isArray(),"条目格式不正确："+name);
            require(rows.size()<=200,"单组条目最多200条");
            Set<String> ids=new HashSet<>();
            for(JsonNode row:rows){require(row.isObject(),"条目格式不正确");String id=text(row,"id");require(!id.isBlank()&&id.length()<=100&&ids.add(id),"条目编号不能为空或重复");}
        }
        for(String name:List.of("price","monthlySales","investment","budget"))if(d.hasNonNull(name)&&!text(d,name).isBlank())number(d.get(name),name,false);
        String start=text(d,"startDate"),end=text(d,"endDate");
        if(!start.isBlank()&&!end.isBlank())require(!date(end).isBefore(date(start)),"计划完成日不能早于开始日");
        walk(d);
    }
    private static void walk(JsonNode n){
        if(n.isTextual())require(n.asText().length()<=12000,"单项文本过长");
        if(n.isContainerNode()) n.forEach(LaunchRules::walk);
    }
    public static LocalDate date(String value){try{return LocalDate.parse(value);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"日期格式无效");}}
    public static BigDecimal number(JsonNode value,String name,boolean positive){
        try {BigDecimal v=new BigDecimal(value.asText());require(v.scale()<=6&&v.compareTo(new BigDecimal("999999999"))<=0&&(positive?v.signum()>0:v.signum()>=0),name+"数值无效");return v;}
        catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,name+"待补充");}
    }
    public static Map<String,BigDecimal> cost(JsonNode d){
        BigDecimal p=number(d.path("price"),"目标售价",true), c=BigDecimal.ZERO;
        require(d.path("costs").size()>0,"请填写成本明细");
        Set<String> categories=new HashSet<>();
        for(JsonNode row:d.path("costs")){
            String category=text(row,"category");require(categories.add(category),"同类费用请合并，避免重复计算");
            require(!text(row,"basis").isBlank(),"所有费用包括零费用均需依据");
            require(Set.of("USD","CNY").contains(text(row,"currency")),"费用币种支持USD/CNY");
            BigDecimal rate=number(row.path("rate"),"汇率",true);
            if(text(row,"currency").equals("USD"))require(rate.compareTo(BigDecimal.ONE)==0,"USD汇率须为1");
            c=c.add(number(row.path("amount"),"费用",false).multiply(rate));
        }
        require(categories.containsAll(List.of("采购","包装","头程","履约","其他")),"采购、包装、头程、履约、其他费用均须明确");
        require(!text(d,"rateDate").isBlank()&&!date(text(d,"rateDate")).isAfter(LocalDate.now()),"汇率日期必须有效且不晚于今天");
        require(!text(d,"costBasis").isBlank(),"费率与试算依据必填");
        BigDecimal r=BigDecimal.ZERO;
        for(String name:List.of("commission","advertising","loss")){BigDecimal v=number(d.path(name),name,false);require(v.compareTo(BigDecimal.ONE)<=0,"费率应为0到1的小数");r=r.add(v);}
        BigDecimal net=BigDecimal.ONE.subtract(r),profit=p.multiply(net).subtract(c);
        Map<String,BigDecimal> out=new LinkedHashMap<>();out.put("unitCost",c);out.put("profit",profit);out.put("margin",profit.divide(p,10,RoundingMode.HALF_UP));
        out.put("breakEven",net.signum()>0?c.divide(net,10,RoundingMode.HALF_UP):null);
        out.put("lowPriceProfit",p.multiply(new BigDecimal(".9")).multiply(net).subtract(c));out.put("highCostProfit",p.multiply(net).subtract(c.multiply(new BigDecimal("1.1"))));
        out.put("maxAdvertising",BigDecimal.ONE.subtract(number(d.path("commission"),"佣金",false)).subtract(number(d.path("loss"),"损耗",false)).subtract(c.divide(p,10,RoundingMode.HALF_UP)));
        out.put("monthlyProfit",text(d,"monthlySales").isBlank()?null:profit.multiply(number(d.path("monthlySales"),"目标月销",false)));
        out.put("recoveryUnits",text(d,"investment").isBlank()||profit.signum()<=0?null:number(d.path("investment"),"一次性投入",false).divide(profit,0,RoundingMode.CEILING));return out;
    }
    public static List<String> riskIssues(JsonNode d){
        List<String> issues=new ArrayList<>();
        for(String type:RISK_TYPES){
            List<JsonNode> rows=new ArrayList<>();d.path("risks").forEach(r->{if(text(r,"type").equals(type))rows.add(r);});
            if(rows.isEmpty()){issues.add("缺少风险调查："+label(type));continue;}
            for(JsonNode r:rows){
                if(!RISK_CLEAR.contains(text(r,"conclusion")))issues.add("风险疑点未闭环："+label(type));
                for(String f:List.of("market","scope","method","evidence","reason","investigator","date"))if(text(r,f).isBlank())issues.add(label(type)+"调查缺少"+label(f));
                if(!text(r,"market").equals(text(d,"market")))issues.add("调查市场与目标市场不一致："+label(type));
                if(text(r,"conclusion").equals("LICENSED")&&(text(r,"licenseScope").isBlank()||text(r,"expires").isBlank()))issues.add("授权须有范围和期限");
                if(!text(r,"expires").isBlank()&&date(text(r,"expires")).isBefore(LocalDate.now()))issues.add("授权已过期");
                if(!text(r,"date").isBlank()&&date(text(r,"date")).isAfter(LocalDate.now()))issues.add("调查日期不能在未来");
            }
        }return issues;
    }
    public static List<String> evaluationIssues(JsonNode d,boolean candidate){
        List<String> out=new ArrayList<>();
        for(String f:List.of("owner","reason","customer","scenario","conclusion","market"))if(text(d,f).isBlank())out.add("待补充："+label(f));
        if(d.path("needs").isEmpty())out.add("至少填写一条需求证据或明确假设");
        for(JsonNode n:d.path("needs"))for(String f:List.of("pain","evidence","validation"))if(text(n,f).isBlank())out.add("需求缺少："+label(f));
        if(d.path("specs").isEmpty())out.add("请填写拟开发规格");
        for(JsonNode spec:d.path("specs"))if(text(spec,"name").isBlank()||text(spec,"target").isBlank())out.add("目标规格未完善");
        if(d.path("requirements").isEmpty())out.add("请填写改进或无需改动的开发方向及验证方式");
        for(JsonNode r:d.path("requirements"))for(String f:List.of("proposal","standard","priority","needId"))if(text(r,f).isBlank())out.add("开发要求缺少："+label(f));
        if(text(d,"salesValue").isBlank()&&text(d,"salesMissingReason").isBlank())out.add("无销售数据请填写原因与替代验证依据");
        try{cost(d);}catch(ResponseStatusException e){out.add(e.getReason());}
        if(candidate){int[] weights={25,25,20,20,10};if(d.path("criteria").size()!=weights.length)out.add("请完成五项评分");
            for(JsonNode c:d.path("criteria")){if(!c.path("score").canConvertToInt()||c.path("score").asDouble()!=c.path("score").asInt()||c.path("score").asInt()<0||c.path("score").asInt()>5||text(c,"reason").isBlank())out.add("评分必须为0至5且有依据");}}
        out.addAll(riskIssues(d));return out;
    }
}
