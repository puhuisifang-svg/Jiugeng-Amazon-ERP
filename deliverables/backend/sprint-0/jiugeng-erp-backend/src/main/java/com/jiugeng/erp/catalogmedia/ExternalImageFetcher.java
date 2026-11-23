package com.jiugeng.erp.catalogmedia;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

/** DNS is checked by the connection resolver itself, including each redirect. No cookies or credentials. */
@Component
class ExternalImageFetcher {
    record Result(byte[] bytes,String etag,String modified,boolean unchanged){}
    static URI validate(String value)throws IOException {
        try{
            URI u=URI.create(value);
            if(value.length()>2048||u.getHost()==null||u.getRawUserInfo()!=null||u.getFragment()!=null||
                !("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||
                (u.getPort()!=-1&&u.getPort()!=80&&u.getPort()!=443))throw new IOException("Unsupported image URL");
            return u;
        }catch(IllegalArgumentException e){throw new IOException("Invalid image URL");}
    }
    static boolean publicAddress(InetAddress address){
        // IPv4-only outbound connections also reject mapped IPv6, link-local and transition tunnels.
        if(!(address instanceof Inet4Address)||address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] a=address.getAddress();int x=a[0]&255,y=a[1]&255;
        return x!=0&&x!=10&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==172&&y>=16&&y<=31)&&
            !(x==192&&(y==168||y==0))&&!(x==198&&(y==18||y==19||y==51))&&!(x==203&&y==0);
    }
    Result fetch(String url,String etag,String modified)throws IOException {
        URI uri=validate(url);long deadline=System.nanoTime()+java.time.Duration.ofSeconds(20).toNanos();
        var config=RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(5000).setConnectionRequestTimeout(1000).build();
        try(var client=HttpClients.custom().setDefaultRequestConfig(config).disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching().disableContentCompression().setUserAgent("Jiugeng-ImageSync/1.0")
                .setDnsResolver(host->{var addresses=InetAddress.getAllByName(host);if(Arrays.stream(addresses).anyMatch(a->a instanceof Inet4Address&&!publicAddress(a)))throw new UnknownHostException("Image host is not public");
                    var safe=Arrays.stream(addresses).filter(ExternalImageFetcher::publicAddress).toArray(InetAddress[]::new);if(safe.length==0)throw new UnknownHostException("Image host is not public");return safe;}).build()){
            for(int redirect=0;redirect<4;redirect++){
                // Literal IP hosts may bypass the DNS resolver in the HTTP client.
                if(uri.getHost().matches("[0-9.]+")||uri.getHost().contains(":")){
                    if(!publicAddress(InetAddress.getByName(uri.getHost())))throw new IOException("Image host is not public");
                }
                if(System.nanoTime()>deadline)throw new IOException("Image fetch timeout");
                var request=new HttpGet(uri);request.setHeader("Accept","image/jpeg,image/png,image/webp");
                if(etag!=null)request.setHeader("If-None-Match",etag);
                if(modified!=null)request.setHeader("If-Modified-Since",modified);
                try(var response=client.execute(request)){
                    int status=response.getStatusLine().getStatusCode();
                    if(status==304)return new Result(null,etag,modified,true);
                    if(status>=300&&status<400){var location=response.getFirstHeader("Location");if(location==null)throw new IOException("Invalid image redirect");uri=validate(uri.resolve(location.getValue()).toString());continue;}
                    if(status!=200||response.getEntity()==null)throw new IOException("Image source unavailable");
                    if(response.getEntity().getContentLength()>LocalCatalogImageStore.MAX_BYTES)throw new IOException("Image source too large");
                    var bytes=new ByteArrayOutputStream();
                    try(var stream=response.getEntity().getContent()){
                        byte[] chunk=new byte[8192];int n;while((n=stream.read(chunk))!=-1){if(System.nanoTime()>deadline||bytes.size()+n>LocalCatalogImageStore.MAX_BYTES)throw new IOException("Image source limit exceeded");bytes.write(chunk,0,n);}
                    }
                    return new Result(bytes.toByteArray(),header(response,"ETag",512),header(response,"Last-Modified",128),false);
                }
            }
        }
        throw new IOException("Too many image redirects");
    }
    private String header(org.apache.http.HttpResponse response,String name,int max){var h=response.getFirstHeader(name);return h==null||h.getValue().length()>max?null:h.getValue();}
}
