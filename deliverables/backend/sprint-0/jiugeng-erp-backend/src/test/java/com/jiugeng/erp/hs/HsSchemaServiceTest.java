package com.jiugeng.erp.hs;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
class HsSchemaServiceTest {
 String page(String code,String unit,String... labels){StringBuilder s=new StringBuilder("<table><tr><td>商品编码</td><td>"+code+"</td></tr><tr><td>编码状态</td><td>正常</td></tr><tr><td>计量单位</td><td>"+unit+"</td></tr></table><h3>申报要素</h3><div><table>");for(int i=0;i<labels.length;i++)s.append("<tr><td>"+i+"</td><td>"+labels[i]+"</td></tr>");return s+"</table></div>";}
 @Test void parsesBothExamplesAndKeepsTheirDistinctUnitsAndOrder(){
  var textile=HsSchemaService.parse("6307909000",Jsoup.parse(page("6307909000","千克/个","品牌类型 [<a>?</a>]","出口享惠情况","成分含量","品牌(中文或外文名称)","货号","GTIN","CAS","其他")));
  assertThat(textile.measurementUnit()).isEqualTo("千克/个");assertThat(textile.fields()).extracting(DeclarationSchema.Field::key).containsExactly("brandType","exportBenefit","compositionContent","brand","articleNumber","gtin","cas","other");
  var carpet=HsSchemaService.parse("5705002000",Jsoup.parse(page("5705002000","千克/平方米","品牌类型","出口享惠情况","是否为铺地制品","纤维成分","规格尺寸","织造方法(无纺、粘绒、植绒、针织等)","固定方式(施胶、涂层粘合等)","GTIN","CAS","其他")));
  assertThat(carpet.measurementUnit()).isEqualTo("千克/平方米");assertThat(carpet.fields()).extracting(DeclarationSchema.Field::key).containsExactly("brandType","exportBenefit","floorCovering","fiberComposition","dimensions","weavingMethod","fixingMethod","gtin","cas","other");
 }
 @Test void rejectsMissingFieldsWrongCodeAndUnsafeSnapshotKeys(){
  assertThatThrownBy(()->HsSchemaService.parse("5705002000",Jsoup.parse(page("6307909000","千克/个","其他")))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->HsSchemaService.parse("5705002000",Jsoup.parse("<html>not found</html>"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->DeclarationSchema.parse("[{\"key\":\"quantity\",\"label\":\"纤维成分\"}]")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(DeclarationSchema.key("新字段说明")).startsWith("hs_");
 }
}
