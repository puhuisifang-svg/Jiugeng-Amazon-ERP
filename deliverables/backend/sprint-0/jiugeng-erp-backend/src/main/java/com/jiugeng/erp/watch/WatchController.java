package com.jiugeng.erp.watch;
import static com.jiugeng.erp.watch.WatchTypes.*;import com.jiugeng.erp.iam.AuthenticatedActor;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;import org.springframework.context.annotation.Profile;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/watch") class WatchController{
 private final WatchService service;private final com.jiugeng.erp.collection.CollectionService collection;WatchController(WatchService service,com.jiugeng.erp.collection.CollectionService collection){this.service=service;this.collection=collection;}
 @GetMapping("/stores") @PreAuthorize("hasAuthority('watch.read')") List<StoreView> stores(){return service.stores();}
 @PostMapping("/stores") @PreAuthorize("hasAuthority('watch.write')") StoreView create(@Valid @RequestBody StoreRequest r,@AuthenticationPrincipal AuthenticatedActor a){return service.createStore(r.storeKey(),r.storeName(),r.sourceUrl(),a);}
 @PostMapping("/stores/from-url") @PreAuthorize("hasAuthority('watch.write')") StoreView fromUrl(@RequestBody java.util.Map<String,String> r,@AuthenticationPrincipal AuthenticatedActor a){
  String seller=seller(r.get("url"));String name=r.getOrDefault("name","");
  var existing=service.stores().stream().filter(v->v.storeKey().equals(seller)).findFirst();
  var store=existing.or(()->service.restoreStore(seller,a)).orElseGet(()->service.createStore(seller,name.isBlank()?seller:name,"https://www.amazon.com/sp?seller="+seller,a));collection.enqueue("STORE",store.id());return store;
 }
 @PostMapping("/stores/{id}/collect") @PreAuthorize("hasAuthority('watch.write')") void collect(@PathVariable String id){collection.enqueue("STORE",id);}
 static String seller(String value){try{var uri=java.net.URI.create(value);if(!"https".equals(uri.getScheme())||!java.util.Set.of("www.amazon.com","amazon.com").contains(uri.getHost())||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException();
  var query=uri.getRawQuery();if(query!=null)for(String p:query.split("&")){var kv=p.split("=",2);if(kv.length==2&&java.util.Set.of("seller","me").contains(kv[0])&&kv[1].matches("[A-Z0-9]{8,30}"))return kv[1];}
 }catch(Exception ignored){}throw new WatchException(400,"WATCH_SELLER_URL","请填写包含 seller= 或 me= 的亚马逊美国站店铺地址");}
 @PostMapping("/stores/{id}/runs") @PreAuthorize("hasAuthority('watch.write')") RunResult run(@PathVariable String id,@Valid @RequestBody RunRequest r,@AuthenticationPrincipal AuthenticatedActor a){return service.run(id,r.requestId(),r.status(),r.itemCount(),r.pageCount(),r.pagesComplete(),r.asins(),a);}
 @GetMapping("/events") @PreAuthorize("hasAuthority('watch.read')") List<EventView> events(){return service.events();}
 @PostMapping("/events/{id}/confirm") @PreAuthorize("hasAuthority('watch.write')") Confirmation confirm(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor a){return service.confirm(id,a);}
 record StoreRequest(@NotBlank @Size(max=100) String storeKey,@NotBlank @Size(max=200) String storeName,@NotBlank @Size(max=1000) String sourceUrl){}
 record RunRequest(@NotBlank @Size(max=100) String requestId,@NotNull RunStatus status,@PositiveOrZero int itemCount,@PositiveOrZero int pageCount,boolean pagesComplete,@NotNull @Size(max=1000) List<@NotBlank String> asins){}
}
