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
 var unknown=adapter.product(Jsoup.parse("<input id=ASIN value=B0TEST0001><span id=productTitle>Example</span>"),"B0TEST0001");assertThat(unknown.get("price").isNull()).isTrue();assertThat(unknown.get("availability").isNull()).isTrue();assertThat(unknown.size()).isEqualTo(7);}
 @Test void rejectsWrongAsinAndRobotPages(){assertThatThrownBy(()->adapter.product(Jsoup.parse(PAGE),"B0TEST0002")).isInstanceOf(CollectionAdapter.CollectionFailure.class);assertThatThrownBy(()->adapter.product(Jsoup.parse("<title>Robot Check</title>"+PAGE),"B0TEST0001")).hasMessageContaining("验证码");}
 @Test void rejectsRedirectsOutsidePublicAmazonRoutes(){for(String uri:new String[]{"http://www.amazon.com/dp/B0TEST0001","https://evil.example/dp/B0TEST0001","https://www.amazon.com@localhost/dp/B0TEST0001","https://www.amazon.com/ap/signin","https://www.amazon.com:444/dp/B0TEST0001"})assertThatThrownBy(()->AmazonPublicAdapter.validate(URI.create(uri))).isInstanceOf(CollectionAdapter.CollectionFailure.class);AmazonPublicAdapter.validate(URI.create("https://www.amazon.com/s?me=SYNTHETIC"));}
 @Test void doesNotInventRangeOrUnknownPrices(){assertThat(AmazonPublicAdapter.price("$10.00 - $20.00")).isNull();assertThat(AmazonPublicAdapter.price(null)).isNull();assertThat(AmazonPublicAdapter.price("$1,299.99")).isEqualByComparingTo("1299.99");}
}
