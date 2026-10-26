package com.jiugeng.erp.collection;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest @ActiveProfiles("test")
class CollectionServiceTest {
    @Autowired CollectionService service;@Autowired JdbcTemplate jdbc;@Autowired SyntheticAdapter adapter;@Autowired ObjectMapper json;
    String store; boolean inventoryComplete=true;
    @TestConfiguration static class Config {@Bean SyntheticAdapter syntheticAdapter(){return new SyntheticAdapter();}}
    static class SyntheticAdapter implements CollectionAdapter {
        Observation result;Runnable during=()->{};RuntimeException failure;boolean authorized=true;
        public String provider(){return "synthetic-contract-test";}public Set<String> kinds(){return Set.of("STORE");}public boolean approved(){return authorized;}
        public Observation collect(Target target){during.run();if(failure!=null)throw failure;return result;}
    }
    @BeforeEach void setup(){
        jdbc.update("DELETE FROM collection_notice");jdbc.update("DELETE FROM collection_job");jdbc.update("DELETE FROM collection_schedule");
        inventoryComplete=true;adapter.authorized=true;adapter.failure=null;adapter.during=()->{};
        store=UUID.randomUUID().toString();jdbc.update("INSERT INTO watch_store(id,store_key,store_key_normalized,store_name,source_url,created_by) VALUES(?,?,?,'Synthetic store','https://example.com/store','test')",store,store,store);
    }
    private void observe(String date,boolean complete,String... asins)throws Exception{
        var fields=json.createObjectNode();var products=fields.putArray("products");for(String asin:asins)products.addObject().put("asin",asin).put("title","Synthetic "+asin);
        fields.put("pageCount",1).put("itemCount",asins.length).put("inventoryComplete",inventoryComplete);
        adapter.result=new CollectionAdapter.Observation(Instant.parse(date),complete?"COMPLETE":"PARTIAL",complete,fields);
        jdbc.update("UPDATE collection_schedule SET next_at=TIMESTAMP '2000-01-01 00:00:00'");service.tick();
    }
    @Test void firstBaselineRepeatedAppearanceAndTwoDayAbsence()throws Exception{
        service.configure("STORE",store,adapter.provider(),true,null);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001","B0AUTO0002");
        assertThat(count("watch_new_product_event")).isZero();
        observe("2026-10-21T12:00:00Z",true,"B0AUTO0002","B0AUTO0003");
        assertThat(count("watch_new_product_event")).isEqualTo(1);
        observe("2026-10-21T14:00:00Z",true,"B0AUTO0002","B0AUTO0003");
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isEqualTo(1);
        observe("2026-10-22T12:00:00Z",false,"B0AUTO0002");
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isEqualTo(1);
        observe("2026-10-23T12:00:00Z",true,"B0AUTO0002","B0AUTO0003");
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isEqualTo(2);
        observe("2026-10-24T12:00:00Z",true,"B0AUTO0001","B0AUTO0002","B0AUTO0003");
        assertThat(count("watch_new_product_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isZero();
        observe("2026-10-19T12:00:00Z",true,"B0AUTO0002");
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isZero();
    }
    @Test void noApprovedSourceAndPauseDuringCollectionCannotAdvanceBaseline()throws Exception{
        adapter.authorized=false;
        assertThatThrownBy(()->service.configure("STORE",store,adapter.provider(),true,null)).isInstanceOf(ResponseStatusException.class);
        adapter.authorized=true;service.configure("STORE",store,adapter.provider(),true,null);
        adapter.during=()->jdbc.update("UPDATE watch_store SET status='DISABLED',version=version+1 WHERE id=?",store);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        assertThat(count("watch_auto_baseline")).isZero();assertThat(service.jobs()).extracting(CollectionService.Job::status).containsExactly("CANCELLED");
    }
    @Test void retryAfterAndExpiredLeaseRecoverWithoutParallelJobs()throws Exception{
        service.configure("STORE",store,adapter.provider(),true,null);adapter.failure=new CollectionAdapter.CollectionFailure(true,600);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        assertThat(service.jobs()).extracting(CollectionService.Job::status).containsExactly("RETRY");
        service.tick();assertThat(service.jobs()).hasSize(1);assertThat(service.jobs().get(0).attempts()).isEqualTo(1);
        jdbc.update("UPDATE collection_job SET status='RUNNING',lease_until=TIMESTAMP '2000-01-01 00:00:00',lease_token='old'");adapter.failure=null;
        service.tick();assertThat(service.jobs().get(0).status()).isEqualTo("SUCCEEDED");assertThat(count("watch_auto_baseline")).isEqualTo(1);
    }
    @Test void searchCoverageNeverTurnsMissingSearchResultsIntoDelistings() throws Exception {
        inventoryComplete=false;service.configure("STORE",store,adapter.provider(),true,null);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001","B0AUTO0002");
        observe("2026-10-21T12:00:00Z",true,"B0AUTO0002");
        observe("2026-10-22T12:00:00Z",true,"B0AUTO0002");
        assertThat(jdbc.queryForObject("SELECT missing_count FROM watch_presence WHERE store_id=? AND asin='B0AUTO0001'",Integer.class,store)).isZero();
    }
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE store_id=?",Long.class,store);}
}
