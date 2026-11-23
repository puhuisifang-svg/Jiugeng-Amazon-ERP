package com.jiugeng.erp.purchase;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class PurchaseExternalImages extends JdbcExternalImageSource {
    PurchaseExternalImages(JdbcClient jdbc){super(jdbc,"purchases.read","SELECT snapshot_image_url url FROM purchase_order_line WHERE image_asset_id IS NULL",false);}
}
