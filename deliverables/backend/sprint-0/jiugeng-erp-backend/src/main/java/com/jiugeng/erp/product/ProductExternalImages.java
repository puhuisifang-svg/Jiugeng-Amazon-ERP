package com.jiugeng.erp.product;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class ProductExternalImages extends JdbcExternalImageSource {
    ProductExternalImages(JdbcClient jdbc){super(jdbc,"products.read","SELECT main_image_url url FROM product_variant WHERE deleted_at IS NULL AND main_image_asset_id IS NULL",false);}
}
