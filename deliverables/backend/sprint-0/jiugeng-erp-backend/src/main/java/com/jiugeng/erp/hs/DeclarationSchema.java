package com.jiugeng.erp.hs;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Public snapshot contract shared by SKU master data and immutable shipment copies. */
public final class DeclarationSchema {
 private DeclarationSchema() {}
 public record Field(String key,String label) {}
 public static final List<Field> LEGACY=List.of(new Field("brandType","品牌类型"),new Field("exportBenefit","出口享惠情况"),new Field("floorCovering","是否为铺地制品"),new Field("fiberComposition","纤维成分"),new Field("dimensions","规格尺寸"),new Field("weavingMethod","织造方法"),new Field("fixingMethod","固定方式"),new Field("gtin","GTIN"),new Field("cas","CAS"),new Field("other","其他"));
 private static final Map<String,String> KEYS=Map.ofEntries(Map.entry("品牌类型","brandType"),Map.entry("出口享惠情况","exportBenefit"),Map.entry("是否为铺地制品","floorCovering"),Map.entry("纤维成分","fiberComposition"),Map.entry("规格尺寸","dimensions"),Map.entry("织造方法","weavingMethod"),Map.entry("固定方式","fixingMethod"),Map.entry("GTIN","gtin"),Map.entry("CAS","cas"),Map.entry("其他","other"),Map.entry("成分含量","compositionContent"),Map.entry("品牌","brand"),Map.entry("货号","articleNumber"));
 public static String key(String label){String base=label.split("[（(]",2)[0].trim();if(KEYS.containsKey(base))return KEYS.get(base);try{return "hs_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(label.getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(Exception e){throw new IllegalStateException(e);}}
 public static List<Field> validate(List<Field> fields){
  if(fields==null||fields.isEmpty()||fields.size()>40)throw invalid();
  var keys=new HashSet<String>();
  for(var f:fields)if(f==null||f.label()==null||f.label().isBlank()||f.label().length()>200||f.label().chars().anyMatch(Character::isISOControl)||!key(f.label()).equals(f.key())||!keys.add(f.key()))throw invalid();
  return fields;
 }
 public static List<Field> parse(String value){try{return validate(Arrays.asList(new ObjectMapper().readValue(value,Field[].class)));}catch(Exception e){throw invalid();}}
 public static String serialize(List<Field> fields,Map<String,String> values){return fields.stream().map(f->f.label()+"："+Objects.toString(values.get(f.key()),"").trim()).collect(java.util.stream.Collectors.joining("\n"));}
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"申报要素字段结构无效，请重新查询HS编码");}
}
