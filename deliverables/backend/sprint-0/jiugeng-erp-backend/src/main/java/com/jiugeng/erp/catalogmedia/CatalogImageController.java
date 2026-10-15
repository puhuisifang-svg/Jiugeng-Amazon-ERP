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
    private final CatalogImageService service;CatalogImageController(CatalogImageService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('products.write','materials.write','suppliers.write')")
    CatalogImageService.Receipt upload(@RequestParam Purpose purpose,@RequestParam MultipartFile file,
            Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException{
        String required=switch(purpose){case SKU_MAIN->"products.write";case MATERIAL_IMAGE->"materials.write";case SUPPLIER_QR->"suppliers.write";};if(!has(authentication,required))
            throw new CatalogImageException(403,"CAPABILITY_DENIED","当前账号无权上传该用途图片");
        return service.upload(purpose,file.getOriginalFilename(),file.getBytes(),actor);}
    @GetMapping("/{id}/thumbnail") @PreAuthorize("hasAnyAuthority('products.read','materials.read','suppliers.read')")
    ResponseEntity<byte[]> thumbnail(@PathVariable String id,Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException{return response(service.read(id,true,actor,has(authentication,"products.read"),has(authentication,"materials.read"),has(authentication,"suppliers.read")));}
    @GetMapping("/{id}/original") @PreAuthorize("hasAnyAuthority('products.read','materials.read','suppliers.read')")
    ResponseEntity<byte[]> original(@PathVariable String id,Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor)throws IOException{return response(service.read(id,false,actor,has(authentication,"products.read"),has(authentication,"materials.read"),has(authentication,"suppliers.read")));}
    private boolean has(Authentication a,String authority){return a.getAuthorities().stream().anyMatch(v->v.getAuthority().equals(authority));}
    private ResponseEntity<byte[]> response(CatalogImageService.Content c){return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mediaType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(c.bytes());}
}
