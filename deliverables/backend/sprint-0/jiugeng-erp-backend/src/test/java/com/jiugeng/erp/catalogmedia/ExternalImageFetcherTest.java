package com.jiugeng.erp.catalogmedia;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.*;
class ExternalImageFetcherTest {
 @Test void rejectsPrivateLoopbackMetadataAndReservedAddresses()throws Exception{
  for(String ip:new String[]{"127.0.0.1","10.1.2.3","172.16.0.1","192.168.1.2","169.254.169.254","100.64.0.1","0.0.0.0","224.0.0.1","::1","::ffff:127.0.0.1"})assertThat(ExternalImageFetcher.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();
  assertThat(ExternalImageFetcher.publicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
  assertThatThrownBy(()->new ExternalImageFetcher().fetch("http://127.0.0.1/private",null,null)).isInstanceOf(java.io.IOException.class);
 }
 @Test void rejectsUnsafeSchemesCredentialsAndPorts()throws Exception{
  for(String url:new String[]{"file:///private/file","http://user:password@example.invalid/photo","http://example.invalid:8080/photo","https://example.invalid/photo#fragment","//example.invalid/photo"})assertThatThrownBy(()->ExternalImageFetcher.validate(url)).isInstanceOf(java.io.IOException.class);
  assertThat(ExternalImageFetcher.validate("https://example.invalid/photo.png").getScheme()).isEqualTo("https");
 }
}
