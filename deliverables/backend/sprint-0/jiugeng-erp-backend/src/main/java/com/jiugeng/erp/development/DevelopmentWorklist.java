package com.jiugeng.erp.development;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only public projection for the home worklist; development remains the source of truth. */
@Service
public class DevelopmentWorklist {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public DevelopmentWorklist(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Item(String id,String name,String number,String owner,String state,Integer stage,LocalDate endDate,String remark){}
    public record Result(List<Item> items,long total){}
    public Result pending(){return pending("",null,null,0,10);}
    public Result pending(String keyword,LocalDate start,LocalDate end,int page,int size){
        String from=" FROM product_development d LEFT JOIN launch_workspace w ON w.kind='DEVELOPMENT' AND w.entity_id=d.id WHERE COALESCE(w.state,d.status) IN ('DRAFT','ACTIVE','IN_PROGRESS','FINAL_REVIEW') AND (EXISTS (SELECT 1 FROM selection_candidate c WHERE c.id=d.candidate_id AND c.status='APPROVED') OR EXISTS (SELECT 1 FROM launch_revision r WHERE r.kind='DEVELOPMENT' AND r.entity_id=d.id AND r.action='APPROVE'))";
        // Fetch the small identity projection; sort by the actual plan, including legacy projects.
        var items=jdbc.sql("SELECT d.id,d.name,d.number,d.developer,d.status,w.state,w.stage,w.document_json,d.fields_json"+from).query((r,n)->{
            try{
                JsonNode doc=json.readTree(r.getString("document_json")==null?r.getString("fields_json"):r.getString("document_json"));
                String plannedEnd=doc.path("endDate").asText("");String state=r.getString("state");
                return new Item(r.getString("id"),r.getString("name"),r.getString("number"),r.getString("developer"),state==null?(r.getString("status").equals("IN_PROGRESS")?"ACTIVE":r.getString("status")):state,state==null?null:r.getInt("stage"),plannedEnd.isBlank()?null:LocalDate.parse(plannedEnd),doc.path("reason").asText(doc.path("remark").asText("")));
            }catch(java.io.IOException e){throw new IllegalStateException("Invalid development document",e);}
        }).list();
        String q=keyword==null?"":keyword.toLowerCase(java.util.Locale.ROOT);
        var filtered=items.stream().filter(i->(i.name()+" "+i.number()+" "+i.owner()).toLowerCase(java.util.Locale.ROOT).contains(q))
            .filter(i->start==null||(i.endDate()!=null&&!i.endDate().isBefore(start)))
            .filter(i->end==null||(i.endDate()!=null&&!i.endDate().isAfter(end))).sorted(java.util.Comparator.comparing(Item::endDate,java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(Item::id)).toList();
        return new Result(filtered.stream().skip((long)page*size).limit(size).toList(),filtered.size());
    }
}
