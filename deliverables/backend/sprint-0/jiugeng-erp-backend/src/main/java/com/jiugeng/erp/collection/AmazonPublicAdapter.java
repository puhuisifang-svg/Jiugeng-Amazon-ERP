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

/** Public US storefront only. No seller account, cookies, CAPTCHA solving or arbitrary hosts. */
@Component @Profile("!mock")
class AmazonPublicAdapter implements CollectionAdapter {
    static final String SOURCE="amazon-public-us";
    private final ObjectMapper mapper; private final boolean enabled; private final HttpClient client;
    private long lastRequest;
    AmazonPublicAdapter(ObjectMapper mapper,@Value("${erp.collection.amazon.enabled:false}") boolean enabled){
        this.mapper=mapper;this.enabled=enabled;
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER);
        // A deployment may explicitly use its existing outbound proxy; never log its configuration.
        String proxy=System.getenv("ERP_COLLECTION_PROXY");
        if(proxy!=null&&!proxy.isBlank()) { URI p=URI.create(proxy);if(!Set.of("http","https").contains(p.getScheme())||p.getHost()==null||p.getUserInfo()!=null||p.getPort()<1)throw new IllegalArgumentException("采集代理配置无效");builder.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(),p.getPort()))); }
        client=builder.build();
    }
    public String provider(){return SOURCE;}
    public Set<String> kinds(){return Set.of("PRODUCT","STORE");}
    public boolean approved(){return enabled;}
    public Observation collect(Target target){
        if(!enabled)throw failure("网页采集未启用",false);
        if(target.kind().equals("PRODUCT")){
            if(!target.key().matches("[A-Z0-9]{10}"))throw failure("ASIN无效",false);
            Document doc=fetch(URI.create("https://www.amazon.com/dp/"+target.key()));
            return new Observation(Instant.now(),"COMPLETE",true,product(doc,target.key()));
        }
        if(!target.kind().equals("STORE")||!target.key().matches("[A-Z0-9]{8,30}"))throw failure("请填写有效的亚马逊美国站seller店铺地址",false);
        return store(target.key());
    }
    private Observation store(String seller){
        URI uri=URI.create("https://www.amazon.com/s?me="+seller+"&marketplaceID=ATVPDKIKX0DER");
        Set<String> visited=new HashSet<>(),pageSignatures=new HashSet<>();var products=new LinkedHashMap<String,ObjectNode>();
        Instant started=Instant.now();int pages=0;
        while(uri!=null){
            if(++pages>20||Duration.between(started,Instant.now()).toSeconds()>200||!visited.add(uri.toString()))throw failure("店铺分页超出单次范围或出现循环，未更新基线",false);
            Document doc=fetch(uri);var rows=doc.select("[data-component-type=s-search-result][data-asin]");
            if(rows.isEmpty())throw failure("未识别到店铺商品列表，保留原基线",false);
            Set<String> pageAsins=new TreeSet<>();
            for(Element row:rows){String asin=row.attr("data-asin");if(!asin.matches("[A-Z0-9]{10}"))continue;
                String title=text(row,"h2");if(title==null)throw failure("店铺商品标题缺失，未更新基线",false);
                ObjectNode item=mapper.createObjectNode().put("asin",asin).put("title",title.substring(0,Math.min(300,title.length())));
                String image=attr(row,"img.s-image","src");if(image!=null)item.put("imageUrl",image);
                BigDecimal price=price(text(row,".a-price .a-offscreen"));if(price!=null)item.put("price",price);
                String rating=text(row,".a-icon-alt");if(rating!=null)item.put("rating",rating);
                products.put(asin,item);pageAsins.add(asin);
            }
            if(pageAsins.isEmpty()||!pageSignatures.add(pageAsins.toString()))throw failure("店铺分页重复或结果为空，未更新基线",false);
            Element next=doc.selectFirst("a.s-pagination-next[href]");
            if(next!=null){URI candidate=uri.resolve(next.attr("href"));validate(candidate);Map<String,String> query=query(candidate);if(!seller.equals(query.get("me"))&&!seller.equals(query.get("seller")))throw failure("店铺下一页未保持seller范围，未更新基线",false);uri=candidate;}
            else if(doc.selectFirst(".s-pagination-next.s-pagination-disabled, .a-pagination .a-last.a-disabled")!=null)uri=null;
            else throw failure("无法确认店铺最后一页，未更新基线",false);
        }
        ObjectNode fields=mapper.createObjectNode();var array=fields.putArray("products");products.values().forEach(array::add);fields.put("pageCount",pages).put("itemCount",products.size());
        return new Observation(Instant.now(),"COMPLETE",true,fields);
    }
    ObjectNode product(Document doc,String asin){
        checkPage(doc);
        String title=text(doc,"#productTitle");String actual=attr(doc,"input#ASIN","value");
        if(actual==null)actual=attr(doc,"input[name=ASIN]","value");
        if(title==null||!asin.equalsIgnoreCase(actual))throw failure("未识别到对应ASIN的完整商品页",false);
        ObjectNode fields=mapper.createObjectNode();
        for(String name:List.of("title","brand","imageUrl","bullets","price","currency","availability"))fields.putNull(name);
        fields.put("title",title);String brand=text(doc,"#bylineInfo");if(brand!=null)fields.put("brand",brand.replaceFirst("^Visit the ","").replaceFirst(" Store$","").replaceFirst("^Brand: *","").substring(0,Math.min(180,brand.replaceFirst("^Visit the ","").replaceFirst(" Store$","").replaceFirst("^Brand: *","").length())));
        String image=attr(doc,"#landingImage","data-old-hires");if(image==null)image=attr(doc,"#landingImage","src");if(image!=null&&image.startsWith("https://"))fields.put("imageUrl",image);
        var bullets=doc.select("#feature-bullets li span.a-list-item");if(!bullets.isEmpty()){var out=fields.putArray("bullets");bullets.stream().map(Element::text).map(String::trim).filter(v->!v.isEmpty()).limit(5).forEach(v->out.add(v.substring(0,Math.min(2000,v.length()))));}
        BigDecimal amount=price(text(doc,"#corePriceDisplay_desktop_feature_div .a-price .a-offscreen, #corePrice_feature_div .a-price .a-offscreen, #priceblock_ourprice, #priceblock_dealprice"));
        if(amount!=null){fields.put("price",amount);fields.put("currency","USD");}
        String availability=text(doc,"#availability");if(availability!=null){String lower=availability.toLowerCase(Locale.ROOT);if(lower.contains("currently unavailable")||lower.contains("out of stock"))fields.put("availability","UNAVAILABLE");else if(lower.contains("in stock")||lower.contains("left in stock"))fields.put("availability","AVAILABLE");}
        return fields;
    }
    private synchronized Document fetch(URI initial){
        URI uri=initial;
        try{
            for(int redirects=0;redirects<4;redirects++){
                validate(uri);long pause=1500-(System.currentTimeMillis()-lastRequest);if(pause>0)Thread.sleep(pause);lastRequest=System.currentTimeMillis();
                var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("User-Agent","JiugengERP-Monitor/0.1").header("Accept-Language","en-US").GET().build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
                try(var body=response.body()){
                    int status=response.statusCode();
                    if(status>=300&&status<400){uri=uri.resolve(response.headers().firstValue("Location").orElseThrow());continue;}
                    if(status==429)throw new CollectionFailure("亚马逊限流，等待重试",true,300);
                    if(status!=200)throw failure("亚马逊返回HTTP "+status,status>=500);
                    byte[] bytes=body.readNBytes(4_000_001);if(bytes.length>4_000_000)throw failure("网页超过大小限制",false);
                    Document doc=Jsoup.parse(new String(bytes,StandardCharsets.UTF_8),uri.toString());checkPage(doc);return doc;
                }
            }
            throw failure("页面重定向过多",false);
        }catch(CollectionFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw failure("采集任务被中断",true);}catch(Exception e){throw failure("亚马逊前台连接失败或超时",true);}
    }
    static void validate(URI uri){if(!"https".equals(uri.getScheme())||!Set.of("www.amazon.com","amazon.com").contains(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||!(uri.getPath().startsWith("/dp/")||uri.getPath().equals("/s")))throw failure("页面跳转超出亚马逊公开商品/店铺范围",false);}
    static void checkPage(Document doc){if(doc.selectFirst("form[action*=validateCaptcha], input#captchacharacters")!=null||doc.title().toLowerCase(Locale.ROOT).contains("robot check")||doc.text().contains("Enter the characters you see below"))throw failure("亚马逊要求验证码，已停止采集",false);}
    static Map<String,String> query(URI uri){Map<String,String> map=new HashMap<>();if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){String[] kv=part.split("=",2);if(kv.length==2)map.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return map;}
    private static String text(Element root,String selector){Element e=root.selectFirst(selector);return e==null||e.text().isBlank()?null:e.text().trim();}
    private static String attr(Element root,String selector,String name){Element e=root.selectFirst(selector);return e==null||e.attr(name).isBlank()?null:e.attr(name).trim();}
    static BigDecimal price(String text){if(text==null||!text.contains("$")||text.contains("-"))return null;try{return new BigDecimal(text.replace("US$","").replace("$","").replace(",","").trim()).setScale(2);}catch(Exception e){return null;}}
    private static CollectionFailure failure(String message,boolean retry){return new CollectionFailure(message,retry,0);}
}
