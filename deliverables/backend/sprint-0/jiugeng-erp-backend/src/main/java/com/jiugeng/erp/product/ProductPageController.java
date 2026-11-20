package com.jiugeng.erp.product;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/products/page")
class ProductPageController {
    private final ProductPageService service;
    ProductPageController(ProductPageService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('products.read')")
    ProductPageService.Page page(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="KEYWORD")String field,@RequestParam(defaultValue="false")boolean exact,@RequestParam(defaultValue="")String productStatus,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String hasImage,@RequestParam(defaultValue="")String hasSupplier,@RequestParam(defaultValue="")String hasMaterial,@RequestParam(defaultValue="")String stock,@RequestParam(defaultValue="")String missing,@RequestParam(defaultValue="")String productType,@RequestParam(defaultValue="updatedAt")String sort,@RequestParam(defaultValue="desc")String direction,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String focusProductId,Authentication auth) {
        boolean suppliers=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("suppliers.read"));
        boolean inventory=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("inventory.read"));
        if((!suppliers&&(!hasSupplier.isEmpty()||"supplier".equals(missing)))||(!inventory&&!stock.isEmpty()))throw new ProductException(403,"FILTER_FORBIDDEN","无权使用该筛选");
        return service.page(new ProductPageService.Filter(q,field,exact,productStatus,status,hasImage,hasSupplier,hasMaterial,stock,missing,productType,null),sort,direction,page,size,suppliers,inventory,focusProductId);
    }
}
