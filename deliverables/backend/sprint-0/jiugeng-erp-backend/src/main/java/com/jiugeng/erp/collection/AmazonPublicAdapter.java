package com.jiugeng.erp.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Public US storefront only. Anonymous US delivery preferences only; no seller account or CAPTCHA solving. */
@Component @Profile("!mock")
class AmazonPublicAdapter implements CollectionAdapter {
    static final String SOURCE="amazon-public-us";
    static final int MAX_STORE_PAGES=500;
    static final long STORE_TIMEOUT_SECONDS=1800;
    static final long STORE_LEASE_SECONDS=STORE_TIMEOUT_SECONDS+180;
    private final ObjectMapper mapper; private final boolean enabled; private final HttpClient client;
    private long lastRequest;
    static final String US_ZIP="10001";
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    record PublicRequest(URI uri,String body,Map<String,String> headers) {}

    AmazonPublicAdapter(ObjectMapper mapper,@Value("${erp.collection.amazon.enabled:false}") boolean enabled){
        this.mapper=mapper;this.enabled=enabled;
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).cookieHandler(cookies);
        // A deployment may explicitly use its existing outbound proxy; never log its configuration.
        String proxy=System.getenv("ERP_COLLECTION_PROXY");
        if(proxy!=null&&!proxy.isBlank()) { URI p=URI.create(proxy);if(!Set.of("http","https").contains(p.getScheme())||p.getHost()==null||p.getUserInfo()!=null||p.getPort()<1)throw new IllegalArgumentException("采集代理配置无效");builder.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(),p.getPort()))); }
        client=builder.build();
    }
    public String provider(){return SOURCE;}
    public Set<String> kinds(){return Set.of("PRODUCT","STORE");}
    public boolean approved(){return enabled;}
    public synchronized Observation collect(Target target){
        if(!enabled)throw failure("网页采集未启用",false);
        if(target.kind().equals("PRODUCT")){
            if(!target.key().matches("[A-Z0-9]{10}"))throw failure("ASIN无效",false);
            Document doc=usPage(URI.create("https://www.amazon.com/dp/"+target.key()));
            ObjectNode fields=product(doc,target.key());
            return new Observation(Instant.now(),fields.size()==7?"COMPLETE":"PARTIAL",true,fields);
        }
        if(!target.kind().equals("STORE")||!target.key().matches("[A-Z0-9]{8,30}"))throw failure("请填写有效的亚马逊美国站seller店铺地址",false);
        URI start=URI.create("https://www.amazon.com/s?me="+target.key()+"&marketplaceID=ATVPDKIKX0DER");
        Document first=usPage(start);
        return store(target.key(),uri->{if(uri.equals(start))return first;Document doc=fetch(uri);requireUsLocation(doc);return doc;});
    }
    Observation store(String seller,java.util.function.Function<URI,Document> reader){
        URI uri=URI.create("https://www.amazon.com/s?me="+seller+"&marketplaceID=ATVPDKIKX0DER");
        Set<String> visited=new HashSet<>(),pageSignatures=new HashSet<>();var products=new LinkedHashMap<String,ObjectNode>();
        Instant started=Instant.now();int pages=0;String storeName=null;
        while(uri!=null){
            if(++pages>MAX_STORE_PAGES)throw failure("店铺超过"+MAX_STORE_PAGES+"页采集上限，未更新基线",false);
            if(Duration.between(started,Instant.now()).toSeconds()>STORE_TIMEOUT_SECONDS)throw failure("店铺采集超时，等待重试，未更新基线",true);
            if(!visited.add(uri.toString()))throw failure("店铺分页出现循环，未更新基线",false);
            Document doc=reader.apply(uri);checkPage(doc);if(storeName==null)storeName=storeName(doc,seller);var rows=doc.select("[data-component-type=s-search-result][data-asin]");
            if(rows.isEmpty())throw failure("未识别到店铺商品列表，保留原基线",false);
            Set<String> pageAsins=new TreeSet<>();
            for(Element row:rows){String asin=row.attr("data-asin");if(!asin.matches("[A-Z0-9]{10}"))continue;
                String title=text(row,"h2");if(title==null)throw failure("店铺商品标题缺失，未更新基线",false);
                ObjectNode item=mapper.createObjectNode().put("asin",asin).put("title",title.substring(0,Math.min(300,title.length())));
                String image=attr(row,"img.s-image","src");if(image!=null)item.put("imageUrl",image);
                searchPrice(row,item);
                String rating=rating(row);if(rating!=null)item.put("rating",rating);
                products.put(asin,item);pageAsins.add(asin);
            }
            if(pageAsins.isEmpty()||!pageSignatures.add(pageAsins.toString()))throw failure("店铺分页重复或结果为空，未更新基线",false);
            Element next=doc.selectFirst("a.s-pagination-next[href]:not(.s-pagination-disabled), .a-pagination .a-last:not(.a-disabled) a[href]");
            if(next!=null){URI candidate=uri.resolve(next.attr("href"));validate(candidate);Map<String,String> query=query(candidate);if(!seller.equals(query.get("me"))&&!seller.equals(query.get("seller")))throw failure("店铺下一页未保持seller范围，未更新基线",false);uri=candidate;}
            else if(doc.selectFirst(".s-pagination-next.s-pagination-disabled, .a-pagination .a-last.a-disabled")!=null || exactSinglePage(doc,pages,pageAsins.size(),rows.size()))uri=null;
            else throw failure("无法确认店铺最后一页，未更新基线",false);
        }
        ObjectNode fields=mapper.createObjectNode();if(storeName!=null)fields.put("storeName",storeName);var array=fields.putArray("products");products.values().forEach(array::add);fields.put("pageCount",pages).put("itemCount",products.size()).put("inventoryComplete",false).put("coverage","PUBLIC_SEARCH_PAGES");var coverage=fields.putArray("visitedPages");visited.stream().sorted().forEach(coverage::add);
        return new Observation(Instant.now(),"COMPLETE",true,fields);
    }
    static boolean exactSinglePage(Document doc,int pages,int uniqueItems,int rows){
        // Amazon omits the entire pager for a single page. Only an exact result count
        // matching every valid unique row proves completion; estimates cannot do so.
        if(pages!=1||uniqueItems!=rows||doc.selectFirst(".s-pagination-container, .a-pagination")!=null)return false;
        for(Element span:doc.select("[data-component-type=s-result-info-bar] span")){
            var match=java.util.regex.Pattern.compile("^([0-9,]+) results?$").matcher(span.text().trim());
            if(match.matches())try{return Integer.parseInt(match.group(1).replace(",",""))==uniqueItems;}catch(NumberFormatException ignored){return false;}
        }
        return false;
    }
    static String storeName(Document doc,String seller){
        // Search scope is tied to this seller, unlike product brands or generic page titles.
        for(Element option:doc.select("select option[value]")){
            if(option.attr("value").equals("me="+seller)){
                String name=option.text().trim();
                if(!name.isBlank()&&name.length()<=200&&!name.equalsIgnoreCase("Amazon.com")&&!name.equals(seller))return name;
            }
        }
        return null;
    }
    ObjectNode product(Document doc,String asin){
        checkPage(doc);
        String title=text(doc,"#productTitle");String actual=attr(doc,"input#ASIN","value");
        if(actual==null)actual=attr(doc,"input[name=ASIN]","value");
        if(title==null||!asin.equalsIgnoreCase(actual))throw failure("未识别到对应ASIN的完整商品页",false);
        ObjectNode fields=mapper.createObjectNode();
        // Absent nodes mean unobserved, not an explicit null observation.
        fields.put("title",title);String brand=text(doc,"#bylineInfo");if(brand!=null)fields.put("brand",brand.replaceFirst("^Visit the ","").replaceFirst(" Store$","").replaceFirst("^Brand: *","").substring(0,Math.min(180,brand.replaceFirst("^Visit the ","").replaceFirst(" Store$","").replaceFirst("^Brand: *","").length())));
        String image=attr(doc,"#landingImage","data-old-hires");if(image==null)image=attr(doc,"#landingImage","src");if(image!=null&&image.startsWith("https://"))fields.put("imageUrl",image);
        var bullets=doc.select("#feature-bullets li span.a-list-item");if(!bullets.isEmpty()){var out=fields.putArray("bullets");bullets.stream().map(Element::text).map(String::trim).filter(v->!v.isEmpty()).limit(5).forEach(v->out.add(v.substring(0,Math.min(2000,v.length()))));}
        BigDecimal amount=price(text(doc,"#corePriceDisplay_desktop_feature_div .a-price .a-offscreen, #corePrice_feature_div .a-price .a-offscreen, #priceblock_ourprice, #priceblock_dealprice"));
        if(amount!=null){fields.put("price",amount);fields.put("currency","USD");}
        String availability=text(doc,"#availability");if(availability!=null){String lower=availability.toLowerCase(Locale.ROOT);if(lower.contains("currently unavailable")||lower.contains("out of stock"))fields.put("availability","UNAVAILABLE");else if(lower.contains("in stock")||lower.contains("left in stock"))fields.put("availability","AVAILABLE");}
        return fields;
    }
    private Document usPage(URI uri){
        // Separate anonymous cookie jar; never use the user's browser/login session.
        var currency=new HttpCookie("i18n-prefs","USD");currency.setDomain(".amazon.com");currency.setPath("/");currency.setVersion(0);currency.setSecure(true);
        cookies.getCookieStore().add(URI.create("https://www.amazon.com"),currency);
        return usPage(uri,this::request);
    }
    Document usPage(URI uri,java.util.function.Function<PublicRequest,String> reader){
        validate(uri);
        Document first=Jsoup.parse(reader.apply(new PublicRequest(uri,null,Map.of())),uri.toString());checkPage(first);
        if(isUsLocation(first)){requireUsLocation(first);return first;}
        Element modal=first.selectFirst("#nav-global-location-data-modal-action[data-a-modal]");
        if(modal==null){
            org.slf4j.LoggerFactory.getLogger(AmazonPublicAdapter.class).warn("Public Amazon location control missing: title={}, chars={}",first.title(),first.outerHtml().length());
            throw failure("暂未读取到美国站地区设置，等待自动重试",true);
        }
        try{
            var config=mapper.readTree(modal.attr("data-a-modal"));
            URI modalUri=uri.resolve(config.path("url").asText());validateLocation(modalUri);
            String token=config.path("ajaxHeaders").path("anti-csrftoken-a2z").asText();
            if(token.isBlank())throw failure("美国站地区校验信息缺失",false);
            String html=reader.apply(new PublicRequest(modalUri,null,Map.of("anti-csrftoken-a2z",token)));
            checkPage(Jsoup.parse(html));
            var match=java.util.regex.Pattern.compile("CSRF_TOKEN\\s*:\\s*\"([^\"]+)\"").matcher(html);
            if(!match.find())throw failure("无法确认美国站地区设置",false);
            String body=mapper.createObjectNode().put("locationType","LOCATION_INPUT").put("zipCode",US_ZIP).put("deviceType","web").put("storeContext","merchant-items").put("pageType","Search").put("actionSource","glow").toString();
            String response=reader.apply(new PublicRequest(URI.create("https://www.amazon.com/portal-migration/hz/glow/address-change?actionSource=glow"),body,Map.of("anti-csrftoken-a2z",match.group(1))));
            var result=mapper.readTree(response);
            if(result.path("isValidAddress").asInt()!=1||result.path("sembuUpdated").asInt()!=1)throw failure("美国站收货地区设置未生效",false);
            Document doc=Jsoup.parse(reader.apply(new PublicRequest(uri,null,Map.of())),uri.toString());checkPage(doc);requireUsLocation(doc);return doc;
        }catch(CollectionFailure e){throw e;}catch(Exception e){throw failure("美国站收货地区初始化失败，保留原数据",true);}
    }
    static boolean isUsLocation(Document doc){String location=text(doc,"#glow-ingress-line2");return location!=null&&location.matches("(?s).*\\b"+US_ZIP+"\\b.*");}
    static void requireUsLocation(Document doc){
        if(!isUsLocation(doc))throw failure("页面收货地区不是美国，已停止采集并保留原数据",false);
        for(Element e:doc.select(".a-price .a-offscreen"))if(e.text().matches("(?i).*(CNY|EUR|GBP|CAD|AUD|JPY|RMB|CN¥|€|£).*"))throw failure("页面币种不是美元，已停止采集并保留原数据",false);
    }
    static void validateLocation(URI uri){
        if(!"https".equals(uri.getScheme())||!"www.amazon.com".equals(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!Set.of("/portal-migration/hz/glow/get-rendered-address-selections","/portal-migration/hz/glow/address-change").contains(uri.getPath()))throw failure("地区设置超出亚马逊公开偏好范围",false);
    }
    static String rating(Element row){
        var pattern=java.util.regex.Pattern.compile("([0-5](?:\\.[0-9]+)?)\\s+out of\\s+5\\s+stars",java.util.regex.Pattern.CASE_INSENSITIVE);
        for(Element e:row.select(".a-icon-alt, [aria-label*=stars], [title*=stars]")){
            var match=pattern.matcher(e.text()+" "+e.attr("aria-label")+" "+e.attr("title"));
            if(match.find()&&Double.parseDouble(match.group(1))<=5)return match.group(1)+" out of 5 stars";
        }
        return null;
    }
    static void searchPrice(Element row,ObjectNode item){
        for(Element e:row.select(".a-price:not(.a-text-price):not([data-a-strike=true])")){
            BigDecimal amount=price(text(e,".a-offscreen"));
            if(amount==null){String whole=text(e,".a-price-whole"),fraction=text(e,".a-price-fraction"),symbol=text(e,".a-price-symbol");if(whole!=null&&fraction!=null&&"$".equals(symbol))amount=price("$"+whole.replaceAll("[.,]$","")+"."+fraction);}
            if(amount!=null){item.put("price",amount).put("priceType","FEATURED");return;}
        }
        // Offer-listing minimum is a public quote, distinct from a featured offer or coupon.
        for(Element offer:row.select("[data-action=s-show-all-offers-display]")){
            Element parent=offer.parent();if(parent==null)continue;
            for(Element e:parent.select("span.a-color-base")){BigDecimal amount=price(e.text());if(amount!=null){item.put("price",amount).put("priceType","OFFER_FROM");return;}}
        }
    }
    private Document fetch(URI uri){return Jsoup.parse(request(new PublicRequest(uri,null,Map.of())),uri.toString());}
    private synchronized String request(PublicRequest input){
        URI uri=input.uri();boolean location=uri.getPath().startsWith("/portal-migration/hz/glow/");long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
        try{
            for(int redirects=0;redirects<4;redirects++){
                if(location)validateLocation(uri);else validate(uri);
                long pause=1500-(System.currentTimeMillis()-lastRequest);if(pause>0)Thread.sleep(pause);lastRequest=System.currentTimeMillis();
                var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("User-Agent","JiugengERP-Monitor/0.1").header("Accept-Language","en-US").header("Accept-Encoding","gzip, deflate");
                input.headers().forEach(builder::header);
                if(input.body()!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(input.body()));else builder.GET();
                HttpResponse<byte[]> response=readResponse(client,builder.build(),Duration.ofNanos(Math.max(1,deadline-System.nanoTime())));
                int status=response.statusCode();
                if(status>=300&&status<400){if(location)throw failure("地区设置发生非预期跳转",false);uri=uri.resolve(response.headers().firstValue("Location").orElseThrow());continue;}
                if(status==429)throw new CollectionFailure("亚马逊限流，等待重试",true,300);
                if(status!=200)throw failure("亚马逊返回HTTP "+status,status>=500);
                String body=decodeBody(response.body(),response.headers().firstValue("Content-Encoding").orElse("identity"));checkPage(Jsoup.parse(body));return body;
            }
            throw failure("页面重定向过多",false);
        }catch(CollectionFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw failure("采集任务被中断",true);}catch(Exception e){throw failure("亚马逊前台连接失败或超时",true);}
    }
    static String decodeBody(byte[] bytes,String encoding){
        try{
            java.io.InputStream stream=new java.io.ByteArrayInputStream(bytes);
            stream=switch(encoding.trim().toLowerCase(Locale.ROOT)){
                case "gzip", "x-gzip" -> new java.util.zip.GZIPInputStream(stream);
                case "deflate" -> new java.util.zip.InflaterInputStream(stream);
                case "", "identity" -> stream;
                default -> throw failure("不支持的网页压缩格式，未更新基线",false);
            };
            try(var decoded=stream){byte[] body=decoded.readNBytes(4_000_001);if(body.length>4_000_000)throw failure("网页解压后超出大小限制，未更新基线",false);return new String(body,StandardCharsets.UTF_8);}
        }catch(java.io.IOException e){throw failure("网页压缩数据不完整，等待重试",true);}
    }
    static HttpResponse<byte[]> readResponse(HttpClient client,HttpRequest request,Duration timeout) throws Exception {
        var body=new BoundedBody(4_000_000);var pending=client.sendAsync(request,info->body);
        try{return pending.get(timeout.toNanos(),java.util.concurrent.TimeUnit.NANOSECONDS);}
        catch(Exception e){body.cancel();pending.cancel(true);throw e;}
    }
    /** Bounded streaming subscriber: cancellation closes stalled bodies as well as headers. */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit; private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription; private boolean cancelled;
        BoundedBody(int limit){this.limit=limit;}
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return result;}
        public synchronized void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;if(cancelled)s.cancel();else s.request(1);}
        public synchronized void onNext(List<java.nio.ByteBuffer> chunks){
            if(cancelled)return;
            for(var chunk:chunks){if(chunk.remaining()>limit-bytes.size()){cancel();return;}byte[] b=new byte[chunk.remaining()];chunk.get(b);bytes.writeBytes(b);}
            subscription.request(1);
        }
        public void onError(Throwable t){result.completeExceptionally(t);}
        public synchronized void onComplete(){if(!cancelled)result.complete(bytes.toByteArray());}
        synchronized void cancel(){cancelled=true;if(subscription!=null)subscription.cancel();result.completeExceptionally(failure("网页读取超时或超过大小限制",true));}
    }
    static void validate(URI uri){if(!"https".equals(uri.getScheme())||!Set.of("www.amazon.com","amazon.com").contains(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!(uri.getPath().startsWith("/dp/")||uri.getPath().equals("/s")))throw failure("页面跳转超出亚马逊公开商品/店铺范围",false);}
    static void checkPage(Document doc){if(doc.selectFirst("form[action*=validateCaptcha], input#captchacharacters")!=null||doc.title().toLowerCase(Locale.ROOT).contains("robot check")||doc.text().contains("Enter the characters you see below"))throw failure("亚马逊要求验证码，已停止采集",false);}
    static Map<String,String> query(URI uri){Map<String,String> map=new HashMap<>();if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){String[] kv=part.split("=",2);if(kv.length==2)map.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return map;}
    private static String text(Element root,String selector){Element e=root.selectFirst(selector);return e==null||e.text().isBlank()?null:e.text().trim();}
    private static String attr(Element root,String selector,String name){Element e=root.selectFirst(selector);return e==null||e.attr(name).isBlank()?null:e.attr(name).trim();}
    static BigDecimal price(String text){if(text==null||!text.contains("$")||text.contains("-"))return null;try{return new BigDecimal(text.replace("US$","").replace("$","").replace(",","").trim()).setScale(2);}catch(Exception e){return null;}}
    private static CollectionFailure failure(String message,boolean retry){return new CollectionFailure(message,retry,0);}
}
