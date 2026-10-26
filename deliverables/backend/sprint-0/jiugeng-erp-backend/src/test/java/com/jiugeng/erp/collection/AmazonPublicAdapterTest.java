package com.jiugeng.erp.collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;
class AmazonPublicAdapterTest {
 private final AmazonPublicAdapter adapter=new AmazonPublicAdapter(new ObjectMapper(),false);
 private static final String PAGE="""
 <html><head><title>Example product</title></head><body>
 <input id="ASIN" value="B0TEST0001"><span id="productTitle"> Synthetic &amp; sample </span>
 <a id="bylineInfo">Visit the Example Store</a><img id="landingImage" src="https://m.media-amazon.com/images/sample.jpg">
 <div id="feature-bullets"><ul><li><span class="a-list-item">First feature</span></li></ul></div>
 <div id="corePriceDisplay_desktop_feature_div"><span class="a-price"><span class="a-offscreen">$19.99</span></span></div>
 <div id="availability">In Stock</div></body></html>
 """;
 @Test void parsesObservedFieldsAndRetainsUnknownSemantics(){var fields=adapter.product(Jsoup.parse(PAGE),"B0TEST0001");assertThat(fields.path("title").asText()).isEqualTo("Synthetic & sample");assertThat(fields.path("price").decimalValue()).isEqualByComparingTo("19.99");assertThat(fields.path("brand").asText()).isEqualTo("Example");assertThat(fields.path("availability").asText()).isEqualTo("AVAILABLE");assertThat(fields.path("bullets").size()).isEqualTo(1);
 var unknown=adapter.product(Jsoup.parse("<input id=ASIN value=B0TEST0001><span id=productTitle>Example</span>"),"B0TEST0001");assertThat(!unknown.has("price")).isTrue();assertThat(!unknown.has("availability")).isTrue();assertThat(unknown.size()).isEqualTo(1);}
 @Test void rejectsWrongAsinAndRobotPages(){assertThatThrownBy(()->adapter.product(Jsoup.parse(PAGE),"B0TEST0002")).isInstanceOf(CollectionAdapter.CollectionFailure.class);assertThatThrownBy(()->adapter.product(Jsoup.parse("<title>Robot Check</title>"+PAGE),"B0TEST0001")).hasMessageContaining("验证码");}
 @Test void rejectsRedirectsOutsidePublicAmazonRoutes(){for(String uri:new String[]{"http://www.amazon.com/dp/B0TEST0001","https://evil.example/dp/B0TEST0001","https://www.amazon.com@localhost/dp/B0TEST0001","https://www.amazon.com/ap/signin","https://www.amazon.com:444/dp/B0TEST0001"})assertThatThrownBy(()->AmazonPublicAdapter.validate(URI.create(uri))).isInstanceOf(CollectionAdapter.CollectionFailure.class);AmazonPublicAdapter.validate(URI.create("https://www.amazon.com/s?me=SYNTHETIC"));}
 @Test void doesNotInventRangeOrUnknownPrices(){assertThat(AmazonPublicAdapter.price("$10.00 - $20.00")).isNull();assertThat(AmazonPublicAdapter.price(null)).isNull();assertThat(AmazonPublicAdapter.price("$1,299.99")).isEqualByComparingTo("1299.99");}
 @Test void stalledBodyTimesOutAndNextRequestStillWorks() throws Exception {
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var executor=java.util.concurrent.Executors.newCachedThreadPool();server.setExecutor(executor);
  server.createContext("/slow", exchange->{try{exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write('x');exchange.getResponseBody().flush();Thread.sleep(5000);}catch(Exception ignored){}finally{exchange.close();}});
  server.createContext("/ok", exchange->{exchange.sendResponseHeaders(200,2);exchange.getResponseBody().write("ok".getBytes());exchange.close();});server.start();
  try(var client=java.net.http.HttpClient.newHttpClient()){
   String url="http://127.0.0.1:"+server.getAddress().getPort();
   assertThatThrownBy(()->AmazonPublicAdapter.readResponse(client,java.net.http.HttpRequest.newBuilder(URI.create(url+"/slow")).build(),java.time.Duration.ofMillis(200))).isInstanceOf(java.util.concurrent.TimeoutException.class);
   assertThat(new String(AmazonPublicAdapter.readResponse(client,java.net.http.HttpRequest.newBuilder(URI.create(url+"/ok")).build(),java.time.Duration.ofSeconds(2)).body())).isEqualTo("ok");
  }finally{server.stop(0);executor.shutdownNow();}
 }
 @Test void oversizedBodyCancelsSubscription(){
  var body=new AmazonPublicAdapter.BoundedBody(2);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
  body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled.set(true);}});
  body.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[3])));
  assertThat(cancelled).isTrue();assertThat(body.getBody().toCompletableFuture().isCompletedExceptionally()).isTrue();
 }
 private String searchPage(String asin,String next){return "<div data-component-type='s-search-result' data-asin='"+asin+"'><h2>Synthetic item</h2></div>"+(next==null?"<span class='s-pagination-next s-pagination-disabled'>Next</span>":"<a class='s-pagination-next' href='"+next+"'>Next</a>");}
 @Test void traversesPagesButDoesNotClaimInventoryCoverage(){
  var result=adapter.store("TESTSELLER",uri->Jsoup.parse(searchPage(uri.toString().contains("page=2")?"B0TEST0002":"B0TEST0001",uri.toString().contains("page=2")?null:"/s?me=TESTSELLER&page=2")));
  assertThat(result.fields().path("itemCount").asInt()).isEqualTo(2);assertThat(result.fields().path("pageCount").asInt()).isEqualTo(2);assertThat(result.fields().path("inventoryComplete").asBoolean()).isFalse();assertThat(result.fields().path("visitedPages").size()).isEqualTo(2);
 }
 @Test void rejectsRepeatedTruncatedAndExcessivePages(){
  assertThatThrownBy(()->adapter.store("TESTSELLER",uri->Jsoup.parse(searchPage("B0TEST0001","/s?me=TESTSELLER&page=2")))).isInstanceOf(CollectionAdapter.CollectionFailure.class);
  assertThatThrownBy(()->adapter.store("TESTSELLER",uri->Jsoup.parse("<div data-component-type='s-search-result' data-asin='B0TEST0001'><h2>Item</h2></div>"))).hasMessageContaining("最后一页");
  var count=new java.util.concurrent.atomic.AtomicInteger();
  assertThatThrownBy(()->adapter.store("TESTSELLER",uri->{int page=count.incrementAndGet();return Jsoup.parse(searchPage(String.format("B0TEST%04d",page),"/s?me=TESTSELLER&page="+(page+1)));})).hasMessageContaining("超出");assertThat(count.get()).isEqualTo(20);
 }
}
