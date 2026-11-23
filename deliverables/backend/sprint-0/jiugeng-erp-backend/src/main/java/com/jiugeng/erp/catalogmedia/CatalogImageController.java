package com.jiugeng.erp.catalogmedia;

import static com.jiugeng.erp.catalogmedia.CatalogImageGateway.Purpose;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @Profile("!mock") @RequestMapping("/api/v1/catalog-images")
class CatalogImageController {
    private final CatalogImageService service;private final CatalogImageStore store;private final CatalogImageVariants variants;
    CatalogImageController(CatalogImageService service,CatalogImageStore store,CatalogImageVariants variants){this.service=service;this.store=store;this.variants=variants;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('products.write','materials.write','suppliers.write','samples.write','customers.write','purchases.write')")
    CatalogImageService.Receipt upload(@RequestParam Purpose purpose,@RequestParam MultipartFile file,
            Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException{
        String required=switch(purpose){case PRODUCT_MAIN, SKU_MAIN->"products.write";case MATERIAL_IMAGE->"materials.write";case SUPPLIER_QR->"suppliers.write";case SAMPLE_STORAGE, SAMPLE_IMAGE->"samples.write";case CUSTOMER_WECHAT->"customers.write";case PURCHASE_ITEM, PURCHASE_WAYBILL->"purchases.write";};if(!has(authentication,required))
            throw new CatalogImageException(403,"CAPABILITY_DENIED","当前账号无权上传该用途图片");
        return service.upload(purpose,file.getOriginalFilename(),file.getBytes(),actor);}
    @GetMapping("/{id}/{variant:thumbnail|original}")
    @PreAuthorize("hasAnyAuthority('products.read','materials.read','suppliers.read','samples.read','customers.read','purchases.read')")
    ResponseEntity<byte[]> content(@PathVariable String id,@PathVariable String variant,
            @org.springframework.web.bind.annotation.RequestHeader(value="If-None-Match",required=false) String condition,
            Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException{
        long start=System.nanoTime();boolean thumbnail=variant.equals("thumbnail");
        var asset=service.authorize(id,thumbnail,actor,has(authentication,"products.read"),has(authentication,"materials.read"),has(authentication,"suppliers.read"),has(authentication,"samples.read"),has(authentication,"customers.read"),has(authentication,"purchases.read"));
        var small=thumbnail?variants.find(id):null;
        String key=small!=null?small.key():thumbnail?asset.thumbnailKey():asset.originalKey();
        String type=small!=null?small.mediaType():asset.storedMediaType();
        boolean cacheable="ACTIVE".equals(asset.status())&&switch(asset.purpose()){case SUPPLIER_QR,CUSTOMER_WECHAT,PURCHASE_WAYBILL,SAMPLE_STORAGE->false;default->true;};
        return ImageHttpResponse.send(key,type,cacheable,condition,start,()->store.read(key));
    }
    private boolean has(Authentication a,String authority){return a.getAuthorities().stream().anyMatch(v->v.getAuthority().equals(authority));}
}
