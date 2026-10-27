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
 @Test void readsNameOnlyFromMatchingSellerSearchScope(){
  var doc=Jsoup.parse("<select><option value='me=OTHERSELLER'>Other store</option><option value='me=TESTSELLER'>Example &amp; Store</option></select>");
  assertThat(AmazonPublicAdapter.storeName(doc,"TESTSELLER")).isEqualTo("Example & Store");
  assertThat(AmazonPublicAdapter.storeName(doc,"UNKNOWNSELLER")).isNull();
  var result=adapter.store("TESTSELLER",uri->Jsoup.parse(doc.html()+searchPage("B0TEST0001",null)));
  assertThat(result.fields().path("storeName").asText()).isEqualTo("Example & Store");
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
  assertThatThrownBy(()->adapter.store("TESTSELLER",uri->{int page=count.incrementAndGet();return Jsoup.parse(searchPage(String.format("B0TEST%04d",page),"/s?me=TESTSELLER&page="+(page+1)));})).hasMessageContaining("采集上限");assertThat(count.get()).isEqualTo(AmazonPublicAdapter.MAX_STORE_PAGES);
 }
 @Test void initializesAnonymousUsDeliveryAndChecksTheResult(){
  var calls=new java.util.ArrayList<AmazonPublicAdapter.PublicRequest>();
  String initial="<span id='glow-ingress-line2'>China</span><span id='nav-global-location-data-modal-action' data-a-modal='{&quot;url&quot;:&quot;/portal-migration/hz/glow/get-rendered-address-selections?deviceType=desktop&quot;,&quot;ajaxHeaders&quot;:{&quot;anti-csrftoken-a2z&quot;:&quot;SYNTHETIC&quot;}}'></span>";
  var result=adapter.usPage(URI.create("https://www.amazon.com/s?me=TESTSELLER"),r->{
   calls.add(r);
   return switch(calls.size()){
    case 1 -> initial;
    case 2 -> "<script>var config={CSRF_TOKEN : \"SYNTHETIC_MODAL\"};</script>";
    case 3 -> "{\"isValidAddress\":1,\"sembuUpdated\":1}";
    default -> "<span id='glow-ingress-line2'>New York 10001</span>";
   };
  });
  assertThat(AmazonPublicAdapter.isUsLocation(result)).isTrue();assertThat(calls).hasSize(4);
  assertThat(calls.get(2).body()).contains("10001","LOCATION_INPUT");assertThat(calls.get(2).headers()).containsEntry("anti-csrftoken-a2z","SYNTHETIC_MODAL");
 }
 @Test void rejectsWrongRegionCurrencyAndPreferenceEndpoints(){
  assertThatThrownBy(()->AmazonPublicAdapter.requireUsLocation(Jsoup.parse("<span id='glow-ingress-line2'>China</span>"))).hasMessageContaining("地区");
  assertThatThrownBy(()->AmazonPublicAdapter.requireUsLocation(Jsoup.parse("<span id='glow-ingress-line2'>New York 10001</span><span class=a-price><span class=a-offscreen>CNY 20.00</span></span>"))).hasMessageContaining("币种");
  assertThatThrownBy(()->AmazonPublicAdapter.validateLocation(URI.create("https://evil.example/portal-migration/hz/glow/address-change"))).hasMessageContaining("范围");
  assertThatThrownBy(()->AmazonPublicAdapter.validateLocation(URI.create("https://www.amazon.com/ap/signin"))).hasMessageContaining("范围");
  assertThatThrownBy(()->adapter.usPage(URI.create("https://www.amazon.com/s"),r->"<title>Robot Check</title>")).hasMessageContaining("验证码");
  assertThatThrownBy(()->adapter.usPage(URI.create("https://www.amazon.com/s"),r->"<span id='glow-ingress-line2'>China</span>")).hasMessageContaining("设置");
 }
 @Test void parsesFeaturedSplitAndOtherOfferPricesWithoutUsingListPrices(){
  var fields=new ObjectMapper().createObjectNode();
  AmazonPublicAdapter.searchPrice(Jsoup.parse("<span class='a-price a-text-price'><span class=a-offscreen>$99.99</span></span><span class=a-price><span class=a-offscreen>$12.99</span></span>"),fields);
  assertThat(fields.path("price").decimalValue()).isEqualByComparingTo("12.99");assertThat(fields.path("priceType").asText()).isEqualTo("FEATURED");
  fields.removeAll();AmazonPublicAdapter.searchPrice(Jsoup.parse("<span class=a-price><span class=a-price-symbol>$</span><span class=a-price-whole>1,299.</span><span class=a-price-fraction>99</span></span>"),fields);
  assertThat(fields.path("price").decimalValue()).isEqualByComparingTo("1299.99");
  fields.removeAll();AmazonPublicAdapter.searchPrice(Jsoup.parse("<div>No featured offers available<span class=a-color-base>$34.09</span><span data-action=s-show-all-offers-display>1 new offer</span></div>"),fields);
  assertThat(fields.path("price").decimalValue()).isEqualByComparingTo("34.09");assertThat(fields.path("priceType").asText()).isEqualTo("OFFER_FROM");
  fields.removeAll();AmazonPublicAdapter.searchPrice(Jsoup.parse("<span class='a-price a-text-price'><span class=a-offscreen>$99.99</span></span><span class=a-price><span class=a-offscreen>CNY 12.99</span></span>"),fields);assertThat(fields.isEmpty()).isTrue();
 }
 @Test void readsAccessibleRatingAndLeavesUnratedProductsUnknown(){
  assertThat(AmazonPublicAdapter.rating(Jsoup.parse("<span aria-label='4.8 out of 5 stars'></span>"))).isEqualTo("4.8 out of 5 stars");
  assertThat(AmazonPublicAdapter.rating(Jsoup.parse("<i class=a-icon-alt>Prime</i><i class=a-icon-alt>4.6 out of 5 stars</i>"))).isEqualTo("4.6 out of 5 stars");
  assertThat(AmazonPublicAdapter.rating(Jsoup.parse("<h2>New on Amazon</h2>"))).isNull();
 }
 @Test void keepsMoreThanTwentyProductsAcrossLegacyPagination(){
  var result=adapter.store("TESTSELLER",uri->{
   int page=uri.toString().contains("page=2")?2:1;var html=new StringBuilder();
   for(int n=1;n<=16;n++)html.append("<div data-component-type='s-search-result' data-asin='").append(String.format("B0TEST%04d",(page-1)*16+n)).append("'><h2>Synthetic product</h2></div>");
   html.append(page==1?"<ul class=a-pagination><li class=a-last><a href='/s?me=TESTSELLER&page=2'>Next</a></li></ul>":"<span class='s-pagination-next s-pagination-disabled'>Next</span>");return Jsoup.parse(html.toString());
  });assertThat(result.fields().path("products").size()).isEqualTo(32);
 }

 @Test void acceptsSinglePageOnlyWhenExactCountProvesEveryResultWasRead(){
  String row="<div data-component-type=s-search-result data-asin=B0TEST0001><h2>Item</h2></div>";
  var result=adapter.store("TESTSELLER",u->Jsoup.parse("<div data-component-type=s-result-info-bar><span>1 result</span></div>"+row));
  assertThat(result.pagesComplete()).isTrue();assertThat(result.fields().path("itemCount").asInt()).isEqualTo(1);
  for(String count:new String[]{"2 results","over 1 results","about 1 results","1-1 of 10 results"})
   assertThatThrownBy(()->adapter.store("TESTSELLER",u->Jsoup.parse("<div data-component-type=s-result-info-bar><span>"+count+"</span></div>"+row))).hasMessageContaining("最后一页");
  assertThatThrownBy(()->adapter.store("TESTSELLER",u->Jsoup.parse("<div data-component-type=s-result-info-bar><span>1 result</span></div>"+row+row))).hasMessageContaining("最后一页");
 }
 @Test void traversesBeyondOldTwentyPageLimitAndLeaseCoversDeadline(){
  var calls=new java.util.concurrent.atomic.AtomicInteger();
  var result=adapter.store("TESTSELLER",u->{int n=calls.incrementAndGet();return Jsoup.parse(searchPage(String.format("B0TEST%04d",n),n==120?null:"/s?me=TESTSELLER&page="+(n+1)));});
  assertThat(result.fields().path("pageCount").asInt()).isEqualTo(120);assertThat(result.fields().path("itemCount").asInt()).isEqualTo(120);
  assertThat(CollectionService.leaseSeconds(AmazonPublicAdapter.SOURCE,"STORE")).isGreaterThan(AmazonPublicAdapter.STORE_TIMEOUT_SECONDS+100);
  assertThat(CollectionService.leaseSeconds(AmazonPublicAdapter.SOURCE,"PRODUCT")).isEqualTo(300);
 }
 @Test void decodesCompressedResponsesWithBoundedExpandedSize()throws Exception{
  byte[] html="<html>Public store</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  for(String format:new String[]{"gzip","deflate"}){
   var bytes=new java.io.ByteArrayOutputStream();
   try(var out=format.equals("gzip")?new java.util.zip.GZIPOutputStream(bytes):new java.util.zip.DeflaterOutputStream(bytes)){out.write(html);}
   assertThat(AmazonPublicAdapter.decodeBody(bytes.toByteArray(),format)).isEqualTo(new String(html,java.nio.charset.StandardCharsets.UTF_8));
  }
  var oversized=new java.io.ByteArrayOutputStream();try(var out=new java.util.zip.GZIPOutputStream(oversized)){out.write(new byte[4_000_001]);}
  assertThatThrownBy(()->AmazonPublicAdapter.decodeBody(oversized.toByteArray(),"gzip")).hasMessageContaining("大小限制");
  assertThatThrownBy(()->AmazonPublicAdapter.decodeBody(new byte[]{1,2,3},"gzip")).hasMessageContaining("不完整");
  assertThatThrownBy(()->AmazonPublicAdapter.decodeBody(html,"br")).hasMessageContaining("压缩格式");
 }

}
