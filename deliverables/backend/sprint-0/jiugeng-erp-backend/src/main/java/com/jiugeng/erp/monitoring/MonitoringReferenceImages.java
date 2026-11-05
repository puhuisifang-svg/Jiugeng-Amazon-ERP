package com.jiugeng.erp.monitoring;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
/** Public read-only boundary for already collected product reference images. */
@Service
public class MonitoringReferenceImages {
 private final JdbcClient jdbc;private final ObjectMapper json;
 public MonitoringReferenceImages(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public Map<String,String> images(Set<String> asins){
  if(asins.isEmpty())return Map.of();Map<String,String> result=new HashMap<>();
  jdbc.sql("SELECT asin,baseline_json FROM monitor_target WHERE marketplace='US' AND removed=FALSE AND asin IN (:asins) AND baseline_json IS NOT NULL").param("asins",asins).query((r,n)->{try{String url=json.readTree(r.getString(2)).path("imageUrl").asText("");if(url.startsWith("https://"))result.put(r.getString(1),url);}catch(java.io.IOException ignored){}return true;}).list();return result;
 }
}
