package com.jiugeng.erp.catalogmedia;

import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.io.IOException;

@RestController @Profile("!mock") @RequestMapping("/api/v1/catalog-images/external")
class ExternalImageController {
    private final ExternalImageService images;private final CatalogImageStore store;
    ExternalImageController(ExternalImageService images,CatalogImageStore store){this.images=images;this.store=store;}
    record Source(String url,String draftPermission){}
    @PostMapping("/resolve")
    ResponseEntity<java.util.Map<String,String>> resolve(@RequestBody Source source,Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException {
        if(source.url()==null)throw new CatalogImageException(400,"IMAGE_SOURCE_INVALID","请输入图片地址");
        try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Map.of("id",images.resolve(source.url(),source.draftPermission(),authentication,actor)));}
        catch(IOException invalid){throw new CatalogImageException(400,"IMAGE_SOURCE_INVALID","图片地址须为有效的公开 HTTP/HTTPS 地址");}
    }
    @GetMapping("/{id}/{variant:thumbnail|original}")
    ResponseEntity<byte[]> read(@PathVariable String id,@PathVariable String variant,@RequestHeader(value="If-None-Match",required=false)String condition,Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException {
        long start=System.nanoTime();var image=images.authorized(id,authentication,actor);
        if(image.original()==null)return ResponseEntity.status(503).header("Retry-After","2").cacheControl(CacheControl.noStore()).build();
        boolean thumb=variant.equals("thumbnail");
        String key=thumb?image.thumbnail():image.original(),type=thumb?image.thumbnailType():image.originalType();
        return ImageHttpResponse.send(key,type,!image.lossless(),condition,start,()->store.read(key));
    }
}
