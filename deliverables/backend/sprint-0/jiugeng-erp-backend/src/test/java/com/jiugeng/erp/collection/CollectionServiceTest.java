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
        String source="synthetic-contract-test";Observation result;Runnable during=()->{};RuntimeException failure;boolean authorized=true;
        public String provider(){return source;}public Set<String> kinds(){return Set.of("STORE","WATCH_NOVELTY");}public boolean approved(){return authorized;}
        public Observation collect(Target target){during.run();if(failure!=null)throw failure;return result;}
    }
    @BeforeEach void setup(){
        jdbc.update("DELETE FROM collection_notice");jdbc.update("DELETE FROM collection_job");jdbc.update("DELETE FROM collection_schedule");
        inventoryComplete=true;adapter.source="synthetic-contract-test";adapter.authorized=true;adapter.failure=null;adapter.during=()->{};
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
    @Test void explicitCollectAfterOwnerEditReplacesStaleInFlightJob() throws Exception {
        adapter.source="amazon-public-us";service.enqueue("STORE",store);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        jdbc.update("UPDATE collection_job SET status='RUNNING',lease_token='old'");
        jdbc.update("UPDATE watch_store SET version=version+1 WHERE id=?",store);
        service.enqueue("STORE",store);
        assertThat(service.jobs().get(0).status()).isEqualTo("CANCELLED");
        observe("2026-10-21T12:00:00Z",true,"B0AUTO0001");
        assertThat(service.jobs()).extracting(CollectionService.Job::status).contains("SUCCEEDED","CANCELLED");
    }
    @Test void observedStoreDisplayFieldsPersistWithoutClearingOnMissingNodes() throws Exception {
        service.configure("STORE",store,adapter.provider(),true,null);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        var fields=json.createObjectNode().put("pageCount",1).put("itemCount",1);
        fields.putArray("products").addObject().put("asin","B0AUTO0001").put("title","Synthetic").put("imageUrl","https://example.com/image.jpg").put("price",19.99).put("priceType","OFFER_FROM").put("rating","4.5 out of 5 stars");
        adapter.result=new CollectionAdapter.Observation(Instant.parse("2026-10-21T12:00:00Z"),"COMPLETE",true,fields);
        jdbc.update("UPDATE collection_schedule SET next_at=TIMESTAMP '2000-01-01 00:00:00'");service.tick();
        observe("2026-10-22T12:00:00Z",true,"B0AUTO0001");
        assertThat(jdbc.queryForObject("SELECT price FROM watch_presence WHERE store_id=?",java.math.BigDecimal.class,store)).isEqualByComparingTo("19.99");
        assertThat(jdbc.queryForObject("SELECT price_type FROM watch_presence WHERE store_id=?",String.class,store)).isEqualTo("OFFER_FROM");
        assertThat(jdbc.queryForObject("SELECT image_url FROM watch_presence WHERE store_id=?",String.class,store)).isEqualTo("https://example.com/image.jpg");
    }
    @Test void resolvesIdPlaceholderButPreservesCustomStoreName() throws Exception {
        jdbc.update("UPDATE watch_store SET store_name=store_key WHERE id=?",store);
        service.configure("STORE",store,adapter.provider(),true,null);
        var fields=json.createObjectNode().put("pageCount",1).put("itemCount",1).put("storeName","Example Store");
        fields.putArray("products").addObject().put("asin","B0AUTO0001").put("title","Synthetic");
        adapter.result=new CollectionAdapter.Observation(Instant.parse("2026-10-21T12:00:00Z"),"COMPLETE",true,fields);
        service.tick();
        assertThat(jdbc.queryForObject("SELECT store_name FROM watch_store WHERE id=?",String.class,store)).isEqualTo("Example Store");
        jdbc.update("UPDATE watch_store SET store_name='Custom name' WHERE id=?",store);
        adapter.result=new CollectionAdapter.Observation(Instant.parse("2026-10-22T12:00:00Z"),"COMPLETE",true,fields);
        jdbc.update("UPDATE collection_schedule SET next_at=TIMESTAMP '2000-01-01 00:00:00'");service.tick();
        assertThat(jdbc.queryForObject("SELECT store_name FROM watch_store WHERE id=?",String.class,store)).isEqualTo("Custom name");
    }
    @Test void noveltyDetailFailureDoesNotChangeDiscoveryAndPartialDateCompletesOnce()throws Exception{
        adapter.source="amazon-public-us";service.enqueue("STORE",store);
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        observe("2026-10-21T12:00:00Z",true,"B0AUTO0001","B0AUTO0002");
        String event=jdbc.queryForObject("SELECT id FROM watch_new_product_event WHERE store_id=?",String.class,store);
        service.enqueue("WATCH_NOVELTY",event);
        jdbc.update("UPDATE collection_schedule SET manual_requested=TRUE WHERE kind='STORE' AND target_id=?",store);service.tick();
        assertThat(jdbc.queryForObject("SELECT j.status FROM collection_job j JOIN collection_schedule s ON s.id=j.schedule_id WHERE s.kind='WATCH_NOVELTY' AND s.target_id=?",String.class,event)).isEqualTo("QUEUED");
        adapter.failure=new CollectionAdapter.CollectionFailure("Synthetic retry",true,60);service.tick();
        assertThat(jdbc.queryForObject("SELECT novelty_status FROM watch_new_product_event WHERE id=?",String.class,event)).isEqualTo("PENDING");
        assertThat(count("watch_new_product_event")).isEqualTo(1);
        adapter.failure=null;adapter.result=new CollectionAdapter.Observation(Instant.parse("2026-10-22T12:00:00Z"),"PARTIAL",true,json.createObjectNode().put("listedAt","September 1, 2026"));
        jdbc.update("UPDATE collection_job SET available_at=TIMESTAMP '2000-01-01 00:00:00' WHERE status='RETRY'");service.tick();
        assertThat(jdbc.queryForObject("SELECT novelty_status FROM watch_new_product_event WHERE id=?",String.class,event)).isEqualTo("CANDIDATE");
        assertThat(jdbc.queryForObject("SELECT enabled FROM collection_schedule WHERE kind='WATCH_NOVELTY' AND target_id=?",Boolean.class,event)).isFalse();
        assertThat(jdbc.queryForObject("SELECT j.status FROM collection_job j JOIN collection_schedule s ON s.id=j.schedule_id WHERE s.kind='WATCH_NOVELTY' AND s.target_id=?",String.class,event)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT first_detected_at FROM watch_new_product_event WHERE id=?",java.sql.Timestamp.class,event).toInstant()).isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));
        service.enqueue("WATCH_NOVELTY",event);adapter.during=()->jdbc.update("UPDATE watch_store SET status='DISABLED',version=version+1 WHERE id=?",store);service.tick();
        assertThat(service.jobs()).extracting(CollectionService.Job::status).contains("CANCELLED");
    }
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE store_id=?",Long.class,store);}
    @Test void storeTimingValidatesAndPreservesActiveJobFence()throws Exception{
        var schedule=service.configure("STORE",store,adapter.provider(),true,null);
        var saved=service.saveStoreTiming(store,new CollectionService.Timing(3,"09:30","Asia/Shanghai",0,null,true));
        assertThat(saved.intervalDays()).isEqualTo(3);assertThat(saved.localTime()).isEqualTo("09:30");assertThat(saved.version()).isEqualTo(1);
        assertThat(Instant.parse(saved.nextAt()).isAfter(Instant.now())).isTrue();
        assertThat(service.schedules().getFirst().version()).isEqualTo(schedule.version());
        assertThatThrownBy(()->service.saveStoreTiming(store,new CollectionService.Timing(1,"08:00","Asia/Shanghai",0,null,true))).hasMessageContaining("409");
        for(var input:List.of(new CollectionService.Timing(0,"08:00","Asia/Shanghai",1,null,true),new CollectionService.Timing(31,"08:00","Asia/Shanghai",1,null,true),new CollectionService.Timing(1,"24:00","Asia/Shanghai",1,null,true),new CollectionService.Timing(1,"08:00","invalid",1,null,true)))
            assertThatThrownBy(()->service.saveStoreTiming(store,input)).hasMessageContaining("400");
        adapter.during=()->service.saveStoreTiming(store,new CollectionService.Timing(7,"10:00","America/Los_Angeles",1,null,true));
        observe("2026-10-20T12:00:00Z",true,"B0AUTO0001");
        assertThat(service.jobs().getFirst().status()).isEqualTo("SUCCEEDED");
    }
    @Test void manualCollectionDoesNotMoveTheScheduledStart()throws Exception{
        service.configure("STORE",store,adapter.provider(),true,null);
        var saved=service.saveStoreTiming(store,new CollectionService.Timing(7,"09:30","Asia/Shanghai",0,null,true));
        jdbc.update("UPDATE collection_schedule SET manual_requested=TRUE");
        var fields=json.createObjectNode().put("pageCount",1).put("itemCount",1).put("inventoryComplete",false);fields.putArray("products").addObject().put("asin","B0AUTO0001").put("title","Synthetic");
        adapter.result=new CollectionAdapter.Observation(Instant.now(),"COMPLETE",true,fields);service.tick();
        assertThat(service.jobs().getFirst().status()).isEqualTo("SUCCEEDED");
        assertThat(service.storeTiming(store).nextAt()).isEqualTo(saved.nextAt());assertThat(service.schedules().getFirst().manualRequested()).isFalse();
        service.tick();assertThat(service.jobs()).hasSize(1);
    }
    @Test void timingUsesWallClockAcrossDstAndKeepsMultiDayCadence(){
        assertThat(CollectionService.nextOccurrence(Instant.parse("2026-03-07T11:00:00Z"),Instant.parse("2026-03-07T11:00:00Z"),1,"03:00","America/Los_Angeles")).isEqualTo(Instant.parse("2026-03-08T10:00:00Z"));
        assertThat(CollectionService.nextOccurrence(Instant.parse("2026-10-27T05:00:00Z"),Instant.parse("2026-10-20T01:30:00Z"),3,"09:30","Asia/Shanghai")).isEqualTo(Instant.parse("2026-10-29T01:30:00Z"));
    }

}
