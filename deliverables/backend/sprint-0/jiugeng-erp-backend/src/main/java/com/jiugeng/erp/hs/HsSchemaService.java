package com.jiugeng.erp.hs;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HsSchemaService {
 public record Schema(String code,String productName,String measurementUnit,List<DeclarationSchema.Field> fields,String sourceUrl,String sourceUpdatedAt,String fetchedAt,boolean stale){}
 private final JdbcClient jdbc;private final ObjectMapper json;
 public HsSchemaService(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 // A single fixed host, no user-provided URL or redirects; bounded response and timeout.
 public synchronized Schema lookup(String code){
  if(code==null||!code.matches("[0-9]{10}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"HS编码须为10位数字");
  Schema cached=jdbc.sql("SELECT payload FROM hs_declaration_schema WHERE code=:code").param("code",code).query((r,n)->decode(r.getString(1))).optional().orElse(null);
  if(cached!=null&&Instant.parse(cached.fetchedAt()).isAfter(Instant.now().minus(Duration.ofDays(7))))return cached;
  try{
   var doc=fetch(code);
   Schema result=parse(code,doc);
   String payload=json.writeValueAsString(result);
   if(cached==null)jdbc.sql("INSERT INTO hs_declaration_schema(code,payload) VALUES(:code,:payload)").param("code",code).param("payload",payload).update();
   else jdbc.sql("UPDATE hs_declaration_schema SET payload=:payload WHERE code=:code").param("code",code).param("payload",payload).update();
   return result;
  }catch(Exception e){if(cached!=null)return new Schema(cached.code(),cached.productName(),cached.measurementUnit(),cached.fields(),cached.sourceUrl(),cached.sourceUpdatedAt(),cached.fetchedAt(),true);throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"未能获取该HS编码的申报要素，请核对编码后重试；已填资料未清空");}
 }
 Document fetch(String code)throws java.io.IOException {return Jsoup.connect("https://www.hsbianma.com/Code/"+code+".html").timeout(8000).maxBodySize(1024*1024).followRedirects(false).userAgent("Jiugeng-ERP/1.0 HS-reference").get();}
 private Schema decode(String s){try{return json.readValue(s,Schema.class);}catch(Exception e){throw new IllegalStateException(e);}}
 static Schema parse(String code,Document doc){
  Map<String,String> info=new HashMap<>();for(var tr:doc.select("tr")){var td=tr.select("td");if(td.size()==2)info.put(td.get(0).text().trim(),td.get(1).text().trim());}
  if(!code.equals(info.get("商品编码"))||!"正常".equals(info.get("编码状态"))||info.getOrDefault("计量单位","").isBlank())throw new IllegalArgumentException("Invalid HS page");
  List<DeclarationSchema.Field> fields=new ArrayList<>();
  for(var heading:doc.select("h3")){if(!heading.text().equals("申报要素"))continue;var section=heading.nextElementSibling();if(section==null)break;for(var tr:section.select("tr")){var td=tr.select("td");if(td.size()!=2||!td.get(0).text().matches("[0-9]+"))continue;var cell=td.get(1).clone();cell.select("a").remove();String label=cell.text().replace("[ ]","").replace("[]","").trim();fields.add(new DeclarationSchema.Field(DeclarationSchema.key(label),label));}break;}
  DeclarationSchema.validate(fields);
  return new Schema(code,info.getOrDefault("商品名称",""),info.get("计量单位"),fields,"https://www.hsbianma.com/Code/"+code+".html",info.getOrDefault("更新时间",""),Instant.now().toString(),false);
 }
}
