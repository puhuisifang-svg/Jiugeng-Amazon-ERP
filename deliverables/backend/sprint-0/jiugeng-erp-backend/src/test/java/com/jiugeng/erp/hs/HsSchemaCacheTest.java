package com.jiugeng.erp.hs;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.simple.JdbcClient;
@SpringBootTest @ActiveProfiles("test")
class HsSchemaCacheTest {
 @Autowired JdbcClient jdbc;@Autowired ObjectMapper json;
 @AfterEach void clean(){jdbc.sql("DELETE FROM hs_declaration_schema").update();}
 @Test void cachesSuccessAndRetainsExpiredSnapshotWhenSourceFails()throws Exception {
  class Source extends HsSchemaService {
   int requests=0;boolean fail=false;
   Source(){super(jdbc,json);}
   @Override Document fetch(String code)throws IOException {requests++;if(fail)throw new IOException("offline");return Jsoup.parse(new HsSchemaServiceTest().page(code,"千克/个","品牌类型","成分含量","其他"));}
  }
  var service=new Source();var first=service.lookup("6307909000");assertThat(service.lookup("6307909000")).isEqualTo(first);assertThat(service.requests).isEqualTo(1);
  var expired=new HsSchemaService.Schema(first.code(),first.productName(),first.measurementUnit(),first.fields(),first.sourceUrl(),first.sourceUpdatedAt(),Instant.now().minusSeconds(8*86400).toString(),false);
  jdbc.sql("UPDATE hs_declaration_schema SET payload=:value WHERE code='6307909000'").param("value",json.writeValueAsString(expired)).update();service.fail=true;
  assertThat(service.lookup("6307909000").stale()).isTrue();assertThat(service.requests).isEqualTo(2);
  assertThatThrownBy(()->service.lookup("5705002000")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("未能获取");
  assertThatThrownBy(()->service.lookup("../../private")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(service.requests).isEqualTo(3);
 }
}
